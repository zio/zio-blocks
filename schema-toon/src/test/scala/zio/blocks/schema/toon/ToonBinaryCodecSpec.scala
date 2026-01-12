package zio.blocks.schema.toon

import zio.blocks.schema._
import zio.test._

object ToonBinaryCodecSpec extends ZIOSpecDefault {
  // Simple case classes for testing
  case class Person(name: String, age: Int)

  object Person {
    implicit val schema: Schema[Person] = Schema.derived
  }

  case class Address(street: String, city: String)

  object Address {
    implicit val schema: Schema[Address] = Schema.derived
  }

  case class PersonWithAddress(name: String, age: Int, address: Address)

  object PersonWithAddress {
    implicit val schema: Schema[PersonWithAddress] = Schema.derived
  }

  // Record with array field
  case class Team(name: String, members: List[String])

  object Team {
    implicit val schema: Schema[Team] = Schema.derived
  }

  // Record with nested array of records
  case class Company(name: String, employees: List[Person])

  object Company {
    implicit val schema: Schema[Company] = Schema.derived
  }

  // Sealed trait for ADT testing
  sealed trait Shape

  object Shape {
    implicit val schema: Schema[Shape] = Schema.derived
  }

  case class Circle(radius: Double) extends Shape

  object Circle {
    implicit val schema: Schema[Circle] = Schema.derived
  }

  case class Rectangle(width: Double, height: Double) extends Shape

  object Rectangle {
    implicit val schema: Schema[Rectangle] = Schema.derived
  }

  case object Point extends Shape {
    implicit val schema: Schema[Point.type] = Schema.derived
  }

  // Record containing a sealed trait
  case class Drawing(title: String, shape: Shape)

  object Drawing {
    implicit val schema: Schema[Drawing] = Schema.derived
  }

