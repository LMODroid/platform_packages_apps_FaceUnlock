package com.libremobileos.facedetect;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Trace;
import android.text.TextUtils;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.libremobileos.yifan.face.AutoFitTextureView;
import com.libremobileos.yifan.face.ImageUtils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public abstract class CameraActivity extends AppCompatActivity implements ImageReader.OnImageAvailableListener {

	private static final String TAG = "Camera2Activity";

	/**
	 * The camera preview size will be chosen to be the smallest frame by pixel size capable of
	 * containing a DESIRED_SIZE x DESIRED_SIZE square.
	 */
	private static final int MINIMUM_PREVIEW_SIZE = 320;

	private AutoFitTextureView previewView;
	private Handler mBackgroundHandler;
	private HandlerThread mBackgroundThread;
	private CameraDevice cameraDevice;
	private CameraCaptureSession cameraCaptureSessions;
	private CaptureRequest captureRequest;
	private CaptureRequest.Builder captureRequestBuilder;
	private ImageReader previewReader;
	private final byte[][] yuvBytes = new byte[3][];
	private int[] rgbBytes = null;
	private boolean isProcessingFrame = false;
	private int yRowStride;
	private Runnable postInferenceCallback;
	private Runnable imageConverter;
	private Bitmap rgbFrameBitmap = null;
	private int imageOrientation;
	private Size previewSize;

	protected final Size desiredInputSize = new Size(640, 480);
	// The calculated actual processing width & height
	protected int width, height;

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	protected void connectToCam(AutoFitTextureView pv) {
		previewView = pv;

		previewView.setSurfaceTextureListener(textureListener);
	}

	private final TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
		@Override
		public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
			//open your camera here
			openCamera();
		}

		@Override
		public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
			// Transform you image captured size according to the surface width and height
			configureTransform(width, height);
		}

		@Override
		public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
			return false;
		}

		@Override
		public void onSurfaceTextureUpdated(SurfaceTexture surface) {
		}
	};
	private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
		@Override
		public void onOpened(CameraDevice camera) {
			//This is called when the camera is open
			Log.e(TAG, "onOpened");
			cameraDevice = camera;
			createCameraPreview();
		}

		@Override
		public void onDisconnected(CameraDevice camera) {
			cameraDevice.close();
		}

		@Override
		public void onError(CameraDevice camera, int error) {
			cameraDevice.close();
			cameraDevice = null;
		}
	};

	private void startBackgroundThread() {
		mBackgroundThread = new HandlerThread("Camera Background");
		mBackgroundThread.start();
		mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
	}

	private void stopBackgroundThread() {
		mBackgroundThread.quitSafely();
		try {
			mBackgroundThread.join();
			mBackgroundThread = null;
			mBackgroundHandler = null;
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	private void createCameraPreview() {
		try {
			SurfaceTexture texture = previewView.getSurfaceTexture();
			assert texture != null;
			texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
			Surface surface = new Surface(texture);
			captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
			captureRequestBuilder.addTarget(surface);

			previewReader =
					ImageReader.newInstance(
							previewSize.getWidth(), previewSize.getHeight(), ImageFormat.YUV_420_888, 2);

			previewReader.setOnImageAvailableListener(this, mBackgroundHandler);
			captureRequestBuilder.addTarget(previewReader.getSurface());

			cameraDevice.createCaptureSession(Arrays.asList(surface, previewReader.getSurface()),
					new CameraCaptureSession.StateCallback() {
				@Override
				public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
					//The camera is already closed
					if (null == cameraDevice) {
						return;
					}
					// When the session is ready, we start displaying the preview.
					cameraCaptureSessions = cameraCaptureSession;
					try {
						// Auto focus should be continuous for camera preview.
						captureRequestBuilder.set(
								CaptureRequest.CONTROL_AF_MODE,
								CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
						// Flash is automatically enabled when necessary.
						captureRequestBuilder.set(
								CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

						// Finally, we start displaying the camera preview.
						captureRequest = captureRequestBuilder.build();
						cameraCaptureSessions.setRepeatingRequest(
								captureRequest, null, mBackgroundHandler);
					} catch (final CameraAccessException e) {
						Log.e(TAG, "Exception!", e);
					}
//					updatePreview();
				}

				@Override
				public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
				}
			}, null);
		} catch (CameraAccessException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Configures the necessary {@link Matrix} transformation to `mTextureView`. This method should be
	 * called after the camera preview size is determined in setUpCameraOutputs and also the size of
	 * `mTextureView` is fixed.
	 *
	 * @param viewWidth The width of `mTextureView`
	 * @param viewHeight The height of `mTextureView`
	 */
	private void configureTransform(final int viewWidth, final int viewHeight) {
		if (null == previewView || null == previewSize) {
			return;
		}
		final int rotation = getWindowManager().getDefaultDisplay().getRotation();
		final Matrix matrix = new Matrix();
		final RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
		final RectF bufferRect = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
		final float centerX = viewRect.centerX();
		final float centerY = viewRect.centerY();
		if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
			bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
			matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
			final float scale =
					Math.max(
							(float) viewHeight / previewSize.getHeight(),
							(float) viewWidth / previewSize.getWidth());
			matrix.postScale(scale, scale, centerX, centerY);
			matrix.postRotate(90 * (rotation - 2), centerX, centerY);
		} else if (Surface.ROTATION_180 == rotation) {
			matrix.postRotate(180, centerX, centerY);
		}
		previewView.setTransform(matrix);
	}

	/** Compares two {@code Size}s based on their areas. */
	private static class CompareSizesByArea implements Comparator<Size> {
		@Override
		public int compare(final Size lhs, final Size rhs) {
			// We cast here to ensure the multiplications won't overflow
			return Long.signum(
					(long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
		}
	}

	/**
	 * Given {@code choices} of {@code Size}s supported by a camera, chooses the smallest one whose
	 * width and height are at least as large as the minimum of both, or an exact match if possible.
	 *
	 * @param choices The list of sizes that the camera supports for the intended output class
	 * @param width The minimum desired width
	 * @param height The minimum desired height
	 * @return The optimal {@code Size}, or an arbitrary one if none were big enough
	 */
	private static Size chooseOptimalSize(final Size[] choices, final int width, final int height) {
		final int minSize = Math.max(Math.min(width, height), MINIMUM_PREVIEW_SIZE);
		final Size desiredSize = new Size(width, height);

		// Collect the supported resolutions that are at least as big as the preview Surface
		boolean exactSizeFound = false;
		final List<Size> bigEnough = new ArrayList<>();
		final List<Size> tooSmall = new ArrayList<>();
		for (final Size option : choices) {
			if (option.equals(desiredSize)) {
				// Set the size but don't return yet so that remaining sizes will still be logged.
				exactSizeFound = true;
			}

			if (option.getHeight() >= minSize && option.getWidth() >= minSize) {
				bigEnough.add(option);
			} else {
				tooSmall.add(option);
			}
		}

		Log.i(TAG, "Desired size: " + desiredSize + ", min size: " + minSize + "x" + minSize);
		Log.i(TAG,"Valid preview sizes: [" + TextUtils.join(", ", bigEnough) + "]");
		Log.i(TAG, "Rejected preview sizes: [" + TextUtils.join(", ", tooSmall) + "]");

		if (exactSizeFound) {
			Log.i(TAG, "Exact size match found.");
			return desiredSize;
		}

		// Pick the smallest of those, assuming we found any
		if (bigEnough.size() > 0) {
			final Size chosenSize = Collections.min(bigEnough, new CompareSizesByArea());
			Log.i(TAG, "Chosen size: " + chosenSize.getWidth() + "x" + chosenSize.getHeight());
			return chosenSize;
		} else {
			Log.e(TAG, "Couldn't find any suitable preview size");
			return choices[0];
		}
	}

	private void openCamera() {
		CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
		Log.e(TAG, "is camera open");
		try {
			String cameraId = manager.getCameraIdList()[0];
			for (String id : manager.getCameraIdList()) {
				CameraCharacteristics characteristics = manager.getCameraCharacteristics(id);
				if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
					cameraId = id;
					break;
				}
			}
			CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
			StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
			Integer sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

			assert map != null;

			// Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
			// bus' bandwidth limitation, resulting in gorgeous previews but the storage of
			// garbage capture data.
			previewSize =
					chooseOptimalSize(
							map.getOutputSizes(SurfaceTexture.class),
							desiredInputSize.getWidth(), desiredInputSize.getHeight());
			width = previewSize.getWidth();
			height = previewSize.getHeight();
			configureTransform(width, height);

			final int orientation = getResources().getConfiguration().orientation;
			if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
				previewView.setAspectRatio(previewSize.getWidth(), previewSize.getHeight());
			} else {
				previewView.setAspectRatio(previewSize.getHeight(), previewSize.getWidth());
			}

			imageOrientation = sensorOrientation + getScreenOrientation();
			rgbFrameBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

			setupFaceRecognizer(new Size(width, height), imageOrientation);

			// Add permission for camera and let user grant the permission
			if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
			//	ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION);
				return;
			}
			manager.openCamera(cameraId, stateCallback, null);
		} catch (CameraAccessException e) {
			e.printStackTrace();
		}
		Log.e(TAG, "openCamera X");
	}

	protected int getImageRotation() {
		return imageOrientation;
	}

	private void closeCamera() {
		if (null != cameraDevice) {
			cameraDevice.close();
			cameraDevice = null;
		}
		if (null != previewReader) {
			previewReader.close();
			previewReader = null;
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		Log.e(TAG, "onResume");
		startBackgroundThread();
	}

	@Override
	protected void onPause() {
		Log.e(TAG, "onPause");
		closeCamera();
		stopBackgroundThread();
		super.onPause();
	}

	private void fillBytes(final Image.Plane[] planes, final byte[][] yuvBytes) {
		// Because of the variable row stride it's not possible to know in
		// advance the actual necessary dimensions of the yuv planes.
		for (int i = 0; i < planes.length; ++i) {
			final ByteBuffer buffer = planes[i].getBuffer();
			if (yuvBytes[i] == null) {
				Log.d(TAG, "Initializing buffer " + i + " at size " + buffer.capacity());
				yuvBytes[i] = new byte[buffer.capacity()];
			}
			buffer.get(yuvBytes[i]);
		}
	}

	private int[] getRgbBytes() {
		imageConverter.run();
		return rgbBytes;
	}

	protected Bitmap getBitmap() {
		return rgbFrameBitmap;
	}

	private int getScreenOrientation() {
		switch (getWindowManager().getDefaultDisplay().getRotation()) {
			case Surface.ROTATION_270:
				return 270;
			case Surface.ROTATION_180:
				return 180;
			case Surface.ROTATION_90:
				return 90;
			default:
				return 0;
		}
	}

	protected void readyForNextImage() {
		if (postInferenceCallback != null) {
			postInferenceCallback.run();
		}
	}

	@Override
	public void onImageAvailable(ImageReader reader) {
		int previewWidth = previewSize.getWidth();
		int previewHeight = previewSize.getHeight();

		if (rgbBytes == null) {
			rgbBytes = new int[previewWidth * previewHeight];
		}
		try {
			final Image image = reader.acquireLatestImage();

			if (image == null) {
				return;
			}

			if (isProcessingFrame) {
				image.close();
				return;
			}
			isProcessingFrame = true;
			Trace.beginSection("imageAvailable");
			final Image.Plane[] planes = image.getPlanes();
			fillBytes(planes, yuvBytes);
			yRowStride = planes[0].getRowStride();
			final int uvRowStride = planes[1].getRowStride();
			final int uvPixelStride = planes[1].getPixelStride();

			imageConverter =
					() -> ImageUtils.convertYUV420ToARGB8888(
							yuvBytes[0],
							yuvBytes[1],
							yuvBytes[2],
							previewWidth,
							previewHeight,
							yRowStride,
							uvRowStride,
							uvPixelStride,
							rgbBytes);

			postInferenceCallback =
					() -> {
						image.close();
						isProcessingFrame = false;
					};

			rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);

			runOnUiThread(this::processImage);
		} catch (final Exception e) {
			Log.e(TAG, "Exception!", e);
			Trace.endSection();
			return;
		}
		Trace.endSection();
	}

	protected abstract void setupFaceRecognizer(final Size bitmapSize, final int imageRotation);

	protected abstract void processImage();

}
