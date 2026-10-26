package com.nexurls.app;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.WebViewListener;
import java.util.ArrayList;
import java.util.List;

/**
 * The app's main screen: the site in Capacitor's web view, a native tab bar
 * under it, a native offline screen, and the first-launch onboarding.
 */
public class MainActivity extends BridgeActivity {

    private static MainActivity current;
    private static String pendingUrl;

    private static final String CHAT_JS =
        "(function(){try{if(window.MobileApp&&typeof MobileApp.open==='function'){MobileApp.open();return 'ok';}" +
        "if(window.Widget&&typeof Widget.open==='function'){Widget.open();return 'ok';}" +
        "var b=document.getElementById('bmc-mobile-btn')||document.getElementById('bmc-pill');if(b){b.click();return 'ok';}}catch(e){}return 'no';})()";

    private AppConfig cfg;
    private LinearLayout tabBar;
    private final List<LinearLayout> tabViews = new ArrayList<>();
    private int selected = 0;
    private FrameLayout offlineView;
    private boolean offline = false;
    private boolean pendingChat = false;
    private boolean tabHidden = false;
    private boolean typing = false;
    private ConnectivityManager.NetworkCallback netCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        registerPlugin(NexAppPlugin.class);
        super.onCreate(savedInstanceState);
        current = this;
        cfg = AppConfig.get(this);

        buildShell();
        watchWebView();
        watchNetwork();

