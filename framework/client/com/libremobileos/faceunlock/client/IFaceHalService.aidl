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

import com.libremobileos.faceunlock.client.IFaceHalServiceCallback;

interface IFaceHalService {
    long getDeviceId();

    oneway void setCallback(in IFaceHalServiceCallback callback);

    int setActiveUser(int userId, String storePath);

    long generateChallenge(int timeout);

    int enroll(in byte[] token, int timeout, in int[] disabledFeatures);

    int revokeChallenge();

    int setFeature(int feature, boolean enable, in byte[] token, int faceId);

    boolean getFeature(int feature, int faceId);

    long getAuthenticatorId();

    int cancel();

    int enumerate();

    int remove(int faceId);

    int authenticate(long operationId);

    int userActivity();

    int resetLockout(in byte[] token);
}
