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
import android.graphics.RectF;
import android.util.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Task-specific API for detecting &amp; recognizing faces in an image.
 * Uses {@link FaceFinder} to detect and scan faces, {@link FaceStorageBackend} to store and retrieve the saved faces and returns the optimal result.<br>
 * Refrain from using this class for registering faces into the recognition system, {@link FaceFinder} does not perform post processing and is as such better suited.
 */
public class FaceRecognizer {
	private final FaceStorageBackend storage;
	private final FaceFinder detector;
	// Minimum detection confidence to track a detection.
	private final float maxDistance;
	// Minimum count of matching detection models.
	private final int minMatchingModels;
	// Minimum count of matching detection models. (ratio)
	private final float minModelRatio;

	private FaceRecognizer(Context ctx, FaceStorageBackend storage, float minConfidence, int inputWidth, int inputHeight, int sensorOrientation, float maxDistance, int minMatchingModels, float minModelRatio, boolean hwAcceleration, boolean enhancedHwAcceleration, int numThreads) {
		this.storage = storage;
		this.detector = FaceFinder.create(ctx, minConfidence, inputWidth, inputHeight, sensorOrientation, hwAcceleration, enhancedHwAcceleration, numThreads);
		this.maxDistance = maxDistance;
		this.minMatchingModels = minMatchingModels;
		this.minModelRatio = minModelRatio;
	}

	/**
	 * Create {@link FaceRecognizer} instance, with minimum matching model constraint.
	 * @param ctx Android {@link Context} object, may be in background.
	 * @param storage The {@link FaceStorageBackend} containing faces to be recognized.
	 * @param minConfidence Minimum confidence to track a detection, must be higher than 0.0f and smaller than 1.0f
	 * @param inputWidth width of the {@link Bitmap}s that are going to be processed
	 * @param inputHeight height of the {@link Bitmap}s that are going to be processed
	 * @param sensorOrientation rotation if the image should be rotated, or 0.
	 * @param maxDistance Maximum distance (difference, not 3D distance) to a saved face to count as recognized. Must be higher than 0.0f and smaller than 1.0f
	 * @param minMatchingModels Minimum count of matching models for one face to count as recognized. If undesired, set to 1
	 * @param hwAcceleration Enable hardware acceleration (NNAPI/GPU)
	 * @param enhancedHwAcceleration if hwAcceleration is enabled, use NNAPI instead of GPU. if not, this toggles XNNPACK
	 * @param numThreads How many threads to use, if running on CPU or with XNNPACK
	 * @return {@link FaceRecognizer} instance.
	 * @see #create(Context, FaceStorageBackend, float, int, int, int, float, float, boolean, boolean, int)
	 * @see #create(Context, FaceStorageBackend, float, int, int, int, float, float)
	 * @see #create(Context, FaceStorageBackend, float, int, int, int, float, int)
	 */
	public static FaceRecognizer create(Context ctx, FaceStorageBackend storage, float minConfidence, int inputWidth, int inputHeight, int sensorOrientation, float maxDistance, int minMatchingModels, boolean hwAcceleration, boolean enhancedHwAcceleration, int numThreads) {
		return new FaceRecognizer(ctx, storage, minConfidence, inputWidth, inputHeight, sensorOrientation, maxDistance, minMatchingModels, 0, hwAcceleration, enhancedHwAcceleration, numThreads);
	}

