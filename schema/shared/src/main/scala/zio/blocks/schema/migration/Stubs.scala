package zio.schema

/**
 * STUB FILE: Mocks for external ZIO Schema dependencies.
 * This allows us to compile the migration engine in isolation 
 * without fighting with sbt configurations initially.
 */

// 1. DynamicValue Stub (আগে ছিল)
trait DynamicValue
object DynamicValue {
  case class Primitive[A](value: A) extends DynamicValue
  case object Error extends DynamicValue
}

// 2. Schema Stub (নতুন যোগ করা হলো - এই মিসিং পিসটার জন্যই এরর আসছিল)
trait Schema[A]

object Schema {
  // টেস্টের জন্য ডামি স্কিমা ক্রিয়েটর
  def structural[A]: Schema[A] = new Schema[A] {}
}