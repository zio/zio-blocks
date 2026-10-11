/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.html

import scala.quoted._
import scala.util.control.NonFatal
import zio.blocks.chunk.Chunk

/**
 * Compile-time implementations of the generic element factories
 * ([[HtmlElements.element]] and [[HtmlElements.voidElement]]).
 *
 * Both factories accept only statically-known tags — a string literal, an
 * `inline`/`final` `val` constant, or a value whose type is a concrete string
 * literal (e.g. `val tag: "my-widget" = ...`) — and validate the void
 * classification with the single shared validator ([[Dom.isVoidTag]]) at
 * compile time, ASCII case-insensitively. Anything else is rejected with a
 * diagnostic stating the failed operation, the violated requirement, and
 * concrete replacement guidance: misclassified literals name the built-in DSL
 * factory with before/after examples preserving the tag spelling plus
 * child-placement guidance; a stable `val` reference whose declared type lost
 * the literal names the `val` and shows how to recover the literal type;
 * everything else points at the dynamic `Either`-based factories with explicit
 * `Left` / `Right` handling.
 */
private[html] object ElementMacros {

  def elementImpl(tag: Expr[String])(using Quotes): Expr[Dom.Element] = {
    val name = staticTag(tag, "element")
    if (Dom.isVoidTag(name)) quotes.reflect.report.errorAndAbort(voidTagStaticError(name))
    else '{ Dom.Element.Generic(${ Expr(name) }, Chunk.empty, Chunk.empty) }
  }

  def voidElementImpl(tag: Expr[String])(using Quotes): Expr[Dom.Element.Void] = {
    val name = staticTag(tag, "voidElement")
    if (!Dom.isVoidTag(name)) quotes.reflect.report.errorAndAbort(nonVoidTagStaticError(name))
    else '{ Dom.Element.VoidGeneric(${ Expr(name) }, Chunk.empty) }
  }

  /**
   * Renders a factory call for a known literal tag, preserving its spelling
   * (e.g. `element("BR")`).
   */
  private def staticCall(factory: String, tag: String): String = s"""$factory("$tag")"""

  /** Names the dynamic `Either`-based counterpart of a static factory. */
  private def dynamicFactory(staticFactory: String): String = s"${staticFactory}Dynamic"

  private def appliedWithAttrs(fragment: String): String =
    if (fragment.endsWith("()")) fragment.dropRight(2) + """(id := "gap")"""
    else fragment + """(id := "gap")"""

  private def voidTagStaticError(tag: String): String = {
    val replacement = Dom.dslFactoryName(tag).getOrElse(staticCall("voidElement", tag))
    val before      = staticCall("element", tag)
    val beforeAttrs = s"""element("$tag")(id := "gap")"""
    s"Cannot create <$tag> with element: <$tag> is a void HTML tag.\n" +
      s"\nUse the built-in $replacement factory:\n" +
      s"  ${before.padTo(beforeAttrs.length, ' ')} → $replacement\n" +
      s"  $beforeAttrs → ${appliedWithAttrs(replacement)}\n" +
      s"\n${Dom.voidGuidance(replacement)}"
  }

  private def nonVoidTagStaticError(tag: String): String = {
    val (replacement, header) = Dom.dslFactoryName(tag) match {
      case Some(dsl) => (dsl, s"Use the built-in $dsl factory:")
      case None      => (staticCall("element", tag), "Use the generic element factory:")
    }
    val before      = staticCall("voidElement", tag)
    val beforeAttrs = s"""voidElement("$tag")(id := "gap")"""
    s"Cannot create <$tag> with voidElement: <$tag> is not a void HTML tag.\n" +
      s"\n$header\n" +
      s"  ${before.padTo(beforeAttrs.length, ' ')} → $replacement\n" +
      s"  $beforeAttrs → ${appliedWithAttrs(replacement)}\n" +
      s"\n${Dom.nonVoidGuidance(tag, replacement)}"
  }

  private def dynamicMatch(staticFactory: String): String = {
    val dynamic = dynamicFactory(staticFactory)
    s"$dynamic(tag) match { case Right(element) => ...; case Left(error) => ... }"
  }

  /** Fixed-literal example tag matching the factory's category. */
  private def exampleTag(staticFactory: String): String =
    if (staticFactory == "element") "div" else "br"

  private def literalAlternatives(staticFactory: String): String = {
    val example = exampleTag(staticFactory)
    s"""for a fixed tag, pass a literal instead, e.g. inline val tag = "$example" or val tag: "$example" = "$example", """ +
      s"or forward an inline parameter, e.g. inline def build(inline tag: String) = $staticFactory(tag), called with a literal"
  }

  private def runtimeTagError(staticFactory: String): String = {
    val dynamic = dynamicFactory(staticFactory)
    s"$staticFactory(tag) failed: tag is not a compile-time string literal, so the void check cannot run at compile time.\n" +
      s"To fix:\n" +
      s"- for a runtime tag, use $dynamic(tag) and handle both outcomes explicitly: ${dynamicMatch(staticFactory)};\n" +
      s"- ${literalAlternatives(staticFactory)}."
  }

  private def widenedTagError(staticFactory: String, valName: String): String = {
    val dynamic = dynamicFactory(staticFactory)
    val example = exampleTag(staticFactory)
    s"$staticFactory(tag) failed: tag is not a compile-time string literal: it refers to val `$valName`, whose declared type is not a literal type.\n" +
      s"To fix:\n" +
      s"""- if the value is a fixed literal, give it a literal type, e.g. val tag: "$example" = "$example", or use an inline val;\n""" +
      s"- otherwise, for a runtime tag, use $dynamic(tag) and handle both outcomes explicitly: ${dynamicMatch(staticFactory)}."
  }

  /**
   * Resolves the tag to a compile-time string, accepting string literals
   * (including `inline val` references, which are inlined before expansion),
   * `final val` constants, and concrete string-literal types. Aborts with an
   * actionable error otherwise.
   */
  private def staticTag(tag: Expr[String], staticFactory: String)(using Quotes): String =
    tag.value.orElse(literalType(tag)) match {
      case Some(name) => name
      case None       =>
        stableValName(tag) match {
          case Some(valName) => quotes.reflect.report.errorAndAbort(widenedTagError(staticFactory, valName))
          case None          => quotes.reflect.report.errorAndAbort(runtimeTagError(staticFactory))
        }
    }

  /**
   * Reads the tag from its type when the type is a concrete string literal
   * (e.g. a `final val` constant reference or an explicitly ascribed
   * `val tag: "my-widget"`). Returns `None` for plain `String` and for abstract
   * or unresolved singleton types.
   */
  private def literalType(tag: Expr[String])(using Quotes): Option[String] = {
    import quotes.reflect._
    tag.asTerm.tpe.dealias match {
      case ConstantType(StringConstant(name)) => Some(name)
      case _                                  => None
    }
  }

  /**
   * Names the stable `val` a tag argument refers to, when it is one. Matches
   * references (through any `inline` / `Typed` wrappers) to non-mutable,
   * non-lazy value definitions — including `final val`s whose declared type
   * widened the literal — without inspecting the value itself, so no guessing
   * is involved. Returns `None` for parameters, `def`s, `var`s, and complex
   * expressions.
   */
  private def stableValName(tag: Expr[String])(using Quotes): Option[String] = {
    import quotes.reflect._
    def strip(term: Term): Term = term match {
      case Inlined(_, _, expansion) => strip(expansion)
      case Typed(expression, _)     => strip(expression)
      case _                        => term
    }
    try {
      strip(tag.asTerm) match {
        case ref @ (_: Ident | _: Select) =>
          val symbol = ref.symbol
          if (
            symbol.isValDef && !symbol.flags.is(Flags.Param) && !symbol.flags.is(Flags.Mutable) && !symbol.flags
              .is(Flags.Lazy)
          ) Some(symbol.name)
          else None
        case _ => None
      }
    } catch { case NonFatal(_) => None }
  }
}
