#pragma once

#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <binder/Status.h>
#include <cstdint>
#include <utils/StrongPointer.h>
#include <vector>

namespace com {

namespace libremobileos {

namespace faceunlock {

namespace client {

class IFaceHalServiceCallback : public ::android::IInterface {
public:
  DECLARE_META_INTERFACE(FaceHalServiceCallback)
  virtual ::android::binder::Status onEnrollResult(int64_t deviceId, int32_t faceId, int32_t userId, int32_t remaining) = 0;
  virtual ::android::binder::Status onAuthenticated(int64_t deviceId, int32_t faceId, int32_t userId, const ::std::vector<uint8_t>& token) = 0;
  virtual ::android::binder::Status onAcquired(int64_t deviceId, int32_t userId, int32_t acquiredInfo, int32_t vendorCode) = 0;
  virtual ::android::binder::Status onError(int64_t deviceId, int32_t userId, int32_t error, int32_t vendorCode) = 0;
  virtual ::android::binder::Status onRemoved(int64_t deviceId, const ::std::vector<int32_t>& faceIds, int32_t userId) = 0;
  virtual ::android::binder::Status onEnumerate(int64_t deviceId, const ::std::vector<int32_t>& faceIds, int32_t userId) = 0;
  virtual ::android::binder::Status onLockoutChanged(int64_t duration) = 0;
};  // class IFaceHalServiceCallback

class IFaceHalServiceCallbackDefault : public IFaceHalServiceCallback {
public:
  ::android::IBinder* onAsBinder() override {
    return nullptr;
  }
  ::android::binder::Status onEnrollResult(int64_t, int32_t, int32_t, int32_t) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status onAuthenticated(int64_t, int32_t, int32_t, const ::std::vector<uint8_t>&) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status onAcquired(int64_t, int32_t, int32_t, int32_t) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status onError(int64_t, int32_t, int32_t, int32_t) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status onRemoved(int64_t, const ::std::vector<int32_t>&, int32_t) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status onEnumerate(int64_t, const ::std::vector<int32_t>&, int32_t) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status onLockoutChanged(int64_t) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
};  // class IFaceHalServiceCallbackDefault

}  // namespace client

}  // namespace faceunlock

}  // namespace libremobileos

}  // namespace com
