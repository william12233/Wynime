# MediaSource

`MediaSource` 是 `Media` 的提供者。它以條目為單位接收 `MediaFetchRequest`，回傳該來源能找到的所有媒體；
目前劇集篩選與候選排序由 `MediaSelector` 負責。

```kotlin
interface MediaSource {
    suspend fun fetch(query: MediaFetchRequest): SizedSource<MediaMatch>
}
```

## 現行資料源

- `SelectorMediaSource`：依 CSS Selector、正則表達式與 JSON 設定從網頁提取條目、線路、劇集及播放連結。
- `JellyfinMediaSource`／`EmbyMediaSource`：連接 Jellyfin 或 Emby 媒體庫。
- `IkarosMediaSource`：連接 Ikaros 媒體庫。
- `Bangumi` 與 Ani API：提供條目、劇集與同步所需的資料。

來源架構目前以網頁與媒體庫為主；新的來源應實作目前的 `MediaSource` 介面，並以
`MediaSourceKind.WEB` 回傳可播放的 HTTP/HLS 媒體。

## SelectorMediaSource

`SelectorMediaSource` 依設定分成兩個階段：

1. 列表模式搜尋條目、開啟條目頁、列出線路與劇集，並從播放頁取出 `ResourceLocation`。
2. 自動匹配模式依條目名稱、集數、線路與 `MediaSourceTier` 篩選目前劇集的候選。

列表模式保留手動選集能力；自動匹配由 `MediaFetcher` 與 `MediaSelector` 驅動。每個 `Media` 的
`mediaId` 必須穩定，`episodeRange` 必須描述它實際包含的劇集，避免切集或下載重複查詢。

來源的 `tier` 數值越低代表優先度越高；`channelTiers` 可以覆寫個別線路的優先度。這些設定只影響
候選排序與快速選擇，不改變播放器或快取層。

## 訂閱與擴充

`MediaSourceSubscription` 儲存來源訂閱的啟用狀態與更新資訊。訂閱更新後，來源由
`MediaSourceManager` 建立實例並提供給 `MediaFetcher`。

要新增一般網頁來源，優先建立 `SelectorMediaSource` 設定；只有需要特殊 API 或媒體庫協定時，才在
`datasource/` 新增專用實作，並補上對應的連線與選源測試。