	/**
	 * Create {@link FaceRecognizer} instance, with matching model ratio constraint.
	 * @param ctx Android {@link Context} object, may be in background.
	 * @param storage The {@link FaceStorageBackend} containing faces to be recognized.
	 * @param minConfidence Minimum confidence to track a detection, must be higher than 0.0f and smaller than 1.0f
	 * @param inputWidth width of the {@link Bitmap}s that are going to be processed
	 * @param inputHeight height of the {@link Bitmap}s that are going to be processed
	 * @param sensorOrientation rotation if the image should be rotated, or 0.
	 * @param maxDistance Maximum distance (difference, not 3D distance) to a saved face to count as recognized. Must be higher than 0.0f and smaller than 1.0f
	 * @param minModelRatio Minimum count of matching models for one face to count as recognized. Must be higher or equal to 0.0f and smaller or equal to 1.0f. If undesired, set to 0f
	 * @param hwAcceleration Enable hardware acceleration (NNAPI/GPU)
	 * @param enhancedHwAcceleration if hwAcceleration is enabled, use NNAPI instead of GPU. if not, this toggles XNNPACK
	 * @param numThreads How many threads to use, if running on CPU or with XNNPACK
	 * @return {@link FaceRecognizer} instance.
	 * @see #create(Context, FaceStorageBackend, float, int, int, int, float, int, boolean, boolean, int)
	 * @see #create(Context, FaceStorageBackend, float, int, int, int, float, float)
	 * @see #create(Context, FaceStorageBackend, float, int, int, int, float, int)
	 */
	public static FaceRecognizer create(Context ctx, FaceStorageBackend storage, float minConfidence, int inputWidth, int inputHeight, int sensorOrientation, float maxDistance, float minModelRatio, boolean hwAcceleration, boolean enhancedHwAcceleration, int numThreads) {
		return new FaceRecognizer(ctx, storage, minConfidence, inputWidth, inputHeight, sensorOrientation, maxDistance, 0, minModelRatio, hwAcceleration, enhancedHwAcceleration, numThreads);
	}

	/**
	 * Create {@link FaceRecognizer} instance, with minimum matching model constraint and sensible defaults regarding hardware acceleration (CPU, XNNPACK, 4 threads).
	 * @param ctx Android {@link Context} object, may be in background.
	 * @param storage The {@link FaceStorageBackend} containing faces to be recognized.
	 * @param minConfidence Minimum confidence to track a detection, must be higher than 0.0f and smaller than 1.0f
	 * @param inputWidth width of the {@link Bitmap}s that are going to be processed
	 * @param inputHeight height of the {@link Bitmap}s that are going to be processed
	 * @param sensorOrientation rotation if the image should be rotated, or 0.
	 * @param maxDistance Maximum distance (difference, not 3D distance) to a saved face to count as recognized. Must be higher than 0.0f and smaller than 1.0f
	 * @param minMatchingModels Minimum count of matching models for one face to count as recognized. If undesired, set to 1
	 * @return {@link FaceRecognizer} instance.
	 * @see #create(Context, FaceStorageBackend, float, int, int, int, float, float, boolean, boolean, int)
	 * @see #create(Context, FaceStorageBackend, float, int, int, int, float, int, boolean, boolean, int)
	 * @see #create(Context, FaceStorageBackend, float, int, int, int, float, float)
	 */
	public static FaceRecognizer create(Context ctx, FaceStorageBackend storage, float minConfidence, int inputWidth, int inputHeight, int sensorOrientation, float maxDistance, int minMatchingModels) {
		return create(ctx, storage, minConfidence, inputWidth, inputHeight, sensorOrientation, maxDistance, minMatchingModels, false, true, 4);
	}

	/**
	 * Create {@link FaceRecognizer} instance, with matching model ratio constraint and sensible defaults regarding hardware acceleration (CPU, XNNPACK, 4 threads).
	 * @param ctx Android {@link Context} object, may be in background.
	 * @param storage The {@link FaceStorageBackend} containing faces to be recognized.
	 * @param minConfidence Minimum confidence to track a detection, must be higher than 0.0f and smaller than 1.0f
	 * @param inputWidth width of the {@link Bitmap}s that are going to be processed
	 * @param inputHeight height of the {@link Bitmap}s that are going to be processed
	 * @param sensorOrientation rotation if the image should be rotated, or 0.
	 * @param maxDistance Maximum distance (difference, not 3D distance) to a saved face to count as recognized. Must be higher than 0.0f and smaller than 1.0f
	 * @param minModelRatio Minimum count of matching models for one face to count as recognized. Must be higher or equal to 0.0f and smaller or equal to 1.0f. If undesired, set to 0f
	 * @return {@link FaceRecognizer} instance.
	 * @see #create(Context, FaceStorageBackend, float, int, int, int, float, int, boolean, boolean, int)
	 * @see #create(Context, FaceStorageBackend, float, int, int, int, float, float, boolean, boolean, int)
	 * @see #create(Context, FaceStorageBackend, float, int, int, int, float, int)
	 */
	@SuppressWarnings("unused")
	public static FaceRecognizer create(Context ctx, FaceStorageBackend storage, float minConfidence, int inputWidth, int inputHeight, int sensorOrientation, float maxDistance, float minModelRatio) {
		return create(ctx, storage, minConfidence, inputWidth, inputHeight, sensorOrientation, maxDistance, minModelRatio, false, true, 4);
	}

