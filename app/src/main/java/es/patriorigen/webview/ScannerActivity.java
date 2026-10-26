package es.patriorigen.webview;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.GradientDrawable.Orientation;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Size;
import android.util.SizeF;
import android.view.Gravity;
import android.view.animation.LinearInterpolator;
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
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

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
    public static final String EXTRA_OPEN_MANUAL = "es.patriorigen.webview.OPEN_MANUAL";
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
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF100003);

        previewView = new PreviewView(this);
        previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        previewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        root.addView(previewView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        View shade = new View(this);
        shade.setBackground(new GradientDrawable(Orientation.TOP_BOTTOM,
                new int[] { 0x99150004, 0x00000000, 0x00000000, 0xCC150004 }));
        root.addView(shade, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout chrome = new LinearLayout(this);
        chrome.setOrientation(LinearLayout.VERTICAL);
        chrome.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout topPanel = new LinearLayout(this);
        topPanel.setOrientation(LinearLayout.HORIZONTAL);
        topPanel.setGravity(Gravity.CENTER_VERTICAL);
        topPanel.setPadding(dp(16), dp(14), dp(16), dp(12));
        GradientDrawable topBackground = new GradientDrawable(
                Orientation.LEFT_RIGHT, new int[] { 0xFFE0041D, 0xFFB00016, 0xFF76000E });
        topPanel.setBackground(topBackground);

        TextView closeButton = new TextView(this);
        closeButton.setText("×");
        closeButton.setTextSize(30);
        closeButton.setTextColor(0xFFFFF6DC);
        closeButton.setGravity(Gravity.CENTER);
        closeButton.setBackground(roundBackground(0x33FFFFFF, 0x66FFFFFF, 12));
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(dp(40), dp(40));
        topPanel.addView(closeButton, closeParams);
        closeButton.setOnClickListener(view -> finish());

        TextView seal = new TextView(this);
        seal.setText("P");
        seal.setTextSize(19);
        seal.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        seal.setTextColor(0xFFF5C400);
        seal.setGravity(Gravity.CENTER);
        GradientDrawable sealBackground = new GradientDrawable();
        sealBackground.setColor(0xFF780712);
        sealBackground.setShape(GradientDrawable.OVAL);
        sealBackground.setStroke(dp(2), 0xFFF5C400);
        LinearLayout.LayoutParams sealParams = new LinearLayout.LayoutParams(dp(40), dp(40));
        sealParams.leftMargin = dp(10);
        topPanel.addView(seal, sealParams);

        TextView title = new TextView(this);
        title.setText("PATRIORIGEN");
        title.setTextColor(0xFFFFF6DC);
        title.setTextSize(14);
        title.setLetterSpacing(0.04f);
        title.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        title.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        titleParams.leftMargin = dp(10);
        topPanel.addView(title, titleParams);

        torchButton = new Button(this);
        torchButton.setText("Linterna");
        torchButton.setTextSize(12);
        torchButton.setTextColor(Color.WHITE);
        torchButton.setBackground(roundBackground(0x33FFFFFF, 0x66FFFFFF, 10));
        torchButton.setVisibility(View.GONE);
        torchButton.setOnClickListener(view -> toggleTorch());
        topPanel.addView(torchButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)));

        chrome.addView(topPanel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View goldRule = new View(this);
        goldRule.setBackgroundColor(0xFFF5C400);
        chrome.addView(goldRule, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));

        FrameLayout.LayoutParams chromeParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP);
        root.addView(chrome, chromeParams);

        ScanGuideView guide = new ScanGuideView(this);
        FrameLayout.LayoutParams guideParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(230), Gravity.CENTER);
        guideParams.leftMargin = dp(20);
        guideParams.rightMargin = dp(20);
        root.addView(guide, guideParams);

        LinearLayout bottomPanel = new LinearLayout(this);
        bottomPanel.setOrientation(LinearLayout.VERTICAL);
        bottomPanel.setGravity(Gravity.CENTER);
        bottomPanel.setPadding(dp(16), dp(13), dp(16), dp(18));
        bottomPanel.setBackgroundColor(0xE65D020C);

        statusView = new TextView(this);
        statusView.setText("Encuadra el código dentro de las esquinas");
        statusView.setTextColor(0xFFFFF6DC);
        statusView.setTextSize(15);
        statusView.setGravity(Gravity.CENTER);
        bottomPanel.addView(statusView);

        Button manualButton = new Button(this);
        manualButton.setText("⌨   Código manual");
        manualButton.setTextSize(13);
        manualButton.setTextColor(0xFFFFF2DC);
        manualButton.setBackground(roundBackground(0xFF390006, 0xFFDB8F8D, 11));
        LinearLayout.LayoutParams manualParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        manualParams.topMargin = dp(10);
        bottomPanel.addView(manualButton, manualParams);
        manualButton.setOnClickListener(view -> {
            Intent result = new Intent().putExtra(EXTRA_OPEN_MANUAL, true);
            setResult(Activity.RESULT_OK, result);
            finish();
        });

        TextView privacyNote = new TextView(this);
        privacyNote.setText("●  El vídeo no sale de tu dispositivo");
        privacyNote.setTextColor(0xFFD7AAA5);
        privacyNote.setTextSize(11);
        privacyNote.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        noteParams.topMargin = dp(6);
        bottomPanel.addView(privacyNote, noteParams);

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

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            int bottomInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            view.setPadding(0, 0, 0, bottomInset);
            return insets;
        });
        setContentView(root);
        ViewCompat.requestApplyInsets(root);
    }

    private GradientDrawable roundBackground(int fill, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private final class ScanGuideView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float laserProgress = 0.5f;
        private final ValueAnimator laserAnimator;

        ScanGuideView(android.content.Context context) {
            super(context);
            laserAnimator = ValueAnimator.ofFloat(0.12f, 0.88f);
            laserAnimator.setDuration(2100);
            laserAnimator.setRepeatMode(ValueAnimator.REVERSE);
            laserAnimator.setRepeatCount(ValueAnimator.INFINITE);
            laserAnimator.setInterpolator(new LinearInterpolator());
            laserAnimator.addUpdateListener(animation -> {
                laserProgress = (float) animation.getAnimatedValue();
                invalidate();
            });
            laserAnimator.start();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float inset = dp(9);
            float left = inset;
            float top = inset;
            float right = getWidth() - inset;
            float bottom = getHeight() - inset;
            float length = dp(34);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(4));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(0xFFF5C400);
            canvas.drawLine(left, top, left + length, top, paint);
            canvas.drawLine(left, top, left, top + length, paint);
            canvas.drawLine(right - length, top, right, top, paint);
            canvas.drawLine(right, top, right, top + length, paint);
            canvas.drawLine(left, bottom - length, left, bottom, paint);
            canvas.drawLine(left, bottom, left + length, bottom, paint);
            canvas.drawLine(right, bottom - length, right, bottom, paint);
            canvas.drawLine(right - length, bottom, right, bottom, paint);

            paint.setStrokeWidth(dp(2));
            paint.setColor(0xFFF5C400);
            paint.setShadowLayer(dp(8), 0, 0, 0xFFF5C400);
            float laserY = top + (bottom - top) * laserProgress;
            canvas.drawLine(left + dp(14), laserY, right - dp(14), laserY, paint);
            paint.clearShadowLayer();
        }

        @Override
        protected void onDetachedFromWindow() {
            laserAnimator.cancel();
            super.onDetachedFromWindow();
        }
    }

    private void startCamera() {
        statusView.setText("Preparando cámara…");
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
        statusView.setText("Encuadra el código dentro de las esquinas");
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
                        () -> statusView.setText("Encuadra el código dentro de las esquinas")))
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
