package com.nexurls.app;

import android.annotation.SuppressLint;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.onesignal.Continue;
import com.onesignal.OneSignal;

/** Native first-launch screens: swipe or tap through, ask for notifications on the last one. */
public class OnboardingActivity extends AppCompatActivity {

    private AppConfig cfg;
    private int page = 0;
    private FrameLayout pageBox;
    private LinearLayout dots;
    private Button primary;
    private Button secondary;

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cfg = AppConfig.get(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(cfg.paper);

        pageBox = new FrameLayout(this);
        root.addView(pageBox, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        dots = new LinearLayout(this);
        dots.setOrientation(LinearLayout.HORIZONTAL);
        dots.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(20));
        dlp.bottomMargin = dp(18);
        root.addView(dots, dlp);

        primary = new Button(this);
        primary.setAllCaps(false);
        primary.setTextColor(cfg.ink);
        primary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        primary.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(cfg.active);
        bg.setCornerRadius(dp(26));
        primary.setBackground(bg);
        primary.setOnClickListener(v -> onPrimary(v));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        plp.leftMargin = dp(28);
        plp.rightMargin = dp(28);
        root.addView(primary, plp);

        secondary = new Button(this);
        secondary.setAllCaps(false);
        secondary.setTextColor(cfg.muted);
        secondary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        secondary.setBackgroundColor(0x00000000);
        secondary.setOnClickListener(v -> onSecondary());
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48));
        slp.gravity = Gravity.CENTER_HORIZONTAL;
        slp.topMargin = dp(4);
        slp.bottomMargin = dp(10);
        root.addView(secondary, slp);

        setContentView(root);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            root.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        WindowInsetsControllerCompat c = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        c.setAppearanceLightStatusBars(true);
        c.setAppearanceLightNavigationBars(true);

        // Swipe left or right between pages.
        GestureDetector swipe = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) { return true; }

            @Override
            public boolean onFling(MotionEvent a, MotionEvent b, float vx, float vy) {
                if (a == null || b == null) return false;
                float dx = b.getX() - a.getX();
                if (Math.abs(dx) < dp(60) || Math.abs(dx) < Math.abs(b.getY() - a.getY())) return false;
                if (dx < 0 && page < cfg.onboarding.size() - 1) show(page + 1);
                else if (dx > 0 && page > 0) show(page - 1);
                return true;
            }
        });
        pageBox.setOnTouchListener((v, e) -> swipe.onTouchEvent(e));

        // Back closes the intro; it is not shown again.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() { finishIntro(); }
        });

        show(0);
    }

    private View pageView(AppConfig.Page p) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setPadding(dp(32), 0, dp(32), 0);

        FrameLayout circle = new FrameLayout(this);
        GradientDrawable oval = new GradientDrawable();
        oval.setShape(GradientDrawable.OVAL);
        oval.setColor(cfg.active);
        circle.setBackground(oval);
        ImageView icon = new ImageView(this);
        icon.setImageResource(AppConfig.icon(p.icon));
        icon.setImageTintList(ColorStateList.valueOf(cfg.ink));
        circle.addView(icon, new FrameLayout.LayoutParams(dp(60), dp(60), Gravity.CENTER));
        col.addView(circle, new LinearLayout.LayoutParams(dp(128), dp(128)));

        TextView title = new TextView(this);
        title.setText(p.title);
        title.setTextColor(cfg.ink);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.topMargin = dp(32);
        col.addView(title, tp);

        TextView text = new TextView(this);
        text.setText(p.text);
        text.setTextColor(cfg.muted);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        text.setGravity(Gravity.CENTER);
        text.setLineSpacing(0, 1.2f);
        LinearLayout.LayoutParams xp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        xp.topMargin = dp(16);
        col.addView(text, xp);
        return col;
    }

    private boolean isLast() { return page >= cfg.onboarding.size() - 1; }

    private void show(int i) {
        page = i;
        View v = pageView(cfg.onboarding.get(i));
        v.setAlpha(0f);
        pageBox.removeAllViews();
        pageBox.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        v.animate().alpha(1f).setDuration(220).start();

        dots.removeAllViews();
        for (int d = 0; d < cfg.onboarding.size(); d++) {
            View dot = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(d == i ? cfg.active : 0x26000000);
            dot.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(8), dp(8));
            lp.leftMargin = dp(4);
            lp.rightMargin = dp(4);
            dots.addView(dot, lp);
        }

        boolean push = cfg.onboarding.get(i).push;
        if (isLast() && push) {
            primary.setText("Turn on notifications");
            secondary.setText("Not now");
        } else if (isLast()) {
            primary.setText("Get started");
            secondary.setText("");
        } else {
            primary.setText("Next");
            secondary.setText("Skip");
        }
    }

    private void onPrimary(View v) {
        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        if (!isLast()) { show(page + 1); return; }
        if (cfg.onboarding.get(page).push) {
            try {
                OneSignal.getNotifications().requestPermission(true, Continue.none());
            } catch (Exception ignored) {}
        }
        finishIntro();
    }

    private void onSecondary() {
        if (isLast()) finishIntro();
        else show(cfg.onboarding.size() - 1);
    }

    private void finishIntro() {
        getSharedPreferences("nexapp", MODE_PRIVATE).edit().putBoolean("onboarded", true).apply();
        finish();
    }
}
