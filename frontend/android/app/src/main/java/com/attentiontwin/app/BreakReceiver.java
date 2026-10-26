package com.attentiontwin.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public final class BreakReceiver extends BroadcastReceiver {
    private static final String CHANNEL = "attentiontwin_breaks";
    @Override public void onReceive(Context context, Intent intent) {
        String action = intent == null ? "" : intent.getAction();
        long now = System.currentTimeMillis();
        FocusUtils.clearExpiredManualPause(context);
        FocusUtils.releaseExpiredSnoozeMarkers(context, now);
        int due = 0;
        try (Store store = new Store(context)) { due = store.countDueHeld(now); store.releaseExpired(now); }
        if ("com.attentiontwin.BREAK".equals(action) && due > 0) postDigest(context, due);
        DndController.reconcile(context);
        FocusScheduler.scheduleNext(context);
    }

    private void postDigest(Context context, int held) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(new NotificationChannel(CHANNEL, "AttentionTwin breaks", NotificationManager.IMPORTANCE_DEFAULT));
        }
        Intent open = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        PendingIntent content = open == null ? null : PendingIntent.getActivity(context, 0, open,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(context, CHANNEL) : new Notification.Builder(context);
        builder.setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Your break is here")
            .setContentText(held + " held notification" + (held == 1 ? " is" : "s are") + " scheduled to return now.")
            .setAutoCancel(true);
        if (content != null) builder.setContentIntent(content);
        try { manager.notify(7812, builder.build()); } catch (SecurityException ignored) { }
    }
}
