# 來源網站實勘紀錄

本文件記錄目前索引中的九個來源插件所依據的網站行為。endpoint、線路名稱與媒體格式來自插件程式及 Android 模擬器的實際請求；短效 token、Cookie 值與驗證內容不寫入文件。

## 來源實作

| 插件 | 搜尋與詳情 | 線路／集數 | 播放解析與請求上下文 | 目前驗證 |
| --- | --- | --- | --- | --- |
| 稀飯動漫 Next | SSR `/search?q=...`、Supabase `search_animes` RPC、`/anime/{id}/play/{episode}` | 從番劇頁 `source` code 與 episode URL 建立頻道；每個 source code 都保留 | 從公開頁面／JS 發現 playback API，呼叫 `functions/v1/issue-web-playback` 取得短效 master playlist 或 MP4，保留 Origin／Referer | `re0` 搜尋與實際畫面 PASS |
| Girigiri | `m3u8.girigirilove.com/api.php/provide/vod/?ac=detail&wd=...`，並以站內詳情頁核對標題 | `/play{subject}-{channel}-{episode}/`；Re:Zero 詳情頁實際只有 `简中` 1 條線路、25 集 | 播放頁／公開 API 回傳的 `ana.girigirilove.com` MP4；直接媒體請求保留必要範圍 headers | `re0`、播放、333.7 MB 下載、刪除清理 PASS |
| 二礦動漫 2RK | `/search?w=...`、`/detail/{slug}?id=...`、公開 `c.js` | `c.js` 實際列出 `线路1`、`线路2`、`线路3`；不由 Host 追加不存在的線路 | 依選定 host 傳 `curXianlu` Cookie，並保留 detail Referer／Origin 取得 HLS；播放與下載對被截斷的大分片改用 1 MB Range 重組；站方線路 2、3 目前回傳 31-byte 金鑰，未繞過或修改金鑰 | 線路 1 的 playlist／金鑰／Range 重組已驗證；線路 2、3 因站方回應 BLOCKED |
| DMBUS | 搜尋頁、`/v/{id}.html`、`/p/{id}-{channel}-{episode}.html` | 由 `play_from` 及 play URL 建立全部網站線路 | HHJX 播放頁的正常 `/api/parse` POST 取得下游 MP4；解析請求使用 Origin／Referer，但 QQ 直出媒體依站方行為移除不被接受的 Referer | Re:Zero 播放、下載進度、刪除清理 PASS |
| DYTTZY | `https://caiji.dyttzyapi.com/api.php/provide/vod?ac=videolist&wd=...`；詳情使用 `ids=...` | `vod_play_from` 以 `$$$` 對應 `vod_play_url`；只保留名稱為 `dyttm3u8` 的來源，以 `#` 分集、`$` 分隔集名與直接 URL | 只接受 API 回傳的 HTTPS `.m3u8`；resolve 時重新抓取詳情，不附加未驗證 Referer／Origin；`dytt` 分享網址與 `vod_down_url` 排除 | API 搜尋／詳情、Android 播放、Android 下載與刪除清理 PASS；Windows Desktop 插件安裝、播放、下載完成與下載檔播放 PASS，桌面輸出尚未清理 |
| 白猫動漫 | `s_all?ex=1&kw=...`、`/show/{id}.html`、`/play/{id}-{channel}-{episode}.html` | `menu0` 的播放線路與 play URL 對應；站點目前使用 `bmddm.com` | 依站方公開 `/time`、`/playinfo` 流程解析媒體；每條線路以第一集探測，只有可用線路回傳給 host | fixture 驗證公式、線路過濾與直接 HLS；需另行完成實機播放驗收 |
| AkiAnime | `/bgmsearch/-------------.html?wd=...`、`/bgmdetail/{code}.html`、`/bgmplay/{code}-{channel}-{episode}.html` | 只從 `this-link` 播放資源與播放 URL 對應線路，保留網站名稱 | 播放頁有公開 HLS 時直接解析並保留 Referer；加密或非 HTTP 線路交給站方 JavaScript 與 WebView | U-17 半決賽搜尋／詳情／第 8 集播放頁煙霧測試 PASS；實機播放待驗 |
| MX動漫 | `/search/?wd=...`、`/detail/{id}/`、`/play/{id}-{channel}-{episode}/` | 以詳情頁 tabs 順序對應實際 channel ID，不假設 ID 連號 | 播放頁的下游媒體受站方區域策略影響，保留正常播放頁給 WebView | 黑子的籃球第二季搜尋／詳情／第 8 集播放頁煙霧測試 PASS；實機播放待驗 |
| 暴風資源 | `bfzyapi.com/api.php/provide/vod?ac=detail&wd=...`、`ids=...` | 只保留 `bfzym3u8`，從 API 的 `vod_play_url` 建立集數 | 只接受公開 API 回傳的 HTTPS HLS，媒體請求保留 `Accept: */*` 與網站 Referer | 咒术回战 ID `46365` 可搜尋，但第一集 CDN 目前 HTTP `404`，標示上游 `BLOCKED` |

## 驗證邊界

- 2RK 線路 2、3 的站方 `/saber` 回應不是可用的 AES-128 key；`HTTP 200` 或建立了播放器資料都不算播放成功。
- 電影天堂目前某些咒術迴戰 HLS URL 回傳 `403 The region has been denied`；保留原始區域狀態，不以其他網域或代理替代。
- 所有插件都在 resolve 時重新取得短效媒體 URL；穩定身分仍是 plugin、subject、channel、episode，不把短效 URL 寫入長期來源資料。

人工截圖與日誌索引位於 [common-smoke-test.md](common-smoke-test.md)。DYTTZY 的播放 CDN 由 API 動態提供，不在插件中硬編碼網域；若站方回傳憑證錯誤或非 HLS URL，保留原始失敗並標示 `BLOCKED` 或 `UNVERIFIED`，不繞過 TLS。