  override def spec: Spec[TestEnvironment, Any] = suite("ToonBinaryCodec")(
    suite("Primitive encoding")(
      test("encodes unit") {
        val encoded = ToonBinaryCodec.unitCodec.encodeToString(())
        assertTrue(encoded == "null")
      },
      test("encodes boolean true") {
        val encoded = ToonBinaryCodec.booleanCodec.encodeToString(true)
        assertTrue(encoded == "true")
      },
      test("encodes boolean false") {
        val encoded = ToonBinaryCodec.booleanCodec.encodeToString(false)
        assertTrue(encoded == "false")
      },
      test("encodes byte") {
        val encoded = ToonBinaryCodec.byteCodec.encodeToString(127.toByte)
        assertTrue(encoded == "127")
      },
      test("encodes negative byte") {
        val encoded = ToonBinaryCodec.byteCodec.encodeToString((-128).toByte)
        assertTrue(encoded == "-128")
      },
      test("encodes short") {
        val encoded = ToonBinaryCodec.shortCodec.encodeToString(32767.toShort)
        assertTrue(encoded == "32767")
      },
      test("encodes int") {
        val encoded = ToonBinaryCodec.intCodec.encodeToString(42)
        assertTrue(encoded == "42")
      },
      test("encodes negative int") {
        val encoded = ToonBinaryCodec.intCodec.encodeToString(-123)
        assertTrue(encoded == "-123")
      },
      test("encodes long") {
        val encoded = ToonBinaryCodec.longCodec.encodeToString(9876543210L)
        assertTrue(encoded == "9876543210")
      },
      test("encodes float") {
        val encoded = ToonBinaryCodec.floatCodec.encodeToString(3.14f)
        assertTrue(encoded == "3.14")
      },
      test("encodes float NaN as null") {
        val encoded = ToonBinaryCodec.floatCodec.encodeToString(Float.NaN)
        assertTrue(encoded == "null")
      },
      test("encodes float infinity as null") {
        val encoded = ToonBinaryCodec.floatCodec.encodeToString(Float.PositiveInfinity)
        assertTrue(encoded == "null")
      },
      test("encodes double") {
        val encoded = ToonBinaryCodec.doubleCodec.encodeToString(2.718281828)
        assertTrue(encoded == "2.718281828")
      },
      test("encodes double NaN as null") {
        val encoded = ToonBinaryCodec.doubleCodec.encodeToString(Double.NaN)
        assertTrue(encoded == "null")
      },
      test("encodes char") {
        val encoded = ToonBinaryCodec.charCodec.encodeToString('A')
        assertTrue(encoded == "A")
      },
      test("encodes string without quotes when safe") {
        val encoded = ToonBinaryCodec.stringCodec.encodeToString("hello")
        assertTrue(encoded == "hello")
      },
      test("encodes string with quotes when contains comma") {
        val encoded = ToonBinaryCodec.stringCodec.encodeToString("hello, world")
        assertTrue(encoded == "\"hello, world\"")
      },
      test("encodes string with quotes when contains colon") {
        val encoded = ToonBinaryCodec.stringCodec.encodeToString("key: value")
        assertTrue(encoded == "\"key: value\"")
      },
      test("encodes string with quotes when contains newline") {
        val encoded = ToonBinaryCodec.stringCodec.encodeToString("line1\nline2")
        assertTrue(encoded == "\"line1\\nline2\"")
      },
      test("encodes empty string with quotes") {
        val encoded = ToonBinaryCodec.stringCodec.encodeToString("")
        assertTrue(encoded == "\"\"")
      },
      test("encodes BigInt") {
        val encoded = ToonBinaryCodec.bigIntCodec.encodeToString(BigInt("12345678901234567890"))
        assertTrue(encoded == "12345678901234567890")
      },
      test("encodes BigDecimal") {
        val encoded = ToonBinaryCodec.bigDecimalCodec.encodeToString(BigDecimal("123.456789"))
        assertTrue(encoded == "123.456789")
      }
    ),
    suite("Primitive round-trip")(
      test("boolean round-trips") {
        val codec   = ToonBinaryCodec.booleanCodec
        val decoded = codec.decodeBytes(codec.encodeToBytes(true), 0, 4)
        assertTrue(decoded == Right(true))
      },
      test("int round-trips") {
        val codec   = ToonBinaryCodec.intCodec
        val bytes   = codec.encodeToBytes(42)
        val decoded = codec.decodeBytes(bytes, 0, bytes.length)
        assertTrue(decoded == Right(42))
      },
      test("long round-trips") {
        val codec   = ToonBinaryCodec.longCodec
        val bytes   = codec.encodeToBytes(9876543210L)
        val decoded = codec.decodeBytes(bytes, 0, bytes.length)
        assertTrue(decoded == Right(9876543210L))
      },
      test("string round-trips") {
        val codec   = ToonBinaryCodec.stringCodec
        val bytes   = codec.encodeToBytes("hello")
        val decoded = codec.decodeBytes(bytes, 0, bytes.length)
        assertTrue(decoded == Right("hello"))
      },
      test("float round-trips") {
        val codec   = ToonBinaryCodec.floatCodec
        val bytes   = codec.encodeToBytes(3.14f)
        val decoded = codec.decodeBytes(bytes, 0, bytes.length)
        assertTrue(decoded == Right(3.14f))
      },
      test("double round-trips") {
        val codec   = ToonBinaryCodec.doubleCodec
        val bytes   = codec.encodeToBytes(2.718281828)
        val decoded = codec.decodeBytes(bytes, 0, bytes.length)
        assertTrue(decoded == Right(2.718281828))
      },
      test("BigInt round-trips") {
        val codec   = ToonBinaryCodec.bigIntCodec
        val value   = BigInt("12345678901234567890")
        val bytes   = codec.encodeToBytes(value)
        val decoded = codec.decodeBytes(bytes, 0, bytes.length)
        assertTrue(decoded == Right(value))
      },
      test("BigDecimal round-trips") {
        val codec   = ToonBinaryCodec.bigDecimalCodec
        val value   = BigDecimal("123.456789")
        val bytes   = codec.encodeToBytes(value)
        val decoded = codec.decodeBytes(bytes, 0, bytes.length)
        assertTrue(decoded == Right(value))
      }
    ),
    suite("Record encoding")(
      test("encodes simple record with exact output") {
        val codec    = ToonFormat.codec[Person]
        val encoded  = codec.encodeToString(Person("Alice", 30))
        val expected = "name: Alice\nage: 30"
        assertTrue(encoded == expected)
      },
      test("encodes nested record with exact output") {
        val codec   = ToonFormat.codec[PersonWithAddress]
        val encoded = codec.encodeToString(
          PersonWithAddress("Bob", 25, Address("123 Main St", "Springfield"))
        )
        val expected = "name: Bob\nage: 25\naddress:\n  street: 123 Main St\n  city: Springfield"
        assertTrue(encoded == expected)
      },
      test("encodes record with primitive array field") {
        val codec    = ToonFormat.codec[Team]
        val encoded  = codec.encodeToString(Team("Engineering", List("Alice", "Bob", "Charlie")))
        val expected = "name: Engineering\nmembers: [3]: Alice,Bob,Charlie"
        assertTrue(encoded == expected)
      },
      test("encodes record with empty array field") {
        val codec    = ToonFormat.codec[Team]
        val encoded  = codec.encodeToString(Team("Empty Team", List.empty))
        val expected = "name: Empty Team\nmembers: [0]: "
        assertTrue(encoded == expected)
      }
    ),
    suite("Array encoding")(
      test("encodes int list inline") {
        val codec   = ToonFormat.codec[List[Int]]
        val encoded = codec.encodeToString(List(1, 2, 3))
        assertTrue(encoded == "[3]: 1,2,3")
      },
      test("encodes string list inline") {
        val codec   = ToonFormat.codec[List[String]]
        val encoded = codec.encodeToString(List("a", "b", "c"))
        assertTrue(encoded == "[3]: a,b,c")
      },
      test("encodes empty list") {
        val codec   = ToonFormat.codec[List[Int]]
        val encoded = codec.encodeToString(List.empty[Int])
        assertTrue(encoded == "[0]: ")
      },
      test("encodes single element list") {
        val codec   = ToonFormat.codec[List[Int]]
        val encoded = codec.encodeToString(List(42))
        assertTrue(encoded == "[1]: 42")
      },
      test("encodes list of booleans") {
        val codec   = ToonFormat.codec[List[Boolean]]
        val encoded = codec.encodeToString(List(true, false, true))
        assertTrue(encoded == "[3]: true,false,true")
      }
    ),
    suite("Nested arrays and complex structures")(
      test("encodes array of records inline") {
        val codec   = ToonFormat.codec[List[Person]]
        val encoded = codec.encodeToString(
          List(
            Person("Alice", 30),
            Person("Bob", 25)
          )
        )
        val expected = "[2]: name: Alice\nage: 30,name: Bob\nage: 25"
        assertTrue(encoded == expected)
      },
      test("encodes record with array of records") {
        val codec   = ToonFormat.codec[Company]
        val encoded = codec.encodeToString(
          Company("Acme Inc", List(Person("Alice", 30), Person("Bob", 25)))
        )
        val expected = "name: Acme Inc\nemployees: [2]: name: Alice\nage: 30,name: Bob\nage: 25"
        assertTrue(encoded == expected)
      }
    ),
    suite("Sealed trait encoding")(
      test("encodes Circle variant") {
        val codec    = ToonFormat.codec[Shape]
        val encoded  = codec.encodeToString(Circle(5.0): Shape)
        val expected = "Circle:\n  radius: 5.0"
        assertTrue(encoded == expected)
      },
      test("encodes Rectangle variant") {
        val codec    = ToonFormat.codec[Shape]
        val encoded  = codec.encodeToString(Rectangle(10.0, 20.0): Shape)
        val expected = "Rectangle:\n  width: 10.0\n  height: 20.0"
        assertTrue(encoded == expected)
      },
      test("encodes case object variant") {
        val codec    = ToonFormat.codec[Shape]
        val encoded  = codec.encodeToString(Point: Shape)
        val expected = "Point:\n"
        assertTrue(encoded == expected)
      },
      test("encodes record containing sealed trait") {
        val codec    = ToonFormat.codec[Drawing]
        val encoded  = codec.encodeToString(Drawing("My Circle", Circle(3.0)))
        val expected = "title: My Circle\nshape: Circle:\n  radius: 3.0"
        assertTrue(encoded == expected)
      }
    )
  )
}
