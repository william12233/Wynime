#include "mpv_windows_bridge.h"

#include <flutter/standard_method_codec.h>
#include <flutter/stream_handler_functions.h>

#include <algorithm>
#include <array>
#include <cmath>
#include <limits>
#include <optional>
#include <sstream>
#include <string_view>
#include <utility>
#include <vector>

namespace {

constexpr UINT_PTR kMpvPollTimerId = 0x57594D50;
constexpr UINT kMpvPollIntervalMs = 100;
constexpr int kMpvFormatFlag = 3;
constexpr int kMpvFormatDouble = 5;
constexpr int kMpvEventNone = 0;
constexpr int kMpvEventShutdown = 1;
constexpr int kMpvEventEndFile = 7;
constexpr int kMpvEventFileLoaded = 8;
constexpr int kMpvEventPlaybackRestart = 21;
constexpr int kMpvEventPropertyChange = 22;
constexpr int kMpvEventQueueOverflow = 24;
constexpr int kMpvEndFileEof = 0;
constexpr int kMpvEndFileError = 4;
constexpr uint64_t kMinimumClientApiMajor = 2;

struct MpvEvent {
  int event_id;
  int error;
  uint64_t reply_userdata;
  void* data;
};

struct MpvEventProperty {
  const char* name;
  int format;
  void* data;
};

struct MpvEventEndFile {
  int reason;
  int error;
  int64_t playlist_entry_id;
  int64_t playlist_insert_id;
  int playlist_insert_num_entries;
};

std::optional<std::string> StringArgument(
    const flutter::EncodableMap& arguments,
    const char* key) {
  const auto found = arguments.find(flutter::EncodableValue(key));
  if (found == arguments.end()) {
    return std::nullopt;
  }
  const auto* value = std::get_if<std::string>(&found->second);
  if (value == nullptr || value->empty()) {
    return std::nullopt;
  }
  return *value;
}

std::optional<int64_t> IntArgument(const flutter::EncodableMap& arguments,
                                   const char* key) {
  const auto found = arguments.find(flutter::EncodableValue(key));
  if (found == arguments.end()) {
    return std::nullopt;
  }
  if (const auto* value = std::get_if<int64_t>(&found->second)) {
    return *value;
  }
  if (const auto* value = std::get_if<int32_t>(&found->second)) {
    return static_cast<int64_t>(*value);
  }
  return std::nullopt;
}

bool IsSafeSessionId(std::string_view value) {
  if (value.empty() || value.size() > 128) {
    return false;
  }
  for (const unsigned char character : value) {
    const bool alpha_numeric =
        (character >= 'a' && character <= 'z') ||
        (character >= 'A' && character <= 'Z') ||
        (character >= '0' && character <= '9');
    const bool allowed_symbol =
        character == '!' || character == '#' || character == '$' ||
        character == '%' || character == '&' || character == '\'' ||
        character == '*' || character == '+' || character == '-' ||
        character == '.' || character == '^' || character == '_' ||
        character == '`' || character == '|' || character == '~';
    if (!alpha_numeric && !allowed_symbol) {
      return false;
    }
  }
  return true;
}

bool IsSafeTimelineIdentity(std::string_view value) {
  if (value.empty() || value.size() > 1024) {
    return false;
  }
  for (const unsigned char character : value) {
    if (character < 0x20 || character == 0x7F) {
      return false;
    }
  }
  return true;
}

bool IsNumericLoopbackUrl(std::string_view value) {
  if (value.empty() || value.size() > 4096 ||
      value.find_first_of("?#@\\\r\n") != std::string_view::npos) {
    return false;
  }

  constexpr std::string_view kIpv4Prefix = "http://127.0.0.1:";
  constexpr std::string_view kIpv6Prefix = "http://[::1]:";
  size_t port_start = std::string_view::npos;
  if (value.starts_with(kIpv4Prefix)) {
    port_start = kIpv4Prefix.size();
  } else if (value.starts_with(kIpv6Prefix)) {
    port_start = kIpv6Prefix.size();
  } else {
    return false;
  }

  const size_t path_start = value.find('/', port_start);
  if (path_start == std::string_view::npos || path_start == port_start) {
    return false;
  }
  unsigned int port = 0;
  for (size_t index = port_start; index < path_start; ++index) {
    const char character = value[index];
    if (character < '0' || character > '9') {
      return false;
    }
    port = port * 10u + static_cast<unsigned int>(character - '0');
    if (port > 65535u) {
      return false;
    }
  }
  return port > 0u && path_start + 1u < value.size();
}

std::wstring ExecutableDirectory() {
  std::wstring buffer(32768, L'\0');
  const DWORD length = GetModuleFileNameW(
      nullptr, buffer.data(), static_cast<DWORD>(buffer.size()));
  if (length == 0 || length >= buffer.size()) {
    return std::wstring();
  }
  buffer.resize(length);
  const size_t separator = buffer.find_last_of(L"\\/");
  if (separator == std::wstring::npos) {
    return std::wstring();
  }
  buffer.resize(separator);
  return buffer;
}

std::string ErrorCodeForResult(int result, const char* operation) {
  std::ostringstream stream;
  stream << operation << "_failed_" << result;
  return stream.str();
}

}  // namespace

