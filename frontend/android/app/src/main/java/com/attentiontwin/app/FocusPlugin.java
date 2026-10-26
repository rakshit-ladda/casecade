package com.attentiontwin.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import java.util.Calendar;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

@CapacitorPlugin(name = "Focus", permissions = {
    @Permission(alias = "postNotifications", strings = {Manifest.permission.POST_NOTIFICATIONS})
})
public final class FocusPlugin extends Plugin {
    private static final String FOCUS_CHANNEL = "attentiontwin_focus";
    private static final String RESTORE_CHANNEL = "attentiontwin_restored";
    private final DecisionEngine engine = new DecisionEngine();

    @PluginMethod public void getState(PluginCall call) {
        FocusUtils.clearExpiredManualPause(getContext());
        FocusUtils.releaseExpiredSnoozeMarkers(getContext(), System.currentTimeMillis());
        FocusListenerService.recheckAndRebind(getContext());
        DndController.reconcile(getContext());
        JSObject result = new JSObject();
        long until = FocusUtils.focusUntil(getContext());
        result.put("accessGranted", FocusUtils.hasNotificationAccess(getContext()));
        result.put("listenerConnected", FocusListenerService.connected);
        result.put("listenerConnectedAt", FocusListenerService.connectedAt);
        result.put("lastCallbackAt", FocusUtils.prefs(getContext()).getLong("last_callback_at", 0L));
        result.put("focusActive", FocusUtils.isFocusActive(getContext()));
        result.put("paused", FocusUtils.prefs(getContext()).getBoolean("paused", false));
        result.put("focusUntil", until == Long.MAX_VALUE ? -1L : until);
        long nextBreak = FocusUtils.nextBreakAt(getContext());
        result.put("nextBreakAt", nextBreak == Long.MAX_VALUE ? 0L : nextBreak);
        PowerManager power = (PowerManager) getContext().getSystemService(android.content.Context.POWER_SERVICE);
        result.put("batteryOptimizationIgnored", Build.VERSION.SDK_INT < 23 || (power != null && power.isIgnoringBatteryOptimizations(getContext().getPackageName())));
        result.put("manufacturer", Build.MANUFACTURER == null ? "Unknown" : Build.MANUFACTURER);
        boolean dndAccess = DndController.hasAccess(getContext());
        result.put("dndPolicyAccess", dndAccess);
        NotificationManager notifications = (NotificationManager) getContext().getSystemService(android.content.Context.NOTIFICATION_SERVICE);
        boolean dndActive = dndAccess && notifications != null && notifications.getCurrentInterruptionFilter() == NotificationManager.INTERRUPTION_FILTER_PRIORITY;
        result.put("dndSilencing", FocusUtils.prefs(getContext()).getBoolean("silence_alerts", false) && dndActive && FocusUtils.isFocusActive(getContext()));
        result.put("autostartState", "Android does not expose this device-specific permission; check Autostart settings.");
        try (Store store = new Store(getContext())) { store.releaseExpired(System.currentTimeMillis()); result.put("today", store.summaryToday()); result.put("seenLast10m", store.countSince(System.currentTimeMillis() - 10L * 60L * 1000L, null)); }
        call.resolve(result);
    }

    @PluginMethod public void setFocus(PluginCall call) {
        int minutes = call.getInt("minutes", 0);
        long until;
        if (minutes == -1) until = Long.MAX_VALUE;
        else if (minutes <= 0) until = 0L;
        else until = System.currentTimeMillis() + Math.min(minutes, 12 * 60) * 60000L;
        android.content.SharedPreferences.Editor editor = FocusUtils.prefs(getContext()).edit().putLong("focus_until", until);
        if (minutes <= 0) {
            long resumeAt = FocusUtils.nextFocusWindowStartAt(getContext());
            editor.putLong("focus_paused_until", resumeAt == Long.MAX_VALUE ? Long.MAX_VALUE : resumeAt);
        } else editor.remove("focus_paused_until");
        editor.apply();
        FocusScheduler.scheduleFocusEnd(getContext());
        DndController.reconcile(getContext());
        JSObject result = new JSObject();
        result.put("focusActive", FocusUtils.isFocusActive(getContext()));
        result.put("focusUntil", until == Long.MAX_VALUE ? -1L : until);
        call.resolve(result);
    }

