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

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.Pair;
import android.util.Size;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.libremobileos.yifan.face.DirectoryFaceStorageBackend;
import com.libremobileos.yifan.face.FaceRecognizer;
import com.libremobileos.yifan.face.FaceStorageBackend;
import com.libremobileos.yifan.face.ImageUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements CameraService.CameraCallback {
	// AI-based detector
	private FaceRecognizer faceRecognizer;
	// Simple view allowing us to draw Rectangles over the Preview
	private FaceBoundsOverlayView overlayView;
	private boolean computingDetection = false;
	private CameraService mCameraService;

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		// Initialize basic views
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		//connectToCam(findViewById(R.id.viewFinder));
		mCameraService = new CameraService(this, this);

		overlayView = findViewById(R.id.overlay);
		overlayView.setOnClickListener(v -> {
			startActivity(new Intent(this, SettingsActivity.class));
			finish();
		});
	}

	@Override
	protected void onResume() {
		super.onResume();
		mCameraService.startBackgroundThread();
		mCameraService.openCamera();
	}

	@Override
	protected void onPause() {
		mCameraService.closeCamera();
		mCameraService.stopBackgroundThread();
		super.onPause();
	}

	@Override
	public void setupFaceRecognizer(final Size bitmapSize, int rotation) {
		// Store registered Faces
		// example for in-memory: FaceStorageBackend faceStorage = new VolatileFaceStorageBackend();
		// example for shared preferences: FaceStorageBackend faceStorage = new SharedPreferencesFaceStorageBackend(getSharedPreferences("faces", 0));
		FaceStorageBackend faceStorage = new DirectoryFaceStorageBackend(getFilesDir());

		// Create AI-based face detection
		faceRecognizer = FaceRecognizer.create(this,
				faceStorage, /* face data storage */
				0.6f, /* minimum confidence to consider object as face */
				bitmapSize.getWidth(), /* bitmap width */
				bitmapSize.getHeight(), /* bitmap height */
				rotation,
				0.7f, /* maximum distance (to saved face model, not from camera) to track face */
				1 /* minimum model count to track face */
		);
	}

	@Override
	public void processImage(Size previewSize, Size rotatedSize, Bitmap rgbBitmap, int rotation) {
		// No mutex needed as this method is not reentrant.
		if (computingDetection) {
			mCameraService.readyForNextImage();
			return;
		}
		computingDetection = true;
		List<FaceRecognizer.Face> data = faceRecognizer.recognize(rgbBitmap);
		computingDetection = false;

		ArrayList<Pair<RectF, String>> bounds = new ArrayList<>();
		// Camera is frontal so the image is flipped horizontally,
		// so flip it again (and rotate Rect to match preview rotation)
		Matrix flip = ImageUtils.getTransformationMatrix(previewSize.getWidth(), previewSize.getHeight(), rotatedSize.getWidth(), rotatedSize.getHeight(), rotation, false);
		flip.preScale(1, -1, previewSize.getWidth() / 2f, previewSize.getHeight() / 2f);

		for (FaceRecognizer.Face face : data) {
			RectF boundingBox = new RectF(face.getLocation());
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
		overlayView.updateBounds(bounds, rotatedSize.getWidth(), rotatedSize.getHeight());
		mCameraService.readyForNextImage();
	}

}
