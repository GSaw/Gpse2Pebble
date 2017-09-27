package org.moontools.gpse2pebble;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Environment;
import android.provider.CalendarContract;
import android.util.Log;
import com.luckycatlabs.sunrisesunset.SunriseSunsetCalculator;
import com.luckycatlabs.sunrisesunset.dto.Location;
import java.io.File;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

public class DataSource {
    private static final int DAY = 1;
    private static final String LOG_TAG = "gpsetest";
    private static final int NIGHT = 0;
    String alt = "";
    SQLiteDatabase db = null;
    double distance = 0.0d;
    double lat = 0.0d;
    double lng = 0.0d;
    int sunriseNextDayTime = 0;
    int sunriseTime = 0;
    int sunsetDayBeforeTime = 0;
    int sunsetTime = 0;
    long timeLastTrack = 0;

    public int getSunriseTime() {
        return this.sunriseTime;
    }

    public int getSunsetTime() {
        return this.sunsetTime;
    }

    public double getLat() {
        return this.lat;
    }

    public int getSunriseNextDayTime() {
        return this.sunriseNextDayTime;
    }

    public int getSunsetDayBeforeTime() {
        return this.sunsetDayBeforeTime;
    }

    public void setLat(double lat) {
        this.lat = lat;
    }

    public double getLng() {
        return this.lng;
    }

    public void setLng(double lng) {
        this.lng = lng;
    }

    public String getAlt() {
        return this.alt;
    }

    public void setAlt(String alt) {
        this.alt = alt;
    }

    public double getDistance() {
        return this.distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }

    public long getTimeLastTrack() {
        return this.timeLastTrack;
    }

    public void setTimeLastTrack(long timeLastTrack) {
        this.timeLastTrack = timeLastTrack;
    }

    public void retrieve() {
        readDB();
        calculateSunriseSunset();
    }

    private int getSecondsSinceMidnight(long time) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(time);
        debug("zone_offset " + (c.getTimeZone().getRawOffset() / 1000));
        debug("dst_offset " + (c.getTimeZone().getDSTSavings() / 1000));
        debug("mn " + (time - (time % 86400)));
        int fromMidnight = (int) ((time % 86400) + ((long) ((c.getTimeZone().getRawOffset() + c.getTimeZone().getDSTSavings()) / 1000)));
        debug("event time = " + time + ", midnight = " + fromMidnight + ", d = " + new Date((long) (fromMidnight * 1000)));
        return fromMidnight;
    }

    private void calculateSunriseSunset() {
        Location loc = new Location(this.lat, this.lng);
        Log.d("gpseTest", "default timezone " + TimeZone.getDefault().getID());
        Calendar c = Calendar.getInstance();
        c.setTime(new Date());
        Log.d("gpseTest", "time " + this.timeLastTrack);
        Log.d("gpseTest", "c = " + c.getTime().getTime());
        Log.d("gpseTest", "lat " + this.lat);
        Log.d("gpseTest", "lng = " + this.lng);
        SunriseSunsetCalculator calc = new SunriseSunsetCalculator(loc, TimeZone.getDefault().getID());
        this.sunriseTime = getSecondsSinceMidnight(calc.getOfficialSunriseCalendarForDate(c).getTimeInMillis() / 1000);
        this.sunsetTime = getSecondsSinceMidnight(calc.getOfficialSunsetCalendarForDate(c).getTimeInMillis() / 1000);
        c.add(Calendar.DAY_OF_MONTH, 1);
        this.sunriseNextDayTime = getSecondsSinceMidnight(calc.getOfficialSunriseCalendarForDate(c).getTimeInMillis() / 1000);
        c.add(Calendar.DAY_OF_MONTH, -2);
        this.sunsetDayBeforeTime = getSecondsSinceMidnight(calc.getOfficialSunsetCalendarForDate(c).getTimeInMillis() / 1000);
    }

    private void readDB() {
        SQLiteDatabase db = getDB();
        if (db != null) {
            int lastId = -1;
            try {
                String trackCategory = "";
                int trackStream = 0;
                Cursor cursor = db.rawQuery("select max(_id) from Node where tag like 'track:android:%'", null);
                if (cursor.moveToNext()) {
                    lastId = cursor.getInt(0);
                } else {
                    debug("no tracks!");
                }
                if (lastId > 0) {
                    debug("last Id = " + lastId);
                    cursor = db.rawQuery("select alt, time, lng, lat, category, stream from Node where _id = ?", new String[]{lastId + ""});
                    if (cursor.moveToNext()) {
                        this.alt = cursor.getString(0);
                        this.timeLastTrack = cursor.getLong(1);
                        debug("last time = " + this.timeLastTrack);
                        this.lng = cursor.getDouble(2);
                        this.lat = cursor.getDouble(3);
                        trackCategory = cursor.getString(4);
                        trackStream = cursor.getInt(5);
                    } else {
                        debug("no last trac read!");
                    }
                } else {
                    debug("lastId is unknown!");
                }
                debug("sum tracks for category [" + trackCategory + "] and stream [" + trackStream + "]");
                String query = String.format("select sum(distance), count(1) cnt from Node where category = ? and stream = %d and distance is not null and tag like 'track:android:%%'", new Object[]{Integer.valueOf(trackStream)});
                debug(query);
                cursor = db.rawQuery(query, new String[]{trackCategory});
                if (cursor.moveToNext()) {
                    this.distance = cursor.getDouble(0);
                    int cnt = cursor.getInt(1);
                    debug("distance = " + this.distance);
                    debug("count = " + cnt);
                    return;
                }
                debug("no last trac read!");
            } catch (Exception e) {
                Log.e(LOG_TAG, "Exception while retrieving data" + e.getMessage());
            }
        }
    }

    private SQLiteDatabase getDB() {
        if (this.db == null && isExternalStorageReadable()) {
            try {
                this.db = SQLiteDatabase.openDatabase(getDatabaseFile().toString(), null, 1);
            } catch (Exception e) {
                Log.e(LOG_TAG, "Database could not open " + e.getMessage());
            }
        }
        return this.db;
    }

    private Date getMidnight() {
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return new Date(cal.getTimeInMillis());
    }

    private boolean isExternalStorageWritable() {
        if ("mounted".equals(Environment.getExternalStorageState())) {
            return true;
        }
        return false;
    }

    private boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        if ("mounted".equals(state) || "mounted_ro".equals(state)) {
            return true;
        }
        return false;
    }

    private File getDatabaseFile() {
        File file = new File("/sdcard/Android/data",
                new File(
                        new File("com.mictale.gpsessentials",
                                new File("files", "databases").toString()).toString(),
                        "gpse.db").toString());
        if (!file.mkdirs()) {
            Log.e(LOG_TAG, "Directory not created");
        }
        debug("Open database " + file + " canRead = " + file.canRead());
        return file;
    }

    private void debug(String msg) {
        Log.d(LOG_TAG, msg);
    }
}