package zio.blocks.schema.migration

import scala.reflect.macros.whitebox
import zio.blocks.schema.Schema

/**
 * Macros for creating MigrationBuilder with compile-time field tracking (Scala
 * 2).
 */
object MigrationBuilderMacros {

  /**
   * Implementation of withFieldTracking macro.
   *
   * Extracts field names from case class types A and B at compile time and
   * creates a MigrationBuilder with those fields as HList types.
   */
  def withFieldTrackingImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    sourceSchema: c.Expr[Schema[A]],
    targetSchema: c.Expr[Schema[B]]
  ): c.Expr[MigrationBuilder[A, B, _, _]] = {
    import c.universe._

    val srcTpe = weakTypeOf[A]
    val tgtTpe = weakTypeOf[B]

    // Extract field names from case class
    def extractFieldNames(tpe: Type): List[String] = {
      val sym = tpe.typeSymbol
      if (sym.isClass && sym.asClass.isCaseClass) {
        sym.asClass.primaryConstructor.asMethod.paramLists.flatten.map(_.name.decodedName.toString)
      } else {
        Nil
      }
    }

    val srcFields = extractFieldNames(srcTpe)
    val tgtFields = extractFieldNames(tgtTpe)

    // Build HList type from field names
    def buildHListType(names: List[String]): Tree =
      names.foldRight(tq"_root_.zio.blocks.schema.migration.FieldSet.HNil": Tree) { (name, acc) =>
        val nameLiteral = c.internal.constantType(Constant(name))
        tq"_root_.zio.blocks.schema.migration.FieldSet.::[$nameLiteral, $acc]"
      }

    val srcHListType = buildHListType(srcFields)
    val tgtHListType = buildHListType(tgtFields)

    c.Expr[MigrationBuilder[A, B, _, _]](q"""
      _root_.zio.blocks.schema.migration.MigrationBuilder.initial[
        ${weakTypeOf[A]},
        ${weakTypeOf[B]},
        $srcHListType,
        $tgtHListType
      ]($sourceSchema, $targetSchema)
    """)
  }

  /**
   * Implementation of withFields macro.
   *
   * Creates a MigrationBuilder with explicitly provided field names.
   */
  def withFieldsImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    srcFields: c.Expr[String]*
  )(
    tgtFields: c.Expr[String]*
  )(
    sourceSchema: c.Expr[Schema[A]],
    targetSchema: c.Expr[Schema[B]]
  ): c.Expr[MigrationBuilder[A, B, _, _]] = {
    import c.universe._

    // Extract string literals
    def extractStrings(exprs: Seq[c.Expr[String]]): List[String] =
      exprs.toList.map { expr =>
        expr.tree match {
          case Literal(Constant(s: String)) => s
          case _                            =>
            c.abort(c.enclosingPosition, "withFields requires string literals")
        }
      }

    val srcNames = extractStrings(srcFields)
    val tgtNames = extractStrings(tgtFields)

    // Build HList type from field names
    def buildHListType(names: List[String]): Tree =
      names.foldRight(tq"_root_.zio.blocks.schema.migration.FieldSet.HNil": Tree) { (name, acc) =>
        val nameLiteral = c.internal.constantType(Constant(name))
        tq"_root_.zio.blocks.schema.migration.FieldSet.::[$nameLiteral, $acc]"
      }

    val srcHListType = buildHListType(srcNames)
    val tgtHListType = buildHListType(tgtNames)

    c.Expr[MigrationBuilder[A, B, _, _]](q"""
      _root_.zio.blocks.schema.migration.MigrationBuilder.initial[
        ${weakTypeOf[A]},
        ${weakTypeOf[B]},
        $srcHListType,
        $tgtHListType
      ]($sourceSchema, $targetSchema)
    """)
  }
}
