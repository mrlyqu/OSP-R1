package pl.pspr1.lista;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.webkit.*;
import android.widget.Toast;
import androidx.webkit.WebViewAssetLoader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final String HOST = "appassets.androidplatform.net";
    private WebView web;
    private ValueCallback<Uri[]> chooser;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this)).build();
        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);

        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                return loader.shouldInterceptRequest(r.getUrl());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                if (HOST.equals(r.getUrl().getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, r.getUrl())); } catch (ActivityNotFoundException e) { }
                return true;
            }
        });

        // Import pliku (<input type="file">)
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (chooser != null) chooser.onReceiveValue(null);
                chooser = cb;
                try { startActivityForResult(p.createIntent(), 1); }
                catch (Exception e) { chooser = null; return false; }
                return true;
            }
        });

        // Eksport pliku (blob: -> folder Pobrane)
        web.addJavascriptInterface(new Object() {
            @JavascriptInterface public void save(String name, String text) { saveToDownloads(name, text); }
        }, "AndroidSave");
        web.setDownloadListener((url, ua, cd, mime, len) -> {
            if (!url.startsWith("blob:")) return;
            String name = URLUtil.guessFileName(url, cd, mime).replaceAll("[^A-Za-z0-9._-]", "_");
            web.evaluateJavascript("fetch('" + url + "').then(function(r){return r.text()})"
                    + ".then(function(t){AndroidSave.save('" + name + "',t)})", null);
        });

        web.loadUrl("https://" + HOST + "/assets/index.html");
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == 1 && chooser != null) {
            chooser.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
            chooser = null;
        }
    }

    private void saveToDownloads(String name, String text) {
        try {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, name);
            v.put(MediaStore.Downloads.MIME_TYPE, "application/json");
            Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            try (OutputStream o = getContentResolver().openOutputStream(u)) {
                o.write(text.getBytes(StandardCharsets.UTF_8));
            }
            runOnUiThread(() -> Toast.makeText(this, "Zapisano w Pobranych: " + name, Toast.LENGTH_LONG).show());
        } catch (Exception e) {
            runOnUiThread(() -> Toast.makeText(this, "Nie udało się zapisać pliku", Toast.LENGTH_LONG).show());
        }
    }
}
