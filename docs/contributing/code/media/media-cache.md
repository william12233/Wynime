# 媒體快取

本文說明目前的媒體快取層。現行架構只處理 HTTP/HLS 媒體與本機檔案。

## 執行時元件

- `MediaCache`：描述一筆可播放的快取及其狀態。
- `MediaCacheStorage`：保存快取資料、檔案狀態與恢復資訊。
- `HttpMediaCacheEngine`：以 HTTP 串流來源建立及管理快取檔案。
- `HttpMediaCacheStorage`：將 HTTP 快取接到下載管理器與本機檔案系統。
- `MediaDownloadManager`：統一提供下載清單、暫停、恢復、刪除與進度查詢。

播放頁會在快取索引完成恢復後檢查目前劇集的完整快取。命中時直接以本機檔案載入播放器，略過來源查找；
快取缺少或無法播放時才啟用線上來源。快取儲存器不會註冊成 `MediaSource`，因此來源選擇不會再另外查詢同一批快取。

直接播放的快取媒體以 `MediaSourceKind.LocalCache` 表示；線上來源使用 `MediaSourceKind.WEB`。
播放器只需依 `ResourceLocation` 解析來源，不需要知道快取的內部實作。

## 資料庫相容性

目前資料庫版本的活動 DAO 映射現行快取資料。`WynimeDatabase` 保留刪除舊表的自動遷移宣告，
讓既有安裝可以安全升級；這些表名只存在於遷移與歷史 schema，不代表現行功能。
