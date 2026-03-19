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

import zio.blocks.schema.json._
import scala.quoted._
import scala.util.control.NonFatal

package object json {
  extension (inline sc: StringContext) {
    inline def json(inline args: Any*): Json = ${ jsonInterpolatorImpl('sc, 'args) }
  }

  private def jsonInterpolatorImpl(sc: Expr[StringContext], args: Expr[Seq[Any]])(using Quotes): Expr[Json] = {
    import quotes.reflect._

    val parts = sc match {
      case '{ StringContext(${ Varargs(rawParts) }: _*) } =>
        rawParts.map { case '{ $rawPart: String } => rawPart.valueOrAbort }
      case _ => report.errorAndAbort("Expected a StringContext with string literal parts")
    }
    val contexts = ContextDetector.detectContexts(parts) match {
      case Right(contexts) => contexts
      case Left(error)     => report.errorAndAbort(s"Invalid JSON structure: $error")
    }
    val argExprs = args match {
      case Varargs(exprs) => exprs
      case _              => Seq.empty
    }
    if (argExprs.size != contexts.size) {
      report.errorAndAbort(
        s"Internal error: context count mismatch (${contexts.size} contexts for ${argExprs.size} args)"
      )
    }
    val processedArgs = contexts.zip(argExprs).map { case (ctx, argExpr) =>
      val argType = argExpr.asTerm.tpe.widen
      if (argType <:< TypeRepr.of[Json]) {
        val typedArgExpr = argExpr.asExprOf[Json]
        ctx match {
          case InterpolationContext.Value =>
            '{
              val v = $typedArgExpr
              if (v eq null) Json.Null
              else v
            }
          case _ => '{ Json.jsonCodec.encodeKey($typedArgExpr) }
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
                  case InterpolationContext.Value =>
                    '{
                      val v = $typedArgExpr
                      if (v == null) Json.Null
                      else $schemaExpr.jsonCodec.encodeValue(v)
                    }
                  case _ => '{ $schemaExpr.jsonCodec.encodeKey($typedArgExpr) }
                }
            }
          case _ => report.errorAndAbort(s"No Schema found for type ${argType.show}.")
        }
      }
    }
    val contextsExpr: Expr[Seq[InterpolationContext]] = Expr.ofSeq(contexts.map {
      case InterpolationContext.Key      => '{ InterpolationContext.Key }
      case InterpolationContext.Value    => '{ InterpolationContext.Value }
      case InterpolationContext.InString => '{ InterpolationContext.InString }
    })
    val processedArgsExpr: Expr[Seq[Any]] = Expr.ofSeq(processedArgs)
    try {
      JsonInterpolatorRuntime.validateJsonLiteral(new StringContext(parts: _*), contexts)
      '{ JsonInterpolatorRuntime.jsonWithContexts($sc, $processedArgsExpr, $contextsExpr) }
    } catch {
      case error if NonFatal(error) => report.errorAndAbort(s"Invalid JSON literal: ${error.getMessage}")
    }
  }
}
