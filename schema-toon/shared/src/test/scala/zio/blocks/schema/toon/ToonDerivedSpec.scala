package zio.blocks.schema.toon

import zio.test._
import zio.blocks.schema._
import java.nio.charset.StandardCharsets

/**
 * Comprehensive integration tests for ToonBinaryCodecDeriver.
 *
 * Test patterns inspired by toon4s (https://github.com/vim89/toon4s) under
 * Apache 2.0 License.
 *
 * All tests use EXACT STRING MATCHING to verify correct TOON output.
 */
object ToonDerivedSpec extends ZIOSpecDefault {

  // ==================== ARRAY-FOCUSED TEST MODELS ====================
  // Per jdegoes feedback: 10+ data types featuring arrays in nested positions

  // 1. Simple record with primitive array
  case class IntList(values: List[Int])
  object IntList {
    implicit val schema: Schema[IntList] = Schema.derived
  }

  // 2. Record with nested array of records
  case class Point(x: Int, y: Int)
  object Point {
    implicit val schema: Schema[Point] = Schema.derived
  }

  case class Polygon(name: String, vertices: List[Point])
  object Polygon {
    implicit val schema: Schema[Polygon] = Schema.derived
  }

  // 3. Record with multiple arrays
  case class DataSet(labels: List[String], scores: List[Int])
  object DataSet {
    implicit val schema: Schema[DataSet] = Schema.derived
  }

  // 4. Deeply nested array structure
  case class Cell(value: Int)
  object Cell {
    implicit val schema: Schema[Cell] = Schema.derived
  }

  case class Row(cells: List[Cell])
  object Row {
    implicit val schema: Schema[Row] = Schema.derived
  }

  case class Grid(rows: List[Row])
  object Grid {
    implicit val schema: Schema[Grid] = Schema.derived
  }

  // 5. Record with array at top-level field and nested record
  case class Tag(id: Int, name: String)
  object Tag {
    implicit val schema: Schema[Tag] = Schema.derived
  }

  case class Article(title: String, tags: List[Tag])
  object Article {
    implicit val schema: Schema[Article] = Schema.derived
  }

  // 6. Array of mixed-type records
  case class User(id: Int, name: String, active: Boolean)
  object User {
    implicit val schema: Schema[User] = Schema.derived
  }

  case class UserList(users: List[User])
  object UserList {
    implicit val schema: Schema[UserList] = Schema.derived
  }

  // 7. Record with optional array
  case class OptionalTags(name: String, tags: Option[List[String]])
  object OptionalTags {
    implicit val schema: Schema[OptionalTags] = Schema.derived
  }

  // 8. Record with empty-capable array
  case class EmptyCapable(items: List[String])
  object EmptyCapable {
    implicit val schema: Schema[EmptyCapable] = Schema.derived
  }

  // 9. Record with array of primitives (various types)
  case class MixedPrimitives(ints: List[Int], longs: List[Long], bools: List[Boolean])
  object MixedPrimitives {
    implicit val schema: Schema[MixedPrimitives] = Schema.derived
  }

  // 10. Complex nested array with record containing another array
  case class Department(name: String, employeeIds: List[Int])
  object Department {
    implicit val schema: Schema[Department] = Schema.derived
  }

  case class Organization(name: String, departments: List[Department])
  object Organization {
    implicit val schema: Schema[Organization] = Schema.derived
  }

  // 11. Vector collection type
  case class VectorData(items: Vector[Int])
  object VectorData {
    implicit val schema: Schema[VectorData] = Schema.derived
  }

  // 12. Set collection type
  case class SetData(members: Set[String])
  object SetData {
    implicit val schema: Schema[SetData] = Schema.derived
  }

  // Simple record (non-array) for comparison
  case class Person(name: String, age: Int)
  object Person {
    implicit val schema: Schema[Person] = Schema.derived
  }

