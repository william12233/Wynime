# 來源插件共同煙霧測試

這份文件定義 Animeko fork 的來源插件人工驗收格式。測試應在實際 Android emulator 或桌面執行檔完成；fixture、HTTP 200、播放器頁載入或已建立 `MediaData` 都不能單獨算作播放成功。

## 測試輸入與判定

- Re:Zero 使用 `re0`；2RK 使用 `关于我转生变成史莱姆这档事`。
- 頻道與線路名稱直接採用網站初始回傳名稱，不由 host 重新命名。
- 每個來源都要核對搜尋結果、番劇、集數、網站列出的所有頻道，以及實際解析出的媒體格式。
- `PASS` 需要看到實際播放畫面，或在同一集留下正向播放進度與總時長；下載 `PASS` 需要完成證據，並在刪除後確認測試檔不存在。
- `BLOCKED` 只用於已確認是網站目前回應或受支援邊界造成、且不能透過正常請求修復的項目；可修復錯誤必須先診斷、修正、重跑。
- `UNVERIFIED` 表示證據不足，不能當成成功，也不能當成網站一定不可用。

## 固定來源範圍

| 插件 ID | 網站 | 搜尋輸入 |
| --- | --- | --- |
| `eacg` | https://eacg.net/ | `re0` |
| `dm1` | https://dm1.xfdm.pro/ | `re0` |
| `next` | https://next.xifanacg.com/ | `re0` |
| `girigiri` | https://ani.girigirilove.com/ | `re0` |
| `2rk` | https://www.2rk.cc/ | `关于我转生变成史莱姆这档事` |
| `dida` | https://www.didahd.pro/ | `re0` |
| `dmbus` | https://dmbus.cc/ | `re0` |

電影天堂／DYTTZY 不在索引、manifest 或已安裝來源範圍。

## 目前人工證據索引

下表只記錄目前工作區已存在且能追溯的證據；未列為 `PASS` 的來源仍須在下一輪人工煙霧測試中補齊，不能由其他來源的成功結果代替。

| 來源 | 播放 | 下載與清理 | 證據／備註 |
| --- | --- | --- | --- |
| `eacg` | `UNVERIFIED` | `UNVERIFIED` | `.tmp-rezero-eacg-2k.log`、`.tmp-rezero-eacg-edd.log` 有播放進度紀錄，但目前紀錄尚未把所選來源與該進度一一對應；另有 `.tmp-play-eacg-v105.log` 的播放器格式錯誤。 |
| `dm1` | `UNVERIFIED` | `UNVERIFIED` | 有 `.tmp-app-log-dmbus-latest.txt` 的 DM1 媒體請求紀錄，但仍需隔離成單一來源、單一集數的播放與下載證據。 |
| `next` | `UNVERIFIED` | `UNVERIFIED` | `.tmp-slime-next-playing.log` 有帶 referer 的短期 MP4 媒體請求，但尚缺同一操作的正向播放進度。 |
| `girigiri` | `UNVERIFIED` | `UNVERIFIED` | 目前只保留查詢／選集與播放器操作檔，尚缺可核對的正向播放進度。 |
| `2rk` | `BLOCKED` | `UNVERIFIED` | 網站 `c.js` 實際列出 `线路1`、`线路2`、`线路3`。目前播放鏈取得 `/saber` 的 31-byte 回應，ExoPlayer 報 `InvalidKeyException: Unsupported key size: 31 bytes`；不能把 HTTP 200 或建立 HLS proxy 當成播放成功。 |
| `dida` | `PASS` | `PASS` | `.tmp-dida-playing-all.log` 有正向 `positionMillis`／`durationMillis`；下載完成與刪除後證據為 `.tmp-dida-download-complete.xml`、`.tmp-dida-download-deleted.png`。 |
| `dmbus` | `PASS` | `PASS` | `.tmp-android-dmbus-slime-playing-fixed-logcat.txt` 有同一番劇的正向播放進度；下載完成、刪除與清理證據為 `.tmp-android-dmbus-download-finished-menu.xml`、`.tmp-android-dmbus-download-after-delete.xml`、`.tmp-android-dmbus-download-clean.png`。 |

### 2RK 線路記錄

2RK 的網站初始名稱保留為 `线路1`、`线路2`、`线路3`。插件從網站 `c.js` 動態取得三個 host，不應把不存在的線路加到 UI。只要站方金鑰回應仍是 31 bytes，三條線路都不能因為頁面載入成功而標示播放 `PASS`；應保留原始錯誤，等待站方回應恢復或在不解密、不修改站方金鑰的正常 WebView 流程中重新驗證。

## 圖標與商店 UI 證據

插件 manifest 的 `icon` 使用 HTTPS 網站圖標 URL，host 會驗證 URL 的 scheme、host 與固定 repository path。Android 實機畫面證據：`.tmp-icon-source-management.png` 與 `.tmp-icon-source-management-bottom.png`；UI 階層：`.tmp-icon-source-management.xml` 與 `.tmp-icon-source-management-bottom.xml`。兩張截圖可見七個來源的圖標、版本與網站 URL，包括 DMBUS。

## 清理規則

下載測試完成後，必須從應用程式下載頁刪除測試檔，再確認 UI 與檔案目錄都沒有殘留。工作區中的 `.tmp-*` 是驗證證據，不是下載媒體；不要把它們加入 Git，也不要用清理下載檔的步驟刪除這些證據。
