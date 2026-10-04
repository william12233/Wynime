# 來源網站實勘紀錄

本文件記錄目前索引中的七個來源插件所依據的網站行為。endpoint、線路名稱與媒體格式來自插件程式及 Android 模擬器的實際請求；短效 token、Cookie 值與驗證內容不寫入文件。

## 來源實作

| 插件 | 搜尋與詳情 | 線路／集數 | 播放解析與請求上下文 | 目前驗證 |
| --- | --- | --- | --- | --- |
| E-ACG | `/vodsearch/...`、`/voddetails-{id}.html` | 詳情頁的 `fed-play-btns` 與 `Comicplay/{subject}-{channel}-{episode}.html`；本次 Re:Zero 實際回傳 3 條線路 | 播放頁的 `data-play`／播放器資料；XHS spectrum 直出 MP4 時不附網站頁面的 Referer。搜尋遇到 Cloudflare 時交給 Host `WebSessionManager` 的互動工作階段 | `re0` 搜尋、3 條線路、`看吧备用` 播放與下載清理 PASS |
| 稀飯動漫 DM1 | `/search.html?wd=...`、`/bangumi/{id}.html` | 從番劇頁的播放群組與 `/watch/{subject}/{channel}/{episode}.html` 建立頻道；網站名稱優先，缺名才使用保守 fallback | 播放頁的公開 player data 解析為 HLS／MP4，保留播放頁 Referer；不固定保存短效媒體 URL | `re0` 搜尋與實際畫面 PASS |
| 稀飯動漫 Next | SSR `/search?q=...`、Supabase `search_animes` RPC、`/anime/{id}/play/{episode}` | 從番劇頁 `source` code 與 episode URL 建立頻道；每個 source code 都保留 | 從公開頁面／JS 發現 playback API，呼叫 `functions/v1/issue-web-playback` 取得短效 master playlist 或 MP4，保留 Origin／Referer | `re0` 搜尋與實際畫面 PASS |
| Girigiri | `m3u8.girigirilove.com/api.php/provide/vod/?ac=detail&wd=...`，並以站內詳情頁核對標題 | `/play{subject}-{channel}-{episode}/`；Re:Zero 詳情頁實際只有 `简中` 1 條線路、25 集 | 播放頁／公開 API 回傳的 `ana.girigirilove.com` MP4；直接媒體請求保留必要範圍 headers | `re0`、播放、333.7 MB 下載、刪除清理 PASS |
| 二礦動漫 2RK | `/search?w=...`、`/detail/{slug}?id=...`、公開 `c.js` | `c.js` 實際列出 `线路1`、`线路2`、`线路3`；不由 Host 追加不存在的線路 | 依選定 host 傳 `curXianlu` Cookie，並保留 detail Referer／Origin 取得 HLS；站方線路 2、3 目前回傳 31-byte 金鑰，未繞過或修改金鑰 | 線路 1 播放 PASS；線路 2、3 因站方回應 BLOCKED |
| DIDA | `/search/-------------.html?wd=...`、`/detail/{id}.html`、`/play/{id}-{channel}-{episode}.html` | 由 `#playlistN` 的原始文字與 play URL 對應 channel ID，保留網站線路名稱 | 一般 player data 或 BBA Artplayer 頁面解析；播放頁 Referer 傳給需要它的媒體請求 | `re0` 搜尋、實際畫面、138.6 MB 下載進度、刪除清理 PASS |
| DMBUS | 搜尋頁、`/v/{id}.html`、`/p/{id}-{channel}-{episode}.html` | 由 `play_from` 及 play URL 建立全部網站線路 | HHJX 播放頁的正常 `/api/parse` POST 取得下游 MP4；解析請求使用 Origin／Referer，但 QQ 直出媒體依站方行為移除不被接受的 Referer | Re:Zero 播放、下載進度、刪除清理 PASS |

## 驗證邊界

- E-ACG 的 Cloudflare／網站驗證由共用互動 WebSession 處理；插件不繞過驗證、不關閉 TLS 檢查，也不記錄驗證 token。
- 2RK 線路 2、3 的站方 `/saber` 回應不是可用的 AES-128 key；`HTTP 200` 或建立了播放器資料都不算播放成功。
- E-ACG 其他線路的 TLS 憑證鏈／上游 timeout 仍按原始錯誤記錄；成功線路不能替代或掩蓋失敗線路。
- 所有插件都在 resolve 時重新取得短效媒體 URL；穩定身分仍是 plugin、subject、channel、episode，不把短效 URL 寫入長期來源資料。

人工截圖與日誌索引位於 [common-smoke-test.md](common-smoke-test.md)。電影天堂／DYTTZY 已依目前驗收範圍移出 index、manifest 與已安裝來源。
