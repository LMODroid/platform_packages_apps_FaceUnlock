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
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

/** Detect multiple faces in one large bitmap and return their locations */
public class FaceDetector {
	// Asset manager to load TFLite model
	private final AssetManager am;
	// TFLite Model API
	private SimilarityClassifier classifier;
	// Optional settings
	private final boolean hwAccleration, enhancedHwAccleration;
	private final int numThreads;
	// Face Detection model parameters
	private static final int TF_FD_API_INPUT_SIZE = 300;
	private static final boolean TF_FD_API_IS_QUANTIZED = true;
	private static final String TF_FD_API_MODEL_FILE = "detect-class1.tflite";
	private static final String TF_FD_API_LABELS_FILE = "file:///android_asset/detect-class1.txt";
	// Minimum detection confidence to track a detection.
	private static final float MINIMUM_CONFIDENCE_TF_FD_API = 0.6f;
	// Maintain aspect ratio or squish image?
	private static final boolean MAINTAIN_ASPECT = false;

	// Wrapper around Bitmap to avoid user passing unprocessed data
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

	// Processes Bitmaps to compatible format
	public static class InputImageProcessor {
		private final Matrix frameToCropTransform;
		private final Matrix cropToFrameTransform = new Matrix();

		public InputImageProcessor(int inputWidth, int inputHeight, int sensorOrientation) {
			frameToCropTransform =
					ImageUtils.getTransformationMatrix(
							inputWidth, inputHeight,
							TF_FD_API_INPUT_SIZE, TF_FD_API_INPUT_SIZE,
							sensorOrientation, MAINTAIN_ASPECT);
			frameToCropTransform.invert(cropToFrameTransform);
		}

		public InputImage process(Bitmap input) {
			Bitmap croppedBitmap = Bitmap.createBitmap(TF_FD_API_INPUT_SIZE, TF_FD_API_INPUT_SIZE, Bitmap.Config.ARGB_8888);
			final Canvas canvas = new Canvas(croppedBitmap);
			canvas.drawBitmap(input, frameToCropTransform, null);
			return new InputImage(croppedBitmap, cropToFrameTransform);
		}
	}


	/** An immutable result returned by a FaceDetector describing what was recognized. */
	public static class Face {
		/**
		 * A unique identifier for what has been recognized. Specific to the class, not the instance of
		 * the object.
		 */
		private final String id;

		/** Display name for the recognition. */
		private final String title;

		/**
		 * A sortable score for how good the recognition is relative to others. Higher should be better. Min: 0f Max: 1.0f
		 */
		private final Float confidence;

		/** Optional location within the source image for the location of the recognized object. */
		private final RectF location;

		public Face(
				final String id, final String title, final Float confidence, final RectF location) {
			this.id = id;
			this.title = title;
			this.confidence = confidence;
			this.location = location;
		}

		public String getId() {
			return id;
		}

		public String getTitle() {
			return title;
		}

		public Float getConfidence() {
			return confidence;
		}

		public RectF getLocation() {
			return new RectF(location);
		}

		@NonNull
		@Override
		public String toString() {
			String resultString = "";
			if (id != null) {
				resultString += "[" + id + "] ";
			}

			if (title != null) {
				resultString += title + " ";
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

	public static FaceDetector create(Context context, boolean hwAccleration, boolean enhancedHwAccleration, int numThreads) {
		return new FaceDetector(context.getAssets(), hwAccleration, enhancedHwAccleration, numThreads);
	}

	public static FaceDetector create(Context context) {
		return create(context, false, true, 4);
	}

	private FaceDetector(AssetManager am, boolean hwAccleration, boolean enhancedHwAccleration, int numThreads) {
		this.am = am;
		this.hwAccleration = hwAccleration;
		this.enhancedHwAccleration = enhancedHwAccleration;
		this.numThreads = numThreads;
	}

	private SimilarityClassifier getClassifier() throws IOException {
		if (classifier == null) {
			classifier = SimilarityClassifier.create(am,
					TF_FD_API_MODEL_FILE,
					TF_FD_API_LABELS_FILE,
					TF_FD_API_INPUT_SIZE,
					TF_FD_API_IS_QUANTIZED,
					hwAccleration,
					enhancedHwAccleration,
					numThreads
			);
		}
		return classifier;
	}

	public List<Face> detectFaces(InputImage input) {
		try {
			List<SimilarityClassifier.Recognition> results = getClassifier().recognizeImage(input.getProcessedImage());

			final List<Face> mappedRecognitions = new LinkedList<>();
			for (final SimilarityClassifier.Recognition result : results) {
				final RectF location = result.getLocation();
				if (location != null && result.getDistance() >= MINIMUM_CONFIDENCE_TF_FD_API) {
					input.getCropToFrameTransform().mapRect(location);
					mappedRecognitions.add(new Face(result.getId(), result.getTitle(), result.getDistance(), location));
				}
			}
			return mappedRecognitions;
		} catch (IOException e) {
			Log.e("FaceDetector", Log.getStackTraceString(e));
			return null;
		}
	}
}
