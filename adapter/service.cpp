/*
 * Copyright (C) 2020 The Android Open Source Project
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

#define LOG_TAG "android.hardware.biometrics.face@1.0-service.lmodroid"

#include "BiometricsFace.h"
#include <android/hardware/biometrics/face/1.0/IBiometricsFace.h>
#include <android/hardware/biometrics/face/1.0/types.h>
#include <android/log.h>
#include <android/binder_manager.h>
#include <binder/ProcessState.h>
#include <hidl/HidlSupport.h>
#include <hidl/HidlTransportSupport.h>

using android::IBinder;
using android::sp;
using android::hardware::configureRpcThreadpool;
using android::hardware::joinRpcThreadpool;
using android::hardware::biometrics::face::implementation::BiometricsFace;
using android::hardware::biometrics::face::V1_0::IBiometricsFace;
using ::aidl::com::libremobileos::faceunlock::client::IFaceHalService;

int main() {
    ALOGI("LMODroid BiometricsFace HAL is being started.");
    // the conventional HAL might start binder services
    android::ProcessState::self()->setThreadPoolMaxThreadCount(4);
    android::ProcessState::self()->startThreadPool();
    configureRpcThreadpool(4, true /*callerWillJoin*/);

    ALOGI("Waiting for faceunlockhal service to start...");
    IFaceHalService faceHalService = IFaceHalService::fromBinder(SpAIBinder(AServiceManager_waitForService("faceunlockhal")));

    android::sp<IBiometricsFace> face = new BiometricsFace(faceHalService);
    const android::status_t status = face->registerAsService("lmodroid");

    if (status != android::OK) {
        ALOGE("Error starting the BiometricsFace HAL.");
        return 1;
    }

    ALOGI("BiometricsFace HAL has started successfully.");
    joinRpcThreadpool();

    ALOGI("BiometricsFace HAL is terminating.");
    return 1;  // should never get here
}
