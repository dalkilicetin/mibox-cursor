package com.aircursor;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Color;
import android.view.Gravity;

import java.net.NetworkInterface;
import java.util.Collections;

public class MainActivity extends Activity {

    private static final int REQ_OVERLAY = 1001;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Layout oluştur
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setBackgroundColor(Color.parseColor("#1a1a2e"));
        layout.setPadding(60, 60, 60, 60);

        statusText = new TextView(this);
        statusText.setTextColor(Color.WHITE);
        statusText.setTextSize(20);
        statusText.setGravity(Gravity.CENTER);
        statusText.setText("AirCursor\nBaslatiliyor...");

        layout.addView(statusText);
        setContentView(layout);

        checkAndStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkAndStart();
    }

    private void checkAndStart() {
        if (!Settings.canDrawOverlays(this)) {
            statusText.setText("AirCursor\n\nOverlay izni gerekli!\nAcilan ekranda izin ver.");
            Intent intent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName())
            );
            startActivityForResult(intent, REQ_OVERLAY);
        } else {
            startCursorService();
        }
    }

    private void startCursorService() {
        Intent service = new Intent(this, CursorService.class);
        startService(service);

        String ip = getLocalIP();
        statusText.setText(
            "AirCursor AKTIF\n\n" +
            "Cursor servisi calisiyor\n\n" +
            "Bridge baglanti adresi:\n" +
            ip + ":9876\n\n" +
            "Bu ekrani kapatabilirsin"
        );
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_OVERLAY) {
            if (Settings.canDrawOverlays(this)) {
                startCursorService();
            } else {
                statusText.setText("AirCursor\n\nIzin verilmedi!\nUygulama calismiyor.");
            }
        }
    }

    private String getLocalIP() {
        try {
            for (NetworkInterface iface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (java.net.InetAddress addr : Collections.list(iface.getInetAddresses())) {
                    if (!addr.isLoopbackAddress() && addr instanceof java.net.Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) { /* ignore */ }
        return "IP bulunamadi";
    }
}