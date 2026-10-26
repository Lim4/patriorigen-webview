package es.patriorigen.webview;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.net.http.SslError;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONObject;

public final class MainActivity extends Activity {
    private static final int NATIVE_SCAN_REQUEST = 5102;
    private static final String HOME_URL = "https://patriorigen.es";

    private WebView webView;
    private Button scanButton;
    private boolean initialScannerLaunched;
    private boolean pageReady;
    private String pendingBarcode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_FULLSCREEN);

        FrameLayout root = new FrameLayout(this);
        webView = new WebView(this);
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        scanButton = new Button(this);
        scanButton.setText("Escanear");
        scanButton.setTextColor(Color.WHITE);
        GradientDrawable buttonBackground = new GradientDrawable();
        buttonBackground.setColor(0xCC303030);
        buttonBackground.setCornerRadius(dp(20));
        scanButton.setBackground(buttonBackground);
        scanButton.setElevation(dp(8));
        scanButton.setOnClickListener(view -> launchNativeScanner());

        scanButton.setPadding(dp(18), 0, dp(18), 0);
        FrameLayout.LayoutParams buttonParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(44), Gravity.BOTTOM | Gravity.END);
        buttonParams.bottomMargin = dp(88);
        buttonParams.rightMargin = dp(16);
        root.addView(scanButton, buttonParams);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            int bottomInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            view.setPadding(0, 0, 0, bottomInset);
            return insets;
        });
        setContentView(root);
        ViewCompat.requestApplyInsets(root);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSafeBrowsingEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Uri uri = Uri.parse(url);
                String host = uri.getHost();
                if (!"https".equalsIgnoreCase(uri.getScheme())
                        || host == null
                        || !(host.equalsIgnoreCase("patriorigen.es")
                            || host.equalsIgnoreCase("www.patriorigen.es"))) return;

                pageReady = true;
                view.evaluateJavascript(
                        "(function(){"
                                + "var b=document.getElementById('startBtn');if(b)b.style.display='none';"
                                + "var s=document.getElementById('cameraStatus');if(s)s.style.display='none';"
                                + "var v=document.getElementById('video');if(v){v.pause();v.style.setProperty('display','none','important');}"
                                + "var p=document.getElementById('placeholder');"
                                + "if(p){var a=p.querySelector('strong'),c=p.querySelector('span');"
                                + "if(a)a.textContent='Cámara principal';"
                                + "if(c)c.textContent='Pulsa «Escanear con lente principal» para leer el código';}"
                                + "})();",
                        null);
                deliverPendingBarcode();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                return !"https".equalsIgnoreCase(uri.getScheme());
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(android.webkit.PermissionRequest request) {
                // Camera access is handled by CameraX in ScannerActivity.
                request.deny();
            }
        });

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl(HOME_URL);
        }

        webView.postDelayed(() -> {
            if (!initialScannerLaunched) {
                initialScannerLaunched = true;
                launchNativeScanner();
            }
        }, 1800);
    }

    private void launchNativeScanner() {
        if (isFinishing()) return;
        try {
            startActivityForResult(
                    new Intent(this, ScannerActivity.class), NATIVE_SCAN_REQUEST);
        } catch (RuntimeException error) {
            Toast.makeText(this, "No se pudo abrir la cámara principal.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != NATIVE_SCAN_REQUEST || resultCode != RESULT_OK || data == null) return;
        if (data.getBooleanExtra(ScannerActivity.EXTRA_OPEN_MANUAL, false)) {
            if (webView != null) {
                webView.evaluateJavascript(
                        "(function(){var b=document.getElementById('manualBtn');if(b)b.click();})();",
                        null);
            }
            return;
        }
        pendingBarcode = data.getStringExtra(ScannerActivity.EXTRA_BARCODE);
        deliverPendingBarcode();
    }

    private void deliverPendingBarcode() {
        if (!pageReady || pendingBarcode == null || webView == null) return;
        String code = pendingBarcode;
        pendingBarcode = null;
        String script = "(function(){"
                + "var input=document.getElementById('barcodeInput');"
                + "var form=document.getElementById('manualForm');"
                + "if(!input||!form)return;"
                + "input.value=" + JSONObject.quote(code) + ";"
                + "form.dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));"
                + "})();";
        webView.evaluateJavascript(script, null);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
