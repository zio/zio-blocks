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

/** The YAML model, codecs and options, and the `yaml"..."` interpolator. */
package object yaml {

  /** Adds the `yaml"..."` interpolator to string literals. */
  implicit class YamlStringContext(val sc: StringContext) extends AnyVal {

    /**
     * Builds a [[Yaml]] tree from a YAML literal, with Scala values spliced in.
     * The literal is read with `YamlReader` at compile time, with placeholders
     * for the splices, and again at run time with the values written in. A
     * value position, such as `port: \$port`, takes a `Yaml` tree or any value
     * with a `Schema`, encoded with its YAML codec (`null` becomes
     * `NullValue`). A key position, or a position inside a quoted string, takes
     * the value's text, as `YamlCodec#encodeKey` writes it (a `Yaml` tree is
     * printed). A spliced type with no `Schema` does not compile.
     *
     * On Scala 2.13, the expansion calls a package-private object, so it
     * compiles only inside the `zio.blocks.schema` package (a known bug). Other
     * known bugs: a spliced collection, record or `Yaml` mapping is misread; a
     * spliced value that the writer quotes, such as the string `8080` or a
     * date, keeps its quotes when it is spliced into a key or into a quoted
     * string; Scala escape sequences such as `\n` in the literal are not
     * processed; and the compile-time check uses the lenient reader, so most
     * malformed literals compile.
     *
     * {{{
     * // scala-3-only
     * val manifest = yaml"""
     * kind: Deployment
     * replicas: 3
     * """
     *
     * manifest.toJson.print // {"kind":"Deployment","replicas":3}
     * }}}
     *
     * @see
     *   [[https://zioblocks.com/reference/schema/built-in-codecs/yaml/ YAML]]
     */
    def yaml(args: Any*): Yaml = macro YamlInterpolatorMacros.yamlImpl
  }
}

/** Expands `yaml"..."` at compile time. */
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
    if (args.size != contexts.size) {
      c.abort(
        c.enclosingPosition,
        s"Internal error: context count mismatch (${contexts.size} contexts for ${args.size} args)"
      )
    }
    val processedArgs: Seq[Tree] = contexts.zip(args).map { case (ctx, argExpr) =>
      val argType = argExpr.actualType.widen
      if (argType <:< typeOf[Yaml]) {
        ctx match {
          case YamlInterpolationContext.Value =>
            val v = c.freshName(TermName("v"))
            q"""{
              val $v = ${argExpr.tree}
              if ($v.asInstanceOf[AnyRef] eq null) _root_.zio.blocks.schema.yaml.Yaml.NullValue
              else $v
            }"""
          case _ => q"${argExpr.tree}.print"
        }
      } else {
        val schemaType     = appliedType(typeOf[Schema[_]].typeConstructor, argType)
        val schemaInstance = c.inferImplicitValue(schemaType, silent = true)
        if (schemaInstance == EmptyTree) c.abort(argExpr.tree.pos, s"No Schema found for type $argType.")
        ctx match {
          case YamlInterpolationContext.Value =>
            val v = c.freshName(TermName("v"))
            q"""{
              val $v = ${argExpr.tree}
              if ($v.asInstanceOf[AnyRef] eq null) _root_.zio.blocks.schema.yaml.Yaml.NullValue
              else $schemaInstance.getInstance(_root_.zio.blocks.schema.yaml.YamlFormat).encodeValue($v)
            }"""
          case _ =>
            q"$schemaInstance.getInstance(_root_.zio.blocks.schema.yaml.YamlFormat).encodeKey(${argExpr.tree})"
        }
      }
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
