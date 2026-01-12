package zio.blocks.schema.migration

import zio.blocks.schema.{Schema, ToStructural, SchemaExpr}
import zio.blocks.schema.CompanionOptics.optic
import zio.blocks.schema.Optic as ZOptic
import zio.blocks.schema.DynamicOptic

import scala.quoted.*
import zio.blocks.schema.migration.MigrationAction.*

/** Typed DSL that compiles to PURE DynamicMigration data (Vector[MigrationAction]).
  *
  * No closures are stored: macro expands selectors to DynamicOptic and stores SchemaExpr trees.
  */
object MigrationDsl {

  /** Builder used only during macro-expanded program construction. */
  final class MigrationBuilder[A] private[migration] (
      private[migration] var ops: List[MigrationAction]
  ) {
    private[migration] def add(op: MigrationAction): Unit =
      ops = op :: ops
  }

  object MigrationBuilder {
    def empty[A]: MigrationBuilder[A] = new MigrationBuilder[A](Nil)
  }

    inline def buildPartial[A, B](
      inline f: MigrationBuilder[A] ?=> Unit
  )(using sa: Schema[A], sb: Schema[B]): Migration[A, B] =
    ${ migrationImpl[A, B]('f, 'sa, 'sb, validate = Expr(false)) }

  inline def build[A, B](
      inline f: MigrationBuilder[A] ?=> Unit
  )(using sa: Schema[A], sb: Schema[B]): Migration[A, B] =
    ${ migrationImpl[A, B]('f, 'sa, 'sb, validate = Expr(true)) }


  object ops {

    /** OLD-style rename (kept for compatibility): selector + string names. */
    inline def rename[A, B](inline at: A => B, from: String, to: String)(using
      b: MigrationBuilder[A]
    ): Unit =
      ${ renameImpl[A, B]('at, 'from, 'to, 'b, 'summon[Schema[A]]) }

    /** OLD-style addField (kept for compatibility): selector + string field + literal default. */
    inline def addField[A, B, D](
      inline at: A => B,
      field: String,
      inline default: D
    )(using
      s: Schema[D],
      b: MigrationBuilder[A]
    ): Unit =
      ${ addFieldImpl[A, B, D]('at, 'field, 'default, 'b, 'summon[Schema[A]]) }

    /** OLD-style deleteField (kept for compatibility). */
    inline def deleteField[A, B](inline at: A => B, field: String)(using
      b: MigrationBuilder[A]
    ): Unit =
      ${ deleteFieldImpl[A, B]('at, 'field, 'b, 'summon[Schema[A]]) }

    // ─────────────────────────────────────────────
    // NEW #519-style ops (recommended)
    // ─────────────────────────────────────────────

    /** Rename a field using selectors instead of strings (recommended). */
    inline def renameField[A, B](inline fromSel: A => Any, inline toSel: B => Any)(using
      b: MigrationBuilder[A]
    ): Unit =
      ${ renameFieldSelectorsImpl[A, B]('fromSel, 'toSel, 'b, 'summon[Schema[A]], 'summon[Schema[B]]) }

    /** Add a field using a target selector + SchemaExpr default (recommended). */
    inline def addFieldExpr[A, B](
      inline targetSel: B => Any,
      inline defaultExpr: SchemaExpr[Any, Any]
    )(using b: MigrationBuilder[A], sb: Schema[B]): Unit =
      ${ addFieldExprImpl[A, B]('targetSel, 'defaultExpr, 'b, 'sb) }

    /** Drop a field using a source selector (recommended). */
    inline def dropField[A](inline sourceSel: A => Any, inline defaultForReverse: SchemaExpr[Any, Any] = DefaultValueExpr)(using
      b: MigrationBuilder[A],
      sa: Schema[A]
    ): Unit =
      ${ dropFieldImpl[A]('sourceSel, 'defaultForReverse, 'b, 'sa) }
  }

  // ----- macro impls -----

  private def migrationImpl[A: Type, B: Type](
    f: Expr[MigrationBuilder[A] ?=> Unit],
    sa: Expr[Schema[A]],
    sb: Expr[Schema[B]],
    validate: Expr[Boolean]
)(using Quotes): Expr[Migration[A, B]] = {
    '{
      val b = MigrationBuilder.empty[A]
      $f(using b)

      // preserve user order (ops were prepended)
      val program = DynamicMigration(b.ops.reverse.toVector)

      if ($validate) {
        MigrationValidator.validateOrThrow(program, $sa.structural, $sb.structural)
      }

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
    '{ $b.add(RenameField($dyn, $from, $to)) }
  }

  private def addFieldImpl[A: Type, B: Type, D: Type](
    at: Expr[A => B],
    field: Expr[String],
    default: Expr[D],
    b: Expr[MigrationBuilder[A]],
    sa: Expr[Schema[A]]
  )(using Quotes): Expr[Unit] = {
    val dyn = extractDynamicOptic[A, B](at, sa)

    val schemaD: Expr[Schema[D]] =
      Expr.summon[Schema[D]].getOrElse {
        quotes.reflect.report.errorAndAbort(s"No given Schema[${Type.show[D]}] found for addField default")
      }

    // IMPORTANT: store DEFAULT AS SchemaExpr (not DynamicValue)
    val expr: Expr[SchemaExpr[Any, Any]] =
      '{ SchemaExpr.Literal[Any, D]($default, $schemaD).asInstanceOf[SchemaExpr[Any, Any]] }

    '{ $b.add(AddField($dyn, $field, $expr)) }
  }

  private def deleteFieldImpl[A: Type, B: Type](
    at: Expr[A => B],
    field: Expr[String],
    b: Expr[MigrationBuilder[A]],
    sa: Expr[Schema[A]]
  )(using Quotes): Expr[Unit] = {
    val dyn = extractDynamicOptic[A, B](at, sa)
    // reverse default: DefaultValueExpr
    '{ $b.add(DropField($dyn, $field, DefaultValueExpr)) }
  }

  

    private def renameFieldSelectorsImpl[A: Type, B: Type](
    fromSel: Expr[A => Any],
    toSel: Expr[B => Any],
    b: Expr[MigrationBuilder[A]],
    sa: Expr[Schema[A]],
    sb: Expr[Schema[B]]
  )(using Quotes): Expr[Unit] = {
    val fromDyn = extractDynamicOptic[A, Any](fromSel, sa)
    val toDyn   = extractDynamicOptic[B, Any](toSel, sb)

    val fromNameE = lastFieldName(fromDyn, "renameField(fromSel)")
    val toNameE   = lastFieldName(toDyn, "renameField(toSel)")
    val parentDyn = parentOfField(fromDyn, "renameField(fromSel)")

    '{ $b.add(RenameField($parentDyn, $fromNameE, $toNameE)) }
  }

  private def addFieldExprImpl[A: Type, B: Type](
    targetSel: Expr[B => Any],
    defaultExpr: Expr[SchemaExpr[Any, Any]],
    b: Expr[MigrationBuilder[A]],
    sb: Expr[Schema[B]]
  )(using Quotes): Expr[Unit] = {
    val targetDyn = extractDynamicOptic[B, Any](targetSel, sb)
    val nameE     = lastFieldName(targetDyn, "addFieldExpr(targetSel)")
    val parentE   = parentOfField(targetDyn, "addFieldExpr(targetSel)")
    '{ $b.add(AddField($parentE, $nameE, $defaultExpr)) }
  }

  private def dropFieldImpl[A: Type](
    sourceSel: Expr[A => Any],
    defaultForReverse: Expr[SchemaExpr[Any, Any]],
    b: Expr[MigrationBuilder[A]],
    sa: Expr[Schema[A]]
  )(using Quotes): Expr[Unit] = {
    val sourceDyn = extractDynamicOptic[A, Any](sourceSel, sa)
    val nameE     = lastFieldName(sourceDyn, "dropField(sourceSel)")
    val parentE   = parentOfField(sourceDyn, "dropField(sourceSel)")
    '{ $b.add(DropField($parentE, $nameE, $defaultForReverse)) }
  }


  /** Extract DynamicOptic from selector */
  private def extractDynamicOptic[A: Type, B: Type](
      sel: Expr[A => B],
      sa: Expr[Schema[A]]
  )(using Quotes): Expr[DynamicOptic] =
    '{
      val o: ZOptic[A, B] =
        optic[A, B]($sel)(using $sa).asInstanceOf[ZOptic[A, B]]
      o.toDynamic
    }

  // ----- optic node helpers (field name / parent optic) -----

  
  private def lastFieldName(dyn: Expr[DynamicOptic], ctx: String)(using Quotes): Expr[String] =
    '{ MigrationDsl.RuntimeOptic.lastFieldNameOrFail($dyn, ${Expr(ctx)}) }

  private def parentOfField(dyn: Expr[DynamicOptic], ctx: String)(using Quotes): Expr[DynamicOptic] =
    '{ MigrationDsl.RuntimeOptic.dropLastNodeOrFail($dyn, ${Expr(ctx)}) }

  

  // ─────────────────────────────────────────────
  // structural-schema helpers (unchanged)
  // ─────────────────────────────────────────────

  inline def structuralSchemaOf[A](using
      s: Schema[A],
      ts: ToStructural[A]
  ): Schema[ts.StructuralType] =
    s.structural


    // ─────────────────────────────────────────────
  // Runtime DynamicOptic helpers (BEGINNER-FRIENDLY)
  // ─────────────────────────────────────────────
  private[migration] object RuntimeOptic {

    /** Last node must be `.field("name")` for field ops. */
    def lastFieldNameOrFail(d: DynamicOptic, ctx: String): String =
      d.nodes.lastOption match {
        case Some(DynamicOptic.Node.Field(name)) => name
        case Some(other) =>
          throw new IllegalArgumentException(
            s"$ctx: selector must end with a field access, but ended with: $other"
          )
        case None =>
          throw new IllegalArgumentException(s"$ctx: empty optic is not allowed")
      }

    /** Remove last node. Used to get "parent record optic" from a field optic. */
    def dropLastNodeOrFail(d: DynamicOptic, ctx: String): DynamicOptic = {
      if (d.nodes.isEmpty)
        throw new IllegalArgumentException(s"$ctx: empty optic is not allowed")

      val init = d.nodes.dropRight(1)
      rebuildFromNodes(init)
    }

    /** Rebuild a DynamicOptic from nodes using only public constructors. */
    def rebuildFromNodes(nodes: Seq[DynamicOptic.Node]): DynamicOptic = {
      // If your DynamicOptic has a public constructor taking nodes, you can do:
      // DynamicOptic(nodes.toVector)
      //
      // But to be safe, rebuild using root + operations:
      nodes.foldLeft(DynamicOptic.root) { (acc, n) =>
        n match {
          case DynamicOptic.Node.Field(name) => acc.field(name)
          case DynamicOptic.Node.Elements    => acc.elements
          case DynamicOptic.Node.MapKeys     => acc.mapKeys
          case DynamicOptic.Node.MapValues   => acc.mapValues
          case DynamicOptic.Node.Case(name)  => acc.caseOf(name)
          case DynamicOptic.Node.Wrapped     => acc.wrapped
          case DynamicOptic.Node.AtIndex(i)  => acc.atIndex(i)
          case DynamicOptic.Node.AtIndices(ixs) => acc.atIndices(ixs)
          case DynamicOptic.Node.AtMapKey(k) => acc.atMapKey(k)
          case DynamicOptic.Node.AtMapKeys(ks) => acc.atMapKeys(ks)
        }
      }
    }
  }
  
}
