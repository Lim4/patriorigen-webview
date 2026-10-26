package es.patriorigen.webview;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.PermissionRequest;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.net.http.SslError;
import android.widget.FrameLayout;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class MainActivity extends ComponentActivity {
    private static final String HOME_URL = "https://patriorigen.es/";
    private static final String CAMERA_RESOURCE = PermissionRequest.RESOURCE_VIDEO_CAPTURE;

    private WebView webView;
    private PermissionRequest pendingCameraRequest;
    private ActivityResultLauncher<String> cameraPermissionLauncher;
    private ActivityResultLauncher<Intent> fileChooserLauncher;
    private android.webkit.ValueCallback<Uri[]> filePathCallback;
    private Uri pendingCameraPhotoUri;
    private Bundle stateToRestore;
    private boolean startupPermissionRequestPending;
    private boolean startupPermissionDenied;
    private boolean resumePermissionRequestPending;
    private boolean cameraPermissionRequestInFlight;
    private boolean checkCameraPermissionOnResume;
    private volatile boolean trustedMainFrame;
    private volatile boolean userCameraTapPending;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        registerActivityResults();
        configureSystemBars();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFFE0041D);

        View navigationBarBackground = new View(this);
        navigationBarBackground.setBackgroundColor(0xFFF5EBEB);
        FrameLayout.LayoutParams navigationParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, android.view.Gravity.BOTTOM);
        root.addView(navigationBarBackground, navigationParams);

        webView = new WebView(this);
        webView.setBackgroundColor(0xFF100003);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            androidx.core.graphics.Insets bars =
                    insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(0, bars.top, 0, bars.bottom);
            FrameLayout.LayoutParams params =
                    (FrameLayout.LayoutParams) navigationBarBackground.getLayoutParams();
            params.height = bars.bottom;
            params.bottomMargin = -bars.bottom;
            navigationBarBackground.setLayoutParams(params);
            return insets;
        });
        setContentView(root);
        ViewCompat.requestApplyInsets(root);

        configureWebView();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView != null && webView.canGoBack()) webView.goBack();
                else finish();
            }
        });

        stateToRestore = savedInstanceState;
        webView.post(() -> {
            startupPermissionRequestPending = true;
            launchCameraPermissionRequest();
        });
    }

    private void openWebsite() {
        if (webView == null) return;
        if (stateToRestore != null) {
            Bundle saved = stateToRestore;
            stateToRestore = null;
            if (webView.restoreState(saved) != null) return;
        }
        webView.loadUrl(HOME_URL);
    }

    private void registerActivityResults() {
        cameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    cameraPermissionRequestInFlight = false;
                    boolean hasCamera = granted && ContextCompat.checkSelfPermission(
                            this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
                    if (startupPermissionRequestPending) {
                        startupPermissionRequestPending = false;
                        startupPermissionDenied = !hasCamera;
                        openWebsite();
                        return;
                    }
                    if (resumePermissionRequestPending) {
                        resumePermissionRequestPending = false;
                        startupPermissionDenied = !hasCamera;
                        return;
                    }

                    PermissionRequest request = pendingCameraRequest;
                    pendingCameraRequest = null;
                    boolean wasUserRetry = userCameraTapPending;
                    userCameraTapPending = false;
                    if (request == null) return;
                    if (hasCamera) {
                        startupPermissionDenied = false;
                        grantCameraRequest(request);
                    } else {
                        denyCameraRequest(request);
                        if (wasUserRetry
                                && !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                            showCameraSettingsDialog();
                        }
                    }
                });

        fileChooserLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    android.webkit.ValueCallback<Uri[]> callback = filePathCallback;
                    filePathCallback = null;
                    Uri[] selected = null;
                    if (result.getResultCode() == RESULT_OK) {
                        Intent data = result.getData();
                        if (data != null && data.getClipData() != null) {
                            List<Uri> uris = new ArrayList<>();
                            for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                                Uri uri = data.getClipData().getItemAt(i).getUri();
                                if (uri != null) uris.add(uri);
                            }
                            if (!uris.isEmpty()) selected = uris.toArray(new Uri[0]);
                        } else if (data != null && data.getData() != null) {
                            selected = new Uri[] { data.getData() };
                        } else if (pendingCameraPhotoUri != null) {
                            selected = new Uri[] { pendingCameraPhotoUri };
                        }
                    }
                    pendingCameraPhotoUri = null;
                    if (callback != null) callback.onReceiveValue(selected);
                });
    }

    private void configureSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
        WindowInsetsControllerCompat bars =
                new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        bars.setAppearanceLightStatusBars(false);
        bars.setAppearanceLightNavigationBars(true);
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSafeBrowsingEnabled(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                trustedMainFrame = isPatriorigenOrigin(Uri.parse(url));
            }

            @Override
            public void onPageCommitVisible(WebView view, String url) {
                super.onPageCommitVisible(view, url);
                if (isPatriorigenOrigin(Uri.parse(url))) installCameraPlaceholderGuard(view);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (isPatriorigenOrigin(Uri.parse(url))) installCameraPlaceholderGuard(view);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !isPatriorigenOrigin(request.getUrl());
            }

            @Override
            public void onReceivedSslError(
                    WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
            }
        });

        webView.addJavascriptInterface(new CameraBridge(), "AndroidCamera");
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> handleWebPermissionRequest(request));
            }

            @Override
            public void onPermissionRequestCanceled(PermissionRequest request) {
                runOnUiThread(() -> {
                    if (pendingCameraRequest == request) pendingCameraRequest = null;
                });
            }

            @Override
            public boolean onShowFileChooser(
                    WebView view,
                    android.webkit.ValueCallback<Uri[]> callback,
                    FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;
                try {
                    fileChooserLauncher.launch(createImageChooser(params));
                    return true;
                } catch (RuntimeException error) {
                    filePathCallback = null;
                    callback.onReceiveValue(null);
                    return false;
                }
            }
        });
    }

    private void installCameraPlaceholderGuard(WebView view) {
        String script = "(function(){"
                + "var v=document.getElementById('video');"
                + "if(v&&!v.dataset.androidVideoGuard){v.dataset.androidVideoGuard='1';"
                + "var style=document.getElementById('patriorigenVideoStartupStyle');"
                + "if(!style){style=document.createElement('style');style.id='patriorigenVideoStartupStyle';"
                + "style.textContent='#video{visibility:hidden!important;background:transparent!important}'"
                + "+'#video.android-camera-ready{visibility:visible!important}';document.head.appendChild(style);}"
                + "var hide=function(){v.classList.remove('android-camera-ready')};"
                + "var reveal=function(){if(v.readyState>=2&&v.videoWidth>0&&v.videoHeight>0)"
                + "v.classList.add('android-camera-ready')};"
                + "v.addEventListener('loadedmetadata',reveal);v.addEventListener('playing',reveal);"
                + "v.addEventListener('emptied',hide);v.addEventListener('pause',function(){if(!v.srcObject||!v.srcObject.active)hide()});"
                + "if(v.srcObject&&v.srcObject.getTracks)v.srcObject.getTracks().forEach(function(t){t.addEventListener('ended',hide)});"
                + "reveal();}"
                + "var b=document.getElementById('startBtn');if(b&&!b.dataset.androidPermissionHook){"
                + "b.dataset.androidPermissionHook='1';b.addEventListener('click',function(){"
                + "AndroidCamera.cameraButtonPressed()},true);}"
                + "})();";
        view.evaluateJavascript(script, null);
    }

    private boolean isPatriorigenOrigin(Uri uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())) return false;
        String host = uri.getHost();
        return host != null && (host.equalsIgnoreCase("patriorigen.es")
                || host.equalsIgnoreCase("www.patriorigen.es"));
    }

    private final class CameraBridge {
        @JavascriptInterface
        public void cameraButtonPressed() {
            if (trustedMainFrame) userCameraTapPending = true;
        }
    }

    private void showCameraSettingsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Permiso de cámara")
                .setMessage("Android no permite volver a mostrar el permiso. Puedes activarlo en los ajustes de la aplicación o usar el código manual.")
                .setNegativeButton("Ahora no", (dialog, which) -> { })
                .setPositiveButton("Abrir ajustes", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                })
                .show();
    }

    private void handleWebPermissionRequest(PermissionRequest request) {
        if (!isPatriorigenOrigin(request.getOrigin())
                || !containsCameraRequest(request.getResources())) {
            denyCameraRequest(request);
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startupPermissionDenied = false;
            userCameraTapPending = false;
            grantCameraRequest(request);
            return;
        }
        if (startupPermissionDenied && !userCameraTapPending) {
            denyCameraRequest(request);
            return;
        }
        if (pendingCameraRequest != null && pendingCameraRequest != request) {
            denyCameraRequest(pendingCameraRequest);
        }
        pendingCameraRequest = request;
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
    }

    private boolean containsCameraRequest(String[] resources) {
        return resources != null && Arrays.asList(resources).contains(CAMERA_RESOURCE);
    }

    private void grantCameraRequest(PermissionRequest request) {
        if (!isPatriorigenOrigin(request.getOrigin())
                || ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                        != PackageManager.PERMISSION_GRANTED) {
            denyCameraRequest(request);
            return;
        }
        try {
            request.grant(new String[] { CAMERA_RESOURCE });
        } catch (RuntimeException ignored) {
            // WebView may cancel a request if the page navigates while Android is asking.
        }
    }

    private void denyCameraRequest(PermissionRequest request) {
        try {
            request.deny();
        } catch (RuntimeException ignored) {
            // The request may already have been canceled by WebView.
        }
    }

    private Intent createImageChooser(WebChromeClient.FileChooserParams params) {
        Intent contentIntent;
        try {
            contentIntent = params.createIntent();
        } catch (RuntimeException error) {
            contentIntent = new Intent(Intent.ACTION_GET_CONTENT);
            contentIntent.addCategory(Intent.CATEGORY_OPENABLE);
        }
        contentIntent.setType("image/*");
        contentIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,
                params.getMode() == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE);

        Intent captureIntent = createCameraCaptureIntent();
        Intent chooser = Intent.createChooser(contentIntent, "Elegir fotografía");
        if (captureIntent != null) {
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[] { captureIntent });
        }
        return chooser;
    }

    private Intent createCameraCaptureIntent() {
        Intent capture = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (capture.resolveActivity(getPackageManager()) == null) return null;
        try {
            File directory = new File(getCacheDir(), "camera");
            if (!directory.exists() && !directory.mkdirs()) return null;
            File photo = File.createTempFile("patriorigen-", ".jpg", directory);
            pendingCameraPhotoUri = FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", photo);
            capture.putExtra(MediaStore.EXTRA_OUTPUT, pendingCameraPhotoUri);
            capture.setClipData(ClipData.newUri(
                    getContentResolver(), "Patriorigen photo", pendingCameraPhotoUri));
            capture.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            return capture;
        } catch (IOException | RuntimeException error) {
            pendingCameraPhotoUri = null;
            return null;
        }
    }

    private void launchCameraPermissionRequest() {
        cameraPermissionRequestInFlight = true;
        try {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        } catch (RuntimeException error) {
            cameraPermissionRequestInFlight = false;
            if (startupPermissionRequestPending) {
                startupPermissionRequestPending = false;
                startupPermissionDenied = ContextCompat.checkSelfPermission(
                        this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED;
                openWebsite();
            } else if (resumePermissionRequestPending) {
                resumePermissionRequestPending = false;
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
        if (checkCameraPermissionOnResume && !cameraPermissionRequestInFlight) {
            checkCameraPermissionOnResume = false;
            resumePermissionRequestPending = true;
            launchCameraPermissionRequest();
        }
    }

    @Override
    protected void onStop() {
        if (!cameraPermissionRequestInFlight) {
            checkCameraPermissionOnResume = true;
        }
        super.onStop();
    }

    @Override
    protected void onPause() {
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        if (filePathCallback != null) {
            filePathCallback.onReceiveValue(null);
            filePathCallback = null;
        }
        if (pendingCameraRequest != null) {
            denyCameraRequest(pendingCameraRequest);
            pendingCameraRequest = null;
        }
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
