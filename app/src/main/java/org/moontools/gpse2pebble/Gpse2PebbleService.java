package org.moontools.gpse2pebble;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.*;
import android.os.Process;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;

import java.util.Date;
import java.util.UUID;

public class Gpse2PebbleService extends Service {

    private static final String LOG_TAG = "Gpse2PebbleService";

    final static int DATE_KEY = 0;
    final static int DIST_KEY = 1;
    final static int ALT_KEY = 2;
    final static int COORDS_KEY = 3;
    final static int SUNRISE_KEY = 4;
    final static int SUNSET_KEY = 5;
    final static int LAST_UPDATE_KEY = 6;
    final static int PHONE_BATTERY_KEY = 7;
    final static int DAYNIGHT_KEY = 8;
    final static int DAYNIGHT_LENGTH_KEY = 9;
    final static int DAYNIGHT_REMAINING_KEY = 10;

    public static final int REFRESH_RATE = 10;

    DataSource mGpseData = new DataSource();

    boolean running = false;

    private final class ServiceHandler extends Handler {

        public ServiceHandler(Looper l) {
            super(l);
        }

        @Override
        public void handleMessage(Message msg) {
            debug("handleMessage " + msg.arg1 + ", running = " + running);
            if(running) {
                //
                stopSelf(msg.arg1);
            } else {
                running = true;

                while (true) {
                    try {
                        Thread.sleep(REFRESH_RATE * 1000);
                        updateContent();
                    } catch (InterruptedException e) {
                        debug("Interrupted!");
                    }
                }
            }
        }
    }

    private Looper mServiceLooper;
    private ServiceHandler mServiceHandler;

    public Gpse2PebbleService() {
    }

    @Override
    public void onCreate() {
        // Start up the thread running the service.  Note that we create a
        // separate thread because the service normally runs in the process's
        // main thread, which we don't want to block.  We also make it
        // background priority so CPU-intensive work will not disrupt our UI.
        HandlerThread thread = new HandlerThread("ServiceStartArguments",
                Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();

        // Get the HandlerThread's Looper and use it for our Handler
        mServiceLooper = thread.getLooper();
        mServiceHandler = new ServiceHandler(mServiceLooper);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        debug("service starting");

        Intent notificationIntent = new Intent(getApplicationContext(), StartScreen.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, notificationIntent, 0);

        Notification notification = new NotificationCompat.Builder(getApplicationContext())
                .setSmallIcon(R.drawable.g2p_icon)
                .setContentTitle("Gpse2Pebble Service")
                .setContentText("Test")
                .setContentIntent(pendingIntent).build();


        //notification.setLatestEventInfo(getApplicationContext(), "Gpse2Pebble",
        //        "Running gpse2pebble Service", pendingIntent);

        //notification.flags |= Notification.FLAG_NO_CLEAR;
        startForeground(1234, notification);
        // For each start request, send a message to start a job and deliver the
        // start ID so we know which request we're stopping when we finish the job
        Message msg = mServiceHandler.obtainMessage();
        msg.arg1 = startId;
        mServiceHandler.sendMessage(msg);

        // If we get killed, after returning from here, restart
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        debug("killed");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void updateContent() {
        mGpseData.retrieve();
        debug("Update content " + mGpseData.getDistance());

        //check Pebble
        boolean isConnected = PebbleKit.isWatchConnected(getApplicationContext());
        debug("connected = " + isConnected);
        if(isConnected) {
            sendToPebble();
        }
    }

    private void sendToPebble() {

        final UUID PEBBLE_APP_UUID = UUID.fromString("01a602e0-937d-4dce-8e4e-aed7295b09f6");

        PebbleDictionary data = new PebbleDictionary();
        // Add a key of 0, and a uint8_t (byte) of value 42.

        //data.addString(DATE_KEY, String.format());
        addBatteryStatusData(data);
        addDistData(data);
        addSunriseSunset(data);
        addLightDayInfo(data);
        addCoords(data);
        data.addString(ALT_KEY, mGpseData.getAlt());

        long diff_time = (new Date().getTime() - mGpseData.getTimeLastTrack()) / 1000;
        if(diff_time > 600) { diff_time = 600; }
        debug("Last Update since " + diff_time);
        data.addUint32(LAST_UPDATE_KEY, (int)diff_time);

        PebbleKit.sendDataToPebble(getApplicationContext(), PEBBLE_APP_UUID, data);
    }

    private void addLightDayInfo(PebbleDictionary data) {
        debug("daynight="+mGpseData.getDaynight());
        debug("daynightLen="+mGpseData.getDaynightLength());
        debug("daynightRem="+mGpseData.getDaynightRemaining());
        data.addUint32(DAYNIGHT_KEY, mGpseData.getDaynight());
        data.addUint32(DAYNIGHT_LENGTH_KEY, mGpseData.getDaynightLength());
        data.addUint32(DAYNIGHT_REMAINING_KEY, mGpseData.getDaynightRemaining());
    }


    private void addBatteryStatusData(PebbleDictionary data) {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = getApplicationContext().registerReceiver(null, ifilter);
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        debug("battery level = " + level);
        debug("battery scale = " + scale);
        data.addInt32(PHONE_BATTERY_KEY, (byte) ((level * 100.0) / (float)scale));
    }

    private void addCoords(PebbleDictionary data) {
        data.addString(COORDS_KEY, String.format("%.5f, %.5f", mGpseData.getLat(), mGpseData.getLng()));
    }

    private void addSunriseSunset(PebbleDictionary data) {
        data.addString(SUNRISE_KEY, mGpseData.getSunrise());
        data.addString(SUNSET_KEY, mGpseData.getSunset());
    }

    private void addDistData(PebbleDictionary data) {
        String format = "%.0fm";
        double d = mGpseData.getDistance();
        if(d >= 1000.0) {
            d /= 1000.0;
            format = "%.1fkm";
        }
        data.addString(DIST_KEY, String.format(format, d));
    }

    private void debug(String msg) {
        Log.d(LOG_TAG, msg);
    }
}