struct MpvWindowsBridge::Runtime {
  using ClientApiVersion = uint64_t(__cdecl*)();
  using Create = void*(__cdecl*)();
  using Initialize = int(__cdecl*)(void*);
  using TerminateDestroy = void(__cdecl*)(void*);
  using SetOptionString = int(__cdecl*)(void*, const char*, const char*);
  using Command = int(__cdecl*)(void*, const char**);
  using SetProperty = int(__cdecl*)(void*, const char*, int, void*);
  using ObserveProperty = int(__cdecl*)(void*, uint64_t, const char*, int);
  using WaitEvent = MpvEvent*(__cdecl*)(void*, double);

  HMODULE module = nullptr;
  ClientApiVersion client_api_version = nullptr;
  Create create = nullptr;
  Initialize initialize = nullptr;
  TerminateDestroy terminate_destroy = nullptr;
  SetOptionString set_option_string = nullptr;
  Command command = nullptr;
  SetProperty set_property = nullptr;
  ObserveProperty observe_property = nullptr;
  WaitEvent wait_event = nullptr;
  uint64_t api_version = 0;

  ~Runtime() {
    if (module != nullptr) {
      FreeLibrary(module);
    }
  }

  template <typename Function>
  bool Resolve(const char* name, Function* output) {
    *output = reinterpret_cast<Function>(GetProcAddress(module, name));
    return *output != nullptr;
  }

  bool Load(std::string* code) {
    const std::wstring directory = ExecutableDirectory();
    if (directory.empty()) {
      *code = "executable_directory_unavailable";
      return false;
    }

    constexpr std::array<const wchar_t*, 4> kAllowedDllNames = {
        L"mpv-2.dll", L"libmpv-2.dll", L"mpv-1.dll", L"libmpv-1.dll"};
    bool incompatible_library_found = false;
    for (const wchar_t* name : kAllowedDllNames) {
      const std::wstring path = directory + L"\\" + name;
      module = LoadLibraryExW(path.c_str(), nullptr,
                              LOAD_LIBRARY_SEARCH_DLL_LOAD_DIR |
                                  LOAD_LIBRARY_SEARCH_SYSTEM32);
      if (module == nullptr) {
        continue;
      }

      const bool complete =
          Resolve("mpv_client_api_version", &client_api_version) &&
          Resolve("mpv_create", &create) &&
          Resolve("mpv_initialize", &initialize) &&
          Resolve("mpv_terminate_destroy", &terminate_destroy) &&
          Resolve("mpv_set_option_string", &set_option_string) &&
          Resolve("mpv_command", &command) &&
          Resolve("mpv_set_property", &set_property) &&
          Resolve("mpv_observe_property", &observe_property) &&
          Resolve("mpv_wait_event", &wait_event);
      if (!complete) {
        incompatible_library_found = true;
        FreeLibrary(module);
        module = nullptr;
        continue;
      }

      api_version = client_api_version();
      const uint64_t major = api_version >> 16u;
      if (major != kMinimumClientApiMajor) {
        incompatible_library_found = true;
        FreeLibrary(module);
        module = nullptr;
        continue;
      }
      *code = "libmpv_ready";
      return true;
    }

    *code = incompatible_library_found ? "client_api_incompatible"
                                       : "runtime_missing";
    return false;
  }
};

