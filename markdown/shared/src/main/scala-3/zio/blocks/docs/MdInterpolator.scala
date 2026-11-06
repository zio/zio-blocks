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

package zio.blocks.docs

import scala.quoted._

/**
 * Adds the `md` string interpolator, which builds a [[Doc]] from Markdown text.
 * The package object `zio.blocks.docs` extends this trait, so
 * `import zio.blocks.docs._` brings `md` into scope on Scala 2 and Scala 3.
 *
 * @see
 *   [[https://zioblocks.com/reference/docs/ Docs]]
 */
trait MdInterpolator {

  extension (inline sc: StringContext) {

    /**
     * Build a [[Doc]] from Markdown text with interpolated values.
     *
     * The literal parts are checked when the code compiles: they are joined
     * with a placeholder word and parsed, and a failure is a compile error such
     * as `Invalid markdown: Unclosed code fence`. Each interpolated value needs
     * a [[ToMarkdown]] instance, or the code does not compile (`No ToMarkdown
     * instance found for type T`).
     *
     * At run time each value is converted with its `ToMarkdown` instance,
     * written out as Markdown text, spliced between the literal parts, and the
     * whole text is parsed with [[Parser.parse]]. So Markdown syntax inside an
     * interpolated `String` takes effect (`"*a*"` becomes emphasis), and a
     * value that makes the text invalid throws an `IllegalArgumentException`
     * with the message `Invalid markdown: ...`. The literal text is not
     * de-indented: an indented `# Title` line in a triple-quoted literal is a
     * paragraph, not a heading.
     *
     * @example
     *   {{{
     * val version = "1.4"
     * val fixes = List("login", "export")
     *
     * val doc = md"""## Release \$version
     * Fixed: \$fixes"""
     *
     * val markdown = doc.toString
     * // ## Release 1.4
     * // Fixed: login, export
     *   }}}
     *
     * @param args
     *   The values to splice in, each with a [[ToMarkdown]] instance
     * @return
     *   The parsed document
     */
    inline def md(inline args: Any*): Doc = ${ MdMacros.mdImpl('sc, 'args) }
  }
}

private[docs] object MdMacros {

  def mdImpl(sc: Expr[StringContext], args: Expr[Seq[Any]])(using Quotes): Expr[Doc] = {
    import quotes.reflect._

    val parts = sc match {
      case '{ StringContext(${ Varargs(rawParts) }: _*) } =>
        rawParts.map { case '{ $rawPart: String } => rawPart.valueOrAbort }
      case _ => report.errorAndAbort("Expected a StringContext with string literal parts")
    }

    val testInput = parts.mkString("X")
    Parser.parse(testInput) match {
      case Left(err) => report.errorAndAbort(s"Invalid markdown: ${err.message}")
      case Right(_)  =>
    }

    val argExprs: Seq[Expr[Any]] = args match {
      case Varargs(exprs) => exprs.toSeq
      case _              => Seq.empty
    }

    val processedArgs: Seq[Expr[Inline]] = argExprs.map { argExpr =>
      val argType        = argExpr.asTerm.tpe.widen
      val toMarkdownTc   = TypeRepr.of[ToMarkdown]
      val toMarkdownType = toMarkdownTc.appliedTo(argType)
      Implicits.search(toMarkdownType) match {
        case success: ImplicitSearchSuccess =>
          argType.asType match {
            case '[t] =>
              val instanceExpr = success.tree.asExprOf[ToMarkdown[t]]
              val typedArgExpr = argExpr.asExprOf[t]
              '{ $instanceExpr.toMarkdown($typedArgExpr) }
          }
        case _: ImplicitSearchFailure =>
          report.errorAndAbort(s"No ToMarkdown instance found for type ${argType.show}")
      }
    }

    val processedArgsExpr: Expr[Seq[Inline]] = Expr.ofSeq(processedArgs)
    '{ MdInterpolatorRuntime.parseAndBuild($sc, $processedArgsExpr) }
  }
}
