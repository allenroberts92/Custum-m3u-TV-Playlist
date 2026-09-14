package com.audjust.mobilewrapper;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private static final String HOME_URL = "https://www.audjust.com/";
    private static final int FILE_CHOOSER_REQUEST = 1001;
    private static final int STORAGE_PERMISSION_REQUEST = 1002;

    private WebView webView;
    private ProgressBar progressBar;
    private ValueCallback<Uri[]> filePathCallback;

    private String pendingDownloadUrl;
    private String pendingDownloadDisposition;
    private String pendingDownloadMime;
    private String pendingBlobUrl;
    private String pendingBlobName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFFFFFFFF);

        webView = new WebView(this);
        FrameLayout.LayoutParams webParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        root.addView(webView, webParams);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(3)
        );
        progressParams.gravity = Gravity.TOP;
        root.addView(progressBar, progressParams);

        setContentView(root);
        configureWebView();

        if (savedInstanceState == null) {
            webView.loadUrl(HOME_URL);
        } else {
            webView.restoreState(savedInstanceState);
        }
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        }

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true);
        }

        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleNavigation(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleNavigation(Uri.parse(url));
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }

            @Override
            public boolean onShowFileChooser(
                    WebView webView,
                    ValueCallback<Uri[]> callback,
                    FileChooserParams fileChooserParams
            ) {
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }
                filePathCallback = callback;

                try {
                    Intent chooserIntent = fileChooserParams.createIntent();
                    chooserIntent.addCategory(Intent.CATEGORY_OPENABLE);
                    startActivityForResult(chooserIntent, FILE_CHOOSER_REQUEST);
                    return true;
                } catch (Exception e) {
                    filePathCallback = null;
                    Toast.makeText(MainActivity.this, "Unable to open the file picker.", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            if (url != null && url.startsWith("blob:")) {
                String fileName = makeFileName(contentDisposition, mimeType);
                if (needsLegacyStoragePermission()) {
                    pendingBlobUrl = url;
                    pendingBlobName = fileName;
                    requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION_REQUEST);
                } else {
                    exportBlob(url, fileName);
                }
            } else {
                startDownload(url, contentDisposition, mimeType);
            }
        });
    }

    private boolean handleNavigation(Uri uri) {
        if (uri == null || uri.getScheme() == null) {
            return false;
        }

        String scheme = uri.getScheme().toLowerCase();
        if ("http".equals(scheme) || "https".equals(scheme)) {
            String host = uri.getHost();
            if (host != null && (host.equals("audjust.com") || host.endsWith(".audjust.com"))) {
                return false;
            }
            openExternal(uri);
            return true;
        }

        if ("blob".equals(scheme)) {
            return false;
        }

        openExternal(uri);
        return true;
    }

    private void openExternal(Uri uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception e) {
            Toast.makeText(this, "No app can open this link.", Toast.LENGTH_SHORT).show();
        }
    }

    private void startDownload(String url, String contentDisposition, String mimeType) {
        if (url == null || url.isEmpty()) {
            return;
        }

        if (needsLegacyStoragePermission()) {
            pendingDownloadUrl = url;
            pendingDownloadDisposition = contentDisposition;
            pendingDownloadMime = mimeType;
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION_REQUEST);
            return;
        }

        try {
            String fileName = makeFileName(contentDisposition, mimeType);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setTitle(fileName);
            request.setDescription("Downloading from Audjust");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            if (mimeType != null && !mimeType.isEmpty()) {
                request.setMimeType(mimeType);
            }

            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null) {
                request.addRequestHeader("Cookie", cookies);
            }
            request.addRequestHeader("User-Agent", webView.getSettings().getUserAgentString());
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

            DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            manager.enqueue(request);
            Toast.makeText(this, "Download started.", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Could not start the download.", Toast.LENGTH_LONG).show();
        }
    }

    private void exportBlob(String blobUrl, String fileName) {
        String js = "(async()=>{try{" +
                "const r=await fetch(" + JSONObject.quote(blobUrl) + ");" +
                "const b=await r.blob();" +
                "const fr=new FileReader();" +
                "fr.onloadend=()=>AndroidBridge.saveDataUrl(fr.result," + JSONObject.quote(fileName) + ");" +
                "fr.onerror=()=>AndroidBridge.showError('Unable to read exported audio');" +
                "fr.readAsDataURL(b);" +
                "}catch(e){AndroidBridge.showError(String(e));}})();";
        webView.evaluateJavascript(js, null);
    }

    private String makeFileName(String contentDisposition, String mimeType) {
        String guessed = URLUtil.guessFileName("audjust-export", contentDisposition, mimeType);
        if (guessed == null || guessed.trim().isEmpty() || "audjust-export".equals(guessed)) {
            String extension = "mp3";
            if (mimeType != null) {
                if (mimeType.contains("wav")) extension = "wav";
                else if (mimeType.contains("mp4") || mimeType.contains("m4a")) extension = "m4a";
                else if (mimeType.contains("ogg")) extension = "ogg";
            }
            guessed = "audjust-export-" + System.currentTimeMillis() + "." + extension;
        }
        return sanitizeFileName(guessed);
    }

    private String sanitizeFileName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "audjust-export-" + System.currentTimeMillis() + ".mp3";
        }
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private boolean needsLegacyStoragePermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST && filePathCallback != null) {
            Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            filePathCallback.onReceiveValue(result);
            filePathCallback = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != STORAGE_PERMISSION_REQUEST) {
            return;
        }

        boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (!granted) {
            pendingDownloadUrl = null;
            pendingBlobUrl = null;
            Toast.makeText(this, "Storage permission is required to save downloads on this Android version.", Toast.LENGTH_LONG).show();
            return;
        }

        if (pendingBlobUrl != null) {
            String url = pendingBlobUrl;
            String name = pendingBlobName;
            pendingBlobUrl = null;
            pendingBlobName = null;
            exportBlob(url, name);
        } else if (pendingDownloadUrl != null) {
            String url = pendingDownloadUrl;
            String disposition = pendingDownloadDisposition;
            String mime = pendingDownloadMime;
            pendingDownloadUrl = null;
            pendingDownloadDisposition = null;
            pendingDownloadMime = null;
            startDownload(url, disposition, mime);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.removeJavascriptInterface("AndroidBridge");
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.clearHistory();
            webView.destroy();
        }
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    public class AndroidBridge {
        @JavascriptInterface
        public void saveDataUrl(String dataUrl, String requestedName) {
            if (dataUrl == null || !dataUrl.startsWith("data:") || !dataUrl.contains(",")) {
                showError("The exported audio could not be saved.");
                return;
            }

            try {
                int comma = dataUrl.indexOf(',');
                String header = dataUrl.substring(5, comma);
                String mime = "application/octet-stream";
                int semicolon = header.indexOf(';');
                if (semicolon >= 0) {
                    mime = header.substring(0, semicolon);
                } else if (!header.isEmpty()) {
                    mime = header;
                }

                byte[] bytes = Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT);
                String fileName = sanitizeFileName(requestedName);
                saveBytes(bytes, fileName, mime);
            } catch (Exception e) {
                showError("The exported audio could not be saved.");
            }
        }

        @JavascriptInterface
        public void showError(String message) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show());
        }
    }

    private void saveBytes(byte[] bytes, String fileName, String mime) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Audjust");
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);

            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                throw new IllegalStateException("Could not create download file");
            }

            try (OutputStream stream = getContentResolver().openOutputStream(uri)) {
                if (stream == null) throw new IllegalStateException("Could not open download file");
                stream.write(bytes);
            }

            values.clear();
            values.put(MediaStore.MediaColumns.IS_PENDING, 0);
            getContentResolver().update(uri, values, null, null);
        } else {
            File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File audjustFolder = new File(downloads, "Audjust");
            if (!audjustFolder.exists() && !audjustFolder.mkdirs()) {
                throw new IllegalStateException("Could not create Audjust download folder");
            }
            try (OutputStream stream = new FileOutputStream(new File(audjustFolder, fileName))) {
                stream.write(bytes);
            }
        }

        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Saved to Downloads/Audjust", Toast.LENGTH_LONG).show());
    }
}