MpvWindowsBridge::MpvWindowsBridge(flutter::BinaryMessenger* messenger,
                                   HWND host_window)
    : host_window_(host_window) {
  method_channel_ =
      std::make_unique<flutter::MethodChannel<EncodableValue>>(
          messenger, "io.github.william12233.wynime/mpv",
          &flutter::StandardMethodCodec::GetInstance());
  method_channel_->SetMethodCallHandler(
      [this](const flutter::MethodCall<EncodableValue>& call,
             std::unique_ptr<MethodResult> result) {
        HandleMethodCall(call, std::move(result));
      });

  event_channel_ = std::make_unique<flutter::EventChannel<EncodableValue>>(
      messenger, "io.github.william12233.wynime/mpv/events",
      &flutter::StandardMethodCodec::GetInstance());
  event_channel_->SetStreamHandler(
      std::make_unique<flutter::StreamHandlerFunctions<EncodableValue>>(
          [this](const EncodableValue*,
                 std::unique_ptr<flutter::EventSink<EncodableValue>>&& sink)
              -> std::unique_ptr<flutter::StreamHandlerError<EncodableValue>> {
            event_sink_ = std::move(sink);
            if (active_session_id_.empty()) {
              EmitIdle();
            } else {
              EmitState(active_state_);
            }
            return nullptr;
          },
          [this](const EncodableValue*)
              -> std::unique_ptr<flutter::StreamHandlerError<EncodableValue>> {
            event_sink_.reset();
            return nullptr;
          }));
}

MpvWindowsBridge::~MpvWindowsBridge() {
  Shutdown();
}

bool MpvWindowsBridge::HandleWindowMessage(UINT message, WPARAM wparam) {
  if (message != WM_TIMER || wparam != kMpvPollTimerId) {
    return false;
  }
  Poll();
  return true;
}

void MpvWindowsBridge::Shutdown() {
  Close(false);
  StopTimer();
  event_sink_.reset();
  runtime_.reset();
}

void MpvWindowsBridge::HandleMethodCall(
    const flutter::MethodCall<EncodableValue>& call,
    std::unique_ptr<MethodResult> result) {
  if (call.method_name() == "probe") {
    result->Success(EncodableValue(Probe()));
    return;
  }
  if (call.method_name() == "close") {
    Close(true);
    result->Success();
    return;
  }

  const auto* arguments =
      call.arguments() == nullptr
          ? nullptr
          : std::get_if<EncodableMap>(call.arguments());
  if (arguments == nullptr) {
    result->Error("invalid_mpv_request", "mpv arguments must be a map.");
    return;
  }

  std::string code;
  bool success = false;
  if (call.method_name() == "open") {
    success = Open(*arguments, &code);
  } else if (call.method_name() == "pause") {
    success = Pause(&code);
  } else if (call.method_name() == "seek") {
    success = Seek(*arguments, &code);
  } else {
    result->NotImplemented();
    return;
  }

  if (success) {
    result->Success();
  } else {
    result->Error(code.empty() ? "mpv_operation_failed" : code,
                  "The Windows libmpv prototype rejected the operation.");
  }
}

MpvWindowsBridge::EncodableMap MpvWindowsBridge::Probe() {
  std::string code;
  const bool available = EnsureRuntime(&code);
  EncodableMap result;
  result[EncodableValue("backendId")] = EncodableValue("windows-mpv");
  result[EncodableValue("availability")] = EncodableValue(
      available ? "available"
                : code == "runtime_missing" ? "unavailable" : "incompatible");
  result[EncodableValue("code")] = EncodableValue(code);
  if (available && runtime_ != nullptr) {
    const int64_t version = static_cast<int64_t>(runtime_->api_version);
    const uint64_t major = runtime_->api_version >> 16u;
    const uint64_t minor = runtime_->api_version & 0xFFFFu;
    result[EncodableValue("clientApiVersion")] = EncodableValue(version);
    result[EncodableValue("runtimeVersion")] =
        EncodableValue("client-api-" + std::to_string(major) + "." +
                       std::to_string(minor));
  }
  return result;
}

bool MpvWindowsBridge::EnsureRuntime(std::string* code) {
  if (runtime_ != nullptr && runtime_->module != nullptr) {
    *code = "libmpv_ready";
    return true;
  }
  runtime_ = std::make_unique<Runtime>();
  if (!runtime_->Load(code)) {
    runtime_.reset();
    return false;
  }
  return true;
}

