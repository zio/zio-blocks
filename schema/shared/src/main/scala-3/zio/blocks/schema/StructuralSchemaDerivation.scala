package zio.blocks.schema

import scala.quoted.*
import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset._
import zio.blocks.schema.{Term => SchemaTerm}

/**
 * Macro implementation for deriving schemas from structural (refinement) types.
 *
 * Supports:
 *   - Record types: `{ val name: String; val age: Int }`
 *   - Variant types (unions):
 *     `{ type Tag = "Case1"; val x: Int } | { type Tag = "Case2"; val y: String }`
 *
 * Structural types have no runtime representation, so this derivation creates
 * schemas that work with DynamicValue.
 */
private[schema] object StructuralSchemaDerivation {

  def derive[A: Type](using Quotes): Expr[Schema[A]] = {
    import quotes.reflect.*

    val tpe = TypeRepr.of[A]

    // Check if this is a union type
    if (isUnionType(tpe)) {
      deriveVariantSchema[A](tpe)
    } else {
      // Try refinement type
      val fields = extractRefinementFields(tpe)

      if (fields.isEmpty) {
        report.errorAndAbort(
          s"Type ${tpe.show} is not a structural type or has no val/def members. " +
            "Structural types must have the form: { val name: Type; ... } or union types."
        )
      }

      generateRecordSchema[A](fields)
    }
  }

  // ============================================================================
  // Union Type Detection and Processing
  // ============================================================================

  private def isUnionType(using Quotes)(tpe: quotes.reflect.TypeRepr): Boolean = {
    import quotes.reflect.*
    tpe match {
      case OrType(_, _) => true
      case _            => false
    }
  }

  private def extractUnionMembers(using Quotes)(tpe: quotes.reflect.TypeRepr): List[quotes.reflect.TypeRepr] = {
    import quotes.reflect.*

    def loop(t: TypeRepr): List[TypeRepr] = t match {
      case OrType(left, right) => loop(left) ++ loop(right)
      case other               => List(other)
    }

    loop(tpe)
  }

  private def extractTagFromRefinement(using Quotes)(tpe: quotes.reflect.TypeRepr): Option[String] = {
    import quotes.reflect.*

    def loop(t: TypeRepr): Option[String] = t match {
      case Refinement(parent, "Tag", TypeBounds(lo, hi)) =>
        lo match {
          case ConstantType(StringConstant(tag)) => Some(tag)
          case _                                 =>
            hi match {
              case ConstantType(StringConstant(tag)) => Some(tag)
              case _                                 => loop(parent)
            }
        }
      case Refinement(parent, _, _) => loop(parent)
      case _                        => None
    }

    loop(tpe)
  }

  /**
   * Derive a proper Variant schema for union types. Each union member becomes a
   * case in the variant.
   */
  private def deriveVariantSchema[A: Type](using
    Quotes
  )(
    tpe: quotes.reflect.TypeRepr
  ): Expr[Schema[A]] = {
    import quotes.reflect.*

    val members = extractUnionMembers(tpe)

    // Extract case information from each union member
    val caseData: List[(String, List[(String, TypeRepr)])] = members.map { memberTpe =>
      val tag = extractTagFromRefinement(memberTpe).getOrElse {
        // Generate a tag from the type structure
        val fields = extractRefinementFields(memberTpe).filterNot(_._1 == "Tag")
        s"Case_${fields.map(_._1).mkString("_")}"
      }

      val fields = extractRefinementFields(memberTpe).filterNot(_._1 == "Tag")
      (tag, fields)
    }

    if (caseData.isEmpty) {
      report.errorAndAbort(s"Union type ${tpe.show} has no cases")
    }

    generateVariantSchema[A](caseData)
  }

  private def generateVariantSchema[A: Type](using
    Quotes
  )(
    cases: List[(String, List[(String, quotes.reflect.TypeRepr)])]
  ): Expr[Schema[A]] = {
    import quotes.reflect.*

    // Build case Term expressions
    // Each case becomes a Term[Binding, A, CaseType] where CaseType <: A
    val caseTermExprs: List[Expr[SchemaTerm[Binding, A, ? <: A]]] = cases.map { case (tagName, fields) =>
      val tagExpr = Expr(tagName)

      // Build the record schema for this case's fields
      val fieldTerms: List[Expr[SchemaTerm[Binding, Any, ?]]] = fields.map { case (name, fieldTpe) =>
        fieldTpe.asType match {
          case '[ft] =>
            val nameExpr   = Expr(name)
            val schemaExpr = Expr.summon[Schema[ft]] match {
              case Some(s) => s
              case None    =>
                report.errorAndAbort(s"No Schema for field '$name' of type ${fieldTpe.show}")
            }
            '{ $schemaExpr.reflect.asTerm[Any]($nameExpr) }
        }
      }

      val fieldSeq = Varargs(fieldTerms)

      '{
        // Create a Record schema for this case
        val caseFields = IndexedSeq($fieldSeq: _*)

        val caseConstructor = new Constructor[A] {
          def usedRegisters: RegisterOffset                       = RegisterOffset.Zero
          def construct(in: Registers, offset: RegisterOffset): A =
            throw new UnsupportedOperationException("Structural variant case cannot be constructed")
        }

        val caseDeconstructor = new Deconstructor[A] {
          def usedRegisters: RegisterOffset                                    = RegisterOffset.Zero
          def deconstruct(out: Registers, offset: RegisterOffset, in: A): Unit = ()
        }

        val caseReflect: Reflect[Binding, A] = new Reflect.Record[Binding, A](
          caseFields.asInstanceOf[IndexedSeq[SchemaTerm[Binding, A, ?]]],
          new TypeName[A](new Namespace(Nil, Nil), $tagExpr, Nil),
          new Binding.Record[A](caseConstructor, caseDeconstructor)
        )

        new SchemaTerm[Binding, A, A](
          $tagExpr,
          caseReflect
        ).asInstanceOf[SchemaTerm[Binding, A, ? <: A]]
      }
    }

    val caseSeq      = Varargs(caseTermExprs)
    val caseTags     = cases.map(_._1)
    val typeName     = s"StructuralVariant[${caseTags.mkString("|")}]"
    val typeNameExpr = Expr(typeName)

    '{
      val casesVec = IndexedSeq($caseSeq: _*)

      // Create discriminator - checks which case variant matches
      val discriminator = new Discriminator[A] {
        def discriminate(value: A): Int = 0 // Structural types can't be discriminated at runtime
      }

      // Create matchers - one per case
      val matcherSeq: IndexedSeq[Matcher[? <: A]] = casesVec.indices.map { _ =>
        new Matcher[A] {
          def downcastOrNull(any: Any): A = any.asInstanceOf[A]
        }.asInstanceOf[Matcher[? <: A]]
      }.toIndexedSeq

      val matchers = new Matchers[A](matcherSeq)

      new Schema[A](
        new Reflect.Variant[Binding, A](
          casesVec,
          new TypeName[A](new Namespace(Nil, Nil), $typeNameExpr, Nil),
          new Binding.Variant[A](discriminator, matchers)
        )
      )
    }
  }

  // ============================================================================
  // Record Type Processing
  // ============================================================================

  private def extractRefinementFields(using
    Quotes
  )(tpe: quotes.reflect.TypeRepr): List[(String, quotes.reflect.TypeRepr)] = {
    import quotes.reflect.*

    def loop(t: TypeRepr, acc: List[(String, TypeRepr)]): List[(String, TypeRepr)] = t match {
      case Refinement(parent, name, info) =>
        val fieldDef: Option[(String, TypeRepr)] = info match {
          case ByNameType(underlying) =>
            Some((name, underlying))
          case MethodType(Nil, Nil, resType) =>
            Some((name, resType))
          case TypeBounds(_, _) =>
            None // type alias - skip
          case other if !other.isInstanceOf[MethodType] && !other.isInstanceOf[PolyType] =>
            Some((name, other))
          case _ =>
            None
        }
        val newAcc = fieldDef.map(_ :: acc).getOrElse(acc)
        loop(parent, newAcc)
      case _ =>
        acc
    }

    loop(tpe, Nil).reverse
  }

  private def generateRecordSchema[A: Type](using
    Quotes
  )(
    fields: List[(String, quotes.reflect.TypeRepr)]
  ): Expr[Schema[A]] = {
    import quotes.reflect.*

    val fieldExprs: List[Expr[SchemaTerm[Binding, A, ?]]] = fields.map { case (name, fieldTpe) =>
      fieldTpe.asType match {
        case '[ft] =>
          val nameExpr   = Expr(name)
          val schemaExpr = Expr.summon[Schema[ft]] match {
            case Some(s) => s
            case None    =>
              report.errorAndAbort(s"No Schema available for field '$name' of type ${fieldTpe.show}")
          }
          '{ $schemaExpr.reflect.asTerm[A]($nameExpr) }
      }
    }

    val fieldSeq = Varargs(fieldExprs)
    val typeName = generateTypeName[A](fields)

    '{
      val terms   = IndexedSeq($fieldSeq: _*)
      val tpeName = $typeName

      val constructor = new Constructor[A] {
        def usedRegisters: RegisterOffset                       = RegisterOffset.Zero
        def construct(in: Registers, offset: RegisterOffset): A =
          throw new UnsupportedOperationException(
            "Structural types cannot be constructed at runtime. " +
              "Use DynamicValue or toDynamicValue/fromDynamicValue methods."
          )
      }

      val deconstructor = new Deconstructor[A] {
        def usedRegisters: RegisterOffset                                    = RegisterOffset.Zero
        def deconstruct(out: Registers, offset: RegisterOffset, in: A): Unit = ()
      }

      new Schema[A](
        new Reflect.Record[Binding, A](
          terms,
          tpeName,
          new Binding.Record[A](constructor, deconstructor)
        )
      )
    }
  }

  private def generateTypeName[A: Type](using
    Quotes
  )(
    fields: List[(String, quotes.reflect.TypeRepr)]
  ): Expr[TypeName[A]] = {
    val fieldNames = fields.map(_._1).mkString(",")
    val name       = s"Structural[$fieldNames]"

    '{
      new TypeName[A](
        new Namespace(Nil, Nil),
        ${ Expr(name) },
        Nil
      )
    }
  }
}
