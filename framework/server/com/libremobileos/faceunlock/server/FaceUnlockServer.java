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

import android.content.Context;
import android.content.om.IOverlayManager;
import android.graphics.Bitmap;
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
import com.libremobileos.yifan.face.FaceDataEncoder;
import com.libremobileos.yifan.face.FaceRecognizer;
import com.libremobileos.yifan.face.FaceStorageBackend;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Random;

public class FaceUnlockServer {
    public static final boolean DEBUG = false;
    private static final String TAG = "FaceUnlockServer";
    private static final String SETTINGS_OVERLAY_PACKAGE =
            "com.libremobileos.faceunlock.settings.overlay";
    private static final String FACE = "Face"; // used to store face in backend
    private static final int MSG_CHALLENGE_TIMEOUT = 100;
    private static final int DEFAULT_FEATURES =
            (int) Math.pow(2, Feature.REQUIRE_ATTENTION)
                    | (int) Math.pow(2, Feature.REQUIRE_DIVERSITY);

    private final long kDeviceId = 123; // Arbitrary value.
    private final int kFaceId = 100; // Arbitrary value.

    private IFaceHalServiceCallback mCallback;
    private FaceHandler mWorkHandler;
    private Context mContext;
    private long mChallenge = 0;
    private int mChallengeCount = 0;
    private boolean mComputingDetection = false;
    private CameraService mCameraService;
    private int mUserId = 0;
    private String mStorePath = "/data/vendor_de/0/facedata";
    private FaceStorageBackend faceStorage = null;
    private boolean mAuthenticating = false;
    private boolean isTimerTicking = false;
    private boolean lockedPermanently = false;
    private int features = DEFAULT_FEATURES;
    // TODO make this configurable? non-permanent seems broken AOSP side, but permanent is annoying
    private boolean shouldLockPermanent = false;
    // TODO make this configurable?
    private boolean lowMemoryMode = false;

