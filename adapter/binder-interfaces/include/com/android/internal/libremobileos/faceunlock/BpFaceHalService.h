#pragma once

#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <utils/Errors.h>
#include <com/android/internal/libremobileos/faceunlock/IFaceHalService.h>

namespace com {

namespace android {

namespace internal {

namespace libremobileos {

namespace faceunlock {

class BpFaceHalService : public ::android::BpInterface<IFaceHalService> {
public:
  explicit BpFaceHalService(const ::android::sp<::android::IBinder>& _aidl_impl);
  virtual ~BpFaceHalService() = default;
  ::android::binder::Status getDeviceId(int64_t* _aidl_return) override;
  ::android::binder::Status setCallback(const ::android::sp<::com::android::internal::libremobileos::faceunlock::IFaceHalServiceCallback>& callback) override;
  ::android::binder::Status setActiveUser(int32_t userId, const ::android::String16& storePath, int32_t* _aidl_return) override;
  ::android::binder::Status generateChallenge(int32_t timeout, int64_t* _aidl_return) override;
  ::android::binder::Status enroll(const ::std::vector<uint8_t>& token, int32_t timeout, const ::std::vector<int32_t>& disabledFeatures, int32_t* _aidl_return) override;
  ::android::binder::Status revokeChallenge(int32_t* _aidl_return) override;
  ::android::binder::Status setFeature(int32_t feature, bool enable, const ::std::vector<uint8_t>& token, int32_t faceId, int32_t* _aidl_return) override;
  ::android::binder::Status getFeature(int32_t feature, int32_t faceId, bool* _aidl_return) override;
  ::android::binder::Status getAuthenticatorId(int64_t* _aidl_return) override;
  ::android::binder::Status cancel(int32_t* _aidl_return) override;
  ::android::binder::Status enumerate(int32_t* _aidl_return) override;
  ::android::binder::Status remove(int32_t faceId, int32_t* _aidl_return) override;
  ::android::binder::Status authenticate(int64_t operationId, int32_t* _aidl_return) override;
  ::android::binder::Status userActivity(int32_t* _aidl_return) override;
  ::android::binder::Status resetLockout(const ::std::vector<uint8_t>& token, int32_t* _aidl_return) override;
};  // class BpFaceHalService

}  // namespace faceunlock

}  // namespace libremobileos

}  // namespace internal

}  // namespace android

}  // namespace com
