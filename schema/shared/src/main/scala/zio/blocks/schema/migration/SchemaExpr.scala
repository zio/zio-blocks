package zio.blocks.schema.migration

import zio.schema.DynamicValue

/**
 * Represents a pure, serializable expression for value transformation.
 * Updated to support Constants (for AddField) and DefaultValue (for DropField).
 */
sealed trait SchemaExpr

object SchemaExpr {
  // ১. যখন আমরা ফিল্ড ড্রপ করি, তখন রিভার্স করার জন্য ডিফল্ট ভ্যালু লাগে
  case object DefaultValue extends SchemaExpr

  // ২. যখন আমরা নতুন ফিল্ড অ্যাড করি, তখন একটা ফিক্সড ভ্যালু দিতে হয় (যেমন: 0 বা "")
  final case class Constant(value: DynamicValue) extends SchemaExpr
}