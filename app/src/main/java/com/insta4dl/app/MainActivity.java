package com.insta4dl.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.window.OnBackInvokedDispatcher;

import androidx.webkit.JavaScriptReplyProxy;
import androidx.webkit.WebMessageCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final String HOME = "https://www.insta4dl.com/";
    private static final int FILE_CHOOSER_REQUEST = 4187;
    private static final long MAX_BLOB_BYTES = 700L * 1024 * 1024;
    private static final int MAX_CHUNK_BASE64_CHARS = 160_000;
    private static final int BG = Color.rgb(18, 13, 35);
    private static final int ACCENT = Color.rgb(192, 95, 248);
    private static final Pattern INSTAGRAM_LINK = Pattern.compile(
            "https?://(?:www\\.|m\\.)?(?:instagram\\.com|instagr\\.am)/[^\\s<>\"']+",
            Pattern.CASE_INSENSITIVE);

    private WebView webView;
    private ProgressBar progress;
    private LinearLayout errorPanel;
    private ValueCallback<Uri[]> filePathCallback;
    private String pendingInstagramUrl;
    private boolean documentStartBridgeInstalled;
    private boolean mainFrameFailed;
    private final Map<String, PendingBlob> blobs = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
        buildUi();
        configureWebView();
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, () -> {
                        if (webView.canGoBack()) webView.goBack();
                        else finish();
                    });
        }
        receiveIntent(getIntent());
        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            Uri data = getIntent() != null ? getIntent().getData() : null;
            if (data != null && isTrustedSite(data)) {
                webView.loadUrl(data.toString());
            } else {
                webView.loadUrl(HOME);
            }
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        // Android 15+ enforces edge-to-edge for apps targeting API 35.
        if (Build.VERSION.SDK_INT >= 35) {
            root.setOnApplyWindowInsetsListener((view, windowInsets) -> {
                Insets insets = windowInsets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(insets.left, insets.top, insets.right, insets.bottom);
                return windowInsets;
            });
        }

        FrameLayout browserArea = new FrameLayout(this);
        webView = new WebView(this);
        browserArea.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgressTintList(ColorStateList.valueOf(ACCENT));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3), Gravity.TOP);
        browserArea.addView(progress, progressParams);

        errorPanel = new LinearLayout(this);
        errorPanel.setOrientation(LinearLayout.VERTICAL);
        errorPanel.setGravity(Gravity.CENTER);
        errorPanel.setPadding(dp(25), dp(18), dp(25), dp(18));
        errorPanel.setBackgroundColor(Color.WHITE);
        TextView errorTitle = new TextView(this);
        errorTitle.setText("Can't load Insta4DL");
        errorTitle.setTextColor(BG);
        errorTitle.setTextSize(23);
        errorTitle.setGravity(Gravity.CENTER);
        errorPanel.addView(errorTitle);
        TextView errorDesc = new TextView(this);
        errorDesc.setText("Check your connection and try again.");
        errorDesc.setTextColor(Color.DKGRAY);
        errorDesc.setTextSize(14);
        errorDesc.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        descParams.topMargin = dp(10);
        errorPanel.addView(errorDesc, descParams);
        TextView retryButton = new TextView(this);
        retryButton.setText("Retry");
        retryButton.setTextColor(Color.WHITE);
        retryButton.setTextSize(15);
        retryButton.setGravity(Gravity.CENTER);
        retryButton.setBackgroundColor(ACCENT);
        retryButton.setPadding(dp(36), dp(12), dp(36), dp(12));
        retryButton.setOnClickListener(v -> {
            errorPanel.setVisibility(View.GONE);
            webView.reload();
        });
        LinearLayout.LayoutParams retryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        retryParams.topMargin = dp(20);
        errorPanel.addView(retryButton, retryParams);
        errorPanel.setVisibility(View.GONE);
        browserArea.addView(errorPanel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        root.addView(browserArea, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        root.addView(makeBottomBar());
        setContentView(root);
    }

    private View makeBottomBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setBackgroundColor(BG);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        addBarAction(bar, "⌂  Home", () -> webView.loadUrl(HOME));
        addBarAction(bar, "▣  Paste", this::pasteClipboard);
        addBarAction(bar, "↓  Files", this::openDownloads);
        addBarAction(bar, "↻  Reload", () -> webView.reload());
        return bar;
    }

    private void addBarAction(LinearLayout bar, String label, Runnable action) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setGravity(Gravity.CENTER);
        button.setTextColor(Color.WHITE);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setPadding(dp(3), dp(16), dp(3), dp(16));
        button.setOnClickListener(v -> action.run());
        bar.addView(button, new LinearLayout.LayoutParams(0, dp(55), 1f));
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setSafeBrowsingEnabled(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false);
        WebView.setWebContentsDebuggingEnabled(false);

        setupTrustedBlobDownloadBridge();

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                return interceptNavigation(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return interceptNavigation(Uri.parse(url));
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                mainFrameFailed = false;
                errorPanel.setVisibility(View.GONE);
                progress.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
                if (!mainFrameFailed) errorPanel.setVisibility(View.GONE);
                if (isTrustedSite(Uri.parse(url))) {
                    if (!documentStartBridgeInstalled &&
                            WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                        webView.evaluateJavascript(readAsset("blob-bridge.js"), null);
                    }
                    insertPendingInstagramLink();
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    mainFrameFailed = true;
                    errorPanel.setVisibility(View.VISIBLE);
                    progress.setVisibility(View.GONE);
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(newProgress);
                progress.setVisibility(newProgress < 100 ? View.VISIBLE : View.GONE);
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), FILE_CHOOSER_REQUEST);
                    return true;
                } catch (ActivityNotFoundException ex) {
                    filePathCallback = null;
                    toast("No file picker available");
                    return false;
                }
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            if (url.startsWith("blob:")) {
                String filename = URLUtil.guessFileName("file", contentDisposition, mimeType);
                downloadBlobUrl(url, filename);
            } else {
                enqueueHttpDownload(url, userAgent, contentDisposition, mimeType);
            }
        });
    }

    private void setupTrustedBlobDownloadBridge() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return;
        Set<String> allowedOrigins = new HashSet<>(Arrays.asList(
                "https://www.insta4dl.com", "https://insta4dl.com"));
        WebViewCompat.addWebMessageListener(webView, "Insta4DLNative", allowedOrigins,
                (view, message, sourceOrigin, isMainFrame, replyProxy) -> {
                    if (!isMainFrame || !isTrustedSite(sourceOrigin) ||
                            message.getType() != WebMessageCompat.TYPE_STRING) return;
                    processBlobMessage(message.getData(), replyProxy);
                });
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(webView, readAsset("blob-bridge.js"), allowedOrigins);
            documentStartBridgeInstalled = true;
        }
    }

    private void processBlobMessage(String raw, JavaScriptReplyProxy reply) {
        JSONObject answer = new JSONObject();
        try {
            if (raw == null || raw.length() > MAX_CHUNK_BASE64_CHARS + 1000) {
                throw new IOException("Message too large");
            }
            JSONObject data = new JSONObject(raw);
            int seq = data.getInt("seq");
            answer.put("seq", seq);
            String operation = data.getString("op");
            String id = data.getString("id");
            if (!id.matches("[a-zA-Z0-9_-]{1,64}")) throw new IOException("Invalid ID");
            switch (operation) {
                case "start": {
                    if (blobs.size() >= 3) throw new IOException("Too many concurrent downloads");
                    long declaredSize = data.getLong("size");
                    if (declaredSize <= 0 || declaredSize > MAX_BLOB_BYTES)
                        throw new IOException("Unsupported file size");
                    if (blobs.containsKey(id)) throw new IOException("Duplicate file ID");
                    String mime = data.optString("mime", "application/octet-stream");
                    if (!isSupportedMime(mime)) throw new IOException("Unsupported file type");
                    String filename = cleanFilename(data.optString("name", "insta4dl-download"), mime);
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Downloads.DISPLAY_NAME, filename);
                    values.put(MediaStore.Downloads.MIME_TYPE, mime);
                    values.put(MediaStore.Downloads.RELATIVE_PATH,
                            Environment.DIRECTORY_DOWNLOADS + "/Insta4DL");
                    values.put(MediaStore.Downloads.IS_PENDING, 1);
                    Uri fileUri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (fileUri == null) throw new IOException("Cannot create download");
                    try {
                        OutputStream stream = getContentResolver().openOutputStream(fileUri, "w");
                        if (stream == null) throw new IOException("Cannot open download");
                        blobs.put(id, new PendingBlob(fileUri, stream, declaredSize));
                    } catch (IOException ex) {
                        getContentResolver().delete(fileUri, null, null);
                        throw ex;
                    }
                    break;
                }
                case "chunk": {
                    PendingBlob blob = blobs.get(id);
                    if (blob == null) throw new IOException("Download missing");
                    String content = data.getString("base64");
                    if (content.length() > MAX_CHUNK_BASE64_CHARS) throw new IOException("Chunk too large");
                    byte[] bytes = android.util.Base64.decode(content, android.util.Base64.DEFAULT);
                    if (blob.written + bytes.length > blob.expected)
                        throw new IOException("File exceeds declared size");
                    try {
                        blob.stream.write(bytes);
                        blob.written += bytes.length;
                    } catch (IOException ex) {
                        discardBlob(id);
                        throw ex;
                    }
                    break;
                }
                case "end": {
                    PendingBlob blob = blobs.get(id);
                    if (blob == null) throw new IOException("Download missing");
                    if (blob.written != blob.expected) {
                        discardBlob(id);
                        throw new IOException("File is incomplete");
                    }
                    blob.stream.close();
                    ContentValues done = new ContentValues();
                    done.put(MediaStore.Downloads.IS_PENDING, 0);
                    getContentResolver().update(blob.uri, done, null, null);
                    blobs.remove(id);
                    toast("Saved to Downloads/Insta4DL");
                    break;
                }
                case "abort": {
                    discardBlob(id);
                    break;
                }
                default:
                    throw new IOException("Unknown message");
            }
            answer.put("ok", true);
        } catch (Exception ex) {
            try {
                answer.put("ok", false);
                answer.put("error", ex.getMessage() == null ? "Download failed" : ex.getMessage());
            } catch (JSONException ignored) { }
        }
        reply.postMessage(answer.toString());
    }

    private static class PendingBlob {
        final Uri uri;
        final OutputStream stream;
        final long expected;
        long written;
        PendingBlob(Uri uri, OutputStream stream, long expected) {
            this.uri = uri;
            this.stream = stream;
            this.expected = expected;
        }
    }

    private void discardBlob(String id) {
        PendingBlob blob = blobs.remove(id);
        if (blob == null) return;
        try { blob.stream.close(); } catch (IOException ignored) { }
        getContentResolver().delete(blob.uri, null, null);
    }

    private static boolean isSupportedMime(String mime) {
        return mime.startsWith("video/") || mime.startsWith("image/") ||
                mime.startsWith("audio/") || mime.equals("application/zip") ||
                mime.equals("application/octet-stream") || mime.equals("application/x-zip-compressed");
    }

    private static String cleanFilename(String filename, String mime) {
        String name = filename.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (name.length() > 100) name = name.substring(0, 100);
        if (name.isEmpty() || name.equals(".") || name.equals("..")) name = "insta4dl-download";
        if (!name.matches("(?s).*\\.[A-Za-z0-9]{2,5}$")) {
            String ext = "bin";
            if (mime.equals("video/mp4")) ext = "mp4";
            else if (mime.equals("image/jpeg")) ext = "jpg";
            else if (mime.equals("image/png")) ext = "png";
            else if (mime.equals("image/webp")) ext = "webp";
            else if (mime.equals("application/zip") || mime.equals("application/x-zip-compressed")) ext = "zip";
            else if (mime.equals("audio/mpeg")) ext = "mp3";
            name += "." + ext;
        }
        return name;
    }

    private void enqueueHttpDownload(String url, String userAgent, String disposition, String mimeType) {
        try {
            Uri uri = Uri.parse(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()))
                throw new IllegalArgumentException("Only HTTPS downloads are supported");
            String filename = cleanFilename(URLUtil.guessFileName(url, disposition, mimeType),
                    mimeType == null ? "application/octet-stream" : mimeType);
            DownloadManager.Request request = new DownloadManager.Request(uri);
            request.setTitle(filename);
            request.setDescription("Insta4DL download");
            if (mimeType != null && !mimeType.isEmpty()) request.setMimeType(mimeType);
            if (userAgent != null) request.addRequestHeader("User-Agent", userAgent);
            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null && !cookies.isEmpty()) request.addRequestHeader("Cookie", cookies);
            request.addRequestHeader("Referer", HOME);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(true);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
                    "Insta4DL/" + filename);
            DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            manager.enqueue(request);
            toast("Downloading to Downloads/Insta4DL");
        } catch (Exception ex) {
            toast("Download failed. Try opening the file in Chrome.");
        }
    }

    private void downloadBlobUrl(String blobUrl, String filename) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            toast("Update Android System WebView to download this file");
            return;
        }
        String js = "window.__insta4dlSaveBlobURL && window.__insta4dlSaveBlobURL(" +
                JSONObject.quote(blobUrl) + "," + JSONObject.quote(filename) + ");";
        webView.evaluateJavascript(js, null);
    }

    private boolean interceptNavigation(Uri uri) {
        if (uri == null) return true;
        if (isTrustedSite(uri)) return false;
        if ("blob".equalsIgnoreCase(uri.getScheme())) {
            downloadBlobUrl(uri.toString(), "insta4dl-download");
            return true;
        }
        if ("https".equalsIgnoreCase(uri.getScheme()) && isMediaLink(uri)) {
            enqueueHttpDownload(uri.toString(), webView.getSettings().getUserAgentString(), null, null);
            return true;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception ex) {
            toast("No application can open this link");
        }
        return true;
    }

    private static boolean isMediaLink(Uri uri) {
        String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
        return path.matches("(?s).*\\.(mp4|webm|jpg|jpeg|png|webp|gif|zip|mp3|m4a)$");
    }

    private static boolean isTrustedSite(Uri uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())) return false;
        String host = uri.getHost();
        return "www.insta4dl.com".equalsIgnoreCase(host) ||
                "insta4dl.com".equalsIgnoreCase(host);
    }

    private void pasteClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()) {
            toast("Copy an Instagram link first");
            return;
        }
        ClipData data = clipboard.getPrimaryClip();
        CharSequence text = data != null && data.getItemCount() > 0
                ? data.getItemAt(0).coerceToText(this) : null;
        String link = text == null ? null : extractInstagramLink(text.toString());
        if (link == null) {
            toast("Clipboard does not contain an Instagram link");
            return;
        }
        pendingInstagramUrl = link;
        if (webView.getUrl() == null || !isTrustedSite(Uri.parse(webView.getUrl()))) {
            webView.loadUrl(HOME);
        } else {
            insertPendingInstagramLink();
        }
    }

    private void receiveIntent(Intent intent) {
        if (intent == null) return;
        if (Intent.ACTION_SEND.equals(intent.getAction()) &&
                "text/plain".equals(intent.getType())) {
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            pendingInstagramUrl = extractInstagramLink(text);
            if (pendingInstagramUrl == null) toast("Share a public Instagram link");
        }
    }

    private static String extractInstagramLink(String text) {
        if (text == null) return null;
        Matcher matcher = INSTAGRAM_LINK.matcher(text);
        if (!matcher.find()) return null;
        String url = matcher.group().replaceAll("[.,)]+$", "");
        return url.length() > 2048 ? null : url;
    }

    private void insertPendingInstagramLink() {
        if (pendingInstagramUrl == null) return;
        String link = pendingInstagramUrl;
        String js = "(function(){" +
                "var el=document.querySelector('input[placeholder*=\"Instagram\" i]," +
                "input[placeholder*=\"link\" i],input[type=url],input[type=text]');" +
                "if(!el)return false;" +
                "var setter=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;" +
                "setter.call(el," + JSONObject.quote(link) + ");" +
                "el.dispatchEvent(new Event('input',{bubbles:true}));" +
                "el.dispatchEvent(new Event('change',{bubbles:true}));" +
                "el.focus();el.scrollIntoView({block:'center',behavior:'smooth'});" +
                "return true;" +
                "})()";
        webView.evaluateJavascript(js, result -> {
            if ("true".equals(result) && link.equals(pendingInstagramUrl)) {
                pendingInstagramUrl = null;
                toast("Instagram link is ready — tap Download");
            }
        });
    }

    private void openDownloads() {
        try {
            Intent intent = new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS);
            startActivity(intent);
        } catch (Exception ex) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("content://com.android.externalstorage.documents/root/primary")));
            } catch (Exception ignored) {
                toast("Open Files → Downloads → Insta4DL");
            }
        }
    }

    private String readAsset(String filename) {
        try (InputStream input = getAssets().open(filename);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toString(StandardCharsets.UTF_8.name());
        } catch (IOException ex) {
            return "";
        }
    }

    private int dp(int size) {
        return Math.round(getResources().getDisplayMetrics().density * size);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        receiveIntent(intent);
        if (pendingInstagramUrl != null) webView.loadUrl(HOME);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent result) {
        super.onActivityResult(requestCode, resultCode, result);
        if (requestCode == FILE_CHOOSER_REQUEST && filePathCallback != null) {
            filePathCallback.onReceiveValue(
                    WebChromeClient.FileChooserParams.parseResult(resultCode, result));
            filePathCallback = null;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        if (webView != null) webView.saveState(state);
        super.onSaveInstanceState(state);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onPause() {
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }

    @Override
    protected void onDestroy() {
        for (String id : new HashSet<>(blobs.keySet())) discardBlob(id);
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }
}
