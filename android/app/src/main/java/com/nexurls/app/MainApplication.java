package com.nexurls.app;

import android.app.Application;
import com.onesignal.OneSignal;
import com.onesignal.notifications.INotificationClickEvent;
import com.onesignal.notifications.INotificationClickListener;
import org.json.JSONObject;

/** Starts push (OneSignal) once, when the app process starts. */
public class MainApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        String appId = AppConfig.get(this).oneSignalAppId;
        if (appId == null || appId.isEmpty()) return;

        OneSignal.initWithContext(this, appId);

        // A tapped notification opens its page inside the app (not the browser).
        OneSignal.getNotifications().addClickListener(new INotificationClickListener() {
            @Override
            public void onClick(INotificationClickEvent event) {
                String url = event.getResult().getUrl();
                if (url == null || url.isEmpty()) url = event.getNotification().getLaunchURL();
                if (url == null || url.isEmpty()) {
                    JSONObject data = event.getNotification().getAdditionalData();
                    if (data != null) url = data.optString("url", "");
                }
                if (url != null && !url.isEmpty()) MainActivity.openFromPush(url);
            }
        });
    }
}
