package zio.blocks.schema.migration

import scala.quoted.*

private[migration] object MigrationHelperMacro {

  def isPrimitiveType(using q: Quotes)(tpe: q.reflect.TypeRepr): Boolean = {
    import q.reflect.*

    val primitiveTypes = List(
      TypeRepr.of[Boolean],
      TypeRepr.of[Byte],
      TypeRepr.of[Short],
      TypeRepr.of[Int],
      TypeRepr.of[Long],
      TypeRepr.of[Float],
      TypeRepr.of[Double],
      TypeRepr.of[Char],
      TypeRepr.of[String],
      TypeRepr.of[java.math.BigInteger],
      TypeRepr.of[java.math.BigDecimal],
      TypeRepr.of[BigInt],
      TypeRepr.of[BigDecimal],
      TypeRepr.of[java.util.UUID],
      TypeRepr.of[java.time.Instant],
      TypeRepr.of[java.time.LocalDate],
      TypeRepr.of[java.time.LocalTime],
      TypeRepr.of[java.time.LocalDateTime],
      TypeRepr.of[java.time.OffsetDateTime],
      TypeRepr.of[java.time.ZonedDateTime],
      TypeRepr.of[java.time.Duration],
      TypeRepr.of[java.time.Period],
      TypeRepr.of[java.time.Year],
      TypeRepr.of[java.time.YearMonth],
      TypeRepr.of[java.time.MonthDay],
      TypeRepr.of[java.time.ZoneId],
      TypeRepr.of[java.time.ZoneOffset],
      TypeRepr.of[Unit],
      TypeRepr.of[Nothing]
    )

    primitiveTypes.exists(pt => tpe =:= pt)
  }

  def isContainerType(using q: Quotes)(tpe: q.reflect.TypeRepr): Boolean = {
    import q.reflect.*

    val containerTypes = List(
      TypeRepr.of[Option[?]],
      TypeRepr.of[List[?]],
      TypeRepr.of[Vector[?]],
      TypeRepr.of[Set[?]],
      TypeRepr.of[Seq[?]],
      TypeRepr.of[IndexedSeq[?]],
      TypeRepr.of[Iterable[?]],
      TypeRepr.of[Map[?, ?]],
      TypeRepr.of[Array[?]]
    )

    containerTypes.exists(ct => tpe <:< ct)
  }

  def isProductType(using q: Quotes)(symbol: q.reflect.Symbol): Boolean = {
    import q.reflect.*
    symbol.flags.is(Flags.Case) && !symbol.flags.is(Flags.Abstract)
  }

  def getProductFields(using q: Quotes)(tpe: q.reflect.TypeRepr): List[(String, q.reflect.TypeRepr)] = {
    val symbol      = tpe.typeSymbol
    val constructor = symbol.primaryConstructor
    if (constructor.isNoSymbol) return Nil

    val paramLists = constructor.paramSymss
    val termParams = paramLists.flatten.filter(_.isTerm)

    termParams.map { param =>
      val paramName = param.name
      val paramType = tpe.memberType(param)
      (paramName, paramType.dealias)
    }
  }

  def isSealedTraitOrEnum(using q: Quotes)(tpe: q.reflect.TypeRepr): Boolean = {
    import q.reflect.*

    tpe.classSymbol.fold(false) { symbol =>
      val flags = symbol.flags
      (flags.is(Flags.Sealed) && (flags.is(Flags.Abstract) || flags.is(Flags.Trait))) ||
      flags.is(Flags.Enum)
    }
  }

  def isEnumValue(using q: Quotes)(tpe: q.reflect.TypeRepr): Boolean = {
    import q.reflect.*
    tpe.termSymbol.flags.is(Flags.Enum)
  }

  def getCaseName(using q: Quotes)(tpe: q.reflect.TypeRepr): String =
    if (isEnumValue(tpe)) tpe.termSymbol.name
    else tpe.typeSymbol.name

  def directSubTypes(using q: Quotes)(tpe: q.reflect.TypeRepr): List[q.reflect.TypeRepr] = {
    import q.reflect.*

    val symbol   = tpe.typeSymbol
    val children = symbol.children

    children.map { child =>
      if (child.isType) child.typeRef
      else Ref(child).tpe
    }
  }

  def extractFieldPathsFromType(using
    q: Quotes
  )(
    tpe: q.reflect.TypeRepr,
    prefix: String,
    visiting: Set[String],
    errorContext: String = "Migration validation"
  ): List[String] = {
    import q.reflect.*

    val dealiased = tpe.dealias
    val typeKey   = dealiased.typeSymbol.fullName

    if (visiting.contains(typeKey)) {
      report.errorAndAbort(
        s"Recursive type detected: ${dealiased.show}. " +
          s"$errorContext does not support recursive types. " +
          s"Recursion path: ${visiting.mkString(" -> ")} -> $typeKey"
      )
    }

    if (isContainerType(dealiased) || isPrimitiveType(dealiased)) {
      return Nil
    }

    if (!isProductType(dealiased.typeSymbol)) {
      return Nil
    }

    val newVisiting = visiting + typeKey
    val fields      = getProductFields(dealiased)

    fields.flatMap { case (fieldName, fieldType) =>
      val fullPath    = if (prefix.isEmpty) fieldName else s"$prefix$fieldName"
      val nestedPaths = extractFieldPathsFromType(fieldType, s"$fullPath.", newVisiting, errorContext)
      fullPath :: nestedPaths
    }
  }

  def getFieldNamesFromType(using q: Quotes)(tpe: q.reflect.TypeRepr): Set[String] = {
    import q.reflect.*

    def extract(t: TypeRepr): Set[String] = t.dealias match {
      case Refinement(parent, name, _) =>
        Set(name) ++ extract(parent)
      case t if isProductType(t.typeSymbol) =>
        getProductFields(t).map(_._1).toSet
      case _ =>
        Set.empty
    }

    extract(tpe.dealias)
  }
}
