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
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.util.Log;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

/**
 * Detect multiple faces in one large {@link Bitmap} and returns {@link Face} objects.
 * Requires preprocessed {@link InputImage} objects from {@link InputImageProcessor}.
 */
public class FaceDetector {
	// Asset manager to load TFLite model
	private final AssetManager am;
	// TFLite Model API
	private SimilarityClassifier classifier;
	// Optional settings
	private final boolean hwAcceleration, enhancedHwAcceleration;
	private final int numThreads;
	private final float minConfidence;
	// Face Detection model parameters
	private static final int TF_FD_API_INPUT_SIZE = 300;
	private static final boolean TF_FD_API_IS_QUANTIZED = true;
	private static final String TF_FD_API_MODEL_FILE = "detect-class1.tflite";
	private static final String TF_FD_API_LABELS_FILE = "detect-class1.txt";
	// Maintain aspect ratio or squish image?
	private static final boolean MAINTAIN_ASPECT = false;

	/**
	 * Wrapper around {@link Bitmap} to avoid user passing unprocessed data
	 * @see InputImageProcessor
	 */
	public static class InputImage {
		private final Bitmap processedImage;
		private final Matrix cropToFrameTransform;

		/* package-private */ InputImage(Bitmap processedImage, Matrix cropToFrameTransform) {
			this.processedImage = processedImage;
			this.cropToFrameTransform = cropToFrameTransform;
		}

		/* package-private */ Bitmap getProcessedImage() {
			return processedImage;
		}

		/* package-private */ Matrix getCropToFrameTransform() {
			return cropToFrameTransform;
		}
	}

	/**
	 * Processes {@link Bitmap}s to compatible format
	 * @see InputImage
	 */
	public static class InputImageProcessor {
		private final Matrix frameToCropTransform;
		private final Matrix cropToFrameTransform = new Matrix();

		/**
		 * Create new {@link InputImage} processor.
		 * @param inputWidth width of the {@link Bitmap}s that are going to be processed
		 * @param inputHeight height of the {@link Bitmap}s that are going to be processed
		 * @param sensorOrientation rotation if the image should be rotated, or 0.
		 */
		public InputImageProcessor(int inputWidth, int inputHeight, int sensorOrientation) {
			frameToCropTransform =
					ImageUtils.getTransformationMatrix(
							sensorOrientation % 180 != 0 ? inputHeight : inputWidth,
							sensorOrientation % 180 != 0 ? inputWidth : inputHeight,
							TF_FD_API_INPUT_SIZE, TF_FD_API_INPUT_SIZE,
							0, MAINTAIN_ASPECT);
			if (sensorOrientation != 0) {
				Matrix myRotationMatrix =
						ImageUtils.getTransformationMatrix(
								inputWidth, inputHeight,
								sensorOrientation % 180 != 0 ? inputHeight : inputWidth,
								sensorOrientation % 180 != 0 ? inputWidth : inputHeight,
								sensorOrientation % 360, false);
				frameToCropTransform.setConcat(frameToCropTransform, myRotationMatrix);
			}
			frameToCropTransform.invert(cropToFrameTransform);
		}

		/**
		 * Process {@link Bitmap} for use in AI model.
		 * @param input {@link Bitmap} with length/height that were specified in the constructor
		 * @return Processed {@link InputImage}
		 */
		public InputImage process(Bitmap input) {
			Bitmap croppedBitmap = Bitmap.createBitmap(TF_FD_API_INPUT_SIZE, TF_FD_API_INPUT_SIZE, Bitmap.Config.ARGB_8888);
			final Canvas canvas = new Canvas(croppedBitmap);
			canvas.drawBitmap(input, frameToCropTransform, null);
			return new InputImage(croppedBitmap, cropToFrameTransform);
		}
	}

	/** An immutable result returned by a {@link FaceDetector} describing what was recognized. */
	public static class Face {
		// A unique identifier for what has been recognized. Specific to the class, not the instance of
		// the object.
		private final String id;

		private final Float confidence;

		private final RectF location;

		/* package-private */ Face(
				final String id, final Float confidence, final RectF location) {
			this.id = id;
			this.confidence = confidence;
			this.location = location;
		}

