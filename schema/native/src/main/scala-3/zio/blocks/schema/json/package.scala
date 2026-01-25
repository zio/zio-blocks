package zio.blocks.schema

import zio.blocks.schema.json._
import scala.quoted._

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

    // Detect interpolation contexts for type checking
    val contexts = ContextDetector.detectContexts(parts) match {
      case Left(error)     => report.errorAndAbort(s"Invalid JSON structure: $error")
      case Right(contexts) => contexts
    }

    // Extract individual arg expressions for type checking and processing
    val argExprs: Seq[Expr[Any]] = args match {
      case Varargs(exprs) => exprs.toSeq
      case _              => Seq.empty
    }

    // Validate and pre-encode args based on their context
    val processedArgs: Seq[Expr[Any]] = if (argExprs.size == contexts.size) {
      contexts.zip(argExprs).map { case (ctx, argExpr) =>
        val argType = argExpr.asTerm.tpe.widen
        ctx match {
          case InterpolationContext.Key =>
            val stringableTc   = TypeRepr.of[Stringable]
            val stringableType = stringableTc.appliedTo(argType)
            Implicits.search(stringableType) match {
              case success: ImplicitSearchSuccess =>
                // Pre-convert to String using Stringable
                argType.asType match {
                  case '[t] =>
                    val stringableExpr = success.tree.asExprOf[Stringable[t]]
                    val typedArgExpr   = argExpr.asExprOf[t]
                    '{ $stringableExpr.asString($typedArgExpr) }
                }
              case _: ImplicitSearchFailure =>
                report.errorAndAbort(
                  s"Type ${argType.show} cannot be used as JSON key. " +
                    "Only stringable types (primitives, UUID, dates, etc.) are allowed."
                )
            }
          case InterpolationContext.Value =>
            val encoderTc   = TypeRepr.of[JsonEncoder]
            val encoderType = encoderTc.appliedTo(argType)
            Implicits.search(encoderType) match {
              case success: ImplicitSearchSuccess =>
                // Pre-encode to Json using JsonEncoder, with null check
                argType.asType match {
                  case '[t] =>
                    val encoderExpr  = success.tree.asExprOf[JsonEncoder[t]]
                    val typedArgExpr = argExpr.asExprOf[t]
                    // Handle null values specially to avoid NPE
                    '{
                      val v = $typedArgExpr
                      if (v.asInstanceOf[AnyRef] == null) Json.Null else $encoderExpr.encode(v)
                    }
                }
              case _: ImplicitSearchFailure =>
                report.errorAndAbort(
                  s"No JsonEncoder found for type ${argType.show}. " +
                    "Add a Schema[T] or explicit JsonEncoder[T] instance."
                )
            }
          case InterpolationContext.InString =>
            // Validate Stringable[A] for inside-string interpolation
            val stringableTc   = TypeRepr.of[Stringable]
            val stringableType = stringableTc.appliedTo(argType)
            Implicits.search(stringableType) match {
              case success: ImplicitSearchSuccess =>
                // Pre-convert to String using Stringable
                argType.asType match {
                  case '[t] =>
                    val stringableExpr = success.tree.asExprOf[Stringable[t]]
                    val typedArgExpr   = argExpr.asExprOf[t]
                    '{ $stringableExpr.asString($typedArgExpr) }
                }
              case _: ImplicitSearchFailure =>
                report.errorAndAbort(
                  s"Type ${argType.show} cannot be used inside a JSON string literal. " +
                    "Only stringable types (primitives, UUID, dates, etc.) are allowed."
                )
            }
        }
      }
    } else {
      report.errorAndAbort(
        s"Internal error: context count mismatch (${contexts.size} contexts for ${argExprs.size} args)"
      )
    }

    // Convert contexts to runtime expression
    val contextsExpr: Expr[Seq[InterpolationContext]] = Expr.ofSeq(contexts.map {
      case InterpolationContext.Key      => '{ InterpolationContext.Key }
      case InterpolationContext.Value    => '{ InterpolationContext.Value }
      case InterpolationContext.InString => '{ InterpolationContext.InString }
    })

    // Convert processed args to Seq expression
    val processedArgsExpr: Expr[Seq[Any]] = Expr.ofSeq(processedArgs)

    // Note: Skip compile-time JSON validation on Native as it has different macro expansion behavior.
    // Type checking for Key/Value/InString contexts is still enforced above.
    '{ JsonInterpolatorRuntime.jsonWithContexts($sc, $processedArgsExpr, $contextsExpr) }
  }
}
