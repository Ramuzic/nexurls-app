package com.nexurls.app;

import android.view.HapticFeedbackConstants;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.onesignal.OneSignal;

/**
 * The small native bridge the website talks to when it runs inside the app.
 * From a page on nexurls.com:
 *   Capacitor.nativePromise('NexApp', 'registerPush', { externalId: 'nexurls_com_12' })
 *   Capacitor.nativePromise('NexApp', 'haptic', {})
 */
@CapacitorPlugin(name = "NexApp")
public class NexAppPlugin extends Plugin {

    /** Links this phone to the signed-in member so the site's notifications reach it. */
    @PluginMethod
    public void registerPush(PluginCall call) {
        String ext = call.getString("externalId", "");
        JSObject r = new JSObject();
        try {
            if (ext != null && !ext.isEmpty()) OneSignal.login(ext);
            String id = OneSignal.getUser().getPushSubscription().getId();
            r.put("id", id == null ? "" : id);
            r.put("optedIn", OneSignal.getUser().getPushSubscription().getOptedIn());
        } catch (Exception e) {
            r.put("id", "");
            r.put("optedIn", false);
        }
        call.resolve(r);
    }

    @PluginMethod
    public void haptic(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            if (getBridge().getWebView() != null) {
                getBridge().getWebView().performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            }
            call.resolve();
        });
    }

    @PluginMethod
    public void info(PluginCall call) {
        JSObject r = new JSObject();
        r.put("platform", "android");
        String v = "";
        try {
            v = getContext().getPackageManager().getPackageInfo(getContext().getPackageName(), 0).versionName;
        } catch (Exception ignored) {}
        r.put("version", v == null ? "" : v);
        call.resolve(r);
    }
}
