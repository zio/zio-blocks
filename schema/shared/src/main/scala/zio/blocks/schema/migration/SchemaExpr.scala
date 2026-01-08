package zio.blocks.schema.migration

/**
 * Represents a pure, serializable expression for value transformation.
 * Instead of using Scala functions (A => B), we use this ADT.
 * * NOTE: This is currently a placeholder to allow compilation of MigrationAction.
 * We will implement the full expression language (Add, Concat, etc.) in a later phase.
 */
sealed trait SchemaExpr

object SchemaExpr {
  // ভবিষ্যতের জন্য একটি ডিফল্ট ভ্যালু প্লেসহোল্ডার রাখা হলো
  case object DefaultValue extends SchemaExpr
}