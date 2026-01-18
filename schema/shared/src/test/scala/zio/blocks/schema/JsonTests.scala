package zio.blocks.schema.json

import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test.TestAspect._

/**
 * Tests for the Json ADT and related functionality.
 */
object JsonTests extends DefaultRunnableSpec {

  /**
   * Tests basic JSON construction and type checking.
   */
  def jsonConstructionSuite: Spec[Unit] = suite("JSON Construction")(
    test("Object construction") {
      val obj = Json.Object("name" -> Json.str("Alice"), "age" -> Json.num(30))
      assert(obj.isObject)
      assert(!obj.isArray)
      assert(!obj.isString)
      assert(!obj.isNumber)
      assert(!obj.isBoolean)
      assert(!obj.isNull)
      assert(obj.fields.size == 2)
      assert(obj.fields.contains("name"))
      assert(obj.fields.contains("age"))
      assert(obj("name") == Json.str("Alice"))
      assert(obj("age") == Json.num(30))
    },

    test("Array construction") {
      val arr = Json.arr(Json.str("a"), Json.num(1), Json.num(2), Json.bool(true))
      assert(arr.isArray)
      assert(!arr.isObject)
      assert(!arr.isString)
      assert(!arr.isNumber)
      assert(!arr.isBoolean)
      assert(!arr.isNull)
      assert(arr.elements.size == 4)
      assert(arr(0) == Json.str("a"))
      assert(arr(1) == Json.num(1))
      assert(arr(2) == Json.num(2))
      assert(arr(3) == Json.bool(true))
    },

    test("String construction") {
      val str = Json.str("hello world")
      assert(str.isString)
      assert(!str.isObject)
      assert(!str.isArray)
      assert(!str.isNumber)
      assert(!str.isBoolean)
      assert(!str.isNull)
      assert(str.stringValue.contains("hello world"))
    },

    test("Number construction") {
      val num = Json.num(42.5)
      assert(num.isNumber)
      assert(!num.isObject)
      assert(!num.isArray)
      assert(!num.isString)
      assert(!num.isBoolean)
      assert(!num.isNull)
      assert(num.numberValue.contains(BigDecimal(42.5)))
      assert(num.numberValue.get == 42.5)
    },

    test("Boolean construction") {
      val bool = Json.bool(true)
      assert(bool.isBoolean)
      assert(!bool.isObject)
      assert(!bool.isArray)
      assert(!bool.isString)
      assert(!bool.isNumber)
      assert(!bool.isNull)
      assert(bool.booleanValue.contains(true))
    },

    test("Null construction") {
      val nullVal = Json.Null
      assert(nullVal.isNull)
      assert(!nullVal.isObject)
      assert(!nullVal.isArray)
      assert(!nullVal.isString)
      assert(!nullVal.isNumber)
      assert(!nullVal.isBoolean)
    }
  )

  /**
   * Tests JSON navigation and accessors.
   */
  def jsonNavigationSuite: Spec[Unit] = suite("JSON Navigation")(
    test("Object field access") {
      val obj = Json.obj("name" -> Json.str("Alice"), "age" -> Json.num(30), "active" -> Json.bool(true))
      
      // Test successful field access
      assert(obj("name") == Json.str("Alice"))
      assert(obj("age") == Json.num(30))
      assert(obj("active") == Json.bool(true))
      
      // Test missing field returns null
      assert(obj("missing") == Json.Null)
      
      // Test field access on non-object
      val str = Json.str("test")
      assert(str("name") == Json.Null)
    },

    test("Array index access") {
      val arr = Json.arr(Json.num(1), Json.num(2), Json.num(3))
      
      // Test successful index access
      assert(arr(0) == Json.num(1))
      assert(arr(1) == Json.num(2))
      assert(arr(2) == Json.num(3))
      
      // Test out of bounds returns null
      assert(arr(3) == Json.Null)
      assert(arr(-1) == Json.Null)
      
      // Test index access on non-array
      val str = Json.str("test")
      assert(str(0) == Json.Null)
    },

    test("Chained access") {
      val obj = Json.obj("user" -> Json.obj("profile" -> Json.obj("name" -> Json.str("Alice"), "age" -> Json.num(30))))
      
      // Test chained access: user.profile.name
      assert(obj("user") == Json.obj("profile"))
      assert(obj("profile")("name") == Json.str("Alice"))
      assert(obj("profile")("age") == Json.num(30))
    }
  )

