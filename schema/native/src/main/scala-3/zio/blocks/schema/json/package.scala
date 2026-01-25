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
        rawParts.map { case '{ $rawPart: String } => rawPart.valueOrAbort }.toList
      case _ => report.errorAndAbort("Expected a StringContext with string literal parts")
    }

    val contexts: Array[Byte] = computeHoleContexts(parts)

    val argExprs: List[Expr[Any]] = args match {
      case Varargs(as) => as.toList
      case _           => Nil
    }

    // Validate arguments per-context (Native still does this at compile-time).
    argExprs.zipWithIndex.foreach { case (argExpr, idx) =>
      val argType0 = argExpr.asTerm.tpe.widenTermRefByName.dealias.widen
      val ctx      = if (idx < contexts.length) contexts(idx) else JsonInterpolatorRuntime.CtxValue

      if (ctx == JsonInterpolatorRuntime.CtxKey) {
        if (!isStringableType(argType0)) {
          report.error(
            s"Key interpolation requires a stringable (PrimitiveType) type, but found '${argType0.show}'. " +
              s"Fix: convert to String or interpolate a PrimitiveType (primitives, temporal types, UUID, Currency).",
            argExpr
          )
        }
      } else if (ctx == JsonInterpolatorRuntime.CtxString) {
        if (!isStringableType(argType0)) {
          report.error(
            s"String-literal interpolation requires a stringable (PrimitiveType) type, but found '${argType0.show}'. " +
              s"Fix: convert to String or interpolate a PrimitiveType.",
            argExpr
          )
        }
      } else {
        val isStringable  = isStringableType(argType0)
        val isRuntimeSafe = isRuntimeSupportedType(argType0)
        val hasEncoder    = hasJsonEncoderImplicit(argType0)

        if (!isStringable && !isRuntimeSafe && !hasEncoder) {
          report.error(
            s"Value interpolation requires either a supported runtime container or an implicit JsonEncoder[A], but found '${argType0.show}'. " +
              s"Fix: provide an implicit Schema[A] / JsonEncoder[A] in scope.",
            argExpr
          )
        }
      }
    }

    val ctxSeqExpr: Expr[Seq[Byte]]                       = Expr.ofSeq(contexts.toList.map(Expr(_)))
    val ctxExpr: Expr[Array[Byte]]                        = '{ $ctxSeqExpr.toArray }
    val argTrees: List[Expr[JsonInterpolatorRuntime.Arg]] = argExprs.zipWithIndex.map { case (argExpr, idx) =>
      val tpe = argExpr.asTerm.tpe.widenTermRefByName.dealias.widen
      val ctx = if (idx < contexts.length) contexts(idx) else JsonInterpolatorRuntime.CtxValue

      if (ctx == JsonInterpolatorRuntime.CtxKey || ctx == JsonInterpolatorRuntime.CtxString) {
        '{ JsonInterpolatorRuntime.StringableArg($argExpr) }.asExprOf[JsonInterpolatorRuntime.Arg]
      } else {
        if (isStringableType(tpe)) {
          '{ JsonInterpolatorRuntime.RuntimeValueArg($argExpr) }.asExprOf[JsonInterpolatorRuntime.Arg]
        } else if (hasJsonEncoderImplicit(tpe)) {
          tpe.asType match {
            case '[a] =>
              val enc = Expr.summon[JsonEncoder[a]].getOrElse {
                report.errorAndAbort(s"Value interpolation requires an implicit JsonEncoder[${tpe.show}] in scope")
              }
              '{ JsonInterpolatorRuntime.EncodedValueArg[a]($argExpr.asInstanceOf[a], $enc) }
                .asExprOf[JsonInterpolatorRuntime.Arg]
          }
        } else if (isRuntimeSupportedType(tpe)) {
          '{ JsonInterpolatorRuntime.RuntimeValueArg($argExpr) }.asExprOf[JsonInterpolatorRuntime.Arg]
        } else {
          tpe.asType match {
            case '[a] =>
              val enc = Expr.summon[JsonEncoder[a]].getOrElse {
                report.errorAndAbort(s"Value interpolation requires an implicit JsonEncoder[${tpe.show}] in scope")
              }
              '{ JsonInterpolatorRuntime.EncodedValueArg[a]($argExpr.asInstanceOf[a], $enc) }
                .asExprOf[JsonInterpolatorRuntime.Arg]
          }
        }
      }
    }

    // Native: skip compile-time JSON literal validation, but still apply per-hole runtime encoding.
    val argsExpr: Expr[Seq[JsonInterpolatorRuntime.Arg]] = Expr.ofSeq(argTrees)
    '{ JsonInterpolatorRuntime.jsonWithInterpolation($sc, $argsExpr, $ctxExpr) }
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

  private def isStringableType(using Quotes)(tpe: quotes.reflect.TypeRepr): Boolean = {
    val typeSymbol      = tpe.typeSymbol.fullName
    val stringableTypes = Set(
      "scala.Unit",
      "scala.Boolean",
      "scala.Byte",
      "scala.Short",
      "scala.Int",
      "scala.Long",
      "scala.Float",
      "scala.Double",
      "scala.Char",
      "java.lang.String",
      "scala.math.BigInt",
      "scala.math.BigDecimal",
      "java.time.DayOfWeek",
      "java.time.Duration",
      "java.time.Instant",
      "java.time.LocalDate",
      "java.time.LocalDateTime",
      "java.time.LocalTime",
      "java.time.Month",
      "java.time.MonthDay",
      "java.time.OffsetDateTime",
      "java.time.OffsetTime",
      "java.time.Period",
      "java.time.Year",
      "java.time.YearMonth",
      "java.time.ZoneId",
      "java.time.ZoneOffset",
      "java.time.ZonedDateTime",
      "java.util.UUID",
      "java.util.Currency"
    )
    stringableTypes.contains(typeSymbol)
  }

  private def hasJsonEncoderImplicit(using Quotes)(tpe: quotes.reflect.TypeRepr): Boolean = {
    import quotes.reflect._
    try {
      val encoderType = TypeRepr.of[JsonEncoder].appliedTo(tpe)
      Implicits.search(encoderType) match {
        case _: ImplicitSearchSuccess => true
        case _                        => false
      }
    } catch {
      case _ => false
    }
  }

  private def isRuntimeSupportedType(using Quotes)(tpe: quotes.reflect.TypeRepr): Boolean = {
    import quotes.reflect._

    val widened = tpe.widenTermRefByName.dealias.widen

    widened =:= TypeRepr.of[Null] ||
    widened <:< TypeRepr.of[Json] ||
    widened <:< TypeRepr.of[Option[?]] ||
    widened <:< TypeRepr.of[scala.collection.Map[?, ?]] ||
    widened <:< TypeRepr.of[Iterable[?]] ||
    widened <:< TypeRepr.of[Array[?]]
  }
}
