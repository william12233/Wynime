# Web 來源驗證碼處理架構

本文說明 Web 來源共用的頁面會話、阻擋判定、Cookie/UA 同步與驗證碼處理。網站的搜尋、條目、線路、集數與播放頁解析由各 source plugin 負責；通用 Web 層不保存網站專用 selector 或搜尋路由。

圖片驗證碼的識別模型與 MacCMS 後台協定見[圖片驗證碼自動識別](../image-captcha.md)。

## 設計目標

1. **通用頁面判定**：所有 HTTP、瀏覽器及自動 solver 的頁面都使用同一個 `PageEvaluator`。
2. **直連優先**：先使用 HTTP 會話；只有頁面明確被驗證碼或 WAF 擋住時，才啟用瀏覽器會話。
3. **驗證結果可現場確認**：解決成功必須能以實際頁面重新驗證，不能只依賴過期的成功快取。
4. **平台層薄化**：平台只提供可被驅動的瀏覽器；會話編排、阻擋判定與 solver 流程位於 commonMain。
5. **限流獨立處理**：429 與站內冷卻頁回報為 `RateLimited`，不誤開驗證碼瀏覽器。
6. **身份一致**：HTTP 請求使用的 Cookie 與 User-Agent 必須和完成驗證的瀏覽器一致。

## 元件與資料流

| 元件 | 職責 |
| --- | --- |
| `PageEvaluator` | 將 HTTP/瀏覽器頁面判定為 `Ok`、`EmptyContent` 或 `Blocked`。 |
| `WebSessionManager` | 取得頁面、管理 host 會話、編排 solver 與瀏覽器重試。 |
| `CaptchaBrowser` | 桌面 JCEF 或 Android WebView 的平台適配器。 |
| `CaptchaSolver` | 注入式的自動驗證策略。 |
| `WebSourceCookieJar` / `WebSourceIdentityRegistry` | 在 HTTP 與瀏覽器間同步 Cookie 與 User-Agent。 |
| source plugin | 以網站自己的 API/HTML 規則解析搜尋、條目、線路、集數與播放媒體。 |

```text
source plugin
   └─ WebSessionManager.fetchPage(url, PageExpectation.AnyContent)
        ├─ 直連 HTTP (Cookie + host 專用 User-Agent)
        ├─ PageEvaluator
        │    ├─ Ok / EmptyContent → 交回 plugin 解析
        │    └─ Blocked → 限流重試或啟用驗證碼流程
        └─ 驗證碼瀏覽器
             ├─ solver 成功 → 同步 Cookie/UA 後重新取頁
             └─ 失敗 → 回報來源被阻擋
```

`PageExpectation` 只描述通用頁面是否可用。解析出哪些條目或線路是 source plugin 的責任，避免共用解析器把其他季、其他頁面或網站不存在的線路混入結果。

## PageEvaluator

`PageEvaluator` 的判定順序如下：

1. HTTP 404/410 回報 `NotFound`。
2. HTTP 429 或可辨識的冷卻頁回報 `RateLimited`。
3. 可辨識的 Cloudflare、Turnstile、圖片驗證碼或其他挑戰回報 `Captcha`。
4. 沒有可用頁面內容的 403 回報 `Forbidden`；瀏覽器驗證後仍會再次驗證頁面。
5. 具有 HTML 內容的正常頁面回報 `Ok`。
6. 沒有內容且沒有阻擋特徵的回應回報 `EmptyContent`。

source plugin 不應把驗證頁交給自己的搜尋解析器，也不應以空結果代替驗證碼或限流錯誤。

## WebSessionManager

會話以 host 為範圍，管理 Cookie、瀏覽器實例、User-Agent 覆寫、限流重試及驗證碼 single-flight。瀏覽器是需要時才建立的解題通道；直連恢復後會回到 HTTP，閒置會話由 TTL/LRU 回收。

互動式驗證必須真的呈現目前頁面；自動驗證成功也必須以重新取頁確認。驗證失敗或解決後仍被擋時，該 host 的暖會話與相關 Cookie 會失效，下一次從乾淨狀態重新開始。

## 執行緒與 Cookie 約束

- `CaptchaBrowser` 的方法為 `suspend`，平台實作自行切換到 CEF/Main 執行緒。
- 瀏覽器回呼只可完成或發送結果，不可 `runBlocking` 或同步等待 UI 執行緒。
- Cookie 收集透過可取消的 coroutine bridge 傳回 manager。
- `cf_clearance` 等繫結 User-Agent 的 Cookie，必須和完成驗證的瀏覽器 UA 一起套用到 HTTP 與播放頁。

## 測試

`PageEvaluatorTest` 覆蓋正常頁、404、429、驗證碼、無特徵 403 與空內容。Cookie/UA、single-flight、TTL 回收與瀏覽器行為使用對應的通用會話測試及 `android-ui-verify` / `desktop-ui-verify` 冒煙驗證。

[PageEvaluator]: ../../../../app/shared/app-data/src/commonMain/kotlin/domain/mediasource/web/PageEvaluator.kt
[WebSessionManager]: ../../../../app/shared/app-data/src/commonMain/kotlin/domain/mediasource/web/captcha/WebSessionManager.kt
[CaptchaBrowser]: ../../../../app/shared/app-data/src/commonMain/kotlin/domain/mediasource/web/captcha/CaptchaBrowser.kt
[CaptchaSolver]: ../../../../app/shared/app-data/src/commonMain/kotlin/domain/mediasource/web/captcha/CaptchaSolver.kt
[WebSourceCookieJar]: ../../../../app/shared/app-data/src/commonMain/kotlin/domain/mediasource/web/captcha/WebSourceCookieJar.kt
[WebSourceIdentityRegistry]: ../../../../app/shared/app-data/src/commonMain/kotlin/domain/mediasource/web/captcha/WebSourceCookieJar.kt
