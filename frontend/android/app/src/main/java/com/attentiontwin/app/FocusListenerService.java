package com.attentiontwin.app;

import android.app.Notification;
import android.content.ComponentName;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/** System-bound listener: it continues receiving callbacks while the UI is closed. */
public final class FocusListenerService extends NotificationListenerService {
    public static volatile boolean connected = false;
    public static volatile long connectedAt = 0L;
    private static volatile long lastRebindAttemptAt = 0L;
    private Store store;
    private final DecisionEngine engine = new DecisionEngine();

    public static void recheckAndRebind(android.content.Context context) {
        long now = System.currentTimeMillis();
        if (FocusUtils.hasNotificationAccess(context) && !connected && now - lastRebindAttemptAt > 30000L) {
            lastRebindAttemptAt = now;
            try { NotificationListenerService.requestRebind(new ComponentName(context, FocusListenerService.class)); }
            catch (RuntimeException ignored) { }
        }
    }

    @Override public void onCreate() {
        super.onCreate();
        store = new Store(this);
    }

    @Override public void onListenerConnected() {
        super.onListenerConnected();
        connected = true;
        connectedAt = System.currentTimeMillis();
        Log.d("AttentionTwin", "listener connected");
    }

    @Override public void onListenerDisconnected() {
        connected = false;
        connectedAt = 0L;
        Log.d("AttentionTwin", "listener disconnected; requesting rebind");
        requestRebind(new ComponentName(this, FocusListenerService.class));
        super.onListenerDisconnected();
    }

