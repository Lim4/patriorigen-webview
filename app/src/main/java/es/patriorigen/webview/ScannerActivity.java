package es.patriorigen.webview;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Size;
import android.util.SizeF;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.ComponentActivity;
import androidx.camera.camera2.interop.Camera2CameraInfo;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraFilter;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.common.InputImage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ScannerActivity extends ComponentActivity {
    public static final String EXTRA_BARCODE = "es.patriorigen.webview.BARCODE";
    private static final int CAMERA_PERMISSION_REQUEST = 5101;

    private final AtomicBoolean barcodeHandled = new AtomicBoolean(false);
    private PreviewView previewView;
    private TextView statusView;
    private Button torchButton;
    private ProcessCameraProvider cameraProvider;
    private Camera camera;
    private BarcodeScanner barcodeScanner;
    private ExecutorService analysisExecutor;
    private boolean torchEnabled;
    private boolean hasTorch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildScannerUi();

        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                        Barcode.FORMAT_EAN_13,
                        Barcode.FORMAT_EAN_8,
                        Barcode.FORMAT_UPC_A,
                        Barcode.FORMAT_UPC_E)
                .build();
        barcodeScanner = BarcodeScanning.getClient(options);
        analysisExecutor = Executors.newSingleThreadExecutor();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            requestPermissions(new String[] { Manifest.permission.CAMERA },
                    CAMERA_PERMISSION_REQUEST);
        }
    }

    private void buildScannerUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        previewView = new PreviewView(this);
        previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        root.addView(previewView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout topPanel = new LinearLayout(this);
        topPanel.setOrientation(LinearLayout.HORIZONTAL);
        topPanel.setGravity(Gravity.CENTER_VERTICAL);
        topPanel.setPadding(dp(16), dp(14), dp(16), dp(14));
        topPanel.setBackgroundColor(0x99000000);

        Button closeButton = new Button(this);
        closeButton.setText("Cerrar");
        closeButton.setOnClickListener(view -> finish());
        topPanel.addView(closeButton);

        TextView title = new TextView(this);
        title.setText("Lente trasera principal");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        topPanel.addView(title, titleParams);

        torchButton = new Button(this);
        torchButton.setText("Linterna");
        torchButton.setVisibility(View.GONE);
        torchButton.setOnClickListener(view -> toggleTorch());
        topPanel.addView(torchButton);

        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP);
        root.addView(topPanel, topParams);

        FrameLayout guide = new FrameLayout(this);
        GradientDrawable border = new GradientDrawable();
        border.setColor(Color.TRANSPARENT);
        border.setCornerRadius(dp(12));
        border.setStroke(dp(3), 0xFFFFD54F);
        guide.setBackground(border);
        FrameLayout.LayoutParams guideParams = new FrameLayout.LayoutParams(dp(300), dp(120),
                Gravity.CENTER);
        root.addView(guide, guideParams);

        LinearLayout bottomPanel = new LinearLayout(this);
        bottomPanel.setOrientation(LinearLayout.VERTICAL);
        bottomPanel.setGravity(Gravity.CENTER);
        bottomPanel.setPadding(dp(18), dp(18), dp(18), dp(28));
        bottomPanel.setBackgroundColor(0x99000000);

        statusView = new TextView(this);
        statusView.setText("Centra el código. Si sale borroso, aléjalo poco a poco.");
        statusView.setTextColor(Color.WHITE);
        statusView.setTextSize(16);
        statusView.setGravity(Gravity.CENTER);
        bottomPanel.addView(statusView);

        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        root.addView(bottomPanel, bottomParams);

        previewView.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                focusAt(event.getX(), event.getY());
            }
            return true;
        });

        setContentView(root);
    }

    private void startCamera() {
        statusView.setText("Preparando la cámara principal…");
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                bindCamera(cameraProvider, chooseMainRearCamera(cameraProvider));
            } catch (Exception error) {
                statusView.setText("No se pudo abrir la cámara. Puedes introducir el código manualmente.");
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCamera(ProcessCameraProvider provider, CameraSelector selector) {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        ImageAnalysis analysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        analysis.setAnalyzer(analysisExecutor, this::analyzeImage);

        try {
            provider.unbindAll();
            camera = provider.bindToLifecycle(this, selector, preview, analysis);
        } catch (RuntimeException primaryCameraUnavailable) {
            try {
                provider.unbindAll();
                camera = provider.bindToLifecycle(
                        this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);
            } catch (RuntimeException cameraError) {
                statusView.setText("No se pudo iniciar la cámara. Usa la consulta manual.");
                return;
            }
        }

        hasTorch = camera.getCameraInfo().hasFlashUnit();
        torchButton.setVisibility(hasTorch ? View.VISIBLE : View.GONE);
        statusView.setText("Lente principal activa. Toca el código si necesita enfoque.");
        previewView.post(() -> focusAt(previewView.getWidth() / 2f,
                previewView.getHeight() / 2f));
    }

    private CameraSelector chooseMainRearCamera(ProcessCameraProvider provider) {
        CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
        MainCameraCandidate best = null;
        for (CameraInfo info : provider.getAvailableCameraInfos()) {
            try {
                Camera2CameraInfo camera2Info = Camera2CameraInfo.from(info);
                String parentId = camera2Info.getCameraId();
                Integer facing = camera2Info.getCameraCharacteristic(
                        CameraCharacteristics.LENS_FACING);
                if (facing == null || facing != CameraCharacteristics.LENS_FACING_BACK) continue;

                CameraCharacteristics parentCharacteristics =
                        manager.getCameraCharacteristics(parentId);
                best = betterCandidate(best, candidate(
                        manager, parentId, parentId, false, parentCharacteristics));

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    Set<String> physicalIds = parentCharacteristics.getPhysicalCameraIds();
                    for (String physicalId : physicalIds) {
                        CameraCharacteristics physical =
                                manager.getCameraCharacteristics(physicalId);
                        best = betterCandidate(best, candidate(
                                manager, parentId, physicalId, true, physical));
                    }
                }
            } catch (Exception ignored) {
                // Keep evaluating cameras; the standard rear camera remains the fallback.
            }
        }

        if (best == null) return CameraSelector.DEFAULT_BACK_CAMERA;
        final String parentId = best.parentId;
        CameraSelector.Builder selector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .addCameraFilter(cameraInfos -> {
                    List<CameraInfo> matching = new ArrayList<>();
                    for (CameraInfo info : cameraInfos) {
                        try {
                            if (parentId.equals(Camera2CameraInfo.from(info).getCameraId())) {
                                matching.add(info);
                            }
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                    return matching.isEmpty() ? cameraInfos : matching;
                });
        if (best.physical && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            selector.setPhysicalCameraId(best.cameraId);
        }
        return selector.build();
    }

    private MainCameraCandidate candidate(
            CameraManager manager,
            String parentId,
            String cameraId,
            boolean physical,
            CameraCharacteristics characteristics) {
        Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
        if (facing != null && facing != CameraCharacteristics.LENS_FACING_BACK) return null;
        SizeF sensorSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
        float area = sensorSize == null ? 0 : sensorSize.getWidth() * sensorSize.getHeight();
        Float minimumFocusDistance =
                characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
        boolean hasAutofocus = minimumFocusDistance != null && minimumFocusDistance > 0;
        return new MainCameraCandidate(
                parentId, cameraId, physical, area * (hasAutofocus ? 1.25f : 0.75f));
    }

    private static MainCameraCandidate betterCandidate(
            MainCameraCandidate current, MainCameraCandidate candidate) {
        if (candidate == null) return current;
        return current == null || candidate.score > current.score ? candidate : current;
    }

    private void analyzeImage(ImageProxy imageProxy) {
        if (barcodeHandled.get()) {
            imageProxy.close();
            return;
        }
        android.media.Image mediaImage = imageProxy.getImage();
        if (mediaImage == null) {
            imageProxy.close();
            return;
        }

        InputImage image = InputImage.fromMediaImage(
                mediaImage, imageProxy.getImageInfo().getRotationDegrees());
        barcodeScanner.process(image)
                .addOnSuccessListener(barcodes -> {
                    if (barcodeHandled.get()) return;
                    for (Barcode barcode : barcodes) {
                        String raw = barcode.getRawValue();
                        if (raw != null && raw.matches("[0-9]{8,14}")
                                && barcodeHandled.compareAndSet(false, true)) {
                            Intent result = new Intent().putExtra(EXTRA_BARCODE, raw);
                            setResult(Activity.RESULT_OK, result);
                            finish();
                            return;
                        }
                    }
                })
                .addOnFailureListener(error -> runOnUiThread(
                        () -> statusView.setText("Enfocando el código…")))
                .addOnCompleteListener(task -> imageProxy.close());
    }

    private void focusAt(float x, float y) {
        if (camera == null || previewView.getWidth() == 0 || previewView.getHeight() == 0) return;
        MeteringPoint point = previewView.getMeteringPointFactory().createPoint(x, y, 0.18f);
        FocusMeteringAction action = new FocusMeteringAction.Builder(
                point,
                FocusMeteringAction.FLAG_AF | FocusMeteringAction.FLAG_AE)
                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                .build();
        camera.getCameraControl().startFocusAndMetering(action);
    }

    private void toggleTorch() {
        if (!hasTorch || camera == null) return;
        torchEnabled = !torchEnabled;
        camera.getCameraControl().enableTorch(torchEnabled);
        torchButton.setText(torchEnabled ? "Apagar linterna" : "Linterna");
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != CAMERA_PERMISSION_REQUEST) return;
        if (grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            setResult(Activity.RESULT_CANCELED);
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        if (cameraProvider != null) cameraProvider.unbindAll();
        if (barcodeScanner != null) barcodeScanner.close();
        if (analysisExecutor != null) analysisExecutor.shutdown();
        super.onDestroy();
    }

    private static final class MainCameraCandidate {
        final String parentId;
        final String cameraId;
        final boolean physical;
        final float score;

        MainCameraCandidate(String parentId, String cameraId, boolean physical, float score) {
            this.parentId = parentId;
            this.cameraId = cameraId;
            this.physical = physical;
            this.score = score;
        }
    }
}
