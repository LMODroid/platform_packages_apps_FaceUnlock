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

package com.libremobileos.yifan.face;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Pair;

import java.util.ArrayList;
import java.util.List;

/**
 * Combination of {@link FaceDetector} and {@link FaceScanner}
 * for workloads where both face detection and face scanning are required.
 * However, this class makes no assumptions about the workload and is therefore bare-bones.
 * Because of this, usage of a task-specific class like {@link FaceRecognizer}
 * is highly recommended, unless these do not fit your use case.
 */
public class FaceFinder {
	private final FaceDetector faceDetector;
	private final FaceDetector.InputImageProcessor detectorInputProcessor;
	/* package-private */ final FaceScanner faceScanner;
	private final int sensorOrientation;

	private FaceFinder(Context ctx, float minConfidence, int inputWidth, int inputHeight, int sensorOrientation, boolean hwAcceleration, boolean enhancedHwAcceleration, int numThreads) {
		this.faceDetector = FaceDetector.create(ctx, minConfidence, hwAcceleration, enhancedHwAcceleration, numThreads);
		this.faceScanner = FaceScanner.create(ctx, hwAcceleration, enhancedHwAcceleration, numThreads);
		this.sensorOrientation = sensorOrientation;
		this.detectorInputProcessor = new FaceDetector.InputImageProcessor(inputWidth, inputHeight, sensorOrientation);
	}

	/**
	 * Create new {@link FaceFinder} instance.
	 * @param ctx Android {@link Context} object, may be in background.
	 * @param minConfidence Minimum confidence to track a detection, must be higher than 0.0f and smaller than 1.0f
	 * @param inputWidth width of the {@link Bitmap}s that are going to be processed
	 * @param inputHeight height of the {@link Bitmap}s that are going to be processed
	 * @param sensorOrientation rotation if the image should be rotated, or 0.
	 * @param hwAcceleration Enable hardware acceleration (NNAPI/GPU)
	 * @param enhancedHwAcceleration if hwAcceleration is enabled, use NNAPI instead of GPU. if not, this toggles XNNPACK
	 * @param numThreads How many threads to use, if running on CPU or with XNNPACK
	 * @return {@link FaceFinder} instance
	 * @see #create(Context, float, int, int, int)
	 */
	public static FaceFinder create(Context ctx, float minConfidence, int inputWidth, int inputHeight, int sensorOrientation, boolean hwAcceleration, boolean enhancedHwAcceleration, int numThreads) {
		return new FaceFinder(ctx, minConfidence, inputWidth, inputHeight, sensorOrientation, hwAcceleration, enhancedHwAcceleration, numThreads);
	}

	/**
	 * Create new {@link FaceFinder} instance  with sensible defaults regarding hardware acceleration (CPU, XNNPACK, 4 threads).
	 * @param ctx Android {@link Context} object, may be in background.
	 * @param minConfidence Minimum confidence to track a detection, must be higher than 0.0f and smaller than 1.0f
	 * @param inputWidth width of the {@link Bitmap}s that are going to be processed
	 * @param inputHeight height of the {@link Bitmap}s that are going to be processed
	 * @param sensorOrientation rotation if the image should be rotated, or 0.
	 * @return FaceFinder instance
	 * @see #create(Context, float, int, int, int, boolean, boolean, int)
	 */
	@SuppressWarnings("unused")
	public static FaceFinder create(Context ctx, float minConfidence, int inputWidth, int inputHeight, int sensorOrientation) {
		return create(ctx, minConfidence, inputWidth, inputHeight, sensorOrientation, false, true, 4);
	}

	/**
	 * Process a Bitmap using {@link FaceDetector},
	 * scanning the resulting found faces using {@link FaceScanner} after manually cropping the image.
	 * Adds extra metadata (location) to {@link FaceScanner.Face} based on best effort basis.
	 * @param input Bitmap to process.
	 * @param allowPostprocessing Allow postprocessing to improve detection quality. Undesirable when registering faces.
	 * @return {@link List} of {@link Pair}s of detection results from {@link FaceDetector} and {@link FaceScanner}
	 */
	public List<Pair<FaceDetector.Face, FaceScanner.Face>> process(Bitmap input, boolean allowPostprocessing) {
		FaceDetector.InputImage inputImage = detectorInputProcessor.process(input);

		final List<FaceDetector.Face> faces = faceDetector.detectFaces(inputImage);
		final List<Pair<FaceDetector.Face, FaceScanner.Face>> results = new ArrayList<>();

		if (faces != null && faces.size() > 0) {
			final FaceScanner.InputImageProcessor scannerInputProcessor = new FaceScanner.InputImageProcessor(input, sensorOrientation);

			for (FaceDetector.Face face : faces) {
				if (face == null) continue;

				FaceScanner.InputImage faceBmp = scannerInputProcessor.process(face.getLocation());
				if (faceBmp == null) continue;

				final FaceScanner.Face scanned = faceScanner.detectFace(faceBmp, allowPostprocessing);
				if (scanned == null) continue;

				scanned.addData(face.getId(), face.getLocation());

				results.add(new Pair<>(face, scanned));
			}
		}

		return results;
	}
}
