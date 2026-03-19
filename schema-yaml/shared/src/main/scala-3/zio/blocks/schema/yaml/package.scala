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

    val processedArgs: Seq[Expr[Any]] = if (argExprs.size == contexts.size) {
      contexts.zip(argExprs).map { case (ctx, argExpr) =>
        val argType = argExpr.asTerm.tpe.widen
        ctx match {
          case YamlInterpolationContext.Key =>
            val keyableTc   = TypeRepr.of[Keyable]
            val keyableType = keyableTc.appliedTo(argType)
            Implicits.search(keyableType) match {
              case success: ImplicitSearchSuccess =>
                argType.asType match {
                  case '[t] =>
                    val keyableExpr  = success.tree.asExprOf[Keyable[t]]
                    val typedArgExpr = argExpr.asExprOf[t]
                    '{ $keyableExpr.asKey($typedArgExpr) }
                }
              case _: ImplicitSearchFailure =>
                report.errorAndAbort(
                  s"Type ${argType.show} cannot be used as YAML key. " +
                    "Only keyable types (primitives, UUID, dates, etc.) are allowed."
                )
            }
          case YamlInterpolationContext.Value =>
            val encoderTc   = TypeRepr.of[YamlEncoder]
            val encoderType = encoderTc.appliedTo(argType)
            Implicits.search(encoderType) match {
              case success: ImplicitSearchSuccess =>
                argType.asType match {
                  case '[t] =>
                    val encoderExpr  = success.tree.asExprOf[YamlEncoder[t]]
                    val typedArgExpr = argExpr.asExprOf[t]
                    '{
                      val v = $typedArgExpr
                      if (v.asInstanceOf[AnyRef] == null) Yaml.NullValue else $encoderExpr.encode(v)
                    }
                }
              case _: ImplicitSearchFailure =>
                report.errorAndAbort(
                  s"No YamlEncoder found for type ${argType.show}. " +
                    "Add a Schema[T] or explicit YamlEncoder[T] instance."
                )
            }
          case YamlInterpolationContext.InString =>
            val keyableTc   = TypeRepr.of[Keyable]
            val keyableType = keyableTc.appliedTo(argType)
            Implicits.search(keyableType) match {
              case success: ImplicitSearchSuccess =>
                argType.asType match {
                  case '[t] =>
                    val keyableExpr  = success.tree.asExprOf[Keyable[t]]
                    val typedArgExpr = argExpr.asExprOf[t]
                    '{ $keyableExpr.asKey($typedArgExpr) }
                }
              case _: ImplicitSearchFailure =>
                report.errorAndAbort(
                  s"Type ${argType.show} cannot be used inside a YAML string literal. " +
                    "Only keyable types (primitives, UUID, dates, etc.) are allowed."
                )
            }
        }
      }
    } else {
      report.errorAndAbort(
        s"Internal error: context count mismatch (${contexts.size} contexts for ${argExprs.size} args)"
      )
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
