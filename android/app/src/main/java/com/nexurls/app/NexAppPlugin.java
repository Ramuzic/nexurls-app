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

    /** A tap you can feel. style: light (default), medium, heavy, soft, rigid, selection, success, warning, error. */
    @PluginMethod
    public void haptic(PluginCall call) {
        String style = call.getString("style", "light");
        int fx;
        switch (style == null ? "light" : style) {
            case "medium": case "rigid": fx = HapticFeedbackConstants.VIRTUAL_KEY; break;
            case "heavy": case "warning": fx = HapticFeedbackConstants.LONG_PRESS; break;
            case "selection": case "soft": fx = HapticFeedbackConstants.CLOCK_TICK; break;
            case "success": fx = android.os.Build.VERSION.SDK_INT >= 30 ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.VIRTUAL_KEY; break;
            case "error": fx = android.os.Build.VERSION.SDK_INT >= 30 ? HapticFeedbackConstants.REJECT : HapticFeedbackConstants.LONG_PRESS; break;
            default: fx = HapticFeedbackConstants.KEYBOARD_TAP;
        }
        final int effect = fx;
        getActivity().runOnUiThread(() -> {
            if (getBridge().getWebView() != null) {
                getBridge().getWebView().performHapticFeedback(effect);
            }
            call.resolve();
        });
    }

    /** The site hides the tabs while a website is open full screen: { hidden: true }. */
    @PluginMethod
    public void tabBar(PluginCall call) {
        boolean hidden = Boolean.TRUE.equals(call.getBoolean("hidden", false));
        getActivity().runOnUiThread(() -> {
            if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).setTabBarHidden(hidden);
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