  /**
   * Tests JSON modification operations.
   */
  def jsonModificationSuite: Spec[Unit] = suite("JSON Modification")(
    test("Object set operation") {
      val original = Json.obj("name" -> Json.str("Alice"))
      val modified = original.set("age", Json.num(31))
      
      assert(modified.isObject)
      assert(modified.fields.size == 2)
      assert(modified.fields.contains("name"))
      assert(modified.fields.contains("age"))
      assert(modified("age") == Json.num(31))
      assert(original("age") == Json.Null) // Original doesn't have age field
    },

    test("Object remove operation") {
      val original = Json.obj("name" -> Json.str("Alice"), "age" -> Json.num(30), "active" -> Json.bool(true))
      val modified = original.remove("age")
      
      assert(modified.isObject)
      assert(modified.fields.size == 2)
      assert(!modified.fields.contains("age"))
      assert(modified.fields.contains("active"))
      assert(modified("active") == Json.bool(true))
    },

    test("Array append operation") {
      val original = Json.arr(Json.num(1), Json.num(2))
      val modified = original.append(Json.num(3))
      
      assert(modified.isArray)
      assert(modified.elements.size == 3)
      assert(modified(0) == Json.num(1))
      assert(modified(1) == Json.num(2))
      assert(modified(2) == Json.num(3))
    },

    test("Array removeAt operation") {
      val original = Json.arr(Json.num(1), Json.num(2), Json.num(3), Json.num(4))
      val modified = original.removeAt(1)
      
      assert(modified.isArray)
      assert(modified.elements.size == 3)
      assert(modified(0) == Json.num(1))
      assert(modified(1) == Json.num(3))
      assert(modified(2) == Json.num(4))
      assert(modified(3) == Json.Null) // Out of bounds should return null
    }
  )

  /**
   * Tests JSON parsing and encoding.
   */
  def jsonParsingEncodingSuite: Spec[Unit] = suite("JSON Parsing & Encoding")(
    test("Parse valid JSON string") {
      val jsonString = """{"name": "Alice", "age": 30, "active": true}"""
      val result = Json.parse(jsonString)
      
      assert(result.isRight)
      val json = result.toOption.get
      assert(json.isObject)
      assert(json("name") == Json.str("Alice"))
      assert(json("age") == Json.num(30))
      assert(json("active") == Json.bool(true))
    },

    test("Parse invalid JSON string") {
      val invalidJsonString = """{"name": "Alice", "age":}"""
      val result = Json.parse(invalidJsonString)
      
      assert(result.isLeft)
      assert(result.left.exists(_.getMessage.contains("Unexpected end of input")))
    },

    test("Encode JSON to string") {
      val json = Json.obj("name" -> Json.str("Alice"), "age" -> Json.num(30))
      val encoded = Json.encode(json)
      
      assert(encoded.contains("\"name\""))
      assert(encoded.contains("\"Alice\""))
      assert(encoded.contains("\"age\""))
      assert(encoded.contains("30"))
    },

    test("Round-trip encoding/decoding") {
      val original = Json.obj("name" -> Json.str("Alice"), "age" -> Json.num(30), "active" -> Json.bool(true))
      val encoded = Json.encode(original)
      val decoded = Json.parse(encoded)
      
      assert(decoded.isRight)
      val roundtrip = decoded.toOption.get
      assert(roundtrip.isDefined)
      assert(roundtrip.get.isObject)
      assert(roundtrip.get.fields.size == 3)
      assert(roundtrip.get.fields.contains("name"))
      assert(roundtrip.get.fields.contains("age"))
      assert(roundtrip.get.fields.contains("active"))
      assert(roundtrip.get("name") == Json.str("Alice"))
      assert(roundtrip.get("age") == Json.num(30))
      assert(roundtrip.get("active") == Json.bool(true))
    }
  )

  /**
   * Tests string interpolators.
   */
  def jsonInterpolatorsSuite: Spec[Unit] = suite("JSON String Interpolators")(
    test("Path interpolator") {
      import zio.blocks.schema.json.interpolators._
      
      val path = p"user.profile.name"
      assert(path.toString == "user.profile.name")
    },

    test("JSON literal interpolator") {
      import zio.blocks.schema.json.interpolators._
      
      val json = j"""{"name": "Alice", "age": 30}"""
      assert(json.isObject)
      assert(json("name") == Json.str("Alice"))
      assert(json("age") == Json.num(30))
    },

    test("JSON literal with variables") {
      import zio.blocks.schema.json.interpolators._
      
      val name = "Alice"
      val age = 25
      val active = true
      
      val json = j"""{"name": $name, "age": $age, "active": $active}"""
      assert(json.isObject)
      assert(json("name") == Json.str("Alice"))
      assert(json("age") == Json.num(25))
      assert(json("active") == Json.bool(true))
    }
  )
}
