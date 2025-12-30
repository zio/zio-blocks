package zio.blocks.schema

import scala.quoted.*

trait ToStructuralVersionSpecific {
  transparent inline given [A]: ToStructural[A] = ${ ToStructuralMacro.derived[A] }
}

private[schema] object ToStructuralMacro {
  def derived[A: Type](using Quotes): Expr[ToStructural[A]] = {
    import quotes.reflect.*

    val aTpe = TypeRepr.of[A].dealias

    // Check platform support
    if (!Platform.supportsReflection) {
      report.errorAndAbort(
        s"""Cannot generate ToStructural[${aTpe.show}] on ${Platform.name}.
           |
           |Structural types require reflection which is only available on JVM.
           |Consider using a case class instead.""".stripMargin
      )
    }

    // Check for recursive types
    if (isRecursiveType(aTpe)) {
      report.errorAndAbort(
        s"""Cannot generate structural type for recursive type ${aTpe.show}.
           |
           |Structural types cannot represent recursive structures.
           |Scala's type system does not support infinite types.""".stripMargin
      )
    }

    // Determine the source type structure
    if (isProductType(aTpe)) {
      deriveForProduct[A](aTpe)
    } else if (isTupleType(aTpe)) {
      deriveForTuple[A](aTpe)
    } else {
      report.errorAndAbort(
        s"""Cannot generate ToStructural for ${aTpe.show}.
           |
           |Only product types (case classes) and tuples are currently supported.
           |Sum types (sealed traits) require Scala 3 union types.""".stripMargin
      )
    }
  }

  private def isProductType(using Quotes)(tpe: quotes.reflect.TypeRepr): Boolean = {
    import quotes.reflect.*
    tpe.classSymbol.exists { sym =>
      val flags = sym.flags
      // Include case objects (Module flag) and case classes
      val isCaseObject = flags.is(Flags.Module) && flags.is(Flags.Case)
      val isCaseClass = !flags.is(Flags.Abstract) && !flags.is(Flags.Trait) && sym.primaryConstructor.exists
      isCaseObject || isCaseClass
    }
  }

  private def isTupleType(using Quotes)(tpe: quotes.reflect.TypeRepr): Boolean = {
    tpe.typeSymbol.fullName.startsWith("scala.Tuple")
  }

  private def isRecursiveType(using Quotes)(tpe: quotes.reflect.TypeRepr): Boolean = {
    import quotes.reflect.*

    def containsType(searchIn: TypeRepr, searchFor: TypeRepr, visited: Set[TypeRepr]): Boolean = {
      if (visited.contains(searchIn.dealias)) return false
      val newVisited = visited + searchIn.dealias

      if (searchIn.dealias =:= searchFor.dealias) return true

      searchIn.dealias match {
        case AppliedType(_, args) =>
          args.exists(arg => containsType(arg, searchFor, newVisited))
        case _ =>
          // Check fields of product types
          searchIn.classSymbol.toList.flatMap { sym =>
            sym.primaryConstructor.paramSymss.flatten.filter(!_.isTypeParam).map { param =>
              searchIn.memberType(param).dealias
            }
          }.exists(fieldTpe => containsType(fieldTpe, searchFor, newVisited))
      }
    }

    // Check if any field type contains the original type
    tpe.classSymbol.toList.flatMap { sym =>
      sym.primaryConstructor.paramSymss.flatten.filter(!_.isTypeParam).map { param =>
        tpe.memberType(param).dealias
      }
    }.exists(fieldTpe => containsType(fieldTpe, tpe, Set.empty))
  }

  private def deriveForProduct[A: Type](using Quotes)(aTpe: quotes.reflect.TypeRepr): Expr[ToStructural[A]] = {
    import quotes.reflect.*

    val classSymbol = aTpe.classSymbol.getOrElse(
      report.errorAndAbort(s"${aTpe.show} is not a class type")
    )

    // Extract fields
    val fields: List[(String, TypeRepr)] = classSymbol.primaryConstructor.paramSymss.flatten
      .filter(!_.isTypeParam)
      .map { param =>
        val fieldName = param.name
        val fieldType = aTpe.memberType(param).dealias
        (fieldName, fieldType)
      }

    // Build the structural type as a refinement (or AnyRef for empty)
    val structuralTpe = buildStructuralType(fields)

    structuralTpe.asType match {
      case '[s] =>
        '{
          new ToStructural[A] {
            type StructuralType = s
            def apply(schema: Schema[A]): Schema[s] = {
              ToStructuralRuntime.transformProductSchema[A, s](schema)
            }
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

    // Build fields as _1, _2, etc.
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
            def apply(schema: Schema[A]): Schema[s] = {
              ToStructuralRuntime.transformTupleSchema[A, s](schema)
            }
          }
        }
    }
  }

  private def buildStructuralType(using Quotes)(fields: List[(String, quotes.reflect.TypeRepr)]): quotes.reflect.TypeRepr = {
    import quotes.reflect.*

    // Start with AnyRef (Object) as the base
    val baseTpe = TypeRepr.of[AnyRef]

    // Build refinements for each field (as def members)
    fields.foldLeft(baseTpe) { case (parent, (fieldName, fieldTpe)) =>
      // Create a MethodType for a no-arg method returning fieldTpe
      val methodType = MethodType(Nil)(_ => Nil, _ => fieldTpe)
      Refinement(parent, fieldName, methodType)
    }
  }
}

