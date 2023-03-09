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
import android.util.Size;

import androidx.annotation.Nullable;

import com.libremobileos.yifan.face.DirectoryFaceStorageBackend;
import com.libremobileos.yifan.face.FaceRecognizer;
import com.libremobileos.yifan.face.FaceStorageBackend;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends CameraActivity {
	// AI-based detector
	private FaceRecognizer faceRecognizer;
	// Simple view allowing us to draw Rectangles over the Preview
	private FaceBoundsOverlayView overlayView;
	private boolean computingDetection = false;

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

	@Override
	protected void setupFaceRecognizer(final Size bitmapSize) {
		// Store registered Faces in Memory
		//FaceStorageBackend faceStorage = new VolatileFaceStorageBackend();
		//FaceStorageBackend faceStorage = new SharedPreferencesFaceStorageBackend(getSharedPreferences("faces", 0));
		FaceStorageBackend faceStorage = new DirectoryFaceStorageBackend(getFilesDir());

		// Create AI-based face detection
		faceRecognizer = FaceRecognizer.create(this,
				faceStorage, /* face data storage */
				0.6f, /* minimum confidence to consider object as face */
				bitmapSize.getWidth(), /* bitmap width */
				bitmapSize.getHeight(), /* bitmap height */
				0, /* We rotates the image, so IGNORE sensorRotation altogether */
				0.7f, /* maximum distance (to saved face model, not from camera) to track face */
				1 /* minimum model count to track face */
		);
	}

	@Override
	protected void processImage() {
		// No mutex needed as this method is not reentrant.
		if (computingDetection) {
			readyForNextImage();
			return;
		}
		computingDetection = true;
		List<FaceRecognizer.Face> data = faceRecognizer.recognize(getCroppedBitmap());
		computingDetection = false;

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
		readyForNextImage();
	}

}
