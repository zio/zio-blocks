package zio.blocks.schema

import scala.language.experimental.macros
import scala.language.dynamics
import scala.reflect.macros.blackbox

/**
 * Scala 2 version-specific companion for ToStructural.
 *
 * Provides macro-based derivation of ToStructural instances. Structural types
 * in Scala 2 are backed by Dynamic.
 */
trait ToStructuralCompanionVersionSpecific {

  /**
   * Derives a ToStructural instance for type A.
   *
   * Will fail at compile-time for:
   *   - Recursive types
   *   - Mutually recursive types
   *   - Sum types (sealed traits/enums) - Scala 2 lacks union types
   */
  implicit def materialize[A]: ToStructural[A] = macro ToStructuralMacros.derivedImpl[A]
}

/**
 * Dynamic-backed structural record implementation for Scala 2.
 *
 * Provides field access via the selectDynamic method.
 */
class StructuralRecord(private val fields: Map[String, Any]) extends Dynamic {
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

  def derivedImpl[A: c.WeakTypeTag](c: blackbox.Context): c.Expr[ToStructural[A]] = {
    import c.universe._

    def fail(msg: String): Nothing = CommonMacroOps.fail(c)(msg)

    def typeArgs(tpe: Type): List[Type] = CommonMacroOps.typeArgs(c)(tpe)

    def directSubTypes(tpe: Type): List[Type] = CommonMacroOps.directSubTypes(c)(tpe)

    def isSealedTraitOrAbstractClass(tpe: Type): Boolean = tpe.typeSymbol.isClass && {
      val classSymbol = tpe.typeSymbol.asClass
      classSymbol.isSealed && (classSymbol.isAbstract || classSymbol.isTrait)
    }

    def isNonAbstractScalaClass(tpe: Type): Boolean =
      tpe.typeSymbol.isClass && !tpe.typeSymbol.isAbstract && !tpe.typeSymbol.isJava

    def isEnumOrModuleValue(tpe: Type): Boolean = tpe.typeSymbol.isModuleClass

    def primaryConstructor(tpe: Type): MethodSymbol = tpe.decls.collectFirst {
      case m: MethodSymbol if m.isPrimaryConstructor => m
    }.getOrElse(fail(s"Cannot find a primary constructor for '$tpe'"))

    // Check for recursive types
    def checkRecursive(tpe: Type, seen: Set[Type] = Set.empty): Unit = {
      if (seen.contains(tpe)) {
        fail(
          s"Recursive type '$tpe' cannot be converted to a structural type. " +
            "Structural types must be finite and non-recursive."
        )
      }
      val newSeen = seen + tpe

      if (isSealedTraitOrAbstractClass(tpe)) {
        directSubTypes(tpe).foreach(checkRecursive(_, newSeen))
      } else if (isNonAbstractScalaClass(tpe) && !isEnumOrModuleValue(tpe)) {
        val tpeTypeArgs   = typeArgs(tpe)
        val tpeTypeParams =
          if (tpeTypeArgs.nonEmpty) tpe.typeSymbol.asClass.typeParams
          else Nil
        primaryConstructor(tpe).paramLists.flatten.foreach { param =>
          var fTpe = param.asTerm.typeSignature.dealias
          if (tpeTypeArgs.nonEmpty) fTpe = fTpe.substituteTypes(tpeTypeParams, tpeTypeArgs)
          // Only check for recursion if the field type is a class/sealed type we can analyze
          if (isNonAbstractScalaClass(fTpe) || isSealedTraitOrAbstractClass(fTpe)) {
            checkRecursive(fTpe, newSeen)
          }
        }
      }
    }

    val tpe = weakTypeOf[A].dealias

    // Sum types are not supported in Scala 2 (no union types)
    if (isSealedTraitOrAbstractClass(tpe)) {
      fail(
        s"Sum type '$tpe' cannot be converted to a structural type in Scala 2. " +
          "Sum types require union types which are only available in Scala 3."
      )
    }

    // Check for recursion
    checkRecursive(tpe)

    // Generate field information for records
    if (isEnumOrModuleValue(tpe)) {
      // Case object - no fields
      c.Expr[ToStructural[A]](
        q"""
          new _root_.zio.blocks.schema.ToStructural[$tpe] {
            type StructuralType = _root_.zio.blocks.schema.StructuralRecord

            def apply(schema: _root_.zio.blocks.schema.Schema[$tpe]): _root_.zio.blocks.schema.Schema[StructuralType] = {
              val reflect = schema.reflect.asRecord.getOrElse(
                throw new IllegalArgumentException("Expected a record schema")
              )
              val typeName = new _root_.zio.blocks.schema.TypeName(
                _root_.zio.blocks.schema.Namespace.zioBlocksSchema,
                "{}",
                Nil
              )
              new _root_.zio.blocks.schema.Schema(
                reflect.typeName(typeName.asInstanceOf[_root_.zio.blocks.schema.TypeName[$tpe]])
                  .asInstanceOf[_root_.zio.blocks.schema.Reflect.Bound[StructuralType]]
              )
            }
          }
        """
      )
    } else if (isNonAbstractScalaClass(tpe)) {
      // Case class - generate ToStructural at compile time, extract fields at runtime
      c.Expr[ToStructural[A]](
        q"""
          new _root_.zio.blocks.schema.ToStructural[$tpe] {
            type StructuralType = _root_.zio.blocks.schema.StructuralRecord

            def apply(schema: _root_.zio.blocks.schema.Schema[$tpe]): _root_.zio.blocks.schema.Schema[StructuralType] = {
              val reflect = schema.reflect.asRecord.getOrElse(
                throw new IllegalArgumentException("Expected a record schema")
              )

              // Extract field names and type names from the reflect structure at runtime
              val fields: Seq[(String, _root_.zio.blocks.schema.TypeName[_])] = reflect.fields.map { term =>
                (term.name, term.value.typeName)
              }
              val structTypeName = _root_.zio.blocks.schema.ToStructural.structuralTypeName(fields)

              val typeName = new _root_.zio.blocks.schema.TypeName[StructuralType](
                _root_.zio.blocks.schema.Namespace.zioBlocksSchema,
                structTypeName,
                Nil
              )

              new _root_.zio.blocks.schema.Schema(
                reflect.typeName(typeName.asInstanceOf[_root_.zio.blocks.schema.TypeName[$tpe]])
                  .asInstanceOf[_root_.zio.blocks.schema.Reflect.Bound[StructuralType]]
              )
            }
          }
        """
      )
    } else {
      fail(s"Cannot derive ToStructural for type '$tpe'. Only case classes and case objects are supported.")
    }
  }
}
