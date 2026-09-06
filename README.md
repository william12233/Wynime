# Wynime

Wynime 是一個以 Android 與 Windows 為首要平台的跨平台動畫來源播放器。

核心目標：

- 多網站來源與多線路解析
- M3U8／MP4 穩定播放
- M3U8 廣告辨識、跳過與時間軸修復
- 背景下載、暫停續傳與可靠刪除
- 去廣告後優先無損封裝 MP4，不相容時回退 MKV
- Bangumi 放送資訊、收藏與已看集數同步
- Flutter 共用 UI，平台原生播放器與 WebView 整合

> 狀態：Phase 12 current-host audit complete；尚未 release。Android／Windows
> 原始碼、deterministic tests 與 build gates 已完成，但正式 Android signing、
> 原生依賴授權／provenance、FFmpeg 真實 remux、Windows 可觀察 UI 與硬體播放
> 證據仍未閉合。詳見 [`docs/PHASE12_STATUS.md`](docs/PHASE12_STATUS.md)。

完整計畫請見 [`docs/PROJECT_PLAN.md`](docs/PROJECT_PLAN.md)。
Release 產物、版本、簽名與發布流程請見 [`docs/release.md`](docs/release.md)。
