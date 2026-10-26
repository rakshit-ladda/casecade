package com.attentiontwin.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.text.TextUtils;
import java.util.Calendar;
import java.util.Locale;

final class FocusUtils {
    static final String PREFS = "attentiontwin_focus";
    private FocusUtils() { }

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean hasNotificationAccess(Context context) {
        String enabled = Settings.Secure.getString(context.getContentResolver(), "enabled_notification_listeners");
        return !TextUtils.isEmpty(enabled) && enabled.contains(new ComponentName(context, FocusListenerService.class).flattenToString());
    }

    static boolean isFocusActive(Context context) {
        SharedPreferences p = prefs(context);
        if (p.getBoolean("paused", false)) return false;
        long pausedUntil = p.getLong("focus_paused_until", 0L);
        if (pausedUntil == Long.MAX_VALUE || pausedUntil > System.currentTimeMillis()) return false;
        long until = prefs(context).getLong("focus_until", 0L);
        return until == Long.MAX_VALUE || until > System.currentTimeMillis() || inDailyWindow(context);
    }

    static long focusUntil(Context context) {
        return prefs(context).getLong("focus_until", 0L);
    }

    static long nextFocusWindowStartAt(Context context) {
        String schedule = prefs(context).getString("focus_schedule", "");
        long now = System.currentTimeMillis(), next = Long.MAX_VALUE;
        Calendar today = Calendar.getInstance();
        for (String item : schedule.split(",")) {
            String[] parts = item.trim().split("-");
            if (parts.length != 2) continue;
            int start = parseTime(parts[0]);
            if (start < 0) continue;
            for (int day=0; day<=1; day++) {
                Calendar candidate = (Calendar) today.clone(); candidate.add(Calendar.DAY_OF_YEAR, day);
                candidate.set(Calendar.HOUR_OF_DAY, start/60); candidate.set(Calendar.MINUTE, start%60); candidate.set(Calendar.SECOND, 0); candidate.set(Calendar.MILLISECOND, 0);
                if (candidate.getTimeInMillis() > now) next = Math.min(next, candidate.getTimeInMillis());
            }
        }
        return next;
    }

    static void clearExpiredManualPause(Context context) {
        long until = prefs(context).getLong("focus_paused_until", 0L);
        if (until > 0L && until != Long.MAX_VALUE && until <= System.currentTimeMillis()) prefs(context).edit().remove("focus_paused_until").apply();
    }

