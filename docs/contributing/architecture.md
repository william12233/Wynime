# 專案架構

Wynime 的 Android 手機／平板與 Windows 入口共享 Kotlin 業務及 Compose UI。Bangumi 提供登入、搜尋、條目、收藏與追番同步；Wynime Cloud Worker 協調 OAuth、播放紀錄同步及取消收藏的驗證操作。

## 模組責任

| 模組 | 責任 |
| --- | --- |
| `app/android` | Android 入口及 APK |
| `app/desktop` | Windows 入口、JCEF、原生元件及 distributable |
| `app/shared/app-platform` | 平台契約、導航、權限及 BuildConfig |
| `app/shared/app-data` | 網路服務、Repository、Room、DataStore 與業務協調 |
| `app/shared/ui-*` | 依功能拆分的 UI 與 ViewModel |
| `app/shared/application` | Koin 組裝及應用程式初始化 |
| `app-models` | 共用 DTO 與穩定序列化契約 |
| `cloud-client` | 從 OpenAPI 生成的 Wynime Cloud 客戶端 |
| `datasource` | Bangumi、媒體來源抽象及媒體庫整合 |
| `source/plugin-api` | v3 外掛契約與 ABI 快照 |
| `source/plugins` | 八個來源、manifest、JVM／Dex 套件 |
| `app/dev-preview` | 播放器開發預覽與測試播放器相依 |
| `utils` | IO、HTTP、序列化、日誌與測試基礎工具 |

UI 使用 Domain／Repository 契約；Data 層協調網路與持久化；Platform 提供各平台 actual 實作。播放與下載共用來源解析及要求上下文，保留 Referer、User-Agent、Cookie、有效期限與原始播放頁。

## 收藏取消

App 建立帳號隔離的 Worker 操作並開啟 Bangumi 條目網頁。返回 App 後查詢遠端收藏；遠端仍存在、驗證失敗或帳號改變時保留真實收藏與待同步狀態。已認證查詢確認不存在後，才標示未收藏並清除操作。

## 相容性

Android applicationId、資料庫版本與檔名、Room 歷史 schema、DataStore 欄位、JSON discriminator、來源 ID、Windows 資料目錄與登入回呼保持可讀取既有資料。舊登入／合併路由可反序列化並轉至現有入口。

八個已知舊外掛按來源 ID 交易式遷移，驗證候選套件後才替換，保留啟用選擇與來源偏好。新安裝只從官方索引取得插件，App 內置套件只供既有安裝遷移；官方索引只發布每個插件的最新版。失敗保留舊安裝供重試；其他舊 ABI 套件不載入。
