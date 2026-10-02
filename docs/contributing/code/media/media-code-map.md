# Media 程式碼索引

這份索引對應目前的「資料源 → 選源 → 播放／快取」流程。

## 資料源與選源

- `datasource/api/.../source/MediaSource.kt`：資料源介面與查詢結果。
- `datasource/api/.../source/MediaSourceKind.kt`：目前只有 `WEB` 與 `LocalCache`。
- `app/shared/app-data/.../domain/mediasource/`：資料源訂閱、實例與連線管理。
- `app/shared/app-data/.../domain/media/fetch/`：`MediaFetcher`、`MediaFetchSession` 與來源查詢狀態。
- `app/shared/app-data/.../domain/media/selector/`：候選過濾、排序、偏好與自動選擇。
- `app/shared/ui-mediaselect/`：共用媒體選擇器 UI。

## 播放與快取

- `app/shared/app-data/.../domain/media/resolver/`：將 `ResourceLocation` 解析成播放器可用的媒體。
- `app/shared/app-data/.../domain/media/cache/`：`MediaCache`、HTTP 快取引擎與儲存層。
- `app/shared/app-data/.../domain/media/download/`：下載工作階段、下載管理器與操作。
- `app/shared/video-player/`：播放器控制器、載入狀態與媒體播放整合。
- `app/shared/ui-episode/`：劇集頁、播放頁與播放器周邊 UI。

## 平台入口

- `app/android/`：Android 啟動器與平台 DI。
- `app/desktop/`：桌面啟動器與 Compose Desktop 打包設定。
- `app/ios/`：iOS 平台整合。

修改媒體流程時，請先確認資料源回傳的 `Media`、選源結果與播放器 resolver 的邊界，
再檢查 Android、桌面與 iOS 的平台 DI 是否仍只註冊目前支援的元件。
