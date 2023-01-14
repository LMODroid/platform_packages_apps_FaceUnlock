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

package com.libremobileos.yifan.face;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Pair;

import java.util.ArrayList;
import java.util.List;

/**
 * Combination of FaceDetector and FaceScanner for workloads where
 * both face detection and face scanning are required. However, this
 * class makes no assumptions about the workload and is therefore bare-bones.
 * Because of this, usage of an task-specific class like FaceRecognizer
 * is highly recommended, unless these do not fit your usecase.
 */
public class FaceFinder {
	private final FaceDetector faceDetector;
	private final FaceDetector.InputImageProcessor detectorInputProc;
	private final FaceScanner faceScanner;
	private final int sensorOrientation;

	private FaceFinder(Context ctx, int inputWidth, int inputHeight, int sensorOrientation, boolean hwAccleration, boolean enhancedHwAccleration, int numThreads) {
		this.faceDetector = FaceDetector.create(ctx, hwAccleration, enhancedHwAccleration, numThreads);
		this.faceScanner = FaceScanner.create(ctx, hwAccleration, enhancedHwAccleration, numThreads);
		this.sensorOrientation = sensorOrientation;
		this.detectorInputProc = new FaceDetector.InputImageProcessor(inputWidth, inputHeight, sensorOrientation);
	}

	public static FaceFinder create(Context ctx, int inputWidth, int inputHeight, int sensorOrientation, boolean hwAccleration, boolean enhancedHwAccleration, int numThreads) {
		return new FaceFinder(ctx, inputWidth, inputHeight, sensorOrientation, hwAccleration, enhancedHwAccleration, numThreads);
	}

	public static FaceFinder create(Context ctx, int inputWidth, int inputHeight, int sensorOrientation) {
		return create(ctx, inputWidth, inputHeight, sensorOrientation, false, true, 4);
	}

	public List<Pair<FaceDetector.Face, FaceScanner.Face>> process(Bitmap input) {
		FaceDetector.InputImage inputImage = detectorInputProc.process(input);

		final List<FaceDetector.Face> faces = faceDetector.detectFaces(inputImage);
		final List<Pair<FaceDetector.Face, FaceScanner.Face>> results = new ArrayList<>();

		if (faces != null && faces.size() > 0) {
			final FaceScanner.InputImageProcessor scannerInputProc = new FaceScanner.InputImageProcessor(input, sensorOrientation);

			for (FaceDetector.Face face : faces) {
				if (face == null) continue;

				FaceScanner.InputImage faceBmp = scannerInputProc.process(face.getLocation());
				if (faceBmp == null) continue;

				final FaceScanner.Face scanned = faceScanner.detectFace(faceBmp);
				if (scanned == null) continue;

				scanned.addData(face.getId(), face.getLocation());

				results.add(new Pair<>(face, scanned));
			}
		}

		return results;
	}
}
