package tw.wynime.sources.dida

import com.wynime.source.plugin.api.SourceChannel
import com.wynime.source.plugin.api.SourceChannelEpisodes
import com.wynime.source.plugin.api.SourcePluginContext
import com.wynime.source.plugin.api.SourcePluginEntryPoint
import com.wynime.source.plugin.api.SourcePlugin
import com.wynime.source.plugin.api.SourceResolveRequest
import com.wynime.source.plugin.api.SourceSearchRequest
import com.wynime.source.plugin.api.SourceSubjectDetails
import com.wynime.source.plugin.api.SourceWebResourceMatch
import tw.wynime.sources.shared.SitePluginBase
import tw.wynime.sources.shared.cleanText
import tw.wynime.sources.shared.extractJsonStringField
import tw.wynime.sources.shared.extractPlayerObjectUrl
import tw.wynime.sources.shared.isMediaUrl
import tw.wynime.sources.shared.links
import tw.wynime.sources.shared.parseEpisodeNumber
import tw.wynime.sources.shared.queryMatches
import tw.wynime.sources.shared.searchQueryVariants

class DidaEntryPoint : SourcePluginEntryPoint {
    override fun create(context: SourcePluginContext): SourcePlugin = DidaPlugin(context)
}

internal class DidaPlugin(context: SourcePluginContext) : SitePluginBase(
    context = context,
    id = "dida",
    displayName = "嘀嗒影視",
    pluginVersion = PLUGIN_VERSION,
    rootUrl = "https://www.didahd.xyz",
    iconUrl = "https://www.didahd.xyz/template/mytheme/statics/img/newfavicon.png",
    description = "嘀嗒影視公開番劇與分集來源",
) {
    override suspend fun search(request: SourceSearchRequest): List<com.wynime.source.plugin.api.SourceSubject> {
        val results = mutableListOf<com.wynime.source.plugin.api.SourceSubject>()
        for (variant in didaSearchQueryVariants(request.query)) {
            val encoded = urlEncode(variant)
            val searchUrls = listOf(
                "$rootUrl/search/-------------.html?wd=$encoded",
                "$rootUrl/search.html?wd=$encoded",
            )
            for (searchUrl in searchUrls) {
                val page = runCatching {
                    requestPage(
                        searchUrl,
                        traceId = request.traceId,
                        entryPoint = request.entryPoint,
                    )
                }.getOrNull() ?: continue
                val found = dynamicSearchLinks(page.html, variant, Regex("(?i)/detail/(\\d+)\\.html"))
                    .ifEmpty { aliasSearchLinks(page.html, variant) }
                results += found
                if (found.isNotEmpty() || results.distinctBy { it.id }.size >= request.limit) break
            }
            if (results.distinctBy { it.id }.isNotEmpty()) break
        }
        return results.distinctBy { it.id }.take(request.limit)
    }

    private fun didaSearchQueryVariants(query: String): List<String> {
        val variants = searchQueryVariants(query).toMutableList()
        // DIDA's current index is simplified-Chinese only. Keep the original
        // spelling first, then retry with a bounded script conversion so direct
        // plugin callers and hosts without a Chinese normalizer can still search.
        val simplified = query.map { DIDA_TRADITIONAL_TO_SIMPLIFIED[it] ?: it }.joinToString("")
        variants += searchQueryVariants(simplified)
        val lower = query.lowercase()
        val isReZero = lower.contains("re0") ||
            lower.contains("re:zero") ||
            lower.contains("re：zero") ||
            query.contains("从零开始") ||
            query.contains("從零開始") ||
            query.contains("リゼロ")
        if (isReZero) {
            variants += listOf("异世界生活", "从零开始")
        }
        return variants.distinct()
    }

    private companion object {
        val DIDA_TRADITIONAL_TO_SIMPLIFIED = mapOf(
            '術' to '术', '迴' to '回', '戰' to '战', '網' to '网',
            '學' to '学', '國' to '国', '會' to '会', '賽' to '赛', '準' to '准',
            '決' to '决', '進' to '进', '開' to '开', '間' to '间', '時' to '时',
            '無' to '无', '這' to '这', '個' to '个', '後' to '后', '來' to '来',
            '兩' to '两', '裡' to '里', '裏' to '里', '麼' to '么', '變' to '变',
            '選' to '选', '擇' to '择', '終' to '终', '結' to '结', '發' to '发',
            '現' to '现', '點' to '点', '線' to '线', '畫' to '画', '劇' to '剧',
            '場' to '场', '別' to '别', '傳' to '传', '說' to '说', '題' to '题',
            '級' to '级', '數' to '数', '種' to '种',
            '從' to '从', '與' to '与', '為' to '为', '讓' to '让',
            '見' to '见', '應' to '应', '對' to '对', '於' to '于', '將' to '将',
            '還' to '还', '過' to '过', '機' to '机', '動' to '动', '東' to '东',
            '風' to '风', '頭' to '头', '體' to '体', '樣' to '样', '區' to '区',
            '簡' to '简', '轉' to '转', '專' to '专', '頁' to '页',
            '熱' to '热', '爭' to '争', '勝' to '胜', '敗' to '败',
            '實' to '实', '業' to '业', '務' to '务',
            '關' to '关', '係' to '系', '廣' to '广', '雲' to '云', '鐘' to '钟',
            '鐵' to '铁', '車' to '车', '醫' to '医', '藥' to '药', '氣' to '气',
            '靈' to '灵', '龍' to '龙', '馬' to '马', '魚' to '鱼',
            '鳥' to '鸟', '貓' to '猫', '門' to '门', '問' to '问', '聞' to '闻',
            '書' to '书', '視' to '视', '戲' to '戏', '影' to '影',
            '響' to '响', '錄' to '录', '製' to '制', '復' to '复', '備' to '备',
            '則' to '则', '華' to '华', '劇' to '剧', '節' to '节', '雙' to '双',
        )
    }

    private fun aliasSearchLinks(html: String, query: String): List<com.wynime.source.plugin.api.SourceSubject> {
        val detailPattern = Regex("(?i)href=[\\\"']([^\\\"']*/detail/(\\d+)\\.html)[\\\"']")
        return Regex("(?is)<li\\b[^>]*>.*?</li>")
            .findAll(html)
            .mapNotNull { block ->
                val detail = detailPattern.find(block.value) ?: return@mapNotNull null
                if (!queryMatches(cleanText(block.value), query)) return@mapNotNull null
                val title = Regex(
                    "(?is)<h4[^>]*class=[\\\"'][^\\\"']*title[^\\\"']*[\\\"'][^>]*>\\s*<a[^>]*>(.*?)</a>",
                ).find(block.value)?.groupValues?.getOrNull(1)?.let(::cleanText)
                    .orEmpty()
                    .ifBlank {
                        links(block.value)
                            .filter { detailPattern.containsMatchIn(it.href) }
                            .maxByOrNull { cleanText(it.text).length }
                            ?.text
                            ?.let(::cleanText)
                            .orEmpty()
                    }
                    .ifBlank { detail.groupValues[2] }
                subject(
                    id = detail.groupValues[2],
                    title = title,
                    detailUrl = absoluteUrl(rootUrl, detail.groupValues[1]),
                )
            }
            .distinctBy { it.id }
            .take(20)
            .toList()
    }

    override suspend fun getSubject(subjectId: String): SourceSubjectDetails {
        val page = requestPage("$rootUrl/detail/$subjectId.html")
        val title = sequenceOf(
            Regex("(?is)<h1[^>]*>(.*?)</h1>"),
            Regex("(?is)<h2[^>]*>(.*?)</h2>"),
        ).mapNotNull { pattern ->
            pattern.find(page.html)?.groupValues?.getOrNull(1)?.let(::cleanText)?.takeIf(String::isNotBlank)
        }.firstOrNull()
            ?: page.title.substringBefore("-").substringBefore("_").ifBlank { subjectId }
        val subject = subject(subjectId, title, page.finalUrl)
        val groups = linkedMapOf<String, MutableList<com.wynime.source.plugin.api.SourceEpisode>>()
        links(page.html).forEach { link ->
            val match = Regex("(?i)/play/(\\d+)-(\\d+)-(\\d+)\\.html").find(link.href) ?: return@forEach
            if (match.groupValues[1] != subjectId) return@forEach
            val channelId = match.groupValues[2]
            val episodeId = match.groupValues[3]
            groups.getOrPut(channelId) { mutableListOf() } += episode(
                id = episodeId,
                title = link.text.ifBlank { "第${episodeId}集" },
                pageUrl = absoluteUrl(rootUrl, link.href),
                episodeSort = parseEpisodeNumber(link.text) ?: episodeId.toFloatOrNull(),
            )
        }
        return SourceSubjectDetails(
            subject = subject,
            channels = groups.map { (channelId, episodes) ->
                SourceChannelEpisodes(
                    SourceChannel(channelId, channelNamesById(page.html, subjectId)[channelId] ?: "线路$channelId"),
                    episodes.distinctBy { it.id },
                )
            },
        )
    }

    private fun channelNamesById(html: String, subjectId: String): Map<String, String> {
        val namesByPlaylist = Regex(
            "(?is)<li[^>]*>\\s*<a[^>]*href=[\\\"']#playlist(\\d+)[\\\"'][^>]*>(.*?)</a>",
        ).findAll(html).associate { it.groupValues[1] to cleanText(it.groupValues[2]) }
        val channelIdsByPlaylist = Regex(
            "(?is)<div[^>]*id=[\\\"']playlist(\\d+)[\\\"'][^>]*>.*?" +
                "href=[\\\"'](?:[^\\\"']*/)?play/${Regex.escape(subjectId)}-(\\d+)-\\d+\\.html",
        ).findAll(html).associate { it.groupValues[1] to it.groupValues[2] }
        return channelIdsByPlaylist.mapNotNull { (playlist, channelId) ->
            namesByPlaylist[playlist]?.takeIf(String::isNotBlank)?.let { channelId to it }
        }.toMap()
    }

    override suspend fun resolve(request: SourceResolveRequest) = run {
        val pageUrl = "$rootUrl/play/${request.subjectId}-${request.channelId}-${request.episodeId}.html"
        val page = requestPage(pageUrl, traceId = request.traceId, entryPoint = request.entryPoint)
        val rawPlayerUrl = extractPlayerObjectUrl(page.html)
        val playerUrl = if (extractJsonStringField(page.html, "from")?.equals("BBA", ignoreCase = true) == true) {
            rawPlayerUrl?.let { encodedUrl ->
                absoluteUrl(
                    page.finalUrl,
                    "/static/player/artplayer/?url=${urlEncode(encodedUrl)}",
                )
            }
        } else {
            rawPlayerUrl
        }
        resolvedMedia(
            request = request,
            pageUrl = page.finalUrl,
            rawUrl = playerUrl ?: page.finalUrl,
            headers = mapOf("Referer" to page.finalUrl),
        )
    }

    override fun matchWebResource(url: String): SourceWebResourceMatch = when {
        isMediaUrl(url) -> SourceWebResourceMatch.Matched(url)
        url.contains("pan.quark.cn", ignoreCase = true) -> SourceWebResourceMatch.LoadPage
        else -> defaultWebResourceMatch(url)
    }
}
