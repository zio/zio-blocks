package zio.blocks.schema

import zio.test._
import zio.blocks.schema.json.{Json, JsonDecoder}
import zio.blocks.schema.patch.Patch

object SyntaxSpec extends SchemaBaseSpec {

  case class Person(name: String, age: Int)
  object Person {
    implicit val schema: Schema[Person] = Schema.derived
  }

  case class Address(street: String, city: String)
  object Address {
    implicit val schema: Schema[Address] = Schema.derived
  }

  override def spec: Spec[TestEnvironment, Any] = suite("SyntaxSpec")(
    suite("diff")(
      test("computes difference between two values") {
        val p1    = Person("Alice", 30)
        val p2    = Person("Alice", 31)
        val patch = p1.diff(p2)
        assertTrue(!patch.isEmpty)
      },
      test("produces empty patch for identical values") {
        val p1    = Person("Alice", 30)
        val patch = p1.diff(p1)
        assertTrue(patch.isEmpty)
      }
    ),
    suite("show")(
      test("converts value to string representation") {
        val p      = Person("Bob", 25)
        val result = p.show
        assertTrue(
          result.contains("Bob"),
          result.contains("25")
        )
      },
      test("handles nested structures") {
        val a      = Address("123 Main St", "Springfield")
        val result = a.show
        assertTrue(
          result.contains("123 Main St"),
          result.contains("Springfield")
        )
      }
    ),
    suite("toJson")(
      test("encodes value to Json AST") {
        val p    = Person("Charlie", 40)
        val json = p.toJson
        assertTrue(
          json.get("name").as[String] == Right("Charlie"),
          json.get("age").as[Int] == Right(40)
        )
      },
      test("preserves all fields") {
        val a    = Address("456 Oak Ave", "Metropolis")
        val json = a.toJson
        assertTrue(
          json.get("street").as[String] == Right("456 Oak Ave"),
          json.get("city").as[String] == Right("Metropolis")
        )
      }
    ),
    suite("toJsonString")(
      test("encodes value to JSON string") {
        val p      = Person("Dave", 50)
        val result = p.toJsonString
        assertTrue(
          result.contains("\"name\""),
          result.contains("\"Dave\""),
          result.contains("\"age\""),
          result.contains("50")
        )
      },
      test("produces valid JSON") {
        val p      = Person("Eve", 35)
        val result = p.toJsonString
        assertTrue(Json.parse(result).isRight)
      }
    ),
    suite("toJsonBytes")(
      test("encodes value to UTF-8 bytes") {
        val p     = Person("Frank", 45)
        val bytes = p.toJsonBytes
        val str   = new String(bytes, java.nio.charset.StandardCharsets.UTF_8)
        assertTrue(
          str.contains("Frank"),
          str.contains("45")
        )
      },
      test("can be decoded back") {
        val p       = Person("Grace", 28)
        val bytes   = p.toJsonBytes
        val decoded = bytes.fromJson[Person]
        assertTrue(decoded == Right(p))
      }
    ),
    suite("String.fromJson[A]")(
      test("decodes valid JSON string") {
        val json   = """{"name":"Henry","age":60}"""
        val result = json.fromJson[Person]
        assertTrue(result == Right(Person("Henry", 60)))
      },
      test("returns error for invalid JSON") {
        val json   = """{"name":"Invalid"}"""
        val result = json.fromJson[Person]
        assertTrue(result.isLeft)
      },
      test("returns error for malformed JSON") {
        val json   = """not json"""
        val result = json.fromJson[Person]
        assertTrue(result.isLeft)
      }
    ),
    suite("Array[Byte].fromJson[A]")(
      test("decodes valid JSON bytes") {
        val json   = """{"name":"Iris","age":33}"""
        val bytes  = json.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        val result = bytes.fromJson[Person]
        assertTrue(result == Right(Person("Iris", 33)))
      },
      test("returns error for invalid data") {
        val bytes  = "garbage".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        val result = bytes.fromJson[Person]
        assertTrue(result.isLeft)
      }
    ),
    suite("Json.as[A]")(
      test("decodes Json AST to typed value") {
        val json   = Json.Object("name" -> Json.String("Jack"), "age" -> Json.Number(70))
        val result = json.as[Person](JsonDecoder.fromSchema[Person])
        assertTrue(result == Right(Person("Jack", 70)))
      },
      test("returns error for mismatched structure") {
        val json   = Json.Object("wrong" -> Json.String("field"))
        val result = json.as[Person](JsonDecoder.fromSchema[Person])
        assertTrue(result.isLeft)
      },
      test("handles primitive types") {
        val json: Json = Json.String("hello")
        val result     = json.as[String]
        assertTrue(result == Right("hello"))
      }
    ),
    suite("applyPatch")(
      test("applies patch to value") {
        val p1     = Person("Kate", 25)
        val p2     = Person("Kate", 26)
        val patch  = p1.diff(p2)
        val result = p1.applyPatch(patch)
        assertTrue(result == p2)
      },
      test("returns original for empty patch") {
        val p      = Person("Leo", 40)
        val patch  = Patch.empty[Person]
        val result = p.applyPatch(patch)
        assertTrue(result == p)
      }
    ),
    suite("applyPatchStrict")(
      test("applies patch strictly and returns Right on success") {
        val p1     = Person("Mike", 30)
        val p2     = Person("Mike", 31)
        val patch  = p1.diff(p2)
        val result = p1.applyPatchStrict(patch)
        assertTrue(result == Right(p2))
      },
      test("returns Right for empty patch") {
        val p      = Person("Nancy", 45)
        val patch  = Patch.empty[Person]
        val result = p.applyPatchStrict(patch)
        assertTrue(result == Right(p))
      }
    ),
    suite("roundtrip")(
      test("toJson -> as[A] roundtrip") {
        val p       = Person("Oscar", 55)
        val json    = p.toJson
        val decoded = json.as[Person](JsonDecoder.fromSchema[Person])
        assertTrue(decoded == Right(p))
      },
      test("toJsonString -> fromJson[A] roundtrip") {
        val p       = Person("Pat", 65)
        val jsonStr = p.toJsonString
        val decoded = jsonStr.fromJson[Person]
        assertTrue(decoded == Right(p))
      },
      test("toJsonBytes -> fromJson[A] roundtrip") {
        val p       = Person("Quinn", 22)
        val bytes   = p.toJsonBytes
        val decoded = bytes.fromJson[Person]
        assertTrue(decoded == Right(p))
      },
      test("diff -> applyPatch roundtrip") {
        val p1     = Person("Rose", 30)
        val p2     = Person("Rose", 35)
        val patch  = p1.diff(p2)
        val result = p1.applyPatch(patch)
        assertTrue(result == p2)
      }
    ),
    suite("edge cases")(
      test("empty string values") {
        val p       = Person("", 0)
        val json    = p.toJson
        val decoded = json.as[Person](JsonDecoder.fromSchema[Person])
        assertTrue(decoded == Right(p))
      },
      test("special characters in strings") {
        val p       = Person("John \"Jack\" O'Brien", 42)
        val json    = p.toJsonString
        val decoded = json.fromJson[Person]
        assertTrue(decoded == Right(p))
      },
      test("unicode characters") {
        val p       = Person("日本語", 100)
        val json    = p.toJsonString
        val decoded = json.fromJson[Person]
        assertTrue(decoded == Right(p))
      },
      test("negative numbers") {
        val p       = Person("Negative", -5)
        val json    = p.toJson
        val decoded = json.as[Person](JsonDecoder.fromSchema[Person])
        assertTrue(decoded == Right(p))
      }
    )
  )
}
