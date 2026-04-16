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

    private volatile int realX = 960;
    private volatile int realY = 540;

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

        realX = screenW / 2;
        realY = screenH / 2;

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
        params.x = realX;
        params.y = realY;

        wm.addView(cursorView, params);
        Log.i(TAG, "Overlay added. Screen: " + screenW + "x" + screenH);
    }

    private void moveCursor(int x, int y) {
        realX = Math.max(0, Math.min(x, screenW - 60));
        realY = Math.max(0, Math.min(y, screenH - 60));
        params.x = realX;
        params.y = realY;
        wm.updateViewLayout(cursorView, params);
    }

    private void startSocketServer() {
        new Thread(() -> {
            try (ServerSocket ss = new ServerSocket(PORT)) {
                Log.i(TAG, "Listening on port " + PORT);
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

                writer.println("{\"status\":\"connected\",\"w\":" + screenW +
                    ",\"h\":" + screenH + ",\"x\":" + realX + ",\"y\":" + realY + "}");

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

            if (type.equals("move")) {
                int dx = obj.optInt("dx", 0);
                int dy = obj.optInt("dy", 0);
                int nx = Math.max(0, Math.min(realX + dx, screenW - 60));
                int ny = Math.max(0, Math.min(realY + dy, screenH - 60));
                mainHandler.post(() -> moveCursor(nx, ny));
                writer.println("{\"x\":" + nx + ",\"y\":" + ny + "}");

            } else if (type.equals("tap")) {
                mainHandler.post(() -> cursorView.showClick());
                writer.println("{\"tap\":true,\"x\":" + realX + ",\"y\":" + realY + "}");

            } else if (type.equals("hide")) {
                mainHandler.post(() -> cursorView.setVisibility(android.view.View.INVISIBLE));
                writer.println("{\"hidden\":true}");

            } else if (type.equals("show")) {
                mainHandler.post(() -> cursorView.setVisibility(android.view.View.VISIBLE));
                writer.println("{\"hidden\":false}");

            } else if (type.equals("scroll_mode")) {
                int mode = obj.optInt("mode", 0);
                mainHandler.post(() -> cursorView.setScrollMode(mode));
                writer.println("{\"scroll_mode\":" + mode + "}");
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
