package org.moontools.gpse2pebble;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.support.v4.app.NotificationCompat.Builder;
import android.util.Log;
import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.PebbleKit.PebbleDataReceiver;
import com.getpebble.android.kit.util.PebbleDictionary;
import java.util.Calendar;
import java.util.Date;
import java.util.UUID;

public class Gpse2PebbleService extends Service {
    static final int ALT_KEY = 2;
    static final int COORDS_KEY = 3;
    static final int DATE_KEY = 0;
    static final int DIST_KEY = 1;
    static final int LAST_UPDATE_KEY = 6;
    private static final String WATCHFACE_UUID = "01a602e0-937d-4dce-8e4e-aed7295b09f6";
    private static final String LOG_TAG = "Gpse2PebbleService";
    private static final UUID PEBBLE_APP_UUID = UUID.fromString(WATCHFACE_UUID);
    static final int PHONE_BATTERY_KEY = 7;
    static final int SUNRISE_NEXT_DAY_KEY = 8;
    static final int SUNRISE_TIME_KEY = 4;
    static final int SUNSET_DAY_BEFORE_KEY = 9;
    static final int SUNSET_TIME_KEY = 5;
    DataSource mGpseData = new DataSource();
    private ServiceHandler mServiceHandler;
    private Looper mServiceLooper;
    PebbleReciver recvPebbleData = new PebbleReciver();
    private boolean running = false;

    private final class ServiceHandler extends Handler {
        public ServiceHandler(Looper l) {
            super(l);
        }

        public void handleMessage(Message msg) {
            Gpse2PebbleService.this.debug("handleMessage " + msg.arg1 + ", running = " + Gpse2PebbleService.this.running);
            PebbleKit.registerReceivedDataHandler(Gpse2PebbleService.this.getApplicationContext(), Gpse2PebbleService.this.recvPebbleData);
            while (Gpse2PebbleService.this.running) {
                Gpse2PebbleService.this.updateContent();
                int sleeping = 60;
                try {
                    Calendar c = Calendar.getInstance();
                    c.set(Calendar.HOUR_OF_DAY, 0);
                    c.set(Calendar.MINUTE, 0);
                    c.set(Calendar.SECOND, 0);
                    c.set(Calendar.MILLISECOND, 0);
                    long current = new Date().getTime();
                    long midnight = c.getTimeInMillis();
                    if (current > midnight && current < 18000000 + midnight) {
                        sleeping = 3540;
                    }
                    Thread.sleep((long) (sleeping * 1000));
                } catch (InterruptedException e) {
                    Gpse2PebbleService.this.debug("Interrupted!");
                }
            }
            try {
                Gpse2PebbleService.this.unregisterReceiver(Gpse2PebbleService.this.recvPebbleData);
            } catch (Exception e2) {
            }
            Gpse2PebbleService.this.stopSelf(msg.arg1);
        }
    }

    private final class PebbleReciver extends PebbleDataReceiver {
        public PebbleReciver() {
            super(Gpse2PebbleService.PEBBLE_APP_UUID);
        }

        public void receiveData(Context context, int transactionId, PebbleDictionary data) {
            Gpse2PebbleService.this.debug("recived some data from pebble!");
            Gpse2PebbleService.this.updateContent();
        }
    }

    public void onCreate() {
        HandlerThread thread = new HandlerThread("ServiceStartArguments", 10);
        thread.start();
        this.mServiceLooper = thread.getLooper();
        this.mServiceHandler = new ServiceHandler(this.mServiceLooper);
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = "start";
        action = intent.getExtras().getString("action");
        debug("service " + action);
        if (!"start".equals(action)) {
            this.running = false;
        } else if (this.running) {
            debug("already running, exit");
            return START_NOT_STICKY;
        } else {
            this.running = true;
        }
        startForeground(1234, new Builder(getApplicationContext()).setSmallIcon(R.drawable.g2p_icon).setContentTitle("Gpse2Pebble Service").setContentText("Test").setContentIntent(PendingIntent.getActivity(getApplicationContext(), 0, new Intent(getApplicationContext(), StartScreen.class), 0)).build());
        Message msg = this.mServiceHandler.obtainMessage();
        msg.arg1 = startId;
        this.mServiceHandler.sendMessage(msg);
        return START_STICKY;
    }

    public void onDestroy() {
        debug("killed");
    }

    public IBinder onBind(Intent intent) {
        return null;
    }

    private void updateContent() {
        this.mGpseData.retrieve();
        debug("Update content " + this.mGpseData.getDistance());
        boolean isConnected = PebbleKit.isWatchConnected(getApplicationContext());
        debug("connected = " + isConnected);
        if (isConnected) {
            sendToPebble();
        }
    }

    private void sendToPebble() {
        PebbleDictionary data = new PebbleDictionary();
        addBatteryStatusData(data);
        addDistData(data);
        addSunriseSunset(data);
        addCoords(data);
        data.addString(2, this.mGpseData.getAlt());
        long diff_time = (new Date().getTime() - this.mGpseData.getTimeLastTrack()) / 1000;
        if (diff_time > 600) {
            diff_time = 600;
        }
        debug("Last Update since " + diff_time);
        data.addUint32(6, (int) diff_time);
        PebbleKit.sendDataToPebble(getApplicationContext(), PEBBLE_APP_UUID, data);
    }

    private void addBatteryStatusData(PebbleDictionary data) {
        Intent batteryStatus = getApplicationContext().registerReceiver(null, new IntentFilter("android.intent.action.BATTERY_CHANGED"));
        int level = batteryStatus.getIntExtra("level", -1);
        int scale = batteryStatus.getIntExtra("scale", -1);
        debug("battery level = " + level);
        debug("battery scale = " + scale);
        data.addInt32(7, (byte) ((int) ((((double) level) * 100.0d) / ((double) ((float) scale)))));
    }

    private void addCoords(PebbleDictionary data) {
        data.addString(3, String.format("%.5f, %.5f", new Object[]{Double.valueOf(this.mGpseData.getLat()), Double.valueOf(this.mGpseData.getLng())}));
    }

    private void addSunriseSunset(PebbleDictionary data) {
        data.addUint32(4, this.mGpseData.getSunriseTime());
        data.addUint32(5, this.mGpseData.getSunsetTime());
        data.addUint32(8, this.mGpseData.getSunriseNextDayTime());
        data.addUint32(9, this.mGpseData.getSunsetDayBeforeTime());
        debug("current seconds " + (86400 - ((new Date().getTime() / 1000) % 86400)));
        debug("send sunrise " + this.mGpseData.getSunriseTime() + "/" + new Date((long) (this.mGpseData.getSunriseTime() * 1000)));
        debug("send sunset " + this.mGpseData.getSunsetTime() + "/" + new Date((long) (this.mGpseData.getSunsetTime() * 1000)));
    }

    private void addDistData(PebbleDictionary data) {
        String format = "%.0fm";
        double d = this.mGpseData.getDistance();
        if (d >= 1000.0d) {
            d /= 1000.0d;
            format = "%.1fkm";
        }
        data.addString(1, String.format(format, new Object[]{Double.valueOf(d)}));
    }

    private void debug(String msg) {
        Log.d(LOG_TAG, msg);
    }
}