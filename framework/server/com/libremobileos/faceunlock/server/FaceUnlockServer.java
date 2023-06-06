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

import static com.libremobileos.faceunlock.client.FaceUnlockManager.SERVICE_NAME;

import android.content.om.IOverlayManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.hardware.biometrics.face.V1_0.FaceAcquiredInfo;
import android.hardware.biometrics.face.V1_0.FaceError;
import android.hardware.biometrics.face.V1_0.Feature;
import android.hardware.biometrics.face.V1_0.Status;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Base64;
import android.util.Log;
import android.util.Size;

import com.libremobileos.faceunlock.client.IFaceHalService;
import com.libremobileos.faceunlock.client.IFaceHalServiceCallback;
import com.libremobileos.faceunlock.client.IFaceUnlockManager;

import com.libremobileos.yifan.face.DirectoryFaceStorageBackend;
import com.libremobileos.yifan.face.FaceRecognizer;
import com.libremobileos.yifan.face.FaceStorageBackend;
import com.libremobileos.yifan.face.ImageUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;

public class FaceUnlockServer {
	private final String TAG = "FaceUnlockServer";
	private final long kDeviceId = 123; // Arbitrary value.
	private final int kFaceId = 100; // Arbitrary value.
	private final boolean DEBUG = false;
	private final String SETTINGS_OVERLAY_PACKAGE = "com.libremobileos.faceunlock.settings.overlay";

	private static final int MSG_CHALLENGE_TIMEOUT = 100;

	private IFaceHalServiceCallback mCallback;
	private FaceRecognizer faceRecognizer;
	private FaceHandler mWorkHandler;
	private Context mContext;
	private long mChallenge = 0;
	private int mChallengeCount = 0;
	private boolean computingDetection = false;
	private CameraService mCameraService;
	private int mUserId = 0;
	private String mStorePath = "/data/vendor_de/0/facedata";
	private boolean isLocked = false;
	private boolean isTimerTicking = false;

