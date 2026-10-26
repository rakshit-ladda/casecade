package com.attentiontwin.app;

import java.util.Locale;
import java.util.regex.Pattern;

/** Small deterministic rules engine shared by live routing and the demo generator. */
public final class DecisionEngine {
    public enum Action { ALLOW, DEFER, SUPPRESS }
    public enum Role { IMPORTANT, NORMAL, DISTRACTING }
    public enum AppRole { BLOCK_IN_FOCUS, ALLOW, SMART, IMPORTANT }

    private static final Pattern OTP = Pattern.compile(
        "\\b(otp|one[- ]time (password|code)|verification code|security code|passcode)\\b|\\b(code|otp|passcode|verification)\\D{0,20}\\d{4,8}\\b",
        Pattern.CASE_INSENSITIVE
    );

    public static final class Stats {
        public final int shown;
        public final int opened;
        public final int importanceUp;
        public final int importanceDown;

        public Stats(int shown, int opened, int importanceUp, int importanceDown) {
            this.shown = Math.max(0, shown);
            this.opened = Math.max(0, opened);
            this.importanceUp = Math.max(0, importanceUp);
            this.importanceDown = Math.max(0, importanceDown);
        }
    }

    public static final class Input {
        public String packageName = "";
        public String category = "other";
        public String notificationCategory = "";
        public String title = "";
        public String text = "";
        public String urgentWords = "";
        public Role role = Role.NORMAL;
        public boolean focusActive;
        public boolean keepPreviews;
        public int minutesToBreak = 25;
        public Stats stats = new Stats(0, 0, 0, 0);
    }

    public static final class Result {
        public final Action action;
        public final String reason;
        public final double openRateEstimate;
        public final double importanceEstimate;
        public final double allowCost;
        public final double deferCost;
        public final double suppressCost;
        public final int observations;
        public final boolean exception;

        Result(Action action, String reason, double openRate, double importance,
               double allowCost, double deferCost, double suppressCost, int observations,
               boolean exception) {
            this.action = action;
            this.reason = reason;
            this.openRateEstimate = openRate;
            this.importanceEstimate = importance;
            this.allowCost = allowCost;
            this.deferCost = deferCost;
            this.suppressCost = suppressCost;
            this.observations = observations;
            this.exception = exception;
        }
    }

    public Result evaluate(Input input) {
        if (input == null) throw new IllegalArgumentException("input is required");
        Stats stats = input.stats == null ? new Stats(0, 0, 0, 0) : input.stats;
        String content = (safe(input.title) + " " + safe(input.text)).toLowerCase(Locale.ROOT);
        String category = safe(input.category).toLowerCase(Locale.ROOT);
        String notifCategory = safe(input.notificationCategory).toLowerCase(Locale.ROOT);
        String pkg = safe(input.packageName).toLowerCase(Locale.ROOT);

        boolean otp = OTP.matcher(content).find();
        boolean urgent = containsAny(content, input.urgentWords);
        boolean critical = isCriticalCategory(notifCategory) || isCriticalPackage(pkg);
        double prior = categoryPrior(category);
        double pOpen = clamp((stats.opened + prior * 8.0) / (stats.shown + 8.0), 0.02, 0.98);
        double importance = roleImportance(input.role, stats);
        int observations = stats.shown;

        double session = sessionPrior(category);
        double resume = input.focusActive ? 12.0 : 2.0;
        double allow = pOpen * (session + resume) + (1.0 - pOpen) * 0.5;
        double defer = 0.1 * pOpen * session + importance * Math.max(0, input.minutesToBreak) / 10.0;
        double suppress = importance * 15.0 * 0.25;

        if (!input.focusActive) return result(Action.ALLOW, "Focus is off; leave the notification available.", pOpen, importance, allow, defer, suppress, observations, false);
        if (critical) return result(Action.ALLOW, "Critical system, call, alarm, or navigation notifications are always allowed.", pOpen, importance, allow, defer, suppress, observations, true);
        if (otp) return result(Action.ALLOW, "Possible verification code detected; never delay it automatically.", pOpen, importance, allow, defer, suppress, observations, true);
        if (urgent) return result(Action.ALLOW, "A user-configured urgent word matched; notification is allowed.", pOpen, importance, allow, defer, suppress, observations, true);
        if (input.role == Role.IMPORTANT) return result(Action.ALLOW, "This app is marked Important by the user.", pOpen, importance, allow, defer, suppress, observations, true);

        Action chosen = Action.ALLOW;
        double best = allow;
        if (defer < best) { chosen = Action.DEFER; best = defer; }
        boolean lowConfidenceProtect = observations < 10 && importance >= 0.35;
        if (!lowConfidenceProtect && suppress < best) chosen = Action.SUPPRESS;

        String reason;
        if (chosen == Action.DEFER) {
            reason = observations < 10
                ? "During focus, defer this app until the next break while its personal history is still small."
                : "During focus, deferral has the lowest estimated attention cost for this app's observed history.";
        } else if (chosen == Action.SUPPRESS) {
            reason = "This app is marked distracting and its estimated suppression cost is lowest; review remains available.";
        } else {
            reason = lowConfidenceProtect
                ? "Limited personal history and meaningful importance; avoid suppression and leave it available."
                : "Allow has the lowest estimated attention cost for this context.";
        }
        return result(chosen, reason, pOpen, importance, allow, defer, suppress, observations, false);
    }

