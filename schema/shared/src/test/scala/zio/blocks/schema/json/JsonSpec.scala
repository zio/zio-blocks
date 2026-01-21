package zio.blocks.schema.json

import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue, Schema, SchemaBaseSpec, SchemaError}
import zio.test._

object JsonSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("JsonSpec")(
    suite("Json ADT")(
      suite("type testing")(
        test("isObject returns true for objects") {
          val json = Json.obj("key" -> Json.str("value"))
          assertTrue(json.isObject, !json.isArray, !json.isString, !json.isNumber, !json.isBoolean, !json.isNull)
        },
        test("isArray returns true for arrays") {
          val json = Json.arr(Json.number(1), Json.number(2))
          assertTrue(json.isArray, !json.isObject, !json.isString, !json.isNumber, !json.isBoolean, !json.isNull)
        },
        test("isString returns true for strings") {
          val json = Json.str("hello")
          assertTrue(json.isString, !json.isObject, !json.isArray, !json.isNumber, !json.isBoolean, !json.isNull)
        },
        test("isNumber returns true for numbers") {
          val json = Json.number(42)
          assertTrue(json.isNumber, !json.isObject, !json.isArray, !json.isString, !json.isBoolean, !json.isNull)
        },
        test("isBoolean returns true for booleans") {
          val json = Json.bool(true)
          assertTrue(json.isBoolean, !json.isObject, !json.isArray, !json.isString, !json.isNumber, !json.isNull)
        },
        test("isNull returns true for null") {
          val json = Json.Null
          assertTrue(json.isNull, !json.isObject, !json.isArray, !json.isString, !json.isNumber, !json.isBoolean)
        }
      ),
      suite("direct accessors")(
        test("fields returns non-empty Seq for objects") {
          val json = Json.obj("a" -> Json.number(1), "b" -> Json.number(2))
          assertTrue(json.fields.nonEmpty, json.fields.length == 2)
        },
        test("fields returns empty Seq for non-objects") {
          assertTrue(
            Json.arr().fields.isEmpty,
            Json.str("test").fields.isEmpty,
            Json.number(42).fields.isEmpty,
            Json.bool(true).fields.isEmpty,
            Json.Null.fields.isEmpty
          )
        },
        test("elements returns non-empty Seq for arrays") {
          val json = Json.arr(Json.number(1), Json.number(2), Json.number(3))
          assertTrue(json.elements.nonEmpty, json.elements.length == 3)
        },
        test("stringValue returns Some for strings") {
          assertTrue(Json.str("hello").stringValue.contains("hello"), Json.number(42).stringValue.isEmpty)
        },
        test("numberValue returns Some for numbers") {
          assertTrue(Json.number(42).numberValue.contains(BigDecimal(42)), Json.str("42").numberValue.isEmpty)
        },
        test("booleanValue returns Some for booleans") {
          assertTrue(
            Json.bool(true).booleanValue.contains(true),
            Json.bool(false).booleanValue.contains(false),
            Json.number(1).booleanValue.isEmpty
          )
        }
      ),
      suite("navigation")(
        test("get retrieves field from object") {
          val json = Json.obj("name" -> Json.str("Alice"), "age" -> Json.number(30))
          assertTrue(
            json.get("name").single == Right(Json.str("Alice")),
            json.get("age").single == Right(Json.number(30))
          )
        },
        test("get returns error for missing field") {
          val json = Json.obj("name" -> Json.str("Alice"))
          assertTrue(json.get("missing").isFailure)
        },
        test("apply(index) retrieves element from array") {
          val json = Json.arr(Json.str("a"), Json.str("b"), Json.str("c"))
          assertTrue(
            json(0).single == Right(Json.str("a")),
            json(1).single == Right(Json.str("b")),
            json(2).single == Right(Json.str("c"))
          )
        },
        test("apply(index) returns error for out of bounds") {
          val json = Json.arr(Json.number(1))
          assertTrue(json(1).isFailure, json(-1).isFailure)
        },
        test("chained navigation works") {
          val json = Json.obj(
            "users" -> Json.arr(
              Json.obj("name" -> Json.str("Alice")),
              Json.obj("name" -> Json.str("Bob"))
            )
          )
          assertTrue(json.get("users")(0).get("name").string == Right("Alice"))
        }
      ),
      suite("modification with DynamicOptic")(
        test("set updates existing field in object") {
          val json    = Json.obj("a" -> Json.number(1))
          val path    = DynamicOptic.root.field("a")
          val updated = json.set(path, Json.number(99))
          assertTrue(updated.get("a").single == Right(Json.number(99)))
        },
        test("set returns unchanged json if field doesn't exist") {
          val json    = Json.obj("a" -> Json.number(1))
          val path    = DynamicOptic.root.field("b")
          val updated = json.set(path, Json.number(2))
          // set on non-existent path returns original unchanged
          assertTrue(updated == json)
        },
        test("set updates element in array") {
          val json    = Json.arr(Json.number(1), Json.number(2), Json.number(3))
          val path    = DynamicOptic.root.at(1)
          val updated = json.set(path, Json.number(99))
          assertTrue(updated(1).single == Right(Json.number(99)))
        },
        test("setOrFail fails for non-existent path") {
          val json   = Json.obj("a" -> Json.number(1))
          val path   = DynamicOptic.root.field("b")
          val result = json.setOrFail(path, Json.number(2))
          assertTrue(result.isLeft)
        },
        test("delete removes field from object") {
          val json    = Json.obj("a" -> Json.number(1), "b" -> Json.number(2))
          val path    = DynamicOptic.root.field("a")
          val updated = json.delete(path)
          assertTrue(updated.get("a").isFailure, updated.get("b").isSuccess)
        },
        test("delete removes element from array") {
          val json    = Json.arr(Json.number(1), Json.number(2), Json.number(3))
          val path    = DynamicOptic.root.at(1)
          val updated = json.delete(path)
          assertTrue(
            updated.elements.length == 2,
            updated(0).single == Right(Json.number(1)),
            updated(1).single == Right(Json.number(3))
          )
        },
        test("deleteOrFail fails for non-existent path") {
          val json   = Json.obj("a" -> Json.number(1))
          val path   = DynamicOptic.root.field("missing")
          val result = json.deleteOrFail(path)
          assertTrue(result.isLeft)
        },
        test("modify transforms value at path") {
          val json    = Json.obj("count" -> Json.number(5))
          val path    = DynamicOptic.root.field("count")
          val updated = json.modify(path) {
            case Json.Number(n) => Json.Number((BigDecimal(n) * 2).toString)
            case other          => other
          }
          assertTrue(updated.get("count").number == Right(BigDecimal(10)))
        },
        test("modifyOrFail fails when partial function not defined") {
          val json   = Json.obj("name" -> Json.str("Alice"))
          val path   = DynamicOptic.root.field("name")
          val result = json.modifyOrFail(path) { case Json.Number(n) =>
            Json.Number((BigDecimal(n) * 2).toString)
          }
          assertTrue(result.isLeft)
        },
        test("insert adds new field to object") {
          val json    = Json.obj("a" -> Json.number(1))
          val path    = DynamicOptic.root.field("b")
          val updated = json.insert(path, Json.number(2))
          assertTrue(updated.get("b").single == Right(Json.number(2)))
        },
        test("insert does nothing if field already exists") {
          val json    = Json.obj("a" -> Json.number(1))
          val path    = DynamicOptic.root.field("a")
          val updated = json.insert(path, Json.number(99))
          assertTrue(updated.get("a").single == Right(Json.number(1))) // Original value unchanged
        },
        test("insertOrFail fails if field already exists") {
          val json   = Json.obj("a" -> Json.number(1))
          val path   = DynamicOptic.root.field("a")
          val result = json.insertOrFail(path, Json.number(99))
          assertTrue(result.isLeft)
        },
        test("insert at array index shifts elements") {
          val json    = Json.arr(Json.number(1), Json.number(3))
          val path    = DynamicOptic.root.at(1)
          val updated = json.insert(path, Json.number(2))
          assertTrue(updated.elements == Vector(Json.number(1), Json.number(2), Json.number(3)))
        },
        test("nested path modification works") {
          val json = Json.obj(
            "user" -> Json.obj(
              "profile" -> Json.obj(
                "age" -> Json.number(25)
              )
            )
          )
          val path    = DynamicOptic.root.field("user").field("profile").field("age")
          val updated = json.set(path, Json.number(26))
          assertTrue(updated.get("user").get("profile").get("age").number == Right(BigDecimal(26)))
        },
        test("get with DynamicOptic navigates nested structure") {
          val json = Json.obj(
            "users" -> Json.arr(
              Json.obj("name" -> Json.str("Alice")),
              Json.obj("name" -> Json.str("Bob"))
            )
          )
          val path = DynamicOptic.root.field("users").at(0).field("name")
          assertTrue(json.get(path).string == Right("Alice"))
        },
        test("get with elements returns all array elements") {
          val json      = Json.arr(Json.number(1), Json.number(2), Json.number(3))
          val path      = DynamicOptic.elements
          val selection = json.get(path)
          assertTrue(selection.all == Right(Vector(Json.number(1), Json.number(2), Json.number(3))))
        },
        test("modify with elements transforms all array elements") {
          val json    = Json.arr(Json.number(1), Json.number(2), Json.number(3))
          val path    = DynamicOptic.elements
          val updated = json.modify(path) {
            case Json.Number(n) => Json.Number((BigDecimal(n) * 10).toString)
            case other          => other
          }
          assertTrue(updated.elements == Vector(Json.number(10), Json.number(20), Json.number(30)))
        }
      ),
      suite("normalization")(
        test("sortKeys sorts object keys alphabetically") {
          val json   = Json.obj("c" -> Json.number(3), "a" -> Json.number(1), "b" -> Json.number(2))
          val sorted = json.sortKeys
          assertTrue(sorted.fields.map(_._1) == Vector("a", "b", "c"))
        },
        test("sortKeys works recursively") {
          val json = Json.obj(
            "z" -> Json.obj("b" -> Json.number(1), "a" -> Json.number(2)),
            "a" -> Json.number(0)
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
          val json = Json.obj(
            "a" -> Json.number(1),
            "b" -> Json.Null,
            "c" -> Json.number(3)
          )
          val dropped = json.dropNulls
          assertTrue(dropped.fields.length == 2, dropped.get("b").isFailure)
        },
        test("dropEmpty removes empty objects and arrays") {
          val json = Json.obj(
            "a" -> Json.number(1),
            "b" -> Json.obj(),
            "c" -> Json.arr()
          )
          val dropped = json.dropEmpty
          assertTrue(dropped.fields.length == 1, dropped.get("a").isSuccess)
        }
      ),
      suite("merging")(
        test("merge with Auto strategy merges objects deeply") {
          val left   = Json.obj("a" -> Json.obj("x" -> Json.number(1)))
          val right  = Json.obj("a" -> Json.obj("y" -> Json.number(2)))
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
          val left   = Json.obj("a" -> Json.number(1))
          val right  = Json.obj("b" -> Json.number(2))
          val merged = left.merge(right, MergeStrategy.Replace)
          assertTrue(merged == right)
        },
        test("merge arrays concatenates them") {
          val left   = Json.arr(Json.number(1), Json.number(2))
          val right  = Json.arr(Json.number(3), Json.number(4))
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
            Json.parse("\"hello\"").toOption.get == Json.str("hello"),
            Json.parse("42").toOption.get == Json.number(42),
            Json.parse("true").toOption.get == Json.bool(true),
            Json.parse("false").toOption.get == Json.bool(false),
            Json.parse("null").toOption.get == Json.Null
          )
        },
        test("encode produces valid JSON") {
          val json = Json.obj(
            "name"   -> Json.str("Alice"),
            "scores" -> Json.arr(Json.number(100), Json.number(95))
          )
          val encoded = json.encode
          assertTrue(encoded.contains("\"name\":\"Alice\"") || encoded.contains("\"name\": \"Alice\""))
        },
        test("roundtrip parsing and encoding") {
          val original = Json.obj(
            "string" -> Json.str("hello"),
            "number" -> Json.number(42.5),
            "bool"   -> Json.bool(true),
            "null"   -> Json.Null,
            "array"  -> Json.arr(Json.number(1), Json.number(2)),
            "nested" -> Json.obj("x" -> Json.number(1))
          )
          val encoded = original.encode
          val parsed  = Json.parse(encoded)
          assertTrue(parsed.isRight, parsed.toOption.get == original)
        }
      ),
      suite("equality and comparison")(
        test("object equality is order-independent") {
          val obj1 = Json.obj("a" -> Json.number(1), "b" -> Json.number(2))
          val obj2 = Json.obj("b" -> Json.number(2), "a" -> Json.number(1))
          assertTrue(obj1 == obj2, obj1.hashCode() == obj2.hashCode())
        },
        test("array equality is order-dependent") {
          val arr1 = Json.arr(Json.number(1), Json.number(2))
          val arr2 = Json.arr(Json.number(2), Json.number(1))
          assertTrue(arr1 != arr2)
        },
        test("compare orders by type then value") {
          assertTrue(
            Json.Null.compare(Json.bool(true)) < 0,
            Json.bool(true).compare(Json.number(1)) < 0,
            Json.number(1).compare(Json.str("a")) < 0,
            Json.str("a").compare(Json.arr()) < 0,
            Json.arr().compare(Json.obj()) < 0
          )
        }
      ),
      suite("DynamicValue conversion")(
        test("toDynamicValue converts primitives") {
          assertTrue(
            Json.Null.toDynamicValue == DynamicValue.Primitive(PrimitiveValue.Unit),
            Json.bool(true).toDynamicValue == DynamicValue.Primitive(PrimitiveValue.Boolean(true)),
            Json.str("hello").toDynamicValue == DynamicValue.Primitive(PrimitiveValue.String("hello"))
          )
        },
        test("toDynamicValue converts integers to Int when possible") {
          val dv = Json.number(42).toDynamicValue
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
          val json = Json.obj(
            "a" -> Json.obj("b" -> Json.number(1)),
            "c" -> Json.number(2)
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
          val json  = Json.obj("x" -> Json.number(10))
          var order = Vector.empty[String]
          json.transformDown { (path, j) =>
            order = order :+ path.toString
            j
          }
          // Root should be visited before children (root path renders as ".")
          assertTrue(order.head == ".", order.contains(".x"))
        },
        test("transformKeys renames object keys") {
          val json        = Json.obj("old_name" -> Json.number(1), "another_key" -> Json.number(2))
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
          val json     = Json.arr(Json.number(1), Json.number(2), Json.number(3), Json.number(4))
          val filtered = json.filter { (_, j) =>
            j match {
              case Json.Number(n) => BigDecimal(n) > BigDecimal(2)
              case _              => true
            }
          }
          assertTrue(filtered.elements == Vector(Json.number(3), Json.number(4)))
        },
        test("filter keeps matching fields in objects") {
          val json     = Json.obj("a" -> Json.number(1), "b" -> Json.number(2), "c" -> Json.number(3))
          val filtered = json.filter { (_, j) =>
            j match {
              case Json.Number(n) => BigDecimal(n) >= BigDecimal(2)
              case _              => true
            }
          }
          assertTrue(filtered.get("a").isFailure, filtered.get("b").isSuccess, filtered.get("c").isSuccess)
        },
        test("filterNot removes matching elements") {
          val json     = Json.arr(Json.number(1), Json.Null, Json.number(2), Json.Null)
          val filtered = json.filterNot((_, j) => j.isNull)
          assertTrue(filtered.elements == Vector(Json.number(1), Json.number(2)))
        },
        test("partition splits by predicate") {
          val json          = Json.arr(Json.number(1), Json.number(2), Json.number(3), Json.number(4))
          val (evens, odds) = json.partition { (_, j) =>
            j match {
              case Json.Number(n) => n.toInt % 2 == 0
              case _              => false
            }
          }
          assertTrue(
            evens.elements == Vector(Json.number(2), Json.number(4)),
            odds.elements == Vector(Json.number(1), Json.number(3))
          )
        },
        test("project extracts specific paths") {
          val json = Json.obj(
            "user" -> Json.obj(
              "name"  -> Json.str("Alice"),
              "age"   -> Json.number(30),
              "email" -> Json.str("alice@example.com")
            ),
            "extra" -> Json.str("ignored")
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
          val json = Json.obj(
            "a" -> Json.number(1),
            "b" -> Json.obj("c" -> Json.number(2), "d" -> Json.number(3))
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
          val json = Json.arr(Json.number(1), Json.number(2), Json.number(3))
          // Collect paths in order
          val paths = json.foldDown(Vector.empty[String]) { (path, _, acc) =>
            acc :+ path.toString
          }
          // Root visited first, then elements (root path renders as ".")
          assertTrue(paths.head == ".", paths.length == 4) // root + 3 elements
        },
        test("foldUpOrFail stops on error") {
          val json   = Json.arr(Json.number(1), Json.str("oops"), Json.number(3))
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
          val json   = Json.obj("a" -> Json.number(1), "b" -> Json.str("error"))
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
          val json = Json.obj(
            "users" -> Json.arr(
              Json.obj("name" -> Json.str("Alice"), "active"   -> Json.bool(true)),
              Json.obj("name" -> Json.str("Bob"), "active"     -> Json.bool(false)),
              Json.obj("name" -> Json.str("Charlie"), "active" -> Json.bool(true))
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
          val json   = Json.obj("a" -> Json.number(1))
          val result = json.query((_, j) => j.isString)
          assertTrue(result.isEmpty)
        },
        test("toKV converts to path-value pairs") {
          val json = Json.obj(
            "a" -> Json.number(1),
            "b" -> Json.obj("c" -> Json.number(2))
          )
          val kvs = json.toKV
          assertTrue(kvs.length == 2, kvs.exists(_._2 == Json.number(1)), kvs.exists(_._2 == Json.number(2)))
        },
        test("fromKV reconstructs JSON from path-value pairs") {
          val json = Json.obj(
            "a" -> Json.number(1),
            "b" -> Json.obj("c" -> Json.number(2))
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
            Json.from("hello") == Json.str("hello"),
            Json.from(42) == Json.number(42),
            Json.from(true) == Json.bool(true),
            Json.from(Vector(1, 2, 3)) == Json.arr(Json.number(1), Json.number(2), Json.number(3))
          )
        }
      )
    ),
    suite("JsonSelection")(
      test("fluent navigation through nested structure") {
        val json = Json.obj(
          "data" -> Json.obj(
            "users" -> Json.arr(
              Json.obj("name" -> Json.str("Alice"), "age" -> Json.number(30)),
              Json.obj("name" -> Json.str("Bob"), "age"   -> Json.number(25))
            )
          )
        )
        assertTrue(
          json.get("data").get("users")(0).get("name").string == Right("Alice"),
          json.get("data").get("users")(1).get("age").int == Right(25)
        )
      },
      test("asObject/asArray/asString type filtering") {
        val json = Json.obj("name" -> Json.str("test"))
        // Type filtering returns empty selection instead of failure for non-matching types
        assertTrue(json.asObject.isSuccess, json.asArray.isEmpty, json.get("name").asString.isSuccess)
      },
      test("extraction methods") {
        val json = Json.arr(Json.str("a"), Json.str("b"), Json.str("c"))
        assertTrue(
          JsonSelection
            .succeed(json)
            .asArray
            .headOption
            .map(_.elements)
            .contains(Vector(Json.str("a"), Json.str("b"), Json.str("c")))
        )
      },
      test("++ combines selections") {
        val sel1     = JsonSelection.succeed(Json.number(1))
        val sel2     = JsonSelection.succeed(Json.number(2))
        val combined = sel1 ++ sel2
        assertTrue(combined.all == Right(Vector(Json.number(1), Json.number(2))))
      },
      test("++ propagates errors") {
        val sel1      = JsonSelection.fail(JsonError("error"))
        val sel2      = JsonSelection.succeed(Json.number(2))
        val combined1 = sel1 ++ sel2
        val combined2 = sel2 ++ sel1
        assertTrue(combined1.isFailure, combined2.isFailure)
      },
      test("size operations") {
        val empty    = JsonSelection.empty
        val single   = JsonSelection.succeed(Json.number(1))
        val multiple = JsonSelection.succeedMany(Vector(Json.number(1), Json.number(2), Json.number(3)))
        assertTrue(empty.isEmpty, empty.size == 0, single.nonEmpty, single.size == 1, multiple.size == 3)
      },
      test("one returns single value or wraps multiple in array") {
        val single   = JsonSelection.succeed(Json.number(1))
        val multiple = JsonSelection.succeedMany(Vector(Json.number(1), Json.number(2)))
        assertTrue(single.one == Right(Json.number(1)), multiple.one == Right(Json.arr(Json.number(1), Json.number(2))))
      },
      test("first returns first value") {
        val multiple = JsonSelection.succeedMany(Vector(Json.number(1), Json.number(2), Json.number(3)))
        assertTrue(multiple.first == Right(Json.number(1)))
      },
      test("toArray wraps values in array") {
        val selection = JsonSelection.succeedMany(Vector(Json.number(1), Json.number(2)))
        assertTrue(selection.toArray == Right(Json.arr(Json.number(1), Json.number(2))))
      },
      test("objects/arrays filters by type") {
        val mixed = JsonSelection.succeedMany(
          Vector(
            Json.obj("a" -> Json.number(1)),
            Json.arr(Json.number(1)),
            Json.str("hello"),
            Json.obj("b" -> Json.number(2))
          )
        )
        assertTrue(mixed.objects.size == 2, mixed.arrays.size == 1)
      },
      test("stringValues/numberValues/booleanValues filters by type") {
        val mixed = JsonSelection.succeedMany(
          Vector(
            Json.str("hello"),
            Json.number(42),
            Json.bool(true),
            Json.str("world")
          )
        )
        assertTrue(mixed.stringValues.size == 2, mixed.numberValues.size == 1, mixed.booleanValues.size == 1)
      }
    ),
    suite("JsonDecoder")(
      test("decode primitives") {
        assertTrue(
          Json.str("hello").as[String] == Right("hello"),
          Json.number(42).as[Int] == Right(42),
          Json.bool(true).as[Boolean] == Right(true)
        )
      },
      test("decode Option") {
        assertTrue(
          Json.str("hello").as[Option[String]] == Right(Some("hello")),
          Json.Null.as[Option[String]] == Right(None)
        )
      },
      test("decode Vector") {
        val json = Json.arr(Json.number(1), Json.number(2), Json.number(3))
        assertTrue(json.as[Vector[Int]] == Right(Vector(1, 2, 3)))
      },
      test("decode Map") {
        val json = Json.obj("a" -> Json.number(1), "b" -> Json.number(2))
        assertTrue(json.as[Map[String, Int]] == Right(Map("a" -> 1, "b" -> 2)))
      }
    ),
    suite("JsonEncoder")(
      test("encode primitives") {
        assertTrue(
          JsonEncoder[String].encode("hello") == Json.str("hello"),
          JsonEncoder[Int].encode(42) == Json.number(42),
          JsonEncoder[Boolean].encode(true) == Json.bool(true)
        )
      },
      test("encode Option") {
        assertTrue(
          JsonEncoder[Option[String]].encode(Some("hello")) == Json.str("hello"),
          JsonEncoder[Option[String]].encode(None) == Json.Null
        )
      },
      test("encode Vector") {
        assertTrue(
          JsonEncoder[Vector[Int]].encode(Vector(1, 2, 3)) == Json.arr(Json.number(1), Json.number(2), Json.number(3))
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
          val left   = Json.obj("a" -> Json.obj("x" -> Json.number(1), "y" -> Json.number(2)))
          val right  = Json.obj("a" -> Json.obj("y" -> Json.number(3), "z" -> Json.number(4)))
          val merged = left.merge(right, MergeStrategy.Deep)
          assertTrue(
            merged.get("a").get("x").number == Right(BigDecimal(1)),
            merged.get("a").get("y").number == Right(BigDecimal(3)),
            merged.get("a").get("z").number == Right(BigDecimal(4))
          )
        },
        test("merge with Shallow strategy replaces nested objects") {
          val left   = Json.obj("a" -> Json.obj("x" -> Json.number(1), "y" -> Json.number(2)))
          val right  = Json.obj("a" -> Json.obj("z" -> Json.number(3)))
          val merged = left.merge(right, MergeStrategy.Shallow)
          assertTrue(
            merged.get("a").get("x").isFailure,
            merged.get("a").get("z").number == Right(BigDecimal(3))
          )
        },
        test("merge with Concat strategy concatenates arrays") {
          val left   = Json.arr(Json.number(1), Json.number(2))
          val right  = Json.arr(Json.number(3), Json.number(4))
          val merged = left.merge(right, MergeStrategy.Concat)
          assertTrue(merged.elements == Vector(Json.number(1), Json.number(2), Json.number(3), Json.number(4)))
        },
        test("merge non-matching types replaces with right") {
          val left   = Json.number(1)
          val right  = Json.str("hello")
          val merged = left.merge(right)
          assertTrue(merged == right)
        }
      ),
      suite("encoding methods")(
        test("encodeToBytes produces valid bytes") {
          val json   = Json.obj("key" -> Json.str("value"))
          val bytes  = json.encodeToBytes
          val parsed = Json.parse(bytes)
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
          val json = Json.obj(
            "z" -> Json.number(1),
            "a" -> Json.Null,
            "m" -> Json.obj(),
            "b" -> Json.arr()
          )
          val normalized = json.normalize
          assertTrue(
            normalized.fields.length == 1,
            normalized.fields.head._1 == "z"
          )
        },
        test("dropNulls works on arrays") {
          val json    = Json.arr(Json.number(1), Json.Null, Json.number(2), Json.Null)
          val dropped = json.dropNulls
          assertTrue(dropped.elements == Vector(Json.number(1), Json.number(2)))
        },
        test("dropEmpty works recursively") {
          val json = Json.obj(
            "a" -> Json.obj("b" -> Json.arr())
          )
          val dropped = json.dropEmpty
          assertTrue(dropped.get("a").isFailure)
        }
      ),
      suite("DynamicOptic advanced operations")(
        test("get with mapValues returns all object values") {
          val json      = Json.obj("a" -> Json.number(1), "b" -> Json.number(2), "c" -> Json.number(3))
          val path      = DynamicOptic.mapValues
          val selection = json.get(path)
          assertTrue(selection.all.toOption.get.toSet == Set[Json](Json.number(1), Json.number(2), Json.number(3)))
        },
        test("get with mapKeys returns all object keys as strings") {
          val json      = Json.obj("a" -> Json.number(1), "b" -> Json.number(2))
          val path      = DynamicOptic.mapKeys
          val selection = json.get(path)
          assertTrue(selection.all.toOption.get.toSet == Set[Json](Json.str("a"), Json.str("b")))
        },
        test("modify with mapValues transforms all values") {
          val json    = Json.obj("a" -> Json.number(1), "b" -> Json.number(2))
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
          val json      = Json.arr(Json.str("a"), Json.str("b"), Json.str("c"), Json.str("d"))
          val path      = DynamicOptic.root.atIndices(0, 2)
          val selection = json.get(path)
          assertTrue(selection.all == Right(Vector(Json.str("a"), Json.str("c"))))
        },
        test("modify with atIndices transforms specific elements") {
          val json    = Json.arr(Json.number(1), Json.number(2), Json.number(3), Json.number(4))
          val path    = DynamicOptic.root.atIndices(1, 3)
          val updated = json.modify(path) {
            case Json.Number(n) => Json.Number((BigDecimal(n) * 10).toString)
            case other          => other
          }
          assertTrue(
            updated(0).single == Right(Json.number(1)),
            updated(1).single == Right(Json.number(20)),
            updated(2).single == Right(Json.number(3)),
            updated(3).single == Right(Json.number(40))
          )
        }
      ),
      suite("toKV edge cases")(
        test("toKV handles empty object") {
          val json = Json.obj()
          val kvs  = json.toKV
          assertTrue(kvs.length == 1, kvs.head._2 == Json.obj())
        },
        test("toKV handles empty array") {
          val json = Json.arr()
          val kvs  = json.toKV
          assertTrue(kvs.length == 1, kvs.head._2 == Json.arr())
        },
        test("fromKVUnsafe works correctly") {
          val kvs = Seq(
            (DynamicOptic.root.field("a"), Json.number(1)),
            (DynamicOptic.root.field("b").field("c"), Json.number(2))
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
          val multiple = JsonSelection.succeedMany(Vector(Json.number(1), Json.number(2)))
          assertTrue(multiple.single.isLeft)
        },
        test("single returns value for single element") {
          val single = JsonSelection.succeed(Json.number(42))
          assertTrue(single.single == Right(Json.number(42)))
        },
        test("collect extracts matching values") {
          val selection = JsonSelection.succeedMany(Vector(Json.number(1), Json.str("a"), Json.number(2)))
          val numbers   = selection.collect { case Json.Number(n) => n }
          assertTrue(numbers == Right(Vector("1", "2")))
        },
        test("orElse returns alternative on failure") {
          val failed   = JsonSelection.fail(JsonError("error"))
          val fallback = JsonSelection.succeed(Json.number(42))
          val result   = failed.orElse(fallback)
          assertTrue(result.single == Right(Json.number(42)))
        },
        test("orElse returns original on success") {
          val success  = JsonSelection.succeed(Json.number(1))
          val fallback = JsonSelection.succeed(Json.number(2))
          val result   = success.orElse(fallback)
          assertTrue(result.single == Right(Json.number(1)))
        },
        test("getOrElse returns values on success") {
          val selection = JsonSelection.succeedMany(Vector(Json.number(1), Json.number(2)))
          val result    = selection.getOrElse(Vector(Json.Null))
          assertTrue(result == Vector(Json.number(1), Json.number(2)))
        },
        test("getOrElse returns default on failure") {
          val failed = JsonSelection.fail(JsonError("error"))
          val result = failed.getOrElse(Vector(Json.Null))
          assertTrue(result == Vector(Json.Null))
        },
        test("map transforms all values") {
          val selection = JsonSelection.succeedMany(Vector(Json.number(1), Json.number(2)))
          val mapped    = selection.map {
            case Json.Number(n) => Json.Number((BigDecimal(n) * 2).toString)
            case other          => other
          }
          assertTrue(mapped.all == Right(Vector(Json.number(2), Json.number(4))))
        },
        test("flatMap chains selections") {
          val json = Json.obj(
            "users" -> Json.arr(
              Json.obj("name" -> Json.str("Alice")),
              Json.obj("name" -> Json.str("Bob"))
            )
          )
          // Get all user names using flatMap
          val path  = DynamicOptic.root.field("users").elements.field("name")
          val names = json.get(path)
          assertTrue(names.size == 2)
        },
        test("filter keeps matching values") {
          val selection = JsonSelection.succeedMany(Vector(Json.number(1), Json.number(2), Json.number(3)))
          val filtered  = selection.filter {
            case Json.Number(n) => BigDecimal(n) > BigDecimal(1)
            case _              => false
          }
          assertTrue(filtered.all == Right(Vector(Json.number(2), Json.number(3))))
        },
        test("as decodes single value") {
          val selection = JsonSelection.succeed(Json.number(42))
          assertTrue(selection.as[Int] == Right(42))
        },
        test("asAll decodes all values") {
          val selection = JsonSelection.succeedMany(Vector(Json.number(1), Json.number(2), Json.number(3)))
          assertTrue(selection.asAll[Int] == Right(Vector(1, 2, 3)))
        },
        test("long extraction works") {
          val selection = JsonSelection.succeed(Json.number(9876543210L))
          assertTrue(selection.long == Right(9876543210L))
        },
        test("double extraction works") {
          val selection = JsonSelection.succeed(Json.number(3.14159))
          assertTrue(selection.double.isRight)
        },
        test("int extraction fails for non-int numbers") {
          val selection = JsonSelection.succeed(Json.number(BigDecimal("9999999999999")))
          assertTrue(selection.int.isLeft)
        },
        test("nullValues filters to only nulls") {
          val selection = JsonSelection.succeedMany(Vector(Json.Null, Json.number(1), Json.Null))
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
          val success = JsonSelection.succeed(Json.number(1))
          assertTrue(success.error.isEmpty)
        },
        test("values returns Some on success") {
          val success = JsonSelection.succeed(Json.number(1))
          assertTrue(success.values.isDefined)
        },
        test("values returns None on failure") {
          val failed = JsonSelection.fail(JsonError("error"))
          assertTrue(failed.values.isEmpty)
        },
        test("headOption returns first value") {
          val selection = JsonSelection.succeedMany(Vector(Json.number(1), Json.number(2)))
          assertTrue(selection.headOption.contains(Json.number(1)))
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
          val numSel  = JsonSelection.succeed(Json.number(42))
          val boolSel = JsonSelection.succeed(Json.bool(true))
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
          assertTrue(json.elements == Vector(Json.number(1), Json.number(2)))
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
          val json = Json.number(BigDecimal(Long.MaxValue))
          val dv   = json.toDynamicValue
          dv match {
            case DynamicValue.Primitive(pv: PrimitiveValue.Long) =>
              assertTrue(pv.value == Long.MaxValue)
            case _ => assertTrue(false)
          }
        },
        test("toDynamicValue converts BigDecimal for decimals") {
          val json = Json.number(BigDecimal("123.456"))
          val dv   = json.toDynamicValue
          dv match {
            case DynamicValue.Primitive(pv: PrimitiveValue.BigDecimal) =>
              assertTrue(pv.value == BigDecimal("123.456"))
            case _ => assertTrue(false)
          }
        },
        test("toDynamicValue converts arrays") {
          val json = Json.arr(Json.number(1), Json.number(2))
          val dv   = json.toDynamicValue
          dv match {
            case DynamicValue.Sequence(elems) =>
              assertTrue(elems.length == 2)
            case _ => assertTrue(false)
          }
        },
        test("toDynamicValue converts objects") {
          val json = Json.obj("a" -> Json.number(1))
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
            Json.number(1).compare(Json.number(2)) < 0,
            Json.number(2).compare(Json.number(1)) > 0,
            Json.number(1).compare(Json.number(1)) == 0,
            Json.str("a").compare(Json.str("b")) < 0,
            Json.bool(false).compare(Json.bool(true)) < 0
          )
        },
        test("compare arrays element by element") {
          assertTrue(
            Json.arr(Json.number(1)).compare(Json.arr(Json.number(2))) < 0,
            Json.arr(Json.number(1), Json.number(2)).compare(Json.arr(Json.number(1))) > 0
          )
        },
        test("compare objects by sorted keys") {
          val obj1 = Json.obj("a" -> Json.number(1))
          val obj2 = Json.obj("b" -> Json.number(1))
          assertTrue(obj1.compare(obj2) < 0)
        }
      ),
      suite("constructors")(
        test("Json.True and Json.False constants") {
          assertTrue(Json.True == Json.bool(true), Json.False == Json.bool(false))
        },
        test("Json.num with different types") {
          assertTrue(
            Json.number(42) == Json.Number("42"),
            Json.number(42L) == Json.Number("42"),
            Json.number(3.14) == Json.Number("3.14")
          )
        },
        test("Object.empty and Array.empty") {
          assertTrue(
            Json.Object.empty == Json.obj(),
            Json.Array.empty == Json.arr()
          )
        }
      ),
      suite("transformKeys edge cases")(
        test("transformKeys works on nested structures") {
          val json = Json.obj(
            "outer_key" -> Json.obj(
              "inner_key" -> Json.number(1)
            )
          )
          val transformed = json.transformKeys((_, k) => k.toUpperCase)
          assertTrue(
            transformed.get("OUTER_KEY").isSuccess,
            transformed.get("OUTER_KEY").get("INNER_KEY").isSuccess
          )
        },
        test("transformKeys works on arrays containing objects") {
          val json = Json.arr(
            Json.obj("snake_case"  -> Json.number(1)),
            Json.obj("another_key" -> Json.number(2))
          )
          val transformed = json.transformKeys((_, k) => k.replace("_", "-"))
          assertTrue(
            transformed(0).get("snake-case").isSuccess,
            transformed(1).get("another-key").isSuccess
          )
        }
      ),
      suite("filter/partition edge cases")(
        test("filter on primitives returns unchanged") {
          val json     = Json.number(42)
          val filtered = json.filter((_, _) => true)
          assertTrue(filtered == json)
        },
        test("partition on primitives") {
          val json                    = Json.str("hello")
          val (matching, nonMatching) = json.partition((_, j) => j.isString)
          assertTrue(matching == json, nonMatching == Json.Null)
        }
      ),
      suite("project edge cases")(
        test("project with empty paths returns Null") {
          val json      = Json.obj("a" -> Json.number(1))
          val projected = json.project()
          assertTrue(projected == Json.Null)
        },
        test("project with non-existent paths") {
          val json      = Json.obj("a" -> Json.number(1))
          val path      = DynamicOptic.root.field("nonexistent")
          val projected = json.project(path)
          assertTrue(projected == Json.Null)
        }
      ),
      suite("AtMapKey operations")(
        test("get with atKey retrieves value by key") {
          val json   = Json.obj("alice" -> Json.number(1), "bob" -> Json.number(2))
          val path   = DynamicOptic.root.atKey("alice")(Schema.string)
          val result = json.get(path)
          assertTrue(result.single == Right(Json.number(1)))
        },
        test("get with atKey returns empty for missing key") {
          val json   = Json.obj("alice" -> Json.number(1))
          val path   = DynamicOptic.root.atKey("missing")(Schema.string)
          val result = json.get(path)
          assertTrue(result.toVector.isEmpty)
        },
        test("modify with atKey updates value at key") {
          val json    = Json.obj("alice" -> Json.number(1), "bob" -> Json.number(2))
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
          val json    = Json.obj("alice" -> Json.number(1))
          val path    = DynamicOptic.root.atKey("missing")(Schema.string)
          val updated = json.modify(path)(_ => Json.number(99))
          assertTrue(updated == json)
        }
      ),
      suite("AtMapKeys operations")(
        test("get with atKeys retrieves multiple values") {
          val json   = Json.obj("a" -> Json.number(1), "b" -> Json.number(2), "c" -> Json.number(3))
          val path   = DynamicOptic.root.atKeys("a", "c")(Schema.string)
          val result = json.get(path)
          assertTrue(result.all == Right(Vector(Json.number(1), Json.number(3))))
        },
        test("get with atKeys returns only existing keys") {
          val json   = Json.obj("a" -> Json.number(1), "b" -> Json.number(2))
          val path   = DynamicOptic.root.atKeys("a", "missing", "b")(Schema.string)
          val result = json.get(path)
          assertTrue(result.all == Right(Vector(Json.number(1), Json.number(2))))
        },
        test("modify with atKeys updates multiple values") {
          val json    = Json.obj("a" -> Json.number(1), "b" -> Json.number(2), "c" -> Json.number(3))
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
          val json   = Json.arr(Json.number(1), Json.number(2))
          val path   = DynamicOptic.root.atKeys("a")(Schema.string)
          val result = json.get(path)
          assertTrue(result.toVector.isEmpty)
        }
      ),
      suite("Elements delete operations")(
        test("delete with elements removes all array elements") {
          val json    = Json.arr(Json.number(1), Json.number(2), Json.number(3))
          val path    = DynamicOptic.elements
          val deleted = json.delete(path)
          assertTrue(deleted == Json.arr())
        },
        test("delete with elements on non-array returns unchanged") {
          val json    = Json.obj("a" -> Json.number(1))
          val path    = DynamicOptic.elements
          val deleted = json.delete(path)
          assertTrue(deleted == json)
        },
        test("delete nested elements through field path") {
          val json = Json.obj(
            "items" -> Json.arr(Json.number(1), Json.number(2), Json.number(3))
          )
          val path    = DynamicOptic.root.field("items").elements
          val deleted = json.delete(path)
          assertTrue(deleted == Json.obj("items" -> Json.arr()))
        }
      ),
      suite("Nested delete operations")(
        test("delete nested field through object path") {
          val json = Json.obj(
            "user" -> Json.obj(
              "name" -> Json.str("Alice"),
              "age"  -> Json.number(30)
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
          val json = Json.obj(
            "items" -> Json.arr(Json.number(1), Json.number(2), Json.number(3))
          )
          val path    = DynamicOptic.root.field("items").at(1)
          val deleted = json.delete(path)
          assertTrue(
            deleted.get("items").toVector == Vector(Json.arr(Json.number(1), Json.number(3)))
          )
        },
        test("delete deeply nested field") {
          val json = Json.obj(
            "a" -> Json.obj(
              "b" -> Json.obj(
                "c" -> Json.number(1),
                "d" -> Json.number(2)
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
          val json   = Json.arr(Json.number(1), Json.number(2), Json.number(3))
          val path   = DynamicOptic.elements
          val result = json.modifyOrFail(path) { case Json.Number(n) =>
            Json.Number((BigDecimal(n) * 2).toString)
          }
          assertTrue(result == Right(Json.arr(Json.number(2), Json.number(4), Json.number(6))))
        },
        test("modifyOrFail with elements fails when partial function not defined") {
          val json   = Json.arr(Json.number(1), Json.str("not a number"), Json.number(3))
          val path   = DynamicOptic.elements
          val result = json.modifyOrFail(path) { case Json.Number(n) =>
            Json.Number((BigDecimal(n) * 2).toString)
          }
          assertTrue(result.isLeft)
        },
        test("modifyOrFail with mapValues succeeds when all match") {
          val json   = Json.obj("a" -> Json.number(1), "b" -> Json.number(2))
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
          val json   = Json.obj("a" -> Json.number(1), "b" -> Json.str("not a number"))
          val path   = DynamicOptic.mapValues
          val result = json.modifyOrFail(path) { case Json.Number(n) =>
            Json.Number((BigDecimal(n) * 10).toString)
          }
          assertTrue(result.isLeft)
        },
        test("modifyOrFail with nested path and elements") {
          val json = Json.obj(
            "items" -> Json.arr(Json.number(1), Json.number(2))
          )
          val path   = DynamicOptic.root.field("items").elements
          val result = json.modifyOrFail(path) { case Json.Number(n) =>
            Json.Number((BigDecimal(n) + 100).toString)
          }
          assertTrue(result == Right(Json.obj("items" -> Json.arr(Json.number(101), Json.number(102)))))
        }
      ),
      suite("insertOrFail edge cases")(
        test("insertOrFail fails for non-existent nested path") {
          val json   = Json.obj()
          val path   = DynamicOptic.root.field("a").field("b")
          val result = json.insertOrFail(path, Json.number(42))
          assertTrue(result.isLeft)
        },
        test("insertOrFail at array index extends array") {
          val json   = Json.arr(Json.number(1), Json.number(2))
          val path   = DynamicOptic.root.at(2)
          val result = json.insertOrFail(path, Json.number(3))
          assertTrue(result == Right(Json.arr(Json.number(1), Json.number(2), Json.number(3))))
        },
        test("insertOrFail fails when field already exists") {
          val json   = Json.obj("a" -> Json.number(1))
          val path   = DynamicOptic.root.field("a")
          val result = json.insertOrFail(path, Json.number(99))
          assertTrue(result.isLeft)
        },
        test("insertOrFail succeeds for new field") {
          val json   = Json.obj("a" -> Json.number(1))
          val path   = DynamicOptic.root.field("b")
          val result = json.insertOrFail(path, Json.number(2))
          assertTrue(result == Right(Json.obj("a" -> Json.number(1), "b" -> Json.number(2))))
        }
      ),
      suite("JSON ordering")(
        test("ordering sorts json values correctly") {
          val values = List(
            Json.number(3),
            Json.number(1),
            Json.number(2)
          )
          val sorted = values.sorted(Json.ordering)
          assertTrue(sorted == List(Json.number(1), Json.number(2), Json.number(3)))
        },
        test("ordering handles mixed types by type order") {
          val values = List(
            Json.obj("a" -> Json.number(1)),
            Json.Null,
            Json.bool(true),
            Json.number(1),
            Json.str("hello"),
            Json.arr(Json.number(1))
          )
          val sorted = values.sorted(Json.ordering)
          // Null < Boolean < Number < String < Array < Object
          assertTrue(
            sorted(0) == Json.Null,
            sorted(1) == Json.bool(true),
            sorted(2) == Json.number(1),
            sorted(3) == Json.str("hello")
          )
        },
        test("ordering sorts strings alphabetically") {
          val values = List(Json.str("c"), Json.str("a"), Json.str("b"))
          val sorted = values.sorted(Json.ordering)
          assertTrue(sorted == List(Json.str("a"), Json.str("b"), Json.str("c")))
        }
      ),
      suite("Config-based parse and encode")(
        test("print with custom config") {
          val json   = Json.obj("a" -> Json.number(1))
          val result = json.print
          assertTrue(result.contains("a") && result.contains("1"))
        },
        test("encodeToBytes produces valid output") {
          val json    = Json.obj("name" -> Json.str("test"))
          val bytes   = json.encodeToBytes
          val decoded = Json.parse(new String(bytes, "UTF-8"))
          assertTrue(decoded == Right(json))
        },
        test("parse handles whitespace correctly") {
          val input  = """  {  "a"  :  1  }  """
          val result = Json.parse(input)
          assertTrue(result == Right(Json.obj("a" -> Json.number(1))))
        },
        test("parse handles unicode escapes") {
          val input  = "{\"emoji\": \"\\u0048\\u0065\\u006c\\u006c\\u006f\"}"
          val result = Json.parse(input)
          assertTrue(result == Right(Json.obj("emoji" -> Json.str("Hello"))))
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
          assertTrue(error.getMessage == "JsonError: test message")
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
            error.path == path,
            error.offset.isEmpty,
            error.line.isEmpty,
            error.column.isEmpty
          )
        }
      ),
      suite("Chunk-based encoding and parsing")(
        test("encodeToChunk produces valid Chunk[Byte]") {
          val json  = Json.obj("name" -> Json.str("Alice"), "age" -> Json.number(30))
          val chunk = json.encodeToChunk
          assertTrue(chunk.length > 0)
        },
        test("parse from Chunk[Byte] works correctly") {
          val json   = Json.obj("key" -> Json.str("value"))
          val chunk  = json.encodeToChunk
          val parsed = Json.parse(chunk)
          assertTrue(parsed == Right(json))
        },
        test("roundtrip encodeToChunk and parse(Chunk) preserves data") {
          val json = Json.obj(
            "users" -> Json.arr(
              Json.obj("name" -> Json.str("Alice"), "age" -> Json.number(30)),
              Json.obj("name" -> Json.str("Bob"), "age"   -> Json.number(25))
            ),
            "count"  -> Json.number(2),
            "active" -> Json.bool(true)
          )
          val chunk  = json.encodeToChunk
          val parsed = Json.parse(chunk)
          assertTrue(parsed == Right(json))
        },
        test("encodeToChunk with custom WriterConfig") {
          val json   = Json.obj("a" -> Json.number(1))
          val chunk  = json.encodeToChunk(WriterConfig)
          val parsed = Json.parse(chunk)
          assertTrue(parsed == Right(json))
        },
        test("parse from Chunk[Byte] with custom ReaderConfig") {
          val json   = Json.obj("a" -> Json.number(1))
          val chunk  = json.encodeToChunk
          val parsed = Json.parse(chunk, ReaderConfig)
          assertTrue(parsed == Right(json))
        },
        test("decode alias works same as parse for Chunk") {
          val json    = Json.obj("test" -> Json.str("value"))
          val chunk   = json.encodeToChunk
          val decoded = Json.decode(chunk)
          assertTrue(decoded == Right(json))
        },
        test("decode with config alias works same as parse") {
          val json    = Json.obj("test" -> Json.str("value"))
          val chunk   = json.encodeToChunk
          val decoded = Json.decode(chunk, ReaderConfig)
          assertTrue(decoded == Right(json))
        }
      ),
      suite("MergeStrategy.Custom")(
        test("Custom merge strategy allows user-defined logic") {
          val left           = Json.obj("a" -> Json.number(1), "b" -> Json.number(2))
          val right          = Json.obj("a" -> Json.number(10), "c" -> Json.number(3))
          val customStrategy = MergeStrategy.Custom { (_, l, r) =>
            (l, r) match {
              case (Json.Number(lv), Json.Number(rv)) => Json.Number((BigDecimal(lv) + BigDecimal(rv)).toString)
              case _                                  => r
            }
          }
          val result = left.merge(right, customStrategy)
          assertTrue(
            result.get("a").first == Right(Json.number(11)),
            result.get("b").first == Right(Json.number(2)),
            result.get("c").first == Right(Json.number(3))
          )
        },
        test("Custom merge strategy receives correct path") {
          var capturedPaths  = List.empty[String]
          val left           = Json.obj("outer" -> Json.obj("inner" -> Json.number(1)))
          val right          = Json.obj("outer" -> Json.obj("inner" -> Json.number(2)))
          val customStrategy = MergeStrategy.Custom { (path, _, r) =>
            capturedPaths = capturedPaths :+ path.toString
            r
          }
          left.merge(right, customStrategy)
          assertTrue(capturedPaths.exists(_.contains("inner")))
        },
        test("Custom merge strategy falls back to user function for non-objects") {
          val left           = Json.arr(Json.number(1))
          val right          = Json.arr(Json.number(2))
          val customStrategy = MergeStrategy.Custom { (_, l, r) =>
            (l, r) match {
              case (Json.Array(lv), Json.Array(rv)) => Json.arr((lv ++ rv): _*)
              case _                                => r
            }
          }
          val result = left.merge(right, customStrategy)
          assertTrue(result == Json.arr(Json.number(1), Json.number(2)))
        }
      )
    )
  )
}
