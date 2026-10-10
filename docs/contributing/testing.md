# 自動化測試

```powershell
.\gradlew.bat check
```

`commonTest` 放跨平台契約與業務測試，`jvmTest` 放 JVM 共用測試，`desktopTest` 放 Windows JVM 實作測試。Android host tests 在 JDK 執行，Android device tests 使用連線裝置或模擬器。

需要重跑特定測試時使用該 test task 的 `--rerun-tasks`，保留既有驗證資料。

## UI

互動回歸優先使用 `utils/ui-testing` 的 `runWynimeComposeUiTest`，以 `performClick`、`performTextInput`、`sendKeyEvent` 驅動，搭配 tag 與 `assertScreenshot` 驗證。原生播放器、JCEF、封裝與登入回呼另以執行期驗證補足。

## 收藏取消

取消操作在 Bangumi 網頁完成。App 與 Worker 使用已認證收藏查詢確認紀錄不存在，才清除本機待同步操作。測試須涵蓋尚未取消、401／429／網路錯誤、操作到期、帳號切換、重試及真正不存在的情況；本機隱藏或 Worker 狀態不能單獨視為遠端取消證據。

## 外掛

來源外掛 API 為 v3，十個附帶來源提供 JVM／Dex 套件。測試涵蓋 SHA-256、entry point、API 契約、離線遷移、啟用狀態保留、損毀與替換失敗；未知舊 ABI 套件在 class loading 前標示不相容。搜尋測試同時覆蓋繁體原字形與簡體 fallback。

## Worker

在 `backend/wynime-bangumi-broker` 執行 `npm run typecheck` 與 `npm test`。整合測試涵蓋 OAuth session、帳號隔離、播放紀錄及收藏取消確認。
