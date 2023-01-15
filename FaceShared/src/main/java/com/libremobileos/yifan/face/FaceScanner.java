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
import java.util.List;
import java.util.Locale;

/**
 * Raw wrapper around AI model that scans ONE Face inside a perfectly cropped Bitmap and returns facial features.
 * Most likely, specialized classes like {@link FaceRecognizer} or {@link FaceFinder}
 * fit your use case better.
 */
public class FaceScanner {
	// Asset manager to load TFLite model
	private final AssetManager am;
	// TFLite Model API
	private SimilarityClassifier classifier;
	// Optional settings
	private final boolean hwAcceleration, enhancedHwAcceleration;
	private final int numThreads;
	// MobileFaceNet model parameters
	private static final int TF_OD_API_INPUT_SIZE = 112;
	private static final boolean TF_OD_API_IS_QUANTIZED = false;
	private static final String TF_OD_API_MODEL_FILE = "mobile_face_net.tflite";
	private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/mobile_face_net.txt";
	// Maintain aspect ratio or squish image?
	private static final boolean MAINTAIN_ASPECT = false;

	/**
	 * Wrapper around Bitmap to avoid user passing unprocessed data
	 * @see InputImageProcessor
	 */
	public static class InputImage {
		private final Bitmap processedImage;
		private final Bitmap userDisplayableImage;

		/* package-private */ InputImage(Bitmap processedImage, Bitmap userDisplayableImage) {
			this.processedImage = processedImage;
			this.userDisplayableImage = userDisplayableImage;
		}

		/* package-private */ Bitmap getProcessedImage() {
			return processedImage;
		}

		/* package-private */ Bitmap getUserDisplayableImage() {
			return userDisplayableImage;
		}
	}

	/**
	 * Processes Bitmaps to compatible format.
	 * This class supports 2 modes of operation:<br>
	 * 1. Preprocess perfectly cropped {@link Bitmap} to AI-compatible format, using the static method {@link #process(Bitmap, int)}<br>
	 * 2. Crop one large {@link Bitmap} to multiple {@link InputImage}s using bounds inside {@link RectF} objects,
	 *    with {@link #InputImageProcessor(Bitmap, int)} and {@link #process(RectF)}.
	 *    This allows processing multiple faces on one {@link Bitmap}, for usage with {@link FaceDetector} and similar classes.
	 * @see InputImage
	 */
	public static class InputImageProcessor {
		private final int sensorOrientation;
		private final Bitmap portraitBmp;
		private final Matrix transform;

		/**
		 * If the class gets instantiated, we enter a special mode of operation for detecting multiple faces on one large {@link Bitmap}.
		 * @param rawImage The image with all faces to be detected
		 * @param sensorOrientation rotation if the image should be rotated, or 0.
		 */
		public InputImageProcessor(Bitmap rawImage, int sensorOrientation) {
			this.sensorOrientation = sensorOrientation;
			Bitmap portraitBmp = Bitmap.createBitmap(
					(sensorOrientation % 180) == 90 ? rawImage.getHeight() : rawImage.getWidth(),
					(sensorOrientation % 180) == 90 ? rawImage.getWidth() : rawImage.getHeight(), Bitmap.Config.ARGB_8888);
			transform = ImageUtils.getTransformationMatrix(
					rawImage.getWidth(),
					rawImage.getHeight(),
					rawImage.getWidth(),
					rawImage.getHeight(),
					sensorOrientation,
					MAINTAIN_ASPECT);
			final Canvas cv = new Canvas(portraitBmp);
			cv.drawBitmap(rawImage, transform, null);
			this.portraitBmp = portraitBmp;
		}

