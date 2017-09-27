package org.moontools.gpse2pebble;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class StartScreen extends Activity {
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start_screen);
        ((Button) findViewById(R.id.button_start)).setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                StartScreen.this.debug("about to start the service!");
                Intent intent = new Intent(StartScreen.this.getApplicationContext(), Gpse2PebbleService.class);
                intent.putExtra("action", "start");
                StartScreen.this.startService(intent);
            }
        });
        ((Button) findViewById(R.id.button_stop)).setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                StartScreen.this.debug("about to start the service!");
                Intent intent = new Intent(StartScreen.this.getApplicationContext(), Gpse2PebbleService.class);
                intent.putExtra("action", "stop");
                StartScreen.this.startService(intent);
            }
        });
        ((Button) findViewById(R.id.button_status)).setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                StartScreen.this.debug("check status tracking");
                Intent i = new Intent();
                i.setAction("com.google.android.gms.wearable.BIND_LISTENER");
                i.setComponent(new ComponentName("com.gpsessentials.wear", "com.gpsessentials.wear.AppListenerService"));
                i.putExtra("path", "/command/Tracking/status");
                i.putExtra("content", "1");
                StartScreen.this.debug("result = " + StartScreen.this.startService(i));
            }
        });
        ((Button) findViewById(R.id.button_pause)).setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                StartScreen.this.debug("pause tracking");
                Intent i = new Intent();
                i.setAction("com.google.android.gms.wearable.BIND_LISTENER");
                i.setComponent(new ComponentName("com.mictale.gpsessentials", "com.gpsessentials.wear.AppListenerService"));
                i.putExtra("path", "/command/Tracking/pause");
                i.putExtra("content", "1");
                StartScreen.this.debug("result = " + StartScreen.this.startService(i));
            }
        });
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_start_screen, menu);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void debug(String msg) {
        Log.d("StartScreen", msg);
    }
}