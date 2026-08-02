#ifndef RUNNER_MPV_WINDOWS_BRIDGE_H_
#define RUNNER_MPV_WINDOWS_BRIDGE_H_

#include <flutter/binary_messenger.h>
#include <flutter/encodable_value.h>
#include <flutter/event_channel.h>
#include <flutter/method_channel.h>
#include <windows.h>

#include <cstdint>
#include <memory>
#include <string>
#include <tuple>

class MpvWindowsBridge {
 public:
  MpvWindowsBridge(flutter::BinaryMessenger* messenger, HWND host_window);
  ~MpvWindowsBridge();

  MpvWindowsBridge(const MpvWindowsBridge&) = delete;
  MpvWindowsBridge& operator=(const MpvWindowsBridge&) = delete;

  bool HandleWindowMessage(UINT message, WPARAM wparam);
  void Shutdown();

 private:
  struct Runtime;

  using EncodableMap = flutter::EncodableMap;
  using EncodableValue = flutter::EncodableValue;
  using MethodResult = flutter::MethodResult<EncodableValue>;

  void HandleMethodCall(const flutter::MethodCall<EncodableValue>& call,
                        std::unique_ptr<MethodResult> result);
  EncodableMap Probe();
  bool EnsureRuntime(std::string* code);
  bool Open(const EncodableMap& arguments, std::string* code);
  bool Pause(std::string* code);
  bool Seek(const EncodableMap& arguments, std::string* code);
  void Close(bool emit_closed);
  void Poll();
  void HandleMpvEvent(const void* raw_event);
  void EmitState(const std::string& state,
                 const std::string& error_code = std::string());
  void EmitIdle();
  void StartTimer();
  void StopTimer();

  std::unique_ptr<flutter::MethodChannel<EncodableValue>> method_channel_;
  std::unique_ptr<flutter::EventChannel<EncodableValue>> event_channel_;
  std::unique_ptr<flutter::EventSink<EncodableValue>> event_sink_;
  std::unique_ptr<Runtime> runtime_;
  HWND host_window_ = nullptr;
  void* mpv_handle_ = nullptr;
  std::string active_session_id_;
  std::string active_state_ = "idle";
  int64_t event_sequence_ = 0;
  int64_t position_ms_ = 0;
  int64_t buffered_position_ms_ = 0;
  bool paused_ = false;
  bool core_idle_ = true;
  bool file_loaded_ = false;
  bool timer_active_ = false;
};

#endif  // RUNNER_MPV_WINDOWS_BRIDGE_H_
