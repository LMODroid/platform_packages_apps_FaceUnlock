#pragma once

#include <binder/IInterface.h>
#include <com/libremobileos/faceunlock/client/IFaceHalServiceCallback.h>

namespace com {

namespace libremobileos {

namespace faceunlock {

namespace client {

class BnFaceHalServiceCallback : public ::android::BnInterface<IFaceHalServiceCallback> {
public:
  static constexpr uint32_t TRANSACTION_onEnrollResult = ::android::IBinder::FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_onAuthenticated = ::android::IBinder::FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_onAcquired = ::android::IBinder::FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_onError = ::android::IBinder::FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_onRemoved = ::android::IBinder::FIRST_CALL_TRANSACTION + 4;
  static constexpr uint32_t TRANSACTION_onEnumerate = ::android::IBinder::FIRST_CALL_TRANSACTION + 5;
  static constexpr uint32_t TRANSACTION_onLockoutChanged = ::android::IBinder::FIRST_CALL_TRANSACTION + 6;
  explicit BnFaceHalServiceCallback();
  ::android::status_t onTransact(uint32_t _aidl_code, const ::android::Parcel& _aidl_data, ::android::Parcel* _aidl_reply, uint32_t _aidl_flags) override;
};  // class BnFaceHalServiceCallback

}  // namespace client

}  // namespace faceunlock

}  // namespace libremobileos

}  // namespace com
