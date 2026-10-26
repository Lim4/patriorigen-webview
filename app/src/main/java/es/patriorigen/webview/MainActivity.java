package es.patriorigen.webview;

import android.Manifest;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.PermissionRequest;
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

        if (savedInstanceState != null && webView.restoreState(savedInstanceState) != null) {
            return;
        }
        webView.loadUrl(HOME_URL);
    }

    private void registerActivityResults() {
        cameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    PermissionRequest request = pendingCameraRequest;
                    pendingCameraRequest = null;
                    if (request == null) return;
                    if (granted && ContextCompat.checkSelfPermission(
                            this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        grantCameraRequest(request);
                    } else {
                        denyCameraRequest(request);
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
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                return !"https".equalsIgnoreCase(uri.getScheme());
            }

            @Override
            public void onReceivedSslError(
                    WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
            }
        });

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

    private void handleWebPermissionRequest(PermissionRequest request) {
        if (!isPatriorigenOrigin(request.getOrigin())
                || !containsCameraRequest(request.getResources())) {
            denyCameraRequest(request);
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            grantCameraRequest(request);
            return;
        }
        if (pendingCameraRequest != null && pendingCameraRequest != request) {
            denyCameraRequest(pendingCameraRequest);
        }
        pendingCameraRequest = request;
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
    }

    private boolean isPatriorigenOrigin(Uri origin) {
        if (origin == null || !"https".equalsIgnoreCase(origin.getScheme())) return false;
        String host = origin.getHost();
        return host != null && (host.equalsIgnoreCase("patriorigen.es")
                || host.equalsIgnoreCase("www.patriorigen.es"));
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

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
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
