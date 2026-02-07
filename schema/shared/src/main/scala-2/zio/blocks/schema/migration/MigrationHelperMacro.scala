package zio.blocks.schema.migration

import scala.reflect.macros.blackbox

private[migration] trait MigrationHelperMacro {
  val c: blackbox.Context
  import c.universe._

  def isPrimitiveType(tpe: c.Type): Boolean = {
    val primitiveTypes = List(
      typeOf[Boolean],
      typeOf[Byte],
      typeOf[Short],
      typeOf[Int],
      typeOf[Long],
      typeOf[Float],
      typeOf[Double],
      typeOf[Char],
      typeOf[String],
      typeOf[java.math.BigInteger],
      typeOf[java.math.BigDecimal],
      typeOf[BigInt],
      typeOf[BigDecimal],
      typeOf[java.util.UUID],
      typeOf[java.time.Instant],
      typeOf[java.time.LocalDate],
      typeOf[java.time.LocalTime],
      typeOf[java.time.LocalDateTime],
      typeOf[java.time.OffsetDateTime],
      typeOf[java.time.ZonedDateTime],
      typeOf[java.time.Duration],
      typeOf[java.time.Period],
      typeOf[java.time.Year],
      typeOf[java.time.YearMonth],
      typeOf[java.time.MonthDay],
      typeOf[java.time.ZoneId],
      typeOf[java.time.ZoneOffset],
      typeOf[Unit],
      typeOf[Nothing]
    )

    primitiveTypes.exists(pt => tpe =:= pt)
  }

  def isContainerType(tpe: c.Type): Boolean = {
    val containerTypes = List(
      typeOf[Option[_]],
      typeOf[List[_]],
      typeOf[Vector[_]],
      typeOf[Set[_]],
      typeOf[Seq[_]],
      typeOf[IndexedSeq[_]],
      typeOf[Iterable[_]],
      typeOf[Map[_, _]],
      typeOf[Array[_]]
    )

    containerTypes.exists(ct => tpe <:< ct)
  }

  def isProductType(symbol: c.Symbol): Boolean =
    symbol.isClass && symbol.asClass.isCaseClass && !symbol.isAbstract

  def getProductFields(tpe: c.Type): List[(String, c.Type)] = {
    val symbol = tpe.typeSymbol
    if (!symbol.isClass) return Nil

    val classSymbol = symbol.asClass
    val primaryCtor = classSymbol.primaryConstructor
    if (primaryCtor == NoSymbol) return Nil

    val ctorParams = primaryCtor.asMethod.paramLists.flatten

    ctorParams.map { param =>
      val paramName = param.name.decodedName.toString
      val paramType = param.typeSignatureIn(tpe).dealias
      (paramName, paramType)
    }
  }

  def isSealedTrait(tpe: c.Type): Boolean = {
    val symbol = tpe.typeSymbol
    symbol.isClass && symbol.asClass.isSealed && (symbol.isAbstract || symbol.asClass.isTrait)
  }

  def getCaseName(tpe: c.Type): String =
    tpe.typeSymbol.name.decodedName.toString

  def directSubTypes(tpe: c.Type): List[c.Type] = {
    val symbol = tpe.typeSymbol
    if (!symbol.isClass) return Nil

    val classSymbol = symbol.asClass
    if (!classSymbol.isSealed) return Nil

    classSymbol.knownDirectSubclasses.toList.map { subSymbol =>
      if (subSymbol.isModuleClass) {
        subSymbol.asClass.module.typeSignature
      } else {
        subSymbol.asType.toType
      }
    }
  }

  def extractFieldPathsFromType(
    tpe: c.Type,
    prefix: String,
    visiting: Set[String],
    errorContext: String = "Migration validation"
  ): List[String] = {
    val dealiased = tpe.dealias
    val typeKey   = dealiased.typeSymbol.fullName

    if (visiting.contains(typeKey)) {
      c.abort(
        c.enclosingPosition,
        s"Recursive type detected: ${dealiased.toString}. " +
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

  def extractCaseNamesFromType(tpe: c.Type): List[String] = {
    val dealiased = tpe.dealias

    if (isSealedTrait(dealiased)) {
      val subTypes = directSubTypes(dealiased)
      subTypes.map(getCaseName)
    } else {
      Nil
    }
  }

  def getFieldNamesFromType(tpe: c.Type): Set[String] = {
    val sym = tpe.typeSymbol
    if (sym.isClass && sym.asClass.isCaseClass) {
      val primaryConstructor = tpe.decls.collectFirst {
        case m: MethodSymbol if m.isPrimaryConstructor => m
      }
      primaryConstructor match {
        case Some(ctor) =>
          ctor.paramLists.flatten
            .filterNot(_.isImplicit)
            .map(_.name.toString.trim)
            .toSet
        case None =>
          Set.empty[String]
      }
    } else {
      tpe match {
        case RefinedType(parents, scope) =>
          val refinementNames = scope.collect {
            case m: TermSymbol if m.isAbstract => m.name.toString.trim
          }.toSet
          refinementNames ++ parents.flatMap(p => getFieldNamesFromType(p))
        case _ =>
          Set.empty[String]
      }
    }
  }
}
