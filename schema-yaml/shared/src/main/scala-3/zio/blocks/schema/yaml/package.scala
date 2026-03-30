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

package zio.blocks.schema

import zio.blocks.schema.yaml._
import scala.quoted._
import scala.util.control.NonFatal

package object yaml {
  extension (inline sc: StringContext) {
    inline def yaml(inline args: Any*): Yaml = ${ yamlInterpolatorImpl('sc, 'args) }
  }

  private def yamlInterpolatorImpl(sc: Expr[StringContext], args: Expr[Seq[Any]])(using Quotes): Expr[Yaml] = {
    import quotes.reflect._

    val parts = sc match {
      case '{ StringContext(${ Varargs(rawParts) }: _*) } =>
        rawParts.map { case '{ $rawPart: String } => rawPart.valueOrAbort }
      case _ => report.errorAndAbort("Expected a StringContext with string literal parts")
    }
    val contexts = YamlContextDetector.detectContexts(parts) match {
      case Left(error)     => report.errorAndAbort(s"Invalid YAML structure: $error")
      case Right(contexts) => contexts
    }
    val argExprs: Seq[Expr[Any]] = args match {
      case Varargs(exprs) => exprs
      case _              => Seq.empty
    }
    if (argExprs.size != contexts.size) {
      report.errorAndAbort(
        s"Internal error: context count mismatch (${contexts.size} contexts for ${argExprs.size} args)"
      )
    }
    val processedArgs: Seq[Expr[Any]] = contexts.zip(argExprs).map { case (ctx, argExpr) =>
      val argType = argExpr.asTerm.tpe.widen
      if (argType <:< TypeRepr.of[Yaml]) {
        val typedArgExpr = argExpr.asExprOf[Yaml]
        ctx match {
          case YamlInterpolationContext.Value =>
            '{
              val v = $typedArgExpr
              if (v eq null) Yaml.NullValue
              else v
            }
          case _ => '{ $typedArgExpr.print }
        }
      } else {
        val schemaType = TypeRepr.of[Schema].appliedTo(argType)
        Implicits.search(schemaType) match {
          case success: ImplicitSearchSuccess =>
            argType.asType match {
              case '[t] =>
                val schemaExpr   = success.tree.asExprOf[Schema[t]]
                val typedArgExpr = argExpr.asExprOf[t]
                ctx match {
                  case YamlInterpolationContext.Value =>
                    '{
                      val v = $typedArgExpr
                      if (v == null) Yaml.NullValue
                      else $schemaExpr.getInstance(YamlFormat).encodeValue(v)
                    }
                  case _ => '{ $schemaExpr.getInstance(YamlFormat).encodeKey($typedArgExpr) }
                }
            }
          case _ => report.errorAndAbort(s"No Schema found for type ${argType.show}.")
        }
      }
    }
    val contextsExpr: Expr[Seq[YamlInterpolationContext]] = Expr.ofSeq(contexts.map {
      case YamlInterpolationContext.Key      => '{ YamlInterpolationContext.Key }
      case YamlInterpolationContext.Value    => '{ YamlInterpolationContext.Value }
      case YamlInterpolationContext.InString => '{ YamlInterpolationContext.InString }
    })
    val processedArgsExpr: Expr[Seq[Any]] = Expr.ofSeq(processedArgs)
    try {
      YamlInterpolatorRuntime.validateYamlLiteral(new StringContext(parts: _*), contexts)
      '{ YamlInterpolatorRuntime.yamlWithContexts($sc, $processedArgsExpr, $contextsExpr) }
    } catch {
      case error if NonFatal(error) => report.errorAndAbort(s"Invalid YAML literal: ${error.getMessage}")
    }
  }
}
