package me.him188.ani.datasources.api.source

import kotlinx.serialization.Serializable

/**
 * 数据源类型.
 *
 * 不同类型的资源在缓冲速度上可能有本质上的区别.
 */ // 在数据源选择器中, 每个数据源会以 [MediaSourceKind] 分类展示.
@Serializable
enum class MediaSourceKind {
    /**
     * 在线视频网站. 资源为 [ResourceLocation.WebVideo] 或 [ResourceLocation.HttpStreamingFile].
     *
     * 對線上影片網站或其他 HTTP 串流資源使用.
     */
    WEB,

    /**
     * 本地视频下载. 只表示那些由 `MediaDownloadManager` 管理的视频.
     *
     * 该类型的资源总是会显示, 忽略一切过滤条件.
     */
    LocalCache;

    companion object {
        /**
         * 除本地缓存这种特殊类型外, 用户可以选择的数据源类型.
         */
        val selectableEntries = listOf(WEB)
    }
}
