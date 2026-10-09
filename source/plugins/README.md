# Wynime source plugins

這個公開倉庫提供 Wynime 的可執行影片來源套件。每個站點都是獨立的外掛 JAR，透過 Wynime 的 `plugin-api` 在執行時載入；站點搜尋、番劇詳情、分集頁與短期媒體地址都在使用時重新取得。

## 設計約束

- 套件只使用站點目前公開頁面與正常公開播放服務。
- 不保存固定影片直連，不使用任意第三方解析器，不繞過 DRM、驗證碼、Cloudflare 或登入限制。
- 外掛回傳的媒體地址是短期結果；穩定識別由站點 ID、來源線與分集 ID 組成。
- 只能載入 `https` 的倉庫 manifest/artifact URL；每個 artifact 都以 SHA-256 驗證。

## 建置

```powershell
./gradlew :source:plugins:test :source:plugins:packageAllPlugins :source:plugins:packageAndroidPlugins
```

JAR 會輸出到 `build/plugins/`，並同步寫入主專案的 `source/plugins/artifacts/`。插件透過主專案內的 `:source:plugin-api` 編譯，不複製或發行獨立的 API JAR。

Android artifact 是只含 `classes.dex` 的 JAR，供 `DexClassLoader` 載入；桌面 artifact 保留 JVM class。

`index.json` 與各 `manifests/*.json` 是公開倉庫的機器可讀索引，更新 artifact 後必須同步更新 SHA-256。

每個插件版本由 `versions/<id>.properties` 獨立管理；App 版本更新不會自動替換插件版本。版本常數只會編譯進對應插件的 artifact，不會由共用 runtime 映射決定。正式 artifact 使用 `source-plugin-<id>-v<version>` 的 immutable Git tag，與 App release tag 分離；索引保留以 immutable Git tag 固定的歷史 manifest，商店可安裝、降級或回復指定版本；App 內置套件只用於既有安裝的遷移。

## 目前來源與人工煙霧測試

| ID | 網站 | 搜尋規則 | 驗證要求 |
| --- | --- | --- | --- |
| `eacg` | [E-ACG](https://eacg1.com/) | `re0` | 播放一集、下載一集後刪除 |
| `dm1` | [DM1](https://dm1.xfdm.pro/) | `re0` | 播放一集、下載一集後刪除 |
| `next` | [Xifan Next](https://next.xifanacg.com/) | `re0` | 播放一集、下載一集後刪除 |
| `girigiri` | [Girigiri Love](https://ani.girigirilove.com/) | `re0` | 播放一集、下載一集後刪除 |
| `2rk` | [2RK](https://www.2rk.cc/) | `关于我转生变成史莱姆这档事` | 播放一集、下載一集後刪除 |
| `dida` | [DIDAHD](https://www.didahd.pro/) | `re0` | 播放一集、下載一集後刪除 |
| `dmbus` | [DMBUS](https://dmbus.cc/) | `re0` | 播放一集、下載一集後刪除 |
| `dyttzy` | [電影天堂](https://caiji.dyttzyapi.com/) | `哪吒之魔童鬧海` | 只使用 `dyttm3u8` 直接 HLS；播放一集、下載一集後刪除 |

線路名稱直接使用網站回傳的初始名稱。煙霧測試只驗證站點正常提供的媒體請求；遇到 TLS 憑證錯誤、破損 playlist、網盤或圖片偽裝串流時，必須記錄為未通過，不能用繞過驗證或替換第三方來源冒充成功。電影天堂只接受 API 回傳的 `dyttm3u8` 直接 HLS，`dytt` 分享網址與 `vod_down_url` 不作為播放或下載來源。

詳細的 host-side 驗證紀錄位於 Wynime 的 `docs/source-plugins/common-smoke-test.md`。
