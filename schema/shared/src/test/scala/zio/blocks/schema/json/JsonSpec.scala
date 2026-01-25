package zio.blocks.schema.json

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.test._

object JsonSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("JsonSpec")(
    suite("Json ADT")(
      suite("type testing")(
        test("isObject returns true for objects") {
          val json = Json.Object("key" -> Json.String("value"))
          assertTrue(json.isObject, !json.isArray, !json.isString, !json.isNumber, !json.isBoolean, !json.isNull)
        },
        test("isArray returns true for arrays") {
          val json = Json.Array(new Json.Number("1"), new Json.Number("2"))
          assertTrue(json.isArray, !json.isObject, !json.isString, !json.isNumber, !json.isBoolean, !json.isNull)
        },
        test("isString returns true for strings") {
          val json = Json.String("hello")
          assertTrue(json.isString, !json.isObject, !json.isArray, !json.isNumber, !json.isBoolean, !json.isNull)
        },
        test("isNumber returns true for numbers") {
          val json = new Json.Number("42")
          assertTrue(json.isNumber, !json.isObject, !json.isArray, !json.isString, !json.isBoolean, !json.isNull)
        },
        test("isBoolean returns true for booleans") {
          val json = Json.Boolean(true)
          assertTrue(json.isBoolean, !json.isObject, !json.isArray, !json.isString, !json.isNumber, !json.isNull)
        },
        test("isNull returns true for null") {
          val json = Json.Null
          assertTrue(json.isNull, !json.isObject, !json.isArray, !json.isString, !json.isNumber, !json.isBoolean)
        }
      ),
      suite("direct accessors")(
        test("fields returns non-empty Seq for objects") {
          val json = Json.Object("a" -> new Json.Number("1"), "b" -> new Json.Number("2"))
          assertTrue(json.fields.nonEmpty, json.fields.length == 2)
        },
        test("fields returns empty Seq for non-objects") {
          assertTrue(
            Json.Array().fields.isEmpty,
            Json.String("test").fields.isEmpty,
            new Json.Number("42").fields.isEmpty,
            Json.Boolean(true).fields.isEmpty,
            Json.Null.fields.isEmpty
          )
        },
        test("elements returns non-empty Seq for arrays") {
          val json = Json.Array(new Json.Number("1"), new Json.Number("2"), new Json.Number("3"))
          assertTrue(json.elements.nonEmpty, json.elements.length == 3)
        },
        test("stringValue returns Some for strings") {
          assertTrue(Json.String("hello").stringValue.contains("hello"), new Json.Number("42").stringValue.isEmpty)
        },
        test("numberValue returns Some for numbers") {
          assertTrue(new Json.Number("42").numberValue.contains(BigDecimal(42)), Json.String("42").numberValue.isEmpty)
        },
        test("booleanValue returns Some for booleans") {
          assertTrue(
            Json.Boolean(true).booleanValue.contains(true),
            Json.Boolean(false).booleanValue.contains(false),
            new Json.Number("1").booleanValue.isEmpty
          )
        }
      ),
      suite("navigation")(
        test("works with p interpolator") {
          val j = Json.Object(
            "users" -> Json.Array(
              Json.Object("name" -> Json.String("Alice")),
              Json.Object("name" -> Json.String("Bob"))
            )
          )
          val path = p".users[1].name"
          assertTrue(j.get(path).string == Right("Bob"))
        },
        test("get retrieves field from object") {
          val json = Json.Object("name" -> Json.String("Alice"), "age" -> new Json.Number("30"))
          assertTrue(
            json.get("name").single == Right(Json.String("Alice")),
            json.get("age").single == Right(new Json.Number("30"))
          )
        },
        test("get returns error for missing field") {
          val json = Json.Object("name" -> Json.String("Alice"))
          assertTrue(json.get("missing").isFailure)
        },
        test("get(index) retrieves element from array") {
          val json: Json = Json.Array(Json.String("a"), Json.String("b"), Json.String("c"))
          assertTrue(
            json.get(0).single == Right(Json.String("a")),
            json.get(1).single == Right(Json.String("b")),
            json.get(2).single == Right(Json.String("c"))
          )
        },
        test("get(index) returns error for out of bounds") {
          val json: Json = Json.Array(new Json.Number("1"))
          assertTrue(json.get(1).isFailure, json.get(-1).isFailure)
        },
        test("chained navigation works") {
          val json: Json = Json.Object(
            "users" -> Json.Array(
              Json.Object("name" -> Json.String("Alice")),
              Json.Object("name" -> Json.String("Bob"))
            )
          )
          assertTrue(json.get("users").get(0).get("name").string == Right("Alice"))
        }
      ),
      suite("modification with DynamicOptic")(
        test("set updates existing field in object") {
          val json    = Json.Object("a" -> new Json.Number("1"))
          val path    = DynamicOptic.root.field("a")
          val updated = json.set(path, new Json.Number("99"))
          assertTrue(updated.get("a").single == Right(new Json.Number("99")))
        },
        test("set returns unchanged json if field doesn't exist") {
          val json    = Json.Object("a" -> new Json.Number("1"))
          val path    = DynamicOptic.root.field("b")
          val updated = json.set(path, new Json.Number("2"))
          // set on non-existent path returns original unchanged
          assertTrue(updated == json)
        },
        test("set updates element in array") {
          val json: Json = Json.Array(new Json.Number("1"), new Json.Number("2"), new Json.Number("3"))
          val path       = DynamicOptic.root.at(1)
          val updated    = json.set(path, new Json.Number("99"))
          assertTrue(updated.get(1).single == Right(new Json.Number("99")))
        },
        test("setOrFail fails for non-existent path") {
          val json   = Json.Object("a" -> new Json.Number("1"))
          val path   = DynamicOptic.root.field("b")
          val result = json.setOrFail(path, new Json.Number("2"))
          assertTrue(result.isLeft)
        },
        test("delete removes field from object") {
          val json    = Json.Object("a" -> new Json.Number("1"), "b" -> new Json.Number("2"))
          val path    = DynamicOptic.root.field("a")
          val updated = json.delete(path)
          assertTrue(updated.get("a").isFailure, updated.get("b").isSuccess)
        },
        test("delete removes element from array") {
          val json: Json = Json.Array(new Json.Number("1"), new Json.Number("2"), new Json.Number("3"))
          val path       = DynamicOptic.root.at(1)
          val updated    = json.delete(path)
          assertTrue(
            updated.elements.length == 2,
            updated.get(0).single == Right(new Json.Number("1")),
            updated.get(1).single == Right(new Json.Number("3"))
          )
        },
        test("deleteOrFail fails for non-existent path") {
          val json   = Json.Object("a" -> new Json.Number("1"))
          val path   = DynamicOptic.root.field("missing")
          val result = json.deleteOrFail(path)
          assertTrue(result.isLeft)
        },
        test("modify transforms value at path") {
          val json    = Json.Object("count" -> new Json.Number("5"))
          val path    = DynamicOptic.root.field("count")
          val updated = json.modify(path) {
            case Json.Number(n) => Json.Number((BigDecimal(n) * 2).toString)
            case other          => other
          }
          assertTrue(updated.get("count").number == Right(BigDecimal(10)))
        },
        test("modifyOrFail fails when partial function not defined") {
          val json   = Json.Object("name" -> Json.String("Alice"))
          val path   = DynamicOptic.root.field("name")
          val result = json.modifyOrFail(path) { case Json.Number(n) =>
            Json.Number((BigDecimal(n) * 2).toString)
          }
          assertTrue(result.isLeft)
        },
        test("insert adds new field to object") {
          val json    = Json.Object("a" -> new Json.Number("1"))
          val path    = DynamicOptic.root.field("b")
          val updated = json.insert(path, new Json.Number("2"))
          assertTrue(updated.get("b").single == Right(new Json.Number("2")))
        },
        test("insert does nothing if field already exists") {
          val json    = Json.Object("a" -> new Json.Number("1"))
          val path    = DynamicOptic.root.field("a")
          val updated = json.insert(path, new Json.Number("99"))
          assertTrue(updated.get("a").single == Right(new Json.Number("1"))) // Original value unchanged
        },
        test("insertOrFail fails if field already exists") {
          val json   = Json.Object("a" -> new Json.Number("1"))
          val path   = DynamicOptic.root.field("a")
          val result = json.insertOrFail(path, new Json.Number("99"))
          assertTrue(result.isLeft)
        },
        test("insert at array index shifts elements") {
          val json    = Json.Array(new Json.Number("1"), new Json.Number("3"))
          val path    = DynamicOptic.root.at(1)
          val updated = json.insert(path, new Json.Number("2"))
          assertTrue(updated.elements == Chunk(new Json.Number("1"), new Json.Number("2"), new Json.Number("3")))
        },
        test("nested path modification works") {
          val json = Json.Object(
            "user" -> Json.Object(
              "profile" -> Json.Object(
                "age" -> new Json.Number("25")
              )
            )
          )
          val path    = DynamicOptic.root.field("user").field("profile").field("age")
          val updated = json.set(path, new Json.Number("26"))
          assertTrue(updated.get("user").get("profile").get("age").number == Right(BigDecimal(26)))
        },
        test("get with DynamicOptic navigates nested structure") {
          val json = Json.Object(
            "users" -> Json.Array(
              Json.Object("name" -> Json.String("Alice")),
              Json.Object("name" -> Json.String("Bob"))
            )
          )
          val path = DynamicOptic.root.field("users").at(0).field("name")
          assertTrue(json.get(path).string == Right("Alice"))
        },
        test("get with elements returns all array elements") {
          val json      = Json.Array(new Json.Number("1"), new Json.Number("2"), new Json.Number("3"))
          val path      = DynamicOptic.elements
          val selection = json.get(path)
          assertTrue(selection.all == Right(Vector(new Json.Number("1"), new Json.Number("2"), new Json.Number("3"))))
        },
        test("modify with elements transforms all array elements") {
          val json    = Json.Array(new Json.Number("1"), new Json.Number("2"), new Json.Number("3"))
          val path    = DynamicOptic.elements
          val updated = json.modify(path) {
            case Json.Number(n) => Json.Number((BigDecimal(n) * 10).toString)
            case other          => other
          }
          assertTrue(updated.elements == Chunk(new Json.Number("10"), new Json.Number("20"), new Json.Number("30")))
        }
      ),
      suite("normalization")(
        test("sortKeys sorts object keys alphabetically") {
          val json   = Json.Object("c" -> new Json.Number("3"), "a" -> new Json.Number("1"), "b" -> new Json.Number("2"))
          val sorted = json.sortKeys
          assertTrue(sorted.fields.map(_._1) == Chunk("a", "b", "c"))
        },
        test("sortKeys works recursively") {
          val json = Json.Object(
            "z" -> Json.Object("b" -> new Json.Number("1"), "a" -> new Json.Number("2")),
            "a" -> new Json.Number("0")
          )
          val sorted = json.sortKeys
          sorted match {
            case Json.Object(fields) =>
              assertTrue(fields.head._1 == "a", fields(1)._1 == "z") &&
              (fields(1)._2 match {
                case Json.Object(innerFields) =>
                  assertTrue(innerFields.head._1 == "a", innerFields(1)._1 == "b")
                case _ => assertTrue(false)
              })
            case _ => assertTrue(false)
          }
        },
        test("dropNulls removes null values") {
          val json = Json.Object(
            "a" -> new Json.Number("1"),
            "b" -> Json.Null,
            "c" -> new Json.Number("3")
          )
          val dropped = json.dropNulls
          assertTrue(dropped.fields.length == 2, dropped.get("b").isFailure)
        },
        test("dropEmpty removes empty objects and arrays") {
          val json = Json.Object(
            "a" -> new Json.Number("1"),
            "b" -> Json.Object(),
            "c" -> Json.Array()
          )
          val dropped = json.dropEmpty
          assertTrue(dropped.fields.length == 1, dropped.get("a").isSuccess)
        }
      ),
      suite("merging")(
        test("merge with Auto strategy merges objects deeply") {
          val left   = Json.Object("a" -> Json.Object("x" -> new Json.Number("1")))
          val right  = Json.Object("a" -> Json.Object("y" -> new Json.Number("2")))
          val merged = left.merge(right)
          merged match {
            case Json.Object(fields) =>
              assertTrue(fields.length == 1) &&
              (fields.head._2 match {
                case Json.Object(innerFields) =>
                  assertTrue(innerFields.length == 2)
                case _ => assertTrue(false)
              })
            case _ => assertTrue(false)
          }
        },
        test("merge with Replace strategy replaces completely") {
          val left   = Json.Object("a" -> new Json.Number("1"))
          val right  = Json.Object("b" -> new Json.Number("2"))
          val merged = left.merge(right, MergeStrategy.Replace)
          assertTrue(merged == right)
        },
        test("merge arrays concatenates them") {
          val left   = Json.Array(new Json.Number("1"), new Json.Number("2"))
          val right  = Json.Array(new Json.Number("3"), new Json.Number("4"))
          val merged = left.merge(right)
          assertTrue(merged.elements.length == 4)
        }
      ),
      suite("parsing and encoding")(
        test("parse valid JSON string") {
          val result = Json.parse("""{"name": "Alice", "age": 30}""")
          assertTrue(
            result.isRight,
            result.toOption.get.get("name").string == Right("Alice"),
            result.toOption.get.get("age").number == Right(BigDecimal(30))
          )
        },
        test("parse JSON array") {
          val result = Json.parse("""[1, 2, 3]""")
          assertTrue(result.isRight, result.toOption.get.elements.length == 3)
        },
        test("parse JSON primitives") {
          assertTrue(
            Json.parse("\"hello\"").toOption.get == Json.String("hello"),
            Json.parse("42").toOption.get == new Json.Number("42"),
            Json.parse("true").toOption.get == Json.Boolean(true),
            Json.parse("false").toOption.get == Json.Boolean(false),
            Json.parse("null").toOption.get == Json.Null
          )
        },
        test("print produces valid JSON") {
          val json: Json = Json.Object(
            "name"   -> Json.String("Alice"),
            "scores" -> Json.Array(new Json.Number("100"), new Json.Number("95"))
          )
          val encoded = json.print
          assertTrue(encoded.contains("\"name\":\"Alice\"") || encoded.contains("\"name\": \"Alice\""))
        },
        test("roundtrip parsing and printing") {
          val original: Json = Json.Object(
            "string" -> Json.String("hello"),
            "number" -> new Json.Number("42.5"),
            "bool"   -> Json.Boolean(true),
            "null"   -> Json.Null,
            "array"  -> Json.Array(new Json.Number("1"), new Json.Number("2")),
            "nested" -> Json.Object("x" -> new Json.Number("1"))
          )
          val encoded = original.print
          val parsed  = Json.parse(encoded)
          assertTrue(parsed.isRight, parsed.toOption.get == original)
        }
      ),
      suite("equality and comparison")(
        test("object equality is order-independent") {
          val obj1 = Json.Object("a" -> new Json.Number("1"), "b" -> new Json.Number("2"))
          val obj2 = Json.Object("b" -> new Json.Number("2"), "a" -> new Json.Number("1"))
          assertTrue(obj1 == obj2, obj1.hashCode() == obj2.hashCode())
        },
        test("array equality is order-dependent") {
          val arr1 = Json.Array(new Json.Number("1"), new Json.Number("2"))
          val arr2 = Json.Array(new Json.Number("2"), new Json.Number("1"))
          assertTrue(arr1 != arr2)
        },
        test("compare orders by type then value") {
          assertTrue(
            Json.Null.compare(Json.Boolean(true)) < 0,
            Json.Boolean(true).compare(new Json.Number("1")) < 0,
            new Json.Number("1").compare(Json.String("a")) < 0,
            Json.String("a").compare(Json.Array()) < 0,
            Json.Array().compare(Json.Object()) < 0
          )
        }
      ),
      suite("DynamicValue conversion")(
        test("toDynamicValue converts primitives") {
          assertTrue(
            Json.Null.toDynamicValue == DynamicValue.Primitive(PrimitiveValue.Unit),
            Json.Boolean(true).toDynamicValue == DynamicValue.Primitive(PrimitiveValue.Boolean(true)),
            Json.String("hello").toDynamicValue == DynamicValue.Primitive(PrimitiveValue.String("hello"))
          )
        },
        test("toDynamicValue converts integers to Int when possible") {
          val dv = new Json.Number("42").toDynamicValue
          dv match {
            case DynamicValue.Primitive(pv: PrimitiveValue.Int) =>
              assertTrue(pv.value == 42)
            case _ => assertTrue(false)
          }
        },
        test("fromDynamicValue converts back to Json") {
          val dv = DynamicValue.Record(
            Vector(
              ("name", DynamicValue.Primitive(PrimitiveValue.String("test"))),
              ("count", DynamicValue.Primitive(PrimitiveValue.Int(10)))
            )
          )
          val json = Json.fromDynamicValue(dv)
          assertTrue(json.get("name").string == Right("test"), json.get("count").number == Right(BigDecimal(10)))
        }
      ),
      suite("transformation methods")(
        test("transformUp applies function bottom-up") {
          val json = Json.Object(
            "a" -> Json.Object("b" -> new Json.Number("1")),
            "c" -> new Json.Number("2")
          )
          // Double all numbers
          val transformed = json.transformUp { (_, j) =>
            j match {
              case Json.Number(n) => Json.Number((BigDecimal(n) * 2).toString)
              case other          => other
            }
          }
          assertTrue(
            transformed.get("a").get("b").number == Right(BigDecimal(2)),
            transformed.get("c").number == Right(BigDecimal(4))
          )
        },
        test("transformDown applies function top-down") {
          val json  = Json.Object("x" -> new Json.Number("10"))
          var order = Vector.empty[String]
          json.transformDown { (path, j) =>
            order = order :+ path.toString
            j
          }
          // Root should be visited before children (root path renders as ".")
          assertTrue(order.head == ".", order.contains(".x"))
        },
        test("transformKeys renames object keys") {
          val json        = Json.Object("old_name" -> new Json.Number("1"), "another_key" -> new Json.Number("2"))
          val transformed = json.transformKeys { (_, key) =>
            key.replace("_", "-")
          }
          assertTrue(
            transformed.get("old-name").isSuccess,
            transformed.get("another-key").isSuccess,
            transformed.get("old_name").isFailure
          )
        }
      ),
      suite("filtering methods")(
        test("filter keeps matching elements in arrays") {
          val json     = Json.Array(new Json.Number("1"), new Json.Number("2"), new Json.Number("3"), new Json.Number("4"))
          val filtered = json.filter { (_, j) =>
            j match {
              case Json.Number(n) => BigDecimal(n) > BigDecimal(2)
              case _              => true
            }
          }
          assertTrue(filtered.elements == Chunk(new Json.Number("3"), new Json.Number("4")))
        },
        test("filter keeps matching fields in objects") {
          val json     = Json.Object("a" -> new Json.Number("1"), "b" -> new Json.Number("2"), "c" -> new Json.Number("3"))
          val filtered = json.filter { (_, j) =>
            j match {
              case Json.Number(n) => BigDecimal(n) >= BigDecimal(2)
              case _              => true
            }
          }
          assertTrue(filtered.get("a").isFailure, filtered.get("b").isSuccess, filtered.get("c").isSuccess)
        },
        test("filterNot removes matching elements") {
          val json     = Json.Array(new Json.Number("1"), Json.Null, new Json.Number("2"), Json.Null)
          val filtered = json.filterNot((_, j) => j.isNull)
          assertTrue(filtered.elements == Chunk(new Json.Number("1"), new Json.Number("2")))
        },
        test("partition splits by predicate") {
          val json          = Json.Array(new Json.Number("1"), new Json.Number("2"), new Json.Number("3"), new Json.Number("4"))
          val (evens, odds) = json.partition { (_, j) =>
            j match {
              case Json.Number(n) => n.toInt % 2 == 0
              case _              => false
            }
          }
          assertTrue(
            evens.elements == Chunk(new Json.Number("2"), new Json.Number("4")),
            odds.elements == Chunk(new Json.Number("1"), new Json.Number("3"))
          )
        },
        test("project extracts specific paths") {
          val json = Json.Object(
            "user" -> Json.Object(
              "name"  -> Json.String("Alice"),
              "age"   -> new Json.Number("30"),
              "email" -> Json.String("alice@example.com")
            ),
            "extra" -> Json.String("ignored")
          )
          val namePath  = DynamicOptic.root.field("user").field("name")
          val agePath   = DynamicOptic.root.field("user").field("age")
          val projected = json.project(namePath, agePath)
          assertTrue(
            projected.get("user").get("name").string == Right("Alice"),
            projected.get("user").get("age").number == Right(BigDecimal(30)),
            projected.get("user").get("email").isFailure,
            projected.get("extra").isFailure
          )
        }
      ),
      suite("folding methods")(
        test("foldUp accumulates bottom-up") {
          val json = Json.Object(
            "a" -> new Json.Number("1"),
            "b" -> Json.Object("c" -> new Json.Number("2"), "d" -> new Json.Number("3"))
          )
          // Sum all numbers
          val sum = json.foldUp(BigDecimal(0)) { (_, j, acc) =>
            j match {
              case Json.Number(n) => acc + BigDecimal(n)
              case _              => acc
            }
          }
          assertTrue(sum == BigDecimal(6))
        },
        test("foldDown accumulates top-down") {
          val json = Json.Array(new Json.Number("1"), new Json.Number("2"), new Json.Number("3"))
          // Collect paths in order
          val paths = json.foldDown(Vector.empty[String]) { (path, _, acc) =>
            acc :+ path.toString
          }
          // Root visited first, then elements (root path renders as ".")
          assertTrue(paths.head == ".", paths.length == 4) // root + 3 elements
        },
        test("foldUpOrFail stops on error") {
          val json   = Json.Array(new Json.Number("1"), Json.String("oops"), new Json.Number("3"))
          val result = json.foldUpOrFail(BigDecimal(0)) { (_, j, acc) =>
            j match {
              case Json.Number(n) => Right(acc + BigDecimal(n))
              case Json.String(_) => Left(JsonError("Found a string!"))
              case _              => Right(acc)
            }
          }
          assertTrue(result.isLeft)
        },
        test("foldDownOrFail stops on error") {
          val json   = Json.Object("a" -> new Json.Number("1"), "b" -> Json.String("error"))
          val result = json.foldDownOrFail(0) { (_, j, acc) =>
            j match {
              case Json.String(s) if s == "error" => Left(JsonError("Found error"))
              case _                              => Right(acc + 1)
            }
          }
          assertTrue(result.isLeft)
        }
      ),
      suite("query methods")(
        test("query finds matching values") {
          val json = Json.Object(
            "users" -> Json.Array(
              Json.Object("name" -> Json.String("Alice"), "active"   -> Json.Boolean(true)),
              Json.Object("name" -> Json.String("Bob"), "active"     -> Json.Boolean(false)),
              Json.Object("name" -> Json.String("Charlie"), "active" -> Json.Boolean(true))
            )
          )
          // Find all active=true values
          val activeUsers = json.query { (_, j) =>
            j match {
              case Json.Boolean(true) => true
              case _                  => false
            }
          }
          assertTrue(activeUsers.size == 2)
        },
        test("query returns empty selection when nothing matches") {
          val json   = Json.Object("a" -> new Json.Number("1"))
          val result = json.query((_, j) => j.isString)
          assertTrue(result.isEmpty)
        },
        test("toKV converts to path-value pairs") {
          val json = Json.Object(
            "a" -> new Json.Number("1"),
            "b" -> Json.Object("c" -> new Json.Number("2"))
          )
          val kvs = json.toKV
          assertTrue(
            kvs.length == 2,
            kvs.exists(_._2 == new Json.Number("1")),
            kvs.exists(_._2 == new Json.Number("2"))
          )
        },
        test("fromKV reconstructs JSON from path-value pairs") {
          val json = Json.Object(
            "a" -> new Json.Number("1"),
            "b" -> Json.Object("c" -> new Json.Number("2"))
          )
          val kvs           = json.toKV
          val reconstructed = Json.fromKV(kvs)
          assertTrue(
            reconstructed.isRight,
            reconstructed.toOption.get.get("a").number == Right(BigDecimal(1)),
            reconstructed.toOption.get.get("b").get("c").number == Right(BigDecimal(2))
          )
        }
      ),
      suite("Json.from constructor")(
        test("from creates Json from encodable value") {
          assertTrue(
            Json.from("hello") == Json.String("hello"),
            Json.from(42) == new Json.Number("42"),
            Json.from(true) == Json.Boolean(true),
            Json.from(Vector(1, 2, 3)) == Json.Array(new Json.Number("1"), new Json.Number("2"), new Json.Number("3"))
          )
        }
      )
    ),
    suite("JsonSelection")(
      test("fluent navigation through nested structure") {
        val json: Json = Json.Object(
          "data" -> Json.Object(
            "users" -> Json.Array(
              Json.Object("name" -> Json.String("Alice"), "age" -> new Json.Number("30")),
              Json.Object("name" -> Json.String("Bob"), "age"   -> new Json.Number("25"))
            )
          )
        )
        assertTrue(
          json.get("data").get("users").get(0).get("name").string == Right("Alice"),
          json.get("data").get("users").get(1).get("age").int == Right(25)
        )
      },
      test("asObject/asArray/asString type filtering") {
        val json = Json.Object("name" -> Json.String("test"))
        // Type filtering returns empty selection instead of failure for non-matching types
        assertTrue(json.asObject.isSuccess, json.asArray.isEmpty, json.get("name").asString.isSuccess)
      },
      test("extraction methods") {
        val json = Json.Array(Json.String("a"), Json.String("b"), Json.String("c"))
        assertTrue(
          JsonSelection
            .succeed(json)
            .asArray
            .headOption
            .map(_.elements)
            .contains(Chunk(Json.String("a"), Json.String("b"), Json.String("c")))
        )
      },
      test("++ combines selections") {
        val sel1     = JsonSelection.succeed(new Json.Number("1"))
        val sel2     = JsonSelection.succeed(new Json.Number("2"))
        val combined = sel1 ++ sel2
        assertTrue(combined.all == Right(Vector(new Json.Number("1"), new Json.Number("2"))))
      },
      test("++ propagates errors") {
        val sel1      = JsonSelection.fail(JsonError("error"))
        val sel2      = JsonSelection.succeed(new Json.Number("2"))
        val combined1 = sel1 ++ sel2
        val combined2 = sel2 ++ sel1
        assertTrue(combined1.isFailure, combined2.isFailure)
      },
      test("size operations") {
        val empty    = JsonSelection.empty
        val single   = JsonSelection.succeed(new Json.Number("1"))
        val multiple =
          JsonSelection.succeedMany(Vector(new Json.Number("1"), new Json.Number("2"), new Json.Number("3")))
        assertTrue(empty.isEmpty, empty.size == 0, single.nonEmpty, single.size == 1, multiple.size == 3)
      },
      test("one returns single value or wraps multiple in array") {
        val single   = JsonSelection.succeed(new Json.Number("1"))
        val multiple = JsonSelection.succeedMany(Vector(new Json.Number("1"), new Json.Number("2")))
        assertTrue(
          single.one == Right(new Json.Number("1")),
          multiple.one == Right(Json.Array(new Json.Number("1"), new Json.Number("2")))
        )
      },
      test("first returns first value") {
        val multiple =
          JsonSelection.succeedMany(Vector(new Json.Number("1"), new Json.Number("2"), new Json.Number("3")))
        assertTrue(multiple.first == Right(new Json.Number("1")))
      },
      test("toArray wraps values in array") {
        val selection = JsonSelection.succeedMany(Vector(new Json.Number("1"), new Json.Number("2")))
        assertTrue(selection.toArray == Right(Json.Array(new Json.Number("1"), new Json.Number("2"))))
      },
      test("objects/arrays filters by type") {
        val mixed = JsonSelection.succeedMany(
          Vector(
            Json.Object("a" -> new Json.Number("1")),
            Json.Array(new Json.Number("1")),
            Json.String("hello"),
            Json.Object("b" -> new Json.Number("2"))
          )
        )
        assertTrue(mixed.objects.size == 2, mixed.arrays.size == 1)
      },
      test("stringValues/numberValues/booleanValues filters by type") {
        val mixed = JsonSelection.succeedMany(
          Vector(
            Json.String("hello"),
            new Json.Number("42"),
            Json.Boolean(true),
            Json.String("world")
          )
        )
        assertTrue(mixed.stringValues.size == 2, mixed.numberValues.size == 1, mixed.booleanValues.size == 1)
      }
    ),
    suite("JsonDecoder")(
      test("decode primitives") {
        assertTrue(
          Json.String("hello").as[String] == Right("hello"),
          new Json.Number("42").as[Int] == Right(42),
          Json.Boolean(true).as[Boolean] == Right(true)
        )
      },
      test("decode Option") {
        assertTrue(
          Json.String("hello").as[Option[String]] == Right(Some("hello")),
          Json.Null.as[Option[String]] == Right(None)
        )
      },
      test("decode Vector") {
        val json = Json.Array(new Json.Number("1"), new Json.Number("2"), new Json.Number("3"))
        assertTrue(json.as[Vector[Int]] == Right(Vector(1, 2, 3)))
      },
      test("decode Map") {
        val json = Json.Object("a" -> new Json.Number("1"), "b" -> new Json.Number("2"))
        assertTrue(json.as[Map[String, Int]] == Right(Map("a" -> 1, "b" -> 2)))
      }
    ),
    suite("JsonEncoder")(
      test("encode primitives") {
        assertTrue(
          JsonEncoder[String].encode("hello") == Json.String("hello"),
          JsonEncoder[Int].encode(42) == new Json.Number("42"),
          JsonEncoder[Boolean].encode(true) == Json.Boolean(true)
        )
      },
      test("encode Option") {
        assertTrue(
          JsonEncoder[Option[String]].encode(Some("hello")) == Json.String("hello"),
          JsonEncoder[Option[String]].encode(None) == Json.Null
        )
      },
      test("encode Vector") {
        assertTrue(
          JsonEncoder[Vector[Int]]
            .encode(Vector(1, 2, 3)) == Json.Array(new Json.Number("1"), new Json.Number("2"), new Json.Number("3"))
        )
      },
      test("encode Map") {
        val encoded = JsonEncoder[Map[String, Int]].encode(Map("a" -> 1, "b" -> 2))
        assertTrue(
          encoded.isObject,
          encoded.get("a").number == Right(BigDecimal(1)),
          encoded.get("b").number == Right(BigDecimal(2))
        )
      }
    ),
    suite("additional coverage")(
      suite("merge strategies")(
        test("merge with Deep strategy merges objects deeply") {
          val left   = Json.Object("a" -> Json.Object("x" -> new Json.Number("1"), "y" -> new Json.Number("2")))
          val right  = Json.Object("a" -> Json.Object("y" -> new Json.Number("3"), "z" -> new Json.Number("4")))
          val merged = left.merge(right, MergeStrategy.Deep)
          assertTrue(
            merged.get("a").get("x").number == Right(BigDecimal(1)),
            merged.get("a").get("y").number == Right(BigDecimal(3)),
            merged.get("a").get("z").number == Right(BigDecimal(4))
          )
        },
        test("merge with Shallow strategy replaces nested objects") {
          val left   = Json.Object("a" -> Json.Object("x" -> new Json.Number("1"), "y" -> new Json.Number("2")))
          val right  = Json.Object("a" -> Json.Object("z" -> new Json.Number("3")))
          val merged = left.merge(right, MergeStrategy.Shallow)
          assertTrue(
            merged.get("a").get("x").isFailure,
            merged.get("a").get("z").number == Right(BigDecimal(3))
          )
        },
        test("merge with Concat strategy concatenates arrays") {
          val left   = Json.Array(new Json.Number("1"), new Json.Number("2"))
          val right  = Json.Array(new Json.Number("3"), new Json.Number("4"))
          val merged = left.merge(right, MergeStrategy.Concat)
          assertTrue(
            merged.elements == Chunk(
              new Json.Number("1"),
              new Json.Number("2"),
              new Json.Number("3"),
              new Json.Number("4")
            )
          )
        },
        test("merge non-matching types replaces with right") {
          val left   = new Json.Number("1")
          val right  = Json.String("hello")
          val merged = left.merge(right)
          assertTrue(merged == right)
        }
      ),
      suite("encoding methods")(
        test("printBytes produces valid bytes") {
          val json: Json = Json.Object("key" -> Json.String("value"))
          val bytes      = json.printBytes
          val parsed     = Json.parse(bytes)
          assertTrue(parsed.isRight, parsed.toOption.get == json)
        },
        test("parse from bytes works") {
          val jsonStr = """{"name":"Alice"}"""
          val bytes   = jsonStr.getBytes("UTF-8")
          val parsed  = Json.parse(bytes)
          assertTrue(parsed.isRight, parsed.toOption.get.get("name").string == Right("Alice"))
        }
      ),
      suite("normalization")(
        test("normalize applies sortKeys, dropNulls, and dropEmpty") {
          val json = Json.Object(
            "z" -> new Json.Number("1"),
            "a" -> Json.Null,
            "m" -> Json.Object(),
            "b" -> Json.Array()
          )
          val normalized = json.normalize
          assertTrue(
            normalized.fields.length == 1,
            normalized.fields.head._1 == "z"
          )
        },
        test("dropNulls works on arrays") {
          val json    = Json.Array(new Json.Number("1"), Json.Null, new Json.Number("2"), Json.Null)
          val dropped = json.dropNulls
          assertTrue(dropped.elements == Chunk(new Json.Number("1"), new Json.Number("2")))
        },
        test("dropEmpty works recursively") {
          val json = Json.Object(
            "a" -> Json.Object("b" -> Json.Array())
          )
          val dropped = json.dropEmpty
          assertTrue(dropped.get("a").isFailure)
        }
      ),
      suite("DynamicOptic advanced operations")(
        test("get with mapValues returns all object values") {
          val json      = Json.Object("a" -> new Json.Number("1"), "b" -> new Json.Number("2"), "c" -> new Json.Number("3"))
          val path      = DynamicOptic.mapValues
          val selection = json.get(path)
          assertTrue(
            selection.all.toOption.get.toSet == Set[Json](
              new Json.Number("1"),
              new Json.Number("2"),
              new Json.Number("3")
            )
          )
        },
        test("get with mapKeys returns all object keys as strings") {
          val json      = Json.Object("a" -> new Json.Number("1"), "b" -> new Json.Number("2"))
          val path      = DynamicOptic.mapKeys
          val selection = json.get(path)
          assertTrue(selection.all.toOption.get.toSet == Set[Json](Json.String("a"), Json.String("b")))
        },
        test("modify with mapValues transforms all values") {
          val json    = Json.Object("a" -> new Json.Number("1"), "b" -> new Json.Number("2"))
          val path    = DynamicOptic.mapValues
          val updated = json.modify(path) {
            case Json.Number(n) => Json.Number((BigDecimal(n) * 10).toString)
            case other          => other
          }
          assertTrue(
            updated.get("a").number == Right(BigDecimal(10)),
            updated.get("b").number == Right(BigDecimal(20))
          )
        },
        test("get with atIndices returns multiple elements") {
          val json      = Json.Array(Json.String("a"), Json.String("b"), Json.String("c"), Json.String("d"))
          val path      = DynamicOptic.root.atIndices(0, 2)
          val selection = json.get(path)
          assertTrue(selection.all == Right(Vector(Json.String("a"), Json.String("c"))))
        },
        test("modify with atIndices transforms specific elements") {
          val json: Json =
            Json.Array(new Json.Number("1"), new Json.Number("2"), new Json.Number("3"), new Json.Number("4"))
          val path    = DynamicOptic.root.atIndices(1, 3)
          val updated = json.modify(path) {
            case Json.Number(n) => Json.Number((BigDecimal(n) * 10).toString)
            case other          => other
          }
          assertTrue(
            updated.get(0).single == Right(new Json.Number("1")),
            updated.get(1).single == Right(new Json.Number("20")),
            updated.get(2).single == Right(new Json.Number("3")),
            updated.get(3).single == Right(new Json.Number("40"))
          )
        }
      ),
      suite("toKV edge cases")(
        test("toKV handles empty object") {
          val json = Json.Object()
          val kvs  = json.toKV
          assertTrue(kvs.length == 1, kvs.head._2 == Json.Object())
        },
        test("toKV handles empty array") {
          val json = Json.Array()
          val kvs  = json.toKV
          assertTrue(kvs.length == 1, kvs.head._2 == Json.Array())
        },
        test("fromKVUnsafe works correctly") {
          val kvs = Seq(
            (DynamicOptic.root.field("a"), new Json.Number("1")),
            (DynamicOptic.root.field("b").field("c"), new Json.Number("2"))
          )
          val json = Json.fromKVUnsafe(kvs)
          assertTrue(
            json.get("a").number == Right(BigDecimal(1)),
            json.get("b").get("c").number == Right(BigDecimal(2))
          )
        },
        test("fromKV with empty seq returns Null") {
          val result = Json.fromKV(Seq.empty)
          assertTrue(result == Right(Json.Null))
        }
      ),
      suite("JsonSelection additional methods")(
        test("single returns error for empty selection") {
          val empty = JsonSelection.empty
          assertTrue(empty.single.isLeft)
        },
        test("single returns error for multiple values") {
          val multiple = JsonSelection.succeedMany(Vector(new Json.Number("1"), new Json.Number("2")))
          assertTrue(multiple.single.isLeft)
        },
        test("single returns value for single element") {
          val single = JsonSelection.succeed(new Json.Number("42"))
          assertTrue(single.single == Right(new Json.Number("42")))
        },
        test("collect extracts matching values") {
          val selection =
            JsonSelection.succeedMany(Vector(new Json.Number("1"), Json.String("a"), new Json.Number("2")))
          val numbers = selection.collect { case Json.Number(n) => n }
          assertTrue(numbers == Right(Vector("1", "2")))
        },
        test("orElse returns alternative on failure") {
          val failed   = JsonSelection.fail(JsonError("error"))
          val fallback = JsonSelection.succeed(new Json.Number("42"))
          val result   = failed.orElse(fallback)
          assertTrue(result.single == Right(new Json.Number("42")))
        },
        test("orElse returns original on success") {
          val success  = JsonSelection.succeed(new Json.Number("1"))
          val fallback = JsonSelection.succeed(new Json.Number("2"))
          val result   = success.orElse(fallback)
          assertTrue(result.single == Right(new Json.Number("1")))
        },
        test("getOrElse returns values on success") {
          val selection = JsonSelection.succeedMany(Vector(new Json.Number("1"), new Json.Number("2")))
          val result    = selection.getOrElse(Vector(Json.Null))
          assertTrue(result == Vector(new Json.Number("1"), new Json.Number("2")))
        },
        test("getOrElse returns default on failure") {
          val failed = JsonSelection.fail(JsonError("error"))
          val result = failed.getOrElse(Vector(Json.Null))
          assertTrue(result == Vector(Json.Null))
        },
        test("map transforms all values") {
          val selection = JsonSelection.succeedMany(Vector(new Json.Number("1"), new Json.Number("2")))
          val mapped    = selection.map {
            case Json.Number(n) => Json.Number((BigDecimal(n) * 2).toString)
            case other          => other
          }
          assertTrue(mapped.all == Right(Vector(new Json.Number("2"), new Json.Number("4"))))
        },
        test("flatMap chains selections") {
          val json = Json.Object(
            "users" -> Json.Array(
              Json.Object("name" -> Json.String("Alice")),
              Json.Object("name" -> Json.String("Bob"))
            )
          )
          // Get all user names using flatMap
          val path  = DynamicOptic.root.field("users").elements.field("name")
          val names = json.get(path)
          assertTrue(names.size == 2)
        },
        test("filter keeps matching values") {
          val selection =
            JsonSelection.succeedMany(Vector(new Json.Number("1"), new Json.Number("2"), new Json.Number("3")))
          val filtered = selection.filter {
            case Json.Number(n) => BigDecimal(n) > BigDecimal(1)
            case _              => false
          }
          assertTrue(filtered.all == Right(Vector(new Json.Number("2"), new Json.Number("3"))))
        },
        test("as decodes single value") {
          val selection = JsonSelection.succeed(new Json.Number("42"))
          assertTrue(selection.as[Int] == Right(42))
        },
        test("asAll decodes all values") {
          val selection =
            JsonSelection.succeedMany(Vector(new Json.Number("1"), new Json.Number("2"), new Json.Number("3")))
          assertTrue(selection.asAll[Int] == Right(Vector(1, 2, 3)))
        },
        test("long extraction works") {
          val selection = JsonSelection.succeed(new Json.Number("9876543210"))
          assertTrue(selection.long == Right(9876543210L))
        },
        test("double extraction works") {
          val selection = JsonSelection.succeed(new Json.Number("3.14159"))
          assertTrue(selection.double.isRight)
        },
        test("int extraction fails for non-int numbers") {
          val selection = JsonSelection.succeed(new Json.Number("9999999999999"))
          assertTrue(selection.int.isLeft)
        },
        test("nullValues filters to only nulls") {
          val selection = JsonSelection.succeedMany(Vector(Json.Null, new Json.Number("1"), Json.Null))
          assertTrue(selection.nullValues.size == 2)
        },
        test("one fails on empty selection") {
          val empty = JsonSelection.empty
          assertTrue(empty.one.isLeft)
        },
        test("first fails on empty selection") {
          val empty = JsonSelection.empty
          assertTrue(empty.first.isLeft)
        },
        test("error returns Some on failure") {
          val failed = JsonSelection.fail(JsonError("test error"))
          assertTrue(failed.error.isDefined, failed.error.get.message == "test error")
        },
        test("error returns None on success") {
          val success = JsonSelection.succeed(new Json.Number("1"))
          assertTrue(success.error.isEmpty)
        },
        test("values returns Some on success") {
          val success = JsonSelection.succeed(new Json.Number("1"))
          assertTrue(success.values.isDefined)
        },
        test("values returns None on failure") {
          val failed = JsonSelection.fail(JsonError("error"))
          assertTrue(failed.values.isEmpty)
        },
        test("headOption returns first value") {
          val selection = JsonSelection.succeedMany(Vector(new Json.Number("1"), new Json.Number("2")))
          assertTrue(selection.headOption.contains(new Json.Number("1")))
        },
        test("headOption returns None for empty") {
          val empty = JsonSelection.empty
          assertTrue(empty.headOption.isEmpty)
        },
        test("toVector returns empty on failure") {
          val failed = JsonSelection.fail(JsonError("error"))
          assertTrue(failed.toVector.isEmpty)
        },
        test("asNumber/asBoolean/asNull type checks") {
          val numSel  = JsonSelection.succeed(new Json.Number("42"))
          val boolSel = JsonSelection.succeed(Json.Boolean(true))
          val nullSel = JsonSelection.succeed(Json.Null)
          // Type filtering returns empty selection instead of failure for non-matching types
          assertTrue(
            numSel.asNumber.isSuccess,
            numSel.asBoolean.isEmpty,
            boolSel.asBoolean.isSuccess,
            boolSel.asNull.isEmpty,
            nullSel.asNull.isSuccess,
            nullSel.asNumber.isEmpty
          )
        }
      ),
      suite("DynamicValue conversion edge cases")(
        test("fromDynamicValue handles Variant") {
          val dv   = DynamicValue.Variant("SomeCase", DynamicValue.Primitive(PrimitiveValue.Int(42)))
          val json = Json.fromDynamicValue(dv)
          assertTrue(json.get("SomeCase").number == Right(BigDecimal(42)))
        },
        test("fromDynamicValue handles Sequence") {
          val dv = DynamicValue.Sequence(
            Vector(
              DynamicValue.Primitive(PrimitiveValue.Int(1)),
              DynamicValue.Primitive(PrimitiveValue.Int(2))
            )
          )
          val json = Json.fromDynamicValue(dv)
          assertTrue(json.elements == Chunk(new Json.Number("1"), new Json.Number("2")))
        },
        test("fromDynamicValue handles Map with string keys") {
          val dv = DynamicValue.Map(
            Vector(
              (DynamicValue.Primitive(PrimitiveValue.String("a")), DynamicValue.Primitive(PrimitiveValue.Int(1))),
              (DynamicValue.Primitive(PrimitiveValue.String("b")), DynamicValue.Primitive(PrimitiveValue.Int(2)))
            )
          )
          val json = Json.fromDynamicValue(dv)
          assertTrue(
            json.isObject,
            json.get("a").number == Right(BigDecimal(1)),
            json.get("b").number == Right(BigDecimal(2))
          )
        },
        test("fromDynamicValue handles Map with non-string keys as array of pairs") {
          val dv = DynamicValue.Map(
            Vector(
              (DynamicValue.Primitive(PrimitiveValue.Int(1)), DynamicValue.Primitive(PrimitiveValue.String("one"))),
              (DynamicValue.Primitive(PrimitiveValue.Int(2)), DynamicValue.Primitive(PrimitiveValue.String("two")))
            )
          )
          val json = Json.fromDynamicValue(dv)
          assertTrue(
            json.isArray,
            json.elements.length == 2
          )
        },
        test("toDynamicValue converts Long when out of Int range") {
          val json = new Json.Number(Long.MaxValue.toString)
          val dv   = json.toDynamicValue
          dv match {
            case DynamicValue.Primitive(pv: PrimitiveValue.Long) =>
              assertTrue(pv.value == Long.MaxValue)
            case _ => assertTrue(false)
          }
        },
        test("toDynamicValue converts BigDecimal for decimals") {
          val json = new Json.Number("123.456")
          val dv   = json.toDynamicValue
          dv match {
            case DynamicValue.Primitive(pv: PrimitiveValue.BigDecimal) =>
              assertTrue(pv.value == BigDecimal("123.456"))
            case _ => assertTrue(false)
          }
        },
        test("toDynamicValue converts arrays") {
          val json = Json.Array(new Json.Number("1"), new Json.Number("2"))
          val dv   = json.toDynamicValue
          dv match {
            case DynamicValue.Sequence(elems) =>
              assertTrue(elems.length == 2)
            case _ => assertTrue(false)
          }
        },
        test("toDynamicValue converts objects") {
          val json = Json.Object("a" -> new Json.Number("1"))
          val dv   = json.toDynamicValue
          dv match {
            case DynamicValue.Record(fields) =>
              assertTrue(fields.length == 1, fields.head._1 == "a")
            case _ => assertTrue(false)
          }
        }
      ),
      suite("JsonError with path")(
        test("atField adds field to path") {
          val error = JsonError("test").atField("foo")
          assertTrue(error.path.toString.contains("foo"))
        },
        test("atIndex adds index to path") {
          val error = JsonError("test").atIndex(5)
          assertTrue(error.path.toString.contains("5"))
        },
        test("chained path building") {
          val error   = JsonError("test").atField("users").atIndex(0).atField("name")
          val pathStr = error.path.toString
          assertTrue(
            pathStr.contains("users"),
            pathStr.contains("0"),
            pathStr.contains("name")
          )
        }
      ),
      suite("comparison edge cases")(
        test("compare same type values") {
          assertTrue(
            new Json.Number("1").compare(new Json.Number("2")) < 0,
            new Json.Number("2").compare(new Json.Number("1")) > 0,
            new Json.Number("1").compare(new Json.Number("1")) == 0,
            Json.String("a").compare(Json.String("b")) < 0,
            Json.Boolean(false).compare(Json.Boolean(true)) < 0
          )
        },
        test("compare arrays element by element") {
          assertTrue(
            Json.Array(new Json.Number("1")).compare(Json.Array(new Json.Number("2"))) < 0,
            Json.Array(new Json.Number("1"), new Json.Number("2")).compare(Json.Array(new Json.Number("1"))) > 0
          )
        },
        test("compare objects by sorted keys") {
          val obj1 = Json.Object("a" -> new Json.Number("1"))
          val obj2 = Json.Object("b" -> new Json.Number("1"))
          assertTrue(obj1.compare(obj2) < 0)
        }
      ),
      suite("constructors")(
        test("Json.True and Json.False constants") {
          assertTrue(Json.True == Json.Boolean(true), Json.False == Json.Boolean(false))
        },
        test("Json.num with different types") {
          assertTrue(
            new Json.Number("42") == Json.Number("42"),
            new Json.Number("42") == Json.Number("42"),
            new Json.Number("3.14") == Json.Number("3.14")
          )
        },
        test("Object.empty and Array.empty") {
          assertTrue(
            Json.Object.empty == Json.Object(),
            Json.Array.empty == Json.Array()
          )
        }
      ),
      suite("transformKeys edge cases")(
        test("transformKeys works on nested structures") {
          val json = Json.Object(
            "outer_key" -> Json.Object(
              "inner_key" -> new Json.Number("1")
            )
          )
          val transformed = json.transformKeys((_, k) => k.toUpperCase)
          assertTrue(
            transformed.get("OUTER_KEY").isSuccess,
            transformed.get("OUTER_KEY").get("INNER_KEY").isSuccess
          )
        },
        test("transformKeys works on arrays containing objects") {
          val json: Json = Json.Array(
            Json.Object("snake_case"  -> new Json.Number("1")),
            Json.Object("another_key" -> new Json.Number("2"))
          )
          val transformed = json.transformKeys((_, k) => k.replace("_", "-"))
          assertTrue(
            transformed.get(0).get("snake-case").isSuccess,
            transformed.get(1).get("another-key").isSuccess
          )
        }
      ),
      suite("filter/partition edge cases")(
        test("filter on primitives returns unchanged") {
          val json     = new Json.Number("42")
          val filtered = json.filter((_, _) => true)
          assertTrue(filtered == json)
        },
        test("partition on primitives") {
          val json                    = Json.String("hello")
          val (matching, nonMatching) = json.partition((_, j) => j.isString)
          assertTrue(matching == json, nonMatching == Json.Null)
        }
      ),
      suite("project edge cases")(
        test("project with empty paths returns Null") {
          val json      = Json.Object("a" -> new Json.Number("1"))
          val projected = json.project()
          assertTrue(projected == Json.Null)
        },
        test("project with non-existent paths") {
          val json      = Json.Object("a" -> new Json.Number("1"))
          val path      = DynamicOptic.root.field("nonexistent")
          val projected = json.project(path)
          assertTrue(projected == Json.Null)
        }
      ),
      suite("AtMapKey operations")(
        test("get with atKey retrieves value by key") {
          val json   = Json.Object("alice" -> new Json.Number("1"), "bob" -> new Json.Number("2"))
          val path   = DynamicOptic.root.atKey("alice")(Schema.string)
          val result = json.get(path)
          assertTrue(result.single == Right(new Json.Number("1")))
        },
        test("get with atKey returns empty for missing key") {
          val json   = Json.Object("alice" -> new Json.Number("1"))
          val path   = DynamicOptic.root.atKey("missing")(Schema.string)
          val result = json.get(path)
          assertTrue(result.toVector.isEmpty)
        },
        test("modify with atKey updates value at key") {
          val json    = Json.Object("alice" -> new Json.Number("1"), "bob" -> new Json.Number("2"))
          val path    = DynamicOptic.root.atKey("alice")(Schema.string)
          val updated = json.modify(path) {
            case Json.Number(n) => Json.Number((BigDecimal(n) * 10).toString)
            case other          => other
          }
          assertTrue(
            updated.get("alice").number == Right(BigDecimal(10)),
            updated.get("bob").number == Right(BigDecimal(2))
          )
        },
        test("modify with atKey does nothing for missing key") {
          val json    = Json.Object("alice" -> new Json.Number("1"))
          val path    = DynamicOptic.root.atKey("missing")(Schema.string)
          val updated = json.modify(path)(_ => new Json.Number("99"))
          assertTrue(updated == json)
        }
      ),
      suite("AtMapKeys operations")(
        test("get with atKeys retrieves multiple values") {
          val json   = Json.Object("a" -> new Json.Number("1"), "b" -> new Json.Number("2"), "c" -> new Json.Number("3"))
          val path   = DynamicOptic.root.atKeys("a", "c")(Schema.string)
          val result = json.get(path)
          assertTrue(result.all == Right(Vector(new Json.Number("1"), new Json.Number("3"))))
        },
        test("get with atKeys returns only existing keys") {
          val json   = Json.Object("a" -> new Json.Number("1"), "b" -> new Json.Number("2"))
          val path   = DynamicOptic.root.atKeys("a", "missing", "b")(Schema.string)
          val result = json.get(path)
          assertTrue(result.all == Right(Vector(new Json.Number("1"), new Json.Number("2"))))
        },
        test("modify with atKeys updates multiple values") {
          val json    = Json.Object("a" -> new Json.Number("1"), "b" -> new Json.Number("2"), "c" -> new Json.Number("3"))
          val path    = DynamicOptic.root.atKeys("a", "c")(Schema.string)
          val updated = json.modify(path) {
            case Json.Number(n) => Json.Number((BigDecimal(n) * 10).toString)
            case other          => other
          }
          assertTrue(
            updated.get("a").number == Right(BigDecimal(10)),
            updated.get("b").number == Right(BigDecimal(2)),
            updated.get("c").number == Right(BigDecimal(30))
          )
        },
        test("get with atKeys on non-object returns empty") {
          val json   = Json.Array(new Json.Number("1"), new Json.Number("2"))
          val path   = DynamicOptic.root.atKeys("a")(Schema.string)
          val result = json.get(path)
          assertTrue(result.toVector.isEmpty)
        }
      ),
      suite("Elements delete operations")(
        test("delete with elements removes all array elements") {
          val json    = Json.Array(new Json.Number("1"), new Json.Number("2"), new Json.Number("3"))
          val path    = DynamicOptic.elements
          val deleted = json.delete(path)
          assertTrue(deleted == Json.Array())
        },
        test("delete with elements on non-array returns unchanged") {
          val json    = Json.Object("a" -> new Json.Number("1"))
          val path    = DynamicOptic.elements
          val deleted = json.delete(path)
          assertTrue(deleted == json)
        },
        test("delete nested elements through field path") {
          val json = Json.Object(
            "items" -> Json.Array(new Json.Number("1"), new Json.Number("2"), new Json.Number("3"))
          )
          val path    = DynamicOptic.root.field("items").elements
          val deleted = json.delete(path)
          assertTrue(deleted == Json.Object("items" -> Json.Array()))
        }
      ),
      suite("Nested delete operations")(
        test("delete nested field through object path") {
          val json = Json.Object(
            "user" -> Json.Object(
              "name" -> Json.String("Alice"),
              "age"  -> new Json.Number("30")
            )
          )
          val path    = DynamicOptic.root.field("user").field("name")
          val deleted = json.delete(path)
          assertTrue(
            deleted.get("user").get("age").number == Right(BigDecimal(30)),
            deleted.get("user").get("name").toVector.isEmpty
          )
        },
        test("delete nested element through array path") {
          val json = Json.Object(
            "items" -> Json.Array(new Json.Number("1"), new Json.Number("2"), new Json.Number("3"))
          )
          val path    = DynamicOptic.root.field("items").at(1)
          val deleted = json.delete(path)
          assertTrue(
            deleted.get("items").toVector == Vector(Json.Array(new Json.Number("1"), new Json.Number("3")))
          )
        },
        test("delete deeply nested field") {
          val json = Json.Object(
            "a" -> Json.Object(
              "b" -> Json.Object(
                "c" -> new Json.Number("1"),
                "d" -> new Json.Number("2")
              )
            )
          )
          val path    = DynamicOptic.root.field("a").field("b").field("c")
          val deleted = json.delete(path)
          assertTrue(
            deleted.get("a").get("b").get("d").number == Right(BigDecimal(2)),
            deleted.get("a").get("b").get("c").toVector.isEmpty
          )
        }
      ),
      suite("modifyOrFail with Elements and MapValues")(
        test("modifyOrFail with elements succeeds when all match") {
          val json   = Json.Array(new Json.Number("1"), new Json.Number("2"), new Json.Number("3"))
          val path   = DynamicOptic.elements
          val result = json.modifyOrFail(path) { case Json.Number(n) =>
            Json.Number((BigDecimal(n) * 2).toString)
          }
          assertTrue(result == Right(Json.Array(new Json.Number("2"), new Json.Number("4"), new Json.Number("6"))))
        },
        test("modifyOrFail with elements fails when partial function not defined") {
          val json   = Json.Array(new Json.Number("1"), Json.String("not a number"), new Json.Number("3"))
          val path   = DynamicOptic.elements
          val result = json.modifyOrFail(path) { case Json.Number(n) =>
            Json.Number((BigDecimal(n) * 2).toString)
          }
          assertTrue(result.isLeft)
        },
        test("modifyOrFail with mapValues succeeds when all match") {
          val json   = Json.Object("a" -> new Json.Number("1"), "b" -> new Json.Number("2"))
          val path   = DynamicOptic.mapValues
          val result = json.modifyOrFail(path) { case Json.Number(n) =>
            Json.Number((BigDecimal(n) * 10).toString)
          }
          assertTrue(
            result.map(_.get("a").number) == Right(Right(BigDecimal(10))),
            result.map(_.get("b").number) == Right(Right(BigDecimal(20)))
          )
        },
        test("modifyOrFail with mapValues fails when partial function not defined") {
          val json   = Json.Object("a" -> new Json.Number("1"), "b" -> Json.String("not a number"))
          val path   = DynamicOptic.mapValues
          val result = json.modifyOrFail(path) { case Json.Number(n) =>
            Json.Number((BigDecimal(n) * 10).toString)
          }
          assertTrue(result.isLeft)
        },
        test("modifyOrFail with nested path and elements") {
          val json = Json.Object(
            "items" -> Json.Array(new Json.Number("1"), new Json.Number("2"))
          )
          val path   = DynamicOptic.root.field("items").elements
          val result = json.modifyOrFail(path) { case Json.Number(n) =>
            Json.Number((BigDecimal(n) + 100).toString)
          }
          assertTrue(
            result == Right(Json.Object("items" -> Json.Array(new Json.Number("101"), new Json.Number("102"))))
          )
        }
      ),
      suite("insertOrFail edge cases")(
        test("insertOrFail fails for non-existent nested path") {
          val json   = Json.Object()
          val path   = DynamicOptic.root.field("a").field("b")
          val result = json.insertOrFail(path, new Json.Number("42"))
          assertTrue(result.isLeft)
        },
        test("insertOrFail at array index extends array") {
          val json   = Json.Array(new Json.Number("1"), new Json.Number("2"))
          val path   = DynamicOptic.root.at(2)
          val result = json.insertOrFail(path, new Json.Number("3"))
          assertTrue(result == Right(Json.Array(new Json.Number("1"), new Json.Number("2"), new Json.Number("3"))))
        },
        test("insertOrFail fails when field already exists") {
          val json   = Json.Object("a" -> new Json.Number("1"))
          val path   = DynamicOptic.root.field("a")
          val result = json.insertOrFail(path, new Json.Number("99"))
          assertTrue(result.isLeft)
        },
        test("insertOrFail succeeds for new field") {
          val json   = Json.Object("a" -> new Json.Number("1"))
          val path   = DynamicOptic.root.field("b")
          val result = json.insertOrFail(path, new Json.Number("2"))
          assertTrue(result == Right(Json.Object("a" -> new Json.Number("1"), "b" -> new Json.Number("2"))))
        }
      ),
      suite("JSON ordering")(
        test("ordering sorts json values correctly") {
          val values = List(
            new Json.Number("3"),
            new Json.Number("1"),
            new Json.Number("2")
          )
          val sorted = values.sorted(Json.ordering)
          assertTrue(sorted == List(new Json.Number("1"), new Json.Number("2"), new Json.Number("3")))
        },
        test("ordering handles mixed types by type order") {
          val values = List(
            Json.Object("a" -> new Json.Number("1")),
            Json.Null,
            Json.Boolean(true),
            new Json.Number("1"),
            Json.String("hello"),
            Json.Array(new Json.Number("1"))
          )
          val sorted = values.sorted(Json.ordering)
          // Null < Boolean < Number < String < Array < Object
          assertTrue(
            sorted(0) == Json.Null,
            sorted(1) == Json.Boolean(true),
            sorted(2) == new Json.Number("1"),
            sorted(3) == Json.String("hello")
          )
        },
        test("ordering sorts strings alphabetically") {
          val values = List(Json.String("c"), Json.String("a"), Json.String("b"))
          val sorted = values.sorted(Json.ordering)
          assertTrue(sorted == List(Json.String("a"), Json.String("b"), Json.String("c")))
        }
      ),
      suite("Config-based parse and encode")(
        test("print with custom config") {
          val json   = Json.Object("a" -> new Json.Number("1"))
          val result = json.print
          assertTrue(result.contains("a") && result.contains("1"))
        },
        test("printBytes produces valid output") {
          val json: Json = Json.Object("name" -> Json.String("test"))
          val bytes      = json.printBytes
          val decoded    = Json.parse(new String(bytes, "UTF-8"))
          assertTrue(decoded == Right(json))
        },
        test("parse handles whitespace correctly") {
          val input  = """  {  "a"  :  1  }  """
          val result = Json.parse(input)
          assertTrue(result == Right(Json.Object("a" -> new Json.Number("1"))))
        },
        test("parse handles unicode escapes") {
          val input  = "{\"emoji\": \"\\u0048\\u0065\\u006c\\u006c\\u006f\"}"
          val result = Json.parse(input)
          assertTrue(result == Right(Json.Object("emoji" -> Json.String("Hello"))))
        }
      ),
      suite("JsonError as Exception")(
        test("JsonError extends Exception with NoStackTrace") {
          val error = JsonError("test message")
          assertTrue(
            error.isInstanceOf[Exception],
            error.isInstanceOf[scala.util.control.NoStackTrace],
            error.getStackTrace.isEmpty
          )
        },
        test("JsonError getMessage returns formatted string") {
          val error = JsonError("test message")
          assertTrue(error.getMessage == "test message")
        },
        test("JsonError with path is formatted correctly") {
          val error = JsonError("test message").atField("users").atIndex(0)
          assertTrue(error.getMessage.contains("users") && error.getMessage.contains("0"))
        },
        test("parseUnsafe throws JsonError directly") {
          val thrown =
            try {
              Json.parseUnsafe("invalid json")
              None
            } catch {
              case e: JsonError => Some(e)
              case _: Throwable => None
            }
          assertTrue(thrown.isDefined)
        },
        test("JsonSelection.oneUnsafe throws JsonError directly") {
          val thrown =
            try {
              JsonSelection.empty.oneUnsafe
              None
            } catch {
              case e: JsonError => Some(e)
              case _: Throwable => None
            }
          assertTrue(thrown.isDefined)
        },
        test("JsonSelection.firstUnsafe throws JsonError directly") {
          val thrown =
            try {
              JsonSelection.empty.firstUnsafe
              None
            } catch {
              case e: JsonError => Some(e)
              case _: Throwable => None
            }
          assertTrue(thrown.isDefined)
        }
      ),
      suite("JsonError additional methods")(
        test("++ combines error messages") {
          val error1   = JsonError("first error")
          val error2   = JsonError("second error")
          val combined = error1 ++ error2
          assertTrue(
            combined.message == "first error; second error",
            combined.path == error1.path
          )
        },
        test("fromSchemaError converts SchemaError to JsonError") {
          val schemaError = SchemaError.missingField(Nil, "testField")
          val jsonError   = JsonError.fromSchemaError(schemaError)
          assertTrue(
            jsonError.message.contains("testField"),
            jsonError.path == DynamicOptic.root
          )
        },
        test("apply with message and path") {
          val path  = DynamicOptic.root.field("users").at(0)
          val error = JsonError("field missing", path)
          assertTrue(
            error.message == "field missing",
            error.path == path
          )
        }
      ),
      suite("Chunk-based encoding and parsing")(
        test("printChunk produces valid Chunk[Byte]") {
          val json: Json = Json.Object("name" -> Json.String("Alice"), "age" -> new Json.Number("30"))
          val chunk      = json.printChunk
          assertTrue(chunk.length > 0)
        },
        test("parse from Chunk[Byte] works correctly") {
          val json: Json = Json.Object("key" -> Json.String("value"))
          val chunk      = json.printChunk
          val parsed     = Json.parse(chunk)
          assertTrue(parsed == Right(json))
        },
        test("roundtrip printChunk and parse(Chunk) preserves data") {
          val json: Json = Json.Object(
            "users" -> Json.Array(
              Json.Object("name" -> Json.String("Alice"), "age" -> new Json.Number("30")),
              Json.Object("name" -> Json.String("Bob"), "age"   -> new Json.Number("25"))
            ),
            "count"  -> new Json.Number("2"),
            "active" -> Json.Boolean(true)
          )
          val chunk  = json.printChunk
          val parsed = Json.parse(chunk)
          assertTrue(parsed == Right(json))
        },
        test("printChunk with custom WriterConfig") {
          val json: Json = Json.Object("a" -> new Json.Number("1"))
          val chunk      = json.printChunk(WriterConfig)
          val parsed     = Json.parse(chunk)
          assertTrue(parsed == Right(json))
        },
        test("parse from Chunk[Byte] with custom ReaderConfig") {
          val json: Json = Json.Object("a" -> new Json.Number("1"))
          val chunk      = json.printChunk
          val parsed     = Json.parse(chunk, ReaderConfig)
          assertTrue(parsed == Right(json))
        },
        test("parse works same as parse for Chunk") {
          val json: Json = Json.Object("test" -> Json.String("value"))
          val chunk      = json.printChunk
          val parsed     = Json.parse(chunk)
          assertTrue(parsed == Right(json))
        },
        test("parse with config works for Chunk") {
          val json: Json = Json.Object("test" -> Json.String("value"))
          val chunk      = json.printChunk
          val parsed     = Json.parse(chunk, ReaderConfig)
          assertTrue(parsed == Right(json))
        }
      ),
      suite("MergeStrategy.Custom")(
        test("Custom merge strategy allows user-defined logic") {
          val left           = Json.Object("a" -> new Json.Number("1"), "b" -> new Json.Number("2"))
          val right          = Json.Object("a" -> new Json.Number("10"), "c" -> new Json.Number("3"))
          val customStrategy = MergeStrategy.Custom { (_, l, r) =>
            (l, r) match {
              case (Json.Number(lv), Json.Number(rv)) => Json.Number((BigDecimal(lv) + BigDecimal(rv)).toString)
              case _                                  => r
            }
          }
          val result = left.merge(right, customStrategy)
          assertTrue(
            result.get("a").first == Right(new Json.Number("11")),
            result.get("b").first == Right(new Json.Number("2")),
            result.get("c").first == Right(new Json.Number("3"))
          )
        },
        test("Custom merge strategy receives correct path") {
          var capturedPaths  = List.empty[String]
          val left           = Json.Object("outer" -> Json.Object("inner" -> new Json.Number("1")))
          val right          = Json.Object("outer" -> Json.Object("inner" -> new Json.Number("2")))
          val customStrategy = MergeStrategy.Custom { (path, _, r) =>
            capturedPaths = capturedPaths :+ path.toString
            r
          }
          left.merge(right, customStrategy)
          assertTrue(capturedPaths.exists(_.contains("inner")))
        },
        test("Custom merge strategy falls back to user function for non-objects") {
          val left           = Json.Array(new Json.Number("1"))
          val right          = Json.Array(new Json.Number("2"))
          val customStrategy = MergeStrategy.Custom { (_, l, r) =>
            (l, r) match {
              case (Json.Array(lv), Json.Array(rv)) => Json.Array((lv ++ rv): _*)
              case _                                => r
            }
          }
          val result = left.merge(right, customStrategy)
          assertTrue(result == Json.Array(new Json.Number("1"), new Json.Number("2")))
        }
      )
    )
  )
}
