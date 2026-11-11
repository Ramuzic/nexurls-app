package com.nexurls.app;

import android.app.Activity;
import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PluginCall;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Google Play Billing for the app (1.1.3).
 *
 * Pro plans are subscriptions (ids with ".pro."), NexCredit packs are one-time products.
 * Every purchase goes to the site, which checks it with Google before granting it; only then
 * does the app acknowledge it (subscriptions) or consume it (credit packs, so they can be
 * bought again). Anything not finished is offered to the site again next time.
 */
class PlayStore implements PurchasesUpdatedListener {

    private final Activity activity;
    private final BillingClient client;
    private PluginCall buying;
    private String buyingId = "";

    PlayStore(Activity activity) {
        this.activity = activity;
        this.client = BillingClient.newBuilder(activity)
            .setListener(this)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build();
    }

    private static String type(String id) {
        return id != null && id.contains(".pro.") ? BillingClient.ProductType.SUBS : BillingClient.ProductType.INAPP;
    }

    private interface Ready { void run(); }

    private void ready(PluginCall call, Ready r) {
        if (client.isReady()) { r.run(); return; }
        client.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult res) {
                if (res.getResponseCode() == BillingClient.BillingResponseCode.OK) r.run();
                else call.reject("Google Play is not available right now. Please try again.");
            }

