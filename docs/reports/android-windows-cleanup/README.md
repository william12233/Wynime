# Wynime 1.0.9 清理與相容性報告

基準為 `main` 的 `443e1248c42542321417ecce70fec035c678ba46`，公開前版為 v1.0.8。Android applicationId 為 `com.wynime.app`；此版 versionCode 為 5，前版實際 APK 為 4。

## 平台與責任

應用程式支援 Android 手機／平板與 Windows。Apple、Linux 桌面與 Android TV 的入口、專用源集、資源及打包分支已移除。Linux CI 主機的 Android 建置工具及通用程式碼協作工作流程仍保留；它們不建立 Linux 應用程式。`tv.xml` 中仍供手機使用的共用播放器動作資源保留。

Animeko 帳號、電子郵件／QR 登入、開發者認證、資料合併及 HTTP 服務依賴已移除。Bangumi 負責登入、首頁、搜尋、收藏與追番；Wynime Cloud 負責播放紀錄及取消收藏操作的建立、查詢和確認。共用 DTO 位於 `app-models`，HTTP 客戶端位於依 OpenAPI 生成的 `cloud-client`。

播放器開發預覽位於 `app/dev-preview`。`mediamp.test` 及 UI 測試相依屬於測試／預覽用途；Windows 正式相依排除播放器傳遞引入的 `ui-test-junit4`。播放器、資料庫、同步衝突演算法與主要函式庫版本維持既有設計。

## 取消收藏的真實狀態

App 建立帳號隔離的 Worker 操作並開啟 Bangumi 網頁。使用者在網頁取消收藏後，返回 App 會查詢已認證的遠端收藏。只有收藏查詢回傳不存在，且帳號及待同步操作仍相同，才清除本機待同步操作。仍有收藏、驗證失敗、登入失效、帳號切換或中途取消均保留待處理狀態。

Worker 不呼叫不存在於官方規格的 OAuth 收藏刪除端點。其確認程序核對 `/v0/me` 與 session 帳號，再查詢收藏；Worker 狀態本身不代表收藏已刪除。使用者指定的測試條目 701779 未被本次發布流程操作。

## 舊資料與外掛

- Room 資料庫檔名 `ani_room_database_main.db`、版本 31、schema identity 與全部 31 份 schema 內容雜湊保留。類別名稱及 schema 目錄使用 `com.wynime`。
- Windows 資料目錄、`ani://bangumi-oauth-callback`、已持久化的 polymorphic SerialName、舊登入路由、Jellyfin device ID 與跨版本單例 mutex 保留。舊登入路由導向現有 Bangumi／設定介面。
- 開發屬性使用 `wynime.*`，仍接受已有本機設定的 `ani.*` 別名；簽章及使用者資料不作清除。
- 外掛 API 為 v3；七個來源為 eacg、dm1、next、girigiri、2rk、dida、dmbus，套件版本 1.0.26。主程式附帶 JVM／Dex 遷移套件及 manifest、SHA-256。
- 遷移以來源 ID 保留啟用狀態及偏好。候選套件完成雜湊、manifest 及 JVM 載入驗證才替換舊安裝；失敗時保留舊檔與紀錄供重試。其他 API v2 外掛標示不相容，避免載入。

## 註解與授權

Kotlin 使用官方 KotlinLexer；TypeScript 使用 TypeScript AST 與 scanner；XML、YAML、TOML、Python、PowerShell 使用對應解析器／token；Java、Shell、Shader、ANTLR 使用詞法 token。移除前後比較非註解 token 或設定結構，保留字串、URL、正規表示式、shebang 與 shader 指令。

掃描涵蓋納入版本控制的原始碼、設定及腳本，以及此次新增檔案。二進位、建置產物、既有 `.tmp*` 驗證資料、受保護的 `.agents`／`.claude` 工具指引與授權檔不屬於移除範圍。MPV 的 HOOK、BIND、SAVE 等執行指令以 shader properties 設定保存，建置時輸出播放器要求的指令語法；20 份 shader 的程式 token 與清理前相同，540 個執行指令依順序雜湊驗證。一般 Markdown 文字與 Python docstring 為文件／字串。Cloud OpenAPI、Bangumi OpenAPI 與 BBCode 生成流程執行註解清除。

作者、年份及原始聲明儲存在 `licenses/source-notices`；`license-source-index.json` 提供聲明與原檔案對照。第三方套件座標、來源網站 URL、Anime4K 名稱與法定授權文字保留。

## 驗證與產物

完整自動檢查、正式建置、簽章、大小與公開資產驗證結果記錄於 `verification.json`。實機、模擬器互動、原生播放器執行及真實 Bangumi 收藏刪除由使用者測試，依本次發布授權豁免；自動測試結果不代替這些執行證據。

Worker TypeScript 型別檢查及 19 項測試通過，涵蓋 OAuth、播放紀錄、session 隔離、取消操作、過期、失敗與重試。正式 Worker 已部署，未認證的取消操作端點回傳 401。

清理明細、來源路徑遷移、資源名稱、schema 雜湊及授權對照以本目錄 JSON 檔保存。`baseline-missing-paths.json` 同時包含移除及搬移前的路徑，應搭配 `source-path-migrations.json` 判讀。
