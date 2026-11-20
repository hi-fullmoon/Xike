package com.xike.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Standalone test APK service; depends only on classes provided by Android. */
public final class ReminderNotificationObserver extends NotificationListenerService {
    @Override
    public void onListenerConnected() {
        if (!getSharedPreferences("reminder-observer", Context.MODE_PRIVATE)
                .edit().putBoolean("connected", true).commit()) {
            throw new IllegalStateException("Cannot persist observer readiness");
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification notification) {
        if (!"com.xike.app".equals(notification.getPackageName()) || notification.getId() != 402) {
            return;
        }
        SharedPreferences preferences = getSharedPreferences("reminder-observer", Context.MODE_PRIVATE);
        try {
            JSONArray events = new JSONArray(preferences.getString("events", "[]"));
            events.put(new JSONObject()
                    .put("tag", notification.getTag())
                    .put("postTime", notification.getPostTime())
                    .put("observedAt", System.currentTimeMillis()));
            if (!preferences.edit().putString("events", events.toString()).commit()) {
                throw new IllegalStateException("Cannot persist notification metadata");
            }
        } catch (JSONException error) {
            throw new IllegalStateException("Cannot encode notification metadata", error);
        }
    }
}
