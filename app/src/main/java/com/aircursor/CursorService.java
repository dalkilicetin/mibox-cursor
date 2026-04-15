package com.aircursor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

import org.json.JSONObject;

/**
 * Arka planda çalışan servis:
 *   1. Ekrana CursorView overlay ekler
 *   2. TCP socket server açar (port 9876)
 *   3. Gelen JSON komutları cursor'a yansıtır
 *   4. ADB input tap ile tıklama yapar
 */
public class CursorService extends Service {

    private static final String TAG = "AirCursor";
    private static final int PORT = 9876;
    private static final String CHANNEL_ID = "aircursor";

    private WindowManager wm;
    private CursorView cursorView;
    private WindowManager.LayoutParams params;
    private Handler mainHandler;

    // Ekran boyutları
    private int screenW = 1920;
    private int screenH = 1080;

    // Cursor pozisyonu (0.0 - 1.0 oransal)
    private float curX = 0.5f;
    private float curY = 0.5f;

    // Gyro accumulator
    private float accX = 0f;
    private float accY = 0f;

    private volatile boolean running = true;
    private Thread serverThread;

    @Override
    public void onCreate() {
        super.onCreate();
        mainHandler = new Handler(Looper.getMainLooper());
        startForeground(1, buildNotification());
        setupOverlay();
        startSocketServer();
        Log.i(TAG, "CursorService başladı, port: " + PORT);
    }

    // ── Overlay Kurulumu ────────────────────────────────────────────────────

    private void setupOverlay() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(dm);
        screenW = dm.widthPixels;
        screenH = dm.heightPixels;

        cursorView = new CursorView(this);

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        );

        // Sol-üst köşeden ofset
        params.gravity = android.view.Gravity.TOP | android.view.Gravity.LEFT;
        params.x = screenW / 2;
        params.y = screenH / 2;

        wm.addView(cursorView, params);
        Log.i(TAG, "Overlay eklendi. Ekran: " + screenW + "x" + screenH);
    }

    private void moveCursor(float x, float y) {
        params.x = Math.max(0, Math.min((int) x, screenW - 60));
        params.y = Math.max(0, Math.min((int) y, screenH - 60));
        wm.updateViewLayout(cursorView, params);
    }

    // ── TCP Socket Server ────────────────────────────────────────────────────
    // Basit JSON protokolü:
    //   {"type":"move","dx":5.2,"dy":-3.1}   → cursor hareket
    //   {"type":"tap"}                         → tıklama
    //   {"type":"pos","x":0.5,"y":0.3}        → mutlak pozisyon (0-1)

    private void startSocketServer() {
        serverThread = new Thread(() -> {
            try (ServerSocket ss = new ServerSocket(PORT)) {
                ss.setSoTimeout(0);
                Log.i(TAG, "Socket server dinliyor: " + PORT);
                while (running) {
                    try {
                        Socket client = ss.accept();
                        Log.i(TAG, "Client bağlandı: " + client.getRemoteSocketAddress());
                        handleClient(client);
                    } catch (Exception e) {
                        if (running) Log.w(TAG, "Client hata: " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Server hata: " + e.getMessage());
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();
    }

    private void handleClient(Socket client) {
        new Thread(() -> {
            try {
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(client.getInputStream()));
                PrintWriter writer = new PrintWriter(client.getOutputStream(), true);

                writer.println("{\"status\":\"connected\",\"screen\":{\"w\":"
                    + screenW + ",\"h\":" + screenH + "}}");

                String line;
                while ((line = reader.readLine()) != null && running) {
                    processCommand(line.trim());
                }
            } catch (Exception e) {
                Log.d(TAG, "Client ayrıldı");
            } finally {
                try { client.close(); } catch (Exception ignored) {}
            }
        }).start();
    }

    private void processCommand(String json) {
        try {
            JSONObject obj = new JSONObject(json);
            String type = obj.getString("type");

            switch (type) {
                case "move": {
                    // Relative hareket: dx, dy piksel
                    float dx = (float) obj.optDouble("dx", 0);
                    float dy = (float) obj.optDouble("dy", 0);
                    final float nx = params.x + dx;
                    final float ny = params.y + dy;
                    mainHandler.post(() -> moveCursor(nx, ny));
                    break;
                }
                case "pos": {
                    // Absolute pozisyon: x,y 0.0-1.0
                    float rx = (float) obj.optDouble("x", 0.5);
                    float ry = (float) obj.optDouble("y", 0.5);
                    final float nx = rx * screenW;
                    final float ny = ry * screenH;
                    mainHandler.post(() -> moveCursor(nx, ny));
                    break;
                }
                case "tap": {
                    // Tıklama: cursor pozisyonuna ADB input tap
                    final int tx = params.x;
                    final int ty = params.y;
                    mainHandler.post(() -> {
                        cursorView.showClick();
                        performTap(tx, ty);
                    });
                    break;
                }
                case "gyro": {
                    // Gyroscope verisi: beta (ileri/geri), gamma (sol/sağ)
                    float beta  = (float) obj.optDouble("beta",  0);
                    float gamma = (float) obj.optDouble("gamma", 0);
                    float speed = 8f; // piksel/event

                    float dx = applyDeadzone(gamma, 2f) * speed;
                    float dy = applyDeadzone(beta,  2f) * speed;

                    if (dx != 0 || dy != 0) {
                        final float nx = params.x + dx;
                        final float ny = params.y + dy;
                        mainHandler.post(() -> moveCursor(nx, ny));
                    }
                    break;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "JSON parse hata: " + json + " → " + e.getMessage());
        }
    }

    private float applyDeadzone(float val, float zone) {
        if (Math.abs(val) < zone) return 0;
        return val > 0 ? val - zone : val + zone;
    }

    private void performTap(int x, int y) {
        try {
            // ADB input tap (servis shell erişimi olmadan)
            Runtime.getRuntime().exec(
                new String[]{"input", "tap", String.valueOf(x), String.valueOf(y)}
            );
        } catch (Exception e) {
            Log.w(TAG, "Tap hata: " + e.getMessage());
        }
    }

    // ── Foreground Notification ──────────────────────────────────────────────

    private Notification buildNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "AirCursor", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);
        }
        return new Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("AirCursor")
            .setContentText("Cursor aktif — port 9876")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        if (cursorView != null && wm != null) {
            try { wm.removeView(cursorView); } catch (Exception ignored) {}
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
