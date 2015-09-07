package org.moontools.gpse2pebble;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Environment;
import android.util.Log;

import com.luckycatlabs.sunrisesunset.SunriseSunsetCalculator;
import com.luckycatlabs.sunrisesunset.dto.Location;

import java.io.File;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

/**
 * Created by gesawtsc on 01.09.2015.
 */
public class DataSource {
    private static final int DAY = 1;
    private static final int NIGHT = 0;

    private static final String LOG_TAG = "gpsetest";
    int daynight = DAY;
    int daynightLength = 0;
    int daynightRemaining = 0;
    long timeLastTrack = 0;
    double lat = 0.0;
    double lng = 0.0;
    String sunrise = "";
    String sunset = "";
    String alt = "";
    double distance = 0.0;

    public double getLat() {
        return lat;
    }

    public int getDaynight() {
        return daynight;
    }

    public int getDaynightLength() {
        return daynightLength;
    }

    public int getDaynightRemaining() {
        return daynightRemaining;
    }

    public void setLat(double lat) {
        this.lat = lat;
    }

    public double getLng() {
        return lng;
    }

    public void setLng(double lng) {
        this.lng = lng;
    }

    public String getSunrise() {
        return sunrise;
    }

    public void setSunrise(String sunrise) {
        this.sunrise = sunrise;
    }

    public String getSunset() {
        return sunset;
    }

    public void setSunset(String sunset) {
        this.sunset = sunset;
    }

    public String getAlt() {
        return alt;
    }

    public void setAlt(String alt) {
        this.alt = alt;
    }

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }

    public long getTimeLastTrack() {
        return timeLastTrack;
    }

    public void setTimeLastTrack(long timeLastTrack) {
        this.timeLastTrack = timeLastTrack;
    }

    public void retrieve() {

        readDB();

        calculateSunriseSunset();

    }

    private void calculateSunriseSunset() {

        Location loc = new Location(lat, lng);
        Log.d("gpseTest", "default timezone " + TimeZone.getDefault().getID());
        Calendar c = Calendar.getInstance();
        c.setTime(new Date());
        Log.d("gpseTest", "time " + timeLastTrack);
        Log.d("gpseTest", "c = " + c.getTime().getTime());
        Log.d("gpseTest", "lat " + lat);
        Log.d("gpseTest", "lng = " + lng);
        SunriseSunsetCalculator calc = new SunriseSunsetCalculator(loc, TimeZone.getDefault().getID());
        sunrise = calc.getOfficialSunriseForDate(c);
        sunset = calc.getOfficialSunsetForDate(c);
        long startEvent = calc.getOfficialSunriseCalendarForDate(c).getTime().getTime();
        long endEvent = calc.getOfficialSunsetCalendarForDate(c).getTime().getTime();
        daynight = DAY;
        debug("current="+c.getTime().getTime());

        if(c.getTime().getTime() > endEvent || c.getTime().getTime() < startEvent) {
            daynight = NIGHT;
            c.add(Calendar.DAY_OF_MONTH, 1);
            startEvent = endEvent;
            endEvent = calc.getOfficialSunriseCalendarForDate(c).getTime().getTime();
        }

        debug("startEvent="+startEvent);
        debug("endEvent="+endEvent);

        daynightLength = (int)(( endEvent - startEvent ) / 1000 / 60);
        if(0 > daynightLength) { daynightLength = 0; };
        daynightRemaining = (int)((endEvent - new Date().getTime()) / 1000 / 60);
        debug("daynightLength="+daynightLength);
        debug("daynightRemaining="+daynightRemaining);

    }

    SQLiteDatabase db = null;


    private void readDB() {
        SQLiteDatabase db = getDB();
        if(null == db) {
            return;
        }

        try {
            int lastId = -1;
            String trackCategory = "";
            int trackStream = 0;
            Cursor cursor = db.rawQuery("select max(_id) from Node where tag like 'track:android:%'", null);
            if(cursor.moveToNext()) {
                lastId = cursor.getInt(0);
            } else {
                debug( "no tracks!");
            }
            if(lastId > 0) {
                debug( "last Id = " + lastId);
                //Last Tracking point
                cursor = db.rawQuery("select alt, time, lng, lat, category, stream from Node where _id = ?", new String[] {lastId+""});
                if(cursor.moveToNext()) {
                    alt = cursor.getString(0);
                    timeLastTrack = cursor.getLong(1);
                    debug( "last time = " + timeLastTrack);
                    lng = cursor.getDouble(2);
                    lat = cursor.getDouble(3);
                    trackCategory = cursor.getString(4);
                    trackStream = cursor.getInt(5);
                } else {
                    debug( "no last trac read!");
                }
            } else {
                debug( "lastId is unknown!");
            }

            debug( "sum tracks for category [" + trackCategory + "] and stream ["+ trackStream + "]");
            //sum distance collected today
            final String query = String.format("select sum(distance), count(1) cnt from Node where category = ? and stream = %d " +
                    "and distance is not null and tag like 'track:android:%%'", trackStream);
            debug( query);
            cursor = db.rawQuery(query, new String[] {trackCategory});
            if(cursor.moveToNext()) {
                distance = cursor.getDouble(0);
                int cnt = cursor.getInt(1);
                debug( "distance = " + distance);
                debug( "count = " + cnt);
            } else {
                debug( "no last trac read!");
            }


        } catch(Exception e) {
            Log.e(LOG_TAG, "Exception while retrieving data" + e.getMessage());
        }

    }

    private SQLiteDatabase getDB() {
        if(db == null ) {
            if(isExternalStorageReadable()) {
                try {
                    db = SQLiteDatabase.openDatabase(getDatabaseFile().toString(), null, SQLiteDatabase.OPEN_READONLY);
                } catch(Exception e) {
                    Log.e(LOG_TAG, "Database could not open " + e.getMessage());
                }
            }
        }
        return db;
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

    /* Checks if external storage is available for read and write */
    private boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    /* Checks if external storage is available to at least read */
    private boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            return true;
        }
        return false;
    }

    private File getDatabaseFile() {
        // Get the directory for the user's public pictures directory.
        File file = new File(Environment.getExternalStorageDirectory(),
                new File(
                        new File("com.mictale.gpsessentials", "databases").toString(),
                            "gpse.db").toString());
        if (!file.mkdirs()) {
            Log.e(LOG_TAG, "Directory not created");
        }
        debug( "Open database " + file + " canRead = " + file.canRead());
        return file;
    }

    private void debug(String msg) {
        Log.d(LOG_TAG, msg);
    }
}
