package com.aircursor;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.*;

public class CursorService extends Service {

    private static final String TAG = "CursorService";

    private boolean running = true;

    @Override
    public void onCreate() {
        super.onCreate();

        startTcpServer();
        startUdpDiscovery();

        Log.d(TAG, "🚀 Service started");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ===================== TCP SERVER =====================
    private void startTcpServer() {
        new Thread(() -> {
            try {
                ServerSocket serverSocket = new ServerSocket(9999);
                Log.d(TAG, "🌐 TCP listening 9999");

                while (running) {
                    Socket socket = serverSocket.accept();
                    handleClient(socket);
                }

            } catch (Exception e) {
                Log.e(TAG, "TCP error", e);
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

    // ===================== UDP DISCOVERY =====================
    private void startUdpDiscovery() {
        new Thread(() -> {
            try {
                DatagramSocket socket = new DatagramSocket(8888, InetAddress.getByName("0.0.0.0"));
                socket.setBroadcast(true);

                byte[] buffer = new byte[15000];

                Log.d(TAG, "📡 UDP discovery listening 8888");

                while (running) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    String msg = new String(packet.getData(), 0, packet.getLength());

                    if (msg.equals("DISCOVER_AIRCURSOR")) {

                        String response = "AIRCURSOR:" + getLocalIp();

                        DatagramPacket responsePacket = new DatagramPacket(
                                response.getBytes(),
                                response.length(),
                                packet.getAddress(),
                                packet.getPort()
                        );

                        socket.send(responsePacket);
                        Log.d(TAG, "📡 Discovery response sent");
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "UDP error", e);
            }
        }).start();
    }

    private String getLocalIp() {
        try {
            for (NetworkInterface intf : NetworkInterface.getNetworkInterfaces()) {
                for (InetAddress addr : java.util.Collections.list(intf.getInetAddresses())) {
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return "0.0.0.0";
    }

    // ===================== COMMAND =====================
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
                        svc.performTap(CursorOverlay.cursorX, CursorOverlay.cursorY);
                        Log.d(TAG, "🎯 Tap");
                    } else {
                        Log.e(TAG, "❌ Accessibility not ready");
                    }
                    break;
                }

                default:
                    Log.w(TAG, "Unknown: " + type);
            }

        } catch (Exception e) {
            Log.e(TAG, "Parse error: " + message, e);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        running = false;
    }
}