	private final IBinder mFaceUnlockHalBinder = new IFaceHalService.Stub() {

		@Override
		public long getDeviceId() {
			return kDeviceId;
		}

		@Override
		public void setCallback(IFaceHalServiceCallback clientCallback) {
			if (DEBUG)
				Log.d(TAG, "setCallback");

			mCallback = clientCallback;

			mWorkHandler.post(() -> {
				IOverlayManager overlayManager = IOverlayManager.Stub.asInterface(
						ServiceManager.getService("overlay" /* Context.OVERLAY_SERVICE */));
				try {
					overlayManager.setEnabledExclusiveInCategory(SETTINGS_OVERLAY_PACKAGE, -2 /* USER_CURRENT */);
				} catch (Exception e) {
					Log.e(TAG, "Failed to enable settings overlay", e);
				}
			});
		}

		@Override
		public int setActiveUser(int userId, String storePath) {
			if (DEBUG)
				Log.d(TAG, "setActiveUser " + userId + " " + storePath);

			mUserId = userId;
			mStorePath = storePath;
			File facesDir = new File(mStorePath + "/faces");
			if (!facesDir.exists()) {
				facesDir.mkdir();
			}

			return Status.OK;
		}

		@Override
		public long generateChallenge(int challengeTimeoutSec) {
			if (DEBUG)
				Log.d(TAG, "generateChallenge + " + challengeTimeoutSec);

			if (mChallengeCount <= 0 || mChallenge == 0) {
				mChallenge = new Random().nextLong();
			}
			mChallengeCount += 1;
			mWorkHandler.removeMessages(MSG_CHALLENGE_TIMEOUT);
			mWorkHandler.sendEmptyMessageDelayed(MSG_CHALLENGE_TIMEOUT, challengeTimeoutSec * 1000L);

			return mChallenge;
		}

		@Override
		public int enroll(byte[] hat, int timeoutSec, int[] disabledFeatures) {
			if (DEBUG)
				Log.d(TAG, "enroll");

			return Status.OK;
		}

		@Override
		public int revokeChallenge() {
			if (DEBUG)
				Log.d(TAG, "revokeChallenge");

			mChallengeCount -= 1;
			if (mChallengeCount <= 0 && mChallenge != 0) {
				mChallenge = 0;
				mChallengeCount = 0;
				mWorkHandler.removeMessages(MSG_CHALLENGE_TIMEOUT);
			}
			return Status.OK;
		}

		@Override
		public int setFeature(int feature, boolean enabled, byte[] hat, int faceId) {
			if (DEBUG)
				Log.d(TAG, "setFeature " + feature + " " + enabled + " " + faceId);

			// We don't do that here;

			return Status.OK;
		}

		@Override
		public boolean getFeature(int feature, int faceId) {
			if (DEBUG)
				Log.d(TAG, "getFeature " + feature + " " + faceId);

			switch (feature) {
				case Feature.REQUIRE_ATTENTION:
					return false;
				case Feature.REQUIRE_DIVERSITY:
					return true;
			}
			return false;
		}

		@Override
		public long getAuthenticatorId() {
			if (DEBUG)
				Log.d(TAG, "getAuthenticatorId");

			return 987; // Arbitrary value.
		}

		@Override
		public int cancel() {
			mWorkHandler.post(() -> {
				if (DEBUG)
					Log.d(TAG, "cancel");

				// Not sure what to do here.
				if (mCameraService != null) {
					mCameraService.closeCamera();
					mCameraService.stopBackgroundThread();
				}
				try {
					mCallback.onError(kDeviceId, mUserId, FaceError.CANCELED, 0);
				} catch (RemoteException e) {
					e.printStackTrace();
				}
				isTimerTicking = false;
				lockOutTimer.cancel();
			});
			return Status.OK;
		}

		@Override
		public int enumerate() {
			if (DEBUG)
				Log.d(TAG, "enumerate");

			mWorkHandler.post(() -> {
				RemoteFaceServiceClient.connect(mStorePath, faced -> {
					int[] faceIds = new int[1];
					if (faced.isEnrolled()) {
						faceIds[0] = kFaceId;
						Log.d(TAG, "enumerate face added");
					}
					if (mCallback != null) {
						try {
							mCallback.onEnumerate(kDeviceId, faceIds, mUserId);
						} catch (RemoteException e) {
							e.printStackTrace();
						}
					}
				});
			});

			return Status.OK;
		}

		@Override
		public int remove(int faceId) {
			if (DEBUG)
				Log.d(TAG, "remove " + faceId);

			mWorkHandler.post(() -> {
				RemoteFaceServiceClient.connect(mStorePath, faced -> {
					int[] faceIds = new int[1];
					if ((faceId == kFaceId || faceId == 0) && faced.isEnrolled()) {
						faced.unenroll();
						faceIds[0] = kFaceId;
					}
					if (mCallback != null) {
						try {
							mCallback.onRemoved(kDeviceId, faceIds, mUserId);
						} catch (RemoteException e) {
							e.printStackTrace();
						}
					}
				});
			});
			return Status.OK;
		}

		@Override
		public int authenticate(long operationId) {
			if (DEBUG)
				Log.d(TAG, "authenticate " + operationId);

			if (!isLocked) {
				mWorkHandler.post(() -> {
					mCameraService = new CameraService(mContext, faceCallback);
					mCameraService.startBackgroundThread();
					mCameraService.openCamera();
				});
				if (!isTimerTicking) {
					isTimerTicking = true;
					lockOutTimer.start();
				}
			}
			return Status.OK;
		}

		@Override
		public int userActivity() {
			if (DEBUG)
				Log.d(TAG, "userActivity");

			return Status.OK;
		}

		@Override
		public int resetLockout(byte[] hat) {
			if (DEBUG)
				Log.d(TAG, "resetLockout");

			isLocked = false;
			isTimerTicking = false;
			lockOutTimer.cancel();

			return Status.OK;
		}
	};

