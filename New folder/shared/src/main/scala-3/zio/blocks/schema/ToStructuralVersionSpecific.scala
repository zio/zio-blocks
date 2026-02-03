package zio.blocks.schema

import zio.blocks.schema.binding.*
import zio.blocks.schema.binding.RegisterOffset.*
import zio.blocks.typeid.{Owner, TypeId}

import scala.quoted.*

private[schema] class ReflectiveDeconstructor[S](
  val usedRegisters: RegisterOffset,
  fieldMetadata: IndexedSeq[(String, Register[Any])]
) extends Deconstructor[S] {

  def deconstruct(out: Registers, baseOffset: RegisterOffset, in: S): Unit = {
    val clazz = in.getClass
    var idx   = 0
    while (idx < fieldMetadata.length) {
      val (fieldName, register) = fieldMetadata(idx)
      val method                = clazz.getMethod(fieldName)
      val value                 = method.invoke(in)
      register.set(out, baseOffset, value)
      idx += 1
    }
  }
}

private[schema] class ReflectiveVariantCaseDeconstructor[S](
  val usedRegisters: RegisterOffset,
  caseName: String,
  fieldMetadata: IndexedSeq[(String, Register[Any])]
) extends Deconstructor[S] {

  def deconstruct(out: Registers, baseOffset: RegisterOffset, in: S): Unit = {
    val outerClazz = in.getClass
    // First, try to get the nested value via the case name method
    val nested = try {
      val caseMethod = outerClazz.getMethod(caseName)
      caseMethod.invoke(in)
    } catch {
      case _: NoSuchMethodException =>
        // Fallback: input is already the case value (nominal type)
        in
    }

    val nestedClazz = nested.getClass
    var idx         = 0
    while (idx < fieldMetadata.length) {
      val (fieldName, register) = fieldMetadata(idx)
      val method                = nestedClazz.getMethod(fieldName)
      val value                 = method.invoke(nested)
      register.set(out, baseOffset, value)
      idx += 1
    }
  }
}

private[schema] class ReflectiveVariantDiscriminator[S](
  caseNames: IndexedSeq[String],
  originalDiscriminator: Discriminator[?],
  originalToSortedIndex: IndexedSeq[Int]
) extends Discriminator[S] {

  def discriminate(value: S): Int = {
    val clazz = value.getClass
    // Try to find which case name method exists on the structural instance
    val idx = caseNames.indexWhere { caseName =>
      try {
        clazz.getMethod(caseName)
        true
      } catch {
        case _: NoSuchMethodException => false
      }
    }
    if (idx >= 0) idx
    else {
      // Fallback to original discriminator for nominal types
      val originalIdx = originalDiscriminator.asInstanceOf[Discriminator[S]].discriminate(value)
      originalToSortedIndex(originalIdx)
    }
  }
}

private[schema] class ReflectiveMatcher[S](
  expectedCaseName: String,
  originalMatcher: Matcher[?]
) extends Matcher[S] {

  def downcastOrNull(any: Any): S =
    if (any == null) null.asInstanceOf[S]
    else {
      val clazz = any.getClass
      try {
        val caseMethod = clazz.getMethod(expectedCaseName)
        // The case method returns the nested structural value
        caseMethod.invoke(any).asInstanceOf[S]
      } catch {
        case _: NoSuchMethodException =>
          originalMatcher.downcastOrNull(any).asInstanceOf[S]
      }
    }
}

trait ToStructuralVersionSpecific {
  transparent inline given [A]: ToStructural[A] = ${ ToStructuralMacro.derived[A] }
}