    @PluginMethod public void setPaused(PluginCall call) {
        boolean paused = call.getBoolean("paused", true);
        FocusUtils.prefs(getContext()).edit().putBoolean("paused", paused).apply();
        DndController.reconcile(getContext());
        FocusScheduler.scheduleNext(getContext());
        call.resolve(new JSObject().put("paused", paused).put("focusActive", FocusUtils.isFocusActive(getContext())));
    }

    @PluginMethod public void restoreDndNow(PluginCall call) {
        DndController.restoreNow(getContext());
        call.resolve(new JSObject().put("restored", true));
    }

    @PluginMethod public void setSettings(PluginCall call) {
        String breaks = call.getString("breakTimes", "").trim();
        String focusSchedule = call.getString("focusSchedule", "").trim();
        String urgentWords = call.getString("urgentWords", "").trim();
        String vipContacts = call.getString("vipContacts", "").trim();
        boolean keepPreviews = call.getBoolean("keepPreviews", false);
        boolean silenceAlerts = call.getBoolean("silenceAlerts", false);
        if (!validTimes(breaks, 12, false)) { call.reject("Use up to 12 break times such as 10:30,13:00,15:30"); return; }
        if (!validWindows(focusSchedule, 8)) { call.reject("Use focus windows such as 09:00-12:00,13:00-17:00"); return; }
        if (urgentWords.length() > 500 || vipContacts.length() > 500) { call.reject("Urgent words and VIP contacts are limited to 500 characters"); return; }
        android.content.SharedPreferences prefs = FocusUtils.prefs(getContext());
        android.content.SharedPreferences.Editor editor = prefs.edit()
            .putString("break_times", breaks)
            .putString("focus_schedule", focusSchedule)
            .putString("urgent_words", urgentWords)
            .putString("vip_contacts", vipContacts)
            .putBoolean("keep_previews", keepPreviews)
            .putBoolean("silence_alerts", silenceAlerts);
        long pauseUntil = prefs.getLong("focus_paused_until", 0L);
        if (pauseUntil == Long.MAX_VALUE && prefs.getLong("focus_until", 0L) == 0L) {
            editor.putLong("focus_paused_until", FocusUtils.nextFocusWindowStartAt(getContext()));
        }
        editor.apply();
        FocusScheduler.scheduleNext(getContext());
        DndController.reconcile(getContext());
        call.resolve(settingsObject());
    }

    @PluginMethod public void getSettings(PluginCall call) { call.resolve(settingsObject()); }

    @PluginMethod public void getApps(PluginCall call) {
        JSArray result = new JSArray();
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        java.util.List<ResolveInfo> apps = getContext().getPackageManager().queryIntentActivities(launcher, 0);
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        for (ResolveInfo info : apps) {
            String pkg = info.activityInfo == null ? "" : info.activityInfo.packageName;
            if (pkg.isEmpty() || pkg.equals(getContext().getPackageName()) || !seen.add(pkg)) continue;
            String label = info.loadLabel(getContext().getPackageManager()).toString();
            JSObject item = new JSObject();
            item.put("packageName", pkg);
            item.put("label", label);
            item.put("category", FocusUtils.categoryFor(pkg, label));
            item.put("role", FocusUtils.roleFor(getContext(), pkg));
            result.put(item);
        }
        java.util.ArrayList<JSONObject> sorted = new java.util.ArrayList<>();
        for (int i=0;i<result.length();i++) sorted.add(result.optJSONObject(i));
        java.util.Collections.sort(sorted, (a,b) -> Integer.compare(priority(a), priority(b)));
        JSArray ordered = new JSArray(); for (JSONObject item : sorted) ordered.put(item);
        call.resolve(new JSObject().put("apps", ordered));
    }

