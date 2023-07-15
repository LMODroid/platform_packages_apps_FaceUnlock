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

package com.libremobileos.faceunlock.client;

import android.hardware.biometrics.face.V1_0.IBiometricsFace;
import android.os.ServiceManager;
import android.util.Log;

public final class FaceUnlockHalManager {

    public static final String SERVICE_NAME = "faceunlockhal";
    private static final String TAG = "FaceUnlockHalManager";

    public static IBiometricsFace getIBiometricsFace() {
        IFaceHalService faceHalService =
                IFaceHalService.Stub.asInterface(ServiceManager.getService(SERVICE_NAME));
        if (faceHalService == null) {
            Log.e(TAG, "Unable to get IFaceHalService.");
            return null;
        }
        return new FakeBiometricsFace(faceHalService);
    }
}
