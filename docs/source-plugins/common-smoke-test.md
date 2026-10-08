# 來源插件共同煙霧測試

這份文件定義 Wynime fork 的來源插件人工驗收格式。測試應在實際 Android emulator 或桌面執行檔完成；fixture、HTTP 200、播放器頁載入或已建立 `MediaData` 都不能單獨算作播放成功。

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
| `girigiri` | https://Wynime.girigirilove.com/ | `re0` |
| `2rk` | https://www.2rk.cc/ | `关于我转生变成史莱姆这档事` |
| `dida` | https://www.didahd.pro/ | `re0` |
| `dmbus` | https://dmbus.cc/ | `re0` |
| `dyttzy` | https://caiji.dyttzyapi.com/ | `哪吒之魔童鬧海`（只驗證 `dyttm3u8` 直接 HLS） |

DYTTZY 的 `dytt` 分享網址、`vod_down_url` 與非 HTTPS HLS 項目不納入播放或下載；API 搜尋／詳情可作為解析證據，但不能代替實際播放與下載證據。

## 目前人工證據索引

下表只記錄目前工作區已存在且能追溯的證據。播放 `PASS` 必須能在截圖或同一操作的播放器狀態中辨識實際畫面；下載 `PASS` 必須有完成與刪除後清理證據。

| 來源 | 播放 | 下載與清理 | 證據／備註 |
| --- | --- | --- | --- |
| `eacg` | `PASS` | `PASS` | `re0` 精確選到 E-ACG；`.tmp-eacg-episode-diagnostic.png` 顯示實際畫面，`.tmp-eacg-diagnostic-logcat.txt` 記錄驗證後的詳情頁、3 條網站頻道與 XHS MP4 resolve；`.tmp-download-eacg-complete.png`／`.tmp-download-eacg-deleted.png` 顯示下載完成與刪除後清理。 |
| `dm1` | `PASS` | `UNVERIFIED` | `.tmp-final-play-dm1.png` 顯示播放器實際畫面，來源卡為稀飯動漫；下載未另行測試，依目前人工驗收指示只需一個來源完成下載驗證。 |
| `next` | `PASS` | `UNVERIFIED` | `.tmp-final-play-next.png` 顯示播放器實際畫面，來源卡為稀飯動漫 Next；下載未另行測試，依目前人工驗收指示只需一個來源完成下載驗證。 |
| `girigiri` | `PASS` | `PASS` | `re0` 搜尋由公開 API 取得 6 個候選，精確選到 `GV1222`；網站詳情頁只有 `简中` 1 個頻道、25 集。`.tmp-girigiri-playing-confirmed.png`／`.tmp-girigiri-download.log` 可核對播放與 `206` 媒體請求；`.tmp-girigiri-download-completed.png` 顯示 `1/25 已完成 · 333.7 MB`，`.tmp-girigiri-download-clean.png` 顯示由 App 刪除後回到 `0/25 已完成`。 |
| `2rk` | `PASS`（線路 1） | `UNVERIFIED` | `.tmp-final-play-2rk-line1.png` 顯示線路 1 實際畫面。網站 `c.js` 實際列出 `线路1`、`线路2`、`线路3`；線路 2、3 的 `/saber` 回應仍是 31 bytes，ExoPlayer 報 `InvalidKeyException: Unsupported key size: 31 bytes`，因此個別標為上游 `BLOCKED`，沒有假稱三條都能播放。 |
| `dida` | `PASS` | `PASS` | `.tmp-dida-static-matcher-fix-playing.png` 顯示嘀嗒影視實際畫面與來源卡；`.tmp-dida-download-progress-20s.png` 顯示已取得 `138.6 MB`，完成與刪除後證據為 `.tmp-dida-download-complete.xml`、`.tmp-dida-download-deleted.png`。 |
| `dmbus` | `PASS` | `PASS` | `.tmp-android-dmbus-slime-playing-fixed-logcat.txt` 有同一番劇的正向播放進度；下載完成、刪除與清理證據為 `.tmp-android-dmbus-download-finished-menu.xml`、`.tmp-android-dmbus-download-after-delete.xml`、`.tmp-android-dmbus-download-clean.png`。 |
| `dyttzy` | `PASS`（Android；Windows Desktop） | `PASS`（Android；Windows Desktop 下載） | `哪吒闹海` 的 Android host 測試已由 `dyttm3u8` 解析到直接 HLS；`.tmp-android-ui-verify/dyttzy-nezha-playing-1.png` 與 `...-playing-2.png` 顯示播放畫面與進度變化。`.tmp-android-ui-verify/dyttzy-na-zha-download-completed.png` 顯示 `1/1 completed · 450.6 MB` 與「電影天堂 · Finished」；輸出 MP4 為 `450021076` bytes，並由 `.../dyttzy-na-zha-downloaded-playback.png` 確認可播放。刪除後 `.../dyttzy-na-zha-download-cleaned.png` 回到 `0/1 completed`，下載目錄無殘留。Windows Desktop 的商店安裝、`電影天堂`／`dyttm3u8` 播放、`1/1 已完成 · 450.6 MB` 下載與下載檔播放證據記錄於 `.tmp-desktop-ui-verify/dyttzy-desktop-evidence.txt`；桌面輸出檔保留供檢查，未執行刪除清理。 |

目前已有下載完成／刪除後清理證據的來源為 E-ACG、Girigiri、DIDA、DMBUS 與 DYTTZY；下載驗收只要求一個來源時，這些證據已超出最低數量。

### 多線路結果

- E-ACG 詳情頁實際回傳 `EDD动漫`、`极速在线`、`看吧备用` 三條線路；最新模擬器測試以 `看吧备用` 成功播放。其餘線路保留網站實際錯誤（TLS 憑證鏈或上游 timeout），不以替代 URL 或停用驗證冒充成功。
- 2RK 詳情頁實際回傳 `线路1`、`线路2`、`线路3`；線路 1 播放成功，線路 2、3 因站方金鑰回應不是可用的 32-byte AES 金鑰而標示 `BLOCKED`。
- DIDA、DMBUS 的來源卡與網站線路名稱均來自插件詳情解析，沒有由 host 補造不存在的線路。

### 2RK 線路記錄

2RK 的網站初始名稱保留為 `线路1`、`线路2`、`线路3`。插件從網站 `c.js` 動態取得三個 host，不應把不存在的線路加到 UI。只要站方金鑰回應仍是 31 bytes，三條線路都不能因為頁面載入成功而標示播放 `PASS`；應保留原始錯誤，等待站方回應恢復或在不解密、不修改站方金鑰的正常 WebView 流程中重新驗證。

## 圖標與商店 UI 證據

插件 manifest 的 `icon` 使用 HTTPS 網站圖標 URL，host 會驗證 URL 的 scheme、host 與固定 repository path。DYTTZY 的 Android 商店與設定頁證據為 `.tmp-android-ui-verify/dyttzy-source-plugin-store.png`、`.tmp-android-ui-verify/dyttzy-settings-page.png`；可核對 `電影天堂`、`dyttzy`、版本與啟用狀態。既有來源管理截圖仍保留於 `.tmp-icon-source-management.png` 與 `.tmp-icon-source-management-bottom.png`。

## 清理規則

下載測試完成後，必須從應用程式下載頁刪除測試檔，再確認 UI 與檔案目錄都沒有殘留。工作區中的 `.tmp-*` 是驗證證據，不是下載媒體；不要把它們加入 Git，也不要用清理下載檔的步驟刪除這些證據。
