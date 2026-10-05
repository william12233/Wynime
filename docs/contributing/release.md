# 正式發行

正式版本由 `.github/workflows/src.main.kts` 產生的 `release.yml` 發布。Release tag 使用 `vX.Y.Z`；tag 去除前綴 `v` 後，會套用到所有正式 artifact 名稱。

## 公開 artifact allowlist

每個正式 release 只包含兩個 GitHub Release asset：

```text
wynime-<version>-arm64-v8a.apk
wynime-<version>-windows-x86_64.zip
```

Android TV、其他 Android ABI、其他作業系統、額外 QR asset 與其他命名都不屬於正式 release。workflow generator 會驗證 release matrix；release 結束後的 verification job 會再次驗證 GitHub Release asset 清單、APK ABI、APK 簽章與 Windows ZIP 完整性。

## 正式 build 與簽章

- Android release job 只 build `arm64-v8a`，並使用 Gradle release signing 設定。
- `SIGNING_RELEASE_STOREFILE`、`SIGNING_RELEASE_STOREPASSWORD`、`SIGNING_RELEASE_KEYALIAS` 與 `SIGNING_RELEASE_KEYPASSWORD` 都是必要的 release secrets。簽章 key 缺少或無法解碼時，workflow 直接失敗。
- Android upload task 只接受 `*-release.apk`，拒絕 `*-release-unsigned.apk`；上傳前會以 Android SDK `apksigner verify --verbose` 驗證簽章。
- Windows asset 必須由 `main-release` desktop distributable 打包，名稱固定為 `windows-x86_64.zip`。
- GitHub Release 的最後 verification job 會下載兩個 asset 重新驗證，任何多餘、缺少、錯名或驗證失敗都會使 release workflow 失敗。

一般 Build workflow 可以產生 debug 與測試 artifact；正式 Release workflow 僅產生上述兩個正式發行 artifact。

## 版本與檢查

修改 release matrix、artifact naming 或 signing 流程時，同步修改 `.github/workflows/src.main.kts` 與生成的 `.github/workflows/release.yml`。Deterministic tests 位於 `build-logic/src/test/kotlin/ReleaseArtifactNamesTest.kt`，至少執行：

```powershell
.\gradlew.bat :build-logic:test --no-configuration-cache --no-daemon
.\gradlew.bat :ci-helper:compileKotlin --no-configuration-cache --no-daemon
```

正式 release 只接受 `release.yml` 的 verification job 成功結果；本機成功 build 不代表公開 asset 已通過簽章與 allowlist 驗證。
