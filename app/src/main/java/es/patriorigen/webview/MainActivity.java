package es.patriorigen.webview;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.net.http.SslError;

import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity {
    private static final String CAMERA_TUNING_SCRIPT =
            "(function(){"
                    + "if(window.__patriorigenCameraTuningInstalled)return;"
                    + "window.__patriorigenCameraTuningInstalled=true;"
                    + "var tunedTracks=new WeakSet();"
                    + "window.setInterval(function(){"
                    + "var video=document.getElementById('video');"
                    + "var tracks=video&&video.srcObject&&video.srcObject.getVideoTracks?video.srcObject.getVideoTracks():[];"
                    + "if(!tracks.length)return;"
                    + "var track=tracks[0];if(tunedTracks.has(track))return;tunedTracks.add(track);"
                    + "var constraints={width:{ideal:1280,max:1280},height:{ideal:720,max:720},frameRate:{ideal:30,max:30}};"
                    + "try{var caps=track.getCapabilities?track.getCapabilities():{};"
                    + "if(caps.focusMode&&caps.focusMode.indexOf('continuous')!==-1)"
                    + "constraints.advanced=[{focusMode:'continuous'}];}catch(e){}"
                    + "if(track.applyConstraints)track.applyConstraints(constraints).catch(function(){});"
                    + "},250);"
                    + "})();";
    private static final int MEDIA_PERMISSION_REQUEST = 4101;
    private WebView webView;
    private PermissionRequest pendingWebPermission;
    private String[] pendingAndroidPermissions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

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
                if ("https".equalsIgnoreCase(uri.getScheme())
                        && host != null
                        && (host.equalsIgnoreCase("patriorigen.es")
                            || host.equalsIgnoreCase("www.patriorigen.es"))) {
                    view.evaluateJavascript(CAMERA_TUNING_SCRIPT, null);
                }
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
            public void onPermissionRequest(PermissionRequest request) {
                handleWebPermission(request);
            }

            @Override
            public void onPermissionRequestCanceled(PermissionRequest request) {
                if (pendingWebPermission == request) {
                    pendingWebPermission = null;
                    pendingAndroidPermissions = null;
                }
            }
        });

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl("https://patriorigen.es");
        }
    }

    private void handleWebPermission(PermissionRequest request) {
        Uri origin = request.getOrigin();
        String host = origin.getHost();
        if (!"https".equalsIgnoreCase(origin.getScheme())
                || host == null
                || !(host.equalsIgnoreCase("patriorigen.es")
                    || host.equalsIgnoreCase("www.patriorigen.es"))) {
            request.deny();
            return;
        }

        List<String> resources = new ArrayList<>();
        List<String> androidPermissions = new ArrayList<>();
        for (String resource : request.getResources()) {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) {
                resources.add(resource);
                androidPermissions.add(Manifest.permission.CAMERA);
            } else if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) {
                resources.add(resource);
                androidPermissions.add(Manifest.permission.RECORD_AUDIO);
            } else {
                request.deny();
                return;
            }
        }
        if (resources.isEmpty()) {
            request.deny();
            return;
        }

        pendingWebPermission = request;
        pendingAndroidPermissions = androidPermissions.toArray(new String[0]);
        boolean needsRuntimePermission = false;
        for (String permission : pendingAndroidPermissions) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                needsRuntimePermission = true;
                break;
            }
        }
        if (needsRuntimePermission) {
            requestPermissions(pendingAndroidPermissions, MEDIA_PERMISSION_REQUEST);
        } else {
            askWebPermission(request, resources.toArray(new String[0]), host);
        }
    }

    private void askWebPermission(
            PermissionRequest request, String[] resources, String host) {
        String description;
        boolean camera = contains(resources, PermissionRequest.RESOURCE_VIDEO_CAPTURE);
        boolean microphone = contains(resources, PermissionRequest.RESOURCE_AUDIO_CAPTURE);
        if (camera && microphone) description = "la cámara y el micrófono";
        else if (camera) description = "la cámara";
        else description = "el micrófono";

        new AlertDialog.Builder(this)
                .setTitle("Permitir acceso")
                .setMessage(host + " quiere usar " + description + ".")
                .setPositiveButton("Permitir", (dialog, which) -> {
                    if (pendingWebPermission == request) {
                        request.grant(resources);
                        clearPendingPermission();
                    }
                })
                .setNegativeButton("Rechazar", (dialog, which) -> {
                    request.deny();
                    clearPendingPermission();
                })
                .setOnCancelListener(dialog -> {
                    request.deny();
                    clearPendingPermission();
                })
                .show();
    }

    private static boolean contains(String[] values, String target) {
        for (String value : values) if (target.equals(value)) return true;
        return false;
    }

    private void clearPendingPermission() {
        pendingWebPermission = null;
        pendingAndroidPermissions = null;
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != MEDIA_PERMISSION_REQUEST) return;

        PermissionRequest request = pendingWebPermission;
        String[] requested = pendingAndroidPermissions;
        if (request == null || requested == null) return;

        boolean granted = grantResults.length == requested.length;
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) granted = false;
        }
        if (granted) {
            Uri origin = request.getOrigin();
            askWebPermission(request, resourcesFor(request), origin.getHost());
        } else {
            request.deny();
            clearPendingPermission();
        }
    }

    private static String[] resourcesFor(PermissionRequest request) {
        List<String> allowed = new ArrayList<>();
        for (String resource : request.getResources()) {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)
                    || PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) {
                allowed.add(resource);
            }
        }
        return allowed.toArray(new String[0]);
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
        clearPendingPermission();
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
