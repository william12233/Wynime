# MediaSource

`MediaSource` 是 `Media` 的提供者。它以條目為單位接收 `MediaFetchRequest`，回傳來源實際找到的媒體；劇集篩選與候選排序由 `MediaSelector` 負責。

```kotlin
interface MediaSource {
    suspend fun fetch(query: MediaFetchRequest): SizedSource<MediaMatch>
}
```

## 現行來源分層

- **Source plugin**：以獨立插件封裝網站專用的搜尋、條目詳情、線路、集數、播放頁與媒體解析。插件回傳網站實際列出的來源名稱與數量，不跨季合併結果。
- **Jellyfin / Emby / Ikaros**：保留既有媒體庫連線，透過 App 內建的 `MediaSourceManager` 建立。
- **本機與快取來源**：處理本機媒體、下載內容及已解析媒體的重用。
- **Bangumi / Wynime API**：提供條目與劇集元資料，不負責網站播放媒體的解析。

網站來源的主要入口是 source plugin bridge。它先以搜尋結果選定條目，再讀取該條目頁的完整線路與集數，最後在播放或下載時動態解析媒體 URL。每次解析都保留 URL、Referer、User-Agent、Cookie、原始播放頁及有效期限等要求上下文。

## Source plugin bridge

插件與 App 之間的邊界由 `SourcePluginMediaSource`、`SourcePluginMediaResolver` 及插件 API 定義：

1. plugin `search` 回傳可選條目。
2. `getSubject` 只保留被選條目詳情頁實際列出的 channel/line。
3. `fetch` 依該條目與集數建立穩定的 `SourceMediaIdentity`。
4. 播放器或下載器要求媒體時，resolver 才呼叫 plugin 動態解析。
5. 解析得到的要求標頭由播放與下載兩條路徑共用。

這個邊界使網站短期 URL、Cookie、Referer 與登入/驗證會話不會被錯誤快取成永久媒體 URL，也避免用另一季或另一個列表頁的同名結果填補目前條目。

## 選源與播放

`MediaSelector` 保留簡單模式與詳細模式。簡單模式顯示每個來源目前可用的代表性線路；詳細模式顯示解析後的候選媒體、畫質、語言、線路及被排除的候選。兩種模式都使用 plugin 回傳的實際來源資料，不再顯示 BT 欄位。

播放與下載都透過同一個 `SourcePluginMediaResolver` 取得 `UriMediaData`。`UriMediaData.headers` 會傳給播放器與下載器，確保需要 Referer、User-Agent 或 Cookie 的來源不會只在其中一條路徑成功。

## 新增來源

一般網站來源應建立 source plugin，並在插件內補上：

- 搜尋詞與網站名稱的實際匹配規則；
- 條目詳情頁的完整 channel/line 解析；
- 集數與播放頁的動態解析；
- 解析時需要的要求標頭、Cookie 與驗證上下文；
- fixture 測試，以及可執行的 live 搜尋/播放/下載驗證。

網站被 Cloudflare、圖片驗證碼或其他 WAF 擋住時，使用共用 `WebSessionManager`；不要在 plugin 內複製另一套 Cookie、UA 或驗證碼流程，也不要繞過加密金鑰或 DRM。
