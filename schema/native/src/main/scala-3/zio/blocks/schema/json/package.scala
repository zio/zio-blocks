package zio.blocks.schema

import zio.blocks.schema.json._
import scala.quoted._

package object json {
  extension (inline sc: StringContext) {
    inline def json(inline args: Any*): Json = ${ jsonInterpolatorImpl('sc, 'args) }
  }

  private def jsonInterpolatorImpl(sc: Expr[StringContext], args: Expr[Seq[Any]])(using Quotes): Expr[Json] = {
    import quotes.reflect._

    // Note: Native doesn't support compile-time JSON validation like JVM does
    // Validation of argument types (stringable or JsonEncoder) is handled at compile-time
    // but JSON literal validation is deferred to runtime
    args match {
      case Varargs(argExprs) =>
        argExprs.foreach { argExpr =>
          val argType = argExpr.asTerm.tpe
          val isStringable = isStringableType(argType)
          val hasEncoder = hasJsonEncoderImplicit(argType)
          
          if (!isStringable && !hasEncoder) {
            val typeName = argType.show
            report.error(
              s"Type '$typeName' cannot be interpolated: no JsonEncoder[A] instance found. " +
              s"Supported in value position: types with JsonEncoder[A] (e.g., types with Schema.derived). " +
              s"Supported in key/string positions: stringable types only (primitives, temporal types, UUID, Currency).",
              argExpr
            )
          }
        }
      case _ => ()
    }
    
    '{ JsonInterpolatorRuntime.jsonWithInterpolation($sc, $args) }
  }

  private def isStringableType(using Quotes)(tpe: quotes.reflect.TypeRepr): Boolean = {
    val typeSymbol = tpe.typeSymbol.fullName
    val stringableTypes = Set(
      "scala.Unit", "scala.Boolean", "scala.Byte", "scala.Short", "scala.Int",
      "scala.Long", "scala.Float", "scala.Double", "scala.Char", "scala.String",
      "scala.BigInt", "scala.BigDecimal",
      "java.time.DayOfWeek", "java.time.Duration", "java.time.Instant",
      "java.time.LocalDate", "java.time.LocalDateTime", "java.time.LocalTime",
      "java.time.Month", "java.time.MonthDay", "java.time.OffsetDateTime",
      "java.time.OffsetTime", "java.time.Period", "java.time.Year", "java.time.YearMonth",
      "java.time.ZoneId", "java.time.ZoneOffset", "java.time.ZonedDateTime",
      "java.util.UUID", "java.util.Currency"
    )
    stringableTypes.contains(typeSymbol)
  }

  private def hasJsonEncoderImplicit(using Quotes)(tpe: quotes.reflect.TypeRepr): Boolean = {
    import quotes.reflect._
    try {
      val encoderType = TypeRepr.of[JsonEncoder].appliedTo(tpe)
      Implicits.search(encoderType) match {
        case _: ImplicitSearchSuccess => true
        case _ => false
      }
    } catch {
      case _ => false
    }
  }
}

