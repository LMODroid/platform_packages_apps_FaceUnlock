package com.libremobileos.facedetect;

import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutionException;

public abstract class CameraActivity extends AppCompatActivity {
	// CameraX boilerplate
	private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
	// View showing camera frames
	protected PreviewView previewView;
	// The desired camera input size
	protected final Size desiredInputSize = new Size(640, 480);
	// The calculated actual processing width & height
	protected int width, height;

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	protected void connectToCam(PreviewView pv) {
		previewView = pv;
		previewView.setScaleType(PreviewView.ScaleType.FIT_CENTER);

		// CameraX boilerplate (create camera connection)
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

	private void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
		// We're connected to the camera, set up everything
		Preview preview = new Preview.Builder()
				.build();

		// Which camera to use
		int selectedCamera = CameraSelector.LENS_FACING_FRONT;
		CameraSelector cameraSelector = new CameraSelector.Builder()
				.requireLensFacing(selectedCamera)
				.build();

		preview.setSurfaceProvider(previewView.getSurfaceProvider());

		// Cameras give us landscape images. If we are in portrait mode
		// (and want to process a portrait image), swap width/height to
		// make the image portrait.
		if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
			width = desiredInputSize.getHeight();
			height = desiredInputSize.getWidth();
		} else {
			width = desiredInputSize.getWidth();
			height = desiredInputSize.getHeight();
		}

		// Set up CameraX boilerplate and configure it to drop frames if we can't keep up
		ImageAnalysis imageAnalysis =
				new ImageAnalysis.Builder()
						.setTargetResolution(new Size(width, height))
						.setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
						.build();

		onSetCameraCallback(imageAnalysis);

		// Bind all objects together
		/* Camera camera = */ cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis, preview);
	}

	protected abstract void onSetCameraCallback(ImageAnalysis imageAnalysis);
}
