package com.wynime.app.data.models.subject

import androidx.collection.mutableIntObjectMapOf
import androidx.compose.runtime.Stable
import kotlin.jvm.JvmInline

data class RelatedPersonInfo(
    val index: Int,
    val personInfo: PersonInfo,
    val position: PersonPosition,
) {
    companion object {
        private val SORT_ORDER by lazy {
            listOf(
                PersonPosition.AnimationWork,
                PersonPosition.OriginalWork,
                PersonPosition.Director,
                PersonPosition.Script,
                PersonPosition.Music,
                PersonPosition.CharacterDesign,
                PersonPosition.SeriesComposition,
                PersonPosition.ArtDirection,
                PersonPosition.ActionAnimationDirection,
            )
        }

        val ImportanceOrder = compareBy<RelatedPersonInfo> { info ->
            val index = SORT_ORDER.indexOfFirst { position ->
                info.position == position
            }
            if (index == -1) {
                Int.MAX_VALUE
            } else {
                index
            }
        }
    }
}

@JvmInline
value class PersonPosition(val id: Int) {
    companion object {
        val Invalid = PersonPosition(-1)

        val OriginalWork = PersonPosition(1)

        val Director = PersonPosition(2)

        val Script = PersonPosition(3)

        val Storyboard = PersonPosition(4)

        val EpisodeDirector = PersonPosition(5)

        val Music = PersonPosition(6)

        val OriginalCharacterDesign = PersonPosition(7)

        val CharacterDesign = PersonPosition(8)

        val Layout = PersonPosition(9)

        val SeriesComposition = PersonPosition(10)

        val ArtDirection = PersonPosition(11)

        val ColorDesign = PersonPosition(13)

        val ChiefAnimationDirector = PersonPosition(14)

        val AnimationDirection = PersonPosition(15)

        val MechanicalDesign = PersonPosition(16)

        val DirectorOfPhotography = PersonPosition(17)

        val Supervision = PersonPosition(18)

        val PropDesign = PersonPosition(19)

        val KeyAnimation = PersonPosition(20)

        val SecondKeyAnimation = PersonPosition(21)

        val AnimationCheck = PersonPosition(22)

        val AssistantProducer = PersonPosition(23)

        val AssociateProducer = PersonPosition(24)

        val BackgroundArt = PersonPosition(25)

        val ColorSetting = PersonPosition(26)

        val DigitalPaint = PersonPosition(27)

        val Editing = PersonPosition(28)

        val OriginalPlan = PersonPosition(29)

        val ThemeSongArrangement = PersonPosition(30)

        val ThemeSongComposition = PersonPosition(31)

        val ThemeSongLyrics = PersonPosition(32)

        val ThemeSongPerformance = PersonPosition(33)

        val InsertedSongPerformance = PersonPosition(34)

        val Planning = PersonPosition(35)

        val PlanningProducer = PersonPosition(36)

        val ProductionManager = PersonPosition(37)

        val Publicity = PersonPosition(38)

        val Recording = PersonPosition(39)

        val RecordingAssistant = PersonPosition(40)

        val SeriesProductionDirector = PersonPosition(41)

        val Production = PersonPosition(42)

        val Setting = PersonPosition(43)

        val SoundDirector = PersonPosition(44)

        val Sound = PersonPosition(45)

        val SoundEffects = PersonPosition(46)

        val SpecialEffects = PersonPosition(47)

        val ADRDirector = PersonPosition(48)

        val CoDirector = PersonPosition(49)

        val BackgroundSetting = PersonPosition(50)

        val InBetweenAnimation = PersonPosition(51)

        val ExecutiveProducer = PersonPosition(52)

        val AssistantProductionCoordination = PersonPosition(56)

        val CastingDirector = PersonPosition(57)

        val ChiefProducer = PersonPosition(58)

        val CoProducer = PersonPosition(59)

        val DialogueEditing = PersonPosition(60)

        val PostProductionAssistant = PersonPosition(61)

        val ProductionAssistant = PersonPosition(62)

        val ProductionCoordination = PersonPosition(64)

        val MusicWork = PersonPosition(65)

        val SpecialThanks = PersonPosition(66)

        val AnimationWork = PersonPosition(67)

        val CGDirector = PersonPosition(69)

        val MechanicalAnimationDirection = PersonPosition(70)

        val ArtDesign = PersonPosition(71)

        val AssistantDirector = PersonPosition(72)

        val ChiefDirector = PersonPosition(74)

        val ThreeDCG = PersonPosition(75)

        val WorkAssistance = PersonPosition(76)

        val ActionAnimationDirection = PersonPosition(77)

        val SupervisingProducer = PersonPosition(80)

        val Assistance = PersonPosition(81)

        val Photography = PersonPosition(82)

        val AssistantProductionManagerAssistance = PersonPosition(83)

        val DesignManager = PersonPosition(84)

        val MusicProducer = PersonPosition(85)

        val ThreeDCGDirector = PersonPosition(86)

        val AnimationProducer = PersonPosition(87)

        val SpecialEffectsAnimationDirection = PersonPosition(88)

        val ChiefEpisodeDirection = PersonPosition(89)

        val AssistantAnimationDirection = PersonPosition(90)

        val AssistantEpisodeDirection = PersonPosition(91)

        val MainAnimator = PersonPosition(92)

        val entryRange = 1..92

        fun findByName(name: String): PersonPosition {
            for (i in entryRange) {
                if (PersonPosition(i).nameCn == name) {
                    return PersonPosition(i)
                }
            }
            return Invalid
        }
    }
}