    @PluginMethod public void setRole(PluginCall call) {
        String pkg = call.getString("packageName", "").trim();
        String role = call.getString("role", "SMART").trim().toUpperCase(Locale.ROOT);
        try { DecisionEngine.AppRole.valueOf(role); }
        catch (IllegalArgumentException error) { call.reject("Choose Block in focus, Allow, Smart, or Important"); return; }
        if (pkg.isEmpty() || pkg.equals(getContext().getPackageName())) { call.reject("Choose an installed app"); return; }
        FocusUtils.prefs(getContext()).edit().putString("role_" + pkg, role).apply();
        call.resolve(new JSObject().put("packageName", pkg).put("role", role));
    }

    @PluginMethod public void getEvents(PluginCall call) {
        String state = call.getString("state", "");
        boolean includeDemo = call.getBoolean("includeDemo", false);
        int limit = call.getInt("limit", 100);
        try (Store store = new Store(getContext())) {
            call.resolve(new JSObject().put("events", store.listEvents(state, limit, includeDemo)));
        }
    }

    @PluginMethod public void feedback(PluginCall call) {
        long id = call.getLong("id", -1L);
        String value = call.getString("value", "").trim().toLowerCase(Locale.ROOT);
        if (id < 0 || !("wrong".equals(value) || "important".equals(value) || "not_important".equals(value))) {
            call.reject("Feedback must be wrong, important, or not_important"); return;
        }
        try (Store store = new Store(getContext())) {
            JSONObject result = store.feedback(id, value);
            if (result == null) { call.reject("Event was not found"); return; }
            try { call.resolve(new JSObject(result.toString())); }
            catch (JSONException error) { call.reject("Could not serialize feedback result", error); }
        }
    }