    /** Deterministic routing for live app notifications; estimates are consulted only for SMART. */
    public Result evaluateForAppRole(Input input, AppRole appRole) {
        if (input == null) throw new IllegalArgumentException("input is required");
        AppRole role = appRole == null ? AppRole.ALLOW : appRole;
        if (!input.focusActive) return result(Action.ALLOW, "Focus is off; leave the notification available.", 0.0, 0.0, 0.0, 0.0, 0.0, 0, false);
        String exception = hardExceptionReason(input);
        if (!exception.isEmpty()) return result(Action.ALLOW, exception, 0.0, 0.0, 0.0, 0.0, 0.0, 0, true);
        if (role == AppRole.ALLOW || role == AppRole.IMPORTANT) {
            return result(Action.ALLOW, role == AppRole.IMPORTANT ? "This app is marked Important/VIP." : "This app is set to Allow during focus.", 0.0, 0.0, 0.0, 0.0, 0.0, 0, role == AppRole.IMPORTANT);
        }
        if (role == AppRole.BLOCK_IN_FOCUS) return result(Action.DEFER, "This app is set to Block in focus.", 0.0, 0.0, 0.0, 0.0, 0.0, 0, false);

        Input safeInput = new Input();
        safeInput.packageName = input.packageName; safeInput.category = input.category;
        safeInput.notificationCategory = input.notificationCategory; safeInput.title = input.title;
        safeInput.text = input.text; safeInput.urgentWords = input.urgentWords;
        safeInput.role = Role.NORMAL; safeInput.focusActive = input.focusActive;
        safeInput.keepPreviews = input.keepPreviews; safeInput.minutesToBreak = input.minutesToBreak;
        safeInput.stats = input.stats;
        Result estimated = evaluate(safeInput);
        Action smartAction = estimated.action == Action.ALLOW ? Action.ALLOW : Action.DEFER;
        return result(smartAction, "SMART used the local estimate to choose " + smartAction.name() + ".", estimated.openRateEstimate, estimated.importanceEstimate, estimated.allowCost, estimated.deferCost, estimated.suppressCost, estimated.observations, false);
    }

    /** Fail-open wrapper for live routing when input/state is corrupt or the engine throws. */
    public Result evaluateForAppRoleSafely(Input input, AppRole role) {
        try { return evaluateForAppRole(input, role); }
        catch (RuntimeException error) {
            return result(Action.ALLOW, "Fail-open: decision engine unavailable; notification left available.", 0.0, 0.0, 0.0, 0.0, 0.0, 0, true);
        }
    }

    public static String hardExceptionReason(Input input) {
        String content = (safe(input.title) + " " + safe(input.text)).toLowerCase(Locale.ROOT);
        String category = safe(input.notificationCategory).toLowerCase(Locale.ROOT);
        String pkg = safe(input.packageName).toLowerCase(Locale.ROOT);
        if (isCriticalCategory(category) || isCriticalPackage(pkg)) return "Exception: call, alarm, navigation or system notification.";
        if (OTP.matcher(content).find()) return "Exception: possible OTP/verification code.";
        if (containsAny(content, input.urgentWords)) return "Exception: user-listed urgent word.";
        return "";
    }

    /** A system re-post is returned once its scheduled time has arrived; it is never re-decided. */
    public static boolean shouldKeepHeldOnRepost(long releaseAt, long now) {
        return releaseAt > now;
    }

    public static boolean shouldAllowSnoozeReleaseOnce(long releaseAt, long now) {
        return releaseAt > 0L && releaseAt <= now;
    }

    private static Result result(Action action, String reason, double pOpen, double importance,
                                 double allow, double defer, double suppress, int observations,
                                 boolean exception) {
        return new Result(action, reason, pOpen, importance, allow, defer, suppress, observations, exception);
    }

    private static boolean isCriticalCategory(String category) {
        return "call".equals(category) || "alarm".equals(category) || "navigation".equals(category)
            || "emergency".equals(category) || "error".equals(category);
    }

    private static boolean isCriticalPackage(String pkg) {
        return "android".equals(pkg) || pkg.startsWith("com.android.phone")
            || pkg.startsWith("com.android.server.telecom") || pkg.startsWith("com.google.android.dialer")
            || pkg.startsWith("com.android.dialer") || pkg.startsWith("com.android.systemui");
    }

    private static double roleImportance(Role role, Stats stats) {
        double base = role == Role.IMPORTANT ? 1.0 : role == Role.DISTRACTING ? 0.15 : 0.5;
        int total = stats.importanceUp + stats.importanceDown;
        if (total == 0) return base;
        return clamp((base * 4.0 + stats.importanceUp) / (4.0 + total), 0.05, 1.0);
    }

    private static double categoryPrior(String category) {
        if (category.contains("social") || category.contains("entertainment")) return 0.62;
        if (category.contains("communication")) return 0.48;
        if (category.contains("work") || category.contains("productivity")) return 0.38;
        if (category.contains("system") || category.contains("health") || category.contains("finance")) return 0.72;
        return 0.45;
    }

    private static double sessionPrior(String category) {
        if (category.contains("social") || category.contains("entertainment")) return 15.0;
        if (category.contains("communication")) return 6.0;
        if (category.contains("work") || category.contains("productivity")) return 5.0;
        if (category.contains("system") || category.contains("health") || category.contains("finance")) return 3.0;
        return 6.0;
    }

    private static boolean containsAny(String text, String words) {
        if (text.isEmpty() || words == null) return false;
        for (String word : words.split(",")) {
            String candidate = word.trim().toLowerCase(Locale.ROOT);
            if (candidate.length() >= 2 && text.contains(candidate)) return true;
        }
        return false;
    }

    private static String safe(String value) { return value == null ? "" : value; }
    private static double clamp(double value, double low, double high) { return Math.max(low, Math.min(high, value)); }
}
