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
import android.os.Bundle;
import android.util.Pair;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;

import com.libremobileos.yifan.face.FaceDataEncoder;
import com.libremobileos.yifan.face.FaceDetector;
import com.libremobileos.yifan.face.FaceFinder;
import com.libremobileos.yifan.face.FaceScanner;

import java.util.ArrayList;
import java.util.List;

public class ScanActivity extends CameraActivity {

	// AI-based detector
	private FaceFinder faceRecognizer;
	// Simple view allowing us to draw a circle over the Preview
	private CircleOverlayView overlayView;
	// If we are waiting for a face to be added to knownFaces
	private long lastAdd;
	private final List<FaceScanner.Face> faces = new ArrayList<>();
	private TextView subText;

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		// Initialize basic views
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_enroll);
		FrameLayout f = findViewById(R.id.frameLayout);
		getLayoutInflater().inflate(R.layout.enroll_main, f);
		connectToCam(f.findViewById(R.id.viewFinder));
		overlayView = f.findViewById(R.id.overlay);
		subText = f.findViewById(R.id.textView);
		subText.setText(R.string.scan_face_now);
		findViewById(R.id.button2).setOnClickListener(v -> {
			startActivity(new Intent(this, SettingsActivity.class));
			finish();
		});
		findViewById(R.id.button).setVisibility(View.GONE);
	}

	@OptIn(markerClass = ExperimentalGetImage.class)
	protected void onSetCameraCallback(ImageAnalysis imageAnalysis) {
		imageAnalysis.setAnalyzer(getMainExecutor(), imageProxy -> {
			if (faces.size() == 10) {
				imageProxy.close();
				return;
			}
			// Convert CameraX Image to Bitmap and process it
			// Return list of detected faces
			List<Pair<FaceDetector.Face, FaceScanner.Face>> data = faceRecognizer.process(BitmapUtils.getBitmap(imageProxy), false);

			if (data.size() > 1) {
				if (lastAdd == -1) { // last frame had two faces too
					subText.setText(R.string.found_2_faces);
				}
				lastAdd = -1;
				imageProxy.close();
				return;
			} else if (lastAdd == -1) {
				lastAdd = System.currentTimeMillis();
			}
			if (data.size() == 0) {
				if (lastAdd == -2) { // last frame had 0 faces too
					subText.setText(R.string.cant_find_face);
				}
				lastAdd = -2;
				imageProxy.close();
				return;
			} else if (lastAdd == -2) {
				lastAdd = System.currentTimeMillis();
			}

			Pair<FaceDetector.Face, FaceScanner.Face> face = data.get(0);

			// Do we want to add a new face?
			if (lastAdd + 1000 < System.currentTimeMillis()) {
				lastAdd = System.currentTimeMillis();
				if (face.second.getBrightnessHint() < 1) {
					subText.setText(R.string.cant_scan_face);
					imageProxy.close();
					return;
				} else {
					subText.setText(R.string.scan_face_now);
				}
				faces.add(face.second);
				overlayView.setPercentage(faces.size() * 10);
			}

			if (faces.size() == 10) {
				startActivity(new Intent(this, EnrollActivity.class).putExtra("faces",
						FaceDataEncoder.encode(faces.stream().map(FaceScanner.Face::getExtra).toArray(float[][]::new))));
				finish();
			}

			// Clean up
			imageProxy.close();
		});

		// Create AI-based face detection
		faceRecognizer = FaceFinder.create(this,
				0.6f, /* minimum confidence to consider object as face */
				width, /* bitmap width */
				height, /* bitmap height */
				0 /* CameraX rotates the image for us, so we chose to IGNORE sensorRotation altogether */
		);
	}
}
