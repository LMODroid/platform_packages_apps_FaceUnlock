/**
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

import static com.libremobileos.yifan.face.FaceScanner.MAXIMUM_DISTANCE_TF_OD_API;

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
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;
import com.libremobileos.yifan.face.FaceFinder;
import com.libremobileos.yifan.face.FaceScanner;
import com.libremobileos.yifan.face.FaceDetector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {

	// CameraX boilerplate
	private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
	// View showing camera frames
	private PreviewView previewView;
	// AI-based detector
	private FaceFinder faceFinder;
	// Simple view allowing us to draw Rectangles over the Preview
	private FaceBoundsOverlayView overlayView;
	// The desired camera input size
	private final Size desiredInputSize = new Size(640, 480);
	// Which camera to use
	private final int selectedCamera = CameraSelector.LENS_FACING_FRONT;
	// The calculated actual processing width & height
	private int width, height;
	// Map of "User visible name" and "Facial features stored as numbers"
	private final HashMap<String, float[]> knownFaces = new HashMap<>();
	// If we are waiting for an face to be added to knownFaces
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

		CameraSelector cameraSelector = new CameraSelector.Builder()
				.requireLensFacing(selectedCamera)
				.build();

		preview.setSurfaceProvider(previewView.getSurfaceProvider());

		// Cameras give us landscape images. If we are in portrait mode
		// (and want to process an portrait image), swap width/height to
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
			List<Pair<FaceDetector.Face, FaceScanner.Face>> data = faceFinder.process(BitmapUtils.getBitmap(imageProxy));
			ArrayList<Pair<RectF, String>> bounds = new ArrayList<>();

			for (Pair<FaceDetector.Face, FaceScanner.Face> faceFacePair : data) {
				// If you are confused why there are two different Face objects,
				// FaceDetector alone only finds Faces on an image and gives us
				// their location. We then crop the image based on the location data
				// and get facial features as numbers, which are stored in FaceScanner.Face

				FaceDetector.Face found = faceFacePair.first; // The generic Face object indicating where an Face is
				FaceScanner.Face scanned = faceFacePair.second; // The Face object with face-scanning data
				RectF boundingBox = new RectF(found.getLocation());

				if (selectedCamera == CameraSelector.LENS_FACING_FRONT) {
					// Camera is frontal so the image is flipped horizontally,
					// so flip it again.
					Matrix flip = new Matrix();
					flip.postScale(-1, 1, width / 2.0f, height / 2.0f);
					flip.mapRect(boundingBox);
				}

				// Call onFaceDetected() to generate UI text for face
				String uiText = onFaceDetected(found, scanned);
				bounds.add(new Pair<>(boundingBox, uiText));
			}

			// Pass bounds to View drawing rectangles
			overlayView.updateBounds(bounds, width, height);
			// Clean up
			imageProxy.close();
		});

		// Bind all objects together
		/* Camera camera = */ cameraProvider.bindToLifecycle((LifecycleOwner)this, cameraSelector, imageAnalysis, preview);

		// Create AI-based face detection
		faceFinder = FaceFinder.create(this, width, height, 0 /* CameraX rotates the image for us, so we chose to IGNORE sensorRotation altogether */);
	}

	private String onFaceDetected(FaceDetector.Face found, FaceScanner.Face scanned) {
		// Go through all saved faces and compare them with our scanned face
		for (Map.Entry<String, float[]> possible : knownFaces.entrySet()) {
			float newdistance = scanned.compare(possible.getValue());
			// If the similarity is really low (not the same face), don't save it
			// If another known face had better similarity, don't save it
			if (newdistance < MAXIMUM_DISTANCE_TF_OD_API && newdistance < scanned.getDistance()) {
				// We have a match! Save "Face identifier" and "Distance to original values"
				scanned.addRecognitionData(possible.getKey(), newdistance);
			}
		}
		// By now the best match, if any, will be stored in the "scanned" object

		// Do we have any match?
		if (scanned.isRecognized()) {
			// If yes, show the user-visible ID and the detection confidence
			return scanned.getTitle() + " " + scanned.getDistance();
		} else {
			// If no, do we want to add a new face?
			if (addPending) {
				// If we want to add a new face, show the dialog.
				runOnUiThread(() -> showAddFaceDialog(scanned));
				addPending = false;
			}
			// Show detected object type (always "Face") and how confident the AI is that this is an Face
			return found.getTitle() + " " + found.getConfidence();
		}
	}

	private void showAddFaceDialog(FaceScanner.Face rec) {
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
			knownFaces.put(name, rec.getExtra());
			dlg.dismiss();
		});
		builder.setView(dialogLayout);
		builder.show();
	}

}
