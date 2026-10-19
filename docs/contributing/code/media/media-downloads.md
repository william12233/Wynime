# 媒體下載

下載流程由 `AddDownloadUseCase`、`DownloadRequestSession` 與 `MediaDownloadManager` 組成：

1. 播放頁或下載頁建立含劇集與 `Media` 的下載請求。
2. `DownloadRequestSession` 取得選源結果，讓使用者確認要下載的劇集。
3. `MediaDownloadManager` 將請求交給支援該 `ResourceLocation` 的快取儲存。
4. `MediaDownload` 暴露暫停、恢復、刪除與進度快照給 UI。

目前可用的下載引擎是 HTTP/HLS 媒體快取引擎；專案只註冊目前支援的網路媒體快取元件。
下載頁仍可處理一般網路媒體與本地快取的狀態，並透過 `MediaCacheEngineKey.WebM3u` 識別 HTTP/HLS 快取。

當設定中的「過濾貼片廣告（實驗性）」開啟時，HLS 下載器會在建立下載片段前套用 `HlsManifestFilter`。這只適用於以獨立片段插入廣告的已完成播放清單；已存在的快取不會被重新處理，需刪除後重新下載。

選源與候選排序請參閱 [選源](media-selector.md)，底層快取請參閱 [媒體快取](media-cache.md)。
