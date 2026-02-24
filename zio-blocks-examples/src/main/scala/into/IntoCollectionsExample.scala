package into

import zio.blocks.schema.Into

// Demonstrates automatic collection reshaping.
// Into composes through List, Vector, Set, Map, and Option,
// applying element-level type coercion at each step.
object IntoCollectionsExample extends App {

  // List → Vector with Int → Long element widening
  println(Into[List[Int], Vector[Long]].into(List(1, 2, 3)))

  // List → Set removes duplicates
  println(Into[List[Int], Set[Long]].into(List(1, 2, 2, 3)))

  // Map with key and value coercion
  println(Into[Map[String, Int], Map[String, Long]].into(Map("a" -> 1, "b" -> 2)))

  // Option element coercion — Some and None both handled
  println(Into[Option[Int], Option[Long]].into(Some(42)))
  println(Into[Option[Int], Option[Long]].into(None))

  // Case class with a collection field — the field conversion is derived automatically
  case class OrderV1(id: String, quantities: List[Int])
  case class OrderV2(id: String, quantities: Vector[Long])

  println(Into.derived[OrderV1, OrderV2].into(OrderV1("order-1", List(5, 10, 3))))
}
