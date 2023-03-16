#pragma once

#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <binder/Status.h>
#include <com/libremobileos/faceunlock/client/IFaceHalServiceCallback.h>
#include <cstdint>
#include <utils/String16.h>
#include <utils/StrongPointer.h>
#include <vector>

namespace com {

namespace libremobileos {

namespace faceunlock {

namespace client {

class IFaceHalService : public ::android::IInterface {
public:
  DECLARE_META_INTERFACE(FaceHalService)
  virtual ::android::binder::Status getDeviceId(int64_t* _aidl_return) = 0;
  virtual ::android::binder::Status setCallback(const ::android::sp<::com::libremobileos::faceunlock::client::IFaceHalServiceCallback>& callback) = 0;
  virtual ::android::binder::Status setActiveUser(int32_t userId, const ::android::String16& storePath, int32_t* _aidl_return) = 0;
  virtual ::android::binder::Status generateChallenge(int32_t timeout, int64_t* _aidl_return) = 0;
  virtual ::android::binder::Status enroll(const ::std::vector<uint8_t>& token, int32_t timeout, const ::std::vector<int32_t>& disabledFeatures, int32_t* _aidl_return) = 0;
  virtual ::android::binder::Status revokeChallenge(int32_t* _aidl_return) = 0;
  virtual ::android::binder::Status setFeature(int32_t feature, bool enable, const ::std::vector<uint8_t>& token, int32_t faceId, int32_t* _aidl_return) = 0;
  virtual ::android::binder::Status getFeature(int32_t feature, int32_t faceId, bool* _aidl_return) = 0;
  virtual ::android::binder::Status getAuthenticatorId(int64_t* _aidl_return) = 0;
  virtual ::android::binder::Status cancel(int32_t* _aidl_return) = 0;
  virtual ::android::binder::Status enumerate(int32_t* _aidl_return) = 0;
  virtual ::android::binder::Status remove(int32_t faceId, int32_t* _aidl_return) = 0;
  virtual ::android::binder::Status authenticate(int64_t operationId, int32_t* _aidl_return) = 0;
  virtual ::android::binder::Status userActivity(int32_t* _aidl_return) = 0;
  virtual ::android::binder::Status resetLockout(const ::std::vector<uint8_t>& token, int32_t* _aidl_return) = 0;
};  // class IFaceHalService

class IFaceHalServiceDefault : public IFaceHalService {
public:
  ::android::IBinder* onAsBinder() override {
    return nullptr;
  }
  ::android::binder::Status getDeviceId(int64_t*) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status setCallback(const ::android::sp<::com::libremobileos::faceunlock::client::IFaceHalServiceCallback>&) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status setActiveUser(int32_t, const ::android::String16&, int32_t*) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status generateChallenge(int32_t, int64_t*) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status enroll(const ::std::vector<uint8_t>&, int32_t, const ::std::vector<int32_t>&, int32_t*) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status revokeChallenge(int32_t*) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status setFeature(int32_t, bool, const ::std::vector<uint8_t>&, int32_t, int32_t*) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status getFeature(int32_t, int32_t, bool*) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status getAuthenticatorId(int64_t*) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status cancel(int32_t*) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status enumerate(int32_t*) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status remove(int32_t, int32_t*) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status authenticate(int64_t, int32_t*) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status userActivity(int32_t*) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status resetLockout(const ::std::vector<uint8_t>&, int32_t*) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
};  // class IFaceHalServiceDefault

}  // namespace client

}  // namespace faceunlock

}  // namespace libremobileos

}  // namespace com
