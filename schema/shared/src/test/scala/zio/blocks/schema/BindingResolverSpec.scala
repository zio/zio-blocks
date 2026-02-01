package zio.blocks.schema

import zio.blocks.schema.binding._
import zio.test._

object BindingResolverSpec extends SchemaBaseSpec {

  case class Person(name: String, age: Int)
  object Person {
    implicit val schema: Schema[Person] = Schema.derived[Person]
  }

  case class SimpleRecord(x: Int, y: String)

  sealed trait Animal
  case class Dog(name: String) extends Animal
  case class Cat(name: String) extends Animal
  object Dog {
    implicit val schema: Schema[Dog] = Schema.derived[Dog]
  }
  object Cat {
    implicit val schema: Schema[Cat] = Schema.derived[Cat]
  }
  object Animal {
    implicit val schema: Schema[Animal] = Schema.derived[Animal]
  }

  case class UserId(value: Long)
  object UserId {
    implicit val schema: Schema[UserId] = Schema[Long].transform((l: Long) => UserId(l), (u: UserId) => u.value)
  }

  def spec: Spec[TestEnvironment, Any] = suite("BindingResolverSpec")(
    suite("BindingResolver.empty")(
      test("creates an empty registry") {
        val reg = BindingResolver.empty
        assertTrue(
          reg.isEmpty,
          reg.size == 0
        )
      }
    ),
    suite("BindingResolver.defaults")(
      test("contains primitive bindings") {
        val reg = BindingResolver.defaults
        assertTrue(
          reg.resolvePrimitive[Int].isDefined,
          reg.resolvePrimitive[String].isDefined,
          reg.resolvePrimitive[Boolean].isDefined
        )
      },
      test("contains sequence bindings") {
        val reg = BindingResolver.defaults
        assertTrue(
          reg.resolveSeq[List[Int]].isDefined,
          reg.resolveSeq[Vector[String]].isDefined
        )
      },
      test("contains map bindings") {
        val reg = BindingResolver.defaults
        assertTrue(reg.resolveMap[Map[String, Int]].isDefined)
      },
      test("contains dynamic binding") {
        val reg = BindingResolver.defaults
        assertTrue(reg.resolveDynamic.isDefined)
      }
    ),
    suite("BindingResolver.++")(
      test("left resolver takes precedence") {
        val leftBinding  = Binding.Primitive.int
        val left         = BindingResolver.empty.bind(leftBinding)
        val rightBinding = Binding.Primitive.int
        val right        = BindingResolver.empty.bind(rightBinding)
        val combined     = left ++ right

        val resolved = combined.resolvePrimitive[Int]
        assertTrue(
          resolved.isDefined,
          resolved.get eq leftBinding
        )
      },
      test("falls back to right resolver when left has no binding") {
        val left  = BindingResolver.empty
        val right = BindingResolver.defaults

        val combined = left ++ right
        assertTrue(combined.resolvePrimitive[Int].isDefined)
      },
      test("combines record bindings") {
        val personBinding = Schema[Person].reflect.binding.asInstanceOf[Binding.Record[Person]]
        val left          = BindingResolver.empty.bind(personBinding)
        val right         = BindingResolver.defaults

        val combined = left ++ right
        assertTrue(
          combined.resolveRecord[Person].isDefined,
          combined.resolvePrimitive[Int].isDefined
        )
      },
      test("combines variant bindings") {
        val animalBinding = Schema[Animal].reflect.binding.asInstanceOf[Binding.Variant[Animal]]
        val left          = BindingResolver.empty.bind(animalBinding)
        val right         = BindingResolver.defaults

        val combined = left ++ right
        assertTrue(
          combined.resolveVariant[Animal].isDefined,
          combined.resolvePrimitive[Int].isDefined
        )
      },
      test("combines wrapper bindings") {
        val wrapperBinding = Binding.Wrapper[UserId, Long](
          wrap = l => scala.Right(UserId(l)),
          unwrap = u => scala.Right(u.value)
        )
        val left  = BindingResolver.empty.bind(wrapperBinding)
        val right = BindingResolver.defaults

        val combined = left ++ right
        assertTrue(
          combined.resolveWrapper[UserId].isDefined,
          combined.resolvePrimitive[Long].isDefined
        )
      },
      test("combines sequence bindings") {
        val left  = BindingResolver.empty.bind[List](Binding.Seq.list[Nothing])
        val right = BindingResolver.empty.bind[Vector](Binding.Seq.vector[Nothing])

        val combined = left ++ right
        assertTrue(
          combined.resolveSeq[List[Int]].isDefined,
          combined.resolveSeq[Vector[String]].isDefined
        )
      },
      test("combines map bindings") {
        val left  = BindingResolver.empty.bind[Map](Binding.Map.map[Nothing, Nothing])
        val right = BindingResolver.defaults

        val combined = left ++ right
        assertTrue(combined.resolveMap[Map[String, Int]].isDefined)
      },
      test("combines dynamic bindings") {
        val left  = BindingResolver.empty.bind(Binding.Dynamic())
        val right = BindingResolver.empty

        val combined = left ++ right
        assertTrue(combined.resolveDynamic.isDefined)
      },
      test("toString shows combination") {
        val left     = BindingResolver.empty
        val right    = BindingResolver.defaults
        val combined = left ++ right
        assertTrue(combined.toString.contains("++"))
      },
      test("is associative for resolution") {
        val a = BindingResolver.empty.bind(Binding.Primitive.int)
        val b = BindingResolver.empty.bind(Binding.Primitive.string)
        val c = BindingResolver.defaults

        val leftAssoc  = (a ++ b) ++ c
        val rightAssoc = a ++ (b ++ c)

        assertTrue(
          leftAssoc.resolvePrimitive[Int].isDefined,
          rightAssoc.resolvePrimitive[Int].isDefined,
          leftAssoc.resolvePrimitive[String].isDefined,
          rightAssoc.resolvePrimitive[String].isDefined
        )
      }
    ),
    suite("BindingResolver.reflection")(
      test("returns None for primitives") {
        val resolver = BindingResolver.reflection
        assertTrue(resolver.resolvePrimitive[Int].isEmpty)
      },
      test("returns None for variants") {
        val resolver = BindingResolver.reflection
        assertTrue(resolver.resolveVariant[Animal].isEmpty)
      },
      test("returns None for sequences") {
        val resolver = BindingResolver.reflection
        assertTrue(resolver.resolveSeq[List[Int]].isEmpty)
      },
      test("returns None for maps") {
        val resolver = BindingResolver.reflection
        assertTrue(resolver.resolveMap[Map[String, Int]].isEmpty)
      },
      test("returns None for wrappers") {
        val resolver = BindingResolver.reflection
        assertTrue(resolver.resolveWrapper[UserId].isEmpty)
      },
      test("returns None for dynamic") {
        val resolver = BindingResolver.reflection
        assertTrue(resolver.resolveDynamic.isEmpty)
      },
      test("toString shows Reflection") {
        assertTrue(BindingResolver.reflection.toString == "BindingResolver.Reflection")
      }
    ),
    suite("combined resolver usage")(
      test("custom registry overrides defaults") {
        val customInt = Binding.Primitive[Int]()
        val custom    = BindingResolver.empty.bind(customInt)
        val combined  = custom ++ BindingResolver.defaults

        val resolved = combined.resolvePrimitive[Int]
        assertTrue(
          resolved.isDefined,
          resolved.get eq customInt
        )
      },
      test("reflection with defaults for rebind") {
        val resolver = BindingResolver.reflection ++ BindingResolver.defaults

        assertTrue(
          resolver.resolvePrimitive[Int].isDefined,
          resolver.resolveSeq[List[Int]].isDefined,
          resolver.resolveMap[Map[String, Int]].isDefined
        )
      }
    ),
    suite("Registry")(
      test("bind returns new registry instance") {
        val reg1 = BindingResolver.empty
        val reg2 = reg1.bind(Binding.Primitive.int)
        assertTrue(
          reg1 ne reg2,
          reg1.isEmpty,
          !reg2.isEmpty
        )
      },
      test("size reflects number of bindings") {
        val reg = BindingResolver.empty
          .bind(Binding.Primitive.int)
          .bind(Binding.Primitive.string)
          .bind(Binding.Primitive.boolean)
        assertTrue(reg.size == 3)
      },
      test("nonEmpty is true when registry has bindings") {
        val reg = BindingResolver.empty.bind(Binding.Primitive.int)
        assertTrue(reg.nonEmpty)
      },
      test("isEmpty is true for empty registry") {
        assertTrue(BindingResolver.empty.isEmpty)
      }
    )
  )
}
