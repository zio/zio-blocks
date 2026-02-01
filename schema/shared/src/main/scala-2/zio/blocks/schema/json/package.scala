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
        ctx match {
          case InterpolationContext.Key =>
            val keyableTc       = typeOf[Keyable[_]].typeConstructor
            val keyableType     = appliedType(keyableTc, argType)
            val keyableInstance = c.inferImplicitValue(keyableType, silent = true)
            if (keyableInstance == EmptyTree) {
              c.abort(
                argExpr.tree.pos,
                s"Type $argType cannot be used as JSON key. " +
                  "Only keyable types (primitives, UUID, dates, etc.) are allowed."
              )
            }
            // Pre-convert to String using Keyable
            q"$keyableInstance.asKey(${argExpr.tree})"

          case InterpolationContext.Value =>
            val encoderTc       = typeOf[JsonEncoder[_]].typeConstructor
            val encoderType     = appliedType(encoderTc, argType)
            val encoderInstance = c.inferImplicitValue(encoderType, silent = true)
            if (encoderInstance == EmptyTree) {
              c.abort(
                argExpr.tree.pos,
                s"No JsonEncoder found for type $argType. " +
                  "Add a Schema[T] or explicit JsonEncoder[T] instance."
              )
            }
            // Pre-encode to Json using JsonEncoder, with null check
            val v = c.freshName(TermName("v"))
            q"""{
              val $v = ${argExpr.tree}
              if ($v.asInstanceOf[AnyRef] == null) _root_.zio.blocks.schema.json.Json.Null
              else $encoderInstance.encode($v)
            }"""

          case InterpolationContext.InString =>
            // Validate Keyable[A] for inside-string interpolation
            val keyableTc       = typeOf[Keyable[_]].typeConstructor
            val keyableType     = appliedType(keyableTc, argType)
            val keyableInstance = c.inferImplicitValue(keyableType, silent = true)
            if (keyableInstance == EmptyTree) {
              c.abort(
                argExpr.tree.pos,
                s"Type $argType cannot be used inside a JSON string literal. " +
                  "Only keyable types (primitives, UUID, dates, etc.) are allowed."
              )
            }
            // Pre-convert to String using Keyable
            q"$keyableInstance.asKey(${argExpr.tree})"
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