  case class Address(street: String, city: String)
  object Address {
    implicit val schema: Schema[Address] = Schema.derived
  }

  // ADT/Sealed trait
  sealed trait Status
  case object Active                 extends Status
  case object Inactive               extends Status
  case class Pending(reason: String) extends Status
  object Status {
    implicit val schema: Schema[Status] = Schema.derived
  }

  // ==================== TEST HELPERS ====================

  private def encode[A](codec: ToonBinaryCodec[A], value: A): String = {
    val writer = new ToonWriter(new Array[Byte](16384), ToonWriterConfig)
    codec.encodeValue(value, writer)
    new String(writer.buf, 0, writer.count, StandardCharsets.UTF_8)
  }

  private def decode[A](codec: ToonBinaryCodec[A], input: String): Either[SchemaError, A] =
    codec.decodeFromString(input)

  private def roundTrip[A](codec: ToonBinaryCodec[A], value: A): Either[SchemaError, A] = {
    val encoded = encode(codec, value)
    decode(codec, encoded)
  }

  // ==================== TEST SUITES ====================

  def spec = suite("ToonDerivedSpec")(
    // ==================== EXACT MATCH PRIMITIVE ARRAY TESTS ====================
    suite("Primitive Array Exact Match")(
      test("integer list inline format") {
        val codec   = Schema.list[Int].derive(ToonFormat.deriver)
        val value   = List(1, 2, 3, 4, 5)
        val encoded = encode(codec, value)
        assertTrue(encoded == "[5]: 1,2,3,4,5")
      },
      test("empty integer list") {
        val codec   = Schema.list[Int].derive(ToonFormat.deriver)
        val value   = List.empty[Int]
        val encoded = encode(codec, value)
        assertTrue(encoded == "[0]:")
      },
      test("single element list") {
        val codec   = Schema.list[Int].derive(ToonFormat.deriver)
        val value   = List(42)
        val encoded = encode(codec, value)
        assertTrue(encoded == "[1]: 42")
      },
      test("boolean list") {
        val codec   = Schema.list[Boolean].derive(ToonFormat.deriver)
        val value   = List(true, false, true)
        val encoded = encode(codec, value)
        assertTrue(encoded == "[3]: true,false,true")
      },
      test("long list") {
        val codec   = Schema.list[Long].derive(ToonFormat.deriver)
        val value   = List(100L, 200L, 300L)
        val encoded = encode(codec, value)
        assertTrue(encoded == "[3]: 100,200,300")
      }
    ),

    // ==================== TABULAR ARRAY FORMAT EXACT MATCH ====================
    suite("Tabular Array Format Exact Match")(
      test("list of simple records uses tabular format") {
        val codec    = Schema.list(Point.schema).derive(ToonFormat.deriver)
        val value    = List(Point(1, 2), Point(3, 4))
        val encoded  = encode(codec, value)
        val expected = """[2]{x,y}:
  1,2
  3,4
"""
        assertTrue(encoded == expected)
      },
      test("list of persons tabular format") {
        val codec    = Schema.list(Person.schema).derive(ToonFormat.deriver)
        val value    = List(Person("Alice", 30), Person("Bob", 25))
        val encoded  = encode(codec, value)
        val expected = """[2]{name,age}:
  Alice,30
  Bob,25
"""
        assertTrue(encoded == expected)
      },
      test("list of addresses tabular format") {
        val codec    = Schema.list(Address.schema).derive(ToonFormat.deriver)
        val value    = List(Address("123 Main", "NYC"), Address("456 Oak", "LA"))
        val encoded  = encode(codec, value)
        val expected = """[2]{street,city}:
  123 Main,NYC
  456 Oak,LA
"""
        assertTrue(encoded == expected)
      },
      test("list of users with boolean tabular format") {
        val codec    = Schema.list(User.schema).derive(ToonFormat.deriver)
        val value    = List(User(1, "Alice", true), User(2, "Bob", false))
        val encoded  = encode(codec, value)
        val expected = """[2]{id,name,active}:
  1,Alice,true
  2,Bob,false
"""
        assertTrue(encoded == expected)
      },
      test("list of tags tabular format") {
        val codec    = Schema.list(Tag.schema).derive(ToonFormat.deriver)
        val value    = List(Tag(1, "scala"), Tag(2, "zio"))
        val encoded  = encode(codec, value)
        val expected = """[2]{id,name}:
  1,scala
  2,zio
"""
        assertTrue(encoded == expected)
      },
      test("empty record list") {
        val codec   = Schema.list(Point.schema).derive(ToonFormat.deriver)
        val value   = List.empty[Point]
        val encoded = encode(codec, value)
        assertTrue(encoded == "[0]:")
      }
    ),

    // ==================== NESTED ARRAY RECORD TESTS ====================
    suite("Nested Array Records Exact Match")(
      test("polygon with vertices array") {
        val codec    = Polygon.schema.derive(ToonFormat.deriver)
        val value    = Polygon("triangle", List(Point(0, 0), Point(1, 0), Point(0, 1)))
        val encoded  = encode(codec, value)
        val expected = """name: triangle
vertices[3]{x,y}:
  0,0
  1,0
  0,1
"""
        assertTrue(encoded == expected)
      },
      test("article with tags array") {
        val codec    = Article.schema.derive(ToonFormat.deriver)
        val value    = Article("TOON Guide", List(Tag(1, "serialization"), Tag(2, "llm")))
        val encoded  = encode(codec, value)
        val expected = """title: TOON Guide
tags[2]{id,name}:
  1,serialization
  2,llm
"""
        assertTrue(encoded == expected)
      },
      test("userlist with users array") {
        val codec    = UserList.schema.derive(ToonFormat.deriver)
        val value    = UserList(List(User(1, "Alice", true), User(2, "Bob", false)))
        val encoded  = encode(codec, value)
        val expected = """users[2]{id,name,active}:
  1,Alice,true
  2,Bob,false
"""
        assertTrue(encoded == expected)
      },
      test("dataset with multiple arrays") {
        val codec    = DataSet.schema.derive(ToonFormat.deriver)
        val value    = DataSet(List("a", "b", "c"), List(10, 20, 30))
        val encoded  = encode(codec, value)
        val expected = """labels[3]: a,b,c
scores[3]: 10,20,30"""
        assertTrue(encoded == expected)
      },
      test("department with employee ids") {
        val codec    = Department.schema.derive(ToonFormat.deriver)
        val value    = Department("Engineering", List(101, 102, 103))
        val encoded  = encode(codec, value)
        val expected = """name: Engineering
employeeIds[3]: 101,102,103"""
        assertTrue(encoded == expected)
      }
    ),

    // ==================== DEEPLY NESTED ARRAY TESTS ====================
    suite("Deeply Nested Arrays Exact Match")(
      test("organization with departments containing arrays") {
        val codec = Organization.schema.derive(ToonFormat.deriver)
        val value = Organization(
          "TechCorp",
          List(
            Department("Engineering", List(1, 2)),
            Department("Sales", List(3))
          )
        )
        val encoded  = encode(codec, value)
        val expected = """name: TechCorp
departments[2]:
- 
    name: Engineering
    employeeIds[2]: 1,2
- 
    name: Sales
    employeeIds[1]: 3
"""
        assertTrue(encoded == expected)
      },
      test("intlist record") {
        val codec    = IntList.schema.derive(ToonFormat.deriver)
        val value    = IntList(List(1, 2, 3))
        val encoded  = encode(codec, value)
        val expected = """values[3]: 1,2,3"""
        assertTrue(encoded == expected)
      },
      test("mixed primitives record") {
        val codec    = MixedPrimitives.schema.derive(ToonFormat.deriver)
        val value    = MixedPrimitives(List(1, 2), List(100L, 200L), List(true, false))
        val encoded  = encode(codec, value)
        val expected = """ints[2]: 1,2
longs[2]: 100,200
bools[2]: true,false"""
        assertTrue(encoded == expected)
      }
    ),

    // ==================== COLLECTION TYPE TESTS ====================
    suite("Collection Types Exact Match")(
      test("vector of integers") {
        val codec   = Schema.vector[Int].derive(ToonFormat.deriver)
        val value   = Vector(10, 20, 30)
        val encoded = encode(codec, value)
        assertTrue(encoded == "[3]: 10,20,30")
      },
      test("set of strings") {
        val codec   = SetData.schema.derive(ToonFormat.deriver)
        val value   = SetData(Set("a"))
        val encoded = encode(codec, value)
        assertTrue(encoded == "members[1]: a")
      },
      test("empty vector") {
        val codec   = Schema.vector[Int].derive(ToonFormat.deriver)
        val value   = Vector.empty[Int]
        val encoded = encode(codec, value)
        assertTrue(encoded == "[0]:")
      }
    ),

    // ==================== SIMPLE RECORD EXACT MATCH ====================
    suite("Simple Records Exact Match")(
      test("person record") {
        val codec    = Person.schema.derive(ToonFormat.deriver)
        val value    = Person("Alice", 30)
        val encoded  = encode(codec, value)
        val expected = """name: Alice
age: 30"""
        assertTrue(encoded == expected)
      },
      test("address record") {
        val codec    = Address.schema.derive(ToonFormat.deriver)
        val value    = Address("123 Main St", "New York")
        val encoded  = encode(codec, value)
        val expected = """street: 123 Main St
city: New York"""
        assertTrue(encoded == expected)
      },
      test("point record") {
        val codec    = Point.schema.derive(ToonFormat.deriver)
        val value    = Point(10, 20)
        val encoded  = encode(codec, value)
        val expected = """x: 10
y: 20"""
        assertTrue(encoded == expected)
      }
    ),

    // ==================== ADT/SEALED TRAIT TESTS ====================
    suite("ADT Exact Match")(
      test("case object Active") {
        val codec         = Status.schema.derive(ToonFormat.deriver)
        val value: Status = Active
        val encoded       = encode(codec, value)
        assertTrue(encoded == "Active: \n")
      },
      test("case class Pending") {
        val codec         = Status.schema.derive(ToonFormat.deriver)
        val value: Status = Pending("awaiting approval")
        val encoded       = encode(codec, value)
        val expected      = """Pending: 
    reason: awaiting approval"""
        assertTrue(encoded == expected)
      }
    ),

    // ==================== PRIMITIVE CODEC EXACT MATCH ====================
    suite("Primitive Codecs Exact Match")(
      test("integer encoding") {
        val codec   = ToonBinaryCodec.intCodec
        val encoded = encode(codec, 12345)
        assertTrue(encoded == "12345")
      },
      test("negative integer") {
        val codec   = ToonBinaryCodec.intCodec
        val encoded = encode(codec, -999)
        assertTrue(encoded == "-999")
      },
      test("boolean true") {
        val codec   = ToonBinaryCodec.booleanCodec
        val encoded = encode(codec, true)
        assertTrue(encoded == "true")
      },
      test("boolean false") {
        val codec   = ToonBinaryCodec.booleanCodec
        val encoded = encode(codec, false)
        assertTrue(encoded == "false")
      },
      test("string simple") {
        val codec   = ToonBinaryCodec.stringCodec
        val encoded = encode(codec, "hello")
        assertTrue(encoded == "hello")
      },
      test("string with quotes needed") {
        val codec   = ToonBinaryCodec.stringCodec
        val encoded = encode(codec, "hello:world")
        assertTrue(encoded == "\"hello:world\"")
      },
      test("double") {
        val codec   = ToonBinaryCodec.doubleCodec
        val encoded = encode(codec, 3.14159)
        assertTrue(encoded == "3.14159")
      },
      test("NaN encodes as null") {
        val codec   = ToonBinaryCodec.doubleCodec
        val encoded = encode(codec, Double.NaN)
        assertTrue(encoded == "null")
      },
      test("Infinity encodes as null") {
        val codec   = ToonBinaryCodec.doubleCodec
        val encoded = encode(codec, Double.PositiveInfinity)
        assertTrue(encoded == "null")
      }
    ),

    // ==================== ROUND-TRIP TESTS ====================
    suite("Round-Trip Verification")(
      test("integer list round-trip") {
        val codec = Schema.list[Int].derive(ToonFormat.deriver)
        val value = List(1, 2, 3, 4, 5)
        assertTrue(roundTrip(codec, value) == Right(value))
      },
      test("person round-trip") {
        val codec = Person.schema.derive(ToonFormat.deriver)
        val value = Person("Bob", 42)
        assertTrue(roundTrip(codec, value) == Right(value))
      },
      test("point list round-trip") {
        val codec = Schema.list(Point.schema).derive(ToonFormat.deriver)
        val value = List(Point(1, 2), Point(3, 4))
        assertTrue(roundTrip(codec, value) == Right(value))
      },
      test("primitive int round-trip") {
        val codec = ToonBinaryCodec.intCodec
        assertTrue(roundTrip(codec, 42) == Right(42))
      },
      test("primitive boolean round-trip") {
        val codec = ToonBinaryCodec.booleanCodec
        assertTrue(roundTrip(codec, true) == Right(true))
      },
      test("LocalDate round-trip") {
        import java.time.LocalDate
        val codec = ToonBinaryCodec.localDateCodec
        val value = LocalDate.of(2026, 1, 11)
        assertTrue(roundTrip(codec, value) == Right(value))
      },
      test("Duration round-trip") {
        import java.time.Duration
        val codec = ToonBinaryCodec.durationCodec
        val value = Duration.ofHours(2).plusMinutes(30)
        assertTrue(roundTrip(codec, value) == Right(value))
      },
      test("UUID round-trip") {
        import java.util.UUID
        val codec = ToonBinaryCodec.uuidCodec
        val value = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        assertTrue(roundTrip(codec, value) == Right(value))
      },
      // New Complex Tests
      test("Polygon round trip") {
        val codec = Polygon.schema.derive(ToonFormat.deriver)
        val value = Polygon("poly1", List(Point(1, 2), Point(3, 4)))
        assertTrue(roundTrip(codec, value) == Right(value))
      },
      test("IntList round trip") {
        val codec = IntList.schema.derive(ToonFormat.deriver)
        val value = IntList(List(1, 2, 3, 4, 5))
        assertTrue(roundTrip(codec, value) == Right(value))
      },
      test("MixedPrimitives round trip") {
        val codec = MixedPrimitives.schema.derive(ToonFormat.deriver)
        val value = MixedPrimitives(List(1, 2), List(100L, 200L), List(true, false))
        assertTrue(roundTrip(codec, value) == Right(value))
      },
      test("Organization round trip") {
        val codec = Organization.schema.derive(ToonFormat.deriver)
        val value = Organization("TechCorp", List(Department("Engineering", List(1, 2)), Department("Sales", List(3))))
        assertTrue(roundTrip(codec, value) == Right(value))
      },
      test("DataSet round trip") {
        val codec = DataSet.schema.derive(ToonFormat.deriver)
        val value = DataSet(List("a", "b", "c"), List(10, 20, 30))
        assertTrue(roundTrip(codec, value) == Right(value))
      },
      test("Status ADT round trip") {
        val codec = Status.schema.derive(ToonFormat.deriver)
        assertTrue(roundTrip(codec, Active) == Right(Active))
        assertTrue(roundTrip(codec, Inactive) == Right(Inactive))
        assertTrue(roundTrip(codec, Pending("Waiting")) == Right(Pending("Waiting")))
      },
      // ==================== ULTRA-COMPLEX NESTED ARRAYS ====================
      // Per jdegoes specific request on PR #663: "I would add some more complicated
      // nesting, where you have a record that has a list of other records, which have
      // lists of records (of primitives)."
      test("3-level nested arrays: Team -> Projects -> Tasks") {
        case class Task(id: Int, name: String)
        object Task {
          implicit val schema: Schema[Task] = Schema.derived
        }
        case class Project(title: String, tasks: List[Task])
        object Project {
          implicit val schema: Schema[Project] = Schema.derived
        }
        case class Team(name: String, projects: List[Project])
        object Team {
          implicit val schema: Schema[Team] = Schema.derived
        }

        val codec = Team.schema.derive(ToonFormat.deriver)
        val value = Team(
          "Engineering",
          List(
            Project("Backend", List(Task(1, "API"), Task(2, "DB"))),
            Project("Frontend", List(Task(3, "UI"), Task(4, "UX"), Task(5, "Design")))
          )
        )
        assertTrue(roundTrip(codec, value) == Right(value))
      },
      test("Double nesting with primitives: Department -> Employees with IDs") {
        case class EmployeeRecord(id: Int, name: String)
        object EmployeeRecord {
          implicit val schema: Schema[EmployeeRecord] = Schema.derived
        }
        case class DeptRecord(title: String, employees: List[EmployeeRecord])
        object DeptRecord {
          implicit val schema: Schema[DeptRecord] = Schema.derived
        }

        val codec = DeptRecord.schema.derive(ToonFormat.deriver)
        val value = DeptRecord(
          "Engineering",
          List(EmployeeRecord(1, "Alice"), EmployeeRecord(2, "Bob"), EmployeeRecord(3, "Carol"))
        )
        assertTrue(roundTrip(codec, value) == Right(value))
      },
      test("Parallel arrays: Config with multiple independent lists") {
        case class MultiListRecord(names: List[String], ids: List[Int], flags: List[Boolean])
        object MultiListRecord {
          implicit val schema: Schema[MultiListRecord] = Schema.derived
        }

        val codec = MultiListRecord.schema.derive(ToonFormat.deriver)
        val value = MultiListRecord(
          List("Alice", "Bob", "Carol"),
          List(1, 2, 3),
          List(true, false, true)
        )
        assertTrue(roundTrip(codec, value) == Right(value))
      },
      test("Array of arrays of primitives: Matrix structure") {
        case class Matrix(rows: List[List[Int]])
        object Matrix {
          implicit val schema: Schema[Matrix] = Schema.derived
        }

        val codec = Matrix.schema.derive(ToonFormat.deriver)
        val value = Matrix(List(List(1, 2, 3), List(4, 5, 6), List(7, 8, 9)))
        assertTrue(roundTrip(codec, value) == Right(value))
      },
      test("Heterogeneous nesting: Config with string lists and nested maps") {
        case class ServerConfig(host: String, ports: List[Int], tags: List[String])
        object ServerConfig {
          implicit val schema: Schema[ServerConfig] = Schema.derived
        }
        case class ClusterConfig(name: String, servers: List[ServerConfig])
        object ClusterConfig {
          implicit val schema: Schema[ClusterConfig] = Schema.derived
        }

        val codec = ClusterConfig.schema.derive(ToonFormat.deriver)
        val value = ClusterConfig(
          "Production",
          List(
            ServerConfig("prod-1", List(8080, 8443), List("primary", "active")),
            ServerConfig("prod-2", List(8080, 8443), List("secondary", "standby")),
            ServerConfig("prod-3", List(9090), List("monitoring"))
          )
        )
        assertTrue(roundTrip(codec, value) == Right(value))
      }
    )
  )
}
