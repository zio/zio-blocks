package zio.blocks.schema

import zio.blocks.schema.json._
import scala.quoted._

package object json {
  extension (inline sc: StringContext) {
    inline def json(inline args: Any*): Json = ${ jsonInterpolatorImpl('sc, 'args) }
  }

  private sealed trait InterpolationContext
  private object InterpolationContext {
    case object Key                                          extends InterpolationContext
    case object Value                                        extends InterpolationContext
    case class StringLiteral(prefix: String, suffix: String) extends InterpolationContext
  }

  private def jsonInterpolatorImpl(sc: Expr[StringContext], args: Expr[Seq[Any]])(using Quotes): Expr[Json] = {
    import quotes.reflect._

    val parts = sc match {
      case '{ StringContext(${ Varargs(rawParts) }: _*) } =>
        rawParts.map { case '{ $rawPart: String } => rawPart.valueOrAbort }.toList
      case _ => report.errorAndAbort("Expected a StringContext with string literal parts")
    }

    val argExprs: List[Expr[Any]] = args match {
      case Varargs(exprs) => exprs.toList
      case _              => report.errorAndAbort("Expected varargs")
    }

    if (argExprs.isEmpty) {
      // On Native, skip compile-time JSON validation (runtime not available at macro compile time)
      '{ JsonInterpolatorRuntime.jsonWithInterpolation($sc, Seq.empty) }
    } else {
      val contexts = analyzeContexts(parts)

      // Type check each argument based on its context
      argExprs.zip(contexts).foreach { case (arg, ctx) =>
        val argType = arg.asTerm.tpe.widen
        ctx match {
          case InterpolationContext.Key =>
            val stringableType = TypeRepr.of[Stringable].appliedTo(argType)
            Implicits.search(stringableType) match {
              case _: ImplicitSearchSuccess => // OK
              case _: ImplicitSearchFailure =>
                report.errorAndAbort(
                  s"Type ${argType.show} cannot be used in JSON key position: no Stringable[${argType.show}] instance found. " +
                    "Only stringable types (primitives, temporal types, UUID, Currency) can be used as JSON keys.",
                  arg.asTerm.pos
                )
            }

          case InterpolationContext.Value =>
            if (!isRuntimeHandledType(argType)) {
              val encoderType = TypeRepr.of[JsonEncoder].appliedTo(argType)
              Implicits.search(encoderType) match {
                case _: ImplicitSearchSuccess => // OK
                case _: ImplicitSearchFailure =>
                  report.errorAndAbort(
                    s"Type ${argType.show} cannot be used in JSON value position: no JsonEncoder[${argType.show}] instance found. " +
                      "Provide an implicit Schema for automatic derivation or define an explicit JsonEncoder.",
                    arg.asTerm.pos
                  )
              }
            }

          case InterpolationContext.StringLiteral(_, _) =>
            val stringableType = TypeRepr.of[Stringable].appliedTo(argType)
            Implicits.search(stringableType) match {
              case _: ImplicitSearchSuccess => // OK
              case _: ImplicitSearchFailure =>
                report.errorAndAbort(
                  s"Type ${argType.show} cannot be interpolated inside a JSON string: no Stringable[${argType.show}] instance found. " +
                    "Only stringable types (primitives, temporal types, UUID, Currency) can be embedded in JSON strings.",
                  arg.asTerm.pos
                )
            }
        }
      }

      // Transform args that have JsonEncoder but are not runtime-handled
      // These need to be encoded to Json before passing to runtime
      val transformedArgs: List[Expr[Any]] = argExprs.zip(contexts).map { case (arg, ctx) =>
        ctx match {
          case InterpolationContext.Value =>
            val argType = arg.asTerm.tpe.widen
            if (!isRuntimeHandledType(argType)) {
              val encoderType = TypeRepr.of[JsonEncoder].appliedTo(argType)
              Implicits.search(encoderType) match {
                case success: ImplicitSearchSuccess =>
                  val encoder = success.tree.asExpr
                  argType.asType match {
                    case '[t] =>
                      val typedArg     = arg.asExprOf[t]
                      val typedEncoder = encoder.asExprOf[JsonEncoder[t]]
                      '{ $typedEncoder.encode($typedArg) }
                  }
                case _ => arg
              }
            } else {
              arg
            }
          case _ => arg
        }
      }

      // Check if there are any string literal interpolations
      val hasStringLiteral = contexts.exists(_.isInstanceOf[InterpolationContext.StringLiteral])

      // On Native, skip compile-time JSON validation (runtime not available at macro compile time)
      if (hasStringLiteral) {
        val (newParts, newArgs) = transformStringInterpolations(parts, transformedArgs, contexts)
        val partsExpr           = Expr(newParts)
        val argsSeqExpr         = Expr.ofSeq(newArgs)
        '{ JsonInterpolatorRuntime.jsonWithInterpolation(StringContext($partsExpr: _*), $argsSeqExpr) }
      } else {
        val transformedArgsExpr = Expr.ofSeq(transformedArgs)
        '{ JsonInterpolatorRuntime.jsonWithInterpolation($sc, $transformedArgsExpr) }
      }
    }
  }

  private def isRuntimeHandledType(using Quotes)(tpe: quotes.reflect.TypeRepr): Boolean = {
    import quotes.reflect._

    val runtimeTypes = List(
      TypeRepr.of[String],
      TypeRepr.of[Boolean],
      TypeRepr.of[Byte],
      TypeRepr.of[Short],
      TypeRepr.of[Int],
      TypeRepr.of[Long],
      TypeRepr.of[Float],
      TypeRepr.of[Double],
      TypeRepr.of[Char],
      TypeRepr.of[BigDecimal],
      TypeRepr.of[BigInt],
      TypeRepr.of[java.time.DayOfWeek],
      TypeRepr.of[java.time.Duration],
      TypeRepr.of[java.time.Instant],
      TypeRepr.of[java.time.LocalDate],
      TypeRepr.of[java.time.LocalDateTime],
      TypeRepr.of[java.time.LocalTime],
      TypeRepr.of[java.time.Month],
      TypeRepr.of[java.time.MonthDay],
      TypeRepr.of[java.time.OffsetDateTime],
      TypeRepr.of[java.time.OffsetTime],
      TypeRepr.of[java.time.Period],
      TypeRepr.of[java.time.Year],
      TypeRepr.of[java.time.YearMonth],
      TypeRepr.of[java.time.ZoneId],
      TypeRepr.of[java.time.ZoneOffset],
      TypeRepr.of[java.time.ZonedDateTime],
      TypeRepr.of[java.util.Currency],
      TypeRepr.of[java.util.UUID],
      TypeRepr.of[Unit],
      TypeRepr.of[Json]
    )

    if (runtimeTypes.exists(t => tpe =:= t || tpe <:< t)) return true
    if (tpe =:= TypeRepr.of[Null] || tpe <:< TypeRepr.of[Null]) return true

    // For collections, recursively check element types
    if (tpe <:< TypeRepr.of[Option[?]]) {
      val elemType = tpe.typeArgs.headOption
      return elemType.forall(isRuntimeHandledType)
    }
    if (tpe <:< TypeRepr.of[scala.collection.Map[?, ?]]) {
      val typeArgs = tpe.typeArgs
      if (typeArgs.size >= 2) {
        return isRuntimeHandledType(typeArgs(0)) && isRuntimeHandledType(typeArgs(1))
      }
      return true
    }
    if (tpe <:< TypeRepr.of[Iterable[?]]) {
      val elemType = tpe.typeArgs.headOption
      return elemType.forall(isRuntimeHandledType)
    }
    if (tpe <:< TypeRepr.of[Array[?]]) {
      val elemType = tpe.typeArgs.headOption
      return elemType.forall(isRuntimeHandledType)
    }

    false
  }

  private def analyzeContexts(parts: List[String]): List[InterpolationContext] = {
    if (parts.size <= 1) return Nil

    val contexts       = new scala.collection.mutable.ListBuffer[InterpolationContext]
    var cumulativeText = ""

    for (i <- 0 until parts.size - 1) {
      cumulativeText += parts(i)
      val after = parts(i + 1)
      contexts += determineContext(cumulativeText, after)
    }

    contexts.toList
  }

  private def determineContext(allBefore: String, after: String): InterpolationContext = {
    val insideString = isInsideString(allBefore)

    if (insideString) {
      val prefixStart = findLastUnescapedQuote(allBefore) + 1
      val prefix      = allBefore.substring(prefixStart)
      val suffixEnd   = findFirstUnescapedQuote(after)
      val suffix      = if (suffixEnd >= 0) after.substring(0, suffixEnd) else after
      InterpolationContext.StringLiteral(prefix, suffix)
    } else {
      val trimmedAfter = after.dropWhile(c => c == ' ' || c == '\t' || c == '\n' || c == '\r')
      if (trimmedAfter.nonEmpty && trimmedAfter.head == ':') {
        InterpolationContext.Key
      } else {
        InterpolationContext.Value
      }
    }
  }

  private def isInsideString(s: String): Boolean =
    countUnescapedQuotes(s) % 2 == 1

  private def countUnescapedQuotes(s: String): Int = {
    var count = 0
    var i     = 0
    while (i < s.length) {
      if (s.charAt(i) == '"') {
        var backslashes = 0
        var j           = i - 1
        while (j >= 0 && s.charAt(j) == '\\') {
          backslashes += 1
          j -= 1
        }
        if (backslashes % 2 == 0) count += 1
      }
      i += 1
    }
    count
  }

  private def findLastUnescapedQuote(s: String): Int = {
    var lastPos = -1
    var i       = 0
    while (i < s.length) {
      if (s.charAt(i) == '"') {
        var backslashes = 0
        var j           = i - 1
        while (j >= 0 && s.charAt(j) == '\\') {
          backslashes += 1
          j -= 1
        }
        if (backslashes % 2 == 0) lastPos = i
      }
      i += 1
    }
    lastPos
  }

  private def findFirstUnescapedQuote(s: String): Int = {
    var i = 0
    while (i < s.length) {
      if (s.charAt(i) == '"') {
        var backslashes = 0
        var j           = i - 1
        while (j >= 0 && s.charAt(j) == '\\') {
          backslashes += 1
          j -= 1
        }
        if (backslashes % 2 == 0) return i
      }
      i += 1
    }
    -1
  }

  private def transformStringInterpolations(
    parts: List[String],
    args: List[Expr[Any]],
    contexts: List[InterpolationContext]
  )(using Quotes): (List[String], List[Expr[Any]]) = {
    import quotes.reflect._

    if (args.isEmpty) return (parts, Nil)

    val newParts = new scala.collection.mutable.ListBuffer[String]
    val newArgs  = new scala.collection.mutable.ListBuffer[Expr[Any]]

    var i = 0
    while (i < args.length) {
      contexts(i) match {
        case InterpolationContext.StringLiteral(_, _) =>
          // Calculate how much text we've already added from previous iterations
          val alreadyAddedLength = parts.take(i).map(_.length).sum

          val cumulativeBefore = parts.take(i + 1).mkString
          val openingQuotePos  = findLastUnescapedQuote(cumulativeBefore)

          // Only add the NEW content (from what we've already added to the opening quote)
          val newContent = cumulativeBefore.substring(alreadyAddedLength, openingQuotePos)
          newParts += newContent

          val stringFragments   = new scala.collection.mutable.ListBuffer[Either[String, Expr[String]]]
          var j                 = i
          var foundClosingQuote = false

          while (j < args.length && !foundClosingQuote) {
            contexts(j) match {
              case InterpolationContext.StringLiteral(prefix, _) =>
                // Only add prefix for the first interpolation in this string (j == i)
                // For subsequent interpolations, the text was already added as nextPart
                if (j == i && prefix.nonEmpty) {
                  stringFragments += Left(prefix)
                }

                val arg            = args(j)
                val argType        = arg.asTerm.tpe.widen
                val stringableType = TypeRepr.of[Stringable].appliedTo(argType)
                Implicits.search(stringableType) match {
                  case success: ImplicitSearchSuccess =>
                    val stringableExpr = success.tree.asExpr
                    val stringifyCall  = argType.asType match {
                      case '[t] =>
                        val typedArg        = arg.asExprOf[t]
                        val typedStringable = stringableExpr.asExprOf[Stringable[t]]
                        '{ $typedStringable.stringify($typedArg) }
                    }
                    stringFragments += Right(stringifyCall)
                  case _ =>
                    report.errorAndAbort(s"No Stringable instance found for ${argType.show}")
                }

                val nextPart        = parts(j + 1)
                val closingQuotePos = findFirstUnescapedQuote(nextPart)

                if (closingQuotePos >= 0) {
                  val suffix = nextPart.substring(0, closingQuotePos)
                  if (suffix.nonEmpty) {
                    stringFragments += Left(suffix)
                  }

                  val concatenated = buildStringConcatenation(stringFragments.toList)
                  newArgs += concatenated

                  val afterQuote = nextPart.substring(closingQuotePos + 1)
                  i = j + 1
                  foundClosingQuote = true

                  if (i < args.length) {
                    newParts += afterQuote
                  } else {
                    newParts += afterQuote
                  }
                } else {
                  stringFragments += Left(nextPart)
                  j += 1
                }

              case _ =>
                report.errorAndAbort("Internal error: expected StringLiteral context")
            }
          }

          if (!foundClosingQuote) {
            report.errorAndAbort("Internal error: unterminated string literal")
          }

        case _ =>
          newParts += parts(i)
          newArgs += args(i)
          i += 1
          if (i == args.length) {
            newParts += parts(i)
          }
      }
    }

    (newParts.toList, newArgs.toList)
  }

  private def buildStringConcatenation(
    fragments: List[Either[String, Expr[String]]]
  )(using Quotes): Expr[String] = {
    if (fragments.isEmpty) {
      return Expr("")
    }

    val first: Expr[String] = fragments.head match {
      case Left(s)  => Expr(s)
      case Right(e) => e
    }

    fragments.tail.foldLeft(first) { (acc, fragment) =>
      fragment match {
        case Left(s)  => '{ $acc + ${ Expr(s) } }
        case Right(e) => '{ $acc + $e }
      }
    }
  }
}
