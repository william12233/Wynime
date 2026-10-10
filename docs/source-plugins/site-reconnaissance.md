# 來源網站實勘紀錄

本文件記錄目前索引中的十個來源插件所依據的網站行為。endpoint、線路名稱與媒體格式來自插件程式及 Android 模擬器的實際請求；短效 token、Cookie 值與驗證內容不寫入文件。

## 來源實作

| 插件 | 搜尋與詳情 | 線路／集數 | 播放解析與請求上下文 | 目前驗證 |
| --- | --- | --- | --- | --- |
| E-ACG | `/vodsearch/...`、`/voddetails-{id}.html` | 詳情頁的 `fed-play-btns` 與 `Comicplay/{subject}-{channel}-{episode}.html`；本次 Re:Zero 實際回傳 3 條線路 | 播放頁的 `data-play`／播放器資料；XHS spectrum 直出 MP4 時不附網站頁面的 Referer。搜尋遇到 Cloudflare 時交給 Host `WebSessionManager` 的互動工作階段 | `re0` 搜尋、3 條線路、`看吧备用` 播放與下載清理 PASS |
| 稀飯動漫 DM1 | `/search.html?wd=...`、`/bangumi/{id}.html` | 從番劇頁的播放群組與 `/watch/{subject}/{channel}/{episode}.html` 建立頻道；網站名稱優先，缺名才使用保守 fallback | 播放頁的公開 player data 解析為 HLS／MP4，保留播放頁 Referer；不固定保存短效媒體 URL | `re0` 搜尋與實際畫面 PASS |
| 稀飯動漫 Next | SSR `/search?q=...`、Supabase `search_animes` RPC、`/anime/{id}/play/{episode}` | 從番劇頁 `source` code 與 episode URL 建立頻道；每個 source code 都保留 | 從公開頁面／JS 發現 playback API，呼叫 `functions/v1/issue-web-playback` 取得短效 master playlist 或 MP4，保留 Origin／Referer | `re0` 搜尋與實際畫面 PASS |
| Girigiri | `m3u8.girigirilove.com/api.php/provide/vod/?ac=detail&wd=...`，並以站內詳情頁核對標題 | `/play{subject}-{channel}-{episode}/`；Re:Zero 詳情頁實際只有 `简中` 1 條線路、25 集 | 播放頁／公開 API 回傳的 `ana.girigirilove.com` MP4；直接媒體請求保留必要範圍 headers | `re0`、播放、333.7 MB 下載、刪除清理 PASS |
| 二礦動漫 2RK | `/search?w=...`、`/detail/{slug}?id=...`、公開 `c.js` | `c.js` 實際列出 `线路1`、`线路2`、`线路3`；不由 Host 追加不存在的線路 | 依選定 host 傳 `curXianlu` Cookie，並保留 detail Referer／Origin 取得 HLS；站方線路 2、3 目前回傳 31-byte 金鑰，未繞過或修改金鑰 | 線路 1 播放 PASS；線路 2、3 因站方回應 BLOCKED |
| DMBUS | 搜尋頁、`/v/{id}.html`、`/p/{id}-{channel}-{episode}.html` | 由 `play_from` 及 play URL 建立全部網站線路 | HHJX 播放頁的正常 `/api/parse` POST 取得下游 MP4；解析請求使用 Origin／Referer，但 QQ 直出媒體依站方行為移除不被接受的 Referer | Re:Zero 播放、下載進度、刪除清理 PASS |
| DYTTZY | `https://caiji.dyttzyapi.com/api.php/provide/vod?ac=videolist&wd=...`；詳情使用 `ids=...` | `vod_play_from` 以 `$$$` 對應 `vod_play_url`；只保留名稱為 `dyttm3u8` 的來源，以 `#` 分集、`$` 分隔集名與直接 URL | 只接受 API 回傳的 HTTPS `.m3u8`；resolve 時重新抓取詳情，不附加未驗證 Referer／Origin；`dytt` 分享網址與 `vod_down_url` 排除 | API 搜尋／詳情、Android 播放、Android 下載與刪除清理 PASS；Windows Desktop 插件安裝、播放、下載完成與下載檔播放 PASS，桌面輸出尚未清理 |
| 白猫動漫 | `s_all?ex=1&kw=...`、`/show/{id}.html`、`/play/{id}-{channel}-{episode}.html` | `menu0` 的播放線路與 play URL 對應；公開播放頁交給 WebView | 播放頁由站方 JavaScript 初始化播放器，插件只返回正常播放頁 | fixture PASS；需另行完成實機播放驗收 |
| AkiAnime | `/bgmsearch/-------------.html?wd=...`、`/bgmdetail/{code}.html`、`/bgmplay/{code}-{channel}-{episode}.html` | 只從 `this-link` 播放資源與播放 URL 對應線路，保留網站名稱 | 播放頁有公開 HLS 時直接解析並保留 Referer；加密或非 HTTP 線路交給站方 JavaScript 與 WebView | U-17 半決賽搜尋／詳情／第 8 集播放頁煙霧測試 PASS；實機播放待驗 |
| MX動漫 | `/search/?wd=...`、`/detail/{id}/`、`/play/{id}-{channel}-{episode}/` | 以詳情頁 tabs 順序對應實際 channel ID，不假設 ID 連號 | 播放頁的下游媒體受站方區域策略影響，保留正常播放頁給 WebView | 黑子的籃球第二季搜尋／詳情／第 8 集播放頁煙霧測試 PASS；實機播放待驗 |

## 驗證邊界

- E-ACG 的 Cloudflare／網站驗證由共用互動 WebSession 處理；插件不繞過驗證、不關閉 TLS 檢查，也不記錄驗證 token。
- 2RK 線路 2、3 的站方 `/saber` 回應不是可用的 AES-128 key；`HTTP 200` 或建立了播放器資料都不算播放成功。
- E-ACG 其他線路的 TLS 憑證鏈／上游 timeout 仍按原始錯誤記錄；成功線路不能替代或掩蓋失敗線路。
- 所有插件都在 resolve 時重新取得短效媒體 URL；穩定身分仍是 plugin、subject、channel、episode，不把短效 URL 寫入長期來源資料。

人工截圖與日誌索引位於 [common-smoke-test.md](common-smoke-test.md)。DYTTZY 的播放 CDN 由 API 動態提供，不在插件中硬編碼網域；若站方回傳憑證錯誤或非 HLS URL，保留原始失敗並標示 `BLOCKED` 或 `UNVERIFIED`，不繞過 TLS。