    @Override public void onDestroy() {
        connected = false;
        if (store != null) store.close();
        super.onDestroy();
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            processNotification(sbn);
        } catch (Throwable error) {
            // Notification interception must fail open. In particular, never cancel or snooze
            // from the recovery path when a DB, parser, or engine unexpectedly fails.
            Log.e("AttentionTwin", "notification processing failed open", error);
        }
    }

    private void processNotification(StatusBarNotification sbn) {
        if (sbn == null) return;
        Notification notification = sbn.getNotification();
        String rawPackage = sbn.getPackageName();
        long now = System.currentTimeMillis();
        if (store == null || notification == null) { Log.d("AttentionTwin", "posted callback skipped: listener/store unavailable"); return; }
        if (isNonActionable(notification)) { recordCallback(now, rawPackage, "skip: ongoing/service/media notification"); return; }

        Bundle extras = notification.extras;
        boolean isTest = extras != null && extras.getBoolean("at_test", false);
        String packageName = rawPackage;
        if (getPackageName().equals(packageName) && !isTest) { recordCallback(now, rawPackage, "skip: app's own notification"); return; }
        if (isTest) packageName = extras.getString("at_fake_pkg", "com.instagram.android");

        String keyHash = keyHash(sbn.getKey());
        String label = applicationLabel(packageName);
        String title = extras == null ? "" : stringExtra(extras, Notification.EXTRA_TITLE);
        String body = extras == null ? "" : stringExtra(extras, Notification.EXTRA_TEXT);
        String channel = Build.VERSION.SDK_INT >= 26 ? safe(sbn.getNotification().getChannelId()) : "";
        String category = FocusUtils.categoryFor(packageName, label);
        String notificationCategory = safe(notification.category);
        String roleText = FocusUtils.roleFor(this, packageName);
        DecisionEngine.AppRole role;
        try { role = DecisionEngine.AppRole.valueOf(roleText); }
        catch (RuntimeException ignored) { role = DecisionEngine.AppRole.ALLOW; }

        boolean focus = FocusUtils.isFocusActive(this);
        // Allow is the default. Evaluate current focus and role before looking at a saved
        // snooze marker: stale snoozes must never hide a notification after focus ends.
        if (!focus || role == DecisionEngine.AppRole.ALLOW || role == DecisionEngine.AppRole.IMPORTANT) {
            FocusUtils.prefs(this).edit().remove("snooze_release_" + keyHash)
                .remove("snooze_release_once_" + keyHash).apply();
            recordCallback(now, rawPackage, "decision=ALLOW role=" + roleText + " reason=" +
                (!focus ? "Focus is off; notification left alone." : role == DecisionEngine.AppRole.IMPORTANT
                    ? "App is marked Important; notification left alone." : "App is set to Allow; notification left alone."));
            return;
        }

        boolean keepPreviews = FocusUtils.prefs(this).getBoolean("keep_previews", false);
        String urgentWords = FocusUtils.prefs(this).getString("urgent_words", "");
        DecisionEngine.Input input = new DecisionEngine.Input();
        input.packageName = packageName;
        input.category = category;
        input.notificationCategory = notificationCategory;
        input.title = title;
        input.text = body;
        input.urgentWords = urgentWords;
        input.role = DecisionEngine.Role.NORMAL;
        input.focusActive = focus;
        input.keepPreviews = keepPreviews;
        input.minutesToBreak = FocusUtils.minutesToBreak(this);
        boolean vip = FocusUtils.isVip(this, title, body);
        String exceptionReason = DecisionEngine.hardExceptionReason(input);
        if (vip) exceptionReason = "Exception: VIP contact is on your allow list.";
        if (!exceptionReason.isEmpty()) {
            recordCallback(now, rawPackage, "decision=ALLOW role=" + roleText + " reason=" + exceptionReason);
            return;
        }

        if (role == DecisionEngine.AppRole.BLOCK_IN_FOCUS &&
            FocusUtils.prefs(this).getBoolean("snooze_release_once_" + keyHash, false)) {
            FocusUtils.prefs(this).edit().remove("snooze_release_once_" + keyHash).remove("snooze_release_" + keyHash).apply();
            org.json.JSONObject released = store.findByKey(keyHash);
            if (released != null) store.updateState(released.optLong("id"), "RELEASED", 0L, "ALLOW", "Released by your request; this repost was allowed once.");
            recordCallback(now, rawPackage, "decision=ALLOW role=BLOCK_IN_FOCUS reason=release request; allow this repost once");
            return;
        }

        long scheduledRelease = FocusUtils.prefs(this).getLong("snooze_release_" + keyHash, 0L);
        if (role == DecisionEngine.AppRole.BLOCK_IN_FOCUS && scheduledRelease > 0L) {
            if (DecisionEngine.shouldAllowSnoozeReleaseOnce(scheduledRelease, now)) {
                FocusUtils.prefs(this).edit().remove("snooze_release_" + keyHash).apply();
                org.json.JSONObject released = store.findByKey(keyHash);
                if (released != null) store.updateState(released.optLong("id"), "RELEASED", 0L, "ALLOW", "The snoozed notification returned at its scheduled release and was allowed once.");
                recordCallback(now, rawPackage, "decision=ALLOW reason=snooze release; allow once");
                return;
            }
            boolean snoozedAgain = false;
            try { snoozeNotification(sbn.getKey(), scheduledRelease - now); snoozedAgain = true; }
            catch (RuntimeException error) { try { cancelNotification(sbn.getKey()); } catch (RuntimeException ignored) { } }
            recordCallback(now, rawPackage, "action=" + (snoozedAgain ? "SNOOZE" : "CANCEL") + " result=retained early re-post");
            return;
        }

        DecisionEngine.Result result;
        long releaseAt = 0L;

        // Blocked apps are snoozed before database writes or cost/history work.
        if (focus && role == DecisionEngine.AppRole.BLOCK_IN_FOCUS && exceptionReason.isEmpty()) {
            releaseAt = FocusUtils.nextReleaseAt(this);
            boolean snoozed = false;
            try { snoozeNotification(sbn.getKey(), releaseAt - now); snoozed = true; }
            catch (RuntimeException error) {
                try { cancelNotification(sbn.getKey()); }
                catch (RuntimeException cancelError) { }
            }
            // A BLOCK role is deterministic; learned estimates cannot change this action.
            result = new DecisionEngine.Result(DecisionEngine.Action.DEFER,
                "This app is set to Block in focus.", 0.0, 0.0, 0.0, 0.0, 0.0, 0, false);
            org.json.JSONObject existing = store.findByKey(keyHash);
            String decisionReason = "This app is set to Block in focus.";
            if (!snoozed) decisionReason += " Android snooze failed; notification was cancelled as fallback.";
            FocusUtils.prefs(this).edit().putLong("snooze_release_" + keyHash, releaseAt).apply();
            if (existing == null || !"HELD".equals(existing.optString("state"))) {
                Store.Event held = makeEvent(keyHash, packageName, label, category, roleText, channel, now, true, "DEFER", "HELD", decisionReason, result, releaseAt, isTest ? "test" : "real", null);
                try { long id = store.insert(held); store.incrementShown(packageName, held.source); }
                catch (RuntimeException error) { Log.d("AttentionTwin", "database write failed after block action"); }
            } else store.updateState(existing.optLong("id"), "HELD", releaseAt, "DEFER", decisionReason);
            recordCallback(now, rawPackage, "decision=DEFER role=BLOCK_IN_FOCUS reason=" + decisionReason + " action=" + (snoozed ? "SNOOZE" : "CANCEL fallback"));
            return;
        }
        org.json.JSONObject existing = store.findByKey(keyHash);
        if (existing != null && now - existing.optLong("ts", 0L) < 4000L) {
            recordCallback(now, rawPackage, "decision=DEDUPED role=" + roleText + " reason=same notification key within 4 seconds");
            return;
        }
        input.stats = store.stats(packageName, isTest ? "test" : "real");
        result = engine.evaluateForAppRoleSafely(input, role);
        if (!exceptionReason.isEmpty()) result = new DecisionEngine.Result(DecisionEngine.Action.ALLOW, exceptionReason, result.openRateEstimate, result.importanceEstimate, result.allowCost, result.deferCost, result.suppressCost, result.observations, true);
        DecisionEngine.Action action = result.action;
        String reason = result.reason;
        if (action == DecisionEngine.Action.SUPPRESS && !keepPreviews) {
            action = DecisionEngine.Action.DEFER;
            reason = "Suppression requires Keep previews; safely falling back to deferral. " + reason;
        }
        if (action == DecisionEngine.Action.DEFER) {
            releaseAt = FocusUtils.nextReleaseAt(this);
            if (Build.VERSION.SDK_INT < 26 || releaseAt == Long.MAX_VALUE || releaseAt <= now) {
                action = DecisionEngine.Action.ALLOW;
                reason = "No usable break time is configured; letting the notification through. " + reason;
                releaseAt = 0L;
            }
        }

        Store.Event event = new Store.Event();
        event.nkey = keyHash;
        event.pkg = packageName;
        event.label = label;
        event.category = category;
        event.role = roleText;
        event.channel = channel;
        event.ts = now;
        event.focus = focus;
        event.action = action.name();
        event.state = action == DecisionEngine.Action.DEFER ? "HELD"
            : action == DecisionEngine.Action.SUPPRESS ? "SUPPRESSED" : "SHOWN";
        event.reason = reason;
        event.pOpen = result.openRateEstimate;
        event.cAllow = result.allowCost;
        event.cDefer = result.deferCost;
        event.cSuppress = result.suppressCost;
        event.importance = result.importanceEstimate;
        event.releaseAt = releaseAt;
        event.source = isTest ? "test" : "real";
        event.exception = result.exception ? reason : "";
        if (action == DecisionEngine.Action.SUPPRESS && keepPreviews) event.preview = (title + "\n" + body).trim();
        long id;
        try { id = store.insert(event); }
        catch (RuntimeException error) { recordCallback(now, rawPackage, "decision=" + action + " role=" + roleText + " database write failed; notification left as Android posted it"); return; }
        store.incrementShown(packageName, event.source);

        String actionResult = "no notification removal needed";
        try {
            if (action == DecisionEngine.Action.DEFER) {
                snoozeNotification(sbn.getKey(), releaseAt - now);
                actionResult = "snoozed until " + releaseAt;
            } else if (action == DecisionEngine.Action.SUPPRESS) {
                cancelNotification(sbn.getKey());
                actionResult = "cancelled from shade";
            }
        } catch (RuntimeException error) {
            store.updateState(id, "SHOWN", 0L, "ALLOW", "Android rejected the action; left the notification available.");
            actionResult = "Android rejected action; allowed fallback";
        }
        recordCallback(now, rawPackage, "decision=" + action + " role=" + roleText + " reason=" + reason + " actionResult=" + actionResult);
    }

    private void recordCallback(long at, String pkg, String outcome) {
        FocusUtils.prefs(this).edit().putLong("last_callback_at", at).apply();
        Log.d("AttentionTwin", "posted pkg=" + pkg + " at=" + at + " " + outcome);
    }

    private Store.Event makeEvent(String key, String pkg, String label, String category, String role, String channel, long ts, boolean focus, String action, String state, String reason, DecisionEngine.Result result, long releaseAt, String source, String preview) {
        Store.Event e = new Store.Event(); e.nkey=key; e.pkg=pkg; e.label=label; e.category=category; e.role=role; e.channel=channel; e.ts=ts; e.focus=focus; e.action=action; e.state=state; e.reason=reason; e.pOpen=result.openRateEstimate; e.cAllow=result.allowCost; e.cDefer=result.deferCost; e.cSuppress=result.suppressCost; e.importance=result.importanceEstimate; e.releaseAt=releaseAt; e.source=source; e.preview=preview; e.exception=result.exception?reason:""; return e;
    }

    @Override public void onNotificationRemoved(StatusBarNotification sbn, RankingMap rankingMap, int reason) {
        if (sbn == null || store == null) return;
        String hash = keyHash(sbn.getKey());
        org.json.JSONObject event = store.findByKey(hash);
        if (event == null) return;
        if (reason == NotificationListenerService.REASON_CLICK) {
            store.markOpenedByKey(hash);
            return;
        }
        if ("SUPPRESSED".equals(event.optString("state")) || reason == NotificationListenerService.REASON_SNOOZED) return;
    }

    private boolean isNonActionable(Notification notification) {
        int flags = notification.flags;
        if ((flags & Notification.FLAG_ONGOING_EVENT) != 0 || (flags & Notification.FLAG_NO_CLEAR) != 0) return true;
        if (Build.VERSION.SDK_INT >= 26 && notification.getChannelId() == null) return true;
        String category = notification.category;
        return Notification.CATEGORY_SERVICE.equals(category)
            || Notification.CATEGORY_TRANSPORT.equals(category)
            || Notification.CATEGORY_CALL.equals(category)
            || Notification.CATEGORY_ALARM.equals(category)
            || Notification.CATEGORY_NAVIGATION.equals(category);
    }

    private String applicationLabel(String packageName) {
        try {
            android.content.pm.ApplicationInfo info = getPackageManager().getApplicationInfo(packageName, 0);
            CharSequence label = getPackageManager().getApplicationLabel(info);
            return label == null ? packageName : label.toString();
        } catch (Exception ignored) { return packageName; }
    }

    private static String stringExtra(Bundle extras, String key) {
        Object value = extras.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    static String keyHash(String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < 16; i++) out.append(String.format(Locale.ROOT, "%02x", digest[i]));
            return out.toString();
        } catch (Exception ignored) { return Integer.toHexString(key.hashCode()); }
    }

    private static String safe(String value) { return value == null ? "" : value; }
}
