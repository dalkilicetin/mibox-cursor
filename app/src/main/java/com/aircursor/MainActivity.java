package com.aircursor;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.graphics.Color;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.e("AIRCURSOR", "=== MAINACTIVITY STARTED ===");

        TextView tv = new TextView(this);
        tv.setText("AIRCURSOR CALISIYOR\nPort: 9876");
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(24);
        tv.setBackgroundColor(Color.BLACK);
        setContentView(tv);

        Log.e("AIRCURSOR", "=== UI SET ===");

        // Servisi başlat
        try {
            startService(new android.content.Intent(this, CursorService.class));
            Log.e("AIRCURSOR", "=== SERVICE STARTED ===");
        } catch (Exception e) {
            Log.e("AIRCURSOR", "SERVICE ERROR: " + e.getMessage());
        }
    }
}