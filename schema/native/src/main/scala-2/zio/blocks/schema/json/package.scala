package zio.blocks.schema

import zio.blocks.schema.json._
import scala.language.experimental.macros
import scala.reflect.macros.blackbox

package object json {
  implicit class JsonStringContext(val sc: StringContext) extends AnyVal {
    def json(args: Any*): Json = macro JsonInterpolatorMacros.jsonImpl
  }
}

private object JsonInterpolatorMacros {
  def jsonImpl(c: blackbox.Context)(args: c.Expr[Any]*): c.Expr[Json] = {
    import c.universe._

    // Validate arguments - must be stringable or have JsonEncoder
    args.foreach { argExpr =>
      val argType      = argExpr.actualType
      val isStringable = isStringableType(c)(argType)
      val hasEncoder   = hasJsonEncoderImplicit(c)(argType)

      if (!isStringable && !hasEncoder) {
        val typeName = argType.toString
        c.error(
          argExpr.tree.pos,
          s"Type '$typeName' cannot be interpolated: no JsonEncoder[A] instance found. " +
            s"Supported in value position: types with JsonEncoder[A] (e.g., types with Schema.derived). " +
            s"Supported in key/string positions: stringable types only (primitives, temporal types, UUID, Currency)."
        )
      }
    }

    // Native compilation can't validate JSON literals at compile time
    // Keep only argument type validation above
    val scExpr   = c.Expr[StringContext](c.prefix.tree.asInstanceOf[Apply].args.head)
    val argsExpr = c.Expr[Seq[Any]](q"Seq(..$args)")
    reify(JsonInterpolatorRuntime.jsonWithInterpolation(scExpr.splice, argsExpr.splice))
  }

  private def isStringableType(c: blackbox.Context)(tpe: c.universe.Type): Boolean = {
    import c.universe._
    val stringableTypes = Set(
      typeOf[scala.Unit].typeSymbol.fullName,
      typeOf[scala.Boolean].typeSymbol.fullName,
      typeOf[scala.Byte].typeSymbol.fullName,
      typeOf[scala.Short].typeSymbol.fullName,
      typeOf[scala.Int].typeSymbol.fullName,
      typeOf[scala.Long].typeSymbol.fullName,
      typeOf[scala.Float].typeSymbol.fullName,
      typeOf[scala.Double].typeSymbol.fullName,
      typeOf[scala.Char].typeSymbol.fullName,
      typeOf[scala.Predef.String].typeSymbol.fullName,
      typeOf[scala.BigInt].typeSymbol.fullName,
      typeOf[scala.BigDecimal].typeSymbol.fullName,
      typeOf[java.time.DayOfWeek].typeSymbol.fullName,
      typeOf[java.time.Duration].typeSymbol.fullName,
      typeOf[java.time.Instant].typeSymbol.fullName,
      typeOf[java.time.LocalDate].typeSymbol.fullName,
      typeOf[java.time.LocalDateTime].typeSymbol.fullName,
      typeOf[java.time.LocalTime].typeSymbol.fullName,
      typeOf[java.time.Month].typeSymbol.fullName,
      typeOf[java.time.MonthDay].typeSymbol.fullName,
      typeOf[java.time.OffsetDateTime].typeSymbol.fullName,
      typeOf[java.time.OffsetTime].typeSymbol.fullName,
      typeOf[java.time.Period].typeSymbol.fullName,
      typeOf[java.time.Year].typeSymbol.fullName,
      typeOf[java.time.YearMonth].typeSymbol.fullName,
      typeOf[java.time.ZoneId].typeSymbol.fullName,
      typeOf[java.time.ZoneOffset].typeSymbol.fullName,
      typeOf[java.time.ZonedDateTime].typeSymbol.fullName,
      typeOf[java.util.UUID].typeSymbol.fullName,
      typeOf[java.util.Currency].typeSymbol.fullName
    )
    stringableTypes.contains(tpe.typeSymbol.fullName)
  }

  private def hasJsonEncoderImplicit(c: blackbox.Context)(tpe: c.universe.Type): Boolean = {
    import c.universe._
    try {
      val encoderType = appliedType(typeOf[JsonEncoder[_]].typeConstructor, List(tpe))
      c.inferImplicitValue(encoderType) != EmptyTree
    } catch {
      case _: Throwable => false
    }
  }
}
