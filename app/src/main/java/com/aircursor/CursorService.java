package com.aircursor;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * CursorService
 *
 * TCP 9876  — iOS ↔ TV komut kanalı (move, tap, key, scroll_mode, hide, show, text)
 * UDP 9877  — APK discovery (AIRCURSOR_DISCOVER broadcast → JSON yanıt)
 *
 * Key inject ve navigation YOK — bunlar ATV Remote protokolü üzerinden iOS sorumluluğu.
 * tap() komutu TapResult döner: iOS buna göre ATV ile navigate eder.
 */
public class CursorService extends Service {

    private static final String TAG      = "CursorService";
    private static final int    TCP_PORT = 9876;
    private static final int    UDP_PORT = 9877;

    private volatile boolean running = true;

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        startSocketServer();
        startUdpDiscovery();
        Log.i(TAG, "Started — TCP:" + TCP_PORT + " UDP:" + UDP_PORT);
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

    // ── UDP Discovery ──────────────────────────────────────────────────────────

    private void startUdpDiscovery() {
        new Thread(() -> {
            try (DatagramSocket udp = new DatagramSocket(UDP_PORT)) {
                udp.setBroadcast(true);
                byte[] buf = new byte[256];
                Log.i(TAG, "UDP discovery on :" + UDP_PORT);
                while (running) {
                    DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                    udp.receive(pkt);
                    String msg = new String(pkt.getData(), 0, pkt.getLength()).trim();
                    if (msg.equals("AIRCURSOR_DISCOVER")) {
                        String response = "{\"service\":\"aircursor\",\"port\":" + TCP_PORT + "}";
                        byte[] resp = response.getBytes();
                        udp.send(new DatagramPacket(resp, resp.length, pkt.getAddress(), pkt.getPort()));
                        Log.d(TAG, "Discovery reply → " + pkt.getAddress());
                    }
                }
            } catch (Exception e) {
                if (running) Log.e(TAG, "UDP error: " + e.getMessage());
            }
        }).start();
    }

    // ── TCP Server ─────────────────────────────────────────────────────────────

    private void startSocketServer() {
        new Thread(() -> {
            try (ServerSocket ss = new ServerSocket(TCP_PORT)) {
                Log.i(TAG, "TCP listening on :" + TCP_PORT);
                while (running) {
                    try {
                        Socket client = ss.accept();
                        Log.i(TAG, "Client connected: " + client.getRemoteSocketAddress());
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
                BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
                PrintWriter    writer = new PrintWriter(client.getOutputStream(), true);

                // Bağlantı kurulunca ekran boyutu + cursor pozisyonu gönder
                AirCursorAccessibility a11y = AirCursorAccessibility.getInstance();
                int w = a11y != null ? a11y.getScreenW() : 1920;
                int h = a11y != null ? a11y.getScreenH() : 1080;
                int x = a11y != null ? (int) a11y.getCursorX() : w / 2;
                int y = a11y != null ? (int) a11y.getCursorY() : h / 2;
                writer.println("{\"status\":\"connected\",\"w\":" + w + ",\"h\":" + h
                    + ",\"x\":" + x + ",\"y\":" + y + "}");

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

    // ── Command handler ────────────────────────────────────────────────────────

    private void processCommand(String json, PrintWriter writer) {
        try {
            JSONObject obj  = new JSONObject(json);
            String     type = obj.getString("type");
            AirCursorAccessibility a11y = AirCursorAccessibility.getInstance();

            switch (type) {

                case "move": {
                    int dx = obj.optInt("dx", 0);
                    int dy = obj.optInt("dy", 0);
                    if (a11y != null) {
                        a11y.moveCursor(dx, dy);
                        writer.println("{\"x\":" + (int)a11y.getCursorX()
                            + ",\"y\":" + (int)a11y.getCursorY() + "}");
                    }
                    break;
                }

                case "tap": {
                    if (a11y == null) {
                        writer.println("{\"tap\":false,\"a11y\":false,\"targetX\":-1,\"targetY\":-1}");
                        break;
                    }
                    // tap() sync çalışır — TapResult döner
                    // iOS bu sonuca göre ATV protokolüyle navigation yapar
                    AirCursorAccessibility.TapResult result = a11y.tap();
                    writer.println(tapResultToJson(result));
                    break;
                }

                case "hide": {
                    if (a11y != null) a11y.setCursorVisible(false);
                    writer.println("{\"hidden\":true}");
                    break;
                }

                case "show": {
                    if (a11y != null) a11y.setCursorVisible(true);
                    writer.println("{\"hidden\":false}");
                    break;
                }

                case "scroll_mode": {
                    int mode = obj.optInt("mode", 0);
                    if (a11y != null) a11y.setScrollMode(mode);
                    writer.println("{\"scroll_mode\":" + mode + "}");
                    break;
                }

                default:
                    Log.w(TAG, "Unknown command type: " + type);
                    writer.println("{\"error\":\"unknown_type\"}");
                    break;
            }

        } catch (Exception e) {
            Log.w(TAG, "processCommand error: " + json + " → " + e.getMessage());
            writer.println("{\"error\":\"parse_error\"}");
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * TapResult → JSON string
     *
     * clicked=true  → iOS'un yapacağı ek şey yok, tıklama oldu
     * clicked=false → iOS targetX/Y'ye göre ATV ile navigate + DPAD_CENTER
     * targetX=-1    → iOS direkt DPAD_CENTER (mevcut focus'a)
     *
     * focusX/Y — mevcut focus koordinatı, iOS navigation delta hesabı için kullanır
     */
    private static String tapResultToJson(AirCursorAccessibility.TapResult r) {
        String safeLabel = r.label.replace("\"", "\\\"").replace("\n", " ");
        return "{\"tap\":true"
            + ",\"clicked\":"  + r.clicked
            + ",\"targetX\":"  + r.targetX
            + ",\"targetY\":"  + r.targetY
            + ",\"focusX\":"   + r.focusX
            + ",\"focusY\":"   + r.focusY
            + ",\"label\":\""  + safeLabel + "\""
            + "}";
    }
}
