package com.libremobileos.faceunlock;

import android.hardware.biometrics.face.V1_0.IBiometricsFace;
import android.hardware.biometrics.face.V1_0.IBiometricsFaceClientCallback;
import android.hardware.biometrics.face.V1_0.OptionalBool;
import android.hardware.biometrics.face.V1_0.OptionalUint64;
import android.hardware.biometrics.face.V1_0.Status;
import android.os.HwBinder;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import com.libremobileos.faceunlock.client.IFaceHalService;
import com.libremobileos.faceunlock.client.IFaceHalServiceCallback;

import java.util.ArrayList;

public class FaceHalAdapterService {
    private static final String TAG = "FaceHalAdapterService";
    private IFaceHalService faceHalService;

    private ArrayList<Byte> byteArraytoByteList(byte[] array) {
        ArrayList<Byte> list = new ArrayList<>();
        for (byte b : array) {
            list.add(b);
        }
        return list;
    }

    private ArrayList<Integer> intArraytoIntList(int[] array) {
        ArrayList<Integer> list = new ArrayList<>();
        for (int i : array) {
            list.add(i);
        }
        return list;
    }

    private byte[] byteListToByteArray(ArrayList<Byte> list) {
        byte[] array = new byte[list.size()];
        for (int i = 0; i < list.size(); i++) {
            array[i] = list.get(i);
        }
        return array;
    }

    private int[] intListToIntArray(ArrayList<Integer> list) {
        int[] array = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            array[i] = list.get(i);
        }
        return array;
    }

    private class BiometricsFace extends IBiometricsFace.Stub {
        @Override
        public OptionalUint64 setCallback(IBiometricsFaceClientCallback iBiometricsFaceClientCallback) throws RemoteException {
            faceHalService.setCallback(new IFaceHalServiceCallback.Stub() {
                @Override
                public void onEnrollResult(long deviceId, int faceId, int userId, int remaining) throws RemoteException {
                    iBiometricsFaceClientCallback.onEnrollResult(deviceId, faceId, userId, remaining);
                }

                @Override
                public void onAuthenticated(long deviceId, int faceId, int userId, byte[] token) throws RemoteException {
                    iBiometricsFaceClientCallback.onAuthenticated(deviceId, faceId, userId, byteArraytoByteList(token));
                }

                @Override
                public void onAcquired(long deviceId, int userId, int acquiredInfo, int vendorCode) throws RemoteException {
                    iBiometricsFaceClientCallback.onAcquired(deviceId, userId, acquiredInfo, vendorCode);
                }

                @Override
                public void onError(long deviceId, int userId, int error, int vendorCode) throws RemoteException {
                    iBiometricsFaceClientCallback.onError(deviceId, userId, error, vendorCode);
                }

                @Override
                public void onRemoved(long deviceId, int[] faceIds, int userId) throws RemoteException {
                    iBiometricsFaceClientCallback.onRemoved(deviceId, intArraytoIntList(faceIds), userId);
                }

                @Override
                public void onEnumerate(long deviceId, int[] faceIds, int userId) throws RemoteException {
                    iBiometricsFaceClientCallback.onEnumerate(deviceId, intArraytoIntList(faceIds), userId);
                }

                @Override
                public void onLockoutChanged(long duration) throws RemoteException {
                    iBiometricsFaceClientCallback.onLockoutChanged(duration);
                }
            });
            OptionalUint64 ret = new OptionalUint64();
            ret.value = faceHalService.getDeviceId();
            ret.status = Status.OK;
            return ret;
        }

        @Override
        public int setActiveUser(int userId, String storePath) throws RemoteException {
            return faceHalService.setActiveUser(userId, storePath);
        }

        @Override
        public OptionalUint64 generateChallenge(int timeout) throws RemoteException {
            OptionalUint64 ret = new OptionalUint64();
            ret.value = faceHalService.generateChallenge(timeout);
            ret.status = Status.OK;
            return ret;
        }

        @Override
        public int enroll(ArrayList<Byte> token, int timeout, ArrayList<Integer> disabledFeatures) throws RemoteException {
            return faceHalService.enroll(byteListToByteArray(token), timeout, intListToIntArray(disabledFeatures));
        }

        @Override
        public int revokeChallenge() throws RemoteException {
            return faceHalService.revokeChallenge();
        }

        @Override
        public int setFeature(int feature, boolean enable, ArrayList<Byte> token, int faceId) throws RemoteException {
            return faceHalService.setFeature(feature, enable, byteListToByteArray(token), faceId);
        }

        @Override
        public OptionalBool getFeature(int feature, int faceId) throws RemoteException {
            OptionalBool ret = new OptionalBool();
            ret.value = faceHalService.getFeature(feature, faceId);
            ret.status = Status.OK;
            return ret;
        }

        @Override
        public OptionalUint64 getAuthenticatorId() throws RemoteException {
            OptionalUint64 ret = new OptionalUint64();
            ret.value = faceHalService.getAuthenticatorId();
            ret.status = Status.OK;
            return ret;
        }

        @Override
        public int cancel() throws RemoteException {
            return faceHalService.cancel();
        }

        @Override
        public int enumerate() throws RemoteException {
            return faceHalService.enumerate();
        }

        @Override
        public int remove(int faceId) throws RemoteException {
            return faceHalService.remove(faceId);
        }

        @Override
        public int authenticate(long operationId) throws RemoteException {
            return faceHalService.authenticate(operationId);
        }

        @Override
        public int userActivity() throws RemoteException {
            return faceHalService.userActivity();
        }

        @Override
        public int resetLockout(ArrayList<Byte> token) throws RemoteException {
            return faceHalService.resetLockout(byteListToByteArray(token));
        }
    }

    public int run(String[] args) throws RemoteException {
        IBinder b = ServiceManager.getService("faceunlockhal");
        faceHalService = IFaceHalService.Stub.asInterface(b);
        while (faceHalService == null) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            b = ServiceManager.getService("faceunlockhal");
            faceHalService = IFaceHalService.Stub.asInterface(b);
        }

        BiometricsFace biometricsFace = new BiometricsFace();
        biometricsFace.registerAsService("lmodroid");
        HwBinder.joinRpcThreadpool();
        return 0;
    }

    public static void main(String[] args) {
        int exitCode = 1;
        try {
            exitCode = new FaceHalAdapterService().run(args);
        } catch (Exception e) {
            Log.e(TAG, "Error", e);
            System.err.println("Error: " + e);
        }
        System.exit(exitCode);
    }
}
