/*
 * Copyright 2020 The Android Open Source Project
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

#pragma once

#include "BiometricsFace.h"

namespace android::hardware::biometrics::face::implementation {

class FaceHalServiceCallback : public BnFaceHalServiceCallback {
public:
    FaceHalServiceCallback(sp<IBiometricsFaceClientCallback>);

    // Methods from ::com::libremobileos::faceunlock::client::IFaceHalServiceCallback follow.
    ::android::binder::Status onEnrollResult(int64_t deviceId, int32_t faceId, int32_t userId, int32_t remaining) override;

    ::android::binder::Status onAuthenticated(int64_t deviceId, int32_t faceId, int32_t userId, const ::std::vector<uint8_t> &token) override;

    ::android::binder::Status onAcquired(int64_t deviceId, int32_t userId, int32_t acquiredInfo, int32_t vendorCode) override;

    ::android::binder::Status onError(int64_t deviceId, int32_t userId, int32_t error, int32_t vendorCode) override;

    ::android::binder::Status onRemoved(int64_t deviceId, const ::std::vector<int32_t> &faceIds, int32_t userId) override;

    ::android::binder::Status onEnumerate(int64_t deviceId, const ::std::vector<int32_t> &faceIds, int32_t userId) override;

    ::android::binder::Status onLockoutChanged(int64_t duration) override;

private:
    sp<IBiometricsFaceClientCallback> mBiometricsFaceClientCallback;
};

}  // namespace android::hardware::biometrics::face::implementation
