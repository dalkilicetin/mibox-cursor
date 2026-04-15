package com.aircursor;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.TextView;
import android.widget.Toast;
import java.net.NetworkInterface;
import java.util.Collections;

public class MainActivity extends Activity {

    private static final int REQ_OVERLAY = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TextView tv = new TextView(this);
        tv.setPadding(40, 40, 40, 40);
        tv.setTextSize(18);
        tv.setText("AirCursor\n\nYükleniyor...");
        setContentView(tv);

        // Overlay izni var mı?
        if (!Settings.canDrawOverlays(this)) {
            tv.setText("AirCursor\n\nOverlay izni gerekli.\nAçılan ekranda izin ver.");
            Intent intent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName())
            );
            startActivityForResult(intent, REQ_OVERLAY);
        } else {
            startCursorService(tv);
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_OVERLAY) {
            TextView tv = (TextView) getContentView();
            if (Settings.canDrawOverlays(this)) {
                startCursorService(tv);
            } else {
                tv.setText("AirCursor\n\n❌ İzin verilmedi.\nUygulama çalışamaz.");
            }
        }
    }

    private android.view.View getContentView() {
        return getWindow().getDecorView().getRootView();
    }

    private void startCursorService(TextView tv) {
        String ip = getLocalIP();
        Intent service = new Intent(this, CursorService.class);
        startService(service);

        tv.setText(
            "AirCursor ✓\n\n" +
            "Cursor servisi çalışıyor.\n\n" +
            "Bridge'de şu adresi kullan:\n" +
            "ws://" + ip + ":9876\n\n" +
            "Bu ekranı kapatabilirsin,\n" +
            "servis arka planda çalışır."
        );
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
        return "192.168.1.???";
    }
}
