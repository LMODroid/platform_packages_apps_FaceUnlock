/*
 * Copyright (C) 2023 LibreMobileOS Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.libremobileos.faceunlock.server;

import android.annotation.NonNull;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
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
import android.os.Handler;
import android.os.HandlerThread;
import android.text.TextUtils;
import android.util.Log;
import android.util.Size;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import com.libremobileos.yifan.face.ImageUtils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CameraService implements ImageReader.OnImageAvailableListener {

    private static final String TAG = "Camera2Service";
    private static final boolean DEBUG = FaceUnlockServer.DEBUG;

    /**
     * The camera preview size will be chosen to be the smallest frame by pixel size capable of
     * containing a DESIRED_SIZE x DESIRED_SIZE square.
     */
    private static final int MINIMUM_PREVIEW_SIZE = 320;

    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;
    private CameraDevice cameraDevice;
    private ImageReader previewReader;
    private byte[][] yuvBytes = null;
    private int[] rgbBytes = null;
    private boolean isProcessingFrame = false;
    private int yRowStride;
    private Runnable postInferenceCallback;
    private Runnable imageConverter;
    private Bitmap rgbFrameBitmap = null;
    private Size previewSize = null;
    private Size rotatedSize = null;
    private final Context mContext;
    private final CameraCallback mCallback;

    protected final Size desiredInputSize = new Size(640, 480);
    // The calculated actual processing width & height
    protected int imageOrientation;

    public interface CameraCallback {
        void setupFaceRecognizer(Size bitmapSize, int rotation);

        void processImage(Size previewSize, Size rotatedSize, Bitmap rgbBitmap, int rotation);

        void stop();
    }

    public CameraService(Context context, CameraCallback callback) {
        mContext = context;
        mCallback = callback;
    }

    private final CameraDevice.StateCallback stateCallback =
            new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    // This is called when the camera is open
                    if (DEBUG) Log.d(TAG, "onOpened");
                    cameraDevice = camera;
                    createCameraPreview();
                }

                @Override
                public void onDisconnected(CameraDevice camera) {
                    if (DEBUG) Log.d(TAG, "onDisconnected");
                    cameraDevice.close();
                }

                @Override
                public void onError(CameraDevice camera, int error) {
                    if (DEBUG) Log.d(TAG, "onError");
                    closeCamera();
                }
            };

    public void startBackgroundThread() {
        if (DEBUG) Log.d(TAG, "startBackgroundThread");
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    public void stopBackgroundThread() {
        if (DEBUG) Log.d(TAG, "stopBackgroundThread");
        closeCamera();

        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mBackgroundThread = null;
        mBackgroundHandler = null;
    }

    private void createCameraPreview() {
        if (DEBUG) Log.d(TAG, "createCameraPreview");
        try {
            previewReader =
                    ImageReader.newInstance(
                            previewSize.getWidth(),
                            previewSize.getHeight(),
                            ImageFormat.YUV_420_888,
                            2);

            previewReader.setOnImageAvailableListener(this, mBackgroundHandler);
            final CaptureRequest.Builder captureRequestBuilder =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(previewReader.getSurface());

            cameraDevice.createCaptureSession(
                    Collections.singletonList(previewReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(
                                @NonNull CameraCaptureSession cameraCaptureSession) {
                            // The camera is already closed
                            if (null == cameraDevice) {
                                return;
                            }
                            // When the session is ready, we start displaying the preview.
                            try {
                                // Auto focus should be continuous for camera preview.
                                captureRequestBuilder.set(
                                        CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                // Flash is automatically enabled when necessary.
                                captureRequestBuilder.set(
                                        CaptureRequest.CONTROL_AE_MODE,
                                        CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

                                // Finally, we start displaying the camera preview.
                                cameraCaptureSession.setRepeatingRequest(
                                        captureRequestBuilder.build(), null, mBackgroundHandler);
                            } catch (final CameraAccessException e) {
                                Log.e(TAG, "Exception!", e);
                            }
                        }

                        @Override
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession cameraCaptureSession) {}
                    },
                    null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /** Compares two {@code Size}s based on their areas. */
    private static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(final Size lhs, final Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum(
                    (long) lhs.getWidth() * lhs.getHeight()
                            - (long) rhs.getWidth() * rhs.getHeight());
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
        Log.i(TAG, "Valid preview sizes: [" + TextUtils.join(", ", bigEnough) + "]");
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

    public void openCamera() {
        if (DEBUG) Log.d(TAG, "openCamera");
        if (null != cameraDevice) {
            Log.e(TAG, "camera already open");
            return;
        }
        mBackgroundHandler.post(
                () -> {
                    CameraManager manager =
                            (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
                    try {
                        String cameraId = manager.getCameraIdList()[0];
                        for (String id : manager.getCameraIdList()) {
                            CameraCharacteristics characteristics =
                                    manager.getCameraCharacteristics(id);
                            if (characteristics.get(CameraCharacteristics.LENS_FACING)
                                    == CameraCharacteristics.LENS_FACING_FRONT) {
                                cameraId = id;
                                break;
                            }
                        }
                        CameraCharacteristics characteristics =
                                manager.getCameraCharacteristics(cameraId);
                        StreamConfigurationMap map =
                                characteristics.get(
                                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                        Integer sensorOrientation =
                                characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

                        assert map != null;

                        // Danger, W.R.! Attempting to use too large a preview size could  exceed
                        // the camera
                        // bus' bandwidth limitation, resulting in gorgeous previews but the storage
                        // of
                        // garbage capture data.
                        previewSize =
                                chooseOptimalSize(
                                        map.getOutputSizes(SurfaceTexture.class),
                                        desiredInputSize.getWidth(),
                                        desiredInputSize.getHeight());
                        rotatedSize = previewSize;

                        imageOrientation = sensorOrientation + getScreenOrientation();
                        rgbFrameBitmap =
                                Bitmap.createBitmap(
                                        previewSize.getWidth(),
                                        previewSize.getHeight(),
                                        Bitmap.Config.ARGB_8888);

                        if (imageOrientation % 180 != 0) {
                            rotatedSize = new Size(previewSize.getHeight(), previewSize.getWidth());
                        }
                        if (DEBUG) Log.d(TAG, "setting up face recognizer");
                        mCallback.setupFaceRecognizer(rotatedSize, imageOrientation);
                        if (DEBUG) Log.d(TAG, "done setting up face recognizer, opening camera");
                        manager.openCamera(cameraId, stateCallback, mBackgroundHandler);
                    } catch (CameraAccessException | SecurityException e) {
                        e.printStackTrace();
                    }
                });
    }

    public boolean isOpen() {
        return cameraDevice != null;
    }

    public void closeCamera() {
        if (DEBUG) Log.d(TAG, "closeCamera");
        previewSize = null;
        if (null != previewReader) {
            previewReader.close();
            previewReader = null;
        }
        if (null != cameraDevice) {
            cameraDevice.close();
            cameraDevice = null;
            mBackgroundHandler.post(() -> mCallback.stop());
        }
        // sets postInferenceCallback to null and frees reference to image
        readyForNextImage();
        rotatedSize = null;
        imageOrientation = 0;
        rgbFrameBitmap = null;
        imageConverter = null;
        rgbBytes = null;
        yuvBytes = null;
    }

    private void fillBytes(final Image.Plane[] planes, final byte[][] yuvBytes) {
        // Because of the variable row stride it's not possible to know in
        // advance the actual necessary dimensions of the yuv planes.
        for (int i = 0; i < planes.length; ++i) {
            final ByteBuffer buffer = planes[i].getBuffer();
            if (yuvBytes[i] == null) {
                yuvBytes[i] = new byte[buffer.capacity()];
            }
            buffer.get(yuvBytes[i]);
        }
    }

    private int[] getRgbBytes() {
        imageConverter.run();
        return rgbBytes;
    }

    private int getScreenOrientation() {
        Display display =
                ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE))
                        .getDefaultDisplay();
        switch (display.getRotation()) {
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

    public void readyForNextImage() {
        if (postInferenceCallback != null) {
            postInferenceCallback.run();
            postInferenceCallback = null;
        }
    }

    @Override
    public void onImageAvailable(ImageReader reader) {
        // Trace.beginSection("imageAvailable");
        try {
            if (DEBUG) Log.d(TAG, "onImageAvailable");
            final Image image = reader.acquireLatestImage();

            if (image == null) {
                if (DEBUG) Log.d(TAG, "image is null");
                return;
            }

            if (previewSize == null) {
                // Camera is currently being destroyed.
                if (DEBUG) Log.d(TAG, "previewSize is null");
                image.close();
                return;
            }

            int previewWidth = previewSize.getWidth();
            int previewHeight = previewSize.getHeight();

            if (rgbBytes == null) {
                rgbBytes = new int[previewWidth * previewHeight];
            }
            if (yuvBytes == null) {
                yuvBytes = new byte[3][];
            }

            if (isProcessingFrame) {
                if (DEBUG) Log.d(TAG, "still processing image, skipping this one");
                image.close();
                return;
            }
            isProcessingFrame = true;
            if (DEBUG) Log.d(TAG, "start processing image " + image.hashCode());
            final Image.Plane[] planes = image.getPlanes();
            fillBytes(planes, yuvBytes);
            yRowStride = planes[0].getRowStride();
            final int uvRowStride = planes[1].getRowStride();
            final int uvPixelStride = planes[1].getPixelStride();

            imageConverter =
                    () ->
                            ImageUtils.convertYUV420ToARGB8888(
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
                        if (DEBUG) Log.d(TAG, "done processing image " + image.hashCode());
                        image.close();
                        isProcessingFrame = false;
                    };

            rgbFrameBitmap.setPixels(
                    getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);

            mCallback.processImage(previewSize, rotatedSize, rgbFrameBitmap, imageOrientation);
        } catch (final Exception e) {
            Log.e(TAG, "Exception!", e);
        } finally {
            // Trace.endSection();
        }
    }
}
