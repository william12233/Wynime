package com.wynime.utils.bbcode

import org.antlr.v4.kotlinruntime.tree.ParseTreeVisitor

public interface BBCodeVisitor<T> : ParseTreeVisitor<T> {

    public fun visitFile(ctx: BBCodeParser.FileContext): T

    public fun visitSection(ctx: BBCodeParser.SectionContext): T

    public fun visitElement(ctx: BBCodeParser.ElementContext): T

    public fun visitPlain(ctx: BBCodeParser.PlainContext): T

    public fun visitB(ctx: BBCodeParser.BContext): T

    public fun visitI(ctx: BBCodeParser.IContext): T

    public fun visitU(ctx: BBCodeParser.UContext): T

    public fun visitS(ctx: BBCodeParser.SContext): T

    public fun visitCode(ctx: BBCodeParser.CodeContext): T

    public fun visitMask(ctx: BBCodeParser.MaskContext): T

    public fun visitQuote(ctx: BBCodeParser.QuoteContext): T

    public fun visitSize(ctx: BBCodeParser.SizeContext): T

    public fun visitColor(ctx: BBCodeParser.ColorContext): T

    public fun visitCenter(ctx: BBCodeParser.CenterContext): T

    public fun visitLeft(ctx: BBCodeParser.LeftContext): T

    public fun visitRight(ctx: BBCodeParser.RightContext): T

    public fun visitBgm_sticker(ctx: BBCodeParser.Bgm_stickerContext): T

    public fun visitText_stiker(ctx: BBCodeParser.Text_stikerContext): T

    public fun visitUrl(ctx: BBCodeParser.UrlContext): T

    public fun visitUrl_named(ctx: BBCodeParser.Url_namedContext): T

    public fun visitImg(ctx: BBCodeParser.ImgContext): T

    public fun visitAttribute_value(ctx: BBCodeParser.Attribute_valueContext): T

}
