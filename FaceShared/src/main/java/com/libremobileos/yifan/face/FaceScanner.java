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

public class FaceScanner {
	// Asset manager to load TFLite model
	private final AssetManager am;
	// TFLite Model API
	private SimilarityClassifier classifier;
	// Optional settings
	private final boolean hwAccleration, enhancedHwAccleration;
	private final int numThreads;
	// MobileFaceNet model parameters
	private static final int TF_OD_API_INPUT_SIZE = 112;
	private static final boolean TF_OD_API_IS_QUANTIZED = false;
	private static final String TF_OD_API_MODEL_FILE = "mobile_face_net.tflite";
	private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/mobile_face_net.txt";
	// Minimum detection confidence to track a detection.
	public static final float MAXIMUM_DISTANCE_TF_OD_API = 0.8f;
	// Maintain aspect ratio or squish image?
	private static final boolean MAINTAIN_ASPECT = false;

	// Wrapper around Bitmap to avoid user passing unprocessed data
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

	// Processes Bitmaps to compatible format
	public static class InputImageProcessor {
		private final int sensorOrientation;
		private final Bitmap portraitBmp;
		private final Matrix transform;

		// If the class gets instantiated, we enter an special mode of operation for detecting multiple faces on one large Bitmap.
		public InputImageProcessor(Bitmap rawImage, int sensorOrientation) {
			this.sensorOrientation = sensorOrientation;
			//TODO replace this mess with ImageUtils transform
			int targetW, targetH;
			if (sensorOrientation == 90 || sensorOrientation == 270) {
				targetH = rawImage.getWidth();
				targetW = rawImage.getHeight();
			} else {
				targetW = rawImage.getWidth();
				targetH = rawImage.getHeight();
			}
			Bitmap portraitBmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888);
			transform = ImageUtils.createTransform(
					rawImage.getWidth(),
					rawImage.getHeight(),
					targetW,
					targetH,
					sensorOrientation);
			final Canvas cv = new Canvas(portraitBmp);
			cv.drawBitmap(rawImage, transform, null);
			this.portraitBmp = portraitBmp;
		}

		// In the normal mode of operation, we take a Bitmap with the cropped face and convert it.
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

		public InputImage process(Bitmap input) {
			return process(input, sensorOrientation);
		}

		// In the special operation mode, we crop the image manually.
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
		/**
		 * A unique identifier for what has been recognized. Specific to the class, not the instance of
		 * the object.
		 */
		private String id;

		/** Display name for the recognition. */
		private String title;

		/**
		 * A sortable score for how good the recognition is relative to others. Lower should be better.
		 */
		private Float distance;

		/** Optional location within the source image for the location of the recognized object. */
		private RectF location;

		/** Optional, source bitmap */
		private final Bitmap crop;

		/** Optional, raw AI output */
		private final float[] extra;

		public Face(
				final String id, final String title, final Float distance, final RectF location, final Bitmap crop, final float[] extra) {
			this.id = id;
			this.title = title;
			this.distance = distance;
			this.location = location;
			this.crop = crop;
			this.extra = extra;
		}

		public String getId() {
			return id;
		}

		public String getTitle() {
			return title;
		}

		public Float getDistance() {
			return distance;
		}

		public RectF getLocation() {
			return new RectF(location);
		}

		public Bitmap getCrop() {
			if (crop == null) return null;
			return Bitmap.createBitmap(crop);
		}

		public float[] getExtra() {
			return extra;
		}

		// add metadata from FaceDetector
		/* package-private */ void addData(String id, RectF location) {
			this.id = id;
			this.location = location;
		}

		// add metadata obtainable after face recognition
		public void addRecognitionData(String title, float distance) {
			this.title = title;
			this.distance = distance;
		}

		// if this Face has been recognized
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

	public static FaceScanner create(Context context, boolean hwAccleration, boolean enhancedHwAccleration, int numThreads) {
		return new FaceScanner(context.getAssets(), hwAccleration, enhancedHwAccleration, numThreads);
	}

	public static FaceScanner create(Context context) {
		return create(context, false, true, 4);
	}

	private FaceScanner(AssetManager am, boolean hwAccleration, boolean enhancedHwAccleration, int numThreads) {
		this.am = am;
		this.hwAccleration = hwAccleration;
		this.enhancedHwAccleration = enhancedHwAccleration;
		this.numThreads = numThreads;
	}

	private SimilarityClassifier getClassifier() throws IOException {
		if (classifier == null) {
			classifier = SimilarityClassifier.create(am,
					TF_OD_API_MODEL_FILE,
					TF_OD_API_LABELS_FILE,
					TF_OD_API_INPUT_SIZE,
					TF_OD_API_IS_QUANTIZED,
					hwAccleration,
					enhancedHwAccleration,
					numThreads
			);
		}
		return classifier;
	}

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