	CameraService.CameraCallback faceCallback = new CameraService.CameraCallback() {
		@Override
		public void setupFaceRecognizer ( final Size bitmapSize, int rotation) {
			// Store registered Faces
			// example for in-memory: FaceStorageBackend faceStorage = new VolatileFaceStorageBackend();
			// example for shared preferences: FaceStorageBackend faceStorage = new SharedPreferencesFaceStorageBackend(getSharedPreferences("faces", 0));
			FaceStorageBackend faceStorage = new DirectoryFaceStorageBackend(new File(mStorePath + "/faces"));

			// Create AI-based face detection
			faceRecognizer = FaceRecognizer.create(mContext,
					faceStorage, /* face data storage */
					0.6f, /* minimum confidence to consider object as face */
					bitmapSize.getWidth(), /* bitmap width */
					bitmapSize.getHeight(), /* bitmap height */
					rotation,
					0.38f, /* maximum distance (to saved face model, not from camera) to track face */
					1, /* minimum model count to track face */
					false, false, 4
			);
		}

		@Override
		public void processImage (Size previewSize, Size rotatedSize, Bitmap rgbBitmap,int rotation)
		{
			if (isLocked) {
				mCameraService.readyForNextImage();
				return;
			}
			// No mutex needed as this method is not reentrant.
			if (computingDetection) {
				mCameraService.readyForNextImage();
				return;
			}
			computingDetection = true;
			List<FaceRecognizer.Face> data = faceRecognizer.recognize(rgbBitmap);
			computingDetection = false;

			// Camera is frontal so the image is flipped horizontally,
			// so flip it again (and rotate Rect to match preview rotation)
			Matrix flip = ImageUtils.getTransformationMatrix(previewSize.getWidth(), previewSize.getHeight(), rotatedSize.getWidth(), rotatedSize.getHeight(), rotation, false);
			flip.preScale(1, -1, previewSize.getWidth() / 2f, previewSize.getHeight() / 2f);

			for (FaceRecognizer.Face face : data) {
				try {
					if (mCallback != null) {
						mCallback.onAcquired(kDeviceId, mUserId, FaceAcquiredInfo.GOOD, 0);
						// Do we have any match?
						if (face.isRecognized()) {
							File f = new File(mStorePath, ".FACE_HAT");
							try {
								if (!f.exists()) {
									throw new IOException("f.exists() == false");
								}
								if (!f.canRead()) {
									throw new IOException("f.canRead() == false");
								}
								try (InputStream inputStream = new FileInputStream(f)) {
									// https://stackoverflow.com/a/35446009
									ByteArrayOutputStream result = new ByteArrayOutputStream();
									byte[] buffer = new byte[1024];
									for (int length; (length = inputStream.read(buffer)) != -1; ) {
										result.write(buffer, 0, length);
									}
									// ignore the warning, api 33-only stuff right there :D
									String base64hat = result.toString(StandardCharsets.UTF_8.name());
									byte[] hat = Base64.decode(base64hat, Base64.URL_SAFE);
									lockOutTimer.cancel();
									mCallback.onAuthenticated(kDeviceId, kFaceId, mUserId, hat);
								}
							} catch (IOException e) {
								Log.e("Authentication", Log.getStackTraceString(e));
							}
							mCameraService.closeCamera();
							mCameraService.stopBackgroundThread();
						}
					}
				} catch (RemoteException e) {
					e.printStackTrace();
				}
			}

			mCameraService.readyForNextImage();
		}
	};

	CountDownTimer lockOutTimer = new CountDownTimer(30000, 1000) {
		public void onTick(long millisUntilFinished) {
			Log.d(TAG, "lockOutTimer: " + millisUntilFinished / 1000);
			isTimerTicking = true;
		}

		public void onFinish() {
			isLocked = true;
			isTimerTicking = false;
			try {
				mCallback.onError(kDeviceId, mUserId, FaceError.TIMEOUT, 0);
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}
	};

	private class FaceHandler extends Handler {
		public FaceHandler(Looper looper) {
			super(looper);
		}

		@Override
		public void handleMessage(Message message) {
			if (message.what == MSG_CHALLENGE_TIMEOUT) {
				mChallenge = 0;
				mChallengeCount = 0;
			}
		}
	}

	public FaceUnlockServer(Context context, Looper serviceThreadLooper, BinderPublishCallback bpc) {
		mContext = context;
		mUserId = 0;
		File facesDir = new File(mStorePath + "/faces");
		if (!facesDir.exists()) {
			facesDir.mkdir();
		}
		mWorkHandler = new FaceHandler(serviceThreadLooper);

		bpc.publishBinderService(SERVICE_NAME, mFaceUnlockManagerBinder);
		bpc.publishBinderService("faceunlockhal", mFaceUnlockHalBinder);
	}

	private final IBinder mFaceUnlockManagerBinder = new IFaceUnlockManager.Stub() {

		@Override
		public void enrollResult(int remaining) throws RemoteException {
			if (mCallback != null) {
				mCallback.onEnrollResult(kDeviceId, kFaceId, mUserId, remaining);
			}
		}

		@Override
		public void error(int error) throws RemoteException {
			if (mCallback != null) {
				mCallback.onError(kDeviceId, mUserId, error, 0);
			}
		}

		@Override
		public void finishEnroll(String encodedFaces, byte[] token) {
			RemoteFaceServiceClient.connect(mStorePath, faced -> {
				try {
					if (!faced.enroll(encodedFaces, token)) {
						mCallback.onError(kDeviceId, mUserId, FaceError.UNABLE_TO_PROCESS, 0);
					} else {
						mCallback.onEnrollResult(kDeviceId, kFaceId, mUserId, 0);
					}
				} catch (RemoteException e) {
					e.printStackTrace();
				}
			});
		}
	};

	public static interface BinderPublishCallback {
		public void publishBinderService(String name, IBinder binder);
	}
}