private[schema] object ToStructuralMacro {
  def derived[A: Type](using Quotes): Expr[ToStructural[A]] = {
    import quotes.reflect.*

    val aTpe = TypeRepr.of[A].dealias

    if (!Platform.supportsReflection) {
      report.errorAndAbort(
        s"""Cannot generate ToStructural[${aTpe.show}] on ${Platform.name}.
           |
           |Structural types require reflection which is only available on JVM.
           |Consider using a case class instead.""".stripMargin
      )
    }

    if (isRecursiveType(aTpe)) {
      report.errorAndAbort(
        s"""Cannot generate structural type for recursive type ${aTpe.show}.
           |
           |Structural types cannot represent recursive structures.
           |Scala's type system does not support infinite types.""".stripMargin
      )
    }

    if (isProductType(aTpe)) {
      deriveForProduct[A](aTpe)
    } else if (isTupleType(aTpe)) {
      deriveForTuple[A](aTpe)
    } else if (isSumType(aTpe)) {
      deriveForSumType[A](aTpe)
    } else {
      report.errorAndAbort(
        s"""Cannot generate ToStructural for ${aTpe.show}.
           |
           |Only product types (case classes), tuples, and sum types (sealed traits/enums) are supported.""".stripMargin
      )
    }
  }

  private def isProductType(using Quotes)(tpe: quotes.reflect.TypeRepr): Boolean =
    tpe.classSymbol.exists(CommonMacroOps.isProductType)

  private def isTupleType(using Quotes)(tpe: quotes.reflect.TypeRepr): Boolean =
    tpe.typeSymbol.fullName.startsWith("scala.Tuple") || CommonMacroOps.isGenericTuple(tpe)

  private def isSumType(using Quotes)(tpe: quotes.reflect.TypeRepr): Boolean = {
    import quotes.reflect.*
    CommonMacroOps.isSealedTraitOrAbstractClass(tpe) || tpe.classSymbol.exists { sym =>
      sym.flags.is(Flags.Enum) && !sym.flags.is(Flags.Case)
    }
  }

  private def isRecursiveType(using Quotes)(tpe: quotes.reflect.TypeRepr): Boolean = {
    import quotes.reflect.*

    def containsType(searchIn: TypeRepr, searchFor: TypeRepr, visited: Set[TypeRepr]): Boolean = {
      val dealiased = searchIn.dealias
      if (visited.contains(dealiased)) return false
      val newVisited = visited + dealiased

      if (dealiased =:= searchFor.dealias) return true

      val typeArgsContain = CommonMacroOps.typeArgs(dealiased) match {
        case args if args.nonEmpty => args.exists(arg => containsType(arg, searchFor, newVisited))
        case _                     => false
      }

      if (typeArgsContain) return true

      dealiased.classSymbol.toList.flatMap { sym =>
        sym.primaryConstructor.paramSymss.flatten.filter(!_.isTypeParam).map { param =>
          dealiased.memberType(param).dealias
        }
      }.exists(fieldTpe => containsType(fieldTpe, searchFor, newVisited))
    }

    tpe.classSymbol.toList.flatMap { sym =>
      sym.primaryConstructor.paramSymss.flatten.filter(!_.isTypeParam).map { param =>
        tpe.memberType(param).dealias
      }
    }.exists(fieldTpe => containsType(fieldTpe, tpe, Set.empty))
  }

  private def fullyUnpackType(using Quotes)(tpe: quotes.reflect.TypeRepr): quotes.reflect.TypeRepr = {
    import quotes.reflect.*

    val dealiased = tpe.dealias

    dealiased match {
      case trTpe: TypeRef if trTpe.isOpaqueAlias =>
        fullyUnpackType(trTpe.translucentSuperType.dealias)
      case _ =>
        val unpacked = CommonMacroOps.dealiasOnDemand(dealiased)
        if (unpacked != dealiased && !(unpacked =:= dealiased)) {
          fullyUnpackType(unpacked)
        } else {
          dealiased match {
            case AppliedType(tycon, args) if args.nonEmpty =>
              val unpackedArgs = args.map(fullyUnpackType)
              if (unpackedArgs != args) tycon.appliedTo(unpackedArgs)
              else dealiased
            case _ => dealiased
          }
        }
    }
  }

  private def deriveForProduct[A: Type](using Quotes)(aTpe: quotes.reflect.TypeRepr): Expr[ToStructural[A]] = {
    import quotes.reflect.*

    val classSymbol = aTpe.classSymbol.getOrElse(
      report.errorAndAbort(s"${aTpe.show} is not a class type")
    )

    val fields: List[(String, TypeRepr)] = classSymbol.primaryConstructor.paramSymss.flatten
      .filter(!_.isTypeParam)
      .map { param =>
        val fieldName = param.name
        val fieldType = aTpe.memberType(param).dealias
        (fieldName, fieldType)
      }

    val structuralTpe = buildStructuralType(fields)

    structuralTpe.asType match {
      case '[s] =>
        '{
          new ToStructural[A] {
            type StructuralType = s
            def apply(schema: Schema[A]): Schema[s] =
              transformProductSchema[A, s](schema)
          }
        }
    }
  }

  private def deriveForTuple[A: Type](using Quotes)(aTpe: quotes.reflect.TypeRepr): Expr[ToStructural[A]] = {
    import quotes.reflect.*

    val typeArgs = aTpe match {
      case AppliedType(_, args) => args
      case _                    => Nil
    }

    val fields: List[(String, TypeRepr)] = typeArgs.zipWithIndex.map { case (tpe, idx) =>
      (s"_${idx + 1}", tpe)
    }

    if (fields.isEmpty) {
      report.errorAndAbort("Cannot generate structural type for empty tuple")
    }

    val structuralTpe = buildStructuralType(fields)

    structuralTpe.asType match {
      case '[s] =>
        '{
          new ToStructural[A] {
            type StructuralType = s
            def apply(schema: Schema[A]): Schema[s] =
              transformTupleSchema[A, s](schema)
          }
        }
    }
  }

  private def buildStructuralType(using
    Quotes
  )(fields: List[(String, quotes.reflect.TypeRepr)]): quotes.reflect.TypeRepr = {
    import quotes.reflect.*

    val baseTpe = TypeRepr.of[AnyRef]

    fields.foldLeft(baseTpe) { case (parent, (fieldName, fieldTpe)) =>
      val unpackedFieldTpe = fullyUnpackType(fieldTpe)
      Refinement(parent, fieldName, ByNameType(unpackedFieldTpe))
    }
  }

  private def buildNestedCaseType(using
    Quotes
  )(caseName: String, fields: List[(String, quotes.reflect.TypeRepr)]): quotes.reflect.TypeRepr = {
    import quotes.reflect.*

    val innerType = buildStructuralType(fields)
    val baseTpe   = TypeRepr.of[AnyRef]
    Refinement(baseTpe, caseName, ByNameType(innerType))
  }

  private def deriveForSumType[A: Type](using Quotes)(aTpe: quotes.reflect.TypeRepr): Expr[ToStructural[A]] = {
    import quotes.reflect.*

    val classSymbol = aTpe.classSymbol.getOrElse(
      report.errorAndAbort(s"${aTpe.show} is not a class type")
    )

    val children = classSymbol.children.toList.sortBy(_.name)

    if (children.isEmpty) {
      report.errorAndAbort(s"Sum type ${aTpe.show} has no cases")
    }

    val caseTypes: List[TypeRepr] = children.map { childSym =>
      val childTpe = if (childSym.flags.is(Flags.Module)) {
        childSym.termRef
      } else {
        aTpe.memberType(childSym).dealias
      }

      val caseName = childSym.name

      val fields: List[(String, TypeRepr)] = if (childSym.flags.is(Flags.Module)) {
        Nil
      } else {
        childSym.primaryConstructor.paramSymss.flatten
          .filter(!_.isTypeParam)
          .map { param =>
            val fieldName = param.name
            val fieldType = childTpe.memberType(param).dealias
            (fieldName, fieldType)
          }
      }

      buildNestedCaseType(caseName, fields)
    }

    val unionType = caseTypes.reduceLeft { (acc, tpe) =>
      OrType(acc, tpe)
    }

    unionType.asType match {
      case '[s] =>
        '{
          new ToStructural[A] {
            type StructuralType = s
            def apply(schema: Schema[A]): Schema[s] =
              transformSumTypeSchema[A, s](schema)
          }
        }
    }
  }

  /**
   * Transform a product schema (case class) to its structural equivalent. Uses
   * reflection-based deconstruction to support anonymous structural instances.
   */
  def transformProductSchema[A, S](schema: Schema[A]): Schema[S] =
    schema.reflect match {
      case record: Reflect.Record[Binding, A] @unchecked =>
        val binding = record.recordBinding.asInstanceOf[Binding.Record[A]]

        val fieldInfos = record.fields.map { field =>
          (field.name, field.value.asInstanceOf[Reflect.Bound[Any]])
        }

        val totalRegisters = binding.constructor.usedRegisters

        val typeName = normalizeTypeName(fieldInfos.toList.map { case (name, reflect) =>
          (name, reflect.typeId.name)
        })

        // Build field metadata for reflective deconstruction
        val fieldMetadata: IndexedSeq[(String, Register[Any])] = record.fields.zipWithIndex.map { case (field, idx) =>
          (field.name, record.registers(idx).asInstanceOf[Register[Any]])
        }.toIndexedSeq

        new Schema[S](
          new Reflect.Record[Binding, S](
            fields = record.fields.map { field =>
              field.value.asInstanceOf[Reflect.Bound[Any]].asTerm[S](field.name)
            },
            typeId = TypeId.nominal[S](typeName, Owner.Root),
            recordBinding = new Binding.Record[S](
              constructor = new Constructor[S] {
                def usedRegisters: RegisterOffset = totalRegisters

                def construct(in: Registers, baseOffset: RegisterOffset): S = {
                  val nominal = binding.constructor.construct(in, baseOffset)
                  nominal.asInstanceOf[S]
                }
              },
              deconstructor = new ReflectiveDeconstructor[S](totalRegisters, fieldMetadata)
            ),
            doc = record.doc,
            modifiers = record.modifiers
          )
        )

      case _ =>
        throw new IllegalArgumentException(
          s"Cannot transform non-record schema to structural type"
        )
    }

  /**
   * Transform a tuple schema to its structural equivalent.
   */
  def transformTupleSchema[A, S](schema: Schema[A]): Schema[S] =
    schema.reflect match {
      case _: Reflect.Record[Binding, A] @unchecked =>
        transformProductSchema[A, S](schema)

      case _ =>
        throw new IllegalArgumentException(
          s"Cannot transform non-record schema to structural type"
        )
    }

  def transformSumTypeSchema[A, S](schema: Schema[A]): Schema[S] =
    schema.reflect match {
      case variant: Reflect.Variant[Binding, A] @unchecked =>
        val binding = variant.variantBinding.asInstanceOf[Binding.Variant[A]]

        val sortedCases                   = variant.cases.sortBy(_.name)
        val caseNames: IndexedSeq[String] = sortedCases.map(_.name).toIndexedSeq

        val unionTypeName = sortedCases.map { case_ =>
          val caseName   = case_.name
          val caseFields = case_.value match {
            case record: Reflect.Record[Binding, _] @unchecked =>
              record.fields.map { field =>
                (field.name, field.value.asInstanceOf[Reflect.Bound[Any]].typeId.name)
              }.toList
            case _ => Nil
          }
          normalizeUnionCaseTypeName(caseName, caseFields)
        }.mkString("|")

        val sortedToOriginalIndex: IndexedSeq[Int] = sortedCases.map { case_ =>
          variant.cases.indexWhere(_.name == case_.name)
        }.toIndexedSeq

        val originalToSortedIndex: IndexedSeq[Int] = {
          val arr = new Array[Int](variant.cases.size)
          sortedToOriginalIndex.zipWithIndex.foreach { case (origIdx, sortedIdx) =>
            arr(origIdx) = sortedIdx
          }
          arr.toIndexedSeq
        }

        val reflectiveDiscriminator =
          new ReflectiveVariantDiscriminator[S](caseNames, binding.discriminator, originalToSortedIndex)

        val newMatchers = Matchers[S](
          sortedCases.zipWithIndex.map { case (case_, sortedIdx) =>
            val originalIdx     = sortedToOriginalIndex(sortedIdx)
            val caseName        = case_.name
            val originalMatcher = binding.matchers(originalIdx)
            new ReflectiveMatcher[S](caseName, originalMatcher)
          }: _*
        )

        new Schema[S](
          new Reflect.Variant[Binding, S](
            cases = sortedCases.map { case_ =>
              transformVariantCase[S](case_)
            },
            typeId = TypeId.nominal[S](unionTypeName, Owner.Root),
            variantBinding = new Binding.Variant[S](
              discriminator = reflectiveDiscriminator,
              matchers = newMatchers
            ),
            doc = variant.doc,
            modifiers = variant.modifiers
          )
        )

      case _ =>
        throw new IllegalArgumentException(
          s"Cannot transform non-variant schema to structural union type"
        )
    }

  private def transformVariantCase[S](case_ : Term[Binding, ?, ?]): Term[Binding, S, ? <: S] = {
    val caseName  = case_.name
    val caseValue = case_.value match {
      case record: Reflect.Record[Binding, ?] @unchecked =>
        val binding                                            = record.recordBinding.asInstanceOf[Binding.Record[Any]]
        val fieldMetadata: IndexedSeq[(String, Register[Any])] = record.fields.zipWithIndex.map { case (field, idx) =>
          (field.name, record.registers(idx).asInstanceOf[Register[Any]])
        }.toIndexedSeq
        val totalRegisters = binding.constructor.usedRegisters

        new Reflect.Record[Binding, Any](
          fields = record.fields.map { field =>
            field.value.asInstanceOf[Reflect.Bound[Any]].asTerm[Any](field.name)
          },
          typeId = record.typeId.asInstanceOf[TypeId[Any]],
          recordBinding = new Binding.Record[Any](
            constructor = binding.constructor.asInstanceOf[Constructor[Any]],
            deconstructor = new ReflectiveVariantCaseDeconstructor[Any](totalRegisters, caseName, fieldMetadata)
          ),
          doc = record.doc,
          modifiers = record.modifiers
        )
      case other => other
    }
    new Term[Binding, S, Any](
      caseName,
      caseValue.asInstanceOf[Reflect.Bound[Any]],
      case_.doc,
      case_.modifiers
    ).asInstanceOf[Term[Binding, S, ? <: S]]
  }

  /**
   * Generate a normalized type name for a structural type. Fields are sorted
   * alphabetically for deterministic naming.
   */
  private def normalizeTypeName(fields: List[(String, String)]): String = {
    val sorted = fields.sortBy(_._1)
    sorted.map { case (name, typeName) =>
      s"$name:${simplifyTypeName(typeName)}"
    }.mkString("{", ",", "}")
  }

  /**
   * Generate a normalized type name for a union case (includes Tag type
   * member).
   */
  private def normalizeUnionCaseTypeName(caseName: String, fields: List[(String, String)]): String = {
    val innerTypeName =
      if (fields.isEmpty) "{}"
      else {
        val sorted = fields.sortBy(_._1)
        sorted.map { case (name, typeName) =>
          s"$name:${simplifyTypeName(typeName)}"
        }.mkString("{", ",", "}")
      }
    s"{$caseName:$innerTypeName}"
  }

  /**
   * Simplify type names for display (e.g., "scala.Int" -> "Int")
   */
  private def simplifyTypeName(typeName: String): String =
    typeName
      .replace("scala.", "")
      .replace("java.lang.", "")
      .replace("Predef.", "")
}
