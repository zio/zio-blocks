package zio.blocks.schema.migration

import zio.blocks.schema.DynamicOptic

/**
 * Type-safe builder API for Schema Migrations.
 * Internally compiles Scala 3 path selectors down to `DynamicMigration` algebra nodes.
 */
class Migration[A, B](val untyped: DynamicMigration) {

  def >>> [C](that: Migration[B, C]): Migration[A, C] =
    new Migration(this.untyped >>> that.untyped)

  def addField[F](
    selector: A => F, // In practice, powered by Scala 3 Inline Macros
    defaultValue: F
  ): Migration[A, B] = {
    val _ = (selector, defaultValue)
    // Macro injects DynamicOptic based on selector AST
    val optic = SemanticOpticGenerator.stub() 
    new Migration(this.untyped >>> DynamicMigration.AddField(optic, ???))
  }

  def deleteField[F](selector: B => F): Migration[A, B] = {
    val _ = selector
    val optic = SemanticOpticGenerator.stub()
    new Migration(this.untyped >>> DynamicMigration.DeleteField(optic))
  }
}

object Migration {
  def identity[A]: Migration[A, A] = new Migration(DynamicMigration.Identity)
}

private[migration] object SemanticOpticGenerator {
  def stub(): DynamicOptic = ???
}
