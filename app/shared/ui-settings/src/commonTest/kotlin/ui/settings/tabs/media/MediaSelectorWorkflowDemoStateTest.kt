package com.wynime.app.ui.settings.tabs.media

import com.wynime.utils.selectorworkflow.HighlightRegion
import com.wynime.utils.selectorworkflow.ResolveOutcome
import com.wynime.utils.selectorworkflow.SelectMode
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class MediaSelectorWorkflowDemoStateTest {

    private fun state(eager: Boolean = true) = MediaSelectorWorkflowDemoState(eager)

    private val MediaSelectorWorkflowDemoState.config get() = viewModel.config
    private val MediaSelectorWorkflowDemoState.playsResolveDemo
        get() = viewModel.config.resolve.outcomes.contains(ResolveOutcome.Timeout)
    private val MediaSelectorWorkflowDemoState.playsCacheQuery
        get() = viewModel.config.cachedQuery
    private val MediaSelectorWorkflowDemoState.highlight
        get() = viewModel.config.highlights.singleOrNull()

    @Test
    fun `starts with the current fast select state and nothing else`() {
        val on = state(eager = true)
        assertEquals(SelectMode.Eager, on.config.selection.mode)
        assertNull(on.config.selection.priorityWait, "刚进来不该演高优先级那一段")
        assertTrue(!on.playsResolveDemo, "刚进来不该演第三步超时那一段")
        assertTrue(!on.playsCacheQuery, "刚进来不该演走缓存那一段")

        assertEquals(SelectMode.WaitAll, state(eager = false).config.selection.mode)
    }

    @Test
    fun `picking the max wait time plays only the priority segment`() {
        val s = state()
        s.onLowTierToleranceChanged(8.seconds)
        assertEquals(8.seconds, s.config.selection.priorityWait)
        assertTrue(s.config.selection.demoBothPriorityPaths, "该连演等到了/等超时两条路径")
        assertTrue(!s.playsResolveDemo, "同时该把第三步那一段收起来")
    }

    @Test
    fun `picking the resolve timeout plays only the resolve segment`() {
        val s = state()
        s.onLowTierToleranceChanged(8.seconds)
        s.onResolveTimeoutChanged(15)

        assertTrue(s.playsResolveDemo)
        assertEquals(15.seconds, s.config.resolve.budget)
        assertNull(s.config.selection.priorityWait, "动第三步就该把高优先级那一段收起来")
    }

    @Test
    fun `picking a cache ttl plays only the cache segment`() {
        val s = state()
        s.onResolveTimeoutChanged(15)
        s.onWebSearchCacheTtlChanged(30.minutes)

        assertTrue(s.playsCacheQuery)
        assertTrue(!s.playsResolveDemo, "动缓存时长就该把第三步那一段收起来")
        assertNull(s.config.selection.priorityWait)
    }

    @Test
    fun `the cache segment only cares whether there is a cache at all`() {

        val s = state()
        s.onWebSearchCacheTtlChanged(15.minutes)
        val short = s.viewModel.state.duration
        s.onWebSearchCacheTtlChanged(1.days)
        assertEquals(short, s.viewModel.state.duration, "缓多久不该改变动画")

        s.onWebSearchCacheTtlChanged(Duration.ZERO)
        assertTrue(!s.playsCacheQuery, "「不缓存」该收回到朴素流程")
    }

    @Test
    fun `toggling fast select puts every segment away`() {
        val s = state(eager = true)
        s.onResolveTimeoutChanged(15)
        assertTrue(s.playsResolveDemo)
        s.onWebSearchCacheTtlChanged(30.minutes)
        assertTrue(s.playsCacheQuery)

        s.onFastSelectWebKindChanged(false)
        assertEquals(SelectMode.WaitAll, s.config.selection.mode)
        assertNull(s.config.selection.priorityWait)
        assertTrue(!s.playsResolveDemo, "切换快速选择时每一段都该收起来")
        assertTrue(!s.playsCacheQuery, "切换快速选择时每一段都该收起来")

        s.onFastSelectWebKindChanged(true)
        assertEquals(SelectMode.Eager, s.config.selection.mode)
    }

    @Test
    fun `the fast select state survives the other two settings`() {
        val s = state(eager = false)
        s.onLowTierToleranceChanged(8.seconds)
        assertEquals(SelectMode.WaitAll, s.config.selection.mode, "演别的段不该把抢先选源打开")
        s.onResolveTimeoutChanged(15)
        assertEquals(SelectMode.WaitAll, s.config.selection.mode)
    }

    @Test
    fun `degenerate wait times fall back to the plain flow`() {

        val s = state()
        s.onLowTierToleranceChanged(8.seconds)
        s.onLowTierToleranceChanged(Duration.ZERO)
        assertNull(s.config.selection.priorityWait)

        s.onLowTierToleranceChanged(8.seconds)
        s.onLowTierToleranceChanged(Duration.INFINITE)
        assertNull(s.config.selection.priorityWait)
    }

    @Test
    fun `syncing the loaded setting does not restart or disturb the current segment`() {

        val s = state(eager = true)
        s.onLowTierToleranceChanged(8.seconds)
        s.viewModel.player.advance(1.seconds)
        val playhead = s.viewModel.player.playhead
        assertTrue(playhead > Duration.ZERO)

        val progress = s.viewModel.state.progress

        s.syncEagerSelect(false)
        assertEquals(SelectMode.WaitAll, s.config.selection.mode, "该跟上真值")
        assertEquals(8.seconds, s.config.selection.priorityWait, "正在演的那一段不该被打断")
        assertTrue(s.viewModel.player.playhead > Duration.ZERO, "不是用户操作, 不该拨回开头")

        assertTrue(abs(s.viewModel.state.progress - progress) < 0.02f)

        val after = s.viewModel.player.playhead
        s.syncEagerSelect(false)
        assertEquals(after, s.viewModel.player.playhead)
    }

    @Test
    fun `each setting highlights the step it governs`() {
        val s = state()
        s.onWebSearchCacheTtlChanged(30.minutes)
        assertEquals(HighlightRegion.Sources, s.highlight, "缓存管的是第一步")

        s.onLowTierToleranceChanged(8.seconds)
        assertEquals(HighlightRegion.Results, s.highlight, "高优先级等待管的是第二步")

        s.onResolveTimeoutChanged(15)
        assertEquals(HighlightRegion.Resolve, s.highlight, "解析超时管的是第三步")

        s.onFastSelectWebKindChanged(true)
        assertEquals(HighlightRegion.Results, s.highlight, "快速选择管的也是第二步")
    }

    @Test
    fun `only one step is ever highlighted at a time`() {

        val s = state(eager = true)
        s.onWebSearchCacheTtlChanged(30.minutes)
        assertEquals(setOf(HighlightRegion.Sources), s.viewModel.config.highlights)
        assertEquals(SelectMode.Eager, s.config.selection.mode, "抢先选源还开着, 但不该跟着亮")
    }

    @Test
    fun `turning a setting off stops highlighting it`() {
        val s = state()
        s.onWebSearchCacheTtlChanged(30.minutes)
        s.onWebSearchCacheTtlChanged(Duration.ZERO)
        assertNull(s.highlight, "不缓存就没有可指的那一步")

        s.onLowTierToleranceChanged(8.seconds)
        s.onLowTierToleranceChanged(Duration.ZERO)
        assertNull(s.highlight)

        s.onFastSelectWebKindChanged(false)
        assertNull(s.highlight)
    }

    @Test
    fun `every change restarts the animation from the beginning`() {
        val s = state()
        fun advanceABit() = s.viewModel.player.advance(1.seconds)

        advanceABit()
        assertTrue(s.viewModel.player.playhead > Duration.ZERO)
        s.onLowTierToleranceChanged(8.seconds)
        assertEquals(Duration.ZERO, s.viewModel.player.playhead, "选了设置项该从头播")

        advanceABit()
        s.onResolveTimeoutChanged(15)
        assertEquals(Duration.ZERO, s.viewModel.player.playhead)

        advanceABit()
        s.onWebSearchCacheTtlChanged(30.minutes)
        assertEquals(Duration.ZERO, s.viewModel.player.playhead)

        advanceABit()
        s.onFastSelectWebKindChanged(false)
        assertEquals(Duration.ZERO, s.viewModel.player.playhead)
    }
}
