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

#include "BiometricsFace.h"
#include "FaceHalServiceCallback.h"

namespace android::hardware::biometrics::face::implementation {

BiometricsFace::BiometricsFace(IFaceHalService faceHalService) : mFaceHalService(faceHalService) {}

Return<Status> intToStatus(int32_t error) {
    switch (error) {
        case 0:
            return Status::OK;
        case 1:
            return Status::ILLEGAL_ARGUMENT;
        case 2:
            return Status::OPERATION_NOT_SUPPORTED;
        case 3:
            return Status::INTERNAL_ERROR;
        case 4:
            return Status::NOT_ENROLLED;
    }
    return Status::OK;
}

// Methods from IBiometricsFace follow.
Return<void> BiometricsFace::setCallback(const sp<IBiometricsFaceClientCallback>& clientCallback,
                                         setCallback_cb _hidl_cb) {
    int64_t userId = 0;
    mFaceHalService->getDeviceId(&userId);
    sp<FaceHalServiceCallback> faceHalCallback = new FaceHalServiceCallback(clientCallback);
    mFaceHalService->setCallback(faceHalCallback);
    _hidl_cb({Status::OK, (uint64_t) userId});
    return Void();
}

Return<Status> BiometricsFace::setActiveUser(int32_t userId, const hidl_string& storePath) {
    int32_t ret = 0;
    mFaceHalService->setActiveUser(userId, storePath.c_str(), &ret);
    return intToStatus(ret);
}

Return<void> BiometricsFace::generateChallenge(uint32_t timeout,
                                               generateChallenge_cb _hidl_cb) {
    int64_t challenge = 0;
    mFaceHalService->generateChallenge(timeout, &challenge);
    _hidl_cb({Status::OK, (uint64_t)challenge});
    return Void();
}

Return<Status> BiometricsFace::enroll(const hidl_vec<uint8_t>& hat, uint32_t timeoutSec,
                                      const hidl_vec<Feature>& disabledFeatures) {
    int32_t ret = 0;
    ::std::vector<int32_t> disabledFeaturesVec;
    for (int i = 0; i < disabledFeatures.size(); ++i) {
        disabledFeaturesVec.push_back(static_cast<int32_t>(disabledFeatures[i]));
    }
    mFaceHalService->enroll(hat, timeoutSec, disabledFeaturesVec, &ret);
    return intToStatus(ret);
}

Return<Status> BiometricsFace::revokeChallenge() {
    int32_t ret = 0;
    mFaceHalService->revokeChallenge(&ret);
    return intToStatus(ret);
}

Return<Status> BiometricsFace::setFeature(Feature feature, bool enabled,
                                          const hidl_vec<uint8_t>& hat,
                                          uint32_t faceId) {
    int32_t ret = 0;
    mFaceHalService->setFeature(static_cast<int32_t>(feature), enabled, hat, faceId, &ret);
    return intToStatus(ret);
}

Return<void> BiometricsFace::getFeature(Feature feature, uint32_t faceId,
                                        getFeature_cb _hidl_cb) {
    bool ret = false;
    mFaceHalService->getFeature(static_cast<int32_t>(feature), faceId, &ret);
    _hidl_cb({Status::OK, ret});
    return Void();
}

Return<void> BiometricsFace::getAuthenticatorId(getAuthenticatorId_cb _hidl_cb) {
    int64_t authenticatorId = 0;
    mFaceHalService->getAuthenticatorId(&authenticatorId);
    _hidl_cb({Status::OK, (uint64_t) authenticatorId});
    return Void();
}

Return<Status> BiometricsFace::cancel() {
    int32_t ret = 0;
    mFaceHalService->cancel(&ret);
    return intToStatus(ret);
}

Return<Status> BiometricsFace::enumerate() {
    int32_t ret = 0;
    mFaceHalService->enumerate(&ret);
    return intToStatus(ret);
}

Return<Status> BiometricsFace::remove(uint32_t faceId) {
    int32_t ret = 0;
    mFaceHalService->remove(faceId, &ret);
    return intToStatus(ret);
}

Return<Status> BiometricsFace::authenticate(uint64_t operationId) {
    int32_t ret = 0;
    mFaceHalService->authenticate(operationId, &ret);
    return intToStatus(ret);
}

Return<Status> BiometricsFace::userActivity() {
    int32_t ret = 0;
    mFaceHalService->userActivity(&ret);
    return intToStatus(ret);
}

Return<Status> BiometricsFace::resetLockout(const hidl_vec<uint8_t>& hat) {
    int32_t ret = 0;
    mFaceHalService->resetLockout(hat, &ret);
    return intToStatus(ret);
}

}  // namespace android::hardware::biometrics::face::implementation
