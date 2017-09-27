package org.moontools.gpse2pebble;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build.VERSION;
import android.support.v4.app.NotificationCompat.BigTextStyle;
import android.support.v4.app.NotificationCompat.Builder;

public class ServiceNotification {
    private static final String NOTIFICATION_TAG = "Service";

    public static void notify(Context context, String exampleString, int number) {
        Resources res = context.getResources();
        Bitmap picture = BitmapFactory.decodeResource(res, R.drawable.example_picture);
        String ticker = exampleString;
        String title = res.getString(R.string.service_notification_title_template);
        String text = res.getString(R.string.service_notification_placeholder_text_template, new Object[]{exampleString});
        notify(context, new Builder(context).setDefaults(-1).setSmallIcon(R.drawable.g2p_icon).setContentTitle(title).setContentText(text).setPriority(0).setLargeIcon(picture).setTicker(ticker).setNumber(number).setContentIntent(PendingIntent.getActivity(context, 0, new Intent("android.intent.action.VIEW", Uri.parse("http://www.google.com")),  PendingIntent.FLAG_UPDATE_CURRENT)).setStyle(new BigTextStyle().bigText(text).setBigContentTitle(title).setSummaryText("Dummy summary text")).addAction(R.drawable.ic_action_stat_share, res.getString(R.string.action_share), PendingIntent.getActivity(context, 0, Intent.createChooser(new Intent("android.intent.action.SEND").setType("text/plain").putExtra("android.intent.extra.TEXT", "Dummy text"), "Dummy title"), PendingIntent.FLAG_UPDATE_CURRENT)).addAction(R.drawable.ic_action_stat_reply, res.getString(R.string.action_reply), null).setAutoCancel(true).build());
    }

    @TargetApi(5)
    private static void notify(Context context, Notification notification) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (VERSION.SDK_INT >= 5) {
            nm.notify(NOTIFICATION_TAG, 0, notification);
        } else {
            nm.notify(NOTIFICATION_TAG.hashCode(), notification);
        }
    }

    @TargetApi(5)
    public static void cancel(Context context) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (VERSION.SDK_INT >= 5) {
            nm.cancel(NOTIFICATION_TAG, 0);
        } else {
            nm.cancel(NOTIFICATION_TAG.hashCode());
        }
    }
}