            @Override
            public void onBillingServiceDisconnected() {}
        });
    }

    /** Looks up product details for ids of one type. */
    private interface Details { void got(List<ProductDetails> list); }

    private void details(List<String> ids, String type, Details d) {
        if (ids.isEmpty()) { d.got(new ArrayList<>()); return; }
        List<QueryProductDetailsParams.Product> ps = new ArrayList<>();
        for (String id : ids) ps.add(QueryProductDetailsParams.Product.newBuilder().setProductId(id).setProductType(type).build());
        client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(ps).build(), (res, result) -> {
            List<ProductDetails> list = result == null ? null : result.getProductDetailsList();
            d.got(list == null ? new ArrayList<>() : list);
        });
    }

    private static String price(ProductDetails p) {
        if (BillingClient.ProductType.SUBS.equals(p.getProductType())) {
            List<ProductDetails.SubscriptionOfferDetails> offers = p.getSubscriptionOfferDetails();
            if (offers == null || offers.isEmpty()) return "";
            // The base plan's regular price (the last phase is the one that repeats).
            List<ProductDetails.PricingPhase> phases = offers.get(0).getPricingPhases().getPricingPhaseList();
            for (ProductDetails.SubscriptionOfferDetails o : offers) {
                if (o.getOfferId() == null) { phases = o.getPricingPhases().getPricingPhaseList(); break; }
            }
            return phases.isEmpty() ? "" : phases.get(phases.size() - 1).getFormattedPrice();
        }
        ProductDetails.OneTimePurchaseOfferDetails o = p.getOneTimePurchaseOfferDetails();
        return o == null ? "" : o.getFormattedPrice();
    }

    void products(List<String> ids, PluginCall call) {
        ready(call, () -> {
            List<String> subs = new ArrayList<>(), inapp = new ArrayList<>();
            for (String id : ids) (BillingClient.ProductType.SUBS.equals(type(id)) ? subs : inapp).add(id);
            details(subs, BillingClient.ProductType.SUBS, a -> details(inapp, BillingClient.ProductType.INAPP, b -> {
                JSArray out = new JSArray();
                List<ProductDetails> all = new ArrayList<>(a);
                all.addAll(b);
                for (ProductDetails p : all) {
                    JSObject o = new JSObject();
                    o.put("id", p.getProductId());
                    o.put("price", price(p));
                    o.put("title", p.getName());
                    out.put(o);
                }
                JSObject r = new JSObject();
                r.put("products", out);
                call.resolve(r);
            }));
        });
    }

    void buy(String id, String token, PluginCall call) {
        if (id == null || id.isEmpty()) { call.reject("Unknown product."); return; }
        if (buying != null) { call.reject("A purchase is already open."); return; }
        String t = type(id);
        ready(call, () -> details(Collections.singletonList(id), t, list -> {
            if (list.isEmpty()) { call.reject("This product is not available right now."); return; }
            ProductDetails p = list.get(0);
            BillingFlowParams.ProductDetailsParams.Builder pp = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(p);
            if (BillingClient.ProductType.SUBS.equals(t)) {
                List<ProductDetails.SubscriptionOfferDetails> offers = p.getSubscriptionOfferDetails();
                if (offers == null || offers.isEmpty()) { call.reject("This plan is not available right now."); return; }
                String offer = offers.get(0).getOfferToken();
                for (ProductDetails.SubscriptionOfferDetails o : offers) { if (o.getOfferId() == null) { offer = o.getOfferToken(); break; } }
                pp.setOfferToken(offer);
            }
            BillingFlowParams.Builder fb = BillingFlowParams.newBuilder().setProductDetailsParamsList(Collections.singletonList(pp.build()));
            if (token != null && !token.isEmpty()) fb.setObfuscatedAccountId(token.replace("-", "").substring(0, Math.min(64, token.replace("-", "").length())));
            buying = call;
            buyingId = id;
            activity.runOnUiThread(() -> {
                BillingResult r = client.launchBillingFlow(activity, fb.build());
                if (r.getResponseCode() != BillingClient.BillingResponseCode.OK && buying != null) {
                    PluginCall c = buying;
                    buying = null;
                    c.reject("Google Play could not open the purchase. Please try again.");
                }
            });
        }));
    }

    private static JSObject item(Purchase p) {
        JSObject o = new JSObject();
        o.put("purchaseToken", p.getPurchaseToken());
        o.put("productId", p.getProducts().isEmpty() ? "" : p.getProducts().get(0));
        o.put("orderId", p.getOrderId() == null ? "" : p.getOrderId());
        o.put("packageName", p.getPackageName());
        o.put("state", p.getPurchaseState() == Purchase.PurchaseState.PURCHASED ? "purchased" : "pending");
        o.put("acknowledged", p.isAcknowledged());
        return o;
    }

    @Override
    public void onPurchasesUpdated(BillingResult res, List<Purchase> purchases) {
        PluginCall c = buying;
        buying = null;
        if (c == null) return;
        int code = res.getResponseCode();
        if (code == BillingClient.BillingResponseCode.USER_CANCELED) {
            JSObject r = new JSObject();
            r.put("status", "cancelled");
            c.resolve(r);
            return;
        }
        if (code == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            JSObject r = new JSObject();
            r.put("status", "owned");
            c.resolve(r);
            return;
        }
        if (code != BillingClient.BillingResponseCode.OK || purchases == null || purchases.isEmpty()) {
            c.reject("The purchase did not go through. Please try again.");
            return;
        }
        Purchase p = purchases.get(0);
        for (Purchase x : purchases) if (x.getProducts().contains(buyingId)) { p = x; break; }
        JSObject r = item(p);
        r.put("status", p.getPurchaseState() == Purchase.PurchaseState.PURCHASED ? "purchased" : "pending");
        c.resolve(r);
    }

    /** onlyOpen: just purchases still waiting to be finished. */
    void owned(boolean onlyOpen, PluginCall call) {
        ready(call, () -> client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(), (r1, subs) ->
            client.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(), (r2, inapp) -> {
                    JSArray items = new JSArray();
                    List<Purchase> all = new ArrayList<>();
                    if (subs != null) all.addAll(subs);
                    if (inapp != null) all.addAll(inapp);
                    for (Purchase p : all) {
                        if (p.getPurchaseState() != Purchase.PurchaseState.PURCHASED) continue;
                        boolean credit = !p.getProducts().isEmpty() && !p.getProducts().get(0).contains(".pro.");
                        if (onlyOpen && p.isAcknowledged() && !credit) continue;
                        items.put(item(p));
                    }
                    JSObject r = new JSObject();
                    r.put("items", items);
                    call.resolve(r);
                })));
    }

    void finish(String purchaseToken, boolean consume, PluginCall call) {
        if (purchaseToken == null || purchaseToken.isEmpty()) { call.resolve(); return; }
        ready(call, () -> {
            if (consume) {
                client.consumeAsync(ConsumeParams.newBuilder().setPurchaseToken(purchaseToken).build(), (res, tok) -> call.resolve());
            } else {
                client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchaseToken).build(), res -> call.resolve());
            }
        });
    }
}
