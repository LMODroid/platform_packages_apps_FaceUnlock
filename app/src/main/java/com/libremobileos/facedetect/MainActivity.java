package com.libremobileos.facedetect;

import android.graphics.RectF;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
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

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		previewView = findViewById(R.id.viewFinder);
		previewView.setScaleType(PreviewView.ScaleType.FIT_CENTER);
		overlayView = findViewById(R.id.overlay);

		cameraProviderFuture = ProcessCameraProvider.getInstance(this);
		faceFinder = FaceFinder.create(this, 640, 480, 90);

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
				.requireLensFacing(CameraSelector.LENS_FACING_FRONT)
				.build();

		preview.setSurfaceProvider(previewView.getSurfaceProvider());

		ImageAnalysis imageAnalysis =
				new ImageAnalysis.Builder()
						.setTargetResolution(new Size(640, 480))
						.setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
						.build();

		imageAnalysis.setAnalyzer(getMainExecutor(), imageProxy -> {
			Pair<List<Pair<FaceDetector.Face, FaceScanner.Face>>, Long> data = faceFinder.process(BitmapUtils.getBitmap(imageProxy));
			ArrayList<RectF> bounds = new ArrayList<>();
			for (Pair<FaceDetector.Face, FaceScanner.Face> faceFacePair : data.first) {
				Log.i("face", "found face id=" + faceFacePair.first.getId() + " conf=" + faceFacePair.first.getConfidence() + " loc=" + faceFacePair.first.getLocation());
				bounds.add(faceFacePair.first.getLocation());
			}
			overlayView.updateBounds(bounds.toArray(new RectF[0]));
			imageProxy.close();
		});

		cameraProvider.bindToLifecycle((LifecycleOwner)this, cameraSelector, imageAnalysis, preview);
	}

}