bool MpvWindowsBridge::Open(const EncodableMap& arguments,
                            std::string* code) {
  const auto session_id = StringArgument(arguments, "sessionId");
  const auto uri = StringArgument(arguments, "uri");
  const auto timeline_identity =
      StringArgument(arguments, "timelineMapIdentity");
  if (!session_id || !IsSafeSessionId(*session_id)) {
    *code = "invalid_session_id";
    return false;
  }
  if (!uri || !IsNumericLoopbackUrl(*uri)) {
    *code = "non_loopback_uri_rejected";
    return false;
  }
  if (!timeline_identity || !IsSafeTimelineIdentity(*timeline_identity)) {
    *code = "invalid_timeline_identity";
    return false;
  }

  Close(false);
  if (!EnsureRuntime(code)) {
    return false;
  }

  mpv_handle_ = runtime_->create();
  if (mpv_handle_ == nullptr) {
    *code = "mpv_create_failed";
    return false;
  }

  const std::array<std::pair<const char*, const char*>, 11> options = {{
      {"config", "no"},
      {"load-scripts", "no"},
      {"ytdl", "no"},
      {"input-default-bindings", "no"},
      {"input-vo-keyboard", "no"},
      {"osc", "no"},
      {"terminal", "no"},
      {"idle", "yes"},
      {"keep-open", "yes"},
      {"sub-auto", "no"},
      {"audio-file-auto", "no"},
  }};
  for (const auto& [name, value] : options) {
    const int option_result =
        runtime_->set_option_string(mpv_handle_, name, value);
    if (option_result < 0) {
      *code = ErrorCodeForResult(option_result, "mpv_option");
      Close(false);
      return false;
    }
  }
  runtime_->set_option_string(mpv_handle_, "title",
                              "Wynime libmpv prototype");

  const int initialize_result = runtime_->initialize(mpv_handle_);
  if (initialize_result < 0) {
    *code = ErrorCodeForResult(initialize_result, "mpv_initialize");
    Close(false);
    return false;
  }

  const std::array<std::tuple<uint64_t, const char*, int>, 4> observations = {{
      {1u, "time-pos", kMpvFormatDouble},
      {2u, "demuxer-cache-time", kMpvFormatDouble},
      {3u, "pause", kMpvFormatFlag},
      {4u, "core-idle", kMpvFormatFlag},
  }};
  for (const auto& [identifier, name, format] : observations) {
    const int observe_result = runtime_->observe_property(
        mpv_handle_, identifier, name, format);
    if (observe_result < 0) {
      *code = ErrorCodeForResult(observe_result, "mpv_observe");
      Close(false);
      return false;
    }
  }

  active_session_id_ = *session_id;
  active_state_ = "opening";
  position_ms_ = 0;
  buffered_position_ms_ = 0;
  paused_ = false;
  core_idle_ = true;
  file_loaded_ = false;

  const char* command[] = {"loadfile", uri->c_str(), "replace", nullptr};
  const int command_result = runtime_->command(mpv_handle_, command);
  if (command_result < 0) {
    *code = ErrorCodeForResult(command_result, "mpv_loadfile");
    Close(false);
    return false;
  }

  StartTimer();
  EmitState("opening");
  *code = "ok";
  return true;
}

bool MpvWindowsBridge::Pause(std::string* code) {
  if (mpv_handle_ == nullptr || runtime_ == nullptr) {
    *code = "mpv_not_open";
    return false;
  }
  int flag = 1;
  const int result =
      runtime_->set_property(mpv_handle_, "pause", kMpvFormatFlag, &flag);
  if (result < 0) {
    *code = ErrorCodeForResult(result, "mpv_pause");
    return false;
  }
  *code = "ok";
  return true;
}

bool MpvWindowsBridge::Seek(const EncodableMap& arguments,
                            std::string* code) {
  if (mpv_handle_ == nullptr || runtime_ == nullptr) {
    *code = "mpv_not_open";
    return false;
  }
  const auto position_ms = IntArgument(arguments, "positionMs");
  if (!position_ms || *position_ms < 0) {
    *code = "invalid_seek_position";
    return false;
  }
  const double seconds = static_cast<double>(*position_ms) / 1000.0;
  const std::string seconds_text = std::to_string(seconds);
  const char* command[] = {"seek", seconds_text.c_str(), "absolute+exact",
                           nullptr};
  const int result = runtime_->command(mpv_handle_, command);
  if (result < 0) {
    *code = ErrorCodeForResult(result, "mpv_seek");
    return false;
  }
  position_ms_ = *position_ms;
  *code = "ok";
  return true;
}

void MpvWindowsBridge::Close(bool emit_closed) {
  if (mpv_handle_ != nullptr && runtime_ != nullptr) {
    runtime_->terminate_destroy(mpv_handle_);
  }
  mpv_handle_ = nullptr;
  StopTimer();
  if (emit_closed && !active_session_id_.empty()) {
    EmitState("closed");
  }
  active_session_id_.clear();
  active_state_ = "idle";
  position_ms_ = 0;
  buffered_position_ms_ = 0;
  paused_ = false;
  core_idle_ = true;
  file_loaded_ = false;
}

