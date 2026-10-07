package com.wynime.app.ui.settings.tabs.about

import com.wynime.app.ui.foundation.a
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.settings_developers_bangumi_upstream
import com.wynime.app.ui.lang.settings_developers_contributor
import com.wynime.app.ui.lang.settings_developers_daily_maintenance
import com.wynime.app.ui.lang.settings_developers_icon_drawing
import com.wynime.app.ui.lang.settings_developers_ml_research
import com.wynime.app.ui.lang.settings_developers_organization
import com.wynime.app.ui.lang.settings_developers_project_initiator
import com.wynime.app.ui.lang.settings_developers_server_development
import com.wynime.app.ui.lang.settings_developers_website_development
import com.wynime.app.ui.settings.Res
import com.wynime.app.ui.settings.generalk1ng
import com.wynime.app.ui.settings.grahamzen
import com.wynime.app.ui.settings.him188
import com.wynime.app.ui.settings.jerryz233
import com.wynime.app.ui.settings.misakatat
import com.wynime.app.ui.settings.nekoouo
import com.wynime.app.ui.settings.nick
import com.wynime.app.ui.settings.nier4ever
import com.wynime.app.ui.settings.nihildigit
import com.wynime.app.ui.settings.rdlwicked
import com.wynime.app.ui.settings.sanlorng
import com.wynime.app.ui.settings.stageguard
import com.wynime.app.ui.settings.woleoz
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import com.wynime.app.ui.foundation.Res as FoundationRes

data class DeveloperCredit(
    val name: String,
    val url: String,
    val role: StringResource,
    val avatar: DrawableResource,
)

val developerCredits = listOf(
    DeveloperCredit(
        "Him188",
        "https://github.com/him188",
        Lang.settings_developers_project_initiator,
        Res.drawable.him188,
    ),
    DeveloperCredit(
        "StageGuard",
        "https://github.com/StageGuard",
        Lang.settings_developers_daily_maintenance,
        Res.drawable.stageguard,
    ),
    DeveloperCredit(
        "General_K1ng",
        "https://github.com/GeneralK1ng",
        Lang.settings_developers_contributor,
        Res.drawable.generalk1ng,
    ),
    DeveloperCredit(
        "GrahamZen",
        "https://github.com/GrahamZen",
        Lang.settings_developers_contributor,
        Res.drawable.grahamzen,
    ),
    DeveloperCredit(
        "JerryZ233",
        "https://github.com/JerryZ233",
        Lang.settings_developers_server_development,
        Res.drawable.jerryz233,
    ),
    DeveloperCredit(
        "MisakaTAT",
        "https://github.com/MisakaTAT",
        Lang.settings_developers_bangumi_upstream,
        Res.drawable.misakatat,
    ),
    DeveloperCredit(
        "NeKoOuO",
        "https://github.com/NeKoOuO",
        Lang.settings_developers_icon_drawing,
        Res.drawable.nekoouo,
    ),
    DeveloperCredit(
        "NickChenヰ",
        "https://github.com/nick-cjyx9",
        Lang.settings_developers_website_development,
        Res.drawable.nick,
    ),
    DeveloperCredit(
        "NieR4ever",
        "https://github.com/NieR4ever",
        Lang.settings_developers_contributor,
        Res.drawable.nier4ever,
    ),
    DeveloperCredit(
        "NihilDigit",
        "https://github.com/NihilDigit",
        Lang.settings_developers_contributor,
        Res.drawable.nihildigit,
    ),
    DeveloperCredit(
        "rdlwicked",
        "https://github.com/rdlwicked",
        Lang.settings_developers_ml_research,
        Res.drawable.rdlwicked,
    ),
    DeveloperCredit(
        "Sanlorng",
        "https://github.com/Sanlorng",
        Lang.settings_developers_contributor,
        Res.drawable.sanlorng,
    ),
    DeveloperCredit("WoLeo-Z", "https://github.com/WoLeo-Z", Lang.settings_developers_contributor, Res.drawable.woleoz),
    DeveloperCredit(
        "OpenAni",
        "https://github.com/open-ani",
        Lang.settings_developers_organization,
        FoundationRes.drawable.a,
    ),
)
