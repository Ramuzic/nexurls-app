package com.nexurls.app;

import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Everything the native shell needs, read from www/app.json (copied into the
 * app as assets/public/app.json by "npx cap copy"). Edit app.json to change
 * tabs, colors or onboarding text without touching code.
 */
public final class AppConfig {

    public static final class Tab {
        public final String label, url, icon, action;
        Tab(String label, String url, String icon, String action) {
            this.label = label; this.url = url; this.icon = icon; this.action = action;
        }
    }

    public static final class Page {
        public final String icon, title, text;
        public final boolean push;
        Page(String icon, String title, String text, boolean push) {
            this.icon = icon; this.title = title; this.text = text; this.push = push;
        }
    }

    private static AppConfig instance;

    public String start = "https://nexurls.com/app/";
    public String host = "nexurls.com";
    public String oneSignalAppId = "";
    public int top = Color.parseColor("#F4F0E8");
    public int bar = Color.parseColor("#121212");
    public int active = Color.parseColor("#FF5A1F");
    public int inactive = Color.parseColor("#B9B2A6");
    public int paper = Color.parseColor("#F4F0E8");
    public int ink = Color.parseColor("#121212");
    public int muted = Color.parseColor("#6B665E");
    public String offlineTitle = "You're offline";
    public String offlineText = "Check your connection and try again.";
    public String offlineButton = "Try again";
    public final List<Tab> tabs = new ArrayList<>();
    public final List<Page> onboarding = new ArrayList<>();

    public static synchronized AppConfig get(Context c) {
        if (instance == null) instance = new AppConfig(c.getApplicationContext());
        return instance;
    }

    private AppConfig(Context c) {
        try (InputStream in = c.getAssets().open("public/app.json")) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            JSONObject j = new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
            start = j.optString("start", start);
            host = j.optString("host", host);
            oneSignalAppId = j.optString("onesignal_app_id", "");
            JSONObject col = j.optJSONObject("colors");
            if (col != null) {
                top = color(col, "top", top);
                bar = color(col, "bar", bar);
                active = color(col, "active", active);
                inactive = color(col, "inactive", inactive);
                paper = color(col, "paper", paper);
                ink = color(col, "ink", ink);
                muted = color(col, "muted", muted);
            }
            JSONArray t = j.optJSONArray("tabs");
            if (t != null) {
                for (int i = 0; i < t.length(); i++) {
                    JSONObject o = t.getJSONObject(i);
                    tabs.add(new Tab(o.optString("label"), o.optString("url", start), o.optString("icon", "home"), o.optString("action", "navigate")));
                }
            }
            JSONArray ob = j.optJSONArray("onboarding");
            if (ob != null) {
                for (int i = 0; i < ob.length(); i++) {
                    JSONObject o = ob.getJSONObject(i);
                    onboarding.add(new Page(o.optString("icon", "next"), o.optString("title"), o.optString("text"), o.optBoolean("push", false)));
                }
            }
            JSONObject off = j.optJSONObject("offline");
            if (off != null) {
                offlineTitle = off.optString("title", offlineTitle);
                offlineText = off.optString("text", offlineText);
                offlineButton = off.optString("button", offlineButton);
            }
        } catch (Exception ignored) {
            // Fall back to the defaults above.
        }
        if (tabs.isEmpty()) tabs.add(new Tab("Home", start, "home", "navigate"));
    }

    private static int color(JSONObject o, String key, int def) {
        try { return Color.parseColor(o.optString(key)); } catch (Exception e) { return def; }
    }

    /** Vector icon for each icon name used in app.json. */
    public static int icon(String name) {
        switch (name) {
            case "home": return R.drawable.ic_tab_home;
            case "next": return R.drawable.ic_tab_next;
            case "reels": return R.drawable.ic_tab_reels;
            case "community": return R.drawable.ic_tab_community;
            case "chat": return R.drawable.ic_tab_chat;
            case "share": return R.drawable.ic_tab_share;
            case "bell": return R.drawable.ic_tab_bell;
            default: return R.drawable.ic_tab_home;
        }
    }

    /** True when a link belongs to the site and should stay inside the app. */
    public boolean isOwn(String url) {
        try {
            String h = Uri.parse(url).getHost();
            if (h == null) return false;
            h = h.toLowerCase();
            return h.equals(host) || h.endsWith("." + host);
        } catch (Exception e) {
            return false;
        }
    }
}
