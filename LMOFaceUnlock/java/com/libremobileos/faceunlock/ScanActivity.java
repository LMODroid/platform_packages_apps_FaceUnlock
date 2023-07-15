/*
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

package com.libremobileos.faceunlock;

import static com.libremobileos.faceunlock.client.FaceUnlockManager.SERVICE_NAME;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Pair;
import android.util.Size;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.libremobileos.yifan.face.FaceDataEncoder;
import com.libremobileos.yifan.face.FaceDetector;
import com.libremobileos.yifan.face.FaceFinder;
import com.libremobileos.yifan.face.FaceScanner;

import com.libremobileos.faceunlock.client.IFaceUnlockManager;

import java.util.ArrayList;
import java.util.List;
import android.hardware.face.FaceManager;

public class ScanActivity extends CameraActivity {

	private Handler mBackgroundHandler;
	private HandlerThread mBackgroundThread;
	// AI-based detector
	private FaceFinder faceRecognizer;
	// Simple view allowing us to draw a circle over the Preview
	private CircleOverlayView overlayView;
	// If we are waiting for a face to be added to knownFaces
	private long lastAdd;
	private final List<FaceScanner.Face> faces = new ArrayList<>();
	private TextView subText;

	private IFaceUnlockManager faceUnlockManager;
	protected byte[] mToken;
	protected int mUserId;
	protected int mSensorId;
	protected long mChallenge;
	protected boolean mFromSettingsSummary;

	protected CancellationSignal mEnrollmentCancel;

	public static final String EXTRA_KEY_CHALLENGE_TOKEN = "hw_auth_token";
	public static final String EXTRA_FROM_SETTINGS_SUMMARY = "from_settings_summary";
	public static final String EXTRA_KEY_SENSOR_ID = "sensor_id";
	public static final String EXTRA_KEY_CHALLENGE = "challenge";
	public static final String EXTRA_USER_ID = "user_id";
	public static final String EXTRA_KEY_REQUIRE_VISION = "accessibility_vision";
	public static final String EXTRA_KEY_REQUIRE_DIVERSITY = "accessibility_diversity";

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		// Initialize basic views
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_enroll);
		FrameLayout f = findViewById(R.id.frameLayout);
		getLayoutInflater().inflate(R.layout.enroll_main, f);
		connectToCam(f.findViewById(R.id.viewFinder));
		overlayView = f.findViewById(R.id.overlay);
		subText = f.findViewById(R.id.textView);
		subText.setText(R.string.scan_face_now);
		findViewById(R.id.button).setOnClickListener(v -> finish());

		IBinder b = ServiceManager.getService(SERVICE_NAME);
		faceUnlockManager = IFaceUnlockManager.Stub.asInterface(b);

		mToken = getIntent().getByteArrayExtra(EXTRA_KEY_CHALLENGE_TOKEN);
		mChallenge = getIntent().getLongExtra(EXTRA_KEY_CHALLENGE, -1L);
		mSensorId = getIntent().getIntExtra(EXTRA_KEY_SENSOR_ID, -1);
		mFromSettingsSummary = getIntent().getBooleanExtra(EXTRA_FROM_SETTINGS_SUMMARY, false);
		mUserId = getIntent().getIntExtra(EXTRA_USER_ID, 0);

		mBackgroundThread = new HandlerThread("AI Background");
		mBackgroundThread.start();
		mBackgroundHandler = new Handler(mBackgroundThread.getLooper());

		if (mToken != null) {
			ArrayList<Integer> disabledFeatures = new ArrayList<>();
			if (!getIntent().getBooleanExtra(EXTRA_KEY_REQUIRE_DIVERSITY, true)) {
				disabledFeatures.add(FaceManager.FEATURE_REQUIRE_REQUIRE_DIVERSITY);
			}
			if (!getIntent().getBooleanExtra(EXTRA_KEY_REQUIRE_VISION, true)) {
				disabledFeatures.add(FaceManager.FEATURE_REQUIRE_ATTENTION);
			}
			final int[] disabledFeaturesArr = new int[disabledFeatures.size()];
			for (int i = 0; i < disabledFeatures.size(); i++) {
				disabledFeaturesArr[i] = disabledFeatures.get(i);
			}
			mEnrollmentCancel = new CancellationSignal();
			FaceManager faceManager = getFaceManagerOrNull(this);
			assert faceManager != null;
			faceManager.enroll(mUserId, mToken, mEnrollmentCancel, mEnrollmentCallback, disabledFeaturesArr);
		}
	}

	@SuppressLint("WrongConstant")
	public static FaceManager getFaceManagerOrNull(Context context) {
		if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_FACE)) {
			return (FaceManager) context.getSystemService("face");
		} else {
			return null;
		}
	}

	private final FaceManager.EnrollmentCallback mEnrollmentCallback
			= new FaceManager.EnrollmentCallback() {

		@Override
		public void onEnrollmentProgress(int remaining) {
		}

		@Override
		public void onEnrollmentHelp(int helpMsgId, CharSequence helpString) {
		}

		@Override
		public void onEnrollmentError(int errMsgId, CharSequence errString) {
		}
	};

	@Override
	protected void onStop() {
		mBackgroundThread.quitSafely();
		try {
			mBackgroundThread.join();
			mBackgroundThread = null;
			mBackgroundHandler = null;
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		super.onStop();
	}

	@Override
	protected void setupFaceRecognizer(final Size bitmapSize) {
		// Create AI-based face detection
		faceRecognizer = FaceFinder.create(this,
				0.6f, /* minimum confidence to consider object as face */
				bitmapSize.getWidth(), /* bitmap width */
				bitmapSize.getHeight(), /* bitmap height */
				imageOrientation,
				false, false, 4
		);
	}

	@Override
	protected void processImage() {
		if (faces.size() == 10) {
			readyForNextImage();
			return;
		}

		// Return list of detected faces
		List<Pair<FaceDetector.Face, FaceScanner.Face>> data = faceRecognizer.process(getBitmap(), false);

        runOnUiThread(() -> {
            if (data.size() > 1) {
                if (lastAdd == -1) { // last frame had two faces too
                    subText.setText(R.string.found_2_faces);
                }
                lastAdd = -1;
                readyForNextImage();
                return;
            } else if (lastAdd == -1) {
                lastAdd = System.currentTimeMillis();
            }
            if (data.size() == 0) {
                if (lastAdd == -2) { // last frame had 0 faces too
                    subText.setText(R.string.cant_find_face);
                }
                lastAdd = -2;
                readyForNextImage();
                return;
            } else if (lastAdd == -2) {
                lastAdd = System.currentTimeMillis();
            }

            Pair<FaceDetector.Face, FaceScanner.Face> face = data.get(0);

            // Do we want to add a new face?
            if (lastAdd + 1000 < System.currentTimeMillis()) {
                lastAdd = System.currentTimeMillis();
                if (face.second.getBrightnessHint() < 1) {
                    subText.setText(R.string.cant_scan_face);
                    readyForNextImage();
                    return;
                } else {
                    subText.setText(R.string.scan_face_now);
                }
                faces.add(face.second);
                overlayView.setPercentage(faces.size() * 10);
            }

            if (faces.size() == 10) {
                String encodedFaces = FaceDataEncoder.encode(faces.stream().map(FaceScanner.Face::getExtra).toArray(float[][]::new));
                if (mToken != null) {
                    try {
                        faceUnlockManager.finishEnroll(encodedFaces, mToken);
                        final Intent intent = new Intent();
                        ComponentName componentName = ComponentName.unflattenFromString("com.android.settings/com.android.settings.biometrics.face.FaceEnrollFinish");
                        intent.setComponent(componentName);
                        intent.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT
                                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        intent.putExtra(EXTRA_KEY_CHALLENGE_TOKEN, mToken);
                        intent.putExtra(EXTRA_KEY_SENSOR_ID, mSensorId);
                        intent.putExtra(EXTRA_KEY_CHALLENGE, mChallenge);
                        intent.putExtra(EXTRA_FROM_SETTINGS_SUMMARY, mFromSettingsSummary);
                        if (mUserId != 0) {
                            intent.putExtra(EXTRA_USER_ID, mUserId);
                        }
                        startActivity(intent);

                        finish();
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            } else {
                if (mToken != null) {
                    try {
                        faceUnlockManager.enrollResult(10 - faces.size());
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            }

            // Clean up
            readyForNextImage();
        });
	}
}
