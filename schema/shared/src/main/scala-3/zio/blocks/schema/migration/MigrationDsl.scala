package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicValue, Schema, ToStructural}
import zio.blocks.schema.CompanionOptics.optic
import zio.blocks.schema.Optic as ZOptic
import zio.blocks.schema.DynamicOptic

import scala.quoted.*

/** Typed DSL that compiles to PURE DynamicMigration data.
  *
  * No closures are stored: the macro expands into Path(...) + DynamicMigration
  * ops. We only allow: field selectors like _.foo.bar and constant defaults.
  */
object MigrationDsl {

  /** Builder used only during macro-expanded program construction. It
    * accumulates ops; we then build DynamicMigration.sequence(...) from them.
    */
  final class MigrationBuilder[A] private[migration] (
      private[migration] var ops: List[DynamicMigration]
  ) {
    private[migration] def add(op: DynamicMigration): Unit =
      ops = op :: ops
  }

  object MigrationBuilder {
    def empty[A]: MigrationBuilder[A] = new MigrationBuilder[A](Nil)
  }

  /** Build a typed Migration[A, B] from a compile-time validated program.
    *
    * IMPORTANT: we capture STRUCTURAL schemas via Schema#structural (PR
    * #614/#589) so the migration aligns with Issue #519.
    */
  inline def migration[A, B](
      inline f: MigrationBuilder[A] ?=> Unit
  )(using sa: Schema[A], sb: Schema[B]): Migration[A, B] =
    ${ migrationImpl[A, B]('f, 'sa, 'sb) }

  // ----- user-facing ops (only available inside migration { ... }) -----

  object ops {

    /** Rename a field at root or nested, using a selector for validation */
    inline def rename[A, B](inline at: A => B, from: String, to: String)(using
    b: MigrationBuilder[A]
): Unit =
  ${ renameImpl[A, B]('at, 'from, 'to, 'b, 'summon[Schema[A]]) }


    /** Add a field with a default value.
      *
      * NOTE: This stores the default as DynamicValue using Schema[D]. If you
      * want to strictly enforce "literal/inline constant only", add quoted AST
      * checks in addFieldImpl.
      */
    inline def addField[A, B, D](
    inline at: A => B,
    field: String,
    inline default: D
)(using
    s: Schema[D],
    b: MigrationBuilder[A]
): Unit =
  ${ addFieldImpl[A, B, D]('at, 'field, 'default, 'b, 'summon[Schema[A]]) }


    inline def deleteField[A, B](inline at: A => B, field: String)(using
    b: MigrationBuilder[A]
): Unit =
  ${ deleteFieldImpl[A, B]('at, 'field, 'b, 'summon[Schema[A]]) }

  }

  // ----- macro impls -----

  private def migrationImpl[A: Type, B: Type](
      f: Expr[MigrationBuilder[A] ?=> Unit],
      sa: Expr[Schema[A]],
      sb: Expr[Schema[B]]
  )(using Quotes): Expr[Migration[A, B]] = {
    '{
      val b = MigrationBuilder.empty[A]
      $f(using b)

      // preserve user order (ops were prepended)
      val program = DynamicMigration.sequence(b.ops.reverse*)

      // capture STRUCTURAL schemas using PR #614/#589
      Migration.fromProgram[A, B](program)(using
        $sa,
        $sb,
        summon[ToStructural[A]],
        summon[ToStructural[B]]
      )

    }
  }

  private def renameImpl[A: Type, B: Type](
    at: Expr[A => B],
    from: Expr[String],
    to: Expr[String],
    b: Expr[MigrationBuilder[A]],
    sa: Expr[Schema[A]]
)(using Quotes): Expr[Unit] = {
  val dyn = extractDynamicOptic[A, B](at, sa)
  '{ $b.add(DynamicMigration.RenameField($dyn, $from, $to)) }
}



  private def addFieldImpl[A: Type, B: Type, D: Type](
    at: Expr[A => B],
    field: Expr[String],
    default: Expr[D],
    b: Expr[MigrationBuilder[A]],
    sa: Expr[Schema[A]]
)(using Quotes): Expr[Unit] = {
  import quotes.reflect.*

  val dyn = extractDynamicOptic[A, B](at, sa)

  val schemaD: Expr[Schema[D]] =
    Expr.summon[Schema[D]].getOrElse {
      report.errorAndAbort(s"No given Schema[${Type.show[D]}] found for addField default")
    }

  val dynDefault: Expr[DynamicValue] = '{ $schemaD.toDynamicValue($default) }

  '{ $b.add(DynamicMigration.AddField($dyn, $field, $dynDefault)) }
}



  private def deleteFieldImpl[A: Type, B: Type](
    at: Expr[A => B],
    field: Expr[String],
    b: Expr[MigrationBuilder[A]],
    sa: Expr[Schema[A]]
)(using Quotes): Expr[Unit] = {
  val dyn = extractDynamicOptic[A, B](at, sa)
  '{ $b.add(DynamicMigration.DeleteField($dyn, $field)) }
}



  /** Extract Path from selector like: _.person.address.street Produces:
    * Path.root / "person" / "address" / "street"
    *
    * Validates that it's ONLY field-selection (no arbitrary code).
    */
  private def extractDynamicOptic[A: Type, B: Type](
      sel: Expr[A => B],
      sa: Expr[Schema[A]]
  )(using Quotes): Expr[DynamicOptic] =
    '{
      // optic(...) is a macro that validates selector syntax like .each / .when[...] etc
      val o: ZOptic[A, B] =
        optic[A, B]($sel)(using $sa).asInstanceOf[ZOptic[A, B]]

      // all Optic types can be converted to DynamicOptic via .toDynamic
      o.toDynamic
    }

  // ─────────────────────────────────────────────
  // structural-schema helpers (Issue #519 + PR #614/#589)
  // ─────────────────────────────────────────────

  /** Force structural schemas (PR #614/#589 adds Schema#structural). */
  inline def structuralSchemaOf[A](using
      s: Schema[A],
      ts: ToStructural[A]
  ): Schema[ts.StructuralType] =
    s.structural

  /** DSL entry point that always stores STRUCTURAL schemas. */
  inline def migrationS[A, B](inline f: MigrationBuilder[A] ?=> Unit)(using
      sa: Schema[A],
      sb: Schema[B]
  ): Migration[A, B] =
    migration[A, B](f)

  // ─────────────────────────────────────────────
  // derive + copy structural shape helper
  // ─────────────────────────────────────────────

  /** Prints `{ def ... }` so users can copy the old structural shape. */
  inline def showStructuralShape[A]: String =
    ${ showStructuralShapeImpl[A] }

  private def showStructuralShapeImpl[A: Type](using Quotes): Expr[String] = {
    import quotes.reflect.*

    val tpe = TypeRepr.of[A].dealias
    val sym = tpe.typeSymbol

    val fields =
      if sym.isClassDef then
        sym.primaryConstructor.paramSymss.flatten.map { p =>
          val name = p.name
          val ptpe = tpe.memberType(p).show
          s"def $name: $ptpe"
        }
      else Nil

    Expr(fields.mkString("{ ", "; ", " }"))
  }
}