		/* package-private */ String getId() {
			return id;
		}

		/**
		 * A score for how good the detection is relative to others.
		 * @return Sortable score, higher is better. Min: 0f Max: 1.0f
		 */
		public Float getConfidence() {
			return confidence;
		}

		/**
		 * Optional location within the source image for the location of the recognized object.
		 * @return {@link RectF} containing location on input image
		 */
		public RectF getLocation() {
			return new RectF(location);
		}

		@Override
		public String toString() {
			String resultString = "";
			if (id != null) {
				resultString += "[" + id + "] ";
			}

			if (confidence != null) {
				resultString += String.format(Locale.US, "(%.1f%%) ", confidence * 100.0f);
			}

			if (location != null) {
				resultString += location + " ";
			}

			return resultString.trim();
		}

	}

	/**
	 * Create {@link FaceDetector} instance.
	 * @param context Android {@link Context} object, may be in background.
	 * @param minConfidence Minimum confidence to track a detection, must be higher than 0.0f and smaller than 1.0f
	 * @param hwAcceleration Enable hardware acceleration (NNAPI/GPU)
	 * @param enhancedHwAcceleration if hwAcceleration is enabled, use NNAPI instead of GPU. if not, this toggles XNNPACK
	 * @param numThreads How many threads to use, if running on CPU or with XNNPACK
	 * @return {@link FaceDetector} instance.
	 * @see #create(Context, float)
	 */
	public static FaceDetector create(Context context, float minConfidence, boolean hwAcceleration, boolean enhancedHwAcceleration, int numThreads) {
		AssetManager assetmanager = null;
		if (context != null)
			assetmanager = context.getAssets();
		return new FaceDetector(assetmanager, minConfidence, hwAcceleration, enhancedHwAcceleration, numThreads);
	}

	/**
	 * Create {@link FaceDetector} instance with sensible defaults regarding hardware acceleration (CPU, XNNPACK, 4 threads).
	 * @param context Android {@link Context} object, may be in background.
	 * @param minConfidence Minimum confidence to track a detection, must be higher than 0.0f and smaller than 1.0f
	 * @return {@link FaceDetector} instance.
	 * @see #create(Context, float, boolean, boolean, int)
	 */
	@SuppressWarnings("unused")
	public static FaceDetector create(Context context, float minConfidence) {
		return create(context, minConfidence, false, true, 4);
	}

	private FaceDetector(AssetManager am, float minConfidence, boolean hwAcceleration, boolean enhancedHwAcceleration, int numThreads) {
		this.am = am;
		this.minConfidence = minConfidence;
		this.hwAcceleration = hwAcceleration;
		this.enhancedHwAcceleration = enhancedHwAcceleration;
		this.numThreads = numThreads;
	}

	private SimilarityClassifier getClassifier() throws IOException {
		if (classifier == null) {
			classifier = SimilarityClassifier.create(am,
					TF_FD_API_MODEL_FILE,
					TF_FD_API_LABELS_FILE,
					TF_FD_API_INPUT_SIZE,
					TF_FD_API_IS_QUANTIZED,
					hwAcceleration,
					enhancedHwAcceleration,
					numThreads
			);
		}
		return classifier;
	}

	/**
	 * Detect multiple faces in an {@link InputImage} and return their locations.
	 * @param input Image, processed with {@link InputImageProcessor}
	 * @return List of {@link Face} objects
	 */
	public List<Face> detectFaces(InputImage input) {
		try {
			List<SimilarityClassifier.Recognition> results = getClassifier().recognizeImage(input.getProcessedImage());

			final List<Face> mappedRecognitions = new LinkedList<>();
			for (final SimilarityClassifier.Recognition result : results) {
				final RectF location = result.getLocation();
				if (location != null && result.getDistance() >= minConfidence) {
					input.getCropToFrameTransform().mapRect(location);
					mappedRecognitions.add(new Face(result.getId(), result.getDistance(), location));
				}
			}
			return mappedRecognitions;
		} catch (IOException e) {
			Log.e("FaceDetector", Log.getStackTraceString(e));
			return null;
		}
	}
}
