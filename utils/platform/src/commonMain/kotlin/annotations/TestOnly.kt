package com.wynime.utils.platform.annotations

@RequiresOptIn(
    "This can only be used in test sourceSets",
    level = RequiresOptIn.Level.ERROR,
)
annotation class TestOnly