		/**
		 * In normal mode of operation, we take a perfectly cropped {@link Bitmap} containing one face and process it.
		 * @param input Bitmap to process.
		 * @param sensorOrientation rotation if the image should be rotated, or 0.
		 * @return Converted {@link InputImage}
		 */
		public static InputImage process(Bitmap input, int sensorOrientation) {
			Matrix frameToCropTransform =
					ImageUtils.getTransformationMatrix(
							input.getWidth(), input.getHeight(),
							TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE,
							sensorOrientation, MAINTAIN_ASPECT);
			Bitmap croppedBitmap = Bitmap.createBitmap(TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE, Bitmap.Config.ARGB_8888);
			final Canvas canvas = new Canvas(croppedBitmap);
			canvas.drawBitmap(input, frameToCropTransform, null);
			return new InputImage(croppedBitmap, input);
		}

		/**
		 * In normal mode of operation, we take a perfectly cropped {@link Bitmap} containing one face and process it.
		 * This utility method uses sensorOrientation that was passed in the constructor and calls {@link #process(Bitmap, int)}
		 * @param input Bitmap to process.
		 * @return Converted {@link InputImage}
		 * @see #process(Bitmap, int)
		 */
		public InputImage process(Bitmap input) {
			return process(input, sensorOrientation);
		}

		/**
		 * In special mode of operation, we crop the image to detect multiple faces on one large {@link Bitmap} (in multiple passes).
		 * @param inputBB {@link RectF} containing location of face cropped out next
		 * @return Converted {@link InputImage}
		 */
		public InputImage process(RectF inputBB) {
			RectF faceBB = new RectF(inputBB);
			transform.mapRect(faceBB);
			if (faceBB.left < 0 || faceBB.top < 0 || faceBB.bottom < 0 ||
					faceBB.right < 0 || (faceBB.left + faceBB.width()) > portraitBmp.getWidth()
					|| (faceBB.top + faceBB.height()) > portraitBmp.getHeight()) return null;
			return process(Bitmap.createBitmap(portraitBmp,
					(int) faceBB.left,
					(int) faceBB.top,
					(int) faceBB.width(),
					(int) faceBB.height()));
		}
	}

	/** An immutable result returned by a FaceDetector describing what was recognized. */
	public static class Face {
		// A unique identifier for what has been recognized. Specific to the class, not the instance of
		// the object.
		private String id;

		private String title;

		private Float distance;

		private RectF location;

		private final Bitmap crop;

		private final float[] extra;

		/* package-private */ Face(
				final String id, final String title, final Float distance, final RectF location, final Bitmap crop, final float[] extra) {
			this.id = id;
			this.title = title;
			this.distance = distance;
			this.location = location;
			this.crop = crop;
			this.extra = extra;
		}

		/* package-private */ String getId() {
			return id;
		}

		/**
		 * Display name for the recognition.
		 * @return Title as {@link String}
		 */
		public String getTitle() {
			return title;
		}

		/**
		 * A score for how good the recognition is relative to others.
		 * @return Sortable score. Lower is better.
		 */
		public Float getDistance() {
			return distance;
		}

		/**
		 * Optional location within the source image for the location of the recognized object.
		 * @return {@link RectF} containing location on input image
		 */
		public RectF getLocation() {
			return new RectF(location);
		}

		/**
		 * Optional, source bitmap
		 * @return User-displayable {@link Bitmap} containing the cropped face
		 */
		public Bitmap getCrop() {
			if (crop == null) return null;
			return Bitmap.createBitmap(crop);
		}

		/**
		 * Optional, raw AI output
		 * @return Facial features encoded in float[]
		 */
		public float[] getExtra() {
			return extra;
		}

		// add metadata from FaceDetector
		/* package-private */ void addData(String id, RectF location) {
			this.id = id;
			this.location = location;
		}

		/**
		 * Add metadata obtainable after face recognition.
		 * @param title The new title (name) to store.
		 * @param distance The new distance to store.
		 */
		public void addRecognitionData(String title, float distance) {
			this.title = title;
			this.distance = distance;
		}

		/**
		 * Test if the face has already been recognized (if {@link #addRecognitionData(String, float)} has been called)
		 * @return equivalent of {@code getDistance() < Float.MAX_VALUE}
		 */
		public boolean isRecognized() {
			return getDistance() < Float.MAX_VALUE;
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

			if (distance != null) {
				resultString += String.format(Locale.US, "(%.1f%%) ", distance * 100.0f);
			}

			if (location != null) {
				resultString += location + " ";
			}

			return resultString.trim();
		}

		/**
		 * Compare two {@link Face}s
		 * @param other The {@link #getExtra() extra} from the other face.
		 * @return The {@link #getDistance() distance}, lower is better.
		 * @see #compare(Face)
		 */
		public float compare(float[] other) {
			final float[] emb = normalizeFloat(extra);
			final float[] knownEmb = normalizeFloat(other);
			float distance = 0;
			for (int i = 0; i < emb.length; i++) {
				float diff = emb[i] - knownEmb[i];
				distance += diff*diff;
			}
			return (float) Math.sqrt(distance);
		}

		/**
		 * Compare two {@link Face}s
		 * @param other The other face.
		 * @return The {@link #getDistance() distance}, lower is better.
		 * @see #compare(float[])
		 */
		@SuppressWarnings("unused")
		public float compare(Face other) {
			return compare(other.getExtra());
		}

		private static float sumSquares(float[] data) {
			float ans = 0.0f;
			for (float datum : data) {
				ans += datum * datum;
			}
			return (ans);
		}

		private static float[] normalizeFloat(float[] emb) {
			float [] norm_out = new float[512];
			double norm  = Math.sqrt(sumSquares(emb));
			for (int i=0;i< emb.length;i++){
				norm_out[i] = (float)(emb[i]/norm);
			}
			return norm_out;
		}
	}

	/**
	 * Create {@link FaceScanner} instance.
	 * @param context Android {@link Context} object, may be in background.
	 * @param hwAcceleration Enable hardware acceleration (NNAPI/GPU)
	 * @param enhancedHwAcceleration if hwAcceleration is enabled, use NNAPI instead of GPU. if not, this toggles XNNPACK
	 * @param numThreads How many threads to use, if running on CPU or with XNNPACK
	 * @return {@link FaceScanner} instance.
	 * @see #create(Context)
	 */
	public static FaceScanner create(Context context, boolean hwAcceleration, boolean enhancedHwAcceleration, int numThreads) {
		return new FaceScanner(context.getAssets(), hwAcceleration, enhancedHwAcceleration, numThreads);
	}

	/**
	 * Create {@link FaceScanner} instance with sensible defaults regarding hardware acceleration (CPU, XNNPACK, 4 threads).
	 * @param context Android {@link Context} object, may be in background.
	 * @return {@link FaceScanner} instance.
	 * @see #create(Context, boolean, boolean, int)
	 */
	@SuppressWarnings("unused")
	public static FaceScanner create(Context context) {
		return create(context, false, true, 4);
	}

	private FaceScanner(AssetManager am, boolean hwAcceleration, boolean enhancedHwAcceleration, int numThreads) {
		this.am = am;
		this.hwAcceleration = hwAcceleration;
		this.enhancedHwAcceleration = enhancedHwAcceleration;
		this.numThreads = numThreads;
	}

	private SimilarityClassifier getClassifier() throws IOException {
		if (classifier == null) {
			classifier = SimilarityClassifier.create(am,
					TF_OD_API_MODEL_FILE,
					TF_OD_API_LABELS_FILE,
					TF_OD_API_INPUT_SIZE,
					TF_OD_API_IS_QUANTIZED,
					hwAcceleration,
					enhancedHwAcceleration,
					numThreads
			);
		}
		return classifier;
	}

	/**
	 * Scan the face inside the {@link InputImage}.
	 * @param input The {@link InputImage} to process
	 * @return {@link Face}
	 */
	public Face detectFace(InputImage input) {
		try {
			List<SimilarityClassifier.Recognition> results = getClassifier().recognizeImage(input.getProcessedImage());
			SimilarityClassifier.Recognition result = results.get(0);
			return new Face(result.getId(), result.getTitle(), result.getDistance(), null, input.getUserDisplayableImage(), result.getExtra()[0]);
		} catch (IOException e) {
			Log.e("FaceScanner", Log.getStackTraceString(e));
			return null;
		}
	}
}
