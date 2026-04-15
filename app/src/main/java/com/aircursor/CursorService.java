package com.aircursor;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

import org.json.JSONObject;

public class CursorService extends Service {

    private static final String TAG = "AirCursor";
    private static final int PORT = 9876;

    private WindowManager wm;
    private CursorView cursorView;
    private WindowManager.LayoutParams params;
    private Handler mainHandler;
    private int screenW = 1920;
    private int screenH = 1080;
    private volatile boolean running = true;

    @Override
    public void onCreate() {
        super.onCreate();
        mainHandler = new Handler(Looper.getMainLooper());
        setupOverlay();
        startSocketServer();
        Log.i(TAG, "CursorService started on port " + PORT);
    }

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

        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = screenW / 2;
        params.y = screenH / 2;

        wm.addView(cursorView, params);
        Log.i(TAG, "Overlay added. Screen: " + screenW + "x" + screenH);
    }

    private void moveCursor(float x, float y) {
        params.x = Math.max(0, Math.min((int) x, screenW - 60));
        params.y = Math.max(0, Math.min((int) y, screenH - 60));
        wm.updateViewLayout(cursorView, params);
    }

    private void startSocketServer() {
        new Thread(() -> {
            try (ServerSocket ss = new ServerSocket(PORT)) {
                Log.i(TAG, "Listening on port " + PORT);
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

    private void handleClient(Socket client) {
        new Thread(() -> {
            try {
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(client.getInputStream()));
                PrintWriter writer = new PrintWriter(client.getOutputStream(), true);
                writer.println("{\"status\":\"connected\",\"w\":" + screenW + ",\"h\":" + screenH + "}");

                String line;
                while ((line = reader.readLine()) != null && running) {
                    processCommand(line.trim());
                }
            } catch (Exception e) {
                Log.d(TAG, "Client disconnected");
            } finally {
                try { client.close(); } catch (Exception ignored) {}
            }
        }).start();
    }

    private void processCommand(String json) {
        try {
            JSONObject obj = new JSONObject(json);
            String type = obj.getString("type");

            if (type.equals("move")) {
                float dx = (float) obj.optDouble("dx", 0);
                float dy = (float) obj.optDouble("dy", 0);
                mainHandler.post(() -> moveCursor(params.x + dx, params.y + dy));

            } else if (type.equals("gyro")) {
                float beta  = (float) obj.optDouble("beta",  0);
                float gamma = (float) obj.optDouble("gamma", 0);
                float speed = 8f;
                float dz = 2f;
                float dx = Math.abs(gamma) > dz ? (gamma > 0 ? gamma - dz : gamma + dz) * speed * 0.05f : 0;
                float dy = Math.abs(beta)  > dz ? (beta  > 0 ? beta  - dz : beta  + dz) * speed * 0.05f : 0;
                if (dx != 0 || dy != 0) {
                    mainHandler.post(() -> moveCursor(params.x + dx, params.y + dy));
                }

            } else if (type.equals("tap")) {
                final int tx = params.x;
                final int ty = params.y;
                mainHandler.post(() -> {
                    cursorView.showClick();
                    try {
                        Runtime.getRuntime().exec(
                            new String[]{"input", "tap", String.valueOf(tx), String.valueOf(ty)}
                        );
                    } catch (Exception e) {
                        Log.w(TAG, "Tap error: " + e.getMessage());
                    }
                });
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
        if (cursorView != null && wm != null) {
            try { wm.removeView(cursorView); } catch (Exception ignored) {}
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}