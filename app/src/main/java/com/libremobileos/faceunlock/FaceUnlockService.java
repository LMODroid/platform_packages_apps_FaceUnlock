package com.libremobileos.faceunlock;

import static android.os.Process.THREAD_PRIORITY_FOREGROUND;

import android.app.Service;
import android.content.om.IOverlayManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.hardware.biometrics.face.V1_0.FaceAcquiredInfo;
import android.hardware.biometrics.face.V1_0.Feature;
import android.hardware.biometrics.face.V1_0.IBiometricsFaceClientCallback;
import android.hardware.biometrics.face.V1_0.OptionalBool;
import android.hardware.biometrics.face.V1_0.OptionalUint64;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Base64;
import android.util.Log;
import android.util.Size;

import com.libremobileos.yifan.face.DirectoryFaceStorageBackend;
import com.libremobileos.yifan.face.FaceRecognizer;
import com.libremobileos.yifan.face.FaceStorageBackend;
import com.libremobileos.yifan.face.ImageUtils;

import android.hardware.biometrics.face.V1_0.IBiometricsFace;
import android.hardware.biometrics.face.V1_0.Status;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class FaceUnlockService extends Service {
	private final String TAG = "FaceUnlockService";
	private final long kDeviceId = 123; // Arbitrary value.
	private final int kFaceId = 100; // Arbitrary value.
	private final boolean DEBUG = false;
	private final String SETTINGS_OVERLAY_PACKAGE = "com.libremobileos.faceunlock.settings.overlay";

	private static final int MSG_CHALLENGE_TIMEOUT = 100;

	private IBiometricsFaceClientCallback mCallback;
	private FaceRecognizer faceRecognizer;
	private FaceHandler mWorkHandler;
	private Context mContext;
	private long mChallenge = 0;
	private int mChallengeCount = 0;
	private boolean computingDetection = false;
	private CameraService mCameraService;
	private int mUserId = 0;
	private String mStorePath = "/data/vendor_de/0/facedata";

	private class BiometricsFace extends IBiometricsFace.Stub {
		@Override
		public OptionalUint64 setCallback(IBiometricsFaceClientCallback clientCallback) {
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

			OptionalUint64 ret = new OptionalUint64();
			ret.value = kDeviceId;
			ret.status = Status.OK;
			return ret;
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
		public OptionalUint64 generateChallenge(int challengeTimeoutSec) {
			if (DEBUG)
				Log.d(TAG, "generateChallenge + " + challengeTimeoutSec);

			if (mChallengeCount <= 0 || mChallenge == 0) {
				mChallenge = new Random().nextLong();
			}
			mChallengeCount += 1;
			mWorkHandler.removeMessages(MSG_CHALLENGE_TIMEOUT);
			mWorkHandler.sendEmptyMessageDelayed(MSG_CHALLENGE_TIMEOUT, challengeTimeoutSec * 1000L);

			OptionalUint64 ret = new OptionalUint64();
			ret.value = mChallenge;
			ret.status = Status.OK;

			return ret;
		}

		@Override
		public int enroll(ArrayList<Byte> hat, int timeoutSec, ArrayList<Integer> disabledFeatures) {
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
		public int setFeature(int feature, boolean enabled, ArrayList<Byte> hat, int faceId) {
			if (DEBUG)
				Log.d(TAG, "setFeature " + feature + " " + enabled + " " + faceId);

			// We don't do that here;

			return Status.OK;
		}

		@Override
		public OptionalBool getFeature(int feature, int faceId) {
			if (DEBUG)
				Log.d(TAG, "getFeature " + feature + " " + faceId);

			OptionalBool ret = new OptionalBool();
			switch (feature) {
				case Feature.REQUIRE_ATTENTION:
					ret.value = false;
					ret.status = Status.OK;
					break;
				case Feature.REQUIRE_DIVERSITY:
					ret.value = true;
					ret.status = Status.OK;
					break;
				default:
					ret.value = false;
					ret.status = Status.ILLEGAL_ARGUMENT;
					break;
			}
			return ret;
		}

		@Override
		public OptionalUint64 getAuthenticatorId() {
			if (DEBUG)
				Log.d(TAG, "getAuthenticatorId");

			OptionalUint64 ret = new OptionalUint64();
			// Arbitrary value.
			ret.value = 987;
			ret.status = Status.OK;
			return ret;
		}

		@Override
		public int cancel() {
			// Not sure what to do here.
			mCameraService.closeCamera();
			mCameraService.stopBackgroundThread();
			return 0;
		}

		@Override
		public int enumerate() {
			if (DEBUG)
				Log.d(TAG, "enumerate");

			mWorkHandler.post(() -> {
				ArrayList<Integer> faceIds = new ArrayList<>();
				RemoteFaceServiceClient.connect(mStorePath, faced -> {
					if (faced.isEnrolled()) {
						faceIds.add(kFaceId);
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
					if ((faceId == kFaceId || faceId == 0) && faced.isEnrolled()) {
						faced.unenroll();
						ArrayList<Integer> faceIds = new ArrayList<>();
						faceIds.add(faceId);
						try {
							if (mCallback != null)
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

			mCameraService = new CameraService(mContext, faceCallback);
			mWorkHandler.post(() -> {
				mCameraService.startBackgroundThread();
				mCameraService.openCamera();
			});
			return Status.OK;
		}

		@Override
		public int userActivity() {
			if (DEBUG)
				Log.d(TAG, "userActivity");

			return Status.OK;
		}

		@Override
		public int resetLockout(ArrayList<Byte> hat) {
			if (DEBUG)
				Log.d(TAG, "resetLockout");

			return Status.OK;
		}
	}

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
					0.7f, /* maximum distance (to saved face model, not from camera) to track face */
					1, /* minimum model count to track face */
					false, false, 4
			);
		}

		@Override
		public void processImage (Size previewSize, Size rotatedSize, Bitmap rgbBitmap,int rotation)
		{
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
							ArrayList<Byte> hat = new ArrayList<>();
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
									for (byte b : Base64.decode(base64hat, Base64.URL_SAFE)) {
										hat.add(b);
									}
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

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		super.onStartCommand(intent, flags, startId);

		return START_STICKY;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		mContext = this;
		mUserId = 0; // TODO: Get real user id
		File facesDir = new File(mStorePath + "/faces");
		if (!facesDir.exists()) {
			facesDir.mkdir();
		}

		HandlerThread handlerThread = new HandlerThread(TAG, THREAD_PRIORITY_FOREGROUND);
		handlerThread.start();
		mWorkHandler = new FaceHandler(handlerThread.getLooper());

		BiometricsFace biometricsFace = new BiometricsFace();
		try {
			biometricsFace.registerAsService("lmodroid");
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}

	private final IFaceUnlockService.Stub binder = new IFaceUnlockService.Stub() {
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
		public String getStorePath() throws RemoteException {
			return mStorePath;
		}
	};

	@Override
	public IBinder onBind(Intent intent) {
		return binder;
	}
}
