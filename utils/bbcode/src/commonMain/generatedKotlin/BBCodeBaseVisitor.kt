package com.wynime.utils.bbcode

import org.antlr.v4.kotlinruntime.tree.AbstractParseTreeVisitor

public abstract class BBCodeBaseVisitor<T> : AbstractParseTreeVisitor<T>(), BBCodeVisitor<T> {

    override fun visitFile(ctx: BBCodeParser.FileContext): T {
        return this.visitChildren(ctx)
    }

    override fun visitSection(ctx: BBCodeParser.SectionContext): T {
        return this.visitChildren(ctx)
    }

    override fun visitElement(ctx: BBCodeParser.ElementContext): T {
        return this.visitChildren(ctx)
    }

    override fun visitPlain(ctx: BBCodeParser.PlainContext): T {
        return this.visitChildren(ctx)
    }

    override fun visitB(ctx: BBCodeParser.BContext): T {
        return this.visitChildren(ctx)
    }

    override fun visitI(ctx: BBCodeParser.IContext): T {
        return this.visitChildren(ctx)
    }

    override fun visitU(ctx: BBCodeParser.UContext): T {
        return this.visitChildren(ctx)
    }

    override fun visitS(ctx: BBCodeParser.SContext): T {
        return this.visitChildren(ctx)
    }

    override fun visitCode(ctx: BBCodeParser.CodeContext): T {
        return this.visitChildren(ctx)
    }

    override fun visitMask(ctx: BBCodeParser.MaskContext): T {
        return this.visitChildren(ctx)
    }

    override fun visitQuote(ctx: BBCodeParser.QuoteContext): T {
        return this.visitChildren(ctx)
    }

    override fun visitSize(ctx: BBCodeParser.SizeContext): T {
        return this.visitChildren(ctx)
    }

    override fun visitColor(ctx: BBCodeParser.ColorContext): T {
        return this.visitChildren(ctx)
    }

    override fun visitCenter(ctx: BBCodeParser.CenterContext): T {
        return this.visitChildren(ctx)
    }

    override fun visitLeft(ctx: BBCodeParser.LeftContext): T {
        return this.visitChildren(ctx)
    }

    override fun visitRight(ctx: BBCodeParser.RightContext): T {
        return this.visitChildren(ctx)
    }

    override fun visitBgm_sticker(ctx: BBCodeParser.Bgm_stickerContext): T {
        return this.visitChildren(ctx)
    }

    override fun visitText_stiker(ctx: BBCodeParser.Text_stikerContext): T {
        return this.visitChildren(ctx)
    }

    override fun visitUrl(ctx: BBCodeParser.UrlContext): T {
        return this.visitChildren(ctx)
    }

    override fun visitUrl_named(ctx: BBCodeParser.Url_namedContext): T {
        return this.visitChildren(ctx)
    }

    override fun visitImg(ctx: BBCodeParser.ImgContext): T {
        return this.visitChildren(ctx)
    }

    override fun visitAttribute_value(ctx: BBCodeParser.Attribute_valueContext): T {
        return this.visitChildren(ctx)
    }
}
