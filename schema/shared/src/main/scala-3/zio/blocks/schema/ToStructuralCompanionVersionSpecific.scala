package zio.blocks.schema

import scala.quoted.*

/**
 * Scala 3 version-specific companion for ToStructural.
 *
 * Provides macro-based derivation of ToStructural instances. Structural types
 * in Scala 3 are backed by Selectable.
 */
trait ToStructuralCompanionVersionSpecific {

  /**
   * Derives a ToStructural instance for type A.
   *
   * Will fail at compile-time for:
   *   - Recursive types
   *   - Mutually recursive types
   */
  transparent inline given [A]: ToStructural[A] = ${ ToStructuralMacros.derivedImpl[A] }
}

/**
 * Selectable-backed structural record implementation for Scala 3.
 *
 * Provides field access via the selectDynamic method. Uses scala.Selectable as
 * a marker trait with a custom selectDynamic implementation.
 */
class StructuralRecord(private val fields: Map[String, Any]) extends scala.Selectable {
  def selectDynamic(name: String): Any = fields.getOrElse(
    name,
    throw new NoSuchFieldException(s"Field '$name' not found in structural record")
  )

  override def toString: String = fields.map { case (k, v) => s"$k: $v" }.mkString("{", ", ", "}")

  override def equals(obj: Any): Boolean = obj match {
    case that: StructuralRecord => this.fields == that.fields
    case _                      => false
  }

  override def hashCode(): Int = fields.hashCode()
}

object StructuralRecord {
  def apply(fields: (String, Any)*): StructuralRecord = new StructuralRecord(fields.toMap)
}

