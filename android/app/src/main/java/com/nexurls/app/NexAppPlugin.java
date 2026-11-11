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

    /* ------------------------------------------------------------------ */
    /* Google Play purchases (1.1.3). The site checks every purchase with  */
    /* Google before it gives anything, the same way it checks Apple's.    */
    /* ------------------------------------------------------------------ */

    private PlayStore store;

    private PlayStore store() {
        if (store == null) store = new PlayStore(getActivity());
        return store;
    }

    /** Prices in the buyer's own currency: { ids: [...] } gives { products: [{ id, price }] }. */
    @PluginMethod
    public void iapProducts(PluginCall call) {
        java.util.List<String> ids = new java.util.ArrayList<>();
        try {
            com.getcapacitor.JSArray a = call.getArray("ids");
            if (a != null) for (int i = 0; i < a.length(); i++) ids.add(a.getString(i));
        } catch (Exception ignored) {}
        store().products(ids, call);
    }

    /** { id, token } where token is the member's purchase id from the site. */
    @PluginMethod
    public void iapBuy(PluginCall call) {
        store().buy(call.getString("id", ""), call.getString("token", ""), call);
    }

    /** Purchases the site has not handled yet (interrupted purchases, pending payments that went through). */
    @PluginMethod
    public void iapPending(PluginCall call) { store().owned(true, call); }

    /** Everything this Google account owns, to restore on a new phone. */
    @PluginMethod
    public void iapRestore(PluginCall call) { store().owned(false, call); }

    /** After the site has added it: { purchaseToken, consume } (consume for NexCredit packs). */
    @PluginMethod
    public void iapFinish(PluginCall call) {
        store().finish(call.getString("purchaseToken", ""), Boolean.TRUE.equals(call.getBoolean("consume", false)), call);
    }

    /** Google Play's subscriptions page for this app. */
    @PluginMethod
    public void iapManage(PluginCall call) {
        try {
            String sku = call.getString("id", "");
            String url = "https://play.google.com/store/account/subscriptions?package=" + getContext().getPackageName()
                + (sku == null || sku.isEmpty() ? "" : "&sku=" + sku);
            android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url));
            i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
        } catch (Exception ignored) {}
        call.resolve();
    }

    @PluginMethod
    public void appleSignIn(PluginCall call) { call.reject("Not available on Android"); }

    /**
     * Continue with Google (1.1.3). Google does not allow its sign-in page inside apps, so the
     * app signs in natively and hands the site Google's signed ID token: { clientId } gives
     * { idToken } or { cancelled: true }. clientId is the site's public Google web client ID.
     */
    @PluginMethod
    public void googleSignIn(PluginCall call) {
        String clientId = call.getString("clientId", "");
        if (clientId == null || clientId.isEmpty()) { call.reject("Google sign-in is not set up yet."); return; }
        try {
            com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption opt =
                new com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption.Builder(clientId).build();
            androidx.credentials.GetCredentialRequest req = new androidx.credentials.GetCredentialRequest.Builder()
                .addCredentialOption(opt).build();
            androidx.credentials.CredentialManager cm = androidx.credentials.CredentialManager.Companion.create(getContext());
            cm.getCredentialAsync(getActivity(), req, new android.os.CancellationSignal(),
                androidx.core.content.ContextCompat.getMainExecutor(getContext()),
                new androidx.credentials.CredentialManagerCallback<androidx.credentials.GetCredentialResponse, androidx.credentials.exceptions.GetCredentialException>() {
                    @Override
                    public void onResult(androidx.credentials.GetCredentialResponse res) {
                        androidx.credentials.Credential c = res.getCredential();
                        if (c instanceof androidx.credentials.CustomCredential
                            && com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(c.getType())) {
                            try {
                                com.google.android.libraries.identity.googleid.GoogleIdTokenCredential g =
                                    com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.createFrom(c.getData());
                                JSObject r = new JSObject();
                                r.put("idToken", g.getIdToken());
                                r.put("givenName", g.getGivenName() == null ? "" : g.getGivenName());
                                r.put("familyName", g.getFamilyName() == null ? "" : g.getFamilyName());
                                call.resolve(r);
                                return;
                            } catch (Exception e) {
                                call.reject("Google sign-in did not work. Please try again.");
                                return;
                            }
                        }
                        call.reject("Google sign-in did not work. Please try again.");
                    }

                    @Override
                    public void onError(androidx.credentials.exceptions.GetCredentialException e) {
                        if (e instanceof androidx.credentials.exceptions.GetCredentialCancellationException) {
                            JSObject r = new JSObject();
                            r.put("cancelled", true);
                            call.resolve(r);
                        } else {
                            call.reject("Google sign-in did not work. Please try again.");
                        }
                    }
                });
        } catch (Exception e) {
            call.reject("Google sign-in did not work. Please try again.");
        }
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

    /** Android launchers show their own notification dots, so the count is not set by hand. */
    @PluginMethod
    public void setBadge(PluginCall call) { call.resolve(); }

    /** A red count on a tab, for unread chat messages: { action: "chat", count: 2 }. 0 clears it. */
    @PluginMethod
    public void setTabBadge(PluginCall call) {
        String action = call.getString("action", "chat");
        Integer n = call.getInt("count", 0);
        int count = n == null ? 0 : Math.max(0, n);
        getActivity().runOnUiThread(() -> {
            if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).setTabBadge(action, count);
            call.resolve();
        });
    }

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
