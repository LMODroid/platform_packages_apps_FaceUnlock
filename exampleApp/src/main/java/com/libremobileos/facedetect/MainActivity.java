/*
 * Copyright 2023 LibreMobileOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.libremobileos.facedetect;

import android.content.res.Configuration;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.Pair;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;

import com.google.common.util.concurrent.ListenableFuture;

import com.libremobileos.yifan.face.FaceRecognizer;
import com.libremobileos.yifan.face.FaceStorageBackend;
import com.libremobileos.yifan.face.SharedPreferencesFaceStorageBackend;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {

	// CameraX boilerplate
	private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
	// View showing camera frames
	private PreviewView previewView;
	// AI-based detector
	private FaceRecognizer faceRecognizer;
	// Simple view allowing us to draw Rectangles over the Preview
	private FaceBoundsOverlayView overlayView;
	// The desired camera input size
	private final Size desiredInputSize = new Size(640, 480);
	// The calculated actual processing width & height
	private int width, height;
	// Store registered Faces in Memory
	private FaceStorageBackend faceStorage;
	// If we are waiting for a face to be added to knownFaces
	private boolean addPending = false;

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		// Initialize basic views
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		previewView = findViewById(R.id.viewFinder);
		previewView.setScaleType(PreviewView.ScaleType.FIT_CENTER);
		overlayView = findViewById(R.id.overlay);
		overlayView.setOnClickListener(v -> addPending = true);
		setTitle(getString(R.string.tap_to_add_face));

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

	@OptIn(markerClass = ExperimentalGetImage.class)
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

		imageAnalysis.setAnalyzer(getMainExecutor(), imageProxy -> {
			// Convert CameraX Image to Bitmap and process it
			// Return list of detected faces
			List<FaceRecognizer.Face> data = faceRecognizer.recognize(BitmapUtils.getBitmap(imageProxy));
			ArrayList<Pair<RectF, String>> bounds = new ArrayList<>();

			for (FaceRecognizer.Face face : data) {
				RectF boundingBox = new RectF(face.getLocation());

				// Camera is frontal so the image is flipped horizontally,
				// so flip it again.
				Matrix flip = new Matrix();
				flip.postScale(-1, 1, width / 2.0f, height / 2.0f);
				flip.mapRect(boundingBox);

				// Generate UI text for face
				String uiText;
				// Do we want to add a new face?
				if (addPending) {
					// If we want to add a new face, show the dialog.
					runOnUiThread(() -> showAddFaceDialog(face));
					addPending = false;
				}
				// Do we have any match?
				if (face.isRecognized()) {
					// If yes, show the user-visible ID and the detection confidence
					uiText = face.getModelCount() + " " + face.getTitle() + " " + face.getDistance();
				} else {
					// Show detected object type (always "Face") and how confident the AI is that this is a Face
					uiText = face.getTitle() + " " + face.getDetectionConfidence();
				}
				bounds.add(new Pair<>(boundingBox, uiText));
			}

			// Pass bounds to View drawing rectangles
			overlayView.updateBounds(bounds, width, height);
			// Clean up
			imageProxy.close();
		});

		// Bind all objects together
		/* Camera camera = */ cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis, preview);

		// Create AI-based face detection
		//faceStorage = new VolatileFaceStorageBackend();
		faceStorage = new SharedPreferencesFaceStorageBackend(getSharedPreferences("faces", 0));
		faceRecognizer = FaceRecognizer.create(this,
				faceStorage, /* face data storage */
				0.6f, /* minimum confidence to consider object as face */
				width, /* bitmap width */
				height, /* bitmap height */
				0, /* CameraX rotates the image for us, so we chose to IGNORE sensorRotation altogether */
				0.7f, /* maximum distance to track face */
				1 /* minimum model count to track face */
		);
	}

	private void showAddFaceDialog(FaceRecognizer.Face rec) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		LayoutInflater inflater = getLayoutInflater();
		View dialogLayout = inflater.inflate(R.layout.image_edit_dialog, null);
		ImageView ivFace = dialogLayout.findViewById(R.id.dlg_image);
		TextView tvTitle = dialogLayout.findViewById(R.id.dlg_title);
		EditText etName = dialogLayout.findViewById(R.id.dlg_input);

		tvTitle.setText(R.string.add_face);
		// Add preview of cropped face to verify we're adding the correct one
		ivFace.setImageBitmap(rec.getCrop());
		etName.setHint(R.string.input_name);

		builder.setPositiveButton(R.string.ok, (dlg, i) -> {
			String name = etName.getText().toString();
			if (name.isEmpty()) {
				return;
			}
			// Save facial features in knownFaces
			if (!faceStorage.extendRegistered(name, rec.getExtra(), true)) {
				Toast.makeText(this, R.string.register_failed, Toast.LENGTH_LONG).show();
			}
			dlg.dismiss();
		});
		builder.setView(dialogLayout);
		builder.show();
	}

}
