# 執行與除錯

Android 手機／平板使用 IDE 的 `app.android` 設定，或執行 `:app:android:installDefaultDebug`。模擬器需選擇對應 ABI。

Windows 可使用 `Run Desktop (Hot Reload)`、`Run Desktop (Normal)`，或執行正式 distributable：

```powershell
.\gradlew.bat :app:desktop:runReleaseDistributable
```

桌面 main class 為 `com.wynime.app.desktop.WynimeDesktop`。JBR 21 必須包含 JCEF，播放器使用既有 mpv／FFmpeg 原生元件。

Windows 使用既有 `Him188/Wynime` 資料與快取目錄。Android applicationId 維持 `com.wynime.app`。除錯及驗證不得清除既有使用者資料；效能比較使用正式建置。

播放器開發預覽位於 `app/dev-preview`；測試播放器相依僅用於預覽或測試源集。
