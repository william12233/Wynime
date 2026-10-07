# 開發環境

使用 Android Studio 或 IntelliJ IDEA 匯入 Gradle 專案。Windows 桌面端需使用包含 JCEF、jmods 與 jpackage 的 JetBrains Runtime 21；Gradle toolchain 設定採用 JBR 21。

Android SDK 版本以根目錄 `gradle.properties` 的 `android.compile.sdk` 為準，並安裝對應 platform、build-tools 與 platform-tools。

```powershell
git clone --recursive https://github.com/william12233/Wynime.git
git config core.autocrlf false
git config core.eol lf
git config core.filemode false
```

在 `local.properties` 設定 `sdk.dir` 與所需的 `wynime.android.abis`。正式簽章資料使用既有本機簽章檔，保留在版本控制之外。

目標平台為 Android 手機／平板及 Windows。共享源集包含 `commonMain`、`jvmMain`、`androidMain`、`desktopMain`；輔助 `skikoMain` 保留供桌面 Compose 程式使用。

建置命令見 [建置與打包](building.md)，執行命令見 [執行與除錯](running.md)。
