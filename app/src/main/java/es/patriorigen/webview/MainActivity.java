package es.patriorigen.webview;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.provider.Settings;
import android.net.Uri;
import android.app.AlertDialog;
import android.graphics.Color;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Size;
import android.util.SizeF;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.net.http.SslError;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.camera.camera2.interop.Camera2CameraInfo;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.common.InputImage;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainActivity extends ComponentActivity {
    private static final int CAMERA_PERMISSION_REQUEST = 5101;
    private static final String HOME_URL = "https://patriorigen.es";
    private static final String PERMISSION_PREFS = "camera_permission_preferences";
    private static final String KEY_PERMISSION_DECLINED = "camera_permission_declined";

    private FrameLayout root;
    private PreviewView previewView;
    private WebView webView;
    private ProcessCameraProvider cameraProvider;
    private Camera camera;
    private BarcodeScanner barcodeScanner;
    private ExecutorService analysisExecutor;
    private final AtomicBoolean barcodeHandled = new AtomicBoolean(false);
    private boolean pageReady;
    private boolean cameraPaused;
    private boolean cameraStarted;
    private boolean permissionRequestInFlight;
    private boolean permissionDenied;
    private String pendingBarcode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        WindowInsetsControllerCompat systemBars =
                new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        systemBars.setAppearanceLightStatusBars(false);
        systemBars.setAppearanceLightNavigationBars(true);

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(160, 0, 20));
        View navigationBarScrim = new View(this);
        navigationBarScrim.setBackgroundColor(Color.rgb(245, 235, 235));
        FrameLayout.LayoutParams scrimParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, android.view.Gravity.BOTTOM);
        root.addView(navigationBarScrim, scrimParams);
        previewView = new PreviewView(this);
        previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        previewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        root.addView(previewView, new FrameLayout.LayoutParams(1, 1));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(0, bars.top, 0, bars.bottom);
            ViewGroup.LayoutParams rawParams = navigationBarScrim.getLayoutParams();
            FrameLayout.LayoutParams navParams = (FrameLayout.LayoutParams) rawParams;
            navParams.height = bars.bottom;
            navParams.bottomMargin = -bars.bottom;
            navigationBarScrim.setLayoutParams(navParams);
            return insets;
        });
        setContentView(root);
        ViewCompat.requestApplyInsets(root);

        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8,
                        Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
                .build();
        barcodeScanner = BarcodeScanning.getClient(options);
        analysisExecutor = Executors.newSingleThreadExecutor();

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSafeBrowsingEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        webView.addJavascriptInterface(new ScannerBridge(), "NativeScanner");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (!isTrustedUrl(url)) return;
                pageReady = true;
                installPatriorigenCameraLayer();
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA)
                        == PackageManager.PERMISSION_GRANTED) startCamera();
                else updateCameraButton(false, "Permite el acceso a la cámara para escanear");
                deliverPendingBarcode();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (isTrustedUrl(uri.toString())) return false;
                return true;
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(android.webkit.PermissionRequest request) {
                request.deny();
            }
        });

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED
                && !getSharedPreferences(PERMISSION_PREFS, MODE_PRIVATE)
                        .getBoolean(KEY_PERMISSION_DECLINED, false)) {
            requestCameraPermission(false);
        }

        if (savedInstanceState != null) webView.restoreState(savedInstanceState);
        else webView.loadUrl(HOME_URL);
    }

    private boolean isTrustedUrl(String value) {
        try {
            Uri uri = Uri.parse(value);
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme()) && host != null
                    && (host.equalsIgnoreCase("patriorigen.es")
                    || host.equalsIgnoreCase("www.patriorigen.es"));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void installPatriorigenCameraLayer() {
        String script = "(function(){"
                + "var style=document.getElementById('nativeCameraLayerStyle');"
                + "if(!style){style=document.createElement('style');style.id='nativeCameraLayerStyle';"
                + "style.textContent='html,body,.app,.scanner,#scanner{background:transparent!important}'"
                + "+'#video{visibility:hidden!important;opacity:0!important;pointer-events:none!important}'"
                + "+'#placeholder{display:none!important}';document.head.appendChild(style);}"
                + "var video=document.getElementById('video');if(video){try{video.pause()}catch(e){}"
                + "video.style.setProperty('visibility','hidden','important');video.style.setProperty('opacity','0','important');"
                + "var parent=video.parentElement;for(var i=0;parent&&i<5;i++,parent=parent.parentElement){"
                + "parent.style.setProperty('background-color','transparent','important');"
                + "parent.style.setProperty('background-image','none','important');"
                + "if(parent.classList.contains('scanner')||parent.id==='scanner')break;}}"
                + "var placeholder=document.getElementById('placeholder');if(placeholder)placeholder.style.setProperty('display','none','important');"
                + "var b=document.getElementById('startBtn');"
                + "if(b&&!b.dataset.nativeBound){var n=b.cloneNode(true);n.id=b.id;b.replaceWith(n);"
                + "n.dataset.nativeBound='1';n.textContent='◉  Cámara activa';"
                + "n.addEventListener('click',function(){NativeScanner.start()});}"
                + "var scanner=document.querySelector('.scanner')||document.getElementById('scanner');if(scanner)scanner.classList.add('active');"
                + "var status=document.getElementById('cameraStatus');if(status){status.style.display='';status.textContent='●  Buscando código…'}"
                + "var getViewport=function(){var v=document.getElementById('video');"
                + "if(v){var r=v.getBoundingClientRect();if(r.width>2&&r.height>2)return v;"
                + "if(v.parentElement){r=v.parentElement.getBoundingClientRect();if(r.width>2&&r.height>2)return v.parentElement;}}"
                + "return document.querySelector('.scanner-viewport,.scanner-frame,.camera-viewport,.camera-container,.scanner')"
                + "||document.getElementById('scanner');};"
                + "var update=function(){var s=getViewport();if(!s)return;var r=s.getBoundingClientRect();"
                + "if(r.width>2&&r.height>2)NativeScanner.setPreviewBounds(r.left,r.top,r.width,r.height)};"
                + "update();requestAnimationFrame(update);setTimeout(update,250);setTimeout(update,900);"
                + "window.addEventListener('resize',update);window.addEventListener('orientationchange',function(){setTimeout(update,250)});"
                + "var overlay=document.getElementById('resultOverlay');if(overlay&&!overlay.dataset.nativeObserver){"
                + "overlay.dataset.nativeObserver='1';new MutationObserver(function(){"
                + "if(overlay.classList.contains('show')||overlay.classList.contains('active'))NativeScanner.pause();"
                + "else NativeScanner.resume()}).observe(overlay,{attributes:true,attributeFilter:['class']});}"
                + "var cs=document.getElementById('cameraStatus');if(cs&&!cs.dataset.nativeStatusObserver){"
                + "cs.dataset.nativeStatusObserver='1';new MutationObserver(function(){"
                + "if(/denegad|rechazad/i.test(cs.textContent))cs.textContent='Permite el acceso a la cámara para escanear';"
                + "}).observe(cs,{childList:true,subtree:true,characterData:true});}"
                + "})();";
        webView.evaluateJavascript(script, null);
    }

    private final class ScannerBridge {
        @JavascriptInterface public void start() {
            runOnUiThread(() -> {
                if (!pageReady || cameraStarted) return;
                cameraPaused = false;
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA)
                        == PackageManager.PERMISSION_GRANTED) startCamera();
                else requestCameraPermission(true);
            });
        }
        @JavascriptInterface public void pause() {
            runOnUiThread(() -> {
                cameraPaused = true;
                if (cameraProvider != null) cameraProvider.unbindAll();
                camera = null;
                cameraStarted = false;
            });
        }
        @JavascriptInterface public void resume() {
            runOnUiThread(() -> {
                barcodeHandled.set(false);
                cameraPaused = false;
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA)
                        == PackageManager.PERMISSION_GRANTED) {
                    startCamera();
                } else {
                    requestCameraPermission(true);
                }
            });
        }
        @JavascriptInterface public void setPreviewBounds(float left, float top, float width, float height) {
            runOnUiThread(() -> {
                if (root == null || previewView == null || width < 1 || height < 1) return;
                float density = getResources().getDisplayMetrics().density;
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                        Math.max(1, Math.round(width * density)),
                        Math.max(1, Math.round(height * density)));
                lp.leftMargin = Math.round(left * density);
                lp.topMargin = Math.round(top * density);
                previewView.setLayoutParams(lp);
            });
        }
        @JavascriptInterface public void barcodeFound(String code) {
            runOnUiThread(() -> {
                if (code == null || !code.matches("[0-9]{8,14}") || !barcodeHandled.compareAndSet(false, true)) return;
                pendingBarcode = code;
                if (cameraProvider != null) cameraProvider.unbindAll();
                camera = null;
                cameraStarted = false;
                deliverPendingBarcode();
            });
        }
    }

    private void requestCameraPermission(boolean userInitiated) {
        if (permissionRequestInFlight) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            permissionDenied = false;
            getSharedPreferences(PERMISSION_PREFS, MODE_PRIVATE).edit()
                    .putBoolean(KEY_PERMISSION_DECLINED, false).apply();
            if (pageReady) startCamera();
            return;
        }
        boolean wasDeclined = getSharedPreferences(PERMISSION_PREFS, MODE_PRIVATE)
                .getBoolean(KEY_PERMISSION_DECLINED, false);
        if (userInitiated && wasDeclined
                && !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            showCameraSettingsDialog();
            return;
        }
        permissionRequestInFlight = true;
        requestPermissions(new String[] { Manifest.permission.CAMERA }, CAMERA_PERMISSION_REQUEST);
    }

    private void showCameraSettingsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Permiso de cámara")
                .setMessage("Para escanear, permite el acceso a la cámara en los ajustes de Android. También puedes introducir el código manualmente.")
                .setNegativeButton("Ahora no", (dialog, which) -> { })
                .setPositiveButton("Abrir ajustes", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                })
                .show();
    }

    private void startCamera() {
        if (cameraStarted || cameraPaused) return;
        cameraStarted = true;
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                bindCamera(cameraProvider, chooseMainRearCamera(cameraProvider));
            } catch (Exception error) {
                cameraStarted = false;
                updateCameraButton(false, "No se pudo abrir la cámara");
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCamera(ProcessCameraProvider provider, CameraSelector selector) {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());
        ImageAnalysis analysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();
        analysis.setAnalyzer(analysisExecutor, this::analyzeImage);
        try {
            provider.unbindAll();
            camera = provider.bindToLifecycle(this, selector, preview, analysis);
            previewView.post(() -> focusAt(previewView.getWidth() / 2f, previewView.getHeight() / 2f));
            updateCameraButton(true, "Buscando código…");
        } catch (RuntimeException error) {
            try {
                provider.unbindAll();
                camera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);
                updateCameraButton(true, "Buscando código…");
            } catch (RuntimeException fallback) {
                cameraStarted = false;
                updateCameraButton(false, "No se pudo abrir la cámara");
            }
        }
    }

    private CameraSelector chooseMainRearCamera(ProcessCameraProvider provider) {
        CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
        Candidate best = null;
        for (CameraInfo info : provider.getAvailableCameraInfos()) {
            try {
                String parent = Camera2CameraInfo.from(info).getCameraId();
                Integer facing = Camera2CameraInfo.from(info).getCameraCharacteristic(CameraCharacteristics.LENS_FACING);
                if (facing == null || facing != CameraCharacteristics.LENS_FACING_BACK) continue;
                CameraCharacteristics chars = manager.getCameraCharacteristics(parent);
                best = better(best, candidate(parent, parent, false, chars));
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    Set<String> ids = chars.getPhysicalCameraIds();
                    for (String id : ids) best = better(best,
                            candidate(parent, id, true, manager.getCameraCharacteristics(id)));
                }
            } catch (Exception ignored) { }
        }
        if (best == null) return CameraSelector.DEFAULT_BACK_CAMERA;
        final String parent = best.parent;
        CameraSelector.Builder builder = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .addCameraFilter(infos -> {
                    List<CameraInfo> matching = new ArrayList<>();
                    for (CameraInfo info : infos) try {
                        if (parent.equals(Camera2CameraInfo.from(info).getCameraId())) matching.add(info);
                    } catch (Exception ignored) { }
                    return matching.isEmpty() ? infos : matching;
                });
        if (best.physical && android.os.Build.VERSION.SDK_INT >= 28) builder.setPhysicalCameraId(best.id);
        return builder.build();
    }

    private Candidate candidate(String parent, String id, boolean physical, CameraCharacteristics c) {
        Integer facing = c.get(CameraCharacteristics.LENS_FACING);
        if (facing != null && facing != CameraCharacteristics.LENS_FACING_BACK) return null;
        SizeF size = c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
        float area = size == null ? 0 : size.getWidth() * size.getHeight();
        Float focus = c.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
        return new Candidate(parent, id, physical, area * (focus != null && focus > 0 ? 1.25f : .75f));
    }
    private Candidate better(Candidate a, Candidate b) {
        return b == null ? a : a == null || b.score > a.score ? b : a;
    }
    private static final class Candidate {
        final String parent, id; final boolean physical; final float score;
        Candidate(String p, String i, boolean f, float s) { parent=p; id=i; physical=f; score=s; }
    }

    private void analyzeImage(ImageProxy proxy) {
        android.media.Image media = proxy.getImage();
        if (media == null || barcodeHandled.get()) { proxy.close(); return; }
        InputImage image = InputImage.fromMediaImage(media, proxy.getImageInfo().getRotationDegrees());
        barcodeScanner.process(image).addOnSuccessListener(barcodes -> {
            if (barcodeHandled.get()) return;
            for (Barcode barcode : barcodes) {
                String raw = barcode.getRawValue();
                if (raw != null && raw.matches("[0-9]{8,14}")) {
                    runOnUiThread(() -> new ScannerBridge().barcodeFound(raw));
                    return;
                }
            }
        }).addOnCompleteListener(task -> proxy.close());
    }

    private void focusAt(float x, float y) {
        if (camera == null || previewView.getWidth() == 0 || previewView.getHeight() == 0) return;
        MeteringPoint point = previewView.getMeteringPointFactory().createPoint(x, y, .18f);
        FocusMeteringAction action = new FocusMeteringAction.Builder(point)
                .setAutoCancelDuration(3, TimeUnit.SECONDS).build();
        camera.getCameraControl().startFocusAndMetering(action);
    }

    private void updateCameraButton(boolean active, String message) {
        if (webView == null || !pageReady) return;
        String js = "(function(){var b=document.getElementById('startBtn');if(b){b.textContent="
                + JSONObject.quote(active ? "◉  Cámara activa" : "◉  Activar cámara")
                + ";b.disabled=" + active + ";}var s=document.getElementById('cameraStatus');"
                + "if(s)s.textContent=" + JSONObject.quote(message) + ";})();";
        webView.evaluateJavascript(js, null);
    }

    private void deliverPendingBarcode() {
        if (!pageReady || pendingBarcode == null || webView == null) return;
        String code = pendingBarcode;
        pendingBarcode = null;
        String js = "(function(){var input=document.getElementById('barcodeInput');"
                + "var form=document.getElementById('manualForm');if(!input||!form)return;"
                + "input.value=" + JSONObject.quote(code)
                + ";form.dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));})();";
        webView.evaluateJavascript(js, null);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != CAMERA_PERMISSION_REQUEST) return;
        permissionRequestInFlight = false;
        boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        permissionDenied = !granted;
        getSharedPreferences(PERMISSION_PREFS, MODE_PRIVATE).edit()
                .putBoolean(KEY_PERMISSION_DECLINED, !granted).apply();
        if (granted) {
            permissionDenied = false;
            startCamera();
        } else {
            updateCameraButton(false, "Permite el acceso a la cámara para escanear");
            if (!shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                showCameraSettingsDialog();
            }
        }
    }
    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        if (webView != null) webView.saveState(out);
        super.onSaveInstanceState(out);
    }
    @Override protected void onDestroy() {
        if (cameraProvider != null) cameraProvider.unbindAll();
        if (barcodeScanner != null) barcodeScanner.close();
        if (analysisExecutor != null) analysisExecutor.shutdown();
        if (webView != null) { webView.stopLoading(); webView.destroy(); webView = null; }
        super.onDestroy();
    }
}
