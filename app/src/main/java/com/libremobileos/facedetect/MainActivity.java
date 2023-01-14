package com.libremobileos.facedetect;

import android.content.res.Configuration;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;
import com.libremobileos.yifan.face.scan.FaceFinder;
import com.libremobileos.yifan.face.scan.FaceScanner;
import com.libremobileos.yifan.face.shared.FaceDetector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {

	private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
	private PreviewView previewView;
	private FaceFinder faceFinder;
	private FaceBoundsOverlayView overlayView;
	private final Size desiredInputSize = new Size(640, 480);
	private final int selectedCamera = CameraSelector.LENS_FACING_FRONT;
	private int previewWidth, previewHeight;

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		previewView = findViewById(R.id.viewFinder);
		previewView.setScaleType(PreviewView.ScaleType.FIT_CENTER);
		overlayView = findViewById(R.id.overlay);

		/* cameras are landscape */
		if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
			previewWidth = desiredInputSize.getHeight();
			previewHeight = desiredInputSize.getWidth();
		} else {
			previewWidth = desiredInputSize.getWidth();
			previewHeight = desiredInputSize.getHeight();
		}

		cameraProviderFuture = ProcessCameraProvider.getInstance(this);
		cameraProviderFuture.addListener(() -> {
			try {
				ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
				bindPreview(cameraProvider);
			} catch (ExecutionException | InterruptedException e) {
				// No errors need to be handled for this Future.
				// This should never be reached.
			}
		}, getMainExecutor());

	}

	@OptIn(markerClass = ExperimentalGetImage.class)
	private void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
		Preview preview = new Preview.Builder()
				.build();

		CameraSelector cameraSelector = new CameraSelector.Builder()
				.requireLensFacing(selectedCamera)
				.build();

		preview.setSurfaceProvider(previewView.getSurfaceProvider());

		ImageAnalysis imageAnalysis =
				new ImageAnalysis.Builder()
						.setTargetResolution(new Size(previewWidth, previewHeight))
						.setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
						.build();

		imageAnalysis.setAnalyzer(getMainExecutor(), imageProxy -> {
			Pair<List<Pair<FaceDetector.Face, FaceScanner.Face>>, Long> data = faceFinder.process(BitmapUtils.getBitmap(imageProxy));
			ArrayList<RectF> bounds = new ArrayList<>();
			Log.i("cam", String.valueOf(imageProxy.getImageInfo().getRotationDegrees()));
			for (Pair<FaceDetector.Face, FaceScanner.Face> faceFacePair : data.first) {
				RectF boundingBox = new RectF(faceFacePair.first.getLocation());
				if (selectedCamera == CameraSelector.LENS_FACING_FRONT) {
					// camera is frontal so the image is flipped horizontally
					// flips horizontally
					Matrix flip = new Matrix();
					int sensorOrientation = imageProxy.getImageInfo().getRotationDegrees();
					if (sensorOrientation == 0 || sensorOrientation == 180) {
						flip.postScale(1, -1, previewWidth / 2.0f, previewHeight / 2.0f);
					} else {
						flip.postScale(-1, 1, previewWidth / 2.0f, previewHeight / 2.0f);
					}
					flip.mapRect(boundingBox);
				}
				bounds.add(boundingBox);
			}
			overlayView.updateBounds(bounds.toArray(new RectF[0]), previewWidth, previewHeight);
			imageProxy.close();
		});

		Camera camera = cameraProvider.bindToLifecycle((LifecycleOwner)this, cameraSelector, imageAnalysis, preview);

		faceFinder = FaceFinder.create(this, previewWidth, previewHeight, 0);
	}

}
