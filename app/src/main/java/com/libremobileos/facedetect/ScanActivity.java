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
import android.util.Size;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

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

	private boolean computingDetection = false;

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

	@Override
	protected void setupFaceRecognizer(final Size bitmapSize) {
		// Create AI-based face detection
		faceRecognizer = FaceFinder.create(this,
				0.6f, /* minimum confidence to consider object as face */
				bitmapSize.getWidth(), /* bitmap width */
				bitmapSize.getHeight(), /* bitmap height */
				0 /* We rotates the image, so IGNORE sensorRotation altogether */
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

		if (faces.size() == 10) {
			readyForNextImage();
			return;
		}

		// Return list of detected faces
		List<Pair<FaceDetector.Face, FaceScanner.Face>> data = faceRecognizer.process(getCroppedBitmap(), false);
		computingDetection = false;

		if (data.size() > 1) {
			if (lastAdd == -1) { // last frame had two faces too
				subText.setText(R.string.found_2_faces);
			}
			lastAdd = -1;
			readyForNextImage();
			return;
		} else if (lastAdd == -1) {
			lastAdd = System.currentTimeMillis();
		}
		if (data.size() == 0) {
			if (lastAdd == -2) { // last frame had 0 faces too
				subText.setText(R.string.cant_find_face);
			}
			lastAdd = -2;
			readyForNextImage();
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
				readyForNextImage();
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
		readyForNextImage();
	}
}
