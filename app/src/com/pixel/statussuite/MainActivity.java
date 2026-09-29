package com.pixel.statussuite;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.DataOutputStream;

public class MainActivity extends Activity {
    private WebView mWebView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mWebView = new WebView(this);
        setContentView(mWebView);

        WebSettings ws = mWebView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(true);

        mWebView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");
        mWebView.loadUrl("file:///android_asset/index.html");
    }

    private class AndroidBridge {
        @JavascriptInterface
        public void applyConfig(final String jsonStr) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        JSONObject obj = new JSONObject(jsonStr);
                        int iconScale = obj.optInt("iconScale", 100);
                        int batWidth = obj.optInt("batWidth", 26);
                        int batHeight = obj.optInt("batHeight", 13);
                        int spacing = obj.optInt("spacing", 8);
                        String batteryMode = obj.optString("batteryMode", "capsule");

                        String cmd = "sh /data/local/tmp/apply_status_config.sh " 
                            + iconScale + " " + batWidth + " " + batHeight + " " + spacing + " " + batteryMode;
                        runSu(cmd);

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "Applied Successfully!", Toast.LENGTH_SHORT).show();
                            }
                        });
                    } catch (final Exception e) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                }
            }).start();
        }

        @JavascriptInterface
        public void restartSystemUI() {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    runSu("pkill -f com.android.systemui");
                }
            }).start();
        }
    }

    private void runSu(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-mm", "-c", cmd});
            p.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
