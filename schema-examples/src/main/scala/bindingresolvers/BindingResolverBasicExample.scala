package bindingresolvers

import zio.blocks.schema._
import zio.blocks.schema.binding._
import util.ShowExpr.show

// Builds a registry manually and resolves bindings from it.
object BindingResolverBasicExample extends App {

  case class Person(name: String, age: Int)
  object Person {
    implicit val schema: Schema[Person] = Schema.derived[Person]
  }

  sealed trait Status
  case object Active   extends Status
  case object Inactive extends Status
  object Status {
    implicit val schema: Schema[Status] = Schema.derived[Status]
  }

  val registry: BindingResolver.Registry =
    BindingResolver.empty
      .bind(Binding.of[Person])  // Binding.Record[Person]
      .bind(Binding.of[Status])  // Binding.Variant[Status]
      .bind(Binding.of[Int])     // Binding.Primitive[Int]

  show(registry.resolveRecord[Person].isDefined)
  show(registry.resolveVariant[Status].isDefined)
  show(registry.resolvePrimitive[Int].isDefined)
  show(registry.resolvePrimitive[String].isDefined) // not registered — None
  show(registry.size)
}

// Demonstrates how BindingResolver.defaults covers primitives and collections.
object BindingResolverDefaultsExample extends App {

  val defaults = BindingResolver.defaults

  show(defaults.resolvePrimitive[Int].isDefined)
  show(defaults.resolvePrimitive[java.time.Instant].isDefined)
  show(defaults.resolveSeq[List[Int]].isDefined)
  show(defaults.resolveSeq[Vector[String]].isDefined)
  show(defaults.resolveMap[Map[String, Int]].isDefined)
  show(defaults.resolveDynamic.isDefined)
}
