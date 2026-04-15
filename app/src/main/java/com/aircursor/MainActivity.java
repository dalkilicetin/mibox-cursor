package com.aircursor;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.util.Collections;

public class MainActivity extends Activity {

    private static final int REQ_OVERLAY = 1001;
    private TextView tv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.parseColor("#1a1a2e"));

        tv = new TextView(this);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(18);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(40, 40, 40, 40);
        tv.setText("AirCursor\nBaslatiliyor...");

        root.addView(tv);
        setContentView(root);

        checkOverlay();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // İzin ekranından dönünce burası tetiklenir
        if (Settings.canDrawOverlays(this)) {
            startCursor();
        }
    }

    private void checkOverlay() {
        if (Settings.canDrawOverlays(this)) {
            startCursor();
        } else {
            tv.setText("AirCursor\n\nOverlay izni gerekli.\nAcilan ekranda izin ver,\nsonra geri don.");
            Intent i = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName())
            );
            startActivity(i);  // startActivityForResult yerine startActivity
        }
    }

    private void startCursor() {
        startService(new Intent(this, CursorService.class));
        String ip = getIP();
        tv.setText("AirCursor AKTIF\n\nPort: 9876\nIP: " + ip + "\n\nBridge: " + ip + ":9876");
    }

    private String getIP() {
        try {
            for (NetworkInterface iface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (java.net.InetAddress addr : Collections.list(iface.getInetAddresses())) {
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return "?";
    }
}