package zio.blocks.schema

import zio.blocks.schema.binding.*
import zio.blocks.schema.binding.RegisterOffset.*

import scala.quoted.*

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

  private def isProductType(using Quotes)(tpe: quotes.reflect.TypeRepr): Boolean = {
    import quotes.reflect.*
    tpe.classSymbol.exists { sym =>
      val flags        = sym.flags
      val isCaseObject = flags.is(Flags.Module) && flags.is(Flags.Case)
      val isCaseClass  = !flags.is(Flags.Abstract) && !flags.is(Flags.Trait) && sym.primaryConstructor.exists
      isCaseObject || isCaseClass
    }
  }

  private def isTupleType(using Quotes)(tpe: quotes.reflect.TypeRepr): Boolean =
    tpe.typeSymbol.fullName.startsWith("scala.Tuple")

  private def isSumType(using Quotes)(tpe: quotes.reflect.TypeRepr): Boolean = {
    import quotes.reflect.*
    tpe.classSymbol.exists { sym =>
      val flags            = sym.flags
      val isSealedTrait    = flags.is(Flags.Sealed) && flags.is(Flags.Trait)
      val isSealedAbstract = flags.is(Flags.Sealed) && flags.is(Flags.Abstract)
      val isEnum           = flags.is(Flags.Enum) && !flags.is(Flags.Case)
      isSealedTrait || isSealedAbstract || isEnum
    }
  }

  private def isRecursiveType(using Quotes)(tpe: quotes.reflect.TypeRepr): Boolean = {
    import quotes.reflect.*

    def containsType(searchIn: TypeRepr, searchFor: TypeRepr, visited: Set[TypeRepr]): Boolean = {
      val dealiased = searchIn.dealias
      if (visited.contains(dealiased)) return false
      val newVisited = visited + dealiased

      if (dealiased =:= searchFor.dealias) return true

      val typeArgsContain = dealiased match {
        case AppliedType(_, args) if args.nonEmpty =>
          args.exists(arg => containsType(arg, searchFor, newVisited))
        case _ => false
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
      val methodType       = MethodType(Nil)(_ => Nil, _ => unpackedFieldTpe)
      Refinement(parent, fieldName, methodType)
    }
  }

  private def buildStructuralTypeWithTag(using
    Quotes
  )(tagName: String, fields: List[(String, quotes.reflect.TypeRepr)]): quotes.reflect.TypeRepr = {
    import quotes.reflect.*

    val baseTpe = TypeRepr.of[AnyRef]

    val tagLiteralType = ConstantType(StringConstant(tagName))
    val withTag        = Refinement(baseTpe, "Tag", TypeBounds(tagLiteralType, tagLiteralType))

    fields.foldLeft(withTag) { case (parent, (fieldName, fieldTpe)) =>
      val unpackedFieldTpe = fullyUnpackType(fieldTpe)
      val methodType       = MethodType(Nil)(_ => Nil, _ => unpackedFieldTpe)
      Refinement(parent, fieldName, methodType)
    }
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

      buildStructuralTypeWithTag(caseName, fields)
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
   * Transform a product schema (case class) to its structural equivalent.
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
          (name, reflect.typeName.name)
        })

        new Schema[S](
          new Reflect.Record[Binding, S](
            fields = record.fields.map { field =>
              field.value.asInstanceOf[Reflect.Bound[Any]].asTerm[S](field.name)
            },
            typeName = new TypeName[S](new Namespace(Nil, Nil), typeName, Nil),
            recordBinding = new Binding.Record[S](
              constructor = new Constructor[S] {
                def usedRegisters: RegisterOffset = totalRegisters

                def construct(in: Registers, baseOffset: RegisterOffset): S = {
                  val nominal = binding.constructor.construct(in, baseOffset)
                  nominal.asInstanceOf[S]
                }
              },
              deconstructor = new Deconstructor[S] {
                def usedRegisters: RegisterOffset = totalRegisters

                def deconstruct(out: Registers, baseOffset: RegisterOffset, in: S): Unit =
                  binding.deconstructor.deconstruct(out, baseOffset, in.asInstanceOf[A])
              }
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

  /**
   * Transform a sum type schema (sealed trait/enum) to its structural union
   * type equivalent.
   */
  def transformSumTypeSchema[A, S](schema: Schema[A]): Schema[S] =
    schema.reflect match {
      case variant: Reflect.Variant[Binding, A] @unchecked =>
        val binding = variant.variantBinding.asInstanceOf[Binding.Variant[A]]

        val unionTypeName = variant.cases.map { case_ =>
          val caseName   = case_.name
          val caseFields = case_.value match {
            case record: Reflect.Record[Binding, _] @unchecked =>
              record.fields.map { field =>
                (field.name, field.value.asInstanceOf[Reflect.Bound[Any]].typeName.name)
              }.toList
            case _ => Nil
          }
          normalizeUnionCaseTypeName(caseName, caseFields)
        }.mkString("|")

        val newMatchers = Matchers[S](
          variant.cases.indices.map { idx =>
            val originalMatcher = binding.matchers(idx)
            new Matcher[S] {
              def downcastOrNull(any: Any): S =
                originalMatcher.downcastOrNull(any).asInstanceOf[S]
            }
          }: _*
        )

        new Schema[S](
          new Reflect.Variant[Binding, S](
            cases = variant.cases.map { case_ =>
              case_.asInstanceOf[Term[Binding, S, ? <: S]]
            },
            typeName = new TypeName[S](new Namespace(Nil, Nil), unionTypeName, Nil),
            variantBinding = new Binding.Variant[S](
              discriminator = (s: S) => {
                binding.discriminator.discriminate(s.asInstanceOf[A])
              },
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
  private def normalizeUnionCaseTypeName(tagName: String, fields: List[(String, String)]): String = {
    val sorted     = fields.sortBy(_._1)
    val tagPart    = s"""Tag:"$tagName""""
    val fieldParts = sorted.map { case (name, typeName) =>
      s"$name:${simplifyTypeName(typeName)}"
    }
    (tagPart :: fieldParts).mkString("{", ",", "}")
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
