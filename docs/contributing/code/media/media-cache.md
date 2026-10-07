# 媒體快取

本文說明目前的媒體快取層。現行架構只處理 HTTP/HLS 媒體與本機檔案。

## 執行時元件

- `MediaCache`：描述一筆可播放的快取及其狀態。
- `MediaCacheStorage`：保存快取資料、檔案狀態與恢復資訊。
- `HttpMediaCacheEngine`：以 HTTP 串流來源建立及管理快取檔案。
- `HttpMediaCacheStorage`：將 HTTP 快取接到下載管理器與本機檔案系統。
- `MediaDownloadManager`：統一提供下載清單、暫停、恢復、刪除與進度查詢。

快取媒體在選源時以 `MediaSourceKind.LocalCache` 表示；線上來源則使用
`MediaSourceKind.WEB`。播放器只需依 `ResourceLocation` 解析來源，不需要知道快取的內部實作。

## 資料庫相容性

目前資料庫版本的活動 DAO 映射現行快取資料。`WynimeDatabase` 保留刪除舊表的自動遷移宣告，
讓既有安裝可以安全升級；這些表名只存在於遷移與歷史 schema，不代表現行功能。
