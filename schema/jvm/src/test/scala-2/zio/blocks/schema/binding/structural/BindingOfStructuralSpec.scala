package zio.blocks.schema.binding.structural

import zio.blocks.schema._
import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset
import zio.test._
import scala.language.reflectiveCalls

/**
 * Tests for Binding.of derivation for structural types (Scala 2 version).
 *
 * Scala 2 only supports flat/nested structural records. Union and intersection
 * types are not supported.
 */
object BindingOfStructuralSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("BindingOfStructuralSpec")(
    flatRecordSuite,
    primitiveTypesSuite,
    nestedRecordSuite,
    containerTypesSuite,
    deconstructorSuite,
    structuralReflectionSuite
  )

  private def createRegisters(binding: Binding.Record[_]): Registers =
    Registers(binding.constructor.usedRegisters)

  lazy val flatRecordSuite: Spec[Any, Nothing] = suite("Flat Record")(
    test("derives binding for simple two-field structural type") {
      val binding = Binding.of[{ def name: String; def age: Int }]
      assertTrue(binding.isInstanceOf[Binding.Record[_]])
    },
    test("usedRegisters is non-zero") {
      val binding = Binding.of[{ def name: String; def age: Int }]
      val record  = binding.asInstanceOf[Binding.Record[_]]
      val used    = record.constructor.usedRegisters
      assertTrue(used != 0L)
    },
    test("constructor creates instance with accessible fields via structural type") {
      type Person = { def name: String; def age: Int }
      val binding = Binding.of[Person]
      val record  = binding.asInstanceOf[Binding.Record[Person]]

      val registers = createRegisters(record)
      registers.setInt(0L, 42)
      registers.setObject(RegisterOffset(ints = 1), "TestName")

      val constructed: Person = record.constructor.construct(registers, 0L)

      assertTrue(constructed.name == "TestName", constructed.age == 42)
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
    }
  )

  lazy val nestedRecordSuite: Spec[Any, Nothing] = suite("Nested Record")(
    test("derives binding for nested structural type") {
      type Inner = { def x: Int }
      type Outer = { def inner: Inner; def name: String }

      val binding = Binding.of[Outer]
      assertTrue(binding.isInstanceOf[Binding.Record[_]])
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
    }
  )

  lazy val deconstructorSuite: Spec[Any, Nothing] = suite("Deconstructor")(
    test("deconstructor extracts values correctly") {
      type Person = { def name: String; def age: Int }
      val binding = Binding.of[Person]
      val record  = binding.asInstanceOf[Binding.Record[Person]]

      val person: Person = new { def name = "Bob"; def age = 25 }

      val registers = createRegisters(record)
      record.deconstructor.deconstruct(registers, 0L, person)

      val age  = registers.getInt(0L)
      val name = registers.getObject(RegisterOffset(ints = 1)).asInstanceOf[String]

      assertTrue(name == "Bob", age == 25)
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

  lazy val structuralReflectionSuite: Spec[Any, Nothing] = suite("StructuralReflection")(
    test("get retrieves member value") {
      val obj: { def name: String } = new { def name = "Test" }
      val result = StructuralReflection.get(obj.asInstanceOf[AnyRef], "name")
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
      val result = StructuralReflection.hasAll(obj.asInstanceOf[AnyRef], Array("a", "b"))
      assertTrue(result)
    },
    test("hasAll returns false when member is missing") {
      val obj: { def a: Int } = new { def a = 1 }
      val result = StructuralReflection.hasAll(obj.asInstanceOf[AnyRef], Array("a", "b"))
      assertTrue(!result)
    },
    test("hasAll returns false for null") {
      val result = StructuralReflection.hasAll(null, Array("a"))
      assertTrue(!result)
    }
  )
}
