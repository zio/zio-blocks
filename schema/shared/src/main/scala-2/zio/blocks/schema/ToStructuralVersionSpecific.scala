package zio.blocks.schema

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

trait ToStructuralVersionSpecific {
  implicit def toStructural[A]: ToStructural[A] = macro ToStructuralMacro.derived[A]
}

object ToStructuralMacro {
  def derived[A: c.WeakTypeTag](c: blackbox.Context): c.Expr[ToStructural[A]] = {
    import c.universe._

    val aTpe = weakTypeOf[A].dealias

    // Check platform support
    if (!Platform.supportsReflection) {
      c.abort(
        c.enclosingPosition,
        s"""Cannot generate ToStructural[${aTpe}] on ${Platform.name}.
           |
           |Structural types require reflection which is only available on JVM.
           |Consider using a case class instead.""".stripMargin
      )
    }

    // Check for recursive types
    if (isRecursiveType(c)(aTpe)) {
      c.abort(
        c.enclosingPosition,
        s"""Cannot generate structural type for recursive type ${aTpe}.
           |
           |Structural types cannot represent recursive structures.
           |Scala's type system does not support infinite types.""".stripMargin
      )
    }

    // Determine the source type structure
    if (isProductType(c)(aTpe)) {
      deriveForProduct[A](c)(aTpe)
    } else if (isTupleType(c)(aTpe)) {
      deriveForTuple[A](c)(aTpe)
    } else {
      c.abort(
        c.enclosingPosition,
        s"""Cannot generate ToStructural for ${aTpe}.
           |
           |Only product types (case classes) and tuples are currently supported.
           |Sum types (sealed traits) are not supported in Scala 2.""".stripMargin
      )
    }
  }

  private def isProductType(c: blackbox.Context)(tpe: c.universe.Type): Boolean =
    tpe.typeSymbol.isClass && tpe.typeSymbol.asClass.isCaseClass

  private def isTupleType(c: blackbox.Context)(tpe: c.universe.Type): Boolean =
    tpe.typeSymbol.fullName.startsWith("scala.Tuple")

  private def isRecursiveType(c: blackbox.Context)(tpe: c.universe.Type): Boolean = {
    import c.universe._

    def containsType(searchIn: Type, searchFor: Type, visited: Set[Type]): Boolean = {
      if (visited.contains(searchIn.dealias)) return false
      val newVisited = visited + searchIn.dealias

      if (searchIn.dealias =:= searchFor.dealias) return true

      searchIn.dealias match {
        case TypeRef(_, _, args) =>
          args.exists(arg => containsType(arg, searchFor, newVisited))
        case _ =>
          // Check fields of product types
          val fields = searchIn.decls.collect {
            case m: MethodSymbol if m.isCaseAccessor => m.returnType.asSeenFrom(searchIn, searchIn.typeSymbol)
          }
          fields.exists(fieldTpe => containsType(fieldTpe, searchFor, newVisited))
      }
    }

    // Check if any field type contains the original type
    val fields = tpe.decls.collect {
      case m: MethodSymbol if m.isCaseAccessor => m.returnType.asSeenFrom(tpe, tpe.typeSymbol)
    }
    fields.exists(fieldTpe => containsType(fieldTpe, tpe, Set.empty))
  }

  private def deriveForProduct[A: c.WeakTypeTag](c: blackbox.Context)(aTpe: c.universe.Type): c.Expr[ToStructural[A]] = {
    import c.universe._

    // Extract fields
    val fields: List[(String, Type)] = aTpe.decls.collect {
      case m: MethodSymbol if m.isCaseAccessor =>
        (m.name.toString, m.returnType.asSeenFrom(aTpe, aTpe.typeSymbol))
    }.toList

    val structuralTpe = buildStructuralType(c)(fields)

    c.Expr[ToStructural[A]](
      q"""
        new _root_.zio.blocks.schema.ToStructural[$aTpe] {
          type StructuralType = $structuralTpe
          def apply(schema: _root_.zio.blocks.schema.Schema[$aTpe]): _root_.zio.blocks.schema.Schema[$structuralTpe] = {
            _root_.zio.blocks.schema.ToStructuralRuntime.transformProductSchema[$aTpe, $structuralTpe](schema)
          }
        }
      """
    )
  }

  private def deriveForTuple[A: c.WeakTypeTag](c: blackbox.Context)(aTpe: c.universe.Type): c.Expr[ToStructural[A]] = {
    import c.universe._

    val typeArgs = aTpe.typeArgs

    // Build fields as _1, _2, etc.
    val fields: List[(String, Type)] = typeArgs.zipWithIndex.map { case (tpe, idx) =>
      (s"_${idx + 1}", tpe)
    }

    if (fields.isEmpty) {
      c.abort(c.enclosingPosition, "Cannot generate structural type for empty tuple")
    }

    val structuralTpe = buildStructuralType(c)(fields)

    c.Expr[ToStructural[A]](
      q"""
        new _root_.zio.blocks.schema.ToStructural[$aTpe] {
          type StructuralType = $structuralTpe
          def apply(schema: _root_.zio.blocks.schema.Schema[$aTpe]): _root_.zio.blocks.schema.Schema[$structuralTpe] = {
            _root_.zio.blocks.schema.ToStructuralRuntime.transformTupleSchema[$aTpe, $structuralTpe](schema)
          }
        }
      """
    )
  }

  private def buildStructuralType(c: blackbox.Context)(fields: List[(String, c.universe.Type)]): c.universe.Type = {
    import c.universe._

    // Handle empty structural types - return AnyRef (equivalent to {})
    if (fields.isEmpty) {
      return definitions.AnyRefTpe
    }

    // Build refinement type for structural type
    // { def field1: Type1; def field2: Type2; ... }

    // Sort fields alphabetically to match Scala 3 behavior
    val sortedFields = fields.sortBy(_._1)

    // Build refinement type using RefinedType
    // We construct it by creating a type tree and then getting its type
    val refinedTypeTree = sortedFields.foldLeft(tq"AnyRef": Tree) { case (parent, (name, tpe)) =>
      val methodName = TermName(name)
      tq"$parent { def $methodName: $tpe }"
    }

    // Get the type from the tree
    c.typecheck(refinedTypeTree, c.TYPEmode).tpe.dealias
  }
}

