#pragma once

#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <utils/Errors.h>
#include <com/libremobileos/faceunlock/client/IFaceHalServiceCallback.h>

namespace com {

namespace libremobileos {

namespace faceunlock {

namespace client {

class BpFaceHalServiceCallback : public ::android::BpInterface<IFaceHalServiceCallback> {
public:
  explicit BpFaceHalServiceCallback(const ::android::sp<::android::IBinder>& _aidl_impl);
  virtual ~BpFaceHalServiceCallback() = default;
  ::android::binder::Status onEnrollResult(int64_t deviceId, int32_t faceId, int32_t userId, int32_t remaining) override;
  ::android::binder::Status onAuthenticated(int64_t deviceId, int32_t faceId, int32_t userId, const ::std::vector<uint8_t>& token) override;
  ::android::binder::Status onAcquired(int64_t deviceId, int32_t userId, int32_t acquiredInfo, int32_t vendorCode) override;
  ::android::binder::Status onError(int64_t deviceId, int32_t userId, int32_t error, int32_t vendorCode) override;
  ::android::binder::Status onRemoved(int64_t deviceId, const ::std::vector<int32_t>& faceIds, int32_t userId) override;
  ::android::binder::Status onEnumerate(int64_t deviceId, const ::std::vector<int32_t>& faceIds, int32_t userId) override;
  ::android::binder::Status onLockoutChanged(int64_t duration) override;
};  // class BpFaceHalServiceCallback

}  // namespace client

}  // namespace faceunlock

}  // namespace libremobileos

}  // namespace com
