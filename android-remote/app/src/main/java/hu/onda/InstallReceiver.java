package hu.onda;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

/** A telepítő visszajelzése. Ha a rendszer megerősítést kér, megnyitja, vagy értesítést tesz ki róla. */
public class InstallReceiver extends BroadcastReceiver {
    @Override
    @SuppressWarnings("deprecation")
    public void onReceive(Context c, Intent i) {
        int st = i.getIntExtra(PackageInstaller.EXTRA_STATUS, -1);
        if (st != PackageInstaller.STATUS_PENDING_USER_ACTION) return;
        Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
        if (confirm == null) return;
        confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { c.startActivity(confirm); } catch (Exception ignored) { }
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel("upd", "Frissítések", NotificationManager.IMPORTANCE_HIGH));
        PendingIntent pi = PendingIntent.getActivity(c, 1, confirm, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        nm.notify(1, new Notification.Builder(c, "upd")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Onda Haptic frissítés")
                .setContentText("Koppints a telepítés befejezéséhez.")
                .setContentIntent(pi).setAutoCancel(true).build());
    }
}
