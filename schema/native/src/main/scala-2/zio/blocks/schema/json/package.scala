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

    val parts = c.prefix.tree match {
      case Apply(_, List(Apply(_, rawParts))) =>
        rawParts.map {
          case Literal(Constant(part: String)) => part
          case _                               => c.abort(c.enclosingPosition, "Expected string literal parts")
        }
      case _ => c.abort(c.enclosingPosition, "Expected StringContext")
    }

    val contexts: Array[Byte] = computeHoleContexts(parts)

    // Validate arguments per-context
    args.zipWithIndex.foreach { case (argExpr, idx) =>
      val argType = argExpr.actualType
      val ctx     = if (idx < contexts.length) contexts(idx) else JsonInterpolatorRuntime.CtxValue

      if (ctx == JsonInterpolatorRuntime.CtxKey) {
        if (!isStringableType(c)(argType)) {
          c.error(
            argExpr.tree.pos,
            s"Key interpolation requires a stringable (PrimitiveType) type, but found '$argType'. " +
              s"Fix: convert to String or interpolate a PrimitiveType (primitives, temporal types, UUID, Currency)."
          )
        }
      } else if (ctx == JsonInterpolatorRuntime.CtxString) {
        if (!isStringableType(c)(argType)) {
          c.error(
            argExpr.tree.pos,
            s"String-literal interpolation requires a stringable (PrimitiveType) type, but found '$argType'. " +
              s"Fix: convert to String or interpolate a PrimitiveType."
          )
        }
      } else {
        val isStringable  = isStringableType(c)(argType)
        val isRuntimeSafe = isRuntimeSupportedType(c)(argType)
        val hasEncoder    = hasJsonEncoderImplicit(c)(argType)

        if (!isStringable && !isRuntimeSafe && !hasEncoder) {
          c.error(
            argExpr.tree.pos,
            s"Value interpolation requires either a supported runtime container or an implicit JsonEncoder[A], but found '$argType'. " +
              s"Fix: provide an implicit Schema[A] / JsonEncoder[A] in scope."
          )
        }
      }
    }

    // Native compilation can't validate JSON literals at compile time.
    // Keep argument type validation above and pass per-hole contexts to the runtime encoder.
    val scExpr = c.Expr[StringContext](c.prefix.tree.asInstanceOf[Apply].args.head)

    val wrappedArgTrees: List[Tree] = args.zipWithIndex.map { case (argExpr, idx) =>
      val tpe = argExpr.actualType
      val ctx = if (idx < contexts.length) contexts(idx) else JsonInterpolatorRuntime.CtxValue

      if (ctx == JsonInterpolatorRuntime.CtxKey || ctx == JsonInterpolatorRuntime.CtxString) {
        q"_root_.zio.blocks.schema.json.JsonInterpolatorRuntime.StringableArg(${argExpr.tree})"
      } else {
        if (isStringableType(c)(tpe) || isRuntimeSupportedType(c)(tpe)) {
          q"_root_.zio.blocks.schema.json.JsonInterpolatorRuntime.RuntimeValueArg(${argExpr.tree})"
        } else {
          q"_root_.zio.blocks.schema.json.JsonInterpolatorRuntime.EncodedValueArg[$tpe](${argExpr.tree}.asInstanceOf[$tpe], _root_.zio.blocks.schema.json.JsonEncoder[$tpe])"
        }
      }
    }.toList

    val ctxTrees: List[Tree] = contexts.toList.map(b => Literal(Constant(b.toInt.toByte)))
    val ctxArrayTree         = q"Array[Byte](..$ctxTrees)"
    val argsExpr             = c.Expr[Seq[JsonInterpolatorRuntime.Arg]](q"Seq[_root_.zio.blocks.schema.json.JsonInterpolatorRuntime.Arg](..$wrappedArgTrees)")
    c.Expr[Json](q"_root_.zio.blocks.schema.json.JsonInterpolatorRuntime.jsonWithInterpolation($scExpr, $argsExpr, $ctxArrayTree)")
  }

  private def computeHoleContexts(parts: List[String]): Array[Byte] = {
    val holeCount = math.max(0, parts.length - 1)
    val out       = new Array[Byte](holeCount)

    var inString = false
    var escaped  = false

    var i = 0
    while (i < holeCount) {
      val s   = parts(i)
      val len = s.length
      var j   = 0
      while (j < len) {
        val ch = s.charAt(j)
        if (inString) {
          if (escaped) escaped = false
          else if (ch == '\\') escaped = true
          else if (ch == '"') inString = false
        } else {
          if (ch == '"') inString = true
        }
        j += 1
      }

      if (inString) out(i) = JsonInterpolatorRuntime.CtxString
      else {
        val next = parts(i + 1)
        val k    = next.dropWhile(_.isWhitespace)
        if (k.startsWith(":")) out(i) = JsonInterpolatorRuntime.CtxKey
        else out(i) = JsonInterpolatorRuntime.CtxValue
      }

      i += 1
    }

    out
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

  private def isRuntimeSupportedType(c: blackbox.Context)(tpe: c.universe.Type): Boolean = {
    import c.universe._
    tpe =:= typeOf[Null] ||
    tpe <:< typeOf[Json] ||
    tpe <:< typeOf[Option[_]] ||
    tpe <:< typeOf[scala.collection.Map[_, _]] ||
    tpe <:< typeOf[Iterable[_]] ||
    tpe <:< typeOf[Array[_]]
  }
}
