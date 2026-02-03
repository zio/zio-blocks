package zio.blocks.schema

import zio.blocks.schema.binding._
import zio.test._

object DynamicSchemaRebindSpec extends SchemaBaseSpec {

  case class Person(name: String, age: Int)
  object Person {
    implicit val schema: Schema[Person] = Schema.derived[Person]
  }

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

  case class Team(name: String, members: List[String])
  object Team {
    implicit val schema: Schema[Team] = Schema.derived[Team]
  }

  case class Config(settings: Map[String, Int])
  object Config {
    implicit val schema: Schema[Config] = Schema.derived[Config]
  }

  def spec: Spec[TestEnvironment, Any] = suite("DynamicSchemaRebindSpec")(
    suite("DynamicSchema.rebind")(
      test("rebinds a simple record schema") {
        val originalSchema = Schema[Person]
        val dynamicSchema  = originalSchema.toDynamicSchema
        val registry       = BindingResolver.defaults.bind(Binding.of[Person])

        val rebound = dynamicSchema.rebind[Person](registry)
        val person  = Person("Alice", 30)
        val dynamic = rebound.toDynamicValue(person)
        val result  = rebound.fromDynamicValue(dynamic)

        assertTrue(result == Right(person))
      },
      test("rebinds a schema with sequence fields") {
        val dynamicSchema = Schema[Team].toDynamicSchema
        val registry      = BindingResolver.defaults.bind(Binding.of[Team])

        val rebound = dynamicSchema.rebind[Team](registry)
        val team    = Team("Alpha", List("Alice", "Bob"))
        val dynamic = rebound.toDynamicValue(team)
        val result  = rebound.fromDynamicValue(dynamic)

        assertTrue(result == Right(team))
      },
      test("rebinds a schema with map fields") {
        val dynamicSchema = Schema[Config].toDynamicSchema
        val registry      = BindingResolver.defaults.bind(Binding.of[Config])

        val rebound = dynamicSchema.rebind[Config](registry)
        val config  = Config(Map("a" -> 1, "b" -> 2))
        val dynamic = rebound.toDynamicValue(config)
        val result  = rebound.fromDynamicValue(dynamic)

        assertTrue(result == Right(config))
      },
      test("rebinds a variant schema") {
        val dynamicSchema = Schema[Animal].toDynamicSchema

        val registry = BindingResolver.defaults
          .bind(Binding.of[Animal])
          .bind(Binding.of[Dog])
          .bind(Binding.of[Cat])

        val rebound     = dynamicSchema.rebind[Animal](registry)
        val dog: Animal = Dog("Buddy")
        val dynamic     = rebound.toDynamicValue(dog)
        val result      = rebound.fromDynamicValue(dynamic)

        assertTrue(result == Right(dog))
      },
      test("throws RebindException when binding is missing") {
        val dynamicSchema = Schema[Person].toDynamicSchema
        val registry      = BindingResolver.defaults

        val thrown = try {
          dynamicSchema.rebind[Person](registry)
          false
        } catch {
          case _: RebindException => true
          case _: Throwable       => false
        }

        assertTrue(thrown)
      },
      test("RebindException contains useful information") {
        val dynamicSchema = Schema[Person].toDynamicSchema
        val registry      = BindingResolver.defaults

        val exception = try {
          dynamicSchema.rebind[Person](registry)
          null
        } catch {
          case e: RebindException => e
          case _: Throwable       => null
        }

        assertTrue(
          exception != null,
          exception.expectedKind == "Record",
          exception.typeId.fullName.contains("Person"),
          exception.getMessage.contains("Person"),
          exception.getMessage.contains("Record")
        )
      },
      test("rebinds primitive schema") {
        val dynamicSchema = Schema[Int].toDynamicSchema
        val registry      = BindingResolver.defaults

        val rebound = dynamicSchema.rebind[Int](registry)
        val dynamic = rebound.toDynamicValue(42)
        val result  = rebound.fromDynamicValue(dynamic)

        assertTrue(result == Right(42))
      },
      test("rebinds dynamic schema") {
        val dynamicSchema = Schema[DynamicValue].toDynamicSchema
        val registry      = BindingResolver.defaults

        val rebound   = dynamicSchema.rebind[DynamicValue](registry)
        val testValue = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val dynamic   = rebound.toDynamicValue(testValue)
        val result    = rebound.fromDynamicValue(dynamic)

        assertTrue(result == Right(testValue))
      },
      test("rebinds wrapper schema") {
        val dynamicSchema  = Schema[UserId].toDynamicSchema
        val wrapperBinding = Binding.Wrapper[UserId, Long](
          wrap = l => UserId(l),
          unwrap = u => u.value
        )
        val registry = BindingResolver.defaults.bind(wrapperBinding)

        val rebound = dynamicSchema.rebind[UserId](registry)
        val userId  = UserId(123L)
        val dynamic = rebound.toDynamicValue(userId)
        val result  = rebound.fromDynamicValue(dynamic)

        assertTrue(result == Right(userId))
      },
      test("rebinds sequence schema") {
        val dynamicSchema = Schema[List[Int]].toDynamicSchema
        val registry      = BindingResolver.defaults

        val rebound = dynamicSchema.rebind[List[Int]](registry)
        val list    = List(1, 2, 3)
        val dynamic = rebound.toDynamicValue(list)
        val result  = rebound.fromDynamicValue(dynamic)

        assertTrue(result == Right(list))
      },
      test("rebinds map schema") {
        val dynamicSchema = Schema[Map[String, Int]].toDynamicSchema
        val registry      = BindingResolver.defaults

        val rebound = dynamicSchema.rebind[Map[String, Int]](registry)
        val map     = Map("a" -> 1, "b" -> 2)
        val dynamic = rebound.toDynamicValue(map)
        val result  = rebound.fromDynamicValue(dynamic)

        assertTrue(result == Right(map))
      },
      test("rebinds nested record schema") {
        case class Inner(value: Int)
        case class Outer(inner: Inner)
        implicit val innerSchema: Schema[Inner] = Schema.derived[Inner]
        implicit val outerSchema: Schema[Outer] = Schema.derived[Outer]

        val dynamicSchema = outerSchema.toDynamicSchema
        val registry      = BindingResolver.defaults
          .bind(Binding.of[Inner])
          .bind(Binding.of[Outer])

        val rebound = dynamicSchema.rebind[Outer](registry)
        val outer   = Outer(Inner(42))
        val dynamic = rebound.toDynamicValue(outer)
        val result  = rebound.fromDynamicValue(dynamic)

        assertTrue(result == Right(outer))
      },
      test("rebinds option schema") {
        val dynamicSchema = Schema[Option[Int]].toDynamicSchema
        val registry      = BindingResolver.defaults
          .bind(Binding.of[Option[Int]])
          .bind(Binding.Record.someInt)
          .bind(Binding.Record.none)

        val rebound  = dynamicSchema.rebind[Option[Int]](registry)
        val someVal  = Some(42)
        val noneVal  = None
        val dynamic1 = rebound.toDynamicValue(someVal)
        val dynamic2 = rebound.toDynamicValue(noneVal)
        val result1  = rebound.fromDynamicValue(dynamic1)
        val result2  = rebound.fromDynamicValue(dynamic2)

        assertTrue(
          result1 == Right(someVal),
          result2 == Right(noneVal)
        )
      },

      test("unified bind method accepts Binding.of result") {
        val binding  = Binding.of[Person]
        val registry = BindingResolver.defaults.bind(binding)
        assertTrue(registry.resolveRecord[Person].isDefined)
      },
      test("throws RebindException when variant binding is missing") {
        val dynamicSchema = Schema[Animal].toDynamicSchema
        val registry      = BindingResolver.defaults
          .bind(Binding.of[Dog])
          .bind(Binding.of[Cat])

        val exception = try {
          dynamicSchema.rebind[Animal](registry)
          null
        } catch {
          case e: RebindException => e
          case _: Throwable       => null
        }

        assertTrue(
          exception != null,
          exception.expectedKind == "Variant",
          exception.typeId.fullName.contains("Animal")
        )
      },

      test("throws RebindException when primitive binding is missing") {
        val dynamicSchema = Schema[Int].toDynamicSchema
        val registry      = BindingResolver.empty

        val exception = try {
          dynamicSchema.rebind[Int](registry)
          null
        } catch {
          case e: RebindException => e
          case _: Throwable       => null
        }

        assertTrue(
          exception != null,
          exception.expectedKind == "Primitive"
        )
      },
      test("throws RebindException when wrapper binding is missing") {
        val dynamicSchema = Schema[UserId].toDynamicSchema
        val registry      = BindingResolver.defaults

        val exception = try {
          dynamicSchema.rebind[UserId](registry)
          null
        } catch {
          case e: RebindException => e
          case _: Throwable       => null
        }

        assertTrue(
          exception != null,
          exception.expectedKind == "Wrapper"
        )
      },
      test("throws RebindException when dynamic binding is missing") {
        val dynamicSchema = Schema[DynamicValue].toDynamicSchema
        val registry      = BindingResolver.empty

        val exception = try {
          dynamicSchema.rebind[DynamicValue](registry)
          null
        } catch {
          case e: RebindException => e
          case _: Throwable       => null
        }

        assertTrue(
          exception != null,
          exception.expectedKind == "Dynamic"
        )
      }
    )
  )
}
