package zio.blocks.schema.json

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

object JsonSpec extends ZIOSpecDefault {
  
  def spec = suite("JsonSpec")(
    suite("constructors")(
      test("create null") {
        assertTrue(Json.Null.isNull)
      },
      test("create boolean") {
        val json = Json.Bool(true)
        assertTrue(json.isBoolean && json.asBoolean.contains(true))
      },
      test("create number") {
        val json = Json.Num(BigDecimal(42))
        assertTrue(json.isNumber && json.asNumber.contains(BigDecimal(42)))
      },
      test("create string") {
        val json = Json.Str("hello")
        assertTrue(json.isString && json.asString.contains("hello"))
      },
      test("create array") {
        val json = Json.arr(Json.Num(1), Json.Num(2), Json.Num(3))
        assertTrue(json.isArray && json.asArray.map(_.length).contains(3))
      },
      test("create object") {
        val json = Json.obj("name" -> Json.Str("Alice"), "age" -> Json.Num(30))
        assertTrue(json.isObject && json.asObject.map(_.size).contains(2))
      }
    ),
    
    suite("navigation")(
      test("navigate to field") {
        val json = Json.obj("user" -> Json.obj("name" -> Json.Str("Bob")))
        val result = json \ "user" flatMap (_ \ "name")
        assertTrue(result.contains(Json.Str("Bob")))
      },
      test("navigate to array index") {
        val json = Json.arr(Json.Num(1), Json.Num(2), Json.Num(3))
        assertTrue(json(1).contains(Json.Num(2)))
      },
      test("navigate with path") {
        val json = Json.obj("users" -> Json.arr(
          Json.obj("name" -> Json.Str("Alice")),
          Json.obj("name" -> Json.Str("Bob"))
        ))
        val path = JsonPath.root / "users" apply 1 / "name"
        assertTrue(json.at(path).contains(Json.Str("Bob")))
      }
    ),
    
    suite("manipulation")(
      test("merge objects") {
        val json1 = Json.obj("a" -> Json.Num(1), "b" -> Json.Num(2))
        val json2 = Json.obj("b" -> Json.Num(3), "c" -> Json.Num(4))
        val merged = json1.merge(json2)
        val expected = Json.obj("a" -> Json.Num(1), "b" -> Json.Num(3), "c" -> Json.Num(4))
        assertTrue(merged == expected)
      },
      test("update at path") {
        val json = Json.obj("user" -> Json.obj("name" -> Json.Str("Alice")))
        val path = JsonPath.root / "user" / "name"
        val updated = json.update(path, Json.Str("Bob"))
        val expected = Json.obj("user" -> Json.obj("name" -> Json.Str("Bob")))
        assertTrue(updated.contains(expected))
      },
      test("delete at path") {
        val json = Json.obj("a" -> Json.Num(1), "b" -> Json.Num(2))
        val path = JsonPath.root / "a"
        val deleted = json.delete(path)
        val expected = Json.obj("b" -> Json.Num(2))
        assertTrue(deleted.contains(expected))
      }
    ),
    
    suite("transformation")(
      test("map array") {
        val json = Json.arr(Json.Num(1), Json.Num(2), Json.Num(3))
        val mapped = json.mapArray {
          case Json.Num(n) => Json.Num(n * 2)
          case other => other
        }
        val expected = Json.arr(Json.Num(2), Json.Num(4), Json.Num(6))
        assertTrue(mapped == expected)
      },
      test("filter object") {
        val json = Json.obj("a" -> Json.Num(1), "b" -> Json.Num(2), "c" -> Json.Num(3))
        val filtered = json.filter {
          case Json.Num(n) => n.toInt % 2 == 0
          case _ => false
        }
        val expected = Json.obj("b" -> Json.Num(2))
        assertTrue(filtered == expected)
      },
      test("transform recursively") {
        val json = Json.obj("nums" -> Json.arr(Json.Num(1), Json.Num(2)))
        val transformed = json.transform {
          case Json.Num(n) => Json.Num(n * 10)
          case other => other
        }
        val expected = Json.obj("nums" -> Json.arr(Json.Num(10), Json.Num(20)))
        assertTrue(transformed == expected)
      }
    ),
    
    suite("serialization")(
      test("compact string") {
        val json = Json.obj("name" -> Json.Str("Alice"), "age" -> Json.Num(30))
        val compact = json.toCompactString
        assertTrue(compact == """{"age":30,"name":"Alice"}""" || 
                   compact == """{"name":"Alice","age":30}""")
      },
      test("pretty print") {
        val json = Json.obj("name" -> Json.Str("Alice"))
        val pretty = json.prettyPrint()
        assertTrue(pretty.contains("name") && pretty.contains("Alice"))
      },
      test("escape special characters") {
        val json = Json.Str("hello\nworld\t\"quoted\"")
        val compact = json.toCompactString
        assertTrue(compact.contains("\\n") && compact.contains("\\t") && compact.contains("\\\""))
      }
    ),
    
    suite("DynamicValue conversion")(
      test("to DynamicValue") {
        val json = Json.obj("name" -> Json.Str("Alice"), "age" -> Json.Num(30))
        val dv = json.toDynamicValue
        assertTrue(dv.isInstanceOf[DynamicValue.Record])
      },
      test("from DynamicValue") {
        val dv = DynamicValue.Record(Vector(
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
          "age" -> DynamicValue.Primitive(PrimitiveValue.Int(30))
        ))
        val json = Json.fromDynamicValue(dv)
        val expected = Json.obj("name" -> Json.Str("Alice"), "age" -> Json.Num(30))
        assertTrue(json == expected)
      },
      test("roundtrip") {
        val original = Json.obj(
          "name" -> Json.Str("Alice"),
          "age" -> Json.Num(30),
          "active" -> Json.Bool(true),
          "tags" -> Json.arr(Json.Str("scala"), Json.Str("zio"))
        )
        val roundtrip = Json.fromDynamicValue(original.toDynamicValue)
        assertTrue(roundtrip == original)
      }
    ),
    
    suite("JsonPath")(
      test("root path") {
        val json = Json.Num(42)
        assertTrue(JsonPath.root.navigate(json).contains(json))
      },
      test("field path") {
        val json = Json.obj("user" -> Json.Str("Alice"))
        val path = JsonPath.root / "user"
        assertTrue(path.navigate(json).contains(Json.Str("Alice")))
      },
      test("index path") {
        val json = Json.arr(Json.Num(1), Json.Num(2))
        val path = JsonPath.root(1)
        assertTrue(path.navigate(json).contains(Json.Num(2)))
      },
      test("JSON Pointer conversion") {
        val path = JsonPath.root / "user" / "name"
        assertTrue(path.toPointer == "/user/name")
      },
      test("parse JSON Pointer") {
        val result = JsonPath.fromPointer("/user/name")
        val expected = JsonPath.root / "user" / "name"
        assertTrue(result.isRight && result.toOption.contains(expected))
      },
      test("JSON Pointer with array index") {
        val result = JsonPath.fromPointer("/users/0/name")
        val expected = JsonPath.root / "users" apply 0 / "name"
        assertTrue(result.isRight && result.toOption.contains(expected))
      }
    ),
    
    suite("JsonCursor")(
      test("navigate down field") {
        val json = Json.obj("user" -> Json.obj("name" -> Json.Str("Alice")))
        val cursor = JsonCursor(json).downField("user").flatMap(_.downField("name"))
        assertTrue(cursor.map(_.focus).contains(Json.Str("Alice")))
      },
      test("navigate down index") {
        val json = Json.arr(Json.Num(1), Json.Num(2), Json.Num(3))
        val cursor = JsonCursor(json).downIndex(1)
        assertTrue(cursor.map(_.focus).contains(Json.Num(2)))
      },
      test("replace value") {
        val json = Json.obj("name" -> Json.Str("Alice"))
        val cursor = JsonCursor(json).downField("name").map(_.replace(Json.Str("Bob")))
        val expected = Json.obj("name" -> Json.Str("Bob"))
        assertTrue(cursor.map(_.top).contains(expected))
      },
      test("delete value") {
        val json = Json.obj("a" -> Json.Num(1), "b" -> Json.Num(2))
        val cursor = JsonCursor(json).downField("a").flatMap(_.delete)
        val expected = Json.obj("b" -> Json.Num(2))
        assertTrue(cursor.map(_.top).contains(expected))
      }
    ),
    
    suite("smart constructors")(
      test("fromInt") {
        val json = Json.fromInt(42)
        assertTrue(json == Json.Num(BigDecimal(42)))
      },
      test("fromString") {
        val json = Json.fromString("hello")
        assertTrue(json == Json.Str("hello"))
      },
      test("fromBoolean") {
        val json = Json.fromBoolean(true)
        assertTrue(json == Json.Bool(true))
      },
      test("fromOption with Some") {
        implicit val encoder: String => Json = Json.fromString
        val json = Json.fromOption(Some("hello"))
        assertTrue(json == Json.Str("hello"))
      },
      test("fromOption with None") {
        implicit val encoder: String => Json = Json.fromString
        val json = Json.fromOption(None)
        assertTrue(json == Json.Null)
      },
      test("fromIterable") {
        implicit val encoder: Int => Json = Json.fromInt
        val json = Json.fromIterable(List(1, 2, 3))
        val expected = Json.arr(Json.Num(1), Json.Num(2), Json.Num(3))
        assertTrue(json == expected)
      },
      test("fromMap") {
        implicit val encoder: Int => Json = Json.fromInt
        val json = Json.fromMap(Map("a" -> 1, "b" -> 2))
        assertTrue(json.isObject && json.asObject.map(_.size).contains(2))
      }
    ),
    
    suite("fold")(
      test("fold over null") {
        val result = Json.Null.fold(
          onNull = "null",
          onBool = _ => "bool",
          onNum = _ => "num",
          onStr = _ => "str",
          onArr = _ => "arr",
          onObj = _ => "obj"
        )
        assertTrue(result == "null")
      },
      test("fold over number") {
        val result = Json.Num(42).fold(
          onNull = 0,
          onBool = _ => 0,
          onNum = _.toInt,
          onStr = _ => 0,
          onArr = _ => 0,
          onObj = _ => 0
        )
        assertTrue(result == 42)
      },
      test("fold over array") {
        val result = Json.arr(Json.Num(1), Json.Num(2), Json.Num(3)).fold(
          onNull = 0,
          onBool = _ => 0,
          onNum = _ => 0,
          onStr = _ => 0,
          onArr = _.length,
          onObj = _ => 0
        )
        assertTrue(result == 3)
      }
    )
  )
}