        if (!getSharedPreferences("nexapp", MODE_PRIVATE).getBoolean("onboarded", false) && !cfg.onboarding.isEmpty()) {
            startActivity(new Intent(this, OnboardingActivity.class));
        }
        if (pendingUrl != null) {
            String u = pendingUrl;
            pendingUrl = null;
            openUrl(u);
        }
        openFromIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        openFromIntent(intent);
    }

    /** A nexurls.com link tapped in another app, or a home screen quick action. */
    private void openFromIntent(Intent intent) {
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction()) || intent.getData() == null) return;
        String url = intent.getData().toString();
        if (!cfg.isOwn(url)) return;
        setIntent(new Intent(this, MainActivity.class));
        openUrl(url);
    }

    @Override
    public void onResume() {
        super.onResume();
        applyBarColors();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        getWindow().getDecorView().post(this::applyBarColors);
    }

    @Override
    public void onDestroy() {
        if (netCallback != null) {
            try {
                ((ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE)).unregisterNetworkCallback(netCallback);
            } catch (Exception ignored) {}
        }
        if (current == this) current = null;
        super.onDestroy();
    }

    /** Called by MainApplication when a notification is tapped. */
    public static void openFromPush(String url) {
        MainActivity a = current;
        if (a == null) {
            pendingUrl = url;
            return;
        }
        a.runOnUiThread(() -> a.openUrl(url));
    }

    /** Links to the site stay in the app; anything else opens in the browser. */
    void openUrl(String url) {
        WebView web = bridge != null ? bridge.getWebView() : null;
        if (cfg.isOwn(url) && web != null) {
            web.loadUrl(url);
        } else {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception ignored) {}
        }
    }

    // ---------- layout ----------

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    private void buildShell() {
        ViewGroup content = findViewById(android.R.id.content);
        View capacitorRoot = content.getChildAt(0);
        content.removeView(capacitorRoot);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(cfg.top);

        FrameLayout webArea = new FrameLayout(this);
        webArea.setBackgroundColor(cfg.paper);
        webArea.addView(capacitorRoot, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        offlineView = buildOffline();
        webArea.addView(offlineView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(webArea, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        tabBar = buildTabBar();
        root.addView(tabBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        content.addView(root, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (bridge != null && bridge.getWebView() != null) bridge.getWebView().setBackgroundColor(cfg.paper);

        // Draw edge to edge and place everything ourselves: the page sits below the
        // status bar, the tab bar runs under the gesture bar, and the keyboard pushes
        // the page up (the tab bar hides while typing).
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            typing = insets.isVisible(WindowInsetsCompat.Type.ime());
            root.setPadding(bars.left, bars.top, bars.right, typing ? ime.bottom : 0);
            tabBar.setVisibility(typing || tabHidden ? View.GONE : View.VISIBLE);
            tabBar.setPadding(0, 0, 0, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        applyBarColors();
    }

    /** Hides the tab bar while a website is open full screen, and brings it back. */
    void setTabBarHidden(boolean hidden) {
        if (hidden == tabHidden || tabBar == null) return;
        tabHidden = hidden;
        tabBar.setVisibility(typing || tabHidden ? View.GONE : View.VISIBLE);
    }

    /** Dark icons on the light top bar; light gesture bar over the dark tab bar. */
    private void applyBarColors() {
        WindowInsetsControllerCompat c = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        c.setAppearanceLightStatusBars(true);
        c.setAppearanceLightNavigationBars(false);
    }

    private LinearLayout buildTabBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(cfg.bar);
        bar.setElevation(dp(8));
        for (int i = 0; i < cfg.tabs.size(); i++) {
            final int index = i;
            AppConfig.Tab t = cfg.tabs.get(i);

            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(0, dp(7), 0, dp(6));
            TypedValue ripple = new TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true);
            item.setBackgroundResource(ripple.resourceId);
            item.setContentDescription(t.label);

            ImageView icon = new ImageView(this);
            icon.setImageResource(AppConfig.icon(t.icon));
            item.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));

            TextView label = new TextView(this);
            label.setText(t.label);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(1);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(3);
            item.addView(label, lp);

            item.setOnClickListener(v -> onTab(index, v));
            bar.addView(item, new LinearLayout.LayoutParams(0, dp(56), 1f));
            tabViews.add(item);
        }
        paintTabs();
        return bar;
    }

    private void paintTabs() {
        for (int i = 0; i < tabViews.size(); i++) {
            LinearLayout item = tabViews.get(i);
            int col = i == selected ? cfg.active : cfg.inactive;
            ((ImageView) item.getChildAt(0)).setImageTintList(ColorStateList.valueOf(col));
            TextView label = (TextView) item.getChildAt(1);
            label.setTextColor(col);
            label.setTypeface(Typeface.DEFAULT, i == selected ? Typeface.BOLD : Typeface.NORMAL);
            item.setSelected(i == selected);
        }
    }

    private void onTab(int index, View v) {
        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        AppConfig.Tab t = cfg.tabs.get(index);
        WebView web = bridge != null ? bridge.getWebView() : null;
        boolean same = index == selected;
        selected = index;
        paintTabs();
        if (web == null) return;

        if ("chat".equals(t.action)) {
            openChat(web);
            return;
        }
        // Tapping the tab you are already on scrolls back to the top, like other apps.
        if (same && score(t, web.getUrl()) > 0) {
            web.evaluateJavascript("window.scrollTo({top:0,behavior:'smooth'})", null);
            return;
        }
        web.loadUrl(t.url);
    }

    /** How well a tab matches a page: same path, and every query value on the tab present on the page. */
    private int score(AppConfig.Tab t, String url) {
        if (url == null || "chat".equals(t.action) || !cfg.isOwn(url)) return 0;
        Uri want = Uri.parse(t.url), have = Uri.parse(url);
        if (!clean(want.getPath()).equals(clean(have.getPath()))) {
            // Extra paths for this tab (Home also lights on the site root).
            if (have.getQuery() != null && !have.getQuery().isEmpty()) return 0;
            for (String m : t.match) if (clean(m).equals(clean(have.getPath()))) return 1;
            return 0;
        }
        int s = 1;
        for (String k : want.getQueryParameterNames()) {
            String w = want.getQueryParameter(k);
            String h = have.getQueryParameter(k);
            if (h == null || !h.equals(w)) return 0;
            s++;
        }
        return s;
    }

    private static String clean(String p) {
        if (p == null || p.isEmpty()) return "/";
        while (p.length() > 1 && p.endsWith("/")) p = p.substring(0, p.length() - 1);
        return p;
    }

    private void syncTab(String url) {
        int best = -1, bestScore = 0;
        for (int i = 0; i < cfg.tabs.size(); i++) {
            int s = score(cfg.tabs.get(i), url);
            if (s > bestScore) { bestScore = s; best = i; }
        }
        // A page no tab points at (a single link, a profile) keeps the last tab lit.
        if (best >= 0 && best != selected) {
            selected = best;
            paintTabs();
        }
    }

    // ---------- chat ----------

    private void openChat(WebView web) {
        web.evaluateJavascript(CHAT_JS, value -> {
            if (!"\"ok\"".equals(value)) {
                // Chat lives on the site's pages; open home, then the chat once it has loaded.
                pendingChat = true;
                web.loadUrl(cfg.start);
            }
        });
    }

    // ---------- web view ----------

    private void watchWebView() {
        if (bridge == null) return;
        bridge.addWebViewListener(new WebViewListener() {
            @Override
            public void onPageStarted(WebView webView) {
                // A new page always starts with the tabs showing.
                setTabBarHidden(false);
            }

            @Override
            public void onPageLoaded(WebView webView) {
                syncTab(webView.getUrl());
                if (pendingChat) {
                    pendingChat = false;
                    webView.postDelayed(() -> webView.evaluateJavascript(CHAT_JS, null), 800);
                }
            }

            @Override
            public void onPageCommitVisible(WebView view, String url) {
                syncTab(url);
            }
        });
    }

    // ---------- offline ----------

    private FrameLayout buildOffline() {
        FrameLayout box = new FrameLayout(this);
        box.setBackgroundColor(cfg.paper);
        box.setVisibility(View.GONE);
        box.setClickable(true);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(dp(32), 0, dp(32), 0);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_offline);
        icon.setImageTintList(ColorStateList.valueOf(cfg.active));
        col.addView(icon, new LinearLayout.LayoutParams(dp(56), dp(56)));

        TextView title = new TextView(this);
        title.setText(cfg.offlineTitle);
        title.setTextColor(cfg.ink);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.topMargin = dp(16);
        col.addView(title, tp);

        TextView text = new TextView(this);
        text.setText(cfg.offlineText);
        text.setTextColor(cfg.muted);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        text.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams xp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        xp.topMargin = dp(8);
        col.addView(text, xp);

        Button retry = new Button(this);
        retry.setText(cfg.offlineButton);
        retry.setAllCaps(false);
        retry.setTextColor(cfg.ink);
        retry.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        retry.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(cfg.active);
        bg.setCornerRadius(dp(24));
        retry.setBackground(bg);
        retry.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if (isOnline()) {
                setOffline(false);
                if (bridge != null && bridge.getWebView() != null) bridge.getWebView().reload();
            }
        });
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(dp(200), dp(48));
        rp.topMargin = dp(22);
        col.addView(retry, rp);

        box.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        return box;
    }

    private boolean isOnline() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            Network n = cm.getActiveNetwork();
            NetworkCapabilities caps = n == null ? null : cm.getNetworkCapabilities(n);
            return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Exception e) {
            return true;
        }
    }

    private void watchNetwork() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            netCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    runOnUiThread(() -> {
                        if (offline) {
                            setOffline(false);
                            if (bridge != null && bridge.getWebView() != null) bridge.getWebView().reload();
                        }
                    });
                }

                @Override
                public void onLost(Network network) {
                    runOnUiThread(() -> { if (!isOnline()) setOffline(true); });
                }
            };
            cm.registerDefaultNetworkCallback(netCallback);
        } catch (Exception ignored) {}
        if (!isOnline()) setOffline(true);
    }

    private void setOffline(boolean off) {
        offline = off;
        offlineView.setVisibility(off ? View.VISIBLE : View.GONE);
    }
}
