package zio.schema

import scala.collection.immutable.ListMap

/**
 * Enhanced Stubs to simulate ZIO Schema's structure.
 * UPDATED: Added 'Schema' trait to fix compilation errors in MigrationBuilder.
 */
// 🔥 নতুন যোগ করা অংশ: স্কিমা ডেফিনিশন
trait Schema[A]

object Schema {
  // প্লেসহোল্ডার ফ্যাক্টরি (যাতে ভবিষ্যতে দরকার হলে ব্যবহার করা যায়)
  def apply[A]: Schema[A] = new Schema[A] {}
}

// --- আগের DynamicValue কোড ---
sealed trait DynamicValue

object DynamicValue {
  // প্রিমিটিভ ভ্যালু (String, Int, Boolean etc.)
  final case class Primitive[A](value: A) extends DynamicValue
  
  // রেকর্ড বা অবজেক্ট (Field Name -> Value)
  final case class Record(values: ListMap[String, DynamicValue]) extends DynamicValue
  
  // নাল বা অপশনাল ভ্যালুর জন্য
  case object NoneValue extends DynamicValue
  final case class SomeValue(value: DynamicValue) extends DynamicValue
  
  // এরর হ্যান্ডলিং
  final case class Error(message: String) extends DynamicValue
}