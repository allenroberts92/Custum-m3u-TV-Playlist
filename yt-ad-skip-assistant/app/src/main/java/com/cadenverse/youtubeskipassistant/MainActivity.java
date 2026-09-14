package com.cadenverse.youtubeskipassistant;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends Activity {
    private TextView statusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.rgb(18, 18, 18));

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(24), dp(36), dp(24), dp(36));
        scrollView.addView(container, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = text("YouTube Ad Skip Assistant", 28, Color.WHITE);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        container.addView(title);

        TextView subtitle = text(
                "Automatically taps YouTube's ad-skip and ad-close controls when they become available.",
                17,
                Color.rgb(210, 210, 210));
        subtitle.setPadding(0, dp(14), 0, dp(24));
        container.addView(subtitle);

        statusView = text("", 18, Color.WHITE);
        statusView.setPadding(dp(16), dp(16), dp(16), dp(16));
        statusView.setBackgroundColor(Color.rgb(38, 38, 38));
        container.addView(statusView, matchWrap());

        Button accessibilityButton = new Button(this);
        accessibilityButton.setText("Open Accessibility Settings");
        accessibilityButton.setTextSize(16);
        accessibilityButton.setAllCaps(false);
        LinearLayout.LayoutParams buttonParams = matchWrap();
        buttonParams.setMargins(0, dp(22), 0, 0);
        container.addView(accessibilityButton, buttonParams);
        accessibilityButton.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });

        Button youtubeButton = new Button(this);
        youtubeButton.setText("Open YouTube");
        youtubeButton.setTextSize(16);
        youtubeButton.setAllCaps(false);
        LinearLayout.LayoutParams youtubeParams = matchWrap();
        youtubeParams.setMargins(0, dp(12), 0, 0);
        container.addView(youtubeButton, youtubeParams);
        youtubeButton.setOnClickListener(v -> openYouTube());

        TextView how = text(
                "Setup\n\n1. Tap “Open Accessibility Settings.”\n2. Find YouTube Ad Skip Assistant and turn it on.\n3. Open the normal YouTube app and use it as usual.\n\nThe service is restricted to the YouTube app and does not require your Google password or network access.",
                16,
                Color.rgb(220, 220, 220));
        how.setPadding(0, dp(28), 0, 0);
        container.addView(how);

        TextView note = text(
                "Limitation: this app cannot remove an unskippable ad before YouTube provides a skip/close control. YouTube can also change its interface at any time, which may require an app update.",
                14,
                Color.rgb(170, 170, 170));
        note.setPadding(0, dp(28), 0, 0);
        container.addView(note);

        setContentView(scrollView);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private void updateStatus() {
        String enabledServices = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        String component = new ComponentName(this, YouTubeAdSkipService.class).flattenToString();

        boolean enabled = false;
        if (enabledServices != null) {
            String target = component.toLowerCase(Locale.ROOT);
            for (String item : enabledServices.split(":")) {
                if (item.trim().toLowerCase(Locale.ROOT).equals(target)) {
                    enabled = true;
                    break;
                }
            }
        }

        if (enabled) {
            statusView.setText("Status: Enabled ✓");
            statusView.setTextColor(Color.rgb(120, 235, 150));
        } else {
            statusView.setText("Status: Disabled — enable the Accessibility service");
            statusView.setTextColor(Color.rgb(255, 190, 105));
        }
    }

    private void openYouTube() {
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage("com.google.android.youtube");
        if (launchIntent != null) {
            startActivity(launchIntent);
        } else {
            Toast.makeText(this, "The YouTube app was not found on this device.", Toast.LENGTH_LONG).show();
        }
    }

    private TextView text(String value, int sizeSp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        return view;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
