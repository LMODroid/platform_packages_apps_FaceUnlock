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
import android.hardware.biometrics.face.V1_0.IBiometricsFaceClientCallback;
import android.hardware.biometrics.face.V1_0.OptionalBool;
import android.hardware.biometrics.face.V1_0.OptionalUint64;
import android.hardware.biometrics.face.V1_0.Status;
import android.hidl.base.V1_0.DebugInfo;
import android.os.HwParcel;
import android.os.IHwBinder;
import android.os.IHwInterface;
import android.os.NativeHandle;
import android.os.RemoteException;

import java.util.ArrayList;

public class FakeBiometricsFace implements IBiometricsFace {

    private static final String TAG = "FakeBiometricsFace";

    private final IFaceHalService mFaceHalService;

    /* package-private */ FakeBiometricsFace(IFaceHalService faceHalService) {
        this.mFaceHalService = faceHalService;
    }

    @Override
    public IHwBinder asBinder() {
        return new IHwBinder() {
            public void transact(int code, HwParcel request, HwParcel reply, int flags)
                    throws RemoteException {}

            public IHwInterface queryLocalInterface(String descriptor) {
                return null;
            }

            public boolean linkToDeath(DeathRecipient recipient, long cookie) {
                return false;
            }

            public boolean unlinkToDeath(DeathRecipient recipient) {
                return false;
            }
        };
    }

    @Override
    public OptionalUint64 setCallback(IBiometricsFaceClientCallback iBiometricsFaceClientCallback)
            throws RemoteException {
        mFaceHalService.setCallback(new FaceCallbackAdapter(iBiometricsFaceClientCallback));
        return makeOkUint(mFaceHalService.getDeviceId());
    }

    @Override
    public int setActiveUser(int i, String s) throws RemoteException {
        return mFaceHalService.setActiveUser(i, s);
    }

    @Override
    public OptionalUint64 generateChallenge(int i) throws RemoteException {
        return makeOkUint(mFaceHalService.generateChallenge(i));
    }

    @Override
    public int enroll(ArrayList<Byte> arrayList, int i, ArrayList<Integer> arrayList1)
            throws RemoteException {
        return mFaceHalService.enroll(convertArr(arrayList), i, convertIntArr(arrayList1));
    }

    @Override
    public int revokeChallenge() throws RemoteException {
        return mFaceHalService.revokeChallenge();
    }

    @Override
    public int setFeature(int i, boolean b, ArrayList<Byte> arrayList, int i1)
            throws RemoteException {
        return mFaceHalService.setFeature(i, b, convertArr(arrayList), i1);
    }

    @Override
    public OptionalBool getFeature(int i, int i1) throws RemoteException {
        OptionalBool ret = new OptionalBool();
        ret.status = Status.OK;
        ret.value = mFaceHalService.getFeature(i, i1);
        return ret;
    }

    @Override
    public OptionalUint64 getAuthenticatorId() throws RemoteException {
        return makeOkUint(mFaceHalService.getAuthenticatorId());
    }

    @Override
    public int cancel() throws RemoteException {
        return mFaceHalService.cancel();
    }

    @Override
    public int enumerate() throws RemoteException {
        return mFaceHalService.enumerate();
    }

    @Override
    public int remove(int i) throws RemoteException {
        return mFaceHalService.remove(i);
    }

    @Override
    public int authenticate(long l) throws RemoteException {
        return mFaceHalService.authenticate(l);
    }

    @Override
    public int userActivity() throws RemoteException {
        return mFaceHalService.userActivity();
    }

    @Override
    public int resetLockout(ArrayList<Byte> arrayList) throws RemoteException {
        return mFaceHalService.resetLockout(convertArr(arrayList));
    }

    @Override
    public ArrayList<String> interfaceChain() throws RemoteException {
        // Stub
        return null;
    }

    @Override
    public void debug(NativeHandle nativeHandle, ArrayList<String> arrayList)
            throws RemoteException {
        // Stub
    }

    @Override
    public String interfaceDescriptor() throws RemoteException {
        // Stub
        return null;
    }

    @Override
    public ArrayList<byte[]> getHashChain() throws RemoteException {
        // Stub
        return null;
    }

    @Override
    public void setHALInstrumentation() throws RemoteException {
        // Stub
    }

    @Override
    public boolean linkToDeath(IHwBinder.DeathRecipient deathRecipient, long l)
            throws RemoteException {
        // Stub
        return false;
    }

    @Override
    public void ping() throws RemoteException {
        // Stub
    }

    @Override
    public DebugInfo getDebugInfo() throws RemoteException {
        // Stub
        return null;
    }

    @Override
    public void notifySyspropsChanged() throws RemoteException {
        // Stub
    }

    @Override
    public boolean unlinkToDeath(IHwBinder.DeathRecipient deathRecipient) throws RemoteException {
        // Stub
        return false;
    }

    private static OptionalUint64 makeOkUint(long value) {
        OptionalUint64 ret = new OptionalUint64();
        ret.status = Status.OK;
        ret.value = value;
        return ret;
    }

    private static int[] convertIntArr(ArrayList<Integer> al) {
        int[] ret = new int[al.size()];
        for (int i = 0; i < ret.length; i++) {
            ret[i] = al.get(i);
        }
        return ret;
    }

    private static byte[] convertArr(ArrayList<Byte> al) {
        byte[] ret = new byte[al.size()];
        for (int i = 0; i < ret.length; i++) {
            ret[i] = al.get(i);
        }
        return ret;
    }
}
