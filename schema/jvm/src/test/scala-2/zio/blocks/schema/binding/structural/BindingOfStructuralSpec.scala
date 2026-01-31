package zio.blocks.schema.binding.structural

import zio.blocks.schema._
import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset
import zio.test._
import scala.language.reflectiveCalls

/**
 * Tests for Binding.of derivation for structural types (Scala 2 version).
 *
 * Scala 2 supports structural records with `with` for intersection types. Union
 * types are not supported (Scala 3 only).
 */
object BindingOfStructuralSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("BindingOfStructuralSpec")(
    flatRecordSuite,
    primitiveTypesSuite,
    nestedRecordSuite,
    containerTypesSuite,
    mixedTypesSuite,
    intersectionTypesSuite,
    roundTripSuite,
    edgeCasesSuite,
    structuralReflectionSuite
  )

  private def createRegisters(binding: Binding.Record[_]): Registers =
    Registers(binding.constructor.usedRegisters)

  lazy val flatRecordSuite: Spec[Any, Nothing] = suite("Flat Record")(
    test("derives binding for simple two-field structural type") {
      val binding = Binding.of[{ def name: String; def age: Int }]
      assertTrue(binding.isInstanceOf[Binding.Record[_]])
    },
    test("constructor creates an instance that can be deconstructed back") {
      type Person = { def name: String; def age: Int }
      val binding = Binding.of[Person]
      val record  = binding.asInstanceOf[Binding.Record[Person]]

      val inputRegisters = createRegisters(record)
      inputRegisters.setInt(RegisterOffset(ints = 0), 30)
      inputRegisters.setObject(RegisterOffset(objects = 0), "Alice")

      val constructed: Person = record.constructor.construct(inputRegisters, 0L)

      val outputRegisters = createRegisters(record)
      record.deconstructor.deconstruct(outputRegisters, 0L, constructed)

      val extractedAge  = outputRegisters.getInt(RegisterOffset(ints = 0))
      val extractedName = outputRegisters.getObject(RegisterOffset(objects = 0)).asInstanceOf[String]

      assertTrue(
        constructed != null,
        extractedName == "Alice",
        extractedAge == 30
      )
    },
    test("deconstructor extracts values correctly") {
      type Person = { def name: String; def age: Int }
      val binding = Binding.of[Person]
      val record  = binding.asInstanceOf[Binding.Record[Person]]

      val person: Person = new { def name = "Bob"; def age = 25 }

      val registers = createRegisters(record)
      val offset    = 0L
      record.deconstructor.deconstruct(registers, offset, person)

      val age  = registers.getInt(RegisterOffset(ints = 0))
      val name = registers.getObject(RegisterOffset(objects = 0)).asInstanceOf[String]

      assertTrue(name == "Bob", age == 25)
    },
    test("usedRegisters is non-zero") {
      val binding = Binding.of[{ def name: String; def age: Int }]
      val record  = binding.asInstanceOf[Binding.Record[_]]

      val used = record.constructor.usedRegisters
      assertTrue(used != 0L)
    }
  )

  lazy val primitiveTypesSuite: Spec[Any, Nothing] = suite("Primitive Types")(
    test("Boolean field binding") {
      val binding = Binding.of[{ def active: Boolean }]
      assertTrue(binding.isInstanceOf[Binding.Record[_]])
    },
    test("Byte field binding") {
      val binding = Binding.of[{ def value: Byte }]
      assertTrue(binding.isInstanceOf[Binding.Record[_]])
    },
    test("Short field binding") {
      val binding = Binding.of[{ def value: Short }]
      assertTrue(binding.isInstanceOf[Binding.Record[_]])
    },
    test("Long field binding") {
      val binding = Binding.of[{ def value: Long }]
      assertTrue(binding.isInstanceOf[Binding.Record[_]])
    },
    test("Float field binding") {
      val binding = Binding.of[{ def value: Float }]
      assertTrue(binding.isInstanceOf[Binding.Record[_]])
    },
    test("Double field binding") {
      val binding = Binding.of[{ def value: Double }]
      assertTrue(binding.isInstanceOf[Binding.Record[_]])
    },
    test("Char field binding") {
      val binding = Binding.of[{ def value: Char }]
      assertTrue(binding.isInstanceOf[Binding.Record[_]])
    },
    test("String field binding") {
      val binding = Binding.of[{ def value: String }]
      assertTrue(binding.isInstanceOf[Binding.Record[_]])
    },
    test("Int field binding") {
      val binding = Binding.of[{ def value: Int }]
      assertTrue(binding.isInstanceOf[Binding.Record[_]])
    },
    test("deconstruct Boolean field") {
      val binding = Binding.of[{ def active: Boolean }]
      val record  = binding.asInstanceOf[Binding.Record[{ def active: Boolean }]]

      val obj: { def active: Boolean } = new { def active = true }

      val registers = createRegisters(record)
      record.deconstructor.deconstruct(registers, 0L, obj)

      assertTrue(registers.getBoolean(0L))
    },
    test("deconstruct Int field") {
      val binding = Binding.of[{ def value: Int }]
      val record  = binding.asInstanceOf[Binding.Record[{ def value: Int }]]

      val obj: { def value: Int } = new { def value = 42 }

      val registers = createRegisters(record)
      record.deconstructor.deconstruct(registers, 0L, obj)

      assertTrue(registers.getInt(0L) == 42)
    },
    test("deconstruct String field") {
      val binding = Binding.of[{ def value: String }]
      val record  = binding.asInstanceOf[Binding.Record[{ def value: String }]]

      val obj: { def value: String } = new { def value = "hello" }

      val registers = createRegisters(record)
      record.deconstructor.deconstruct(registers, 0L, obj)

      assertTrue(registers.getObject(0L).asInstanceOf[String] == "hello")
    }
  )

  lazy val nestedRecordSuite: Spec[Any, Nothing] = suite("Nested Record")(
    test("derives binding for nested structural type") {
      type Inner = { def x: Int }
      type Outer = { def inner: Inner; def name: String }

      val binding = Binding.of[Outer]
      assertTrue(binding.isInstanceOf[Binding.Record[_]])
    },
    test("deconstructor handles nested object types") {
      type Inner = { def x: Int }
      type Outer = { def inner: Inner; def name: String }

      val binding = Binding.of[Outer]
      val record  = binding.asInstanceOf[Binding.Record[Outer]]

      val innerInstance: Inner = new { def x = 42 }
      val outerInstance: Outer = new { def inner = innerInstance; def name = "test" }

      val registers = createRegisters(record)
      record.deconstructor.deconstruct(registers, 0L, outerInstance)

      val retrievedInner = registers.getObject(0L).asInstanceOf[Inner]
      val retrievedName  = registers.getObject(RegisterOffset(objects = 1)).asInstanceOf[String]

      assertTrue(retrievedInner.x == 42, retrievedName == "test")
    }
  )

  lazy val containerTypesSuite: Spec[Any, Nothing] = suite("Container Types")(
    test("Option field binding") {
      type WithOption = { def opt: Option[Int] }
      val binding = Binding.of[WithOption]
      assertTrue(binding.isInstanceOf[Binding.Record[_]])
    },
    test("List field binding") {
      type WithList = { def items: List[String] }
      val binding = Binding.of[WithList]
      assertTrue(binding.isInstanceOf[Binding.Record[_]])
    },
    test("Vector field binding") {
      type WithVector = { def items: Vector[Int] }
      val binding = Binding.of[WithVector]
      assertTrue(binding.isInstanceOf[Binding.Record[_]])
    },
    test("Set field binding") {
      type WithSet = { def items: Set[String] }
      val binding = Binding.of[WithSet]
      assertTrue(binding.isInstanceOf[Binding.Record[_]])
    },
    test("Map field binding") {
      type WithMap = { def data: Map[String, Int] }
      val binding = Binding.of[WithMap]
      assertTrue(binding.isInstanceOf[Binding.Record[_]])
    },
    test("Either field binding") {
      type WithEither = { def result: Either[String, Int] }
      val binding = Binding.of[WithEither]
      assertTrue(binding.isInstanceOf[Binding.Record[_]])
    },
    test("deconstruct container fields") {
      type WithContainers = { def items: List[Int]; def opt: Option[String] }
      val binding = Binding.of[WithContainers]
      val record  = binding.asInstanceOf[Binding.Record[WithContainers]]

      val obj: WithContainers = new { def items = List(1, 2, 3); def opt = Some("hello") }

      val registers = createRegisters(record)
      record.deconstructor.deconstruct(registers, 0L, obj)

      val items = registers.getObject(0L).asInstanceOf[List[Int]]
      val opt   = registers.getObject(RegisterOffset(objects = 1)).asInstanceOf[Option[String]]

      assertTrue(items == List(1, 2, 3), opt == Some("hello"))
    }
  )

  lazy val mixedTypesSuite: Spec[Any, Nothing] = suite("Mixed Types")(
    test("deconstruct structural with case class field") {
      case class Inner(x: Int, y: Int)
      type Outer = { def inner: Inner; def name: String }

      val binding = Binding.of[Outer]
      assertTrue(binding.isInstanceOf[Binding.Record[_]])

      val record = binding.asInstanceOf[Binding.Record[Outer]]

      val obj: Outer = new { def inner = Inner(10, 20); def name = "test" }

      val registers = createRegisters(record)
      record.deconstructor.deconstruct(registers, 0L, obj)

      val inner = registers.getObject(0L).asInstanceOf[Inner]
      val name  = registers.getObject(RegisterOffset(objects = 1)).asInstanceOf[String]

      assertTrue(inner == Inner(10, 20), name == "test")
    }
  )

  // Scala 2 intersection types use `with` instead of `&`
  lazy val intersectionTypesSuite: Spec[Any, Nothing] = suite("Intersection Types (Scala 2 with)")(
    test("derives binding for intersection of structural types") {
      type HasName = { def name: String }
      type HasAge  = { def age: Int }
      type Person  = HasName with HasAge

      val binding = Binding.of[Person]
      assertTrue(binding.isInstanceOf[Binding.Record[_]])
    },
    test("deconstruct intersection type") {
      type HasName = { def name: String }
      type HasAge  = { def age: Int }
      type Person  = HasName with HasAge

      val binding = Binding.of[Person]
      val record  = binding.asInstanceOf[Binding.Record[Person]]

      val obj: Person = new { def name = "Alice"; def age = 30 }

      val registers = createRegisters(record)
      record.deconstructor.deconstruct(registers, 0L, obj)

      // Fields are sorted alphabetically: age (Int), name (String/Object)
      val age  = registers.getInt(RegisterOffset(ints = 0))
      val name = registers.getObject(RegisterOffset(objects = 0)).asInstanceOf[String]

      assertTrue(name == "Alice", age == 30)
    }
  )

  lazy val roundTripSuite: Spec[Any, Nothing] = suite("Round-Trip")(
    test("deconstruct user-created structural instance") {
      type Person = { def name: String; def age: Int }
      val binding = Binding.of[Person]
      val record  = binding.asInstanceOf[Binding.Record[Person]]

      val original: Person = new { def name = "Diana"; def age = 28 }

      val registers = createRegisters(record)
      record.deconstructor.deconstruct(registers, 0L, original)

      val age  = registers.getInt(RegisterOffset(ints = 0))
      val name = registers.getObject(RegisterOffset(objects = 0)).asInstanceOf[String]

      assertTrue(name == "Diana", age == 28)
    },
    test("constructor and deconstructor have matching usedRegisters") {
      type Person = { def name: String; def age: Int }
      val binding = Binding.of[Person]
      val record  = binding.asInstanceOf[Binding.Record[Person]]

      assertTrue(record.constructor.usedRegisters == record.deconstructor.usedRegisters)
    }
  )

  lazy val edgeCasesSuite: Spec[Any, Nothing] = suite("Edge Cases")(
    test("single field structural type binding") {
      val binding = Binding.of[{ def value: Int }]
      val record  = binding.asInstanceOf[Binding.Record[{ def value: Int }]]

      assertTrue(record.constructor.usedRegisters != 0L)
    },
    test("many fields structural type") {
      type ManyFields = {
        def a: Int
        def b: Int
        def c: Int
        def d: Int
        def e: Int
      }
      val binding = Binding.of[ManyFields]
      val record  = binding.asInstanceOf[Binding.Record[ManyFields]]

      assertTrue(record.constructor.usedRegisters != 0L)
    },
    test("deconstructor uses offset parameter") {
      type Person = { def name: String; def age: Int }
      val binding = Binding.of[Person]
      val record  = binding.asInstanceOf[Binding.Record[Person]]

      val baseOffset = RegisterOffset(objects = 5, ints = 3)

      val person: Person = new { def name = "Eve"; def age = 40 }
      val registers      = createRegisters(record)
      record.deconstructor.deconstruct(registers, baseOffset, person)

      val age  = registers.getInt(RegisterOffset.add(baseOffset, 0L))
      val name = registers.getObject(RegisterOffset.add(baseOffset, RegisterOffset(ints = 1))).asInstanceOf[String]

      assertTrue(name == "Eve", age == 40)
    }
  )

  lazy val structuralReflectionSuite: Spec[Any, Nothing] = suite("StructuralReflection")(
    test("get retrieves member value") {
      val obj: { def name: String } = new { def name = "Test" }
      val result                    = StructuralReflection.get(obj.asInstanceOf[AnyRef], "name")
      assertTrue(result == "Test")
    },
    test("get caches method lookup") {
      val obj1: { def value: Int } = new { def value = 1 }
      val obj2: { def value: Int } = new { def value = 2 }

      val r1 = StructuralReflection.get(obj1.asInstanceOf[AnyRef], "value")
      val r2 = StructuralReflection.get(obj2.asInstanceOf[AnyRef], "value")

      assertTrue(r1 == Integer.valueOf(1), r2 == Integer.valueOf(2))
    },
    test("hasAll returns true when all members exist") {
      val obj: { def a: Int; def b: String } = new { def a = 1; def b = "x" }
      val result                             = StructuralReflection.hasAll(obj.asInstanceOf[AnyRef], Array("a", "b"))
      assertTrue(result)
    },
    test("hasAll returns false when member is missing") {
      val obj: { def a: Int } = new { def a = 1 }
      val result              = StructuralReflection.hasAll(obj.asInstanceOf[AnyRef], Array("a", "b"))
      assertTrue(!result)
    },
    test("hasAll returns false for null") {
      val result = StructuralReflection.hasAll(null, Array("a"))
      assertTrue(!result)
    }
  )
}