void MpvWindowsBridge::Poll() {
  if (mpv_handle_ == nullptr || runtime_ == nullptr) {
    return;
  }
  for (;;) {
    MpvEvent* event = runtime_->wait_event(mpv_handle_, 0.0);
    if (event == nullptr || event->event_id == kMpvEventNone) {
      return;
    }
    HandleMpvEvent(event);
    if (mpv_handle_ == nullptr) {
      return;
    }
  }
}

void MpvWindowsBridge::HandleMpvEvent(const void* raw_event) {
  const auto* event = static_cast<const MpvEvent*>(raw_event);
  switch (event->event_id) {
    case kMpvEventFileLoaded:
      file_loaded_ = true;
      core_idle_ = false;
      EmitState("ready");
      break;
    case kMpvEventPlaybackRestart:
      EmitState(paused_ ? "paused" : "playing");
      break;
    case kMpvEventPropertyChange: {
      const auto* property =
          static_cast<const MpvEventProperty*>(event->data);
      if (property == nullptr || property->name == nullptr ||
          property->data == nullptr) {
        break;
      }
      const std::string_view name(property->name);
      if (property->format == kMpvFormatDouble) {
        const double value = *static_cast<const double*>(property->data);
        if (std::isfinite(value) && value >= 0.0) {
          const double milliseconds = value * 1000.0;
          const int64_t normalized = static_cast<int64_t>(std::min(
              milliseconds,
              static_cast<double>(std::numeric_limits<int64_t>::max())));
          if (name == "time-pos") {
            position_ms_ = normalized;
          } else if (name == "demuxer-cache-time") {
            buffered_position_ms_ = std::max(position_ms_, normalized);
          }
        }
      } else if (property->format == kMpvFormatFlag) {
        const bool value = *static_cast<const int*>(property->data) != 0;
        if (name == "pause") {
          paused_ = value;
        } else if (name == "core-idle") {
          core_idle_ = value;
        }
      }
      if (file_loaded_) {
        EmitState(core_idle_ ? "buffering" : paused_ ? "paused" : "playing");
      }
      break;
    }
    case kMpvEventEndFile: {
      const auto* end_file = static_cast<const MpvEventEndFile*>(event->data);
      if (end_file != nullptr && end_file->reason == kMpvEndFileEof) {
        EmitState("ended");
      } else if (end_file != nullptr &&
                 end_file->reason == kMpvEndFileError) {
        EmitState("failed", "mpv_playback_failed");
      }
      break;
    }
    case kMpvEventQueueOverflow:
      EmitState("failed", "mpv_event_queue_overflow");
      break;
    case kMpvEventShutdown:
      Close(true);
      break;
    default:
      break;
  }
}

void MpvWindowsBridge::EmitState(const std::string& state,
                                 const std::string& error_code) {
  active_state_ = state;
  if (!event_sink_) {
    return;
  }
  EncodableMap event;
  event[EncodableValue("sequence")] = EncodableValue(event_sequence_++);
  event[EncodableValue("state")] = EncodableValue(state);
  event[EncodableValue("positionMs")] = EncodableValue(position_ms_);
  event[EncodableValue("bufferedPositionMs")] =
      EncodableValue(buffered_position_ms_);
  if (!active_session_id_.empty()) {
    event[EncodableValue("sessionId")] = EncodableValue(active_session_id_);
  }
  if (!error_code.empty()) {
    event[EncodableValue("errorCode")] = EncodableValue(error_code);
    event[EncodableValue("sessionExpired")] = EncodableValue(false);
  }
  event_sink_->Success(EncodableValue(event));
}

void MpvWindowsBridge::EmitIdle() {
  if (!event_sink_) {
    return;
  }
  EncodableMap event;
  event[EncodableValue("sequence")] = EncodableValue(event_sequence_++);
  event[EncodableValue("state")] = EncodableValue("idle");
  event[EncodableValue("positionMs")] = EncodableValue(int64_t{0});
  event[EncodableValue("bufferedPositionMs")] = EncodableValue(int64_t{0});
  event_sink_->Success(EncodableValue(event));
}

void MpvWindowsBridge::StartTimer() {
  if (!timer_active_ && host_window_ != nullptr) {
    timer_active_ = SetTimer(host_window_, kMpvPollTimerId,
                             kMpvPollIntervalMs, nullptr) != 0;
  }
}

void MpvWindowsBridge::StopTimer() {
  if (timer_active_ && host_window_ != nullptr) {
    KillTimer(host_window_, kMpvPollTimerId);
  }
  timer_active_ = false;
}