    static void releaseExpiredSnoozeMarkers(Context context, long now) {
        SharedPreferences prefs = prefs(context);
        SharedPreferences.Editor editor = prefs.edit();
        for (java.util.Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith("snooze_release_")) continue;
            long releaseAt = entry.getValue() instanceof Number ? ((Number) entry.getValue()).longValue() : 0L;
            if (releaseAt > 0L && releaseAt <= now) {
                String hash = key.substring("snooze_release_".length());
                editor.putBoolean("snooze_release_once_" + hash, true).remove(key);
            }
        }
        editor.apply();
    }

    static boolean inDailyWindow(Context context) {
        String schedule = prefs(context).getString("focus_schedule", "");
        int now = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) * 60 + Calendar.getInstance().get(Calendar.MINUTE);
        for (String item : schedule.split(",")) {
            String[] parts = item.trim().split("-");
            if (parts.length != 2) continue;
            int start = parseTime(parts[0]);
            int end = parseTime(parts[1]);
            if (start < 0 || end < 0 || start == end) continue;
            if (start < end ? now >= start && now < end : now >= start || now < end) return true;
        }
        return false;
    }

    static long nextBreakAt(Context context) {
        String times = prefs(context).getString("break_times", "10:30,13:00,15:30");
        long now = System.currentTimeMillis();
        long next = Long.MAX_VALUE;
        for (String value : times.split(",")) {
            int minutes = parseTime(value);
            if (minutes < 0) continue;
            Calendar candidate = Calendar.getInstance();
            candidate.set(Calendar.HOUR_OF_DAY, minutes / 60);
            candidate.set(Calendar.MINUTE, minutes % 60);
            candidate.set(Calendar.SECOND, 0);
            candidate.set(Calendar.MILLISECOND, 0);
            if (candidate.getTimeInMillis() <= now) candidate.add(Calendar.DAY_OF_YEAR, 1);
            next = Math.min(next, candidate.getTimeInMillis());
        }
        return next;
    }

    static int minutesToBreak(Context context) {
        long next = nextBreakAt(context);
        if (next == Long.MAX_VALUE) return 25;
        return Math.max(1, (int) Math.ceil((next - System.currentTimeMillis()) / 60000.0));
    }

    static long nextReleaseAt(Context context) {
        long now = System.currentTimeMillis();
        long release = nextBreakAt(context);
        long focusUntil = focusUntil(context);
        if (focusUntil > now && focusUntil != Long.MAX_VALUE) release = Math.min(release, focusUntil);
        if (release == Long.MAX_VALUE) release = now + 8L * 60L * 60L * 1000L;
        return Math.max(now + 1000L, release);
    }

    static boolean isVip(Context context, String title, String text) {
        String configured = prefs(context).getString("vip_contacts", "");
        String message = ((title == null ? "" : title) + " " + (text == null ? "" : text)).toLowerCase(Locale.ROOT);
        for (String entry : configured.split(",")) {
            String name = entry.trim().toLowerCase(Locale.ROOT);
            if (name.length() >= 2 && message.contains(name)) return true;
        }
        return false;
    }

    static String roleFor(Context context, String packageName) {
        String stored = prefs(context).getString("role_" + packageName, null);
        if ("IMPORTANT".equals(stored)) return DecisionEngine.AppRole.IMPORTANT.name();
        if ("DISTRACTING".equals(stored)) return DecisionEngine.AppRole.BLOCK_IN_FOCUS.name();
        if ("NORMAL".equals(stored)) {
            return DecisionEngine.AppRole.ALLOW.name();
        }
        if (stored != null) return stored;
        return DecisionEngine.AppRole.ALLOW.name();
    }

    static String categoryFor(String packageName, String label) {
        String value = ((packageName == null ? "" : packageName) + " " + (label == null ? "" : label)).toLowerCase(Locale.ROOT);
        if (value.contains("instagram") || value.contains("youtube") || value.contains("tiktok") || value.contains("facebook") || value.contains("linkedin")) return "social";
        if (value.contains("whatsapp") || value.contains("telegram") || value.contains("messenger") || value.contains("slack") || value.contains("gmail") || value.contains("mail")) return "communication";
        if (value.contains("calendar") || value.contains("notion") || value.contains("docs") || value.contains("github") || value.contains("outlook")) return "work";
        if (value.contains("bank") || value.contains("pay") || value.contains("health")) return "health";
        if (value.contains("system") || value.contains("phone") || value.contains("dialer")) return "system";
        return "other";
    }

    static int parseTime(String value) {
        String[] parts = value.trim().split(":");
        if (parts.length != 2) return -1;
        try {
            int h = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]);
            return h < 0 || h > 23 || m < 0 || m > 59 ? -1 : h * 60 + m;
        } catch (NumberFormatException ignored) { return -1; }
    }

    static long nextFocusWindowTransition(Context context) {
        String schedule = prefs(context).getString("focus_schedule", "");
        long now = System.currentTimeMillis();
        long next = Long.MAX_VALUE;
        Calendar today = Calendar.getInstance();
        for (String item : schedule.split(",")) {
            String[] parts = item.trim().split("-");
            if (parts.length != 2) continue;
            int start = parseTime(parts[0]), end = parseTime(parts[1]);
            if (start < 0 || end < 0 || start == end) continue;
            for (int day = 0; day <= 1; day++) {
                Calendar startAt = (Calendar) today.clone(); startAt.add(Calendar.DAY_OF_YEAR, day);
                startAt.set(Calendar.HOUR_OF_DAY, start / 60); startAt.set(Calendar.MINUTE, start % 60); startAt.set(Calendar.SECOND, 0); startAt.set(Calendar.MILLISECOND, 0);
                if (startAt.getTimeInMillis() > now) next = Math.min(next, startAt.getTimeInMillis());
                Calendar endAt = (Calendar) today.clone(); endAt.add(Calendar.DAY_OF_YEAR, day);
                if (end < start) endAt.add(Calendar.DAY_OF_YEAR, 1);
                endAt.set(Calendar.HOUR_OF_DAY, end / 60); endAt.set(Calendar.MINUTE, end % 60); endAt.set(Calendar.SECOND, 0); endAt.set(Calendar.MILLISECOND, 0);
                if (endAt.getTimeInMillis() > now) next = Math.min(next, endAt.getTimeInMillis());
            }
        }
        return next;
    }
}
