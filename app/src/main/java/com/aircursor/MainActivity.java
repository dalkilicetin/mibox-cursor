package com.aircursor;

import android.app.Activity;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.TextView;
import android.graphics.Color;

public class MainActivity extends Activity {

    private static final int OVERLAY_PERMISSION_REQ = 1234;
    private TextView tv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.e("AIRCURSOR", "=== MAINACTIVITY STARTED ===");

        tv = new TextView(this);
        tv.setText("AIRCURSOR\nPort: 9876");
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(24);
        tv.setBackgroundColor(Color.BLACK);
        tv.setPadding(40, 40, 40, 40);
        setContentView(tv);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!checkOverlayPermission()) {
                tv.setText("Lutfen izin verin!\n\nAcilan ekranda\n'Diger uygulamalarin uzerine ciz'\nizni verin.\n\nSonra geri donun.");
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
            tv.setText("Izin alindi!\nBaslatiliyor...");
            tv.setTextColor(Color.GREEN);
            // 500ms bekle - sistem izin tablosunu guncellesin
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                startCursorService();
            }, 500);
        }
    }

    private boolean checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Once standart kontrol
            if (Settings.canDrawOverlays(this)) return true;
            // TV bug icin AppOpsManager ile kontrol
            try {
                AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
                int mode = appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
                    android.os.Process.myUid(),
                    getPackageName()
                );
                return mode == AppOpsManager.MODE_ALLOWED;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    private void startCursorService() {
        try {
            startService(new Intent(this, CursorService.class));
            Log.e("AIRCURSOR", "=== SERVICE STARTED ===");
            tv.setText("AIRCURSOR HAZIR\nPort: 9876\n\nBaglanti bekleniyor...");
            tv.setTextColor(Color.GREEN);
            // 2 saniye sonra kapat
            tv.postDelayed(this::finish, 2000);
        } catch (Exception e) {
            Log.e("AIRCURSOR", "SERVICE ERROR: " + e.getMessage());
            tv.setText("HATA: " + e.getMessage());
            tv.setTextColor(Color.RED);
        }
    }
}
