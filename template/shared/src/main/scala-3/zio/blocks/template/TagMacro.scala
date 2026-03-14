package zio.blocks.template

import scala.quoted.*
import zio.blocks.chunk.Chunk

private[template] object TagMacro {

  def applyImpl(
    tag: Expr[InlineTag],
    modifier: Expr[Modifier],
    modifiers: Expr[Seq[Modifier]]
  )(using Quotes): Expr[Dom.Element] =
    extractTagName(tag) match {
      case None          => runtimeFallback(tag, modifier, modifiers)
      case Some(tagName) =>
        modifiers match {
          case Varargs(modExprs) =>
            val allMods    = modifier +: modExprs.toSeq
            val classified = allMods.map(classifyModifier)
            if (classified.exists(_.isInstanceOf[Classified.Unknown]))
              runtimeFallback(tag, modifier, modifiers)
            else
              buildDirect(tagName, classified)
          case _ =>
            runtimeFallback(tag, modifier, modifiers)
        }
    }

  def directBuild(tag: String, attrs: Chunk[Dom.Attribute], children: Chunk[Dom]): Dom.Element =
    Dom.Element.Generic(tag, attrs, children)

  private def runtimeFallback(
    tag: Expr[InlineTag],
    modifier: Expr[Modifier],
    modifiers: Expr[Seq[Modifier]]
  )(using Quotes): Expr[Dom.Element] =
    '{ $tag.runtimeApply($modifier, $modifiers*) }

  private def buildDirect(
    tagName: String,
    classified: Seq[Classified]
  )(using Quotes): Expr[Dom.Element] = {
    val attrExprs  = classified.collect { case Classified.Attr(expr) => expr }
    val childExprs = classified.collect { case Classified.Child(expr) => expr }

    val attrChunk =
      if (attrExprs.isEmpty) '{ Chunk.empty[Dom.Attribute] } else '{ Chunk(${ Varargs(attrExprs) }*) }
    val childChunk =
      if (childExprs.isEmpty) '{ Chunk.empty[Dom] } else '{ Chunk(${ Varargs(childExprs) }*) }

    '{ TagMacro.directBuild(${ Expr(tagName) }, $attrChunk, $childChunk) }
  }

  private def extractTagName(tag: Expr[InlineTag])(using Quotes): Option[String] = {
    import quotes.reflect.*
    tag.asTerm.underlying match {
      case Apply(_, List(Literal(StringConstant(name))))                               => Some(name)
      case Inlined(_, _, Apply(_, List(Literal(StringConstant(name)))))                => Some(name)
      case Inlined(_, _, Inlined(_, _, Apply(_, List(Literal(StringConstant(name)))))) => Some(name)
      case _                                                                           =>
        tag match {
          case '{ new InlineTag(${ Expr(name) }) } => Some(name)
          case _                                   => None
        }
    }
  }

  private sealed trait Classified
  private object Classified {
    final case class Attr(expr: Expr[Dom.Attribute]) extends Classified
    final case class Child(expr: Expr[Dom])          extends Classified
    final case class Unknown(expr: Expr[Modifier])   extends Classified
  }

  private def classifyModifier(mod: Expr[Modifier])(using Quotes): Classified = {
    import quotes.reflect.*
    val term = mod.asTerm.underlying
    val tpe  = term.tpe.widen

    if (tpe <:< TypeRepr.of[Dom.Attribute])
      Classified.Attr(term.asExprOf[Dom.Attribute])
    else if (tpe <:< TypeRepr.of[Dom])
      Classified.Child(term.asExprOf[Dom])
    else
      term match {
        case Apply(fn, List(inner)) =>
          val fnSym      = fn.symbol
          val ownerName  = fnSym.owner.fullName
          val methodName = fnSym.name
          if (ownerName.contains("Modifier") || ownerName.contains("template")) {
            val innerTpe = inner.tpe.widen
            if (methodName == "attributeToModifier" || innerTpe <:< TypeRepr.of[Dom.Attribute])
              Classified.Attr(inner.asExprOf[Dom.Attribute])
            else if (methodName == "domToModifier" || innerTpe <:< TypeRepr.of[Dom])
              Classified.Child(inner.asExprOf[Dom])
            else if (methodName == "stringToModifier" || innerTpe <:< TypeRepr.of[String])
              Classified.Child('{ Dom.Text(${ inner.asExprOf[String] }) })
            else
              Classified.Unknown(mod)
          } else
            Classified.Unknown(mod)
        case _ =>
          Classified.Unknown(mod)
      }
  }
}
