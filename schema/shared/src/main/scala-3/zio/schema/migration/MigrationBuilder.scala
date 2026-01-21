package zio.schema.migration

import zio.blocks.schema.{Schema, SchemaExpr}

class MigrationBuilder[A, B](
  val sourceSchema: Schema[A],
  val targetSchema: Schema[B],
  val actions: Vector[MigrationAction]
) {

  def withAction(action: MigrationAction): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ action)

  def build: Migration[A, B] =
    Migration(DynamicMigration(actions), sourceSchema, targetSchema)

  inline def addField(inline target: B => Any, inline default: SchemaExpr[A, ?]): MigrationBuilder[A, B] =
    ${ MigrationMacros.addFieldImpl[A, B]('this, 'target, 'default) }

  inline def dropField(
    inline source: A => Any,
    inline defaultForReverse: SchemaExpr[B, ?] = SchemaExpr.Literal((), Schema.unit)
  ): MigrationBuilder[A, B] =
    ${ MigrationMacros.dropFieldImpl[A, B]('this, 'source, 'defaultForReverse) }

  inline def renameField(inline from: A => Any, inline to: B => Any): MigrationBuilder[A, B] =
    ${ MigrationMacros.renameFieldImpl[A, B]('this, 'from, 'to) }

  inline def transformValue[S, T](inline path: A => S, inline expr: SchemaExpr[S, T]): MigrationBuilder[A, B] =
    ${ MigrationMacros.transformValueImpl[A, B, S, T]('this, 'path, 'expr) }

  inline def mandate[S](inline path: A => Option[S]): MigrationBuilder[A, B] =
    ${ MigrationMacros.mandateImpl[A, B, S]('this, 'path) }

  inline def optionalize[S](inline path: A => S): MigrationBuilder[A, B] =
    ${ MigrationMacros.optionalizeImpl[A, B, S]('this, 'path) }

  inline def transformElements[S, T](inline path: A => Seq[S], migration: Migration[S, T]): MigrationBuilder[A, B] =
    ${ MigrationMacros.transformElementsImpl[A, B, S, T]('this, 'path, 'migration) }

  inline def transformKeys[K, V, K2](inline path: A => Map[K, V], migration: Migration[K, K2]): MigrationBuilder[A, B] =
    ${ MigrationMacros.transformKeysImpl[A, B, K, V, K2]('this, 'path, 'migration) }

  inline def transformValues[K, V, V2](
    inline path: A => Map[K, V],
    migration: Migration[V, V2]
  ): MigrationBuilder[A, B] =
    ${ MigrationMacros.transformValuesImpl[A, B, K, V, V2]('this, 'path, 'migration) }

  inline def transformCase[S, T](inline path: A => S, migration: Migration[S, T]): MigrationBuilder[A, B] =
    ${ MigrationMacros.transformCaseImpl[A, B, S, T]('this, 'path, 'migration) }

  inline def renameCase(inline path: A => Any, from: String, to: String): MigrationBuilder[A, B] =
    ${ MigrationMacros.renameCaseImpl[A, B]('this, 'path, 'from, 'to) }

  inline def changeType[S](inline path: A => S, inline converter: SchemaExpr[S, ?]): MigrationBuilder[A, B] =
    ${ MigrationMacros.changeTypeImpl[A, B, S]('this, 'path, 'converter) }

  inline def join(
    inline at: B => Any,
    inline combiner: SchemaExpr[?, ?],
    inline sources: (A => Any)*
  ): MigrationBuilder[A, B] =
    ${ MigrationMacros.joinImpl[A, B]('this, 'at, 'combiner, 'sources) }

  inline def split(
    inline at: A => Any,
    inline splitter: SchemaExpr[?, ?],
    inline targets: (B => Any)*
  ): MigrationBuilder[A, B] =
    ${ MigrationMacros.splitImpl[A, B]('this, 'at, 'splitter, 'targets) }
}
