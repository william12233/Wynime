# 來源套件共同煙霧測試

每次發布來源套件時，先執行離線契約測試，再用 host 的 Android emulator 或桌面執行檔進行人工煙霧測試。每個來源至少選一部搜尋結果，打開一集播放，並下載同一集一次；下載完成後刪除檔案，確認下載目錄沒有留下測試媒體。

搜尋輸入使用網站可接受的原文：Re:Zero 使用 `re0`，2RK 使用 `关于我转生变成史莱姆这档事`。來源線顯示名稱不自行改寫，直接採用網站初始回傳的 `displayName`。

測試紀錄至少包含：來源 ID、網站查詢字串、番劇與集數、線路原名、解析後媒體格式、播放畫面或播放日誌、下載完成畫面或檔案大小、刪除後目錄狀態。若網站回傳的 URL 無法播放或下載，記錄原始 HTTP／播放器錯誤並標示 `BLOCKED` 或 `UNVERIFIED`；不得以停用 TLS 驗證、繞過 DRM／驗證碼、固定他站直連或任意解析器取代正常來源。

電影天堂／DYTTZY 已加入 `index.json` 與 manifest。人工驗證只使用 API 回傳的 `dyttm3u8` 直接 HTTPS HLS；`dytt` 分享網址、`vod_down_url` 與非 HLS 項目不納入播放或下載。Android host 已完成直接 HLS 播放、下載合併、下載檔播放與 App 內建刪除清理；Windows Desktop 已完成插件安裝、直接 HLS 播放、下載完成與下載檔播放，桌面輸出檔保留供檢查，尚未執行刪除清理。

`sourcePluginLiveSmokeTest` 可用 `WYNIME_SOURCE_PLUGIN_LIVE_QUERY` 與
`WYNIME_SOURCE_PLUGIN_LIVE_EPISODE` 暫時覆寫所有來源的共同查詢與集數；未設定時維持各來源的預設測試條目。
