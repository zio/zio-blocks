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
import scala.language.experimental.macros
import scala.reflect.macros.blackbox
import scala.util.control.NonFatal

package object yaml {
  implicit class YamlStringContext(val sc: StringContext) extends AnyVal {
    def yaml(args: Any*): Yaml = macro YamlInterpolatorMacros.yamlImpl
  }
}

private object YamlInterpolatorMacros {
  def yamlImpl(c: blackbox.Context)(args: c.Expr[Any]*): c.Expr[Yaml] = {
    import c.universe._

    val parts = c.prefix.tree match {
      case Apply(_, List(Apply(_, rawParts))) =>
        rawParts.map {
          case Literal(Constant(part: String)) => part
          case _                               => c.abort(c.enclosingPosition, "Expected string literal parts")
        }
      case _ => c.abort(c.enclosingPosition, "Expected StringContext")
    }

    val contexts = YamlContextDetector.detectContexts(parts) match {
      case Left(error)     => c.abort(c.enclosingPosition, s"Invalid YAML structure: $error")
      case Right(contexts) => contexts
    }

    val processedArgs: Seq[Tree] = if (args.size == contexts.size) {
      contexts.zip(args).map { case (ctx, argExpr) =>
        val argType = argExpr.actualType.widen
        ctx match {
          case YamlInterpolationContext.Key =>
            val keyableTc       = typeOf[YamlKeyable[_]].typeConstructor
            val keyableType     = appliedType(keyableTc, argType)
            val keyableInstance = c.inferImplicitValue(keyableType, silent = true)
            if (keyableInstance == EmptyTree) {
              c.abort(
                argExpr.tree.pos,
                s"Type $argType cannot be used as YAML key. " +
                  "Only keyable types (primitives, UUID, dates, etc.) are allowed."
              )
            }
            q"$keyableInstance.asKey(${argExpr.tree})"

          case YamlInterpolationContext.Value =>
            val encoderTc       = typeOf[YamlEncoder[_]].typeConstructor
            val encoderType     = appliedType(encoderTc, argType)
            val encoderInstance = c.inferImplicitValue(encoderType, silent = true)
            if (encoderInstance == EmptyTree) {
              c.abort(
                argExpr.tree.pos,
                s"No YamlEncoder found for type $argType. " +
                  "Add a Schema[T] or explicit YamlEncoder[T] instance."
              )
            }
            val v = c.freshName(TermName("v"))
            q"""{
              val $v = ${argExpr.tree}
              if ($v.asInstanceOf[AnyRef] == null) _root_.zio.blocks.schema.yaml.Yaml.NullValue
              else $encoderInstance.encode($v)
            }"""

          case YamlInterpolationContext.InString =>
            val keyableTc       = typeOf[YamlKeyable[_]].typeConstructor
            val keyableType     = appliedType(keyableTc, argType)
            val keyableInstance = c.inferImplicitValue(keyableType, silent = true)
            if (keyableInstance == EmptyTree) {
              c.abort(
                argExpr.tree.pos,
                s"Type $argType cannot be used inside a YAML string literal. " +
                  "Only keyable types (primitives, UUID, dates, etc.) are allowed."
              )
            }
            q"$keyableInstance.asKey(${argExpr.tree})"
        }
      }
    } else {
      c.abort(
        c.enclosingPosition,
        s"Internal error: context count mismatch (${contexts.size} contexts for ${args.size} args)"
      )
    }

    val contextsExpr = contexts.map {
      case YamlInterpolationContext.Key      => q"_root_.zio.blocks.schema.yaml.YamlInterpolationContext.Key"
      case YamlInterpolationContext.Value    => q"_root_.zio.blocks.schema.yaml.YamlInterpolationContext.Value"
      case YamlInterpolationContext.InString => q"_root_.zio.blocks.schema.yaml.YamlInterpolationContext.InString"
    }

    try {
      YamlInterpolatorRuntime.validateYamlLiteral(new StringContext(parts: _*), contexts)
      val scExpr     = c.Expr[StringContext](c.prefix.tree.asInstanceOf[Apply].args.head)
      val argsExpr   = c.Expr[Seq[Any]](q"_root_.scala.Seq(..$processedArgs)")
      val ctxSeqExpr = c.Expr[Seq[YamlInterpolationContext]](q"_root_.scala.Seq(..$contextsExpr)")
      reify(YamlInterpolatorRuntime.yamlWithContexts(scExpr.splice, argsExpr.splice, ctxSeqExpr.splice))
    } catch {
      case error if NonFatal(error) => c.abort(c.enclosingPosition, s"Invalid YAML literal: ${error.getMessage}")
    }
  }
}
