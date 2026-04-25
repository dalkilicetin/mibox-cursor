package com.aircursor;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.TextView;
import android.graphics.Color;

public class MainActivity extends Activity {

    private static final int OVERLAY_PERMISSION_REQ = 1234;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.e("AIRCURSOR", "=== MAINACTIVITY STARTED ===");

        TextView tv = new TextView(this);
        tv.setText("AIRCURSOR CALISIYOR\nPort: 9999");
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(24);
        tv.setBackgroundColor(Color.BLACK);
        setContentView(tv);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                tv.setText("Lutfen izin verin!\n\nAcilan ekranda\n'Diger uygulamalarin uzerine ciz'\nizni verin.");
                tv.setTextColor(Color.YELLOW);
                Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())
                );
                startActivityForResult(intent, OVERLAY_PERMISSION_REQ);
            } else {
                startCursorService();
            }
        } else {
            startCursorService();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == OVERLAY_PERMISSION_REQ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                startCursorService();
            } else {
                Log.e("AIRCURSOR", "Overlay izni reddedildi!");
            }
        }
    }

    private void startCursorService() {
        try {
            startService(new Intent(this, CursorService.class));
            Log.e("AIRCURSOR", "=== SERVICE STARTED ===");

            TextView tv = new TextView(this);
            tv.setText("AIRCURSOR HAZIR\nPort: 9999\n\nBaglanti bekleniyor...");
            tv.setTextColor(Color.GREEN);
            tv.setTextSize(20);
            tv.setBackgroundColor(Color.BLACK);
            setContentView(tv);

            // 2 saniye sonra kapat
            tv.postDelayed(this::finish, 2000);

        } catch (Exception e) {
            Log.e("AIRCURSOR", "SERVICE ERROR: " + e.getMessage());
        }
    }
}
