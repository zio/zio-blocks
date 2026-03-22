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
import scala.language.experimental.macros
import scala.reflect.macros.blackbox
import scala.util.control.NonFatal

package object json {
  implicit class JsonStringContext(val sc: StringContext) extends AnyVal {
    def json(args: Any*): Json = macro JsonInterpolatorMacros.jsonImpl
  }
}

private object JsonInterpolatorMacros {
  def jsonImpl(c: blackbox.Context)(args: c.Expr[Any]*): c.Expr[Json] = {
    import c.universe._

    val parts = c.prefix.tree match {
      case Apply(_, List(Apply(_, rawParts))) =>
        rawParts.map {
          case Literal(Constant(part: String)) => part
          case _                               => c.abort(c.enclosingPosition, "Expected string literal parts")
        }
      case _ => c.abort(c.enclosingPosition, "Expected StringContext")
    }
    val contexts = ContextDetector.detectContexts(parts) match {
      case Right(contexts) => contexts
      case Left(error)     => c.abort(c.enclosingPosition, s"Invalid JSON structure: $error")
    }
    if (args.size != contexts.size) {
      c.abort(
        c.enclosingPosition,
        s"Internal error: context count mismatch (${contexts.size} contexts for ${args.size} args)"
      )
    }
    val processedArgs = contexts.zip(args).map { case (ctx, argExpr) =>
      val argType = argExpr.actualType.widen
      if (argType <:< typeOf[Json]) {
        ctx match {
          case InterpolationContext.Value =>
            val v = c.freshName(TermName("v"))
            q"""{
              val $v = ${argExpr.tree}
              if ($v.asInstanceOf[AnyRef] eq null) _root_.zio.blocks.schema.json.Json.Null
              else $v
            }"""
          case _ => q"Json.jsonCodec.encodeKey(${argExpr.tree})"
        }
      } else {
        val schemaType     = appliedType(typeOf[Schema[_]].typeConstructor, argType)
        val schemaInstance = c.inferImplicitValue(schemaType, silent = true)
        if (schemaInstance == EmptyTree) c.abort(argExpr.tree.pos, s"No Schema found for type $argType.")
        ctx match {
          case InterpolationContext.Value =>
            val v = c.freshName(TermName("v"))
            q"""{
              val $v = ${argExpr.tree}
              if ($v.asInstanceOf[AnyRef] eq null) _root_.zio.blocks.schema.json.Json.Null
              else $schemaInstance.jsonCodec.encodeValue($v)
            }"""
          case _ =>
            q"$schemaInstance.jsonCodec.encodeKey(${argExpr.tree})"
        }
      }
    }
    val contextsExpr = contexts.map {
      case InterpolationContext.Key      => q"_root_.zio.blocks.schema.json.InterpolationContext.Key"
      case InterpolationContext.Value    => q"_root_.zio.blocks.schema.json.InterpolationContext.Value"
      case InterpolationContext.InString => q"_root_.zio.blocks.schema.json.InterpolationContext.InString"
    }
    try {
      JsonInterpolatorRuntime.validateJsonLiteral(new StringContext(parts: _*), contexts)
      val scExpr     = c.Expr[StringContext](c.prefix.tree.asInstanceOf[Apply].args.head)
      val argsExpr   = c.Expr[Seq[Any]](q"_root_.scala.Seq(..$processedArgs)")
      val ctxSeqExpr = c.Expr[Seq[InterpolationContext]](q"_root_.scala.Seq(..$contextsExpr)")
      reify(JsonInterpolatorRuntime.jsonWithContexts(scExpr.splice, argsExpr.splice, ctxSeqExpr.splice))
    } catch {
      case error if NonFatal(error) => c.abort(c.enclosingPosition, s"Invalid JSON literal: ${error.getMessage}")
    }
  }
}
