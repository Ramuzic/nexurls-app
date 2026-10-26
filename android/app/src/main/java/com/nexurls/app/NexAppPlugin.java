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

    /** Apple in-app purchases and Sign in with Apple are iPhone only; Android keeps the website checkout. */
    @PluginMethod
    public void iapProducts(PluginCall call) { call.reject("Not available on Android"); }

    @PluginMethod
    public void iapBuy(PluginCall call) { call.reject("Not available on Android"); }

    @PluginMethod
    public void iapPending(PluginCall call) { JSObject r = new JSObject(); r.put("items", new com.getcapacitor.JSArray()); call.resolve(r); }

    @PluginMethod
    public void iapRestore(PluginCall call) { JSObject r = new JSObject(); r.put("items", new com.getcapacitor.JSArray()); call.resolve(r); }

    @PluginMethod
    public void iapFinish(PluginCall call) { call.resolve(); }

    @PluginMethod
    public void iapManage(PluginCall call) { call.resolve(); }

    @PluginMethod
    public void appleSignIn(PluginCall call) { call.reject("Not available on Android"); }

    /** The site hides the tabs while a website is open full screen: { hidden: true }. */
    @PluginMethod
    public void tabBar(PluginCall call) {
        boolean hidden = Boolean.TRUE.equals(call.getBoolean("hidden", false));
        getActivity().runOnUiThread(() -> {
            if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).setTabBarHidden(hidden);
            call.resolve();
        });
    }

    /** Android launchers show their own notification dots, so the count is not set by hand. */
    @PluginMethod
    public void setBadge(PluginCall call) { call.resolve(); }

    /** Pull to refresh is handled by the page itself on Android. */
    @PluginMethod
    public void setRefresh(PluginCall call) { call.resolve(); }

    /** Google Play's own "rate this app" sheet. Play decides whether it really shows. */
    @PluginMethod
    public void requestReview(PluginCall call) {
        try {
            com.google.android.play.core.review.ReviewManager m = com.google.android.play.core.review.ReviewManagerFactory.create(getContext());
            m.requestReviewFlow().addOnCompleteListener(task -> {
                if (task.isSuccessful() && getActivity() != null) {
                    m.launchReviewFlow(getActivity(), task.getResult()).addOnCompleteListener(done -> call.resolve());
                } else {
                    call.resolve();
                }
            });
        } catch (Exception e) {
            call.resolve();
        }
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
