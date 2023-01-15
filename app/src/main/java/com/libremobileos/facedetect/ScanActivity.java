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
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;

import com.libremobileos.yifan.face.FaceDataEncoder;
import com.libremobileos.yifan.face.FaceRecognizer;
import com.libremobileos.yifan.face.FaceScanner;
import com.libremobileos.yifan.face.FaceStorageBackend;
import com.libremobileos.yifan.face.SharedPreferencesFaceStorageBackend;
import com.libremobileos.yifan.face.VolatileFaceStorageBackend;

import java.util.ArrayList;
import java.util.List;

public class ScanActivity extends CameraActivity {

	// AI-based detector
	private FaceRecognizer faceRecognizer;
	// Simple view allowing us to draw a circle over the Preview
	private CircleOverlayView overlayView;
	// If we are waiting for a face to be added to knownFaces
	private long lastAdd;
	private final List<FaceRecognizer.Face> faces = new ArrayList<>();
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
		overlayView.setOnClickListener(v -> {
				startActivity(new Intent(this, SettingsActivity.class));
				finish();
		});
		subText = f.findViewById(R.id.textView);
		subText.setText("Scan your face now");
		findViewById(R.id.button2).setOnClickListener(v -> {
			startActivity(new Intent(this, MainActivity.class));
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
			List<FaceRecognizer.Face> data = faceRecognizer.recognize(BitmapUtils.getBitmap(imageProxy));

			if (data.size() > 1) {
				if (lastAdd == -1) { // last frame had two faces too
					subText.setText("Almost nobody has 2 faces, and I'm pretty sure you don't :)");
				}
				lastAdd = -1;
				imageProxy.close();
				return;
			} else if (lastAdd == -1) {
				lastAdd = System.currentTimeMillis();
				subText.setText("Scan your face now");
			}
			if (data.size() == 0) {
				if (lastAdd == -2) { // last frame had 0 faces too
					subText.setText("Where's your face?");
				}
				lastAdd = -2;
				imageProxy.close();
				return;
			} else if (lastAdd == -2) {
				lastAdd = System.currentTimeMillis();
				subText.setText("Scan your face now");
			}

			FaceRecognizer.Face face = data.get(0);

			// Do we want to add a new face?
			if (lastAdd + 1000 < System.currentTimeMillis()) {
				lastAdd = System.currentTimeMillis();
				if (face.getBrightnessHint() < 0) {
					subText.setText("Can't properly see your face, maybe turn the lamp on?");
					return;
				}
				faces.add(face);
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

		// We don't need recognition here
		FaceStorageBackend faceStorage = new VolatileFaceStorageBackend();

		// Create AI-based face detection
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
}
