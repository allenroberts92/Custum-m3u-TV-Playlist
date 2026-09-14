package com.chris.msceandroid;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private static final String INSTALLER_NAME = "MSCE.1.5.13.Installer.Win64.exe";
    private static final String DOWNLOAD_SUBDIR = Environment.DIRECTORY_DOWNLOADS + "/MSCE/";
    private static final String EXPECTED_SHA256 = "30c5d0070cdfca7dc7b0f1c973468b12995ff3e14685a1308790257aa030fefe";

    private TextView statusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(16, 19, 26));
        getWindow().setNavigationBarColor(Color.rgb(16, 19, 26));

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(16, 19, 26));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(24), dp(28), dp(24), dp(28));
        scroll.addView(panel, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        TextView title = text("MSCE 1.5.13\nAndroid Launcher", 30, Color.WHITE);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        title.setPadding(0, 0, 0, dp(18));
        panel.addView(title);

        TextView badge = text("WINDOWS COMPATIBILITY BUILD", 13, Color.rgb(180, 194, 255));
        badge.setGravity(Gravity.CENTER_HORIZONTAL);
        badge.setPadding(0, 0, 0, dp(20));
        panel.addView(badge);

        TextView explanation = text(
                "This APK packages the exact Moonscraper Chart Editor 1.5.13 Win64 installer and prepares it for use with Winlator. " +
                "It is not a native Unity Android port.\n\n" +
                "1. Tap Prepare Moonscraper.\n" +
                "2. Tap Open Winlator.\n" +
                "3. Create/open a 64-bit Winlator container.\n" +
                "4. In Winlator, open Downloads/MSCE and run " + INSTALLER_NAME + ".\n" +
                "5. After installation, create a Winlator shortcut for Moonscraper.",
                17, Color.rgb(226, 230, 239));
        explanation.setLineSpacing(0, 1.12f);
        explanation.setPadding(0, 0, 0, dp(22));
        panel.addView(explanation);

        Button prepare = button("Prepare Moonscraper");
        prepare.setOnClickListener(v -> exportInstaller());
        panel.addView(prepare);

        Button openWinlator = button("Open Winlator");
        LinearLayout.LayoutParams openParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(54));
        openParams.topMargin = dp(12);
        openWinlator.setLayoutParams(openParams);
        openWinlator.setOnClickListener(v -> openWinlator());
        panel.addView(openWinlator);

        statusView = text("Ready. Installer SHA-256:\n" + EXPECTED_SHA256, 13, Color.rgb(166, 176, 197));
        statusView.setPadding(0, dp(22), 0, 0);
        statusView.setTextIsSelectable(true);
        panel.addView(statusView);

        setContentView(scroll);
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(17);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setBackgroundColor(Color.rgb(49, 87, 213));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(54));
        button.setLayoutParams(params);
        return button;
    }

    private TextView text(String value, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void exportInstaller() {
        statusView.setText("Preparing installer…");
        new Thread(() -> {
            try {
                ContentResolver resolver = getContentResolver();
                Uri downloads = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);

                deleteOldCopy(resolver, downloads);

                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, INSTALLER_NAME);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "application/x-msdownload");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, DOWNLOAD_SUBDIR);
                values.put(MediaStore.MediaColumns.IS_PENDING, 1);

                Uri destination = resolver.insert(downloads, values);
                if (destination == null) {
                    throw new IllegalStateException("Android could not create the Downloads file.");
                }

                try (InputStream input = getAssets().open(INSTALLER_NAME);
                     OutputStream output = resolver.openOutputStream(destination, "w")) {
                    if (output == null) {
                        throw new IllegalStateException("Android could not open the Downloads file.");
                    }
                    byte[] buffer = new byte[64 * 1024];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        output.write(buffer, 0, count);
                    }
                    output.flush();
                } catch (Exception copyError) {
                    resolver.delete(destination, null, null);
                    throw copyError;
                }

                values.clear();
                values.put(MediaStore.MediaColumns.IS_PENDING, 0);
                resolver.update(destination, values, null, null);

                runOnUiThread(() -> statusView.setText(
                        "Prepared successfully.\nSaved to Downloads/MSCE/" + INSTALLER_NAME +
                        "\n\nNext: tap Open Winlator."));
            } catch (Exception e) {
                runOnUiThread(() -> statusView.setText("Could not prepare installer:\n" + e.getMessage()));
            }
        }).start();
    }

    private void deleteOldCopy(ContentResolver resolver, Uri downloads) {
        String selection = MediaStore.MediaColumns.DISPLAY_NAME + "=? AND " +
                MediaStore.MediaColumns.RELATIVE_PATH + "=?";
        String[] args = new String[]{INSTALLER_NAME, DOWNLOAD_SUBDIR};
        try (Cursor cursor = resolver.query(downloads,
                new String[]{MediaStore.MediaColumns._ID}, selection, args, null)) {
            if (cursor == null) return;
            while (cursor.moveToNext()) {
                long id = cursor.getLong(0);
                resolver.delete(ContentUris.withAppendedId(downloads, id), null, null);
            }
        } catch (Exception ignored) {
            // If an older copy cannot be removed, Android can still create a new Downloads item.
        }
    }

    private void openWinlator() {
        String[] packageNames = new String[]{
                "com.winlator",
                "com.winlator.cmod",
                "com.winlator.bionic",
                "com.ludashi.benchmark"
        };

        for (String packageName : packageNames) {
            Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
            if (launchIntent != null) {
                startActivity(launchIntent);
                return;
            }
        }

        statusView.setText("Winlator is not installed. Opening the official Winlator releases page…");
        Intent browser = new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://github.com/brunodev85/winlator/releases/latest"));
        startActivity(browser);
    }
}
