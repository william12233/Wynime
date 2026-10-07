package com.wynime.app.ui.update.devbuild

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DevBuildInputTest {
    private fun parse(text: String) = parseDevBuildInput(text, "william12233/Wynime")

    @Test
    fun `parses commit sha and commit links`() {
        assertEquals(DevBuildInput.Commit("28ec14ac"), parse("28EC14AC"))
        assertEquals(DevBuildInput.Commit(SHA_A), parse("  $SHA_A\n"))
        assertEquals(DevBuildInput.Commit(SHA_A), parse("https://github.com/william12233/Wynime/commit/$SHA_A"))
        assertEquals(DevBuildInput.Commit(SHA_A), parse("https://github.com/william12233/Wynime/commit/$SHA_A?diff=split#r1"))
        assertEquals(DevBuildInput.Commit(SHA_A), parse("https://github.com/william12233/Wynime/pull/12/commits/$SHA_A"))
        assertEquals(DevBuildInput.Commit(SHA_A), parse("https://api.github.com/repos/william12233/Wynime/commits/$SHA_A"))
        assertNull(parse("28ec14"), "too short for a sha")
        assertNull(parse("https://github.com/william12233/Wynime/commit/not-a-sha"))
    }

    @Test
    fun `parses pull request numbers and links`() {
        assertEquals(DevBuildInput.PullRequest(123), parse("123"))
        assertEquals(DevBuildInput.PullRequest(123), parse("#123"))
        assertEquals(DevBuildInput.PullRequest(123), parse("https://github.com/william12233/Wynime/pull/123"))
        assertEquals(DevBuildInput.PullRequest(123), parse("https://github.com/william12233/Wynime/pull/123/files"))
        assertEquals(DevBuildInput.PullRequest(123), parse("https://api.github.com/repos/william12233/Wynime/pulls/123"))
    }

    @Test
    fun `parses workflow run and artifact links`() {
        assertEquals(
            DevBuildInput.WorkflowRun(1234),
            parse("https://github.com/william12233/Wynime/actions/runs/1234"),
        )
        assertEquals(
            DevBuildInput.WorkflowRun(1234),
            parse("https://github.com/william12233/Wynime/actions/runs/1234/job/5678"),
        )
        assertEquals(
            DevBuildInput.Artifact(99),
            parse("https://github.com/william12233/Wynime/actions/runs/1234/artifacts/99"),
        )
        assertEquals(
            DevBuildInput.Artifact(99),
            parse("https://api.github.com/repos/william12233/Wynime/actions/artifacts/99/zip"),
        )
        assertEquals(
            DevBuildInput.WorkflowRun(1234),
            parse("https://api.github.com/repos/william12233/Wynime/actions/runs/1234"),
        )
    }

    @Test
    fun `parses direct package links by the last path segment`() {
        val url = "https://github.com/william12233/Wynime/releases/download/v4.12.0/wynime-4.12.0-macos-aarch64.dmg"
        assertEquals(DevBuildInput.PackageUrl(url, "wynime-4.12.0-macos-aarch64.dmg"), parse(url))
        assertEquals(
            DevBuildInput.PackageUrl("https://example.com/a/b/Wynime%20Dev.dmg?token=1", "Wynime Dev.dmg"),
            parse("https://example.com/a/b/Wynime%20Dev.dmg?token=1"),
        )
        assertNull(parse("https://example.com/"), "no file name")
        assertNull(parse("https://example.com/downloads"), "no extension")
        assertNull(parse("ftp://example.com/a.dmg"), "not http")
    }

    @Test
    fun `rejects other repositories and unknown input`() {
        assertNull(parse(""))
        assertNull(parse("hello world"))
        assertNull(parse("https://github.com/other/repo/commit/$SHA_A"))
        assertNull(parse("https://github.com/other/repo/pull/1"))
        assertNull(parse("https://github.com/william12233/Wynime/issues/1"))
        assertEquals(DevBuildInput.Commit(SHA_A), parse("https://github.com/WILLIAM12233/wynime/commit/$SHA_A"))
    }
}
