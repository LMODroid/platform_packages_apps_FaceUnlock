package com.libremobileos.facedetect;

import static android.os.Process.THREAD_PRIORITY_FOREGROUND;
import static com.libremobileos.facedetect.BuildConfig.DEBUG;

import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.lifecycle.ProcessCameraProvider;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.hardware.biometrics.face.V1_0.FaceAcquiredInfo;
import android.hardware.biometrics.face.V1_0.Feature;
import android.hardware.biometrics.face.V1_0.IBiometricsFaceClientCallback;
import android.hardware.biometrics.face.V1_0.OptionalBool;
import android.hardware.biometrics.face.V1_0.OptionalUint64;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.HwBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import android.util.Pair;
import android.util.Size;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.google.common.util.concurrent.ListenableFuture;
import com.libremobileos.yifan.face.FaceDataEncoder;
import com.libremobileos.yifan.face.FaceRecognizer;
import com.libremobileos.yifan.face.FaceStorageBackend;
import com.libremobileos.yifan.face.SharedPreferencesFaceStorageBackend;

import android.hardware.biometrics.face.V1_0.IBiometricsFace;
import android.hardware.biometrics.face.V1_0.IBiometricsFaceClientCallback;
import android.hardware.biometrics.face.V1_0.Status;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public class ServiceActivity extends AppCompatActivity {
	private String TAG = "FaceUnlockService";
	private long kDeviceId = 123; // Arbitrary value.
	private long kAuthenticatorId = 987; // Arbitrary value.

	private static final int MSG_CHALLENGE_TIMEOUT = 100;

	private IBiometricsFaceClientCallback mCallback;
	private FaceRecognizer faceRecognizer;
	private FaceHandler mWorkHandler;
	private Context mContext;
	private long mChallenge = 0;
	private int mChallengeCount = 0;
	private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
	protected final Size desiredInputSize = new Size(640, 480);
	protected int width, height;


	private class BiometricsFace extends IBiometricsFace.Stub {
		@Override
		public OptionalUint64 setCallback(IBiometricsFaceClientCallback clientCallback) throws RemoteException {
			if (DEBUG)
				Log.d(TAG, "setCallback");

			mCallback = clientCallback;
			OptionalUint64 ret = new OptionalUint64();
			ret.value = kDeviceId;
			ret.status = Status.OK;
			return ret;
		}

		@Override
		public int setActiveUser(int userId, String storePath) throws RemoteException {
			if (DEBUG)
				Log.d(TAG, "setActiveUser " + userId + " " + storePath);

			return Status.OK;
		}

		@Override
		public OptionalUint64 generateChallenge(int challengeTimeoutSec) throws RemoteException {
			if (DEBUG)
				Log.d(TAG, "generateChallenge + " + challengeTimeoutSec);

			if (mChallengeCount <= 0 || mChallenge == 0) {
				mChallenge = new Random().nextLong();
			}
			mChallengeCount += 1;
			mWorkHandler.removeMessages(MSG_CHALLENGE_TIMEOUT);
			mWorkHandler.sendEmptyMessageDelayed(MSG_CHALLENGE_TIMEOUT, challengeTimeoutSec * 1000);

			OptionalUint64 ret = new OptionalUint64();
			ret.value = mChallenge;
			ret.status = Status.OK;

			return ret;
		}

		@Override
		public int enroll(ArrayList<Byte> hat, int timeoutSec, ArrayList<Integer> disabledFeatures) throws RemoteException {
			if (DEBUG)
				Log.d(TAG, "enroll");

			mCallback.onEnrollResult(kDeviceId, 0, 0, 0);

			return Status.OK;
		}

		@Override
		public int revokeChallenge() throws RemoteException {
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
		public int setFeature(int feature, boolean enabled, ArrayList<Byte> hat, int faceId) throws RemoteException {
			if (DEBUG)
				Log.d(TAG, "setFeature " + feature + " " + enabled + " " + faceId);

			// We don't do that here;

			return Status.OK;
		}

		@Override
		public OptionalBool getFeature(int feature, int faceId) throws RemoteException {
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
		public OptionalUint64 getAuthenticatorId() throws RemoteException {
			if (DEBUG)
				Log.d(TAG, "getAuthenticatorId");

			OptionalUint64 ret = new OptionalUint64();
			ret.value = kAuthenticatorId;
			ret.status = Status.OK;
			return ret;
		}

		@Override
		public int cancel() throws RemoteException {
			// Not sure what to do here.
			return 0;
		}

		@Override
		public int enumerate() throws RemoteException {
			if (DEBUG)
				Log.d(TAG, "enumerate");

			mWorkHandler.post(() -> {
				try {
					ArrayList<Integer> faceIds = new ArrayList<>();
					RemoteFaceServiceClient.connect(mContext, faced -> {
						if (faced.isEnrolled()) {
							faceIds.add(0);
							Log.d(TAG, "enumerate face 0 added");
						}
					});

					if (mCallback != null) {
						mCallback.onEnumerate(kDeviceId, faceIds, 0);
					}
				} catch (RemoteException e) {
					e.printStackTrace();
				}
			});

			return Status.OK;
		}

		@Override
		public int remove(int faceId) throws RemoteException {
			if (DEBUG)
				Log.d(TAG, "remove " + faceId);

			mWorkHandler.post(() -> {
				RemoteFaceServiceClient.connect(mContext, faced -> {
					if (faceId == 0 && faced.isEnrolled()) {
						faced.unenroll();
						ArrayList<Integer> faceIds = new ArrayList<>();
						faceIds.add(faceId);
						try {
							if (mCallback != null)
								mCallback.onRemoved(kDeviceId, faceIds, 0);
						} catch (RemoteException e) {
							e.printStackTrace();
						}
					}
				});
			});
			return Status.OK;
		}

		@Override
		public int authenticate(long operationId) throws RemoteException {
			if (DEBUG)
				Log.d(TAG, "authenticate " + operationId);


			mWorkHandler.post(() -> {
				// CameraX boilerplate (create camera connection)
				cameraProviderFuture = ProcessCameraProvider.getInstance(mContext);
				cameraProviderFuture.addListener(() -> {
					try {
						ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
						bindCamera(cameraProvider);
					} catch (ExecutionException | InterruptedException e) {
						// No errors need to be handled for this Future.
						// This should never be reached.
					}
				}, getMainExecutor());
			});
			return Status.OK;
		}

		@Override
		public int userActivity() throws RemoteException {
			if (DEBUG)
				Log.d(TAG, "userActivity");

			return Status.OK;
		}

		@Override
		public int resetLockout(ArrayList<Byte> hat) throws RemoteException {
			if (DEBUG)
				Log.d(TAG, "resetLockout");

			return Status.OK;
		}
	}

	private void bindCamera(ProcessCameraProvider cameraProvider) {
		// Which camera to use
		int selectedCamera = CameraSelector.LENS_FACING_FRONT;
		CameraSelector cameraSelector = new CameraSelector.Builder()
				.requireLensFacing(selectedCamera)
				.build();

		// Cameras give us landscape images. If we are in portrait mode
		// (and want to process a portrait image), swap width/height to
		// make the image portrait.
		if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
			width = desiredInputSize.getHeight();
			height = desiredInputSize.getWidth();
		} else {
			width = desiredInputSize.getWidth();
			height = desiredInputSize.getHeight();
		}

		// Set up CameraX boilerplate and configure it to drop frames if we can't keep up
		ImageAnalysis imageAnalysis =
				new ImageAnalysis.Builder()
						.setTargetResolution(new Size(width, height))
						.setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
						.build();

		onSetCameraCallback(imageAnalysis);

		// Bind all objects together
		/* Camera camera = */ cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis);
	}

	@OptIn(markerClass = ExperimentalGetImage.class)
	protected void onSetCameraCallback(ImageAnalysis imageAnalysis) {
		imageAnalysis.setAnalyzer(getMainExecutor(), imageProxy -> {
			// Convert CameraX Image to Bitmap and process it
			// Return list of detected faces
			List<FaceRecognizer.Face> data = faceRecognizer.recognize(BitmapUtils.getBitmap(imageProxy));

			for (FaceRecognizer.Face face : data) {
				RectF boundingBox = new RectF(face.getLocation());

				// Camera is frontal so the image is flipped horizontally,
				// so flip it again.
				Matrix flip = new Matrix();
				flip.postScale(-1, 1, width / 2.0f, height / 2.0f);
				flip.mapRect(boundingBox);

				try {
					if (mCallback != null) {
						mCallback.onAcquired(kDeviceId, 0, FaceAcquiredInfo.GOOD, 0);
						// Do we have any match?
						if (face.isRecognized()) {
							mCallback.onAuthenticated(kDeviceId, 0, 0, new ArrayList<>());
							imageAnalysis.clearAnalyzer();
						}
					}
				} catch (RemoteException e) {
					e.printStackTrace();
				}
			}

			// Clean up
			imageProxy.close();
		});

		// Store registered Faces in Memory
		//faceStorage = new VolatileFaceStorageBackend();
		FaceStorageBackend faceStorage = new SharedPreferencesFaceStorageBackend(getSharedPreferences("faces", 0));

		// Create AI-based face detection
		faceRecognizer = FaceRecognizer.create(this,
				faceStorage, /* face data storage */
				0.6f, /* minimum confidence to consider object as face */
				width, /* bitmap width */
				height, /* bitmap height */
				0, /* CameraX rotates the image for us, so we chose to IGNORE sensorRotation altogether */
				0.7f, /* maximum distance (to saved face model, not from camera) to track face */
				1 /* minimum model count to track face */
		);
	}

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
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_service);
		TextView status = findViewById(R.id.status_textView);

		HandlerThread handlerThread = new HandlerThread(TAG, THREAD_PRIORITY_FOREGROUND);
		handlerThread.start();
		mWorkHandler = new FaceHandler(handlerThread.getLooper());
		mContext = this;

		BiometricsFace biometricsFace = new BiometricsFace();
		try {
			biometricsFace.registerAsService("default");
			status.setText("Service registered");
		} catch (RemoteException e) {
			e.printStackTrace();
		}
		//HwBinder.joinRpcThreadpool();
	}
}
