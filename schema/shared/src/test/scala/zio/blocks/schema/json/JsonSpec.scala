package zio.blocks.schema.json

import zio.blocks.schema.{SchemaBaseSpec, DynamicValue, DynamicOptic, PrimitiveValue}
import zio.test._
import zio.test.Assertion._

object JsonSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("JsonSpec")(
    suite("Json ADT")(
      test("create null") {
        assert(Json.Null.isNull)(isTrue) &&
        assert(Json.Null.isObject)(isFalse) &&
        assert(Json.Null.isArray)(isFalse)
      },
      test("create boolean") {
        val t = Json.Boolean(true)
        val f = Json.Boolean(false)
        assert(t.isBoolean)(isTrue) &&
        assert(t.booleanValue)(isSome(isTrue)) &&
        assert(f.booleanValue)(isSome(isFalse))
      },
      test("create number") {
        val n = Json.number(42)
        assert(n.isNumber)(isTrue) &&
        assert(n.numberValue)(isSome(equalTo("42"))) &&
        assert(n.asInstanceOf[Json.Number].toInt)(isSome(equalTo(42)))
      },
      test("create string") {
        val s = Json.String("hello")
        assert(s.isString)(isTrue) &&
        assert(s.stringValue)(isSome(equalTo("hello")))
      },
      test("create array") {
        val arr = Json.Array(Vector(Json.number(1), Json.number(2), Json.number(3)))
        assert(arr.isArray)(isTrue) &&
        assert(arr.elements.size)(equalTo(3))
      },
      test("create object") {
        val obj = Json.Object(Vector("name" -> Json.String("Alice"), "age" -> Json.number(30)))
        assert(obj.isObject)(isTrue) &&
        assert(obj.fields.size)(equalTo(2)) &&
        assert(obj.get("name"))(isSome(equalTo(Json.String("Alice"))))
      }
    ),
    suite("Navigation")(
      test("navigate object by key") {
        val obj = Json.Object("name" -> Json.String("Bob"), "age" -> Json.number(25))
        val selection = obj("name")
        assert(selection.values.size)(equalTo(1)) &&
        assert(selection.values.head)(equalTo(Json.String("Bob")))
      },
      test("navigate array by index") {
        val arr = Json.Array(Vector(Json.number(10), Json.number(20), Json.number(30)))
        val selection = arr(1)
        assert(selection.values.size)(equalTo(1)) &&
        assert(selection.values.head)(equalTo(Json.number(20)))
      },
      test("navigate with DynamicOptic - simple field") {
        val json = Json.Object("name" -> Json.String("Alice"), "age" -> Json.number(30))
        val path = DynamicOptic.root.field("name")
        val result = json.get(path)
        assert(result.values.size)(equalTo(1)) &&
        assert(result.values.head)(equalTo(Json.String("Alice")))
      },
      test("navigate with DynamicOptic - nested path") {
        val json = Json.Object(
          "user" -> Json.Object(
            "name" -> Json.String("Bob"),
            "address" -> Json.Object(
              "city" -> Json.String("NYC")
            )
          )
        )
        val path = DynamicOptic.root.field("user").field("address").field("city")
        val result = json.get(path)
        assert(result.values.size)(equalTo(1)) &&
        assert(result.values.head)(equalTo(Json.String("NYC")))
      },
      test("navigate with DynamicOptic - array index") {
        val json = Json.Object(
          "users" -> Json.Array(Vector(
            Json.Object("name" -> Json.String("Alice")),
            Json.Object("name" -> Json.String("Bob"))
          ))
        )
        val path = DynamicOptic.root.field("users").at(1).field("name")
        val result = json.get(path)
        assert(result.values.size)(equalTo(1)) &&
        assert(result.values.head)(equalTo(Json.String("Bob")))
      },
      test("navigate with DynamicOptic - elements") {
        val json = Json.Array(Vector(Json.number(1), Json.number(2), Json.number(3)))
        val path = DynamicOptic.root.elements
        val result = json.get(path)
        assert(result.values.size)(equalTo(3))
      }
    ),
    suite("Type filtering")(
      test("filter objects") {
        val json = Json.Array(Vector(
          Json.Object("a" -> Json.number(1)),
          Json.number(2),
          Json.Object("b" -> Json.number(3))
        ))
        val selection = JsonSelection(json.asInstanceOf[Json.Array].elements)
        val objects = selection.objects
        assert(objects.values.size)(equalTo(2))
      },
      test("filter numbers") {
        val json = Json.Array(Vector(Json.number(1), Json.String("two"), Json.number(3)))
        val selection = JsonSelection(json.asInstanceOf[Json.Array].elements)
        val numbers = selection.numbers
        assert(numbers.values.size)(equalTo(2))
      }
    ),
    suite("DynamicValue conversion")(
      test("convert null to DynamicValue") {
        val dv = Json.Null.toDynamicValue
        assert(dv)(equalTo(DynamicValue.Primitive(PrimitiveValue.Unit)))
      },
      test("convert boolean to DynamicValue") {
        val dv = Json.Boolean(true).toDynamicValue
        assert(dv)(equalTo(DynamicValue.Primitive(PrimitiveValue.Boolean(true))))
      },
      test("convert number to DynamicValue") {
        val dv = Json.number(42).toDynamicValue
        assert(dv)(equalTo(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(42)))))
      },
      test("convert string to DynamicValue") {
        val dv = Json.String("hello").toDynamicValue
        assert(dv)(equalTo(DynamicValue.Primitive(PrimitiveValue.String("hello"))))
      },
      test("convert array to DynamicValue") {
        val arr = Json.Array(Vector(Json.number(1), Json.number(2)))
        val dv = arr.toDynamicValue
        assert(dv)(isSubtype[DynamicValue.Sequence](anything))
      },
      test("convert object to DynamicValue") {
        val obj = Json.Object("name" -> Json.String("Alice"))
        val dv = obj.toDynamicValue
        assert(dv)(isSubtype[DynamicValue.Record](anything))
      },
      test("round-trip DynamicValue conversion") {
        val original = Json.Object(
          "name" -> Json.String("Alice"),
          "age" -> Json.number(30),
          "active" -> Json.Boolean(true)
        )
        val dv = original.toDynamicValue
        val restored = Json.fromDynamicValue(dv)
        assert(restored)(equalTo(original))
      }
    ),
    suite("Merge")(
      test("merge objects") {
        val obj1 = Json.Object("a" -> Json.number(1), "b" -> Json.number(2))
        val obj2 = Json.Object("b" -> Json.number(3), "c" -> Json.number(4))
        val merged = obj1.merge(obj2)
        val expected = Json.Object("a" -> Json.number(1), "b" -> Json.number(3), "c" -> Json.number(4))
        assert(merged)(equalTo(expected))
      },
      test("merge arrays") {
        val arr1 = Json.Array(Vector(Json.number(1), Json.number(2)))
        val arr2 = Json.Array(Vector(Json.number(3), Json.number(4)))
        val merged = arr1.merge(arr2)
        val expected = Json.Array(Vector(Json.number(1), Json.number(2), Json.number(3), Json.number(4)))
        assert(merged)(equalTo(expected))
      }
    ),
    suite("Comparison")(
      test("compare numbers") {
        assert(Json.number(1).compare(Json.number(2)))(isLessThan(0)) &&
        assert(Json.number(2).compare(Json.number(1)))(isGreaterThan(0)) &&
        assert(Json.number(1).compare(Json.number(1)))(equalTo(0))
      },
      test("compare strings") {
        assert(Json.String("a").compare(Json.String("b")))(isLessThan(0)) &&
        assert(Json.String("b").compare(Json.String("a")))(isGreaterThan(0))
      }
    ),
    suite("Transformation")(
      test("transformUp - increment all numbers") {
        val json = Json.Object(
          "a" -> Json.number(1),
          "b" -> Json.Array(Vector(Json.number(2), Json.number(3)))
        )
        val result = json.transformUp { (_, value) =>
          value match {
            case Json.Number(v) => Json.number(BigDecimal(v) + 1)
            case other => other
          }
        }
        val expected = Json.Object(
          "a" -> Json.number(2),
          "b" -> Json.Array(Vector(Json.number(3), Json.number(4)))
        )
        assert(result)(equalTo(expected))
      },
      test("transformKeys - uppercase all keys") {
        val json = Json.Object(
          "name" -> Json.String("Alice"),
          "age" -> Json.number(30)
        )
        val result = json.transformKeys((_, key) => key.toUpperCase)
        val expected = Json.Object(
          "NAME" -> Json.String("Alice"),
          "AGE" -> Json.number(30)
        )
        assert(result)(equalTo(expected))
      },
      test("filter - keep only numbers") {
        val json = Json.Object(
          "a" -> Json.number(1),
          "b" -> Json.String("text"),
          "c" -> Json.number(2)
        )
        val result = json.filter((_, value) => value.isNumber)
        val expected = Json.Object(
          "a" -> Json.number(1),
          "c" -> Json.number(2)
        )
        assert(result)(equalTo(expected))
      },
      test("filterNot - remove nulls") {
        val json = Json.Array(Vector(
          Json.number(1),
          Json.Null,
          Json.number(2),
          Json.Null
        ))
        val result = json.filterNot((_, value) => value.isNull)
        val expected = Json.Array(Vector(Json.number(1), Json.number(2)))
        assert(result)(equalTo(expected))
      }
    ),
    suite("Manipulation")(
      test("modify - update field value") {
        val json = Json.Object("name" -> Json.String("Alice"), "age" -> Json.number(30))
        val path = DynamicOptic.root.field("age")
        val result = json.modify(path, _ => Json.number(31))
        val expected = Json.Object("name" -> Json.String("Alice"), "age" -> Json.number(31))
        assert(result)(equalTo(expected))
      },
      test("modify - update array element") {
        val json = Json.Array(Vector(Json.number(1), Json.number(2), Json.number(3)))
        val path = DynamicOptic.root.at(1)
        val result = json.modify(path, v => Json.number(BigDecimal(v.numberValue.get) * 10))
        val expected = Json.Array(Vector(Json.number(1), Json.number(20), Json.number(3)))
        assert(result)(equalTo(expected))
      },
      test("set - replace value at path") {
        val json = Json.Object("user" -> Json.Object("name" -> Json.String("Alice")))
        val path = DynamicOptic.root.field("user").field("name")
        val result = json.set(path, Json.String("Bob"))
        val expected = Json.Object("user" -> Json.Object("name" -> Json.String("Bob")))
        assert(result)(equalTo(expected))
      },
      test("delete - remove object field") {
        val json = Json.Object("a" -> Json.number(1), "b" -> Json.number(2), "c" -> Json.number(3))
        val path = DynamicOptic.root.field("b")
        val result = json.delete(path)
        val expected = Json.Object("a" -> Json.number(1), "c" -> Json.number(3))
        assert(result)(equalTo(expected))
      },
      test("delete - remove array element") {
        val json = Json.Array(Vector(Json.number(1), Json.number(2), Json.number(3)))
        val path = DynamicOptic.root.at(1)
        val result = json.delete(path)
        val expected = Json.Array(Vector(Json.number(1), Json.number(3)))
        assert(result)(equalTo(expected))
      }
    ),
    suite("Parsing and Encoding")(
      test("parse simple object") {
        val input = """{"name":"Alice","age":30}"""
        val result = Json.parse(input)
        val expected = Json.Object("name" -> Json.String("Alice"), "age" -> Json.number(30))
        assert(result)(isRight(equalTo(expected)))
      },
      test("parse array") {
        val input = """[1,2,3]"""
        val result = Json.parse(input)
        val expected = Json.Array(Vector(Json.number(1), Json.number(2), Json.number(3)))
        assert(result)(isRight(equalTo(expected)))
      },
      test("parse nested structure") {
        val input = """{"user":{"name":"Bob","tags":["a","b"]}}"""
        val result = Json.parse(input)
        val expected = Json.Object(
          "user" -> Json.Object(
            "name" -> Json.String("Bob"),
            "tags" -> Json.Array(Vector(Json.String("a"), Json.String("b")))
          )
        )
        assert(result)(isRight(equalTo(expected)))
      },
      test("encode simple object") {
        val json = Json.Object("name" -> Json.String("Alice"), "age" -> Json.number(30))
        val result = Json.encode(json)
        assert(result.replaceAll("\\s", ""))(equalTo("""{"name":"Alice","age":30}"""))
      },
      test("encode and parse round-trip") {
        val original = Json.Object(
          "name" -> Json.String("Alice"),
          "age" -> Json.number(30),
          "active" -> Json.Boolean(true),
          "tags" -> Json.Array(Vector(Json.String("a"), Json.String("b")))
        )
        val encoded = Json.encode(original)
        val parsed = Json.parse(encoded)
        assert(parsed)(isRight(equalTo(original)))
      }
    ),
    suite("JsonEncoder/JsonDecoder")(
      test("encode primitive types with schema") {
        import zio.blocks.schema.Schema

        val intJson = Json.from(42)
        val stringJson = Json.from("hello")
        val boolJson = Json.from(true)

        assert(intJson)(equalTo(Json.number(42))) &&
        assert(stringJson)(equalTo(Json.String("hello"))) &&
        assert(boolJson)(equalTo(Json.Boolean(true)))
      },
      test("decode primitive types with schema") {
        import zio.blocks.schema.Schema

        val intResult = Json.number(42).as[Int]
        val stringResult = Json.String("hello").as[String]
        val boolResult = Json.Boolean(true).as[Boolean]

        assert(intResult)(isRight(equalTo(42))) &&
        assert(stringResult)(isRight(equalTo("hello"))) &&
        assert(boolResult)(isRight(equalTo(true)))
      },
      test("encode case class with schema") {
        import zio.blocks.schema.Schema

        case class Person(name: String, age: Int)
        object Person {
          implicit val schema: Schema[Person] = Schema.derived
        }

        val person = Person("Alice", 30)
        val json = Json.from(person)

        assert(json.isObject)(isTrue)
      },
      test("decode case class with schema") {
        import zio.blocks.schema.Schema

        case class Person(name: String, age: Int)
        object Person {
          implicit val schema: Schema[Person] = Schema.derived
        }

        val json = Json.Object(
          "name" -> Json.String("Bob"),
          "age" -> Json.number(25)
        )
        val result = json.as[Person]

        assert(result)(isRight(equalTo(Person("Bob", 25))))
      },
      test("round-trip encode/decode with schema") {
        import zio.blocks.schema.Schema

        case class User(id: Int, name: String, active: Boolean)
        object User {
          implicit val schema: Schema[User] = Schema.derived
        }

        val original = User(1, "Alice", true)
        val json = Json.from(original)
        val decoded = json.as[User]

        assert(decoded)(isRight(equalTo(original)))
      }
    ),
    // ===========================================================================
    // Normalization Tests
    // ===========================================================================
    suite("normalize")(
      test("sorts object keys") {
        val json = Json.Object(
          "z" -> Json.number(1),
          "a" -> Json.number(2),
          "m" -> Json.number(3)
        )
        val normalized = json.normalize
        val expected = Json.Object(
          "a" -> Json.number(2),
          "m" -> Json.number(3),
          "z" -> Json.number(1)
        )
        assert(normalized)(equalTo(expected))
      },
      test("normalizes nested objects") {
        val json = Json.Object(
          "outer" -> Json.Object(
            "z" -> Json.number(1),
            "a" -> Json.number(2)
          )
        )
        val normalized = json.normalize
        val expected = Json.Object(
          "outer" -> Json.Object(
            "a" -> Json.number(2),
            "z" -> Json.number(1)
          )
        )
        assert(normalized)(equalTo(expected))
      }
    ),
    // ===========================================================================
    // Folding Tests
    // ===========================================================================
    suite("foldDown")(
      test("accumulates values top-down") {
        val json = Json.Object(
          "a" -> Json.number(1),
          "b" -> Json.Object("c" -> Json.number(2))
        )
        val count = json.foldDown(0) { (_, _, acc) => acc + 1 }
        assert(count)(equalTo(4)) // root + a + b + c
      },
      test("provides correct paths") {
        val json = Json.Object("a" -> Json.number(1))
        val paths = json.foldDown(Vector.empty[DynamicOptic]) { (path, _, acc) =>
          acc :+ path
        }
        assert(paths.size)(equalTo(2)) // root + a
      }
    ),
    suite("foldUp")(
      test("accumulates values bottom-up") {
        val json = Json.Object(
          "a" -> Json.number(1),
          "b" -> Json.Object("c" -> Json.number(2))
        )
        val count = json.foldUp(0) { (_, _, acc) => acc + 1 }
        assert(count)(equalTo(4)) // c + b + a + root
      }
    ),
    // ===========================================================================
    // Query Tests
    // ===========================================================================
    suite("query")(
      test("finds all matching values") {
        val json = Json.Object(
          "a" -> Json.number(1),
          "b" -> Json.number(2),
          "c" -> Json.String("hello")
        )
        val numbers = json.query((_, v) => v.isNumber)
        assert(numbers.toVector)(hasSize(equalTo(2)))
      },
      test("returns empty selection when no matches") {
        val json = Json.Object("a" -> Json.String("hello"))
        val numbers = json.query((_, v) => v.isNumber)
        assert(numbers.toVector)(isEmpty)
      }
    ),
    // ===========================================================================
    // KV Tests
    // ===========================================================================
    suite("toKV")(
      test("flattens simple object") {
        val json = Json.Object(
          "a" -> Json.number(1),
          "b" -> Json.String("hello")
        )
        val kvs = json.toKV
        assert(kvs.size)(equalTo(2))
      },
      test("flattens nested structure") {
        val json = Json.Object(
          "a" -> Json.Object("b" -> Json.number(1)),
          "c" -> Json.Array(Json.number(2), Json.number(3))
        )
        val kvs = json.toKV
        assert(kvs.size)(equalTo(3)) // a.b, c[0], c[1]
      },
      test("includes empty arrays and objects as leaves") {
        val json = Json.Object(
          "empty_obj" -> Json.Object.empty,
          "empty_arr" -> Json.Array.empty
        )
        val kvs = json.toKV
        assert(kvs.size)(equalTo(2))
      }
    ),
    suite("fromKV")(
      test("assembles simple object") {
        val kvs = Seq(
          DynamicOptic.root.field("a") -> Json.number(1),
          DynamicOptic.root.field("b") -> Json.String("hello")
        )
        val result = Json.fromKV(kvs)
        assert(result)(isRight(anything))
      },
      test("assembles nested structure") {
        val kvs = Seq(
          DynamicOptic.root.field("a").field("b") -> Json.number(1),
          DynamicOptic.root.field("c").at(0) -> Json.number(2)
        )
        val result = Json.fromKV(kvs)
        assert(result)(isRight(anything))
      },
      test("roundtrips with toKV") {
        val original = Json.Object(
          "a" -> Json.Object("b" -> Json.number(1)),
          "c" -> Json.Array(Json.number(2), Json.number(3))
        )
        val kvs = original.toKV
        val result = Json.fromKV(kvs)
        assert(result)(isRight(equalTo(original)))
      }
    )
  )
}

