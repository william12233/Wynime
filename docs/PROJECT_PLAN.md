# Wynime — 完整產品與技術計畫

版本：0.3  
狀態：專案長期單一事實來源（Source of Truth）  
優先平台：Android、Windows

## 1. 產品定位

Wynime 是不依賴磁力與 BT 的跨平台動畫來源播放器，核心目標如下：

1. 多網站搜尋、作品合併、集數與多線路解析。
2. 支援 HTML、JSON API、iframe 與 JavaScript 動態播放器。
3. 穩定播放 M3U8、MP4，必要時支援 DASH。
4. 自動辨識與跳過 M3U8 串流廣告。
5. 修復非標準 HLS 時間軸，改善快轉失敗、跳回 00:00 與續播錯位。
6. 背景下載、暫停續傳、URL 過期刷新與當機恢復。
7. 去廣告後優先無損封裝 MP4，不相容時回退 MKV。
8. 與 Bangumi 同步放送資訊、收藏狀態與已看集數。
9. Android 與 Windows 使用高一致性 Flutter UI，但各平台採用最合適的原生播放器與 WebView。

## 2. 明確排除

- 磁力連結、BT、做種與上傳。
- 破解 DRM、繞過付費授權或合法存取限制。
- 將所有網站假設成可完全自動解析。

## 3. 核准技術方向

- UI：Flutter。
- Android 原生整合：Kotlin。
- Android 主播放器：Media3 ExoPlayer。
- Android 相容性回退：libmpv。
- Windows 主播放器：libmpv。
- Windows 動態網站解析：WebView2。
- 播放、下載與去廣告共用同一份 `PlaybackSession`。
- 下載與刪除共用同一份 `DownloadArtifactManifest`。
- FFmpeg 預設使用 stream copy 進行無損 MP4 remux；失敗時回退 MKV。
- Rust 串流／下載核心需先通過 Android、Windows FFI 與當機恢復原型 Gate，未通過則回退 Dart 控制層加平台原生實作。

## 4. 不可退讓的工程規則

1. 播放器與下載器不得各自重新解析不同 Headers、Cookie 或 Token。
2. 去廣告只有一份權威 `AdRemovalPlan`。
3. 廣告計畫必須綁定來源、線路、集數與 Manifest 指紋，不得只綁作品與集數。
4. 下載與刪除使用同一個 Artifact Manifest；刪除時禁止重新猜路徑。
5. 刪除完成前必須驗證所有登記檔案均不存在；中途當機可恢復。
6. 未簽章來源允許安裝，但網域白名單、權限聲明、執行沙箱與資源限制為強制。
7. Android 與 Windows 不強迫使用同一播放器引擎。
8. 401／403 應重新解析工作階段，不得誤判為解碼器錯誤。
9. 所有主要 UI 必須經固定尺寸實機／模擬器截圖與 Golden tests。
10. 遙測預設關閉；診斷日誌必須脫敏。

## 5. 來源系統

### 分層解析

1. CMS／模板自動辨識。
2. CSS、XPath、JSONPath、正則等規則來源。
3. WebView 動態解析、XHR／fetch／iframe 與媒體請求攔截。
4. 特殊網站程式化 Adapter。

### 來源安全

- 簽章為選用；簽章來源顯示作者驗證。
- 所有來源必須宣告網域白名單與權限。
- 限制 CPU 時間、記憶體、請求數、回應大小、重新導向與併發。
- 更新若新增網域或權限，必須重新取得使用者同意。

## 6. 播放與 HLS

`PlaybackSession` 至少保存：來源、線路、集數、播放頁、媒體 URL、本機清理後 Manifest、Headers、Cookies、Referer、Origin、User-Agent、到期時間、刷新回呼、字幕、音軌、去廣告計畫與時間映射。

### 引擎策略

- Android：Media3 → libmpv → 原站 WebView。
- Windows：libmpv → 原站 WebView2。
- 切換引擎需保留播放位置、音量、倍速、字幕、音軌與去廣告時間映射。

### 去廣告

原始 M3U8 經 Manifest parser、fingerprint、廣告偵測、時間軸修復後，由本機 HLS Proxy 提供乾淨 M3U8，播放器與下載器共用。

模式：關閉、安全、智慧、激進。

`EXT-X-DISCONTINUITY` 僅是輔助訊號，不可單獨判定廣告。

## 7. 下載與刪除