    private final IBinder mFaceUnlockHalBinder =
            new IFaceHalService.Stub() {

                @Override
                public long getDeviceId() {
                    return kDeviceId;
                }

                @Override
                public void setCallback(IFaceHalServiceCallback clientCallback) {
                    if (DEBUG) Log.d(TAG, "setCallback");

                    mCallback = clientCallback;

                    mWorkHandler.post(
                            () -> {
                                IOverlayManager overlayManager =
                                        IOverlayManager.Stub.asInterface(
                                                ServiceManager.getService(
                                                        "overlay" /* Context.OVERLAY_SERVICE */));
                                try {
                                    overlayManager.setEnabledExclusiveInCategory(
                                            SETTINGS_OVERLAY_PACKAGE, -2 /* USER_CURRENT */);
                                } catch (Exception e) {
                                    Log.e(TAG, "Failed to enable settings overlay", e);
                                }
                            });
                }

                @Override
                public int setActiveUser(int userId, String storePath) {
                    if (DEBUG) Log.d(TAG, "setActiveUser " + userId + " " + storePath);

                    mUserId = userId;
                    mStorePath = storePath;
                    File facesDir = new File(mStorePath + "/faces");
                    if (!facesDir.exists()) {
                        facesDir.mkdir();
                    }

                    // Store registered Faces
                    // example for in-memory: FaceStorageBackend faceStorage = new
                    // VolatileFaceStorageBackend();
                    // example for shared preferences: FaceStorageBackend faceStorage = new
                    // SharedPreferencesFaceStorageBackend(getSharedPreferences("faces", 0));
                    faceStorage = new DirectoryFaceStorageBackend(new File(mStorePath + "/faces"));

                    try {
                        String str =
                                new String(Files.readAllBytes(Paths.get(mStorePath + "/settings")));
                        features = Integer.parseInt(str);
                    } catch (NumberFormatException | IOException e) {
                        features = DEFAULT_FEATURES;
                    }

                    return Status.OK;
                }

                @Override
                public long generateChallenge(int challengeTimeoutSec) {
                    if (DEBUG) Log.d(TAG, "generateChallenge + " + challengeTimeoutSec);

                    if (mChallengeCount <= 0 || mChallenge == 0) {
                        mChallenge = new Random().nextLong();
                    }
                    mChallengeCount += 1;
                    mWorkHandler.removeMessages(MSG_CHALLENGE_TIMEOUT);
                    mWorkHandler.sendEmptyMessageDelayed(
                            MSG_CHALLENGE_TIMEOUT, challengeTimeoutSec * 1000L);

                    return mChallenge;
                }

                @Override
                public int enroll(byte[] hat, int timeoutSec, int[] disabledFeatures) {
                    if (DEBUG) Log.d(TAG, "enroll");

                    return Status.OK;
                }

                @Override
                public int revokeChallenge() {
                    if (DEBUG) Log.d(TAG, "revokeChallenge");

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
                    if (DEBUG) Log.d(TAG, "setFeature " + feature + " " + enabled + " " + faceId);

                    switch (feature) {
                        case Feature.REQUIRE_ATTENTION:
                            // "Require looking at phone" in face settings
                        case Feature.REQUIRE_DIVERSITY:
                            // Accessibility toggle in enroll education
                            final int ft = (int) Math.pow(2, feature);
                            features = (features & ~ft) | (enabled ? ft : 0);
                            try {
                                Files.write(
                                        Paths.get(mStorePath + "/settings"),
                                        String.valueOf(features).getBytes());
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            return Status.OK;
                    }
                    Log.w(
                            TAG,
                            "setFeature unsupported feature"
                                    + feature
                                    + " "
                                    + enabled
                                    + " "
                                    + faceId);
                    return Status.OPERATION_NOT_SUPPORTED;
                }

                @Override
                public boolean getFeature(int feature, int faceId) {
                    if (DEBUG) Log.d(TAG, "getFeature " + feature + " " + faceId);
                    // Any feature in HIDL 1.0 needs to be enabled by default
                    switch (feature) {
                        case Feature.REQUIRE_ATTENTION:
                        case Feature.REQUIRE_DIVERSITY:
                            return (features & (int) Math.pow(2, feature)) > 0;
                    }
                    Log.w(TAG, "getFeature unsupported feature" + feature + " " + faceId);
                    return false;
                }

                @Override
                public long getAuthenticatorId() {
                    if (DEBUG) Log.d(TAG, "getAuthenticatorId");

                    return 987; // Arbitrary value.
                }

                @Override
                public int cancel() {
                    mWorkHandler.post(
                            () -> {
                                if (DEBUG) Log.d(TAG, "cancel");

                                // Not sure what to do here.
                                mCameraService.closeCamera();
                                try {
                                    mCallback.onError(kDeviceId, mUserId, FaceError.CANCELED, 0);
                                } catch (RemoteException e) {
                                    e.printStackTrace();
                                }
                                isTimerTicking = false;
                                lockOutTimer.cancel();
                                mAuthenticating = false;
                            });
                    return Status.OK;
                }

                @Override
                public int enumerate() {
                    if (DEBUG) Log.d(TAG, "enumerate");

                    mWorkHandler.post(
                            () -> {
                                int[] faceIds = new int[1];
                                if (faceStorage != null && faceStorage.getNames().contains(FACE)) {
                                    faceIds[0] = kFaceId;
                                    if (DEBUG) Log.d(TAG, "enumerate face added");
                                }
                                if (mCallback != null) {
                                    try {
                                        mCallback.onEnumerate(kDeviceId, faceIds, mUserId);
                                    } catch (RemoteException e) {
                                        e.printStackTrace();
                                    }
                                }
                            });

                    return Status.OK;
                }

                @Override
                public int remove(int faceId) {
                    if (DEBUG) Log.d(TAG, "remove " + faceId);

                    mWorkHandler.post(
                            () -> {
                                int[] faceIds = new int[1];
                                if ((faceId == kFaceId || faceId == 0)
                                        && faceStorage != null
                                        && faceStorage.getNames().contains(FACE)) {
                                    if (faceStorage.delete(FACE)) {
                                        File f = new File(mStorePath, ".FACE_HAT");
                                        if (f.exists()) {
                                            f.delete();
                                        }
                                    }
                                    faceIds[0] = kFaceId;
                                }
                                if (mCallback != null) {
                                    try {
                                        mCallback.onRemoved(kDeviceId, faceIds, mUserId);
                                    } catch (RemoteException e) {
                                        e.printStackTrace();
                                    }
                                }
                                features = DEFAULT_FEATURES;
                                try {
                                    Files.write(
                                            Paths.get(mStorePath + "/settings"),
                                            String.valueOf(features).getBytes());
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            });
                    return Status.OK;
                }

                @Override
                public int authenticate(long operationId) {
                    if (DEBUG) Log.d(TAG, "authenticate " + operationId);
                    if (mAuthenticating) Log.e(TAG, "authenticating twice");
                    if (lockedPermanently) {
                        try {
                            mCallback.onError(kDeviceId, mUserId, FaceError.LOCKOUT_PERMANENT, 0);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    } else {
                        try {
                            mCallback.onAcquired(kDeviceId, mUserId, FaceAcquiredInfo.START, 0);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                        mAuthenticating = true;
                        mWorkHandler.post(
                                () -> {
                                    mCameraService.openCamera();
                                    if (!isTimerTicking) {
                                        isTimerTicking = true;
                                        lockOutTimer.start();
                                    }
                                });
                    }
                    return Status.OK;
                }

                @Override
                public int userActivity() {
                    // Note: this method appears to be unused by AOSP
                    if (DEBUG) Log.d(TAG, "userActivity");

                    if (mAuthenticating && !mCameraService.isOpen()) {
                        mWorkHandler.post(
                                () -> {
                                    mCameraService.openCamera();
                                    if (!isTimerTicking) {
                                        isTimerTicking = true;
                                        lockOutTimer.start();
                                    }
                                });
                    }

                    return Status.OK;
                }

                @Override
                public int resetLockout(byte[] hat) {
                    if (DEBUG) Log.d(TAG, "resetLockout");

                    lockedPermanently = false;

                    return Status.OK;
                }
            };

    final CameraService.CameraCallback faceCallback =
            new CameraService.CameraCallback() {
                private FaceRecognizer mFaceRecognizer = null;
                private String lastStore = null;
                private Size lastSize = null;
                private Integer lastRotation = null;
                private Boolean lastSecure = null;

                @Override
                public void setupFaceRecognizer(final Size bitmapSize, int rotation) {
                    final boolean secureMode =
                            (features & (int) Math.pow(2, Feature.REQUIRE_ATTENTION)) > 0;
                    if (mFaceRecognizer != null
                            && (lastSize == null
                                    || lastRotation == null
                                    || lastSecure == null
                                    || lastStore == null
                                    || !lastSize.equals(bitmapSize)
                                    || !lastRotation.equals(rotation)
                                    || !lastSecure.equals(secureMode)
                                    || !lastStore.equals(mStorePath))) {
                        if (DEBUG) {
                            Log.d(TAG, "nuked face recognizer: ");
                            Log.d(TAG, "lastSize == null: " + (lastSize == null));
                            Log.d(TAG, "lastRotation == null: " + (lastRotation == null));
                            Log.d(TAG, "lastSecure == null: " + (lastSecure == null));
                            Log.d(TAG, "lastStore == null: " + (lastStore == null));
                            Log.d(TAG, "!lastSize.equals(bitmapSize): " + (!lastSize.equals(bitmapSize)));
                            Log.d(TAG, "!lastRotation.equals(rotation): " + (!lastRotation.equals(rotation)));
                            Log.d(TAG, "!lastSecure.equals(secureMode): " + (!lastSecure.equals(secureMode)));
                            Log.d(TAG, "!lastStore.equals(mStorePath): " + (!lastStore.equals(mStorePath)));
                        }
                        mFaceRecognizer = null;
                    }
                    if (faceStorage == null) {
                        Log.w(TAG, "tried to unlock with null storage");
                        return;
                    }
                    // Create AI-based face detection
                    if (mFaceRecognizer == null) {
                        // Note: we create FaceRecognizer on WorkHandler and initialize Camera on
                        // cam thread at the same time
                        mWorkHandler.post(
                                () -> {
                                    if (DEBUG)
                                        Log.d(
                                                TAG,
                                                "creating FaceRecognizer, secureMode="
                                                        + secureMode);
                                    mFaceRecognizer =
                                            FaceRecognizer.create(
                                                    mContext,
                                                    faceStorage, /* face data storage */
                                                    0.6f, /* minimum confidence to consider object as face */
                                                    bitmapSize.getWidth(), /* bitmap width */
                                                    bitmapSize.getHeight(), /* bitmap height */
                                                    rotation,
                                                    secureMode
                                                            ? 0.45f
                                                            : 0.7f, /* maximum distance (to saved face model, not from camera) to track face */
                                                    1, /* minimum model count to track face */
                                                    false,
                                                    false,
                                                    4);
                                    if (DEBUG) Log.d(TAG, "done creating FaceRecognizer async");
                                });
                        if (!lowMemoryMode) {
                            lastSize = bitmapSize;
                            lastRotation = rotation;
                            lastSecure = secureMode;
                            lastStore = mStorePath;
                        }
                    }
                }

                @Override
                public void processImage(
                        Size previewSize, Size rotatedSize, Bitmap rgbBitmap, int rotation) {
                    if (DEBUG) Log.d(TAG, "processImage");
                    if (mComputingDetection) {
                        Log.e(TAG, "mComputingDetection true in non-reentrant method?");
                        mCameraService.readyForNextImage();
                        return;
                    }
                    if (mFaceRecognizer == null) {
                        if (DEBUG) Log.d(TAG, "still creating mFaceRecognizer");
                        mCameraService.readyForNextImage();
                        return;
                    }
                    mComputingDetection = true;
                    final List<FaceRecognizer.Face> data = mFaceRecognizer.recognize(rgbBitmap);

                    if (data != null && mCallback != null) {
                        try {
                            if (data.size() < 1) {
                                if (DEBUG) Log.d(TAG, "Found no faces");
                                mCallback.onAcquired(
                                        kDeviceId, mUserId, FaceAcquiredInfo.NOT_DETECTED, 0);
                            } else if (data.size() > 1) {
                                if (DEBUG)
                                    Log.d(TAG, "Found " + data.size() + " faces, expected 1");
                                mCallback.onAcquired(
                                        kDeviceId, mUserId, FaceAcquiredInfo.FACE_OBSCURED, 0);
                            } else {
                                if (DEBUG) Log.d(TAG, "Found 1 face");
                                FaceRecognizer.Face face = data.get(0);
                                if (face.getBrightnessHint() < 0) {
                                    if (DEBUG)
                                        Log.d(TAG, "Skipping face due to bad light conditions");
                                    mCallback.onAcquired(
                                            kDeviceId, mUserId, FaceAcquiredInfo.INSUFFICIENT, 0);
                                } else {
                                    mCallback.onAcquired(
                                            kDeviceId, mUserId, FaceAcquiredInfo.GOOD, 0);
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
                                                ByteArrayOutputStream result =
                                                        new ByteArrayOutputStream();
                                                byte[] buffer = new byte[1024];
                                                for (int length;
                                                        (length = inputStream.read(buffer))
                                                                != -1; ) {
                                                    result.write(buffer, 0, length);
                                                }
                                                // ignore the warning, api 33-only stuff right there
                                                // :D
                                                String base64hat =
                                                        result.toString(
                                                                StandardCharsets.UTF_8.name());
                                                byte[] hat =
                                                        Base64.decode(base64hat, Base64.URL_SAFE);
                                                isTimerTicking = false;
                                                lockOutTimer.cancel();
                                                mAuthenticating = false;
                                                mCallback.onAuthenticated(
                                                        kDeviceId, kFaceId, mUserId, hat);
                                                mCameraService.closeCamera();
                                                if (DEBUG)
                                                    Log.d(
                                                            TAG,
                                                            "authenticated successfully! distance ="
                                                                + " "
                                                                    + face.getDistance());
                                            }
                                        } catch (IOException e) {
                                            Log.e("Authentication", Log.getStackTraceString(e));
                                        }
                                    } else {
                                        if (DEBUG) Log.d(TAG, "Skipping face because no match");
                                    }
                                }
                            }
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    } else {
                        if (DEBUG)
                            Log.d(
                                    TAG,
                                    "data == null? "
                                            + (data == null)
                                            + " mCallback == null? "
                                            + (mCallback == null));
                    }

                    mComputingDetection = false;
                    mCameraService.readyForNextImage();
                }

                @Override
                public void stop() {
                    // Avoid memory leak.
                    if (lowMemoryMode) {
                        mFaceRecognizer = null;
                        lastStore = null;
                        lastSize = null;
                        lastRotation = null;
                        lastSecure = null;
                    }
                }
            };

    final CountDownTimer lockOutTimer =
            new CountDownTimer(30000, 1000) {
                public void onTick(long millisUntilFinished) {
                    if (DEBUG) Log.d(TAG, "lockOutTimer: " + millisUntilFinished / 1000);
                    isTimerTicking = true;
                }

                public void onFinish() {
                    if (DEBUG) Log.d(TAG, "timer finished");
                    isTimerTicking = false;
                    mWorkHandler.post(() -> mCameraService.closeCamera());
                    if (shouldLockPermanent) {
                        lockedPermanently = true;
                        mAuthenticating = false;
                        try {
                            mCallback.onError(kDeviceId, mUserId, FaceError.LOCKOUT_PERMANENT, 0);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    } else {
                        try {
                            mCallback.onError(kDeviceId, mUserId, FaceError.TIMEOUT, 0);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
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

    public FaceUnlockServer(
            Context context, Looper serviceThreadLooper, BinderPublishCallback bpc) {
        mContext = context;
        mUserId = 0;
        File facesDir = new File(mStorePath + "/faces");
        if (!facesDir.exists()) {
            facesDir.mkdir();
        }
        mWorkHandler = new FaceHandler(serviceThreadLooper);
        mCameraService = new CameraService(mContext, faceCallback);
        mCameraService.startBackgroundThread();

        bpc.publishBinderService(SERVICE_NAME, mFaceUnlockManagerBinder);
        bpc.publishBinderService("faceunlockhal", mFaceUnlockHalBinder);
    }

    private final IBinder mFaceUnlockManagerBinder =
            new IFaceUnlockManager.Stub() {

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
                    boolean result = false;
                    if (faceStorage != null) {
                        result =
                                faceStorage.register(
                                        FACE, FaceDataEncoder.decode(encodedFaces), true);
                    } else {
                        Log.w(TAG, "tried to enroll with null storage");
                    }
                    if (result) {
                        File f = new File(mStorePath, ".FACE_HAT");
                        try {
                            if (f.exists()) {
                                f.delete();
                            } else {
                                if (!f.createNewFile())
                                    throw new IOException("f.createNewFile() failed");
                            }
                            OutputStreamWriter hatOSW =
                                    new OutputStreamWriter(new FileOutputStream(f));
                            hatOSW.write(new String(Base64.encode(token, Base64.URL_SAFE)));
                            hatOSW.close();
                        } catch (IOException e) {
                            Log.e("RemoteFaceServiceClient", "Failed to write HAT", e);
                            result = false;
                        }
                    }
                    try {
                        if (!result) {
                            mCallback.onError(kDeviceId, mUserId, FaceError.UNABLE_TO_PROCESS, 0);
                        } else {
                            mCallback.onEnrollResult(kDeviceId, kFaceId, mUserId, 0);
                        }
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            };

    public static interface BinderPublishCallback {
        public void publishBinderService(String name, IBinder binder);
    }
}
