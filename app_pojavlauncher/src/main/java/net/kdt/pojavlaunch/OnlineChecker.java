package net.kdt.pojavlaunch; 

import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class OnlineChecker {

    public interface OnlineCallback {
        void onOnlineReceived(int playersCount);
        void onError(Exception e);
    }

    public static void fetchOnline(String serverAddress, OnlineCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Handler mainHandler = new Handler(Looper.getMainLooper());
                try {
                    URL url = new URL("https://api.mcsrvstat.us/2/" + serverAddress);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(5000);
                    connection.setReadTimeout(5000);

                    if (connection.getResponseCode() == 200) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                        StringBuilder response = new StringBuilder();
                        String line;

                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                        reader.close();

                        JSONObject jsonObject = new JSONObject(response.toString());
                        boolean isOnline = jsonObject.optBoolean("online", false);
                        int playersCount = 0;

                        if (isOnline && jsonObject.has("players")) {
                            JSONObject playersObj = jsonObject.getJSONObject("players");
                            playersCount = playersObj.optInt("online", 0);
                        }

                        final int finalPlayers = playersCount;
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onOnlineReceived(finalPlayers);
                            }
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onError(e);
                        }
                    });
                }
            }
        }).start();
    }
}
