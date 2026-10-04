# 來源插件架構

## 範圍

影片來源是由第一方插件索引管理的可執行插件。主程式只負責插件生命週期、來源註冊、媒體解析、播放與下載；網站解析規則與網站 session 狀態由各插件封裝。

目前索引中的線上動漫來源為 `eacg`、`dm1`、`next`、`girigiri`、`2rk`、`dida`、`dmbus` 七個插件。`dyttzy` 不在索引，也不屬於主程式的線上來源範圍。其他本地檔案、Jellyfin、Emby 與 Ikaros 來源仍由既有通用來源框架提供。

## 資料流

```text
官方 GitHub index.json
        │  schema/API/HTTPS/path 驗證
        ▼
插件商店 UI
        │  manifest、平台、版本、SHA-256
        ▼
staging 解壓 → loadable artifact 驗證 → 原子移入 installed
        │
        ▼
SourcePluginRegistry
        │  每個插件獨立 context、HTTP client、cookie jar、session
        ▼
MediaSourceManager.allInstances
        │
        ├─ 搜尋／詳情／頻道／集數
        └─ SourcePluginMedia（穩定 subject/channel/episode 身分）
                │ 使用時重新 resolve
                ▼
SourcePluginMediaResolver
        ├─ WEB → 通用 WebVideo resolver
        └─ HLS/MP4/UNKNOWN → HttpStreamingMediaDataProvider
                                      │
                                      ├─ 播放器
                                      └─ 下載器
```

插件不得持有 UI、播放器或下載器實例，也不得把短期媒體網址當成長期資料庫身分。`SourceMediaIdentity` 由插件 ID、網站 subject ID、頻道 ID 與集數 ID 組成；短期網址在播放或下載開始時重新取得。

## 插件 API

插件只依賴 `source/plugin-api`。核心型別包括：

- `SourceSubject`、`SourceChannel`、`SourceEpisode`、`SourceSubjectDetails`：網站內容模型。
- `SourcePluginMetadata`：顯示名稱、版本、網站、圖示、支援平台與最低主程式版本。
- `SourcePluginContext`：插件 ID、主程式版本、平台、受限 HTTP client 與 logger。
- `ResolvedMedia`：媒體網址、格式、原始頁面、過期時間，以及 headers/cookies/referrer/origin。
- `SourceWebResourceMatch`：只觀察一般 WebView 請求；插件不可解密 DRM、繞過驗證或自行製造網站未請求的網址。

來源頻道名稱直接使用網站初始名稱，不在主程式中重新命名。插件解析失敗時回傳可診斷的錯誤，不得讓單一插件載入失敗造成主程式啟動失敗。

## 商店與安裝安全

商店固定使用主專案 `william12233/Wynime` 的 `main/source/plugins/index.json`。索引與 manifest 必須通過下列檢查：

- schema、plugin API、插件 ID、版本、平台與重複 ID。
- manifest 路徑與 artifact 路徑只能位於固定 repository；絕對網址必須是 HTTPS 且與 repository host 相同。
- website、icon 與宣告的 artifact 必須使用 HTTPS；artifact 必須有 64 字元 SHA-256。
- artifact 先下載至 staging，再驗證 hash、解壓縮、檢查可載入檔案與插件 metadata，最後以目錄移動完成提交。
- ZIP/JAR 解壓縮拒絕 path traversal；插件 ID、版本與所有儲存路徑元件都只能是單一路徑元件。
- 安裝、更新、載入或驗證任一步失敗都清理 staging 與新提交目錄，既有可用版本保持在登錄庫中。
- 啟用、停用、更新、解除安裝與索引離線快取都由主程式控制；不支援的 API、平台或最低版本只顯示錯誤狀態。

安裝器會先載入候選 artifact 驗證 entry class 與 metadata，再寫入 `InstalledSourcePlugins`。插件載入器使用唯讀 artifact；Desktop 使用獨立 `URLClassLoader`，Android 使用 `DexClassLoader`，iOS 僅保持編譯相容並回報不支援執行。

## Session 與媒體請求上下文

每個插件 ID 都有獨立的 HTTP client、cookie jar、User-Agent 與 session 範圍。`ResolvedMedia.requestHeaders()` 會合併顯式 headers 與 cookies、Referer、Origin；同一份上下文同時交給播放與下載。

HLS 解析器對 master playlist、variant/media playlist、分片、`EXT-X-KEY` 與 init segment 沿用解析結果的 headers。插件不得把 cookie 或短期 token 寫入日誌、manifest、索引或 UI。

## 來源網站實作邊界

七個插件各自持有搜尋、詳情、頻道、集數、播放器頁與媒體解析邏輯，並以 fixture 與合約測試固定輸入輸出。正常的 iframe、HLS、JSON API、播放器頁與網站下游 CDN 都可依網站公開請求流程解析；需要 DRM 解密、CAPTCHA 繞過、TLS 驗證關閉或猜測／修改站方金鑰的情況必須回報 `BLOCKED`，不可在插件中繞過。

共通人工驗證使用 `re0`。2RK 不提供 `re0` 時使用 `关于我转生变成史莱姆这档事`；來源頻道顯示網站原始名稱。下載驗證只需每個來源一部集數，完成後刪除測試檔案，不把測試媒體留在裝置或工作區。

## 測試層級

1. API common tests 固定穩定身分、媒體上下文與序列化。
2. 主程式 common tests 驗證索引／manifest 驗證、ETag 快取、登錄庫啟用狀態與儲存路徑邊界。
3. 各插件 fixture tests 驗證搜尋、詳情、頻道、集數、播放器頁與媒體 URL 解析。
4. Desktop loader test 使用實際插件 JAR；Android integration test 使用實際 DEX artifact。
5. 真機／模擬器與 Desktop smoke test 驗證每個啟用來源的搜尋、播放、單集下載與測試檔清理。

人工驗證報告必須區分 `PASS`、`BLOCKED` 與 `UNVERIFIED`。HTTP 200 或播放器頁載入本身不等於播放成功；播放必須有實際畫面或進度，下載必須有完成證據，且刪除後確認檔案不存在。