private[schema] object ToStructuralMacros {

  def derivedImpl[A: Type](using Quotes): Expr[ToStructural[A]] = {
    import quotes.reflect.*

    def fail(msg: String): Nothing = CommonMacroOps.fail(msg)

    def typeArgs(tpe: TypeRepr): List[TypeRepr] = CommonMacroOps.typeArgs(tpe)

    def directSubTypes(tpe: TypeRepr): List[TypeRepr] = CommonMacroOps.directSubTypes(tpe)

    def isSealedTraitOrAbstractClass(tpe: TypeRepr): Boolean = tpe.classSymbol.fold(false) { symbol =>
      val flags = symbol.flags
      flags.is(Flags.Sealed) && (flags.is(Flags.Abstract) || flags.is(Flags.Trait))
    }

    def isNonAbstractScalaClass(tpe: TypeRepr): Boolean = tpe.classSymbol.fold(false) { symbol =>
      val flags = symbol.flags
      !(flags.is(Flags.Abstract) || flags.is(Flags.JavaDefined) || flags.is(Flags.Trait))
    }

    def isEnumOrModuleValue(tpe: TypeRepr): Boolean =
      tpe.termSymbol.flags.is(Flags.Enum) || tpe.typeSymbol.flags.is(Flags.Module)

    def isUnion(tpe: TypeRepr): Boolean = CommonMacroOps.isUnion(tpe)

    def allUnionTypes(tpe: TypeRepr): List[TypeRepr] = CommonMacroOps.allUnionTypes(tpe)

    // Check if a type is from the standard library (should not be checked for recursion)
    def isStdLibType(tpe: TypeRepr): Boolean = {
      val fullName = tpe.typeSymbol.fullName
      fullName.startsWith("scala.") ||
      fullName.startsWith("java.") ||
      fullName.startsWith("zio.") // ZIO types are also stable
    }

    // Check for recursive types - only for user-defined types
    def checkRecursive(tpe: TypeRepr, seen: Set[TypeRepr] = Set.empty): Unit = {
      // Skip standard library types - they are well-defined and not user-recursive
      if (isStdLibType(tpe)) return

      if (seen.exists(_ =:= tpe)) {
        fail(
          s"Recursive type '${tpe.show}' cannot be converted to a structural type. " +
            "Structural types must be finite and non-recursive."
        )
      }
      val newSeen = seen + tpe

      if (isSealedTraitOrAbstractClass(tpe)) {
        directSubTypes(tpe).foreach(checkRecursive(_, newSeen))
      } else if (isUnion(tpe)) {
        allUnionTypes(tpe).foreach(checkRecursive(_, newSeen))
      } else if (isNonAbstractScalaClass(tpe) && !isEnumOrModuleValue(tpe)) {
        tpe.classSymbol.foreach { clsSym =>
          val (tpeTypeArgs, tpeTypeParams, tpeParams) = clsSym.primaryConstructor.paramSymss match {
            case tps :: ps if tps.exists(_.isTypeParam) => (typeArgs(tpe), tps, ps)
            case ps                                     => (Nil, Nil, ps)
          }
          tpeParams.flatten.foreach { param =>
            var fTpe = tpe.memberType(param).dealias
            if (tpeTypeArgs.nonEmpty) fTpe = fTpe.substituteTypes(tpeTypeParams, tpeTypeArgs)
            if (
              !isStdLibType(fTpe) && (isNonAbstractScalaClass(fTpe) || isSealedTraitOrAbstractClass(fTpe) || isUnion(
                fTpe
              ))
            ) {
              checkRecursive(fTpe, newSeen)
            }
          }
        }
      }
    }

    val tpe = TypeRepr.of[A].dealias

    // Check for recursion
    checkRecursive(tpe)

    if (isEnumOrModuleValue(tpe)) {
      // Case object - no fields
      '{
        new ToStructural[A] {
          type StructuralType = StructuralRecord

          def apply(schema: Schema[A]): Schema[StructuralType] = {
            val reflect = schema.reflect.asRecord.getOrElse(
              throw new IllegalArgumentException("Expected a record schema")
            )
            val typeName = new TypeName[StructuralType](
              Namespace.zioBlocksSchema,
              "{}",
              Nil
            )
            new Schema(
              reflect
                .typeName(typeName.asInstanceOf[TypeName[A]])
                .asInstanceOf[Reflect.Bound[StructuralType]]
            )
          }
        }
      }
    } else if (isUnion(tpe)) {
      // Union types - generate structural representation
      // Note: We use discriminated union encoding with tag/value fields

      // For union types, we create a structural record with a "tag" field and "value" field
      '{
        new ToStructural[A] {
          type StructuralType = StructuralRecord

          def apply(schema: Schema[A]): Schema[StructuralType] = {
            val reflect = schema.reflect.asVariant.getOrElse(
              throw new IllegalArgumentException("Expected a variant schema")
            )

            // For variants/unions, use a discriminated union representation
            val tagTypeName    = new TypeName[String](Namespace.scala, "String", Nil)
            val valueTypeName  = new TypeName[Any](Namespace.scala, "Any", Nil)
            val fields         = Seq(("tag", tagTypeName), ("value", valueTypeName))
            val structTypeName = ToStructural.structuralTypeName(fields)

            val typeName = new TypeName[StructuralType](
              Namespace.zioBlocksSchema,
              structTypeName,
              Nil
            )
            new Schema(
              reflect
                .typeName(typeName.asInstanceOf[TypeName[A]])
                .asInstanceOf[Reflect.Bound[StructuralType]]
            )
          }
        }
      }
    } else if (isSealedTraitOrAbstractClass(tpe)) {
      // Sealed traits - generate union-like structural representation
      '{
        new ToStructural[A] {
          type StructuralType = StructuralRecord

          def apply(schema: Schema[A]): Schema[StructuralType] = {
            val reflect = schema.reflect.asVariant.getOrElse(
              throw new IllegalArgumentException("Expected a variant schema")
            )

            // For variants/sealed traits, use a discriminated union representation
            val tagTypeName    = new TypeName[String](Namespace.scala, "String", Nil)
            val valueTypeName  = new TypeName[Any](Namespace.scala, "Any", Nil)
            val fields         = Seq(("tag", tagTypeName), ("value", valueTypeName))
            val structTypeName = ToStructural.structuralTypeName(fields)

            val typeName = new TypeName[StructuralType](
              Namespace.zioBlocksSchema,
              structTypeName,
              Nil
            )
            new Schema(
              reflect
                .typeName(typeName.asInstanceOf[TypeName[A]])
                .asInstanceOf[Reflect.Bound[StructuralType]]
            )
          }
        }
      }
    } else if (isNonAbstractScalaClass(tpe)) {
      // Case class - generate ToStructural at compile time, extract fields at runtime
      '{
        new ToStructural[A] {
          type StructuralType = StructuralRecord

          def apply(schema: Schema[A]): Schema[StructuralType] = {
            val reflect = schema.reflect.asRecord.getOrElse(
              throw new IllegalArgumentException("Expected a record schema")
            )

            // Extract field names and type names from the reflect structure at runtime
            val fields: Seq[(String, TypeName[?])] = reflect.fields.map { term =>
              (term.name, term.value.typeName)
            }
            val structTypeName = ToStructural.structuralTypeName(fields)

            val typeName = new TypeName[StructuralType](
              Namespace.zioBlocksSchema,
              structTypeName,
              Nil
            )
            new Schema(
              reflect
                .typeName(typeName.asInstanceOf[TypeName[A]])
                .asInstanceOf[Reflect.Bound[StructuralType]]
            )
          }
        }
      }
    } else {
      fail(
        s"Cannot derive ToStructural for type '${tpe.show}'. Only case classes, case objects, and sealed traits are supported."
      )
    }
  }
}
