# 建置與打包

Wynime 的目標平台為 Android 手機／平板與 Windows。Windows distributable 需在 Windows 使用包含 JCEF 與 jpackage 的 JBR 21 建置。Android CI 可以在 Linux 主機執行。

## 本機設定

在不納入 Git 的 `local.properties` 指定 Android SDK。正式簽章沿用既有設定，不將金鑰或密碼寫入原始碼。

```properties
sdk.dir=D:\william\APP\DevTools\Android\Sdk
wynime.android.abis=arm64-v8a
```

`wynime.android.abis=x86_64` 適用於模擬器，`all` 可建置完整 ABI 集合。既有 `ani.*` 本機設定由 Gradle convention 相容讀取。

## Android

```powershell
.\gradlew.bat :app:android:assembleDefaultDebug
.\gradlew.bat :app:android:assembleDefaultRelease
```

產物位於 `app/android/build/outputs/apk/default/`。正式 APK 的 applicationId 為 `com.wynime.app`；發布更新的 versionCode 必須高於上一個公開 APK。

## Windows

```powershell
.\gradlew.bat :app:desktop:createReleaseDistributable
```

產物位於 `app/desktop/build/compose/binaries/main-release/app/Wynime/`。發布 ZIP 包含 `Wynime/Wynime.exe`、所需 runtime、原生函式庫及授權資料。

## 自動化驗證

```powershell
.\gradlew.bat check
```

`check` 執行 JVM、Android host、ABI 快照及外掛產物一致性檢查。Android device tests 由 `connectedCheck` 執行。播放器的開發預覽位於 `app/dev-preview`。

## 生成原始碼

Wynime Cloud 的規格位於 `backend/wynime-bangumi-broker/openapi.json`，以 `:cloud-client:generateOpenApiForWynimeCloud` 生成客戶端。Bangumi 客戶端與 BBCode grammar 沿用各模組生成任務。生成後的 Kotlin 由詞法清理任務移除註解，編譯任務在清理後執行。
