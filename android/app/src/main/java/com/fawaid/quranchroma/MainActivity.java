package com.fawaid.quranchroma;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.webkit.WebViewAssetLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * القرآن كروما — تطوير: أبو عبد الرحمن أحمد الأثري
 * يعرض التطبيق (web/index.html) داخل WebView، ويضيف حفظ الفيديو في المعرض واختيار الملفات.
 */
public class MainActivity extends Activity {

    private static final String START_URL = "https://appassets.androidplatform.net/assets/index.html";
    private static final int FILE_REQ = 1001;

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;

    // حالة الحفظ الجاري
    private OutputStream out;
    private Uri savedUri;
    private File savedFile;
    private String savedMime = "video/mp4";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        web.setBackgroundColor(0xFF12110E);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if ("appassets.androidplatform.net".equals(u.getHost())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (ActivityNotFoundException e) {
                    toast("لا يوجد تطبيق لفتح الرابط");
                }
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                // نحترم نوع الملف المطلوب (صوت، فيديو، أو صورة)
                java.util.ArrayList<String> types = new java.util.ArrayList<>();
                for (String t : params.getAcceptTypes()) {
                    if (t == null) continue;
                    for (String part : t.split(",")) {
                        part = part.trim();
                        if (part.contains("/")) types.add(part);
                    }
                }
                if (types.isEmpty()) types.add("*/*");
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType(types.size() == 1 ? types.get(0) : "*/*");
                if (types.size() > 1) i.putExtra(Intent.EXTRA_MIME_TYPES, types.toArray(new String[0]));
                try {
                    startActivityForResult(Intent.createChooser(i, "اختر ملفاً"), FILE_REQ);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });

        web.addJavascriptInterface(new Bridge(), "QCBridge");

        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl(START_URL);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (web != null) web.saveState(outState);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_REQ && fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            fileCallback = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }

    private void toast(final String msg) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show());
    }

    /** الجسر بين صفحة التطبيق والأندرويد */
    private class Bridge {

        @JavascriptInterface
        public boolean begin(String name, String mime) {
            try {
                closeQuietly();
                savedUri = null;
                savedFile = null;
                savedMime = (mime == null || mime.isEmpty()) ? "video/mp4" : mime;
                String safe = name.replaceAll("[\\\\/:*?\"<>|]", "-");
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.MediaColumns.DISPLAY_NAME, safe);
                    cv.put(MediaStore.MediaColumns.MIME_TYPE, savedMime);
                    cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/QuranChroma");
                    cv.put(MediaStore.MediaColumns.IS_PENDING, 1);
                    savedUri = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv);
                    if (savedUri == null) return false;
                    out = getContentResolver().openOutputStream(savedUri);
                } else {
                    File dir = getExternalFilesDir(Environment.DIRECTORY_MOVIES);
                    if (dir == null) return false;
                    if (!dir.exists() && !dir.mkdirs()) return false;
                    savedFile = new File(dir, safe);
                    out = new FileOutputStream(savedFile);
                }
                return out != null;
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public boolean chunk(String b64) {
            try {
                if (out == null) return false;
                out.write(Base64.decode(b64, Base64.DEFAULT));
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public String finish() {
            try {
                if (out != null) {
                    out.flush();
                    out.close();
                    out = null;
                }
                if (Build.VERSION.SDK_INT >= 29 && savedUri != null) {
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.MediaColumns.IS_PENDING, 0);
                    getContentResolver().update(savedUri, cv, null, null);
                    return "في المعرض: Movies/QuranChroma";
                } else if (savedFile != null) {
                    MediaScannerConnection.scanFile(MainActivity.this, new String[]{savedFile.getAbsolutePath()}, new String[]{savedMime}, null);
                    return "في: " + savedFile.getAbsolutePath();
                }
            } catch (Exception e) {
                return "";
            }
            return "";
        }

        @JavascriptInterface
        public void share() {
            runOnUiThread(() -> {
                try {
                    Uri u = savedUri;
                    if (u == null) {
                        toast("احفظ الفيديو أولاً");
                        return;
                    }
                    Intent send = new Intent(Intent.ACTION_SEND);
                    send.setType(savedMime);
                    send.putExtra(Intent.EXTRA_STREAM, u);
                    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(send, "مشاركة الفيديو"));
                } catch (Exception e) {
                    toast("تعذّرت المشاركة");
                }
            });
        }

        private void closeQuietly() {
            try {
                if (out != null) out.close();
            } catch (Exception ignored) {
            }
            out = null;
        }
    }
}
