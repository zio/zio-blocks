package zio.blocks.schema.binding.structural

import zio.blocks.schema._
import zio.blocks.schema.binding._
import zio.test._
import scala.annotation.experimental
import scala.annotation.nowarn
import scala.language.reflectiveCalls

/**
 * Tests for Binding.of derivation for structural types.
 *
 * These tests verify that Binding.of can derive Binding instances for
 * structural types (refinement types), with full constructor/deconstructor
 * support using register-based serialization.
 */
object BindingOfStructuralSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("BindingOfStructuralSpec")(
    flatRecordSuite,
    primitiveTypesSuite,
    nestedRecordSuite,
    containerTypesSuite,
    mixedTypesSuite,
    intersectionTypesSuite,
    variantUnionSuite,
    roundTripSuite,
    edgeCasesSuite,
    structuralReflectionSuite
  )

  private def createRegisters(binding: Binding.Record[?]): Registers =
    Registers(binding.constructor.usedRegisters)

  lazy val flatRecordSuite: Spec[Any, Nothing] = suite("Flat Record")(
    test("derives binding for simple two-field structural type") {
      val binding = Binding.of[{ def name: String; def age: Int }]
      assertTrue(binding.isInstanceOf[Binding.Record[?]])
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

      @nowarn("msg=unused") val person: Person = new { def name = "Bob"; def age = 25 }

      val registers = createRegisters(record)
      val offset    = 0L
      record.deconstructor.deconstruct(registers, offset, person)

      val age  = registers.getInt(RegisterOffset(ints = 0))
      val name = registers.getObject(RegisterOffset(objects = 0)).asInstanceOf[String]

      assertTrue(name == "Bob", age == 25)
    },
    test("usedRegisters is non-zero") {
      val binding = Binding.of[{ def name: String; def age: Int }]
      val record  = binding.asInstanceOf[Binding.Record[?]]

      val used = record.constructor.usedRegisters
      assertTrue(used != 0L)
    }
  )

  lazy val primitiveTypesSuite: Spec[Any, Nothing] = suite("Primitive Types")(
    test("Boolean field binding") {
      val binding = Binding.of[{ def active: Boolean }]
      assertTrue(binding.isInstanceOf[Binding.Record[?]])
    },
    test("Byte field binding") {
      val binding = Binding.of[{ def value: Byte }]
      assertTrue(binding.isInstanceOf[Binding.Record[?]])
    },
    test("Short field binding") {
      val binding = Binding.of[{ def value: Short }]
      assertTrue(binding.isInstanceOf[Binding.Record[?]])
    },
    test("Long field binding") {
      val binding = Binding.of[{ def value: Long }]
      assertTrue(binding.isInstanceOf[Binding.Record[?]])
    },
    test("Float field binding") {
      val binding = Binding.of[{ def value: Float }]
      assertTrue(binding.isInstanceOf[Binding.Record[?]])
    },
    test("Double field binding") {
      val binding = Binding.of[{ def value: Double }]
      assertTrue(binding.isInstanceOf[Binding.Record[?]])
    },
    test("Char field binding") {
      val binding = Binding.of[{ def value: Char }]
      assertTrue(binding.isInstanceOf[Binding.Record[?]])
    },
    test("String field binding") {
      val binding = Binding.of[{ def value: String }]
      assertTrue(binding.isInstanceOf[Binding.Record[?]])
    },
    test("Int field binding") {
      val binding = Binding.of[{ def value: Int }]
      assertTrue(binding.isInstanceOf[Binding.Record[?]])
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
      assertTrue(binding.isInstanceOf[Binding.Record[?]])
    },
    test("deconstructor handles nested object types") {
      type Inner = { def x: Int }
      type Outer = { def inner: Inner; def name: String }

      val binding = Binding.of[Outer]
      val record  = binding.asInstanceOf[Binding.Record[Outer]]

      @nowarn("msg=unused") val innerInstance: Inner = new { def x = 42 }
      @nowarn("msg=unused") val outerInstance: Outer = new { def inner = innerInstance; def name = "test" }

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
      assertTrue(binding.isInstanceOf[Binding.Record[?]])
    },
    test("List field binding") {
      type WithList = { def items: List[String] }
      val binding = Binding.of[WithList]
      assertTrue(binding.isInstanceOf[Binding.Record[?]])
    },
    test("Vector field binding") {
      type WithVector = { def items: Vector[Int] }
      val binding = Binding.of[WithVector]
      assertTrue(binding.isInstanceOf[Binding.Record[?]])
    },
    test("Set field binding") {
      type WithSet = { def items: Set[String] }
      val binding = Binding.of[WithSet]
      assertTrue(binding.isInstanceOf[Binding.Record[?]])
    },
    test("Map field binding") {
      type WithMap = { def data: Map[String, Int] }
      val binding = Binding.of[WithMap]
      assertTrue(binding.isInstanceOf[Binding.Record[?]])
    },
    test("Either field binding") {
      type WithEither = { def result: Either[String, Int] }
      val binding = Binding.of[WithEither]
      assertTrue(binding.isInstanceOf[Binding.Record[?]])
    },
    test("deconstruct container fields") {
      type WithContainers = { def items: List[Int]; def opt: Option[String] }
      val binding = Binding.of[WithContainers]
      val record  = binding.asInstanceOf[Binding.Record[WithContainers]]

      @nowarn("msg=unused") val obj: WithContainers = new { def items = List(1, 2, 3); def opt = Some("hello") }

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
      assertTrue(binding.isInstanceOf[Binding.Record[?]])

      val record = binding.asInstanceOf[Binding.Record[Outer]]

      @nowarn("msg=unused") val obj: Outer = new { def inner = Inner(10, 20); def name = "test" }

      val registers = createRegisters(record)
      record.deconstructor.deconstruct(registers, 0L, obj)

      val inner = registers.getObject(0L).asInstanceOf[Inner]
      val name  = registers.getObject(RegisterOffset(objects = 1)).asInstanceOf[String]

      assertTrue(inner == Inner(10, 20), name == "test")
    }
  )

  lazy val intersectionTypesSuite: Spec[Any, Nothing] = suite("Intersection Types (Scala 3)")(
    test("derives binding for intersection of structural types") {
      type HasName = { def name: String }
      type HasAge  = { def age: Int }
      type Person  = HasName & HasAge

      val binding = Binding.of[Person]
      assertTrue(binding.isInstanceOf[Binding.Record[?]])
    },
    test("deconstruct intersection type") {
      type HasName = { def name: String }
      type HasAge  = { def age: Int }
      type Person  = HasName & HasAge

      val binding = Binding.of[Person]
      val record  = binding.asInstanceOf[Binding.Record[Person]]

      @nowarn("msg=unused") val obj: Person = new { def name = "Alice"; def age = 30 }

      val registers = createRegisters(record)
      record.deconstructor.deconstruct(registers, 0L, obj)

      // Fields are sorted alphabetically: age (Int), name (String/Object)
      val age  = registers.getInt(RegisterOffset(ints = 0))
      val name = registers.getObject(RegisterOffset(objects = 0)).asInstanceOf[String]

      assertTrue(name == "Alice", age == 30)
    }
  )

  lazy val variantUnionSuite: Spec[Any, Nothing] = suite("Variant / Union Types (Scala 3)")(
    test("derives variant binding for union of structural types") {
      type Dog = { def bark: String }
      type Cat = { def meow: String }
      type Pet = Dog | Cat

      val binding = Binding.of[Pet]
      assertTrue(binding.isInstanceOf[Binding.Variant[?]])
    },
    test("variant discriminator identifies correct type") {
      type Dog = { def bark: String }
      type Cat = { def meow: String }
      type Pet = Dog | Cat

      val binding = Binding.of[Pet]
      val variant = binding.asInstanceOf[Binding.Variant[Pet]]

      @nowarn("msg=unused") val dog: Pet = new { def bark = "woof" }
      @nowarn("msg=unused") val cat: Pet = new { def meow = "meow" }

      val dogIdx = variant.discriminator.discriminate(dog)
      val catIdx = variant.discriminator.discriminate(cat)

      assertTrue(dogIdx != catIdx, dogIdx >= 0, catIdx >= 0)
    },
    test("variant matchers downcast correctly") {
      type Dog = { def bark: String }
      type Cat = { def meow: String }
      type Pet = Dog | Cat

      val binding = Binding.of[Pet]
      val variant = binding.asInstanceOf[Binding.Variant[Pet]]

      @nowarn("msg=unused") val dog: Pet = new { def bark = "woof" }
      @nowarn("msg=unused") val cat: Pet = new { def meow = "meow" }

      val dogMatcher = variant.matchers(0)
      val catMatcher = variant.matchers(1)

      val dogFromDog = dogMatcher.downcastOrNull(dog)
      val catFromCat = catMatcher.downcastOrNull(cat)
      val dogFromCat = dogMatcher.downcastOrNull(cat)
      val catFromDog = catMatcher.downcastOrNull(dog)

      assertTrue(
        dogFromDog != null,
        catFromCat != null,
        dogFromCat == null,
        catFromDog == null
      )
    },
    test("three-way union discrimination") {
      type A   = { def a: Int }
      type B   = { def b: String }
      type C   = { def c: Boolean }
      type ABC = A | B | C

      val binding = Binding.of[ABC]
      val variant = binding.asInstanceOf[Binding.Variant[ABC]]

      @nowarn("msg=unused") val instA: ABC = new { def a = 1 }
      @nowarn("msg=unused") val instB: ABC = new { def b = "hello" }
      @nowarn("msg=unused") val instC: ABC = new { def c = true }

      val idxA = variant.discriminator.discriminate(instA)
      val idxB = variant.discriminator.discriminate(instB)
      val idxC = variant.discriminator.discriminate(instC)

      assertTrue(
        Set(idxA, idxB, idxC).size == 3,
        idxA >= 0,
        idxB >= 0,
        idxC >= 0
      )
    }
  )

  lazy val roundTripSuite: Spec[Any, Nothing] = suite("Round-Trip")(
    test("deconstruct user-created structural instance") {
      type Person = { def name: String; def age: Int }
      val binding = Binding.of[Person]
      val record  = binding.asInstanceOf[Binding.Record[Person]]

      @nowarn("msg=unused") val original: Person = new { def name = "Diana"; def age = 28 }

      val registers = createRegisters(record)
      record.deconstructor.deconstruct(registers, 0L, original)

      val age  = registers.getInt(0L)
      val name = registers.getObject(RegisterOffset(ints = 1)).asInstanceOf[String]

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

      @nowarn("msg=unused") val person: Person = new { def name = "Eve"; def age = 40 }
      val registers                            = createRegisters(record)
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
