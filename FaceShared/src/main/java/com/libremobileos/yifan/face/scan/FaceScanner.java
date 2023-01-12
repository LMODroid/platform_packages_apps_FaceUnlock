package com.libremobileos.yifan.face.scan;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.libremobileos.yifan.face.shared.ImageUtils;
import com.libremobileos.yifan.face.shared.SimilarityClassifier;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FaceScanner {
	private final AssetManager am;
	private SimilarityClassifier classifier;
	// MobileFaceNet
	private static final int TF_OD_API_INPUT_SIZE = 112;
	private static final boolean TF_OD_API_IS_QUANTIZED = false;
	private static final String TF_OD_API_MODEL_FILE = "mobile_face_net.tflite";
	private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/labelmap.txt";
	// Minimum detection confidence to track a detection.
	public static final float MAXIMUM_DISTANCE_TF_OD_API = 0.8f;
	private static final boolean MAINTAIN_ASPECT = false;

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

	public static class InputImageProcessor {
		private final int sensorOrientation;

		public InputImageProcessor(int sensorOrientation) {
			this.sensorOrientation = sensorOrientation;
		}

		public InputImage process(Bitmap input) {
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

		// utility because everyone is lazy :D
		public InputImage process(Bitmap fullInput, Bitmap input, RectF inputBB) {
			//TODO cleanup
			Matrix transform = createTransform(
					fullInput.getWidth(),
					fullInput.getHeight(),
					input.getWidth(),
					input.getHeight(),
					sensorOrientation);
			RectF faceBB = new RectF(inputBB);
			transform.mapRect(faceBB);
			if (faceBB.left < 0 || faceBB.top < 0 || faceBB.bottom < 0 ||
					faceBB.right < 0 || (faceBB.left + faceBB.width()) > input.getWidth()
					|| (faceBB.top + faceBB.height()) > input.getHeight()) return null;
			return process(Bitmap.createBitmap(input,
					(int) faceBB.left,
					(int) faceBB.top,
					(int) faceBB.width(),
					(int) faceBB.height()));
		}

		public Bitmap transformRawImageToPortrait(Bitmap input) {
			//TODO replace this mess with ImageUtils transform
			int targetW, targetH;
			if (sensorOrientation == 90 || sensorOrientation == 270) {
				targetH = input.getWidth();
				targetW = input.getHeight();
			} else {
				targetW = input.getWidth();
				targetH = input.getHeight();
			}
			Bitmap portraitBmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888);
			Matrix transform = createTransform(
					input.getWidth(),
					input.getHeight(),
					targetW,
					targetH,
					sensorOrientation);
			final Canvas cv = new Canvas(portraitBmp);
			cv.drawBitmap(input, transform, null);
			return portraitBmp;
		}
	}

	private static Matrix createTransform( //should be removed while reworking transformRawImageToPortrait
			final int srcWidth,
			final int srcHeight,
			final int dstWidth,
			final int dstHeight,
			final int applyRotation) {
		Matrix matrix = new Matrix();
		if (applyRotation != 0) {
			matrix.postTranslate(-srcWidth / 2.0f, -srcHeight / 2.0f);
			matrix.postRotate(applyRotation);
			matrix.postTranslate(dstWidth / 2.0f, dstHeight / 2.0f);
		}
		return matrix;
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
		 * A sortable score for how good the recognition is relative to others. Lower should be better.
		 */
		private final Float distance;

		/** Optional location within the source image for the location of the recognized object. */
		private final RectF location;

		/** Optional, source bitmap */
		private final Bitmap crop;

		/** Optional, raw AI output */
		private final float[][] extra;

		public Face(
				final String id, final String title, final Float distance, final RectF location, final Bitmap crop, final float[][] extra) {
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

		public float[][] getExtra() {
			return extra;
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

		public float compare(float[][] other) {
			final float[] emb = normalizeFloat(extra[0]);
			final float[] knownEmb = normalizeFloat(other[0]);
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

	public static FaceScanner create(Context context) {
		return new FaceScanner(context.getAssets());
	}

	public FaceScanner(AssetManager am) {
		this.am = am;
	}

	private SimilarityClassifier getClassifier() throws IOException {
		if (classifier == null) {
			classifier = SimilarityClassifier.create(am,
					TF_OD_API_MODEL_FILE,
					TF_OD_API_LABELS_FILE,
					TF_OD_API_INPUT_SIZE,
					TF_OD_API_IS_QUANTIZED
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

	public Face detectFace(InputImage input, boolean add) {
		try {
			List<SimilarityClassifier.Recognition> results = getClassifier().recognizeImage(input.getProcessedImage());
			SimilarityClassifier.Recognition result = results.get(0);
			return new Face(result.getId(), result.getTitle(), result.getDistance(), null, add ? input.getUserDisplayableImage() : null, result.getExtra());
		} catch (IOException e) {
			Log.e("FaceDetector", Log.getStackTraceString(e));
			return null;
		}
	}
}
