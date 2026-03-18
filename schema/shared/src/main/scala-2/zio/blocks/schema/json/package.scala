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

    // Detect interpolation contexts for type checking
    val contexts = ContextDetector.detectContexts(parts) match {
      case Left(error)     => c.abort(c.enclosingPosition, s"Invalid JSON structure: $error")
      case Right(contexts) => contexts
    }

    // Validate and pre-encode args based on their context
    val processedArgs: Seq[Tree] = if (args.size == contexts.size) {
      contexts.zip(args).map { case (ctx, argExpr) =>
        val argType = argExpr.actualType.widen
        if (argType <:< typeOf[Json]) {
          ctx match {
            case InterpolationContext.Value =>
              val v = c.freshName(TermName("v"))
              q"""{
                val $v = ${argExpr.tree}
                if ($v.asInstanceOf[AnyRef] eq null) _root_.zio.blocks.schema.json.Json.Null
                else Json.jsonCodec.encodeValue($v)
              }"""
            case _ => q"Json.jsonCodec.encodeKey(${argExpr.tree})"
          }
        } else {
          ctx match {
            case InterpolationContext.Value =>
              val schemaType     = appliedType(typeOf[Schema[_]].typeConstructor, argType)
              val schemaInstance = c.inferImplicitValue(schemaType, silent = true)
              if (schemaInstance == EmptyTree) {
                c.abort(argExpr.tree.pos, s"No Schema found for type $argType.")
              }
              val v = c.freshName(TermName("v"))
              q"""{
                val $v = ${argExpr.tree}
                if ($v.asInstanceOf[AnyRef] eq null) _root_.zio.blocks.schema.json.Json.Null
                else $schemaInstance.getInstance(_root_.zio.blocks.schema.json.JsonFormat).encodeValue($v)
              }"""
            case _ =>
              val schemaType     = appliedType(typeOf[Schema[_]].typeConstructor, argType)
              val schemaInstance = c.inferImplicitValue(schemaType, silent = true)
              if (schemaInstance == EmptyTree) {
                c.abort(argExpr.tree.pos, s"No Schema found for type $argType.")
              }
              q"$schemaInstance.getInstance(_root_.zio.blocks.schema.json.JsonFormat).encodeKey(${argExpr.tree})"
          }
        }
      }
    } else {
      c.abort(
        c.enclosingPosition,
        s"Internal error: context count mismatch (${contexts.size} contexts for ${args.size} args)"
      )
    }

    // Convert contexts to runtime expression
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
