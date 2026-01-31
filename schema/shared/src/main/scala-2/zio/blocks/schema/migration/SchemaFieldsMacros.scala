package zio.blocks.schema.migration

import scala.language.experimental.macros
import scala.reflect.macros.whitebox

/**
 * Macros for extracting field names from case class types at compile time.
 *
 * Inspects type A and extracts field names as an HList of singleton string
 * types, enabling compile-time validation of migration completeness.
 */
object SchemaFieldsMacros {

  /**
   * Derive a SchemaFields instance for type A with concrete field types.
   *
   * For case class types, extracts field names as an HList type like: "name" ::
   * "age" :: HNil
   *
   * For non-case-class types, returns HNil.
   */
  implicit def derived[A]: SchemaFields[A] = macro SchemaFieldsMacros.derivedImpl[A]

  /**
   * Create a SchemaFields from the type structure.
   */
  def fromType[A]: SchemaFields[A] = macro SchemaFieldsMacros.fromTypeImpl[A]

  /**
   * Create a SchemaFields from explicit field names.
   */
  def fromNames[A](names: String*): SchemaFields[A] = macro SchemaFieldsMacros.fromNamesImpl[A]

  // ─────────────────────────────────────────────────────────────────────────
  // Macro Implementations
  // ─────────────────────────────────────────────────────────────────────────

  def derivedImpl[A: c.WeakTypeTag](c: whitebox.Context): c.Expr[SchemaFields[A]] = {
    import c.universe._

    val tpe = weakTypeOf[A]

    // Extract field names from case class
    val fieldNames: List[String] = {
      val sym = tpe.typeSymbol
      if (sym.isClass && sym.asClass.isCaseClass) {
        sym.asClass.primaryConstructor.asMethod.paramLists.flatten.map(_.name.decodedName.toString)
      } else {
        Nil
      }
    }

    if (fieldNames.isEmpty) {
      c.Expr[SchemaFields[A]](q"""
        _root_.zio.blocks.schema.migration.SchemaFields.emptyWith[${weakTypeOf[
          A
        ]}, _root_.zio.blocks.schema.migration.FieldSet.HNil]
      """)
    } else {
      // Build HList type from field names
      val hlistType = fieldNames.foldRight(tq"_root_.zio.blocks.schema.migration.FieldSet.HNil": Tree) { (name, acc) =>
        val nameLiteral = c.internal.constantType(Constant(name))
        tq"_root_.zio.blocks.schema.migration.FieldSet.::[$nameLiteral, $acc]"
      }

      val namesExpr = q"_root_.scala.List(..$fieldNames)"

      c.Expr[SchemaFields[A]](q"""
        new _root_.zio.blocks.schema.migration.SchemaFields[${weakTypeOf[A]}] {
          type Fields = $hlistType
          def fieldNames: _root_.scala.List[_root_.java.lang.String] = $namesExpr
        }
      """)
    }
  }

  def fromTypeImpl[A: c.WeakTypeTag](c: whitebox.Context): c.Expr[SchemaFields[A]] = {
    import c.universe._

    val tpe = weakTypeOf[A]

    // Try to extract field names from case class
    val fieldNames: List[String] = {
      val sym = tpe.typeSymbol
      if (sym.isClass && sym.asClass.isCaseClass) {
        sym.asClass.primaryConstructor.asMethod.paramLists.flatten.map(_.name.decodedName.toString)
      } else {
        Nil
      }
    }

    if (fieldNames.isEmpty) {
      c.Expr[SchemaFields[A]](q"""
        _root_.zio.blocks.schema.migration.SchemaFields.emptyWith[${weakTypeOf[
          A
        ]}, _root_.zio.blocks.schema.migration.FieldSet.HNil]
      """)
    } else {
      // Build HList type from field names
      val hlistType = fieldNames.foldRight(tq"_root_.zio.blocks.schema.migration.FieldSet.HNil": Tree) { (name, acc) =>
        val nameLiteral = c.internal.constantType(Constant(name))
        tq"_root_.zio.blocks.schema.migration.FieldSet.::[$nameLiteral, $acc]"
      }

      val namesExpr = q"_root_.scala.List(..$fieldNames)"

      c.Expr[SchemaFields[A]](q"""
        new _root_.zio.blocks.schema.migration.SchemaFields[${weakTypeOf[A]}] {
          type Fields = $hlistType
          def fieldNames: _root_.scala.List[_root_.java.lang.String] = $namesExpr
        }
      """)
    }
  }

  def fromNamesImpl[A: c.WeakTypeTag](c: whitebox.Context)(
    names: c.Expr[String]*
  ): c.Expr[SchemaFields[A]] = {
    import c.universe._

    // Extract string literals
    val fieldNames: List[String] = names.toList.map { expr =>
      expr.tree match {
        case Literal(Constant(s: String)) => s
        case _                            =>
          c.abort(c.enclosingPosition, "fromNames requires string literals")
      }
    }

    if (fieldNames.isEmpty) {
      c.Expr[SchemaFields[A]](q"""
        _root_.zio.blocks.schema.migration.SchemaFields.emptyWith[${weakTypeOf[
          A
        ]}, _root_.zio.blocks.schema.migration.FieldSet.HNil]
      """)
    } else {
      // Build HList type from field names
      val hlistType = fieldNames.foldRight(tq"_root_.zio.blocks.schema.migration.FieldSet.HNil": Tree) { (name, acc) =>
        val nameLiteral = c.internal.constantType(Constant(name))
        tq"_root_.zio.blocks.schema.migration.FieldSet.::[$nameLiteral, $acc]"
      }

      val namesExpr = q"_root_.scala.List(..$fieldNames)"

      c.Expr[SchemaFields[A]](q"""
        new _root_.zio.blocks.schema.migration.SchemaFields[${weakTypeOf[A]}] {
          type Fields = $hlistType
          def fieldNames: _root_.scala.List[_root_.java.lang.String] = $namesExpr
        }
      """)
    }
  }
}
