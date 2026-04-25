package com.aircursor;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;

import org.json.JSONObject;

/**
 * CursorService: TCP server + UDP discovery + komut yönlendirme.
 * Overlay ve tap = AirCursorAccessibility üzerinden (SYSTEM_ALERT_WINDOW gerekmez)
 */
public class CursorService extends Service {

    private static final String TAG = "AirCursor";
    private static final int TCP_PORT = 9876;
    private static final int UDP_PORT = 9877;

    private static final String TAPSERVER_PATH = "/data/local/tmp/tapserver";
    private static final int TAPSERVER_PORT = 9877;
    private volatile boolean running = true;

    @Override
    public void onCreate() {
        super.onCreate();
        extractAndStartTapserver();
        startSocketServer();
        startUdpDiscovery();
        Log.i(TAG, "CursorService started on TCP:" + TCP_PORT + " UDP:" + UDP_PORT);
    }

    private void extractAndStartTapserver() {
        new Thread(() -> {
            try {
                // Assets'ten extract et
                java.io.File dest = new java.io.File(TAPSERVER_PATH);
                if (!dest.exists()) {
                    java.io.InputStream in = getAssets().open("tapserver");
                    java.io.FileOutputStream out = new java.io.FileOutputStream(dest);
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    in.close(); out.close();
                    Log.i(TAG, "tapserver extracted to " + TAPSERVER_PATH);
                }
                // Çalıştırma izni ver
                Runtime.getRuntime().exec(new String[]{"chmod", "+x", TAPSERVER_PATH}).waitFor();
                // Zaten çalışıyor mu kontrol et
                Process check = Runtime.getRuntime().exec(new String[]{"sh", "-c", "pgrep -f tapserver"});
                check.waitFor();
                if (check.exitValue() != 0) {
                    // Çalışmıyorsa başlat
                    Runtime.getRuntime().exec(new String[]{"sh", "-c", TAPSERVER_PATH + " &"});
                    Log.i(TAG, "tapserver started on port " + TAPSERVER_PORT);
                } else {
                    Log.i(TAG, "tapserver already running");
                }
            } catch (Exception e) {
                Log.e(TAG, "tapserver error: " + e.getMessage());
            }
        }).start();
    }

    private void startUdpDiscovery() {
        new Thread(() -> {
            try (DatagramSocket udp = new DatagramSocket(UDP_PORT)) {
                udp.setBroadcast(true);
                byte[] buf = new byte[256];
                Log.i(TAG, "UDP discovery listening on " + UDP_PORT);
                while (running) {
                    DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                    udp.receive(pkt);
                    String msg = new String(pkt.getData(), 0, pkt.getLength()).trim();
                    if (msg.equals("AIRCURSOR_DISCOVER")) {
                        String response = "{\"service\":\"aircursor\",\"port\":" + TCP_PORT + "}";
                        byte[] resp = response.getBytes();
                        DatagramPacket reply = new DatagramPacket(
                            resp, resp.length, pkt.getAddress(), pkt.getPort());
                        udp.send(reply);
                        Log.d(TAG, "UDP discovery reply → " + pkt.getAddress());
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "UDP error: " + e.getMessage());
            }
        }).start();
    }

    private void startSocketServer() {
        new Thread(() -> {
            try (ServerSocket ss = new ServerSocket(TCP_PORT)) {
                Log.i(TAG, "Listening on port " + TCP_PORT);
                while (running) {
                    try {
                        Socket client = ss.accept();
                        Log.i(TAG, "Client: " + client.getRemoteSocketAddress());
                        handleClient(client);
                    } catch (Exception e) {
                        if (running) Log.w(TAG, "Client error: " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Server error: " + e.getMessage());
            }
        }).start();
    }

    private void handleClient(final Socket client) {
        new Thread(() -> {
            try {
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(client.getInputStream()));
                final PrintWriter writer = new PrintWriter(client.getOutputStream(), true);

                AirCursorAccessibility a11y = AirCursorAccessibility.getInstance();
                int w = a11y != null ? a11y.getScreenW() : 1920;
                int h = a11y != null ? a11y.getScreenH() : 1080;
                int x = a11y != null ? (int) a11y.getCursorX() : w / 2;
                int y = a11y != null ? (int) a11y.getCursorY() : h / 2;

                writer.println("{\"status\":\"connected\",\"w\":" + w +
                    ",\"h\":" + h + ",\"x\":" + x + ",\"y\":" + y + "}");

                String line;
                while ((line = reader.readLine()) != null && running) {
                    processCommand(line.trim(), writer);
                }
            } catch (Exception e) {
                Log.d(TAG, "Client disconnected");
            } finally {
                try { client.close(); } catch (Exception ignored) {}
            }
        }).start();
    }

    private void processCommand(String json, PrintWriter writer) {
        try {
            JSONObject obj = new JSONObject(json);
            String type = obj.getString("type");

            AirCursorAccessibility a11y = AirCursorAccessibility.getInstance();

            if (type.equals("move")) {
                int dx = obj.optInt("dx", 0);
                int dy = obj.optInt("dy", 0);
                if (a11y != null) {
                    a11y.moveCursor(dx, dy);
                    writer.println("{\"x\":" + (int)a11y.getCursorX() +
                        ",\"y\":" + (int)a11y.getCursorY() + "}");
                }

            } else if (type.equals("tap")) {
                if (a11y != null) {
                    int tx = (int) a11y.getCursorX();
                    int ty = (int) a11y.getCursorY();
                    // Test: getRootInActiveWindow çalışıyor mu?
                    String nearest = a11y.dumpNearestNode(tx, ty);
                    Log.i(TAG, "Tap nearest: " + nearest);
                    a11y.tap();
                    boolean ready = AirCursorAccessibility.getInstance() != null;
                    writer.println("{\"tap\":true,\"x\":" + tx +
                        ",\"y\":" + ty + ",\"a11y\":" + ready +
                        (nearest != null ? ",\"nearest\":" + nearest : "") + "}");
                } else {
                    Log.w(TAG, "tap: accessibility not ready");
                    writer.println("{\"tap\":false,\"a11y\":false}");
                }

            } else if (type.equals("hide")) {
                if (a11y != null) a11y.setCursorVisible(false);
                writer.println("{\"hidden\":true}");

            } else if (type.equals("show")) {
                if (a11y != null) a11y.setCursorVisible(true);
                writer.println("{\"hidden\":false}");

            } else if (type.equals("scroll_mode")) {
                int mode = obj.optInt("mode", 0);
                if (a11y != null) a11y.setScrollMode(mode);
                writer.println("{\"scroll_mode\":" + mode + "}");

            } else if (type.equals("text")) {
                String value = obj.optString("value", "");
                if (!value.isEmpty() && a11y != null) {
                    a11y.injectText(value);
                    writer.println("{\"text\":true}");
                }

            } else if (type.equals("key")) {
                int code = obj.optInt("code", 0);
                if (code > 0 && a11y != null) {
                    a11y.injectKey(code);
                    writer.println("{\"key\":" + code + "}");
                }

            } else if (type.equals("swipe")) {
                int x1 = obj.optInt("x1", 960);
                int y1 = obj.optInt("y1", 540);
                int x2 = obj.optInt("x2", 960);
                int y2 = obj.optInt("y2", 340);
                int dur = obj.optInt("duration", 150);
                if (a11y != null) a11y.performSwipe(x1, y1, x2, y2, dur);
                writer.println("{\"swipe\":true}");
            }

        } catch (Exception e) {
            Log.w(TAG, "JSON error: " + json);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
