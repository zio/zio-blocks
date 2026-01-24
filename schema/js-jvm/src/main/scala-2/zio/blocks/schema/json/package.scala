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

  sealed trait InterpolationContext
  object InterpolationContext {
    case object Key                                          extends InterpolationContext
    case object Value                                        extends InterpolationContext
    case class StringLiteral(prefix: String, suffix: String) extends InterpolationContext
  }

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

    if (args.isEmpty) {
      try {
        JsonInterpolatorRuntime.jsonWithInterpolation(new StringContext(parts: _*), Seq.empty)
        val scExpr = c.Expr[StringContext](c.prefix.tree.asInstanceOf[Apply].args.head)
        reify(JsonInterpolatorRuntime.jsonWithInterpolation(scExpr.splice, Seq.empty))
      } catch {
        case error if NonFatal(error) => c.abort(c.enclosingPosition, s"Invalid JSON literal: ${error.getMessage}")
      }
    } else {
      val contexts = analyzeContexts(parts)

      // Type check each argument based on its context
      args.zip(contexts).foreach { case (arg, ctx) =>
        val argType = arg.tree.tpe.widen
        ctx match {
          case InterpolationContext.Key =>
            val stringableType     = c.typeOf[Stringable[_]]
            val requiredType       = appliedType(stringableType.typeConstructor, argType)
            val stringableInstance = c.inferImplicitValue(requiredType, silent = true)
            if (stringableInstance == EmptyTree) {
              c.abort(
                arg.tree.pos,
                s"Type $argType cannot be used in JSON key position: no Stringable[$argType] instance found. " +
                  "Only stringable types (primitives, temporal types, UUID, Currency) can be used as JSON keys."
              )
            }

          case InterpolationContext.Value =>
            if (!isRuntimeHandledType(c)(argType)) {
              val jsonEncoderType = c.typeOf[JsonEncoder[_]]
              val requiredType    = appliedType(jsonEncoderType.typeConstructor, argType)
              val encoderInstance = c.inferImplicitValue(requiredType, silent = true)
              if (encoderInstance == EmptyTree) {
                c.abort(
                  arg.tree.pos,
                  s"Type $argType cannot be used in JSON value position: no JsonEncoder[$argType] instance found. " +
                    "Provide an implicit Schema[$argType] for automatic derivation or define an explicit JsonEncoder."
                )
              }
            }

          case InterpolationContext.StringLiteral(_, _) =>
            val stringableType     = c.typeOf[Stringable[_]]
            val requiredType       = appliedType(stringableType.typeConstructor, argType)
            val stringableInstance = c.inferImplicitValue(requiredType, silent = true)
            if (stringableInstance == EmptyTree) {
              c.abort(
                arg.tree.pos,
                s"Type $argType cannot be interpolated inside a JSON string: no Stringable[$argType] instance found. " +
                  "Only stringable types (primitives, temporal types, UUID, Currency) can be embedded in JSON strings."
              )
            }
        }
      }

      // Transform args that have JsonEncoder but are not runtime-handled
      // These need to be encoded to Json before passing to runtime
      val transformedArgs: List[c.Expr[Any]] = args
        .zip(contexts)
        .map { case (arg, ctx) =>
          ctx match {
            case InterpolationContext.Value =>
              val argType = arg.tree.tpe.widen
              if (!isRuntimeHandledType(c)(argType)) {
                val jsonEncoderType = c.typeOf[JsonEncoder[_]]
                val requiredType    = appliedType(jsonEncoderType.typeConstructor, argType)
                val encoderInstance = c.inferImplicitValue(requiredType, silent = true)
                if (encoderInstance != EmptyTree) {
                  c.Expr[Any](q"$encoderInstance.encode(${arg.tree})")
                } else {
                  arg
                }
              } else {
                arg
              }
            case _ => arg
          }
        }
        .toList

      // Check if there are any string literal interpolations that need transformation
      val hasStringLiteral = contexts.exists(_.isInstanceOf[InterpolationContext.StringLiteral])

      if (hasStringLiteral) {
        val (newParts, newArgs) = transformStringInterpolations(c)(parts, transformedArgs, contexts)

        try {
          JsonInterpolatorRuntime.jsonWithInterpolation(
            new StringContext(newParts: _*),
            (2 to newParts.size).map(_ => "")
          )
        } catch {
          case error if NonFatal(error) =>
            c.abort(c.enclosingPosition, s"Invalid JSON literal: ${error.getMessage}")
        }

        val newPartsLiterals = newParts.map(p => Literal(Constant(p)))
        val scTree           = q"new _root_.scala.StringContext(..$newPartsLiterals)"
        val argsSeqTree      = q"_root_.scala.Seq(..$newArgs)"
        c.Expr[Json](
          q"_root_.zio.blocks.schema.json.JsonInterpolatorRuntime.jsonWithInterpolation($scTree, $argsSeqTree)"
        )
      } else {
        try {
          JsonInterpolatorRuntime.jsonWithInterpolation(new StringContext(parts: _*), (2 to parts.size).map(_ => ""))
        } catch {
          case error if NonFatal(error) => c.abort(c.enclosingPosition, s"Invalid JSON literal: ${error.getMessage}")
        }

        val scExpr               = c.Expr[StringContext](c.prefix.tree.asInstanceOf[Apply].args.head)
        val transformedArgsTrees = transformedArgs.map(_.tree)
        val argsExpr             = c.Expr[Seq[Any]](q"Seq(..$transformedArgsTrees)")
        reify(JsonInterpolatorRuntime.jsonWithInterpolation(scExpr.splice, argsExpr.splice))
      }
    }
  }

  private def isRuntimeHandledType(c: blackbox.Context)(tpe: c.universe.Type): Boolean = {
    import c.universe._

    val runtimeTypes = List(
      typeOf[String],
      typeOf[Boolean],
      typeOf[Byte],
      typeOf[Short],
      typeOf[Int],
      typeOf[Long],
      typeOf[Float],
      typeOf[Double],
      typeOf[Char],
      typeOf[BigDecimal],
      typeOf[BigInt],
      typeOf[java.time.DayOfWeek],
      typeOf[java.time.Duration],
      typeOf[java.time.Instant],
      typeOf[java.time.LocalDate],
      typeOf[java.time.LocalDateTime],
      typeOf[java.time.LocalTime],
      typeOf[java.time.Month],
      typeOf[java.time.MonthDay],
      typeOf[java.time.OffsetDateTime],
      typeOf[java.time.OffsetTime],
      typeOf[java.time.Period],
      typeOf[java.time.Year],
      typeOf[java.time.YearMonth],
      typeOf[java.time.ZoneId],
      typeOf[java.time.ZoneOffset],
      typeOf[java.time.ZonedDateTime],
      typeOf[java.util.Currency],
      typeOf[java.util.UUID],
      typeOf[Unit],
      typeOf[Json]
    )

    if (runtimeTypes.exists(t => tpe =:= t || tpe <:< t)) return true
    if (tpe =:= typeOf[Null] || tpe <:< typeOf[Null]) return true

    // For collections, recursively check element types
    if (tpe <:< typeOf[Option[_]]) {
      val elemType = tpe.typeArgs.headOption
      return elemType.forall(t => isRuntimeHandledType(c)(t))
    }
    if (tpe <:< typeOf[scala.collection.Map[_, _]]) {
      val typeArgs = tpe.typeArgs
      if (typeArgs.size >= 2) {
        return isRuntimeHandledType(c)(typeArgs(0)) && isRuntimeHandledType(c)(typeArgs(1))
      }
      return true
    }
    if (tpe <:< typeOf[Iterable[_]]) {
      val elemType = tpe.typeArgs.headOption
      return elemType.forall(t => isRuntimeHandledType(c)(t))
    }
    if (tpe <:< typeOf[Array[_]]) {
      val elemType = tpe.typeArgs.headOption
      return elemType.forall(t => isRuntimeHandledType(c)(t))
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

  private def transformStringInterpolations[C <: blackbox.Context](c: C)(
    parts: List[String],
    args: List[c.Expr[Any]],
    contexts: List[InterpolationContext]
  ): (List[String], List[c.Tree]) = {
    import c.universe._

    if (args.isEmpty) return (parts, Nil)

    val newParts = new scala.collection.mutable.ListBuffer[String]
    val newArgs  = new scala.collection.mutable.ListBuffer[c.Tree]

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

          // Collect all string literal interpolations that are part of this string
          val stringFragments   = new scala.collection.mutable.ListBuffer[Either[String, c.Tree]]
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

                // Add the stringified argument
                val arg                = args(j)
                val argType            = arg.tree.tpe.widen
                val stringableType     = c.typeOf[Stringable[_]]
                val requiredType       = appliedType(stringableType.typeConstructor, argType)
                val stringableInstance = c.inferImplicitValue(requiredType)
                stringFragments += Right(q"$stringableInstance.stringify(${arg.tree})")

                // Check if the string ends after this interpolation
                val nextPart        = parts(j + 1)
                val closingQuotePos = findFirstUnescapedQuote(nextPart)

                if (closingQuotePos >= 0) {
                  // String ends - add suffix (text from arg to closing quote)
                  val suffix = nextPart.substring(0, closingQuotePos)
                  if (suffix.nonEmpty) {
                    stringFragments += Left(suffix)
                  }

                  // Build the concatenated string expression
                  val concatenated = buildStringConcatenation(c)(stringFragments.toList)
                  newArgs += concatenated

                  // Continue after the closing quote
                  val afterQuote = nextPart.substring(closingQuotePos + 1)
                  i = j + 1
                  foundClosingQuote = true

                  // Handle what comes after: more args or end of input
                  if (i < args.length) {
                    // More args to process - need to prepend afterQuote to conceptual next part
                    // We handle this by adding afterQuote as a new part
                    newParts += afterQuote
                  } else {
                    // Last arg - afterQuote is trailing content
                    newParts += afterQuote
                  }
                } else {
                  // String continues - add the text between this arg and the next
                  stringFragments += Left(nextPart)
                  j += 1
                }

              case _ =>
                c.abort(c.enclosingPosition, "Internal error: expected StringLiteral context")
            }
          }

          if (!foundClosingQuote) {
            c.abort(c.enclosingPosition, "Internal error: unterminated string literal")
          }

        case _ =>
          // Key or Value position - pass through unchanged
          newParts += parts(i)
          newArgs += args(i).tree
          i += 1
          if (i == args.length) {
            newParts += parts(i)
          }
      }
    }

    (newParts.toList, newArgs.toList)
  }

  private def buildStringConcatenation[C <: blackbox.Context](c: C)(
    fragments: List[Either[String, c.Tree]]
  ): c.Tree = {
    import c.universe._

    if (fragments.isEmpty) {
      return Literal(Constant(""))
    }

    val first = fragments.head match {
      case Left(s)  => Literal(Constant(s))
      case Right(t) => t
    }

    fragments.tail.foldLeft(first: c.Tree) { (acc, fragment) =>
      fragment match {
        case Left(s)  => q"$acc + ${Literal(Constant(s))}"
        case Right(t) => q"$acc + $t"
      }
    }
  }
}
