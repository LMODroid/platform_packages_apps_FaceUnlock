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

import android.content.Intent;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.Pair;

import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;

import com.libremobileos.yifan.face.FaceRecognizer;
import com.libremobileos.yifan.face.FaceStorageBackend;
import com.libremobileos.yifan.face.SharedPreferencesFaceStorageBackend;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends CameraActivity {
	// AI-based detector
	private FaceRecognizer faceRecognizer;
	// Simple view allowing us to draw Rectangles over the Preview
	private FaceBoundsOverlayView overlayView;

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		// Initialize basic views
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		connectToCam(findViewById(R.id.viewFinder));

		overlayView = findViewById(R.id.overlay);
		overlayView.setOnClickListener(v -> {
			startActivity(new Intent(this, SettingsActivity.class));
			finish();
		});
	}

	@OptIn(markerClass = ExperimentalGetImage.class)
	@Override
	protected void onSetCameraCallback(ImageAnalysis imageAnalysis) {
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

		// Store registered Faces in Memory
		//faceStorage = new VolatileFaceStorageBackend();
		FaceStorageBackend faceStorage = new SharedPreferencesFaceStorageBackend(getSharedPreferences("faces", 0));

		// Create AI-based face detection
		faceRecognizer = FaceRecognizer.create(this,
				faceStorage, /* face data storage */
				0.6f, /* minimum confidence to consider object as face */
				width, /* bitmap width */
				height, /* bitmap height */
				0, /* CameraX rotates the image for us, so we chose to IGNORE sensorRotation altogether */
				0.7f, /* maximum distance (to saved face model, not from camera) to track face */
				1 /* minimum model count to track face */
		);
	}

}