private val nameCnMap by lazy(LazyThreadSafetyMode.PUBLICATION) {
    mutableIntObjectMapOf<String>().apply {
        put(PersonPosition.OriginalWork.id, "原作")
        put(PersonPosition.Director.id, "导演")
        put(PersonPosition.Script.id, "脚本")
        put(PersonPosition.Storyboard.id, "分镜")
        put(PersonPosition.EpisodeDirector.id, "演出")
        put(PersonPosition.Music.id, "音乐")
        put(PersonPosition.OriginalCharacterDesign.id, "人物原案")
        put(PersonPosition.CharacterDesign.id, "人物设定")
        put(PersonPosition.Layout.id, "分镜构图")
        put(PersonPosition.SeriesComposition.id, "系列构成")
        put(PersonPosition.ArtDirection.id, "美术监督")
        put(PersonPosition.ColorDesign.id, "色彩设计")
        put(PersonPosition.ChiefAnimationDirector.id, "总作画监督")
        put(PersonPosition.AnimationDirection.id, "作画监督")
        put(PersonPosition.MechanicalDesign.id, "机械设定")
        put(PersonPosition.DirectorOfPhotography.id, "摄影监督")
        put(PersonPosition.Supervision.id, "监修")
        put(PersonPosition.PropDesign.id, "道具设计")
        put(PersonPosition.KeyAnimation.id, "原画")
        put(PersonPosition.SecondKeyAnimation.id, "第二原画")
        put(PersonPosition.AnimationCheck.id, "动画检查")
        put(PersonPosition.AssistantProducer.id, "助理制片人")
        put(PersonPosition.AssociateProducer.id, "协同制片人")
        put(PersonPosition.BackgroundArt.id, "背景美术")
        put(PersonPosition.ColorSetting.id, "色彩指定")
        put(PersonPosition.DigitalPaint.id, "数码绘图")
        put(PersonPosition.Editing.id, "剪辑")
        put(PersonPosition.OriginalPlan.id, "原案")
        put(PersonPosition.ThemeSongArrangement.id, "主题歌编曲")
        put(PersonPosition.ThemeSongComposition.id, "主题歌作曲")
        put(PersonPosition.ThemeSongLyrics.id, "主题歌作词")
        put(PersonPosition.ThemeSongPerformance.id, "主题歌演出")
        put(PersonPosition.InsertedSongPerformance.id, "插入歌演出")
        put(PersonPosition.Planning.id, "企划")
        put(PersonPosition.PlanningProducer.id, "企划制片人")
        put(PersonPosition.ProductionManager.id, "制作管理")
        put(PersonPosition.Publicity.id, "宣传")
        put(PersonPosition.Recording.id, "录音")
        put(PersonPosition.RecordingAssistant.id, "录音助理")
        put(PersonPosition.SeriesProductionDirector.id, "系列监督")
        put(PersonPosition.Production.id, "制作")
        put(PersonPosition.Setting.id, "设定")
        put(PersonPosition.SoundDirector.id, "音响监督")
        put(PersonPosition.Sound.id, "音响")
        put(PersonPosition.SoundEffects.id, "音效")
        put(PersonPosition.SpecialEffects.id, "特效")
        put(PersonPosition.ADRDirector.id, "配音监督")
        put(PersonPosition.CoDirector.id, "联合导演")
        put(PersonPosition.BackgroundSetting.id, "背景设定")
        put(PersonPosition.InBetweenAnimation.id, "补间动画")
        put(PersonPosition.ExecutiveProducer.id, "执行制片人")
        put(PersonPosition.AssistantProductionCoordination.id, "助理制片协调")
        put(PersonPosition.CastingDirector.id, "演员监督")
        put(PersonPosition.ChiefProducer.id, "总制片")
        put(PersonPosition.CoProducer.id, "联合制片人")
        put(PersonPosition.DialogueEditing.id, "台词编辑")
        put(PersonPosition.PostProductionAssistant.id, "后期制片协调")
        put(PersonPosition.ProductionAssistant.id, "制作助手")
        put(PersonPosition.ProductionCoordination.id, "制作协调")
        put(PersonPosition.MusicWork.id, "音乐制作")
        put(PersonPosition.SpecialThanks.id, "友情协力")
        put(PersonPosition.AnimationWork.id, "动画制作")
        put(PersonPosition.CGDirector.id, "CG 导演")
        put(PersonPosition.MechanicalAnimationDirection.id, "机械作画监督")
        put(PersonPosition.ArtDesign.id, "美术设计")
        put(PersonPosition.AssistantDirector.id, "副导演")
        put(PersonPosition.ChiefDirector.id, "总导演")
        put(PersonPosition.ThreeDCG.id, "3DCG")
        put(PersonPosition.WorkAssistance.id, "制作协力")
        put(PersonPosition.ActionAnimationDirection.id, "动作作画监督")
        put(PersonPosition.SupervisingProducer.id, "监制")
        put(PersonPosition.Assistance.id, "协力")
        put(PersonPosition.Photography.id, "摄影")
        put(PersonPosition.AssistantProductionManagerAssistance.id, "制作进行协力")
        put(PersonPosition.DesignManager.id, "设定制作")
        put(PersonPosition.MusicProducer.id, "音乐制作人")
        put(PersonPosition.ThreeDCGDirector.id, "3DCG 导演")
        put(PersonPosition.AnimationProducer.id, "动画制片人")
        put(PersonPosition.SpecialEffectsAnimationDirection.id, "特效作画监督")
        put(PersonPosition.ChiefEpisodeDirection.id, "主演出")
        put(PersonPosition.AssistantAnimationDirection.id, "作画监督助理")
        put(PersonPosition.AssistantEpisodeDirection.id, "演出助理")
        put(PersonPosition.MainAnimator.id, "主动画师")
    }
}

@Stable
val PersonPosition.nameCn: String?
    get() = nameCnMap[id]
