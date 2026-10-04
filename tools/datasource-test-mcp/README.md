# wynime-source-plugin-media-test

這是 Wynime 的媒體能力測試 MCP 服務。它保留與桌面 App 相同的通用 Web 會話、Cookie、驗證碼與影片探測能力；網站搜尋、條目、線路、集數及播放頁解析由獨立的 source plugin 負責，不在這個工具內維護 CSS selector 或訂閱設定。

服務使用 MCP Streamable HTTP 的無狀態子集：JSON-RPC 以 POST 傳至 `/mcp`，回覆為
`application/json`。預設只綁定 `127.0.0.1`，並拒絕非本機 Origin。

## 工具

- `probe_video`：檢查影片 URL 的 HTTP 可達性，並可用 Animeko 桌面端同款 mpv 播放器實際播放；也可以擷取播放畫面。
- `detect_hls_ads`：下載並分析 HLS master/media playlist，套用 App 的 HLS 廣告過濾器，回報疑似廣告區段。

source plugin 的搜尋、條目詳情、網站實際列出的線路、集數、播放 URL 及下載驗證，位於
`D:\william\APP\Dev\wynime-sources` 的 fixture/live 測試與插件本身。測試時必須保留網站實際列出的線路名稱與數量，不能用共用 selector 引擎合併其他季或其他頁面的結果。

## 程式結構

- `mcp/`：HTTP MCP server、JSON-RPC 與工具註冊。
- `captcha/`：共用 WebSessionManager、Cookie/UA 同步與驗證碼處理。
- `resolver/`：通用播放頁 WebView/CEF 影片 URL 擷取。
- `video/`：HTTP、mpv 播放與 HLS 分析。
- 根套件：`Main.kt` 與 CEF 初始化入口。

## 人機驗證

頁面取用仍使用 App 的 `WebSessionManager` 與 `PageEvaluator`。被驗證碼擋住時，工具會嘗試共用的自動 solver；無法自動解決就終止該次探測，不會把驗證頁誤判成搜尋結果，也不會以替代搜尋詞掩蓋來源失敗。

## 播放標頭

source plugin resolver 回傳的 URL、Referer、User-Agent、Cookie 及其他要求標頭會一起交給播放器與下載器。`probe_video` 使用同一組標頭探測媒體，方便確認播放與下載是否走同一個來源上下文。

## 執行

```bash
./gradlew :tools:datasource-test-mcp:installDist
./tools/datasource-test-mcp/build/install/datasource-test-mcp/bin/datasource-test-mcp
```

建議使用包含 JCEF 的 JetBrains Runtime 21。預設服務位於
`http://127.0.0.1:8264/mcp`，可用 `--host <host>` 與 `--port <port>` 覆寫。

## 測試

```bash
./gradlew :tools:datasource-test-mcp:test
```
