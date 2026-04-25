package com.aircursor;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.graphics.Color;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i("AIRCURSOR", "=== MainActivity started ===");

        TextView tv = new TextView(this);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(20);
        tv.setBackgroundColor(Color.BLACK);
        setContentView(tv);

        // Overlay izni gerekmez — TYPE_ACCESSIBILITY_OVERLAY kullanıyoruz
        // Sadece accessibility iznini kontrol et
        startCursorService();
        tv.setText("AirCursor Hazır\nPort: 9876\n\nErişilebilirlik iznini açık tutun.");
        tv.postDelayed(this::finish, 2000);
    }

    private void startCursorService() {
        try {
            startService(new Intent(this, CursorService.class));
            Log.i("AIRCURSOR", "=== CursorService started ===");
        } catch (Exception e) {
            Log.e("AIRCURSOR", "Service error: " + e.getMessage());
        }
    }
}
