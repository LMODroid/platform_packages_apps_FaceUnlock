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

import android.os.RemoteException;
import android.os.ServiceManager;

public final class FaceUnlockManager {

	public static final String SERVICE_NAME = "faceunlock";
	private static final String TAG = "FaceUnlockManager";

	private static FaceUnlockManager sFaceUnlockManager;
	private IFaceUnlockManager mFaceUnlockManager;

	private FaceUnlockManager() {
		mFaceUnlockManager = IFaceUnlockManager.Stub.asInterface(
				ServiceManager.getService(SERVICE_NAME));
		if (mFaceUnlockManager == null)
			throw new RuntimeException("Unable to get FaceUnlockService.");
	}

	public static FaceUnlockManager getInstance() {
		if (sFaceUnlockManager != null)
			return sFaceUnlockManager;
		sFaceUnlockManager = new FaceUnlockManager();
		return sFaceUnlockManager;
	}

	/**
	 * Send enroll result remainings to HAL.
	 */
	public void enrollResult(int remaining) {
		try {
			mFaceUnlockManager.enrollResult(remaining);
		} catch (RemoteException e) {
			throw new RuntimeException("Failed when enrollResult(): " + e);
		}
	}

	/**
	 * Send error code to HAL.
	 */
	public void error(int error) {
		try {
			mFaceUnlockManager.error(error);
		} catch (RemoteException e) {
			throw new RuntimeException("Failed when error(): " + e);
		}
	}

	/**
	 * Save Enrolled face and HAT
	 */
	public void finishEnroll(String encodedFaces, byte[] token) {
		try {
			mFaceUnlockManager.finishEnroll(encodedFaces, token);
		} catch (RemoteException e) {
			throw new RuntimeException("Failed when finishEnroll(): " + e);
		}
	}
}