    @PluginMethod public void releaseNow(PluginCall call) {
        int restored = 0;
        int held = 0;
        try (Store store = new Store(getContext())) {
            JSONArray items = store.listEvents("SUPPRESSED", 300, false);
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) continue;
                String preview = item.optString("preview", "");
                if (preview.isEmpty()) continue;
                postRestored(item.optString("label", "App"), preview, item.optLong("id"));
                store.updateState(item.optLong("id"), "RESTORED", 0L, "ALLOW", "User restored this saved preview.");
                restored++;
            }
            JSONArray waiting = store.heldEvents(false);
            held = waiting.length();
            android.content.SharedPreferences.Editor prefs = FocusUtils.prefs(getContext()).edit();
            for (int i = 0; i < waiting.length(); i++) {
                JSONObject item = waiting.optJSONObject(i);
                if (item == null) continue;
                String hash = item.optString("nkey", "");
                if (!hash.isEmpty()) {
                    prefs.putBoolean("snooze_release_once_" + hash, true).remove("snooze_release_" + hash);
                }
                store.updateState(item.optLong("id"), "RELEASED", 0L, "ALLOW",
                    "You asked to release everything. Android will allow the original alert if it is re-posted; snoozes cannot be pulled forward early.");
            }
            prefs.apply();
            if (held > 0) postHeldDigest(held);
        }
        DndController.restoreNow(getContext());
        call.resolve(new JSObject().put("restored", restored).put("held", held)
            .put("message", held > 0 ? held + " held alerts were added to your release digest. Android may still show the original alert at its scheduled break." : "Saved alerts are available again."));
    }

    @PluginMethod public void evaluate(PluginCall call) {
        JSONObject request = call.getData();
        DecisionEngine.Input input = inputFrom(request, "real");
        DecisionEngine.Result result;
        try (Store store = new Store(getContext())) {
            input.stats = store.stats(input.packageName, "real");
            result = engine.evaluate(input);
        }
        call.resolve(resultObject(result));
    }

    @PluginMethod public void sendTest(PluginCall call) {
        String pkg = call.getString("pkg", "com.instagram.android");
        String title = call.getString("title", "Test notification");
        String text = call.getString("text", "AttentionTwin test — this is a local test event.");
        if (Build.VERSION.SDK_INT >= 33 && getContext().checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            call.reject("Allow AttentionTwin notifications first, then send the test again."); return;
        }
        NotificationManager manager = (NotificationManager) getContext().getSystemService(android.content.Context.NOTIFICATION_SERVICE);
        if (manager == null) { call.reject("Notification manager is unavailable"); return; }
        ensureChannel(manager, FOCUS_CHANNEL, "AttentionTwin test", NotificationManager.IMPORTANCE_DEFAULT);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(getContext(), FOCUS_CHANNEL) : new Notification.Builder(getContext());
        builder.setSmallIcon(R.mipmap.ic_launcher).setContentTitle(title).setContentText(text).setCategory(Notification.CATEGORY_MESSAGE).setAutoCancel(true);
        builder.setExtras(new android.os.Bundle());
        builder.getExtras().putBoolean("at_test", true);
        builder.getExtras().putString("at_fake_pkg", pkg);
        try {
            manager.notify(9204, builder.build());
            call.resolve(new JSObject().put("sent", true).put("packageName", pkg));
        } catch (SecurityException error) { call.reject("Android denied posting the test notification", error); }
    }

    @PluginMethod public void loadDemoData(PluginCall call) {
        int count = 0;
        try (Store store = new Store(getContext())) {
            store.deleteDemo();
            Random random = new Random(7262026L);
            String[] pkgs = {"com.instagram.android", "com.whatsapp", "com.google.android.gm", "com.google.android.youtube", "org.telegram.messenger", "com.slack"};
            String[] labels = {"Instagram", "WhatsApp", "Gmail", "YouTube", "Telegram", "Slack"};
            String[] categories = {"social", "communication", "communication", "social", "communication", "work"};
            String[] notifTypes = {"social_like", "direct_message", "email", "recommendation", "direct_message", "mention"};
            Calendar base = Calendar.getInstance();
            base.add(Calendar.DAY_OF_YEAR, -14);
            for (int day = 0; day < 14; day++) {
                Calendar date = (Calendar) base.clone();
                date.add(Calendar.DAY_OF_YEAR, day);
                boolean weekday = date.get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY && date.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY;
                int[] hours = weekday ? new int[]{9, 10, 11, 12, 15, 16, 17, 19, 20} : new int[]{10, 12, 15, 18, 20, 21};
                for (int slot = 0; slot < hours.length; slot++) {
                    int appIndex;
                    if (hours[slot] <= 9) appIndex = 2;
                    else if (hours[slot] >= 19) appIndex = random.nextBoolean() ? 0 : 1;
                    else if (hours[slot] >= 10 && hours[slot] <= 13 && weekday) appIndex = new int[]{0, 1, 2, 5}[random.nextInt(4)];
                    else if (hours[slot] >= 15 && hours[slot] <= 18 && weekday) appIndex = new int[]{1, 2, 4, 5}[random.nextInt(4)];
                    else appIndex = random.nextInt(pkgs.length);
                    date.set(Calendar.HOUR_OF_DAY, hours[slot]);
                    date.set(Calendar.MINUTE, random.nextInt(60));
                    date.set(Calendar.SECOND, 0);
                    boolean focus = weekday && ((hours[slot] >= 10 && hours[slot] < 13) || (hours[slot] >= 15 && hours[slot] < 18));
                    String roleName = "com.instagram.android".equals(pkgs[appIndex]) ? "DISTRACTING" : "NORMAL";
                    DecisionEngine.Input input = new DecisionEngine.Input();
                    input.packageName = pkgs[appIndex]; input.category = categories[appIndex]; input.notificationCategory = notifTypes[appIndex];
                    input.role = DecisionEngine.Role.valueOf(roleName); input.focusActive = focus; input.minutesToBreak = 20;
                    input.stats = store.stats(pkgs[appIndex], "demo");
                    DecisionEngine.Result result = engine.evaluate(input);
                    Store.Event event = new Store.Event();
                    event.nkey = "demo-" + day + "-" + slot; event.pkg = pkgs[appIndex]; event.label = labels[appIndex];
                    event.category = categories[appIndex]; event.channel = "demo"; event.ts = date.getTimeInMillis(); event.focus = focus;
                    DecisionEngine.Action demoAction = result.action;
                    String demoReason = result.reason;
                    if (demoAction == DecisionEngine.Action.SUPPRESS && !FocusUtils.prefs(getContext()).getBoolean("keep_previews", false)) {
                        demoAction = DecisionEngine.Action.DEFER;
                        demoReason = "Demo rule: suppression falls back to deferral because Keep previews is off.";
                    }
                    event.action = demoAction.name(); event.reason = demoReason; event.pOpen = result.openRateEstimate;
                    event.cAllow = result.allowCost; event.cDefer = result.deferCost; event.cSuppress = result.suppressCost;
                    event.importance = result.importanceEstimate; event.source = "demo"; event.state = demoAction == DecisionEngine.Action.DEFER ? "HELD" : demoAction == DecisionEngine.Action.SUPPRESS ? "SUPPRESSED" : "SHOWN";
                    long id = store.insert(event); store.incrementShown(event.pkg, "demo");
                    if (random.nextDouble() < result.openRateEstimate) {
                        store.markDemoOpened(id); store.incrementOpened(event.pkg, "demo");
                    }
                    if (count % 11 == 4) store.feedback(id, "important");
                    else if (count % 13 == 7) store.feedback(id, "not_important");
                    count++;
                }
            }
        }
        call.resolve(new JSObject().put("count", count).put("source", "demo"));
    }

    @PluginMethod public void clearDemoData(PluginCall call) {
        try (Store store = new Store(getContext())) { store.deleteDemo(); }
        call.resolve();
    }

    @PluginMethod public void clearData(PluginCall call) {
        FocusUtils.prefs(getContext()).edit().putLong("focus_until", 0L).apply();
        DndController.reconcile(getContext());
        try (Store store = new Store(getContext())) { store.deleteAll(); }
        FocusUtils.prefs(getContext()).edit().clear().apply();
        FocusScheduler.scheduleNext(getContext());
        call.resolve();
    }

    @PluginMethod public void openAccessSettings(PluginCall call) {
        Intent detail = new Intent("android.settings.NOTIFICATION_LISTENER_DETAIL_SETTINGS");
        detail.putExtra("android.provider.extra.NOTIFICATION_LISTENER_COMPONENT_NAME", new ComponentName(getContext(), FocusListenerService.class).flattenToString());
        try { getActivity().startActivity(detail); call.resolve(); return; }
        catch (Exception ignored) { }
        try { getActivity().startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); call.resolve(); }
        catch (Exception error) { call.reject("Could not open notification access settings", error); }
    }

    @PluginMethod public void openAppSettings(PluginCall call) {
        openIntent(call, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getContext().getPackageName())));
    }

    @PluginMethod public void openBatterySettings(PluginCall call) {
        openIntent(call, new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
    }

    @PluginMethod public void openDndSettings(PluginCall call) {
        openIntent(call, new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
    }

    @PluginMethod public void openAutostart(PluginCall call) {
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"));
            getActivity().startActivity(intent);
            call.resolve();
        } catch (Exception ignored) {
            openIntent(call, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getContext().getPackageName())));
        }
    }

    @PluginMethod public void requestPostPermission(PluginCall call) {
        if (Build.VERSION.SDK_INT < 33 || getContext().checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            call.resolve(new JSObject().put("granted", true)); return;
        }
        requestPermissionForAlias("postNotifications", call, "postPermissionResult");
    }

    @PermissionCallback private void postPermissionResult(PluginCall call) {
        boolean granted = Build.VERSION.SDK_INT < 33 || getContext().checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        call.resolve(new JSObject().put("granted", granted));
    }

    private void openIntent(PluginCall call, Intent intent) {
        try { getActivity().startActivity(intent); call.resolve(); }
        catch (Exception error) { call.reject("Could not open the Android setting", error); }
    }

    private JSObject settingsObject() {
        android.content.SharedPreferences prefs = FocusUtils.prefs(getContext());
        return new JSObject().put("breakTimes", prefs.getString("break_times", "10:30,13:00,15:30"))
            .put("focusSchedule", prefs.getString("focus_schedule", ""))
            .put("keepPreviews", prefs.getBoolean("keep_previews", false))
            .put("urgentWords", prefs.getString("urgent_words", ""))
            .put("vipContacts", prefs.getString("vip_contacts", ""))
            .put("silenceAlerts", prefs.getBoolean("silence_alerts", false));
    }

    private int priority(JSONObject item) {
        if (item == null) return 10;
        String pkg = item.optString("packageName", "");
        if ("com.whatsapp".equals(pkg)) return 0;
        if ("com.instagram.android".equals(pkg)) return 1;
        if ("com.google.android.gm".equals(pkg)) return 2;
        return 10;
    }

    private DecisionEngine.Input inputFrom(JSONObject request, String source) {
        DecisionEngine.Input input = new DecisionEngine.Input();
        input.packageName = request.optString("packageName", "");
        input.category = request.optString("category", FocusUtils.categoryFor(input.packageName, ""));
        input.notificationCategory = request.optString("notificationCategory", "");
        input.title = request.optString("title", ""); input.text = request.optString("text", "");
        input.urgentWords = FocusUtils.prefs(getContext()).getString("urgent_words", "");
        input.focusActive = request.optBoolean("focusActive", FocusUtils.isFocusActive(getContext()));
        input.keepPreviews = FocusUtils.prefs(getContext()).getBoolean("keep_previews", false);
        input.minutesToBreak = request.optInt("minutesToBreak", FocusUtils.minutesToBreak(getContext()));
        try { input.role = DecisionEngine.Role.valueOf(request.optString("role", FocusUtils.roleFor(getContext(), input.packageName))); }
        catch (IllegalArgumentException ignored) { input.role = DecisionEngine.Role.NORMAL; }
        return input;
    }

    private JSObject resultObject(DecisionEngine.Result result) {
        return new JSObject().put("action", result.action.name()).put("reason", result.reason)
            .put("openRateEstimate", result.openRateEstimate).put("importanceEstimate", result.importanceEstimate)
            .put("allowCostEstimate", result.allowCost).put("deferCostEstimate", result.deferCost)
            .put("suppressCostEstimate", result.suppressCost).put("observations", result.observations)
            .put("lowConfidence", result.observations < 10).put("exception", result.exception)
            .put("estimateLabel", "rule-based estimate; not validated human behavior");
    }

    private void postRestored(String label, String preview, long id) {
        NotificationManager manager = (NotificationManager) getContext().getSystemService(android.content.Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        ensureChannel(manager, RESTORE_CHANNEL, "Restored notifications", NotificationManager.IMPORTANCE_DEFAULT);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(getContext(), RESTORE_CHANNEL) : new Notification.Builder(getContext());
        builder.setSmallIcon(R.mipmap.ic_launcher).setContentTitle("Restored from " + label).setContentText(preview).setAutoCancel(true);
        try { manager.notify((int) (id & 0x7fffffff), builder.build()); } catch (SecurityException ignored) { }
    }

    private void postHeldDigest(int count) {
        NotificationManager manager = (NotificationManager) getContext().getSystemService(android.content.Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        ensureChannel(manager, RESTORE_CHANNEL, "AttentionTwin updates", NotificationManager.IMPORTANCE_DEFAULT);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(getContext(), RESTORE_CHANNEL) : new Notification.Builder(getContext());
        builder.setSmallIcon(R.mipmap.ic_launcher).setContentTitle("Your focus update")
            .setContentText(count + " notifications were held. Open their apps to catch up; Android controls when snoozed alerts return.")
            .setAutoCancel(true);
        try { manager.notify(9205, builder.build()); } catch (SecurityException ignored) { }
    }

    private static void ensureChannel(NotificationManager manager, String id, String name, int importance) {
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(new NotificationChannel(id, name, importance));
    }

    private static boolean validTimes(String value, int max, boolean allowEmpty) {
        if (value.isEmpty()) return allowEmpty;
        String[] entries = value.split(",");
        if (entries.length > max) return false;
        for (String entry : entries) if (FocusUtils.parseTime(entry) < 0) return false;
        return true;
    }

    private static boolean validWindows(String value, int max) {
        if (value.isEmpty()) return true;
        String[] entries = value.split(",");
        if (entries.length > max) return false;
        for (String entry : entries) {
            if (!Pattern.matches("(?:[01]\\d|2[0-3]):[0-5]\\d-(?:[01]\\d|2[0-3]):[0-5]\\d", entry.trim())) return false;
            String[] parts = entry.trim().split("-");
            if (FocusUtils.parseTime(parts[0]) == FocusUtils.parseTime(parts[1])) return false;
        }
        return true;
    }
}
