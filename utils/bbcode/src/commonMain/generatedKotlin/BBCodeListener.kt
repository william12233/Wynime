package com.wynime.utils.bbcode

import org.antlr.v4.kotlinruntime.tree.ParseTreeListener

public interface BBCodeListener : ParseTreeListener {

    public fun enterFile(ctx: BBCodeParser.FileContext)

    public fun exitFile(ctx: BBCodeParser.FileContext)

    public fun enterSection(ctx: BBCodeParser.SectionContext)

    public fun exitSection(ctx: BBCodeParser.SectionContext)

    public fun enterElement(ctx: BBCodeParser.ElementContext)

    public fun exitElement(ctx: BBCodeParser.ElementContext)

    public fun enterPlain(ctx: BBCodeParser.PlainContext)

    public fun exitPlain(ctx: BBCodeParser.PlainContext)

    public fun enterB(ctx: BBCodeParser.BContext)

    public fun exitB(ctx: BBCodeParser.BContext)

    public fun enterI(ctx: BBCodeParser.IContext)

    public fun exitI(ctx: BBCodeParser.IContext)

    public fun enterU(ctx: BBCodeParser.UContext)

    public fun exitU(ctx: BBCodeParser.UContext)

    public fun enterS(ctx: BBCodeParser.SContext)

    public fun exitS(ctx: BBCodeParser.SContext)

    public fun enterCode(ctx: BBCodeParser.CodeContext)

    public fun exitCode(ctx: BBCodeParser.CodeContext)

    public fun enterMask(ctx: BBCodeParser.MaskContext)

    public fun exitMask(ctx: BBCodeParser.MaskContext)

    public fun enterQuote(ctx: BBCodeParser.QuoteContext)

    public fun exitQuote(ctx: BBCodeParser.QuoteContext)

    public fun enterSize(ctx: BBCodeParser.SizeContext)

    public fun exitSize(ctx: BBCodeParser.SizeContext)

    public fun enterColor(ctx: BBCodeParser.ColorContext)

    public fun exitColor(ctx: BBCodeParser.ColorContext)

    public fun enterCenter(ctx: BBCodeParser.CenterContext)

    public fun exitCenter(ctx: BBCodeParser.CenterContext)

    public fun enterLeft(ctx: BBCodeParser.LeftContext)

    public fun exitLeft(ctx: BBCodeParser.LeftContext)

    public fun enterRight(ctx: BBCodeParser.RightContext)

    public fun exitRight(ctx: BBCodeParser.RightContext)

    public fun enterBgm_sticker(ctx: BBCodeParser.Bgm_stickerContext)

    public fun exitBgm_sticker(ctx: BBCodeParser.Bgm_stickerContext)

    public fun enterText_stiker(ctx: BBCodeParser.Text_stikerContext)

    public fun exitText_stiker(ctx: BBCodeParser.Text_stikerContext)

    public fun enterUrl(ctx: BBCodeParser.UrlContext)

    public fun exitUrl(ctx: BBCodeParser.UrlContext)

    public fun enterUrl_named(ctx: BBCodeParser.Url_namedContext)

    public fun exitUrl_named(ctx: BBCodeParser.Url_namedContext)

    public fun enterImg(ctx: BBCodeParser.ImgContext)

    public fun exitImg(ctx: BBCodeParser.ImgContext)

    public fun enterAttribute_value(ctx: BBCodeParser.Attribute_valueContext)

    public fun exitAttribute_value(ctx: BBCodeParser.Attribute_valueContext)

}
