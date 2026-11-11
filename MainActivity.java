package com.zuxy.ai;

import android.Manifest;
import android.app.Activity;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class MainActivity extends Activity {
    private WebView webView;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        webView = new WebView(this);
        webView.setBackgroundColor(0xFF070A14);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new Bridge(), "Zuxy");
        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 44);
        }
    }

    private class Bridge {
        @JavascriptInterface public void openNotificationAccess() {
            runOnUiThread(() -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        }
        @JavascriptInterface public void openUsageAccess() {
            runOnUiThread(() -> startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)));
        }
        @JavascriptInterface public void saveApiKey(String key) {
            getSharedPreferences("zuxy", MODE_PRIVATE).edit().putString("gemini_key", key.trim()).apply();
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Gemini key saved on this device", Toast.LENGTH_SHORT).show());
        }
        @JavascriptInterface public String getApiKeySet() {
            return getSharedPreferences("zuxy", MODE_PRIVATE).getString("gemini_key", "").isEmpty() ? "false" : "true";
        }
        @JavascriptInterface public void setPaused(boolean paused) {
            getSharedPreferences("zuxy", MODE_PRIVATE).edit().putBoolean("paused", paused).apply();
        }
        @JavascriptInterface public boolean isPaused() {
            return getSharedPreferences("zuxy", MODE_PRIVATE).getBoolean("paused", false);
        }
        @JavascriptInterface public void testVoice(String text) {
            runOnUiThread(() -> VoiceTest.speak(MainActivity.this, text));
        }
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
