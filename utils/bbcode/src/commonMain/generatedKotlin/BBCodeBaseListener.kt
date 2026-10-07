package com.wynime.utils.bbcode

import org.antlr.v4.kotlinruntime.ParserRuleContext
import org.antlr.v4.kotlinruntime.tree.ErrorNode
import org.antlr.v4.kotlinruntime.tree.TerminalNode

public open class BBCodeBaseListener : BBCodeListener {

    override fun enterFile(ctx: BBCodeParser.FileContext) {}

    override fun exitFile(ctx: BBCodeParser.FileContext) {}

    override fun enterSection(ctx: BBCodeParser.SectionContext) {}

    override fun exitSection(ctx: BBCodeParser.SectionContext) {}

    override fun enterElement(ctx: BBCodeParser.ElementContext) {}

    override fun exitElement(ctx: BBCodeParser.ElementContext) {}

    override fun enterPlain(ctx: BBCodeParser.PlainContext) {}

    override fun exitPlain(ctx: BBCodeParser.PlainContext) {}

    override fun enterB(ctx: BBCodeParser.BContext) {}

    override fun exitB(ctx: BBCodeParser.BContext) {}

    override fun enterI(ctx: BBCodeParser.IContext) {}

    override fun exitI(ctx: BBCodeParser.IContext) {}

    override fun enterU(ctx: BBCodeParser.UContext) {}

    override fun exitU(ctx: BBCodeParser.UContext) {}

    override fun enterS(ctx: BBCodeParser.SContext) {}

    override fun exitS(ctx: BBCodeParser.SContext) {}

    override fun enterCode(ctx: BBCodeParser.CodeContext) {}

    override fun exitCode(ctx: BBCodeParser.CodeContext) {}

    override fun enterMask(ctx: BBCodeParser.MaskContext) {}

    override fun exitMask(ctx: BBCodeParser.MaskContext) {}

    override fun enterQuote(ctx: BBCodeParser.QuoteContext) {}

    override fun exitQuote(ctx: BBCodeParser.QuoteContext) {}

    override fun enterSize(ctx: BBCodeParser.SizeContext) {}

    override fun exitSize(ctx: BBCodeParser.SizeContext) {}

    override fun enterColor(ctx: BBCodeParser.ColorContext) {}

    override fun exitColor(ctx: BBCodeParser.ColorContext) {}

    override fun enterCenter(ctx: BBCodeParser.CenterContext) {}

    override fun exitCenter(ctx: BBCodeParser.CenterContext) {}

    override fun enterLeft(ctx: BBCodeParser.LeftContext) {}

    override fun exitLeft(ctx: BBCodeParser.LeftContext) {}

    override fun enterRight(ctx: BBCodeParser.RightContext) {}

    override fun exitRight(ctx: BBCodeParser.RightContext) {}

    override fun enterBgm_sticker(ctx: BBCodeParser.Bgm_stickerContext) {}

    override fun exitBgm_sticker(ctx: BBCodeParser.Bgm_stickerContext) {}

    override fun enterText_stiker(ctx: BBCodeParser.Text_stikerContext) {}

    override fun exitText_stiker(ctx: BBCodeParser.Text_stikerContext) {}

    override fun enterUrl(ctx: BBCodeParser.UrlContext) {}

    override fun exitUrl(ctx: BBCodeParser.UrlContext) {}

    override fun enterUrl_named(ctx: BBCodeParser.Url_namedContext) {}

    override fun exitUrl_named(ctx: BBCodeParser.Url_namedContext) {}

    override fun enterImg(ctx: BBCodeParser.ImgContext) {}

    override fun exitImg(ctx: BBCodeParser.ImgContext) {}

    override fun enterAttribute_value(ctx: BBCodeParser.Attribute_valueContext) {}

    override fun exitAttribute_value(ctx: BBCodeParser.Attribute_valueContext) {}

    override fun enterEveryRule(ctx: ParserRuleContext) {}

    override fun exitEveryRule(ctx: ParserRuleContext) {}

    override fun visitTerminal(node: TerminalNode) {}

    override fun visitErrorNode(node: ErrorNode) {}
}
