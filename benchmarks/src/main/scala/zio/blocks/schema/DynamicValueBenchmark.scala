package zio.blocks.schema

import org.openjdk.jmh.annotations._

class DynamicValueBenchmark extends BaseBenchmark {
  import DynamicValueDomain._

  var a: LinkedList    = Node("A", Node("B", Node("C", Node("D", Node("E", End)))))
  var dv: DynamicValue = schema.toDynamicValue(a)

  @Benchmark
  def fromDynamicValue: Either[SchemaError, LinkedList] = schema.fromDynamicValue(dv)

  @Benchmark
  def toDynamicValue: DynamicValue = schema.toDynamicValue(a)
}

object DynamicValueDomain {
  sealed trait LinkedList

  object End extends LinkedList

  case class Node(value: String, @Modifier.deferred next: LinkedList) extends LinkedList

  implicit val endSchema: Schema[End.type]     = Schema.derived
  implicit lazy val nodeSchema: Schema[Node]   = Schema.derived
  implicit lazy val schema: Schema[LinkedList] = Schema.derived
}