	/** Stores a combination of {@link FaceScanner.Face} and {@link FaceDetector.Face}, for face recognition workloads */
	public static class Face extends FaceScanner.Face {
		private final float confidence;
		private final int modelCount;
		private final float modelRatio;

		/* package-private */ Face(String id, String title, Float distance, Float confidence, RectF location, Bitmap crop, float[] extra, int modelCount, float modelRatio, float brightnessTest1, float brightnessTest2) {
			super(id, title, distance, location, crop, extra, brightnessTest1, brightnessTest2);
			this.confidence = confidence;
			this.modelRatio = modelRatio;
			this.modelCount = modelCount;
		}

		/* package-private */ Face(FaceScanner.Face original, Float confidence, int modelCount, float modelRatio) {
			this(original.getId(), original.getTitle(), original.getDistance(), confidence, original.getLocation(), original.getCrop(), original.getExtra(), modelCount, modelRatio, original.brightnessTest1, original.brightnessTest2);
		}

		/* package-private */ Face(FaceDetector.Face raw, FaceScanner.Face original, int modelCount, float modelRatio) {
			this(original, raw.getConfidence(), modelCount, modelRatio);
		}

		/**
		 * A score for how good the detection (NOT recognition, that's {@link #getDistance()}) is relative to others.
		 * @return Sortable score, higher is better. Min: 0f Max: 1.0f
		 */
		public float getDetectionConfidence() {
			return confidence;
		}

		/**
		 * How many models detected the face.
		 * @return Model count
		 */
		public int getModelCount() {
			return modelCount;
		}

		/**
		 * How many models detected the face, ratio. Min: 0f Max: 1f
		 * @return {@link #getModelCount()} divided through number of available models
		 */
		@SuppressWarnings("unused")
		public float getModelRatio() {
			return modelRatio;
		}
	}

	/**
	 * Detect faces and scan them
	 * @param input {@link Bitmap} to process
	 * @return {@link List} of {@link Face}s
	 */
	public List<Face> recognize(Bitmap input) {
		final Set<String> savedFaces = storage.getNames();
		final List<Pair<FaceDetector.Face, FaceScanner.Face>> faces = detector.process(input,
				true /* allow post processing, nobody will (should) use this class for registering faces */);
		final List<Face> results = new ArrayList<>();

		for (Pair<FaceDetector.Face, FaceScanner.Face> faceFacePair : faces) {
			FaceDetector.Face found = faceFacePair.first; // The generic Face object indicating where a Face is
			FaceScanner.Face scanned = faceFacePair.second; // The Face object with face-scanning data
			// Go through all saved faces and compare them with our scanned face
			int matchingModelsOut = 0;
			float modelRatioOut = 0;
			for (String savedName : savedFaces) {
				float[][] rawData = storage.get(savedName);
				int matchingModels = 0;
				float finalDistance = Float.MAX_VALUE;
				// Go through all saved models for one face
				for (float[] data : rawData) {
					float newDistance = scanned.compare(data);
					// If the similarity is really low (not the same face), don't save it
					if (newDistance < maxDistance) {
						matchingModels++;
						if (finalDistance > newDistance)
							finalDistance = newDistance;
					}
				}
				float modelRatio = (float)matchingModels / rawData.length;
				// If another known face had better similarity, don't save it
				if (minModelRatio > 0 ? minModelRatio < modelRatio :
						matchingModels >= Math.min(rawData.length, minMatchingModels) && finalDistance < scanned.getDistance()) {
					// We have a match! Save "Face identifier" and "Distance to original values"
					scanned.addRecognitionData(savedName, finalDistance);
					matchingModelsOut = matchingModels;
					modelRatioOut = modelRatio;
				}
			}

			results.add(new Face(found, scanned, matchingModelsOut, modelRatioOut));
		}
		return results;
	}
}