流程：解析 → 鎖定 Manifest snapshot → 建立 Artifact Manifest → 只下載保留分片 → 解密／驗證 → 時間軸修復 → MP4 remux → MKV fallback → 完成驗證。

每個 Artifact Manifest 登記：最終影片、暫存分片、M3U8 快照、字幕、音軌、封面、去廣告計畫、時間映射、FFmpeg 暫存、日誌與恢復狀態。

刪除流程：DB 標記 `DELETING` → 建立持久化 DeleteJob → canonical containment 與 symlink／junction 防護 → 按 Manifest 逐檔刪除 → 驗證不存在 → 完成 DB 交易。失敗時標記 `DELETE_FAILED`，不得假裝成功。

## 8. Bangumi

首版功能：登入、每日放送、作品與集數資料、收藏狀態、已看集數、離線同步佇列、失敗重試、條目映射修正與衝突處理。

Bangumi 保存收藏與已看集數；Wynime 本機保存精確播放秒數、來源、線路、播放器引擎、去廣告時間映射與下載狀態。

## 9. 多語言與字體

- Flutter `gen_l10n` + ARB：繁中、簡中、日文、英文。
- 介面語言與番名顯示語言分離。
- 番名資料保存 original、zh-Hant、zh-Hans、ja、en 與 aliases。
- 正式字體尚待 Android／Windows 混排視覺測試後鎖定；候選為 Noto Sans CJK Full Variable、Source Han Sans Variable、Sarasa Gothic UI。

## 10. 響應式 UI

- Compact：小於 600 logical px。
- Medium：600–1023。
- Expanded：1024 以上。

共用元件但採不同頁面骨架。固定驗收尺寸：Android 360×800、412×915；Windows 1024×768、1440×900。

## 11. 設定中心

### 外觀
主題、強調色、介面密度、卡片大小、動畫、首頁模組、UI 縮放、Windows 視窗記憶。

### 語言與名稱
介面語言、番名偏好、副標題名稱、集數名稱格式、搜尋別名、日期時間格式。

### 播放
預設引擎、自動回退、硬體解碼、畫質、緩衝、預載、倍速、音量、下一集、快轉秒數、手勢、畫中畫、字幕、音軌、失敗換線與診斷。

### 去廣告
模式、播放時啟用、下載時啟用、結構分析、內容辨識、信心門檻、移除區段預覽、單集暫停與診斷匯出。

### 下載
目錄、畫質、音軌、字幕、輸出格式、Wi-Fi only、充電時下載、低電量暫停、並行數、速度限制、重試、Token 刷新、完成驗證、暫存清理、刪除方式、孤兒檔掃描與空間警告。

### 來源
啟停、優先順序、自動換源、並行數、請求間隔、429 退避、Desktop UA、Cookie、更新、安全警告、權限、白名單、資源限制、健康檢查、自動建立器與診斷。

### Bangumi
帳號連結、自動同步、啟動同步、播放完成同步、收藏同步、衝突策略、待同步數量與日誌。

### 隱私與進階
清除紀錄／Cookie／日誌、日誌脫敏、資料匯出與刪除、Crash report、Telemetry、HLS Proxy、Manifest 檢視、FFmpeg／MPV／Media3 進階設定與 DB 維護。

## 12. 開發階段

- Phase 0：Repository bootstrap、Flutter Android／Windows、四語言、App shell、Design Tokens、核心介面、CI 與測試骨架。
- Phase 1：Domain、SQLite／Drift、設定、觀看紀錄、Artifact Manifest、DeleteJob。
- Phase 2：來源規則引擎、安全模型、Fixture tests。
- Phase 3：Android WebView、Windows WebView2、Desktop UA、Cookie 與媒體攔截。
- Phase 4：PlaybackSession、本機 HLS Proxy、Media3 與錯誤分類。
- Phase 5：HLS parser、fingerprint、安全去廣告與時間映射。
- Phase 6：Windows mpv、Android mpv 原型與引擎切換。
- Phase 7：下載、續傳、AES-128、URL 刷新與恢復。
- Phase 8：FFmpeg remux、MKV fallback、驗證、權威刪除與孤兒掃描。
- Phase 9：Bangumi。
- Phase 10：自動來源建立器。
- Phase 11：完整 UI 與 Golden baseline。
- Phase 12：效能、安全、授權、安裝包與發行。

## 13. Phase 0 Gate

必須通過：Android build、Windows build、format／analyze、unit tests、Golden smoke test。Phase 0 不接真實網站、不接真實播放器、不執行真實下載，也不接正式 Bangumi OAuth。