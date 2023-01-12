package com.libremobileos.yifan.face.shared;

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

public class FaceDetector {
	private final AssetManager am;
	private SimilarityClassifier classifier;
	// Face Detect
	private static final int TF_FD_API_INPUT_SIZE = 300;
	private static final boolean TF_FD_API_IS_QUANTIZED = true;
	private static final String TF_FD_API_MODEL_FILE = "detect-class1.tflite";
	private static final String TF_FD_API_LABELS_FILE = "file:///android_asset/face_labels_list.txt";
	private static final float MINIMUM_CONFIDENCE_TF_FD_API = 0.5f;
	private static final boolean MAINTAIN_ASPECT = false;

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

	public static FaceDetector create(Context context) {
		return new FaceDetector(context.getAssets());
	}

	public FaceDetector(AssetManager am) {
		this.am = am;
	}

	private SimilarityClassifier getClassifier() throws IOException {
		if (classifier == null) {
			classifier = SimilarityClassifier.create(am,
					TF_FD_API_MODEL_FILE,
					TF_FD_API_LABELS_FILE,
					TF_FD_API_INPUT_SIZE,
					TF_FD_API_IS_QUANTIZED
			);
		}
		return classifier;
	}

	public void setUseNNAPI(boolean useNNAPI) {
		try {
			getClassifier().setUseNNAPI(useNNAPI);
		} catch (IOException ignored) {
			// if it doesn't initialize, crash at a later point.
		}
	}

	public void setNumThreads(int numThreads) {
		try {
			getClassifier().setNumThreads(numThreads);
		} catch (IOException ignored) {
			// if it doesn't initialize, crash at a later point.
		}
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
