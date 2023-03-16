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

#include "FaceHalServiceCallback.h"

namespace android::hardware::biometrics::face::implementation {

using ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo;
using ::android::hardware::biometrics::face::V1_0::FaceError;

FaceHalServiceCallback::FaceHalServiceCallback(sp<IBiometricsFaceClientCallback> biometricsFaceClientCallback) : mBiometricsFaceClientCallback(biometricsFaceClientCallback) {}

// Methods from ::com::libremobileos::faceunlock::client::IFaceHalServiceCallback follow.
::android::binder::Status FaceHalServiceCallback::onEnrollResult(int64_t deviceId, int32_t faceId, int32_t userId, int32_t remaining) {
    mBiometricsFaceClientCallback->onEnrollResult(deviceId, faceId, userId, remaining);
    return ::android::binder::Status::ok();
}

::android::binder::Status FaceHalServiceCallback::onAuthenticated(int64_t deviceId, int32_t faceId, int32_t userId, const ::std::vector<uint8_t> &token) {
    mBiometricsFaceClientCallback->onAuthenticated(deviceId, faceId, userId, token);
    return ::android::binder::Status::ok();
}

::android::binder::Status FaceHalServiceCallback::onAcquired(int64_t deviceId, int32_t userId, int32_t acquiredInfo, int32_t vendorCode) {
    mBiometricsFaceClientCallback->onAcquired(deviceId, userId, static_cast<FaceAcquiredInfo>(acquiredInfo), vendorCode);
    return ::android::binder::Status::ok();
}

::android::binder::Status FaceHalServiceCallback::onError(int64_t deviceId, int32_t userId, int32_t error, int32_t vendorCode) {
    mBiometricsFaceClientCallback->onError(deviceId, userId, static_cast<FaceError>(error), vendorCode);
    return ::android::binder::Status::ok();
}

::android::binder::Status FaceHalServiceCallback::onRemoved(int64_t deviceId, const ::std::vector<int32_t> &faceIds, int32_t userId) {
    std::vector<uint32_t> ufaceIds(begin(faceIds), end(faceIds));
    mBiometricsFaceClientCallback->onRemoved(deviceId, ufaceIds, userId);
    return ::android::binder::Status::ok();
}

::android::binder::Status FaceHalServiceCallback::onEnumerate(int64_t deviceId, const ::std::vector<int32_t> &faceIds, int32_t userId) {
    std::vector<uint32_t> ufaceIds(begin(faceIds), end(faceIds));
    mBiometricsFaceClientCallback->onEnumerate(deviceId, ufaceIds, userId);
    return ::android::binder::Status::ok();
}

::android::binder::Status FaceHalServiceCallback::onLockoutChanged(int64_t duration) {
    mBiometricsFaceClientCallback->onLockoutChanged(duration);
    return ::android::binder::Status::ok();
}

}  // namespace android::hardware::biometrics::face::implementation
