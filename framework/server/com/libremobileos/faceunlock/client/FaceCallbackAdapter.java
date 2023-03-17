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

import android.hardware.biometrics.face.V1_0.IBiometricsFaceClientCallback;
import android.os.RemoteException;

import java.util.ArrayList;

public class FaceCallbackAdapter extends IFaceHalServiceCallback.Stub {
    private final IBiometricsFaceClientCallback mCallback;

    /* package-private */ FaceCallbackAdapter(IBiometricsFaceClientCallback callback) {
        this.mCallback = callback;
    }

    @Override
    public void onEnrollResult(long deviceId, int faceId, int userId, int remaining) throws RemoteException {
        mCallback.onEnrollResult(deviceId, faceId, userId, remaining);
    }

    @Override
    public void onAuthenticated(long deviceId, int faceId, int userId, byte[] token) throws RemoteException {
        mCallback.onAuthenticated(deviceId, faceId, userId, convertArr(token));
    }

    @Override
    public void onAcquired(long deviceId, int userId, int acquiredInfo, int vendorCode) throws RemoteException {
        mCallback.onAcquired(deviceId, userId, acquiredInfo, vendorCode);
    }

    @Override
    public void onError(long deviceId, int userId, int error, int vendorCode) throws RemoteException {
        mCallback.onError(deviceId, userId, error, vendorCode);
    }

    @Override
    public void onRemoved(long deviceId, int[] faceIds, int userId) throws RemoteException {
        mCallback.onRemoved(deviceId, convertArr(faceIds), userId);
    }

    @Override
    public void onEnumerate(long deviceId, int[] faceIds, int userId) throws RemoteException {
        mCallback.onEnumerate(deviceId, convertArr(faceIds), userId);
    }

    @Override
    public void onLockoutChanged(long duration) throws RemoteException {
        mCallback.onLockoutChanged(duration);
    }

    private static ArrayList<Integer> convertArr(int[] ar) {
        ArrayList<Integer> ret = new ArrayList<>();;
        for (int b : ar) {
            ret.add(b);
        }
        return ret;
    }

    private static ArrayList<Byte> convertArr(byte[] ar) {
        ArrayList<Byte> ret = new ArrayList<>();;
        for (byte b : ar) {
            ret.add(b);
        }
        return ret;
    }
}
