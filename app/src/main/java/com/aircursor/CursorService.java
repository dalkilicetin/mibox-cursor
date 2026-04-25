package com.aircursor;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

public class CursorService extends Service {

    private static final String TAG = "CursorService";
    private boolean running = true;

    @Override
    public void onCreate() {
        super.onCreate();
        startServer();
        Log.d(TAG, "🚀 CursorService started");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startServer() {
        new Thread(() -> {
            try {
                ServerSocket serverSocket = new ServerSocket(9999);
                Log.d(TAG, "🌐 Server listening on 9999");

                while (running) {
                    Socket socket = serverSocket.accept();
                    handleClient(socket);
                }

            } catch (Exception e) {
                Log.e(TAG, "Server error", e);
            }
        }).start();
    }

    private void handleClient(Socket socket) {
        new Thread(() -> {
            try {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream())
                );

                String line;
                while ((line = reader.readLine()) != null) {
                    handleMessage(line);
                }

            } catch (Exception e) {
                Log.e(TAG, "Client error", e);
            }
        }).start();
    }

    private void handleMessage(String message) {
        try {
            JSONObject json = new JSONObject(message);
            String type = json.optString("type");

            switch (type) {

                case "move": {
                    float dx = (float) json.optDouble("dx", 0);
                    float dy = (float) json.optDouble("dy", 0);

                    CursorOverlay.move(dx, dy);
                    break;
                }

                case "tap": {
                    AirCursorAccessibility svc = AirCursorAccessibility.getInstance();

                    if (svc != null) {
                        float x = CursorOverlay.cursorX;
                        float y = CursorOverlay.cursorY;

                        svc.performTap(x, y);
                        Log.d(TAG, "🎯 Tap at: " + x + "," + y);

                    } else {
                        Log.e(TAG, "❌ Accessibility service not ready");
                    }
                    break;
                }

                default:
                    Log.w(TAG, "Unknown command: " + type);
            }

        } catch (Exception e) {
            Log.e(TAG, "Parse error: " + message, e);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        running = false;
        Log.d(TAG, "🛑 CursorService stopped");
    }
}