package zio.blocks.schema.json

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.blocks.schema.SchemaError
import zio.test._
import zio.test.Assertion.{equalTo, isRight}
import java.time._
import java.util.{Currency, UUID}

object JsonSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("JsonSpec")(
    suite("Json ADT")(
      suite("unified type operations")(
        test("is returns true when type matches") {
          assert(Json.Object.empty.is(JsonType.Object))(equalTo(true)) &&
          assert(Json.Array.empty.is(JsonType.Array))(equalTo(true)) &&
          assert(Json.String("test").is(JsonType.String))(equalTo(true)) &&
          assert(Json.Number(42).is(JsonType.Number))(equalTo(true)) &&
          assert(Json.Boolean(true).is(JsonType.Boolean))(equalTo(true)) &&
          assert(Json.Null.is(JsonType.Null))(equalTo(true))
        },
        test("is returns false when type does not match") {
          val obj = Json.Object.empty
          assert(obj.is(JsonType.Array))(equalTo(false)) &&
          assert(obj.is(JsonType.String))(equalTo(false)) &&
          assert(obj.is(JsonType.Number))(equalTo(false)) &&
          assert(obj.is(JsonType.Boolean))(equalTo(false)) &&
          assert(obj.is(JsonType.Null))(equalTo(false))
        },
        test("as returns Some when type matches") {
          val obj: Json                 = Json.Object("a" -> Json.Number(1))
          val arr: Json                 = Json.Array(Json.Number(1))
          val str: Json                 = Json.String("hello")
          val num: Json                 = Json.Number(42)
          val bool: Json                = Json.Boolean(true)
          val nul: Json                 = Json.Null
          val objResult                 = obj.as(JsonType.Object)
          val arrResult                 = arr.as(JsonType.Array)
          val strResult                 = str.as(JsonType.String)
          val numResult                 = num.as(JsonType.Number)
          val boolResult                = bool.as(JsonType.Boolean)
          val nullResult                = nul.as(JsonType.Null)
          val _: Option[Json.Object]    = objResult
          val _: Option[Json.Array]     = arrResult
          val _: Option[Json.String]    = strResult
          val _: Option[Json.Number]    = numResult
          val _: Option[Json.Boolean]   = boolResult
          val _: Option[Json.Null.type] = nullResult
          assertTrue(
            objResult.isDefined,
            arrResult.isDefined,
            strResult.isDefined,
            numResult.isDefined,
            boolResult.isDefined,
            nullResult.isDefined
          )
        },
        test("as returns None when type does not match") {
          assertTrue(
            Json.Null.as(JsonType.Number).isEmpty,
            Json.Boolean(true).as(JsonType.Number).isEmpty,
            Json.String("x").as(JsonType.Number).isEmpty,
            Json.Number(0).as(JsonType.String).isEmpty,
            Json.Array.empty.as(JsonType.String).isEmpty,
            Json.Object.empty.as(JsonType.Array).isEmpty,
            Json.Object.empty.as(JsonType.String).isEmpty,
            Json.Object.empty.as(JsonType.Number).isEmpty,
            Json.Object.empty.as(JsonType.Boolean).isEmpty,
            Json.Object.empty.as(JsonType.Null).isEmpty
          )
        },
        test("unwrap returns Some with correct inner value when type matches") {
          val obj: Json                        = Json.Object("a" -> Json.Number(1))
          val arr: Json                        = Json.Array(Json.Number(1), Json.Number(2))
          val str: Json                        = Json.String("hello")
          val num: Json                        = Json.Number(42)
          val bool: Json                       = Json.Boolean(true)
          val nul: Json                        = Json.Null
          val objUnwrap                        = obj.unwrap(JsonType.Object)
          val arrUnwrap                        = arr.unwrap(JsonType.Array)
          val strUnwrap                        = str.unwrap(JsonType.String)
          val numUnwrap                        = num.unwrap(JsonType.Number)
          val boolUnwrap                       = bool.unwrap(JsonType.Boolean)
          val nullUnwrap                       = nul.unwrap(JsonType.Null)
          val _: Option[Chunk[(String, Json)]] = objUnwrap
          val _: Option[Chunk[Json]]           = arrUnwrap
          val _: Option[String]                = strUnwrap
          val _: Option[BigDecimal]            = numUnwrap
          val _: Option[Boolean]               = boolUnwrap
          val _: Option[Unit]                  = nullUnwrap
          assert(objUnwrap)(equalTo(Some(Chunk(("a", Json.Number(1)))))) &&
          assert(arrUnwrap)(equalTo(Some(Chunk(Json.Number(1), Json.Number(2))))) &&
          assert(strUnwrap)(equalTo(Some("hello"))) &&
          assert(numUnwrap)(equalTo(Some(BigDecimal(42)))) &&
          assert(boolUnwrap)(equalTo(Some(true))) &&
          assert(nullUnwrap)(equalTo(Some(())))
        },
        test("unwrap returns None when type does not match") {
          assertTrue(
            Json.Null.unwrap(JsonType.Number).isEmpty,
            Json.Boolean(true).unwrap(JsonType.Number).isEmpty,
            Json.String("x").unwrap(JsonType.Number).isEmpty,
            Json.Number(0).unwrap(JsonType.String).isEmpty,
            Json.Array.empty.unwrap(JsonType.String).isEmpty,
            Json.Object.empty.unwrap(JsonType.Array).isEmpty,
            Json.Object.empty.unwrap(JsonType.String).isEmpty,
            Json.Object.empty.unwrap(JsonType.Number).isEmpty,
            Json.Object.empty.unwrap(JsonType.Boolean).isEmpty,
            Json.Object.empty.unwrap(JsonType.Null).isEmpty
          )
        },
        test("JsonType.apply works as a predicate function") {
          val obj: Json  = Json.Object.empty
          val arr: Json  = Json.Array.empty
          val str: Json  = Json.String("test")
          val num: Json  = Json.Number(42)
          val bool: Json = Json.Boolean(true)
          val nul: Json  = Json.Null
          assert(JsonType.Object(obj))(equalTo(true)) &&
          assert(JsonType.Object(arr))(equalTo(false)) &&
          assert(JsonType.Array(arr))(equalTo(true)) &&
          assert(JsonType.Array(str))(equalTo(false)) &&
          assert(JsonType.String(str))(equalTo(true)) &&
          assert(JsonType.String(num))(equalTo(false)) &&
          assert(JsonType.Number(num))(equalTo(true)) &&
          assert(JsonType.Number(bool))(equalTo(false)) &&
          assert(JsonType.Boolean(bool))(equalTo(true)) &&
          assert(JsonType.Boolean(nul))(equalTo(false)) &&
          assert(JsonType.Null(nul))(equalTo(true)) &&
          assert(JsonType.Null(obj))(equalTo(false))
        },
        test("select wraps json in selection") {
          val json      = Json.String("hello")
          val selection = json.select
          assertTrue(selection.isSuccess, selection.one == Right(Json.String("hello")))
        },
        test("select(jsonType) returns selection when type matches") {
          val json      = Json.Object("a" -> Json.Number(1))
          val selection = json.select(JsonType.Object)
          assertTrue(selection.isSuccess, selection.one == Right(json))
        },
        test("select(jsonType) returns empty when type does not match") {
          val json = Json.Object("a" -> Json.Number(1))
          assertTrue(
            json.select(JsonType.Array).isEmpty,
            json.select(JsonType.String).isEmpty,
            json.select(JsonType.Number).isEmpty
          )
        }
      ),
      suite("prune/retain methods")(
        test("prune removes matching values from object") {
          val json   = Json.Object("a" -> Json.Number(1), "b" -> Json.Null, "c" -> Json.Number(2))
          val result = json.prune(_.is(JsonType.Null)).as(JsonType.Object).get
          assertTrue(
            result.fields.length == 2,
            result.get("a").isSuccess,
            result.get("b").isFailure,
            result.get("c").isSuccess
          )
        },
        test("prune removes matching values from array") {
          val json   = Json.Array(Json.Number(1), Json.Null, Json.Number(2), Json.Null)
          val result = json.prune(_.is(JsonType.Null))
          assertTrue(result.elements == Chunk(Json.Number(1), Json.Number(2)))
        },
        test("prune works recursively") {
          val json   = Json.Object("user" -> Json.Object("name" -> Json.String("Alice"), "age" -> Json.Null))
          val result = json.prune(_.is(JsonType.Null))
          assertTrue(result.get("user").get("name").isSuccess, result.get("user").get("age").isFailure)
        },
        test("prunePath removes values at matching paths") {
          val json   = Json.Object("keep" -> Json.Number(1), "drop" -> Json.Number(2))
          val result = json.prunePath { path =>
            path.nodes.exists {
              case f: DynamicOptic.Node.Field => f.name == "drop"
              case _                          => false
            }
          }
          assertTrue(result.get("keep").isSuccess, result.get("drop").isFailure)
        },
        test("pruneBoth removes values matching both path and value predicates") {
          val json = Json.Object(
            "nums" -> Json.Array(Json.Number(1), Json.Number(100), Json.Number(5)),
            "strs" -> Json.Array(Json.String("a"))
          )
          val result = json.pruneBoth { (path, value) =>
            val inNums = path.nodes.exists {
              case f: DynamicOptic.Node.Field => f.name == "nums"
              case _                          => false
            }
            val isLarge = value.unwrap(JsonType.Number).exists(_ > 10)
            inNums && isLarge
          }
          assertTrue(result.get("nums").as[Chunk[Int]] == Right(Chunk(1, 5)), result.get("strs").isSuccess)
        },
        test("retain keeps only matching values in object") {
          val json   = Json.Object("a" -> Json.Number(1), "b" -> Json.String("hi"), "c" -> Json.Number(2))
          val result = json.retain(_.is(JsonType.Number))
          assertTrue(result.get("a").isSuccess, result.get("b").isFailure, result.get("c").isSuccess)
        },
        test("retain keeps only matching values in array") {
          val json   = Json.Array(Json.Number(1), Json.String("x"), Json.Number(2))
          val result = json.retain(_.is(JsonType.Number))
          assertTrue(result.elements == Chunk(Json.Number(1), Json.Number(2)))
        },
        test("retainPath keeps values at matching paths") {
          val json   = Json.Object("keep" -> Json.Number(1), "drop" -> Json.Number(2))
          val result = json.retainPath { path =>
            path.nodes.exists {
              case f: DynamicOptic.Node.Field => f.name == "keep"
              case _                          => false
            }
          }
          assertTrue(result.get("keep").isSuccess, result.get("drop").isFailure)
        },
        test("retainBoth keeps values matching both path and value predicates") {
          val json   = Json.Object("keep" -> Json.Number(100), "drop" -> Json.Number(5))
          val result = json.retainBoth { (path, value) =>
            val hasKeepField = path.nodes.exists {
              case f: DynamicOptic.Node.Field => f.name == "keep"
              case _                          => false
            }
            val isLarge = value.unwrap(JsonType.Number).exists(_ > 10)
            hasKeepField && isLarge
          }
          assertTrue(result.get("keep").isSuccess, result.get("drop").isFailure)
        }
      ),
      suite("jsonType")(
        test("jsonType returns correct type for each Json subtype") {
          assertTrue(
            Json.Object.empty.jsonType == JsonType.Object,
            Json.Array.empty.jsonType == JsonType.Array,
            Json.String("test").jsonType == JsonType.String,
            Json.Number(42).jsonType == JsonType.Number,
            Json.Boolean(true).jsonType == JsonType.Boolean,
            Json.Null.jsonType == JsonType.Null
          )
        },
        test("JsonType.typeIndex matches Json.typeIndex") {
          assertTrue(
            Json.Null.typeIndex == JsonType.Null.typeIndex,
            Json.Boolean(true).typeIndex == JsonType.Boolean.typeIndex,
            Json.Number(1).typeIndex == JsonType.Number.typeIndex,
            Json.String("s").typeIndex == JsonType.String.typeIndex,
            Json.Array.empty.typeIndex == JsonType.Array.typeIndex,
            Json.Object.empty.typeIndex == JsonType.Object.typeIndex
          )
        },
        test("JsonType ordering is Null < Boolean < Number < String < Array < Object") {
          assertTrue(
            JsonType.Null.typeIndex < JsonType.Boolean.typeIndex,
            JsonType.Boolean.typeIndex < JsonType.Number.typeIndex,
            JsonType.Number.typeIndex < JsonType.String.typeIndex,
            JsonType.String.typeIndex < JsonType.Array.typeIndex,
            JsonType.Array.typeIndex < JsonType.Object.typeIndex
          )
        }
      ),
      suite("direct accessors")(
        test("fields returns non-empty Seq for objects") {
          val json = Json.Object("a" -> Json.Number(1), "b" -> Json.Number(2))
          assertTrue(json.fields.nonEmpty, json.fields.length == 2)
        },
        test("fields returns empty Seq for non-objects") {
          assertTrue(
            Json.Array().fields.isEmpty,
            Json.String("test").fields.isEmpty,
            Json.Number(42).fields.isEmpty,
            Json.Boolean(true).fields.isEmpty,
            Json.Null.fields.isEmpty
          )
        },
        test("elements returns non-empty Seq for arrays") {
          val json = Json.Array(Json.Number(1), Json.Number(2), Json.Number(3))
          assertTrue(json.elements.nonEmpty, json.elements.length == 3)
        },
        test("elements returns empty Seq for non-arrays") {
          assertTrue(
            Json.Object().elements.isEmpty,
            Json.String("test").elements.isEmpty,
            Json.Number(42).elements.isEmpty,
            Json.Boolean(true).elements.isEmpty,
            Json.Null.elements.isEmpty
          )
        }
      ),
      suite("navigation")(
        test("works with p interpolator") {
          val json = Json.Object(
            "users" -> Json.Array(
              Json.Object("name" -> Json.String("Alice")),
              Json.Object("name" -> Json.String("Bob"))
            )
          )
          assertTrue(json.get(p".users[1].name").as[String] == Right("Bob"))
        },
        test("get retrieves field from object") {
          val json = Json.Object("name" -> Json.String("Alice"), "age" -> Json.Number(30))
          assertTrue(
            json.get("name").one == Right(Json.String("Alice")),
            json.get("age").one == Right(Json.Number(30))
          )
        },
        test("get returns error for missing field") {
          val json = Json.Object("name" -> Json.String("Alice"))
          assertTrue(json.get("missing").isFailure)
        },
        test("apply(index) retrieves element from array") {
          val json = Json.Array(Json.String("a"), Json.String("b"), Json.String("c"))
          assertTrue(
            json.get(0).one == Right(Json.String("a")),
            json.get(1).one == Right(Json.String("b")),
            json.get(2).one == Right(Json.String("c"))
          )
        },
        test("apply(index) returns error for out of bounds") {
          val json = Json.Array(Json.Number(1))
          assertTrue(json.get(1).isFailure, json.get(-1).isFailure)
        },
        test("chained navigation works") {
          val json = Json.Object(
            "users" -> Json.Array(
              Json.Object("name" -> Json.String("Alice")),
              Json.Object("name" -> Json.String("Bob"))
            )
          )
          assertTrue(json.get("users")(0).get("name").as[String] == Right("Alice"))
        }
      ),
      suite("modification with DynamicOptic")(
        test("set updates existing field in object") {
          val json   = Json.Object("a" -> Json.Number(1))
          val result = json.set(DynamicOptic.root.field("a"), Json.Number(99))
          assertTrue(result.get("a").one == Right(Json.Number(99)))
        },
        test("set returns unchanged json if field doesn't exist") {
          val json   = Json.Object("a" -> Json.Number(1))
          val result = json.set(DynamicOptic.root.field("b"), Json.Number(2))
          assertTrue(result == json) // set on non-existent path returns original unchanged
        },
        test("set updates element in array") {
          val json   = Json.Array(Json.Number(1), Json.Number(2), Json.Number(3))
          val result = json.set(DynamicOptic.root.at(1), Json.Number(99))
          assertTrue(result.get(1).one == Right(Json.Number(99)))
        },
        test("setOrFail fails for non-existent path") {
          val json1   = Json.Object("a" -> Json.Number(1))
          val json2   = Json.Array(Json.Number(1))
          val result1 = json1.setOrFail(DynamicOptic.root.field("b"), Json.Number(2))
          val result2 = json2.setOrFail(DynamicOptic.root.field("b"), Json.Number(2))
          val result3 = json1.setOrFail(DynamicOptic.root.at(0), Json.Number(2))
          val result4 = json2.setOrFail(DynamicOptic.root.at(100), Json.Number(2))
          val result5 = json1.setOrFail(DynamicOptic.root.elements, Json.Number(2))
          val result6 = json2.setOrFail(DynamicOptic.root.mapValues, Json.Number(2))
          assertTrue(result1.isLeft, result2.isLeft, result3.isLeft, result4.isLeft, result5.isLeft, result6.isLeft)
        },
        test("delete removes field from object") {
          val json   = Json.Object("a" -> Json.Number(1), "b" -> Json.Number(2))
          val result = json.delete(DynamicOptic.root.field("a"))
          assertTrue(result.get("a").isFailure, result.get("b").isSuccess)
        },
        test("delete removes element from array") {
          val json   = Json.Array(Json.Number(1), Json.Number(2), Json.Number(3))
          val result = json.delete(DynamicOptic.root.at(1))
          assertTrue(
            result.elements.length == 2,
            result.get(0).one == Right(Json.Number(1)),
            result.get(1).one == Right(Json.Number(3))
          )
        },
        test("deleteOrFail fails for non-existent path") {
          val json   = Json.Object("a" -> Json.Number(1))
          val result = json.deleteOrFail(DynamicOptic.root.field("missing"))
          assertTrue(result.isLeft)
        },
        test("modify transforms value at path") {
          val json   = Json.Object("count" -> Json.Number(5))
          val result = json.modify(DynamicOptic.root.field("count")) {
            case Json.Number(n) => Json.Number(n * 2)
            case other          => other
          }
          assertTrue(result.get("count").as[BigDecimal] == Right(BigDecimal(10)))
        },
        test("modifyOrFail fails when partial function not defined") {
          val json   = Json.Object("name" -> Json.String("Alice"))
          val result = json.modifyOrFail(DynamicOptic.root.field("name")) { case Json.Number(n) => Json.Number(n * 2) }
          assertTrue(result.isLeft)
        },
        test("insert adds new field to object") {
          val json   = Json.Object("a" -> Json.Number(1))
          val result = json.insert(DynamicOptic.root.field("b"), Json.Number(2))
          assertTrue(result.get("b").one == Right(Json.Number(2)))
        },
        test("insert does nothing if field already exists") {
          val json   = Json.Object("a" -> Json.Number(1))
          val result = json.insert(DynamicOptic.root.field("a"), Json.Number(99))
          assertTrue(result.get("a").one == Right(Json.Number(1))) // Original value unchanged
        },
        test("insertOrFail fails if insertion is not possible") {
          val json1   = Json.Object("a" -> Json.Number(1))
          val json2   = Json.Array(Json.Number(1))
          val result1 = json1.insertOrFail(DynamicOptic.root.field("a"), Json.Number(99))
          val result2 = json1.insertOrFail(DynamicOptic.root.at(0), Json.Number(99))
          val result3 = json2.insertOrFail(DynamicOptic.root.field("a"), Json.Number(99))
          val result4 = json2.insertOrFail(DynamicOptic.root.at(10), Json.Number(99))
          assertTrue(result1.isLeft, result2.isLeft, result3.isLeft, result4.isLeft)
        },
        test("insert at array index shifts elements") {
          val json    = Json.Array(Json.Number(1), Json.Number(3))
          val updated = json.insert(DynamicOptic.root.at(1), Json.Number(2))
          assertTrue(updated.elements == Chunk(Json.Number(1), Json.Number(2), Json.Number(3)))
        },
        test("nested path modification works") {
          val json   = Json.Object("user" -> Json.Object("profile" -> Json.Object("age" -> Json.Number(25))))
          val result = json.set(DynamicOptic.root.field("user").field("profile").field("age"), Json.Number(26))
          assertTrue(result.get("user").get("profile").get("age").as[BigDecimal] == Right(BigDecimal(26)))
        },
        test("get with DynamicOptic navigates nested structure") {
          val json = Json.Object(
            "users" -> Json.Array(
              Json.Object("name" -> Json.String("Alice")),
              Json.Object("name" -> Json.String("Bob"))
            )
          )
          val result = json.get(DynamicOptic.root.field("users").at(0).field("name"))
          assertTrue(result.as[String] == Right("Alice"))
        },
        test("get with elements returns all array elements") {
          val json   = Json.Array(Json.Number(1), Json.Number(2), Json.Number(3))
          val result = json.get(DynamicOptic.elements).either
          assertTrue(result == Right(Chunk(Json.Number(1), Json.Number(2), Json.Number(3))))
        },
        test("modify with elements transforms all array elements") {
          val json   = Json.Array(Json.Number(1), Json.Number(2), Json.Number(3))
          val result = json.modify(DynamicOptic.elements) {
            case Json.Number(n) => Json.Number(n * 10)
            case other          => other
          }
          assertTrue(result.elements == Chunk(Json.Number(10), Json.Number(20), Json.Number(30)))
        }
      ),
      suite("normalization")(
        test("sortKeys sorts object keys alphabetically") {
          val json   = Json.Object("c" -> Json.Number(3), "a" -> Json.Number(1), "b" -> Json.Number(2))
          val result = json.sortKeys
          assertTrue(result.fields.map(_._1) == Chunk("a", "b", "c"))
        },
        test("sortKeys works recursively") {
          val json = Json.Object(
            "z" -> Json.Object("b" -> Json.Number(1), "a" -> Json.Number(2)),
            "a" -> Json.Number(0)
          )
          val result = json.sortKeys
          result match {
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
        test("dropNulls removes null values recursively") {
          val json = Json.Object(
            "a" -> Json.Number(1),
            "b" -> Json.Null,
            "c" -> Json.Object(
              "x" -> Json.Number(3),
              "y" -> Json.Null
            )
          )
          val result = json.dropNulls
          assertTrue(
            result == Json.Object(
              "a" -> Json.Number(1),
              "c" -> Json.Object(
                "x" -> Json.Number(3)
              )
            )
          )
        },
        test("dropEmpty removes empty objects and arrays") {
          val json = Json.Object(
            "a" -> Json.Number(1),
            "b" -> Json.Object(),
            "c" -> Json.Array(),
            "d" -> Json.Object(
              "x" -> Json.Number(3),
              "y" -> Json.Object(),
              "z" -> Json.Array()
            )
          )
          val result = json.dropEmpty
          assertTrue(
            result == Json.Object(
              "a" -> Json.Number(1),
              "d" -> Json.Object(
                "x" -> Json.Number(3)
              )
            )
          )
        },
        test("normalize applies sortKeys, dropNulls, and dropEmpty") {
          val json = Json.Object(
            "z" -> Json.Number(1),
            "a" -> Json.Null,
            "m" -> Json.Object(),
            "b" -> Json.Array(),
            "j" -> Json.Object(
              "w" -> Json.Number(1),
              "v" -> Json.Null,
              "o" -> Json.Object(),
              "p" -> Json.Array()
            )
          )
          val result = json.normalize
          assertTrue(result == json.sortKeys.dropNulls.dropEmpty)
        },
        test("dropNulls works on arrays") {
          val json   = Json.Array(Json.Number(1), Json.Null, Json.Number(2), Json.Null)
          val result = json.dropNulls
          assertTrue(result.elements == Chunk(Json.Number(1), Json.Number(2)))
        },
        test("normalize works on arrays") {
          val json = Json.Array(
            Json.Object(
              "z" -> Json.Number(1),
              "a" -> Json.Null,
              "m" -> Json.Object(),
              "b" -> Json.Array()
            ),
            Json.Array(),
            Json.Number(2),
            Json.Null
          )
          val result = json.normalize
          assertTrue(
            result == Json.Array(
              Json.Object(
                "z" -> Json.Number(1)
              ),
              Json.Number(2)
            )
          )
        },
        test("sortKeys works on arrays") {
          val json = Json.Array(
            Json.Object(
              "z" -> Json.Number(1),
              "a" -> Json.Number(2)
            ),
            Json.Number(2),
            Json.Array()
          )
          val result = json.sortKeys
          assertTrue(
            result == Json.Array(
              Json.Object(
                "a" -> Json.Number(2),
                "z" -> Json.Number(1)
              ),
              Json.Number(2),
              Json.Array()
            )
          )
        },
        test("dropEmpty works recursively") {
          val json = Json.Object(
            "a" -> Json.Object("b" -> Json.Array(Json.Object()))
          )
          val result = json.dropEmpty
          assertTrue(result == Json.Object())
        }
      ),
      suite("merging")(
        test("merge with Auto strategy merges objects deeply") {
          val json1  = Json.Object("a" -> Json.Object("x" -> Json.Number(1)))
          val json2  = Json.Object("a" -> Json.Object("y" -> Json.Number(2)))
          val result = json1.merge(json2)
          result match {
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
          val json1  = Json.Object("a" -> Json.Number(1))
          val json2  = Json.Object("b" -> Json.Number(2))
          val result = json1.merge(json2, MergeStrategy.Replace)
          assertTrue(result == json2)
        },
        test("merge arrays by index with Auto") {
          val json1  = Json.Array(Json.Number(1), Json.Number(2))
          val json2  = Json.Array(Json.Number(3), Json.Number(4))
          val result = json1.merge(json2)
          assertTrue(result.elements == Chunk(Json.Number(3), Json.Number(4)))
        },
        test("merge arrays concatenates them with Concat") {
          val json1  = Json.Array(Json.Number(1), Json.Number(2))
          val json2  = Json.Array(Json.Number(3), Json.Number(4))
          val result = json1.merge(json2, MergeStrategy.Concat)
          assertTrue(result.elements.length == 4)
        },
        test("merge arrays by index preserves extra elements from longer array") {
          val json1  = Json.Array(Json.Number(1), Json.Number(2), Json.Number(3))
          val json2  = Json.Array(Json.Number(10), Json.Number(20))
          val result = json1.merge(json2)
          assertTrue(result.elements == Chunk(Json.Number(10), Json.Number(20), Json.Number(3)))
        },
        test("merge arrays by index with right longer than left") {
          val json1  = Json.Array(Json.Number(1))
          val json2  = Json.Array(Json.Number(10), Json.Number(20), Json.Number(30))
          val result = json1.merge(json2)
          assertTrue(result.elements == Chunk(Json.Number(10), Json.Number(20), Json.Number(30)))
        },
        test("merge nested arrays recursively with Auto") {
          val json1  = Json.Object("arr" -> Json.Array(Json.Number(1), Json.Number(2)))
          val json2  = Json.Object("arr" -> Json.Array(Json.Number(10)))
          val result = json1.merge(json2)
          assertTrue(result.get("arr").one.map(_.elements) == Right(Chunk(Json.Number(10), Json.Number(2))))
        },
        test("merge with Shallow only merges at root level") {
          val json1  = Json.Object("a" -> Json.Object("x" -> Json.Number(1), "y" -> Json.Number(2)))
          val json2  = Json.Object("a" -> Json.Object("z" -> Json.Number(3)))
          val result = json1.merge(json2, MergeStrategy.Shallow)
          assertTrue(
            result.get("a").get("z").as[BigDecimal] == Right(BigDecimal(3)),
            result.get("a").get("x").isFailure,
            result.get("a").get("y").isFailure
          )
        },
        test("merge with Shallow on nested arrays replaces at root") {
          val json1  = Json.Array(Json.Object("a" -> Json.Number(1)))
          val json2  = Json.Array(Json.Object("b" -> Json.Number(2)))
          val result = json1.merge(json2, MergeStrategy.Shallow)
          assertTrue(result.get(0).get("b").as[BigDecimal] == Right(BigDecimal(2)))
        }
      ),
      suite("parsing and encoding")(
        test("parse valid JSON object") {
          val result = Json.parse("""{"name": "Alice", "age": 30}""")
          assertTrue(
            result.isRight,
            result.toOption.get.get("name").as[String] == Right("Alice"),
            result.toOption.get.get("age").as[BigDecimal] == Right(BigDecimal(30))
          )
        },
        test("cannot parse invalid JSON object") {
          val result = Json.parse("""{"name": "Alice", "age": 30]""")
          assertTrue(result.isLeft)
        },
        test("parse JSON array") {
          val result = Json.parse("""[1, 2, 3]""")
          assertTrue(result.isRight, result.toOption.get.elements.length == 3)
        },
        test("cannot parse invalid JSON array") {
          val result = Json.parse("""[1, 2, 3}""")
          assertTrue(result.isLeft)
        },
        test("parse JSON primitives") {
          assertTrue(
            Json.parse("\"hello\"").toOption.get == Json.String("hello"),
            Json.parse("42").toOption.get == Json.Number(42),
            Json.parse("true").toOption.get == Json.Boolean(true),
            Json.parse("false").toOption.get == Json.Boolean(false),
            Json.parse("null").toOption.get == Json.Null
          )
        },
        test("encode produces valid JSON") {
          val json   = Json.Object("name" -> Json.String("Alice"), "scores" -> Json.Array(Json.Number(1), Json.Number(2)))
          val result = json.print
          assertTrue(result == """{"name":"Alice","scores":[1,2]}""")
        },
        test("roundtrip parsing and encoding") {
          val json = Json.Object(
            "string" -> Json.String("hello"),
            "number" -> Json.Number(42.5),
            "bool"   -> Json.Boolean(true),
            "null"   -> Json.Null,
            "array"  -> Json.Array(Json.Number(1), Json.Number(2)),
            "nested" -> Json.Object("x" -> Json.Number(1))
          )
          val result = Json.parse(json.print)
          assertTrue(result.isRight, result.toOption.get == json)
        }
      ),
      suite("equality and comparison")(
        test("object equality is order-independent") {
          val json1 = Json.Object("a" -> Json.Number(1), "b" -> Json.Number(2))
          val json2 = Json.Object("b" -> Json.Number(2), "a" -> Json.Number(1))
          assertTrue(json1 == json2, json1.hashCode() == json2.hashCode())
        },
        test("array equality is order-dependent") {
          val json1 = Json.Array(Json.Number(1), Json.Number(2))
          val json2 = Json.Array(Json.Number(2), Json.Number(1))
          assertTrue(json1 != json2)
        },
        test("compare orders by type then value") {
          assertTrue(
            Json.Null.compare(Json.Boolean(true)) < 0,
            Json.Boolean(true).compare(Json.Number(1)) < 0,
            Json.Number(1).compare(Json.String("a")) < 0,
            Json.String("a").compare(Json.Array()) < 0,
            Json.Array().compare(Json.Object()) < 0,
            Json.Object().compare(Json.Null) > 0
          )
        }
      ),
      suite("DynamicValue conversion")(
        test("toDynamicValue converts primitives") {
          assertTrue(
            Json.Null.toDynamicValue == DynamicValue.Null,
            Json.Boolean(true).toDynamicValue == DynamicValue.Primitive(PrimitiveValue.Boolean(true)),
            Json.String("hello").toDynamicValue == DynamicValue.Primitive(PrimitiveValue.String("hello"))
          )
        },
        test("toDynamicValue converts integers to Int when possible") {
          val dv = Json.Number(42).toDynamicValue
          assertTrue(dv match {
            case DynamicValue.Primitive(pv: PrimitiveValue.Int) => pv.value == 42
            case _                                              => false
          })
        },
        test("fromDynamicValue converts back to Json") {
          val dv = DynamicValue.Record(
            Chunk(
              ("name", DynamicValue.Primitive(PrimitiveValue.String("test"))),
              ("count", DynamicValue.Primitive(PrimitiveValue.Int(10)))
            )
          )
          val json = Json.fromDynamicValue(dv)
          assertTrue(
            json.get("name").as[String] == Right("test"),
            json.get("count").as[BigDecimal] == Right(BigDecimal(10))
          )
        }
      ),
      suite("transformation methods")(
        test("transformUp applies function bottom-up") {
          val json = Json.Object(
            "a" -> Json.Object("b" -> Json.Number(1)),
            "c" -> Json.Array(Json.Number(2))
          )
          val result = json.transformUp { (_, j) =>
            j match {
              case Json.Number(n) => Json.Number(n * 2) // Double all numbers
              case other          => other
            }
          }
          assertTrue(
            result.get("a").get("b").as[BigDecimal] == Right(BigDecimal(2)),
            result.get("c").get(DynamicOptic.root.at(0)).as[BigDecimal] == Right(BigDecimal(4))
          )
        },
        test("transformDown applies function top-down") {
          val json   = Json.Object("x" -> Json.Number(10), "y" -> Json.Array(Json.Number(20)))
          var result = Chunk.empty[String]
          json.transformDown { (path, j) =>
            result = result :+ path.toString
            j
          }
          // Root should be visited before children (root path renders as ".")
          assertTrue(result == Chunk(".", ".x", ".y", ".y[0]"))
        },
        test("transformKeys renames object keys") {
          val json   = Json.Object("old_name" -> Json.Number(1), "another_key" -> Json.Number(2))
          val result = json.transformKeys((_, key) => key.replace("_", "-"))
          assertTrue(
            result.get("old-name").isSuccess,
            result.get("another-key").isSuccess,
            result.get("old_name").isFailure
          )
        }
      ),
      suite("prune/retain methods")(
        test("retain keeps matching elements in arrays") {
          val json   = Json.Array(Json.Number(1), Json.Number(2), Json.Number(3), Json.Number(4))
          val result = json.retainBoth { (_, j) =>
            j match {
              case Json.Number(n) => n > 2
              case _              => true
            }
          }
          assertTrue(result.elements == Chunk(Json.Number(3), Json.Number(4)))
        },
        test("retain keeps matching fields in objects") {
          val json   = Json.Object("a" -> Json.Number(1), "b" -> Json.Number(2), "c" -> Json.Number(3))
          val result = json.retainBoth { (_, j) =>
            j match {
              case Json.Number(n) => n >= 2
              case _              => true
            }
          }
          assertTrue(result.get("a").isFailure, result.get("b").isSuccess, result.get("c").isSuccess)
        },
        test("prune removes matching elements") {
          val json   = Json.Array(Json.Number(1), Json.Null, Json.Number(2), Json.Null)
          val result = json.prune(j => j.is(JsonType.Null))
          assertTrue(result.elements == Chunk(Json.Number(1), Json.Number(2)))
        },
        test("partition splits by value predicate") {
          val json               = Json.Array(Json.Number(1), Json.Number(2), Json.Number(3), Json.Number(4))
          val (result1, result2) = json.partition {
            case Json.Number(n) => n.toInt % 2 == 0
            case _              => false
          }
          assertTrue(
            result1.elements == Chunk(Json.Number(2), Json.Number(4)),
            result2.elements == Chunk(Json.Number(1), Json.Number(3))
          )
        },
        test("partitionPath splits by path predicate") {
          val json = Json.Object(
            "keep" -> Json.Object("a" -> Json.Number(1), "b" -> Json.Number(2)),
            "drop" -> Json.Object("c" -> Json.Number(3))
          )
          val (result1, result2) = json.partitionPath { path =>
            path.nodes.exists {
              case DynamicOptic.Node.Field("keep") => true
              case _                               => false
            }
          }
          assertTrue(
            result1.get("keep").get("a").one == Right(Json.Number(1)),
            result1.get("drop").isFailure,
            result2.get("drop").get("c").one == Right(Json.Number(3)),
            result2.get("keep").isFailure
          )
        },
        test("partitionBoth splits by path and value predicate") {
          val json               = Json.Object("a" -> Json.Number(1), "b" -> Json.String("x"), "c" -> Json.Number(2))
          val (result1, result2) = json.partitionBoth { (path, j) =>
            path.nodes.lastOption.exists {
              case DynamicOptic.Node.Field("a") | DynamicOptic.Node.Field("c") => true
              case _                                                           => false
            } && j.is(JsonType.Number)
          }
          assertTrue(
            result1.get("a").one == Right(Json.Number(1)),
            result1.get("c").one == Right(Json.Number(2)),
            result1.get("b").isFailure,
            result2.get("b").one == Right(Json.String("x")),
            result2.get("a").isFailure
          )
        },
        test("project extracts specific paths") {
          val json = Json.Object(
            "user" -> Json.Object(
              "name"  -> Json.String("Alice"),
              "age"   -> Json.Number(30),
              "email" -> Json.String("alice@example.com")
            ),
            "extra" -> Json.String("ignored")
          )
          val result =
            json.project(DynamicOptic.root.field("user").field("name"), DynamicOptic.root.field("user").field("age"))
          assertTrue(
            result.get("user").get("name").as[String] == Right("Alice"),
            result.get("user").get("age").as[BigDecimal] == Right(BigDecimal(30)),
            result.get("user").get("email").isFailure,
            result.get("extra").isFailure
          )
        }
      ),
      suite("folding methods")(
        test("foldUp accumulates bottom-up") {
          val json = Json.Object(
            "a" -> Json.Number(1),
            "b" -> Json.Object("c" -> Json.Number(2), "d" -> Json.Array(Json.Number(3)))
          )
          val result = json.foldUp(BigDecimal(0)) { (_, j, acc) =>
            j match {
              case Json.Number(n) => acc + n // Sum all numbers
              case _              => acc
            }
          }
          assertTrue(result == BigDecimal(6))
        },
        test("foldDown accumulates top-down") {
          val json1   = Json.Array(Json.Number(1), Json.Number(2), Json.Number(3))
          val json2   = Json.Object("a" -> Json.Number(2), "b" -> Json.Number(3))
          val result1 = json1.foldDown(Chunk.empty[String])((path, _, acc) => acc :+ path.toString)
          val result2 = json2.foldDown(Chunk.empty[String])((path, _, acc) => acc :+ path.toString)
          assertTrue(result1.head == ".", result1.length == 4) &&
          assertTrue(result2.head == ".", result2.length == 3)
        },
        test("foldUpOrFail stops on error") {
          val json   = Json.Array(Json.Number(1), Json.String("oops"), Json.Number(3))
          val result = json.foldUpOrFail(BigDecimal(0)) { (_, j, acc) =>
            j match {
              case Json.Number(n) => Right(acc + n)
              case Json.String(_) => Left(SchemaError("Found a string!"))
              case _              => Right(acc)
            }
          }
          assertTrue(result.isLeft)
        },
        test("foldDownOrFail stops on error") {
          val json   = Json.Object("a" -> Json.Number(1), "b" -> Json.String("error"))
          val result = json.foldDownOrFail(0) { (_, j, acc) =>
            j match {
              case Json.String(s) if s == "error" => Left(SchemaError("Found error"))
              case _                              => Right(acc + 1)
            }
          }
          assertTrue(result.isLeft)
        }
      ),
      suite("query methods (via JsonSelection)")(
        test("query finds matching values") {
          val json = Json.Object(
            "users" -> Json.Array(
              Json.Object("name" -> Json.String("Alice"), "active"   -> Json.Boolean(true)),
              Json.Object("name" -> Json.String("Bob"), "active"     -> Json.Boolean(false)),
              Json.Object("name" -> Json.String("Charlie"), "active" -> Json.Boolean(true))
            )
          )
          val result = json.select.query {
            case Json.Boolean(true) => true
            case _                  => false
          }
          assertTrue(result.size == 2)
        },
        test("query returns empty selection when nothing matches") {
          val json   = Json.Object("a" -> Json.Number(1))
          val result = json.select.query(JsonType.String)
          assertTrue(result.isEmpty)
        },
        test("toKV converts JSON object to path-value pairs") {
          val json = Json.Object(
            "a" -> Json.Number(1),
            "b" -> Json.Object("c" -> Json.Number(2))
          )
          val result = json.toKV
          assertTrue(result.length == 2, result.exists(_._2 == Json.Number(1)), result.exists(_._2 == Json.Number(2)))
        },
        test("toKV converts JSON array to path-value pairs") {
          val json   = Json.Array(Json.Number(1), Json.Object("c" -> Json.Number(2)))
          val result = json.toKV
          assertTrue(result.length == 2, result.exists(_._2 == Json.Number(1)), result.exists(_._2 == Json.Number(2)))
        },
        test("fromKV reconstructs JSON from path-value pairs") {
          val json = Json.Object(
            "a" -> Json.Number(1),
            "b" -> Json.Object("c" -> Json.Number(2))
          )
          val result = Json.fromKV(json.toKV)
          assertTrue(
            result.isRight,
            result.toOption.get.get("a").as[BigDecimal] == Right(BigDecimal(1)),
            result.toOption.get.get("b").get("c").as[BigDecimal] == Right(BigDecimal(2))
          )
        }
      ),
      suite("Json.from constructor")(
        test("from creates Json from encodable value") {
          assertTrue(
            Json.from("hello") == Json.String("hello"),
            Json.from(42) == Json.Number(42),
            Json.from(true) == Json.Boolean(true),
            Json.from(Chunk(1, 2, 3)) == Json.Array(Json.Number(1), Json.Number(2), Json.Number(3))
          )
        }
      )
    ),
    suite("JsonSelection")(
      test("fluent navigation through nested structure") {
        val json = Json.Object(
          "data" -> Json.Object(
            "users" -> Json.Array(
              Json.Object("name" -> Json.String("Alice"), "age" -> Json.Number(30)),
              Json.Object("name" -> Json.String("Bob"), "age"   -> Json.Number(25))
            )
          )
        )
        assertTrue(
          json.get("data").get("users")(0).get("name").as[String] == Right("Alice"),
          json.get("data").get("users")(1).get("age").as[Int] == Right(25)
        )
      },
      test("select type filtering") {
        val json = Json.Object("name" -> Json.String("test"))
        assertTrue(
          json.select(JsonType.Object).isSuccess,
          json.select(JsonType.Array).isEmpty,
          json.get("name").strings.isSuccess
        )
      },
      test("extraction methods") {
        val json = Json.Array(Json.String("a"), Json.String("b"), Json.String("c"))
        assertTrue(
          JsonSelection
            .succeed(json)
            .arrays
            .any
            .toOption
            .map(_.elements)
            .contains(Chunk(Json.String("a"), Json.String("b"), Json.String("c")))
        )
      },
      test("++ combines selections") {
        val selection1 = JsonSelection.succeed(Json.Number(1))
        val selection2 = JsonSelection.succeed(Json.Number(2))
        val result     = selection1 ++ selection2
        assertTrue(result.either == Right(Chunk(Json.Number(1), Json.Number(2))))
      },
      test("++ propagates errors") {
        val selection1 = JsonSelection.fail(SchemaError("error"))
        val selection2 = JsonSelection.succeed(Json.Number(2))
        val result1    = selection1 ++ selection2
        val result2    = selection2 ++ selection1
        assertTrue(result1.isFailure, result2.isFailure)
      },
      test("size operations") {
        val selection1 = JsonSelection.empty
        val selection2 = JsonSelection.succeed(Json.Number(1))
        val selection3 = JsonSelection.succeedMany(Chunk(Json.Number(1), Json.Number(2), Json.Number(3)))
        assertTrue(selection1.size == 0, selection2.size == 1, selection3.size == 3)
      },
      test("all returns single value or wraps multiple in array") {
        val selection1 = JsonSelection.succeed(Json.Number(1))
        val selection2 = JsonSelection.succeedMany(Chunk(Json.Number(1), Json.Number(2)))
        assertTrue(
          selection1.all == Right(Json.Number(1)),
          selection2.all == Right(Json.Array(Json.Number(1), Json.Number(2)))
        )
      },
      test("any returns first value") {
        val selection = JsonSelection.succeedMany(Chunk(Json.Number(1), Json.Number(2), Json.Number(3)))
        assertTrue(selection.any == Right(Json.Number(1)))
      },
      test("toArray wraps values in array") {
        val selection = JsonSelection.succeedMany(Chunk(Json.Number(1), Json.Number(2)))
        assertTrue(selection.toArray == Right(Json.Array(Json.Number(1), Json.Number(2))))
      },
      test("objects/arrays filters by type") {
        val selection = JsonSelection.succeedMany(
          Chunk(
            Json.Object("a" -> Json.Number(1)),
            Json.Array(Json.Number(1)),
            Json.String("hello"),
            Json.Object("b" -> Json.Number(2))
          )
        )
        assertTrue(selection.objects.size == 2, selection.arrays.size == 1)
      },
      test("strings/numbers/booleans filters by type") {
        val selection = JsonSelection.succeedMany(
          Chunk(
            Json.String("hello"),
            Json.Number(42),
            Json.Boolean(true),
            Json.String("world")
          )
        )
        assertTrue(selection.strings.size == 2, selection.numbers.size == 1, selection.booleans.size == 1)
      }
    ),
    suite("JsonDecoder")(
      test("decode primitives") {
        assertTrue(
          Json.String("hello").as[String] == Right("hello"),
          Json.Number(42).as[Int] == Right(42),
          Json.Boolean(true).as[Boolean] == Right(true)
        )
      },
      test("decode Option") {
        assertTrue(
          Json.String("hello").as[Option[String]] == Right(Some("hello")),
          Json.Null.as[Option[String]] == Right(None)
        )
      },
      test("decode Chunk") {
        val json = Json.Array(Json.Number(1), Json.Number(2), Json.Number(3))
        assertTrue(json.as[Chunk[Int]] == Right(Chunk(1, 2, 3)))
      },
      test("decode Map") {
        val json = Json.Object("a" -> Json.Number(1), "b" -> Json.Number(2))
        assertTrue(json.as[Map[String, Int]] == Right(Map("a" -> 1, "b" -> 2)))
      }
    ),
    suite("JsonEncoder")(
      test("encode primitives") {
        assertTrue(
          JsonEncoder[String].encode("hello") == Json.String("hello"),
          JsonEncoder[Int].encode(42) == Json.Number(42),
          JsonEncoder[Boolean].encode(true) == Json.Boolean(true)
        )
      },
      test("encode Option") {
        assertTrue(
          JsonEncoder[Option[String]].encode(Some("hello")) == Json.String("hello"),
          JsonEncoder[Option[String]].encode(None) == Json.Null
        )
      },
      test("encode Chunk") {
        assertTrue(
          JsonEncoder[Chunk[Int]].encode(Chunk(1, 2, 3)) == Json.Array(Json.Number(1), Json.Number(2), Json.Number(3))
        )
      },
      test("encode Map") {
        val json = JsonEncoder[Map[String, Int]].encode(Map("a" -> 1, "b" -> 2))
        assert(json.is(JsonType.Object))(equalTo(true)) &&
        assertTrue(
          json.get("a").as[BigDecimal] == Right(BigDecimal(1)),
          json.get("b").as[BigDecimal] == Right(BigDecimal(2))
        )
      }
    ),
    suite("additional coverage")(
      suite("merge strategies")(
        test("merge with Auto strategy merges objects deeply") {
          val json1  = Json.Object("a" -> Json.Object("x" -> Json.Number(1), "y" -> Json.Number(2)))
          val json2  = Json.Object("a" -> Json.Object("y" -> Json.Number(3), "z" -> Json.Number(4)))
          val result = json1.merge(json2, MergeStrategy.Auto)
          assertTrue(
            result.get("a").get("x").as[BigDecimal] == Right(BigDecimal(1)),
            result.get("a").get("y").as[BigDecimal] == Right(BigDecimal(3)),
            result.get("a").get("z").as[BigDecimal] == Right(BigDecimal(4))
          )
        },
        test("merge with Shallow strategy replaces nested objects") {
          val json1  = Json.Object("a" -> Json.Object("x" -> Json.Number(1), "y" -> Json.Number(2)))
          val json2  = Json.Object("a" -> Json.Object("z" -> Json.Number(3)))
          val result = json1.merge(json2, MergeStrategy.Shallow)
          assertTrue(
            result.get("a").get("x").isFailure,
            result.get("a").get("z").as[BigDecimal] == Right(BigDecimal(3))
          )
        },
        test("merge with Concat strategy concatenates arrays") {
          val json1  = Json.Array(Json.Number(1), Json.Number(2))
          val json2  = Json.Array(Json.Number(3), Json.Number(4))
          val result = json1.merge(json2, MergeStrategy.Concat)
          assertTrue(result.elements == Chunk(Json.Number(1), Json.Number(2), Json.Number(3), Json.Number(4)))
        },
        test("merge non-matching types replaces with right") {
          val json1  = Json.Number(1)
          val json2  = Json.String("hello")
          val result = json1.merge(json2)
          assertTrue(result == json2)
        }
      ),
      suite("encoding methods")(
        test("printBytes produces valid bytes") {
          val json   = Json.Object("key" -> Json.String("value"))
          val result = Json.parse(json.printBytes)
          assertTrue(result.isRight, result.toOption.get == json)
        },
        test("parse from bytes works") {
          val result = Json.parse("""{"name":"Alice"}""".getBytes("UTF-8"))
          assertTrue(result.isRight, result.toOption.get.get("name").as[String] == Right("Alice"))
        },
        test("parse from bytes with config works") {
          val result = Json.parse("""{"name":"Alice"},""".getBytes("UTF-8"), ReaderConfig.withCheckForEndOfInput(false))
          assertTrue(result.isRight, result.toOption.get.get("name").as[String] == Right("Alice"))
        },
        test("printTo writes to ByteBuffer") {
          val json   = Json.Object("a" -> Json.Number(1))
          val result = java.nio.ByteBuffer.allocate(1024)
          json.printTo(result)
          result.flip()
          val bytes = new Array[Byte](result.remaining())
          result.get(bytes)
          assertTrue(new String(bytes, "UTF-8") == """{"a":1}""")
        },
        test("parse from ByteBuffer works") {
          val result = Json.parse(java.nio.ByteBuffer.wrap("""{"name":"Alice"}""".getBytes("UTF-8")))
          assertTrue(result.isRight, result.toOption.get.get("name").as[String] == Right("Alice"))
        },
        test("parse from ByteBuffer with config works") {
          val result = Json.parse(
            java.nio.ByteBuffer.wrap("""{"name":"Alice"},""".getBytes("UTF-8")),
            ReaderConfig.withCheckForEndOfInput(false)
          )
          assertTrue(result.isRight, result.toOption.get.get("name").as[String] == Right("Alice"))
        },
        test("printTo with config writes to ByteBuffer") {
          val json   = Json.Object("a" -> Json.Number(1))
          val result = java.nio.ByteBuffer.allocate(1024)
          json.printTo(result, WriterConfig.withIndentionStep2)
          result.flip()
          val bytes = new Array[Byte](result.remaining())
          result.get(bytes)
          assertTrue(
            new String(bytes, "UTF-8") ==
              """{
                |  "a": 1
                |}""".stripMargin
          )
        },
        test("parse from CharSequence works") {
          val result = Json.parse(new java.lang.StringBuilder("""{"name":"Alice"}"""))
          assertTrue(result.isRight, result.toOption.get.get("name").as[String] == Right("Alice"))
        },
        test("parse from CharSequence with config works") {
          val result =
            Json.parse(new java.lang.StringBuilder("""{"name":"Alice"},"""), ReaderConfig.withCheckForEndOfInput(false))
          assertTrue(result.isRight, result.toOption.get.get("name").as[String] == Right("Alice"))
        },
        test("unsafe parse from string works") {
          val result = Json.parseUnsafe("""{"name":"Alice"}""")
          assertTrue(result.get("name").as[String] == Right("Alice"))
        }
      ),
      suite("DynamicOptic advanced operations")(
        test("get with mapValues returns all object values") {
          val json      = Json.Object("a" -> Json.Number(1), "b" -> Json.Number(2), "c" -> Json.Number(3))
          val selection = json.get(DynamicOptic.mapValues)
          assertTrue(
            selection.either.toOption.get.toSet == Set[Json](Json.Number(1), Json.Number(2), Json.Number(3))
          )
        },
        test("get with mapKeys returns all object keys as strings") {
          val json      = Json.Object("a" -> Json.Number(1), "b" -> Json.Number(2))
          val selection = json.get(DynamicOptic.mapKeys)
          assertTrue(selection.either.toOption.get.toSet == Set[Json](Json.String("a"), Json.String("b")))
        },
        test("modify with mapValues transforms all values") {
          val json    = Json.Object("a" -> Json.Number(1), "b" -> Json.Number(2))
          val updated = json.modify(DynamicOptic.mapValues) {
            case Json.Number(n) => Json.Number(n * 10)
            case other          => other
          }
          assertTrue(
            updated.get("a").as[BigDecimal] == Right(BigDecimal(10)),
            updated.get("b").as[BigDecimal] == Right(BigDecimal(20))
          )
        },
        test("get with atIndices returns multiple elements") {
          val json      = Json.Array(Json.String("a"), Json.String("b"), Json.String("c"), Json.String("d"))
          val selection = json.get(DynamicOptic.root.atIndices(0, 2))
          assertTrue(selection.either == Right(Chunk(Json.String("a"), Json.String("c"))))
        },
        test("modify with atIndices transforms specific elements") {
          val json    = Json.Array(Json.Number(1), Json.Number(2), Json.Number(3), Json.Number(4))
          val updated = json.modify(DynamicOptic.root.atIndices(1, 3)) {
            case Json.Number(n) => Json.Number(n * 10)
            case other          => other
          }
          assertTrue(
            updated.get(0).one == Right(Json.Number(1)),
            updated.get(1).one == Right(Json.Number(20)),
            updated.get(2).one == Right(Json.Number(3)),
            updated.get(3).one == Right(Json.Number(40))
          )
        }
      ),
      suite("toKV edge cases")(
        test("toKV handles empty object") {
          val json   = Json.Object()
          val result = json.toKV
          assertTrue(result.length == 1, result.head._2 == Json.Object())
        },
        test("toKV handles empty array") {
          val json   = Json.Array()
          val result = json.toKV
          assertTrue(result.length == 1, result.head._2 == Json.Array())
        },
        test("fromKVUnsafe works correctly") {
          val result = Json.fromKVUnsafe(
            Seq(
              (DynamicOptic.root.field("a"), Json.Number(1)),
              (DynamicOptic.root.field("b").field("c"), Json.Number(2))
            )
          )
          assertTrue(
            result.get("a").as[BigDecimal] == Right(BigDecimal(1)),
            result.get("b").get("c").as[BigDecimal] == Right(BigDecimal(2))
          )
        },
        test("fromKV with empty seq returns Null") {
          val result = Json.fromKV(Seq.empty)
          assertTrue(result == Right(Json.Null))
        },
        test("fromKVUnsafe with single path creates structure") {
          val result = Json.fromKVUnsafe(Seq((DynamicOptic.root.field("a"), Json.Number(1))))
          assertTrue(result == Json.Object("a" -> Json.Number(1)))
        },
        test("fromKVUnsafe with multiple paths") {
          val result = Json.fromKVUnsafe(
            Seq(
              (DynamicOptic.root.field("a"), Json.Number(1)),
              (DynamicOptic.root.field("b"), Json.Number(2))
            )
          )
          assertTrue(result.get("a").one == Right(Json.Number(1))) &&
          assertTrue(result.get("b").one == Right(Json.Number(2)))
        }
      ),
      suite("Json path operations")(
        test("get with DynamicOptic for non-existent path") {
          val json = Json.Object("a" -> Json.Number(1))
          assertTrue(json.get(DynamicOptic.root.field("nonexistent")).isEmpty)
        },
        test("modifyAtPath returns unchanged when path doesn't exist in array") {
          val json   = Json.Array(Json.Number(1), Json.Number(2))
          val result = json.modify(DynamicOptic.root.at(10))(_ => Json.Number(99))
          assertTrue(result == json)
        },
        test("modifyAtPath returns unchanged when path doesn't exist in object") {
          val json   = Json.Object("a" -> Json.Number(1))
          val result = json.modify(DynamicOptic.root.field("nonexistent"))(_ => Json.Number(99))
          assertTrue(result == json)
        },
        test("modifyOrFail fails when path doesn't exist") {
          val json   = Json.Object("a" -> Json.Number(1))
          val result = json.modifyOrFail(DynamicOptic.root.field("nonexistent")) { case j => j }
          assertTrue(result.isLeft)
        },
        test("modifyOrFail fails when partial function not defined") {
          val json   = Json.Object("a" -> Json.Number(1))
          val result = json.modifyOrFail(DynamicOptic.root.field("a")) { case Json.String(_) => Json.String("xxx") }
          assertTrue(result.isLeft)
        },
        test("insertAtPath succeeds for new field") {
          val json   = Json.Object("a" -> Json.Number(1))
          val result = json.insert(DynamicOptic.root.field("b"), Json.Number(2))
          assertTrue(result == Json.Object("a" -> Json.Number(1), "b" -> Json.Number(2)))
        },
        test("insertAtPath returns unchanged when path exists") {
          val json   = Json.Object("a" -> Json.Number(1))
          val result = json.insert(DynamicOptic.root.field("a"), Json.Number(99))
          assertTrue(result == json)
        },
        test("insertOrFail fails when path exists") {
          val json   = Json.Object("a" -> Json.Number(1))
          val result = json.insertOrFail(DynamicOptic.root.field("a"), Json.Number(99))
          assertTrue(result.isLeft)
        },
        test("insertOrFail fails for deeply nested non-existent parent") {
          val json   = Json.Object("a" -> Json.Number(1))
          val result = json.insertOrFail(DynamicOptic.root.field("nonexistent").field("child"), Json.Number(99))
          assertTrue(result.isLeft)
        }
      ),
      suite("Json parse error branches")(
        test("parse handles invalid JSON") {
          assertTrue(Json.parse("{invalid json}").isLeft)
        },
        test("parse handles truncated JSON") {
          assertTrue(Json.parse("{\"a\": ").isLeft)
        },
        test("parse handles empty input") {
          assertTrue(Json.parse("").isLeft)
        },
        test("parse with config handles invalid JSON") {
          assertTrue(Json.parse("{broken", ReaderConfig).isLeft)
        },
        test("parseUnsafe throws on invalid JSON") {
          val result = try {
            Json.parseUnsafe("{invalid}")
            false
          } catch {
            case _: SchemaError => true
            case _: Throwable   => false
          }
          assertTrue(result)
        }
      ),
      suite("Json fold operations")(
        test("foldUpOrFail succeeds with valid function") {
          val json   = Json.Object("a" -> Json.Number(1), "b" -> Json.Number(2))
          val result = json.foldUpOrFail(0) { (_, j, acc) =>
            j match {
              case Json.Number(n) => Right(acc + n.toInt)
              case _              => Right(acc)
            }
          }
          assertTrue(result == Right(3))
        },
        test("foldUpOrFail stops on error") {
          val json   = Json.Object("a" -> Json.Number(1), "b" -> Json.String("not a number"))
          val result = json.foldUpOrFail(0) { (_, j, acc) =>
            j match {
              case Json.Number(n) => Right(acc + n.toInt)
              case _: Json.String => Left(SchemaError("String not allowed"))
              case _              => Right(acc)
            }
          }
          assertTrue(result.isLeft)
        },
        test("foldDownOrFail succeeds with valid function") {
          val json   = Json.Object("a" -> Json.Number(1), "b" -> Json.Number(2))
          val result = json.foldDownOrFail(0) { (_, j, acc) =>
            j match {
              case Json.Number(n) => Right(acc + n.toInt)
              case _              => Right(acc)
            }
          }
          assertTrue(result == Right(3))
        },
        test("foldDownOrFail stops on error") {
          val json   = Json.Object("a" -> Json.String("error first"), "b" -> Json.Number(2))
          val result = json.foldDownOrFail(0) { (_, j, acc) =>
            j match {
              case Json.Number(n) => Right(acc + n.toInt)
              case _: Json.String => Left(SchemaError("String not allowed"))
              case _              => Right(acc)
            }
          }
          assertTrue(result.isLeft)
        },
        test("foldUpOrFail on nested structure") {
          val json   = Json.Object("outer" -> Json.Object("inner" -> Json.Number(5)))
          val result = json.foldUpOrFail(0) { (_, j, acc) =>
            j match {
              case Json.Number(n) => Right(acc + n.toInt)
              case _              => Right(acc)
            }
          }
          assertTrue(result == Right(5))
        },
        test("foldDownOrFail on array") {
          val json   = Json.Array(Json.Number(1), Json.Number(2), Json.Number(3))
          val result = json.foldDownOrFail(0) { (_, j, acc) =>
            j match {
              case Json.Number(n) => Right(acc + n.toInt)
              case _              => Right(acc)
            }
          }
          assertTrue(result == Right(6))
        }
      ),
      suite("Json modifyAtPathOrFail additional branches")(
        test("modifyAtPathOrFail with nested object path succeeds") {
          val json   = Json.Object("outer" -> Json.Object("inner" -> Json.Number(1)))
          val result = json.modifyOrFail(DynamicOptic.root.field("outer").field("inner")) { case Json.Number(_) =>
            Json.Number(99)
          }
          assertTrue(result == Right(Json.Object("outer" -> Json.Object("inner" -> Json.Number(99)))))
        },
        test("modifyAtPathOrFail with nested array path succeeds") {
          val json   = Json.Object("arr" -> Json.Array(Json.Number(1), Json.Number(2)))
          val result = json.modifyOrFail(DynamicOptic.root.field("arr").at(0)) { case Json.Number(_) => Json.Number(9) }
          assertTrue(result == Right(Json.Object("arr" -> Json.Array(Json.Number(9), Json.Number(2)))))
        },
        test("modifyAtPathOrFail fails when inner path not found") {
          val json   = Json.Object("outer" -> Json.Object("inner" -> Json.Number(1)))
          val result = json.modifyOrFail(DynamicOptic.root.field("outer").field("nonexistent")) { case j => j }
          assertTrue(result.isLeft)
        },
        test("modifyAtPathOrFail on elements path") {
          val json   = Json.Array(Json.Number(1), Json.Number(2))
          val result = json.modifyOrFail(DynamicOptic.root.elements) { case Json.Number(n) => Json.Number(n * 10) }
          assertTrue(result == Right(Json.Array(Json.Number(10), Json.Number(20))))
        },
        test("modifyAtPathOrFail on mapValues path") {
          val json   = Json.Object("a" -> Json.Number(1), "b" -> Json.Number(2))
          val result = json.modifyOrFail(DynamicOptic.root.mapValues) { case Json.Number(n) => Json.Number(n * 10) }
          assertTrue(result.isRight)
        }
      ),
      suite("Json insertAtPathOrFail additional branches")(
        test("insertAtPathOrFail into nested object succeeds") {
          val json   = Json.Object("outer" -> Json.Object("inner" -> Json.Number(1)))
          val result = json.insertOrFail(DynamicOptic.root.field("outer").field("newField"), Json.Number(99))
          assertTrue(result.isRight)
        },
        test("insertAtPathOrFail into nested array succeeds") {
          val json   = Json.Object("arr" -> Json.Array(Json.Number(1), Json.Number(2)))
          val result = json.insertOrFail(DynamicOptic.root.field("arr").at(2), Json.Number(3))
          assertTrue(result.isRight)
        },
        test("insertAtPathOrFail at array end extends array") {
          val json   = Json.Array(Json.Number(1))
          val result = json.insertOrFail(DynamicOptic.root.at(1), Json.Number(2))
          assertTrue(result.isRight)
        },
        test("insertAtPathOrFail fails when nested field exists") {
          val json   = Json.Object("outer" -> Json.Object("inner" -> Json.Number(1)))
          val result = json.insertOrFail(DynamicOptic.root.field("outer").field("inner"), Json.Number(99))
          assertTrue(result.isLeft)
        },
        test("insertAtPathOrFail fails when object is not an object") {
          val json   = Json.Object("a" -> Json.Number(1))
          val result = json.insertOrFail(DynamicOptic.root.field("a").field("b"), Json.Number(99))
          assertTrue(result.isLeft)
        }
      ),
      suite("Json get on primitives")(
        test("get field on non-object returns error selection") {
          val json   = Json.Number(42)
          val result = json.get("field")
          assertTrue(result.isFailure)
        },
        test("get index on non-array returns error selection") {
          val json   = Json.String("hello")
          val result = json.get(0)
          assertTrue(result.isFailure)
        }
      ),
      suite("Json Object compare")(
        test("compare objects with same keys different order") {
          val obj1 = Json.Object("b" -> Json.Number(2), "a" -> Json.Number(1))
          val obj2 = Json.Object("a" -> Json.Number(1), "b" -> Json.Number(2))
          assertTrue(obj1.compare(obj2) == 0)
        },
        test("compare objects with different values") {
          val obj1 = Json.Object("a" -> Json.Number(1))
          val obj2 = Json.Object("a" -> Json.Number(2))
          assertTrue(obj1.compare(obj2) < 0)
        },
        test("compare objects with different keys") {
          val obj1 = Json.Object("a" -> Json.Number(1))
          val obj2 = Json.Object("b" -> Json.Number(1))
          assertTrue(obj1.compare(obj2) != 0)
        },
        test("compare objects with different number of keys") {
          val obj1 = Json.Object("a" -> Json.Number(1))
          val obj2 = Json.Object("a" -> Json.Number(1), "b" -> Json.Number(2))
          assertTrue(obj1.compare(obj2) != 0)
        }
      ),
      suite("JsonSelection additional methods")(
        test("one returns error for empty selection") {
          val selection = JsonSelection.empty
          assertTrue(selection.one.isLeft)
        },
        test("one returns error for multiple values") {
          val selection = JsonSelection.succeedMany(Chunk(Json.Number(1), Json.Number(2)))
          assertTrue(selection.one.isLeft)
        },
        test("one returns value for single element") {
          val selection = JsonSelection.succeed(Json.Number(42))
          assertTrue(selection.one == Right(Json.Number(42)))
        },
        test("collect extracts matching values") {
          val selection = JsonSelection.succeedMany(Chunk(Json.Number(1), Json.String("a"), Json.Number(2)))
          val result    = selection.collect { case Json.Number(n) => n }
          assertTrue(result == Right(Chunk(BigDecimal(1), BigDecimal(2))))
        },
        test("orElse returns alternative on failure") {
          val selection1 = JsonSelection.fail(SchemaError("error"))
          val selection2 = JsonSelection.succeed(Json.Number(42))
          val result     = selection1.orElse(selection2)
          assertTrue(result.one == Right(Json.Number(42)))
        },
        test("orElse returns original on success") {
          val selection1 = JsonSelection.succeed(Json.Number(1))
          val selection2 = JsonSelection.succeed(Json.Number(2))
          val result     = selection1.orElse(selection2)
          assertTrue(result.one == Right(Json.Number(1)))
        },
        test("getOrElse returns values on success") {
          val selection = JsonSelection.succeedMany(Chunk(Json.Number(1), Json.Number(2)))
          val result    = selection.getOrElse(Chunk(Json.Null))
          assertTrue(result == Chunk(Json.Number(1), Json.Number(2)))
        },
        test("getOrElse returns default on failure") {
          val selection = JsonSelection.fail(SchemaError("error"))
          val result    = selection.getOrElse(Chunk(Json.Null))
          assertTrue(result == Chunk(Json.Null))
        },
        test("map transforms all values") {
          val selection = JsonSelection.succeedMany(Chunk(Json.Number(1), Json.Number(2)))
          val result    = selection.map {
            case Json.Number(n) => Json.Number(n * 2)
            case other          => other
          }
          assertTrue(result.either == Right(Chunk(Json.Number(2), Json.Number(4))))
        },
        test("flatMap chains selections") {
          val json = Json.Object(
            "users" -> Json.Array(
              Json.Object("name" -> Json.String("Alice")),
              Json.Object("name" -> Json.String("Bob"))
            )
          )
          // Get all user names using flatMap
          val result = json.get(DynamicOptic.root.field("users").elements.field("name"))
          assertTrue(result.size == 2)
        },
        test("filter keeps matching values") {
          val selection = JsonSelection.succeedMany(Chunk(Json.Number(1), Json.Number(2), Json.Number(3)))
          val result    = selection.filter {
            case Json.Number(n) => n > 1
            case _              => false
          }
          assertTrue(result.either == Right(Chunk(Json.Number(2), Json.Number(3))))
        },
        test("as decodes single value") {
          val selection = JsonSelection.succeed(Json.Number(42))
          assertTrue(selection.as[Int] == Right(42))
        },
        test("asAll decodes all values") {
          val selection = JsonSelection.succeedMany(Chunk(Json.Number(1), Json.Number(2), Json.Number(3)))
          assertTrue(selection.asAll[Int] == Right(Chunk(1, 2, 3)))
        },
        test("nulls filters to only nulls") {
          val selection = JsonSelection.succeedMany(Chunk(Json.Null, Json.Number(1), Json.Null))
          assertTrue(selection.nulls.size == 2)
        },
        test("error returns Some on failure") {
          val selection = JsonSelection.fail(SchemaError("test error"))
          assertTrue(selection.error.isDefined, selection.error.get.message == "test error")
        },
        test("error returns None on success") {
          val selection = JsonSelection.succeed(Json.Number(1))
          assertTrue(selection.error.isEmpty)
        },
        test("values returns Some on success") {
          val selection = JsonSelection.succeed(Json.Number(1))
          assertTrue(selection.values.isDefined)
        },
        test("values returns None on failure") {
          val selection = JsonSelection.fail(SchemaError("error"))
          assertTrue(selection.values.isEmpty)
        },
        test("any.toOption returns first value") {
          val selection = JsonSelection.succeedMany(Chunk(Json.Number(1), Json.Number(2)))
          assertTrue(selection.any.toOption.contains(Json.Number(1)))
        },
        test("any.toOption returns None for empty") {
          val selection = JsonSelection.empty
          assertTrue(selection.any.toOption.isEmpty)
        },
        test("isEmpty returns true on failure") {
          val selection = JsonSelection.fail(SchemaError("error"))
          assertTrue(selection.isEmpty)
        },
        test("numbers/booleans/nulls type filtering") {
          val selection1 = JsonSelection.succeed(Json.Number(42))
          val selection2 = JsonSelection.succeed(Json.Boolean(true))
          val selection3 = JsonSelection.succeed(Json.Null)
          assertTrue(
            selection1.numbers.isSuccess,
            selection1.booleans.isEmpty,
            selection2.booleans.isSuccess,
            selection2.nulls.isEmpty,
            selection3.nulls.isSuccess,
            selection3.numbers.isEmpty
          )
        },
        test("query with predicate finds all matching values recursively") {
          val json = Json.Object(
            "name"  -> Json.String("Alice"),
            "age"   -> Json.Number(30),
            "items" -> Json.Array(Json.String("apple"), Json.Number(42), Json.String("banana"))
          )
          val result = json.select.query(JsonType.String).toChunk
          assertTrue(
            result.length == 3,
            result.contains(Json.String("Alice")),
            result.contains(Json.String("apple")),
            result.contains(Json.String("banana"))
          )
        },
        test("query with value predicate finds matching values") {
          val json   = Json.Array(Json.Number(1), Json.Number(10), Json.Number(5), Json.Number(20))
          val result = json.select.query(_.unwrap(JsonType.Number).exists(_ > 5)).toChunk
          assertTrue(
            result.length == 2,
            result.contains(Json.Number(10)),
            result.contains(Json.Number(20))
          )
        },
        test("queryPath finds values at paths matching predicate") {
          val json = Json.Object(
            "user"     -> Json.Object("name" -> Json.String("Alice"), "age" -> Json.Number(30)),
            "metadata" -> Json.Object("created" -> Json.String("2024-01-01"))
          )
          val result = json.select.queryPath { path =>
            path.nodes.headOption.exists {
              case f: DynamicOptic.Node.Field => f.name == "user"
              case _                          => false
            }
          }.toChunk
          assertTrue(result.nonEmpty)
        },
        test("queryPath finds values at specific indices") {
          val json   = Json.Array(Json.String("zero"), Json.String("one"), Json.String("two"))
          val result = json.select.queryPath { path =>
            path.nodes.exists {
              case idx: DynamicOptic.Node.AtIndex => idx.index == 1
              case _                              => false
            }
          }.toChunk
          assertTrue(result == Chunk(Json.String("one")))
        },
        test("queryBoth finds values matching both path and value predicates") {
          val json = Json.Object(
            "numbers" -> Json.Array(Json.Number(1), Json.Number(100), Json.Number(5)),
            "strings" -> Json.Array(Json.String("a"), Json.String("b"))
          )
          val result = json.select.queryBoth { (path, value) =>
            val inNumbersField = path.nodes.exists {
              case f: DynamicOptic.Node.Field => f.name == "numbers"
              case _                          => false
            }
            val isLargeNumber = value.unwrap(JsonType.Number).exists(_ > 10)
            inNumbersField && isLargeNumber
          }.toChunk
          assertTrue(result == Chunk(Json.Number(100)))
        },
        test("queryBoth returns empty when no matches") {
          val json   = Json.Object("a" -> Json.Number(1))
          val result = json.select.queryBoth((_, value) => value.is(JsonType.String)).toChunk
          assertTrue(result.isEmpty)
        }
      ),
      suite("DynamicValue conversion edge cases")(
        test("fromDynamicValue handles Variant") {
          val dv     = DynamicValue.Variant("SomeCase", DynamicValue.Primitive(PrimitiveValue.Int(42)))
          val result = Json.fromDynamicValue(dv)
          assertTrue(result.get("SomeCase").as[BigDecimal] == Right(BigDecimal(42)))
        },
        test("fromDynamicValue handles Sequence") {
          val dv = DynamicValue.Sequence(
            Chunk(
              DynamicValue.Primitive(PrimitiveValue.Int(1)),
              DynamicValue.Primitive(PrimitiveValue.Int(2))
            )
          )
          val result = Json.fromDynamicValue(dv)
          assertTrue(result.elements == Chunk(Json.Number(1), Json.Number(2)))
        },
        test("fromDynamicValue handles Map with string keys") {
          val dv = DynamicValue.Map(
            Chunk(
              (DynamicValue.Primitive(PrimitiveValue.String("a")), DynamicValue.Primitive(PrimitiveValue.Int(1))),
              (DynamicValue.Primitive(PrimitiveValue.String("b")), DynamicValue.Primitive(PrimitiveValue.Int(2)))
            )
          )
          val result = Json.fromDynamicValue(dv)
          assertTrue(
            result.get("a").as[BigDecimal] == Right(BigDecimal(1)),
            result.get("b").as[BigDecimal] == Right(BigDecimal(2))
          )
        },
        test("fromDynamicValue handles Map with non-string keys as array of pairs") {
          val dv = DynamicValue.Map(
            Chunk(
              (DynamicValue.Primitive(PrimitiveValue.Int(1)), DynamicValue.Primitive(PrimitiveValue.String("one"))),
              (DynamicValue.Primitive(PrimitiveValue.Int(2)), DynamicValue.Primitive(PrimitiveValue.String("two")))
            )
          )
          val result = Json.fromDynamicValue(dv)
          assertTrue(result.elements.length == 2)
        },
        test("toDynamicValue converts Long when out of Int range") {
          val json   = Json.Number(Long.MaxValue)
          val result = json.toDynamicValue
          assertTrue(result match {
            case DynamicValue.Primitive(pv: PrimitiveValue.Long) => pv.value == Long.MaxValue
            case _                                               => false
          })
        },
        test("toDynamicValue converts BigDecimal for decimals") {
          val json   = Json.Number(123.456)
          val result = json.toDynamicValue
          assertTrue(result match {
            case DynamicValue.Primitive(pv: PrimitiveValue.BigDecimal) => pv.value == BigDecimal(123.456)
            case _                                                     => false
          })
        },
        test("toDynamicValue converts arrays") {
          val json   = Json.Array(Json.Number(1), Json.Number(2))
          val result = json.toDynamicValue
          assertTrue(result match {
            case DynamicValue.Sequence(elems) => elems.length == 2
            case _                            => false
          })
        },
        test("toDynamicValue converts objects") {
          val json   = Json.Object("a" -> Json.Number(1))
          val result = json.toDynamicValue
          result match {
            case DynamicValue.Record(fields) => assertTrue(fields.length == 1, fields.head._1 == "a")
            case _                           => assertTrue(false)
          }
        }
      ),
      suite("SchemaError with path")(
        test("atField adds field to path") {
          val error = SchemaError("test").atField("foo")
          assertTrue(error.errors.head.source.toString.contains("foo"))
        },
        test("atIndex adds index to path") {
          val error = SchemaError("test").atIndex(5)
          assertTrue(error.errors.head.source.toString.contains("5"))
        },
        test("chained path building") {
          val error  = SchemaError("test").atField("users").atIndex(0).atField("name")
          val result = error.errors.head.source.toString
          assertTrue(
            result.contains("users"),
            result.contains("0"),
            result.contains("name")
          )
        }
      ),
      suite("comparison edge cases")(
        test("compare same type values") {
          assertTrue(
            Json.Number(1).compare(Json.Number(2)) < 0,
            Json.Number(2).compare(Json.Number(1)) > 0,
            Json.Number(1).compare(Json.Number(1)) == 0,
            Json.String("a").compare(Json.String("b")) < 0,
            Json.Boolean(false).compare(Json.Boolean(true)) < 0
          )
        },
        test("compare arrays element by element") {
          assertTrue(
            Json.Array(Json.Number(1)).compare(Json.Array(Json.Number(2))) < 0,
            Json.Array(Json.Number(1), Json.Number(2)).compare(Json.Array(Json.Number(1))) > 0
          )
        },
        test("compare objects by sorted keys") {
          val obj1 = Json.Object("a" -> Json.Number(1))
          val obj2 = Json.Object("b" -> Json.Number(1))
          assertTrue(obj1.compare(obj2) < 0)
        }
      ),
      suite("constructors")(
        test("Json.True and Json.False constants") {
          assertTrue(Json.True == Json.Boolean(true), Json.False == Json.Boolean(false))
        },
        test("Json.Number with different representations") {
          assertTrue(
            Json.Number(42) == Json.Number(42),
            Json.Number(3.14) == Json.Number(3.14)
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
          val json   = Json.Object("outer_key" -> Json.Object("inner_key" -> Json.Number(1)))
          val result = json.transformKeys((_, k) => k.toUpperCase)
          assertTrue(
            result.get("OUTER_KEY").isSuccess,
            result.get("OUTER_KEY").get("INNER_KEY").isSuccess
          )
        },
        test("transformKeys works on arrays containing objects") {
          val json = Json.Array(
            Json.Object("snake_case"  -> Json.Number(1)),
            Json.Object("another_key" -> Json.Number(2))
          )
          val result = json.transformKeys((_, k) => k.replace("_", "-"))
          assertTrue(
            result.get(0).get("snake-case").isSuccess,
            result.get(1).get("another-key").isSuccess
          )
        }
      ),
      suite("retain/prune/partition edge cases")(
        test("retain on primitives returns unchanged") {
          val json   = Json.Number(42)
          val result = json.retainBoth((_, _) => true)
          assertTrue(result == json)
        },
        test("prune on primitives returns unchanged") {
          val json   = Json.Number(42)
          val result = json.pruneBoth((_, _) => false)
          assertTrue(result == json)
        },
        test("partition on primitives") {
          val json               = Json.String("hello")
          val (result1, result2) = json.partition(_.is(JsonType.String))
          assertTrue(result1 == json, result2 == Json.Null)
        }
      ),
      suite("project edge cases")(
        test("project with empty paths returns Null") {
          val json   = Json.Object("a" -> Json.Number(1))
          val result = json.project()
          assertTrue(result == Json.Null)
        },
        test("project with non-existent paths") {
          val json   = Json.Object("a" -> Json.Number(1))
          val result = json.project(DynamicOptic.root.field("nonexistent"))
          assertTrue(result == Json.Null)
        }
      ),
      suite("AtMapKey operations")(
        test("get with atKey retrieves value by key") {
          val json   = Json.Object("alice" -> Json.Number(1), "bob" -> Json.Number(2))
          val result = json.get(DynamicOptic.root.atKey("alice")(Schema.string))
          assertTrue(result.one == Right(Json.Number(1)))
        },
        test("get with atKey returns empty for missing key") {
          val json   = Json.Object("alice" -> Json.Number(1))
          val result = json.get(DynamicOptic.root.atKey("missing")(Schema.string))
          assertTrue(result.isEmpty)
        },
        test("modify with atKey updates value at key") {
          val json   = Json.Object("alice" -> Json.Number(1), "bob" -> Json.Number(2))
          val result = json.modify(DynamicOptic.root.atKey("alice")(Schema.string)) {
            case Json.Number(n) => Json.Number(n * 10)
            case other          => other
          }
          assertTrue(
            result.get("alice").as[BigDecimal] == Right(BigDecimal(10)),
            result.get("bob").as[BigDecimal] == Right(BigDecimal(2))
          )
        },
        test("modify with atKey does nothing for missing key") {
          val json   = Json.Object("alice" -> Json.Number(1))
          val result = json.modify(DynamicOptic.root.atKey("missing")(Schema.string))(_ => Json.Number(99))
          assertTrue(result == json)
        }
      ),
      suite("AtMapKeys operations")(
        test("get with atKeys retrieves multiple values") {
          val json   = Json.Object("a" -> Json.Number(1), "b" -> Json.Number(2), "c" -> Json.Number(3))
          val result = json.get(DynamicOptic.root.atKeys("a", "c")(Schema.string))
          assertTrue(result.either == Right(Chunk(Json.Number(1), Json.Number(3))))
        },
        test("get with atKeys returns only existing keys") {
          val json   = Json.Object("a" -> Json.Number(1), "b" -> Json.Number(2))
          val result = json.get(DynamicOptic.root.atKeys("a", "missing", "b")(Schema.string))
          assertTrue(result.either == Right(Chunk(Json.Number(1), Json.Number(2))))
        },
        test("modify with atKeys updates multiple values") {
          val json   = Json.Object("a" -> Json.Number(1), "b" -> Json.Number(2), "c" -> Json.Number(3))
          val result = json.modify(DynamicOptic.root.atKeys("a", "c")(Schema.string)) {
            case Json.Number(n) => Json.Number(n * 10)
            case other          => other
          }
          assertTrue(
            result.get("a").as[BigDecimal] == Right(BigDecimal(10)),
            result.get("b").as[BigDecimal] == Right(BigDecimal(2)),
            result.get("c").as[BigDecimal] == Right(BigDecimal(30))
          )
        },
        test("get with atKeys on non-object returns empty") {
          val json   = Json.Array(Json.Number(1), Json.Number(2))
          val result = json.get(DynamicOptic.root.atKeys("a")(Schema.string))
          assertTrue(result.isEmpty)
        }
      ),
      suite("Elements delete operations")(
        test("delete with elements removes all array elements") {
          val json   = Json.Array(Json.Number(1), Json.Number(2), Json.Number(3))
          val result = json.delete(DynamicOptic.elements)
          assertTrue(result == Json.Array())
        },
        test("delete with elements on non-array returns unchanged") {
          val json   = Json.Object("a" -> Json.Number(1))
          val result = json.delete(DynamicOptic.elements)
          assertTrue(result == json)
        },
        test("delete nested elements through field path") {
          val json   = Json.Object("items" -> Json.Array(Json.Number(1), Json.Number(2), Json.Number(3)))
          val result = json.delete(DynamicOptic.root.field("items").elements)
          assertTrue(result == Json.Object("items" -> Json.Array()))
        }
      ),
      suite("Nested delete operations")(
        test("delete nested field through object path") {
          val json   = Json.Object("user" -> Json.Object("name" -> Json.String("Alice"), "age" -> Json.Number(30)))
          val result = json.delete(DynamicOptic.root.field("user").field("name"))
          assertTrue(
            result.get("user").get("age").as[BigDecimal] == Right(BigDecimal(30)),
            result.get("user").get("name").isEmpty
          )
        },
        test("delete nested element through array path") {
          val json   = Json.Object("items" -> Json.Array(Json.Number(1), Json.Number(2), Json.Number(3)))
          val result = json.delete(DynamicOptic.root.field("items").at(1))
          assertTrue(result.get("items").toChunk == Chunk(Json.Array(Json.Number(1), Json.Number(3))))
        },
        test("delete deeply nested field") {
          val json   = Json.Object("a" -> Json.Object("b" -> Json.Object("c" -> Json.Number(1), "d" -> Json.Number(2))))
          val result = json.delete(DynamicOptic.root.field("a").field("b").field("c"))
          assertTrue(
            result.get("a").get("b").get("d").as[BigDecimal] == Right(BigDecimal(2)),
            result.get("a").get("b").get("c").isEmpty
          )
        }
      ),
      suite("modifyOrFail with Elements and MapValues")(
        test("modifyOrFail with elements succeeds when all match") {
          val json   = Json.Array(Json.Number(1), Json.Number(2), Json.Number(3))
          val result = json.modifyOrFail(DynamicOptic.elements) { case Json.Number(n) => Json.Number(n * 2) }
          assertTrue(result == Right(Json.Array(Json.Number(2), Json.Number(4), Json.Number(6))))
        },
        test("modifyOrFail with elements fails when partial function not defined") {
          val json   = Json.Array(Json.Number(1), Json.String("not a number"), Json.Number(3))
          val result = json.modifyOrFail(DynamicOptic.elements) { case Json.Number(n) => Json.Number(n * 2) }
          assertTrue(result.isLeft)
        },
        test("modifyOrFail with mapValues succeeds when all match") {
          val json   = Json.Object("a" -> Json.Number(1), "b" -> Json.Number(2))
          val result = json.modifyOrFail(DynamicOptic.mapValues) { case Json.Number(n) => Json.Number(n * 10) }
          assertTrue(
            result.map(_.get("a").as[BigDecimal]) == Right(Right(BigDecimal(10))),
            result.map(_.get("b").as[BigDecimal]) == Right(Right(BigDecimal(20)))
          )
        },
        test("modifyOrFail with mapValues fails when partial function not defined") {
          val json   = Json.Object("a" -> Json.Number(1), "b" -> Json.String("not a number"))
          val result = json.modifyOrFail(DynamicOptic.mapValues) { case Json.Number(n) => Json.Number(n * 10) }
          assertTrue(result.isLeft)
        },
        test("modifyOrFail with nested path and elements") {
          val json   = Json.Object("items" -> Json.Array(Json.Number(1), Json.Number(2)))
          val result = json.modifyOrFail(DynamicOptic.root.field("items").elements) { case Json.Number(n) =>
            Json.Number(n + 100)
          }
          assertTrue(result == Right(Json.Object("items" -> Json.Array(Json.Number(101), Json.Number(102)))))
        }
      ),
      suite("insertOrFail edge cases")(
        test("insertOrFail fails for non-existent nested path") {
          val json   = Json.Object()
          val result = json.insertOrFail(DynamicOptic.root.field("a").field("b"), Json.Number(42))
          assertTrue(result.isLeft)
        },
        test("insertOrFail at array index extends array") {
          val json   = Json.Array(Json.Number(1), Json.Number(2))
          val result = json.insertOrFail(DynamicOptic.root.at(2), Json.Number(3))
          assertTrue(result == Right(Json.Array(Json.Number(1), Json.Number(2), Json.Number(3))))
        },
        test("insertOrFail fails when field already exists") {
          val json   = Json.Object("a" -> Json.Number(1))
          val result = json.insertOrFail(DynamicOptic.root.field("a"), Json.Number(99))
          assertTrue(result.isLeft)
        },
        test("insertOrFail succeeds for new field") {
          val json   = Json.Object("a" -> Json.Number(1))
          val result = json.insertOrFail(DynamicOptic.root.field("b"), Json.Number(2))
          assertTrue(result == Right(Json.Object("a" -> Json.Number(1), "b" -> Json.Number(2))))
        }
      ),
      suite("JSON ordering")(
        test("ordering sorts json values correctly") {
          val values = List(Json.Number(3), Json.Number(1), Json.Number(2))
          val result = values.sorted(Json.ordering)
          assertTrue(result == List(Json.Number(1), Json.Number(2), Json.Number(3)))
        },
        test("ordering handles mixed types by type order") {
          val values = List(
            Json.Object("a" -> Json.Number(1)),
            Json.Null,
            Json.Boolean(true),
            Json.Number(1),
            Json.String("hello"),
            Json.Array(Json.Number(1))
          )
          val result = values.sorted(Json.ordering)
          assertTrue(
            result(0) == Json.Null,
            result(1) == Json.Boolean(true),
            result(2) == Json.Number(1),
            result(3) == Json.String("hello")
          )
        },
        test("ordering sorts strings alphabetically") {
          val jsons  = List(Json.String("c"), Json.String("a"), Json.String("b"))
          val result = jsons.sorted(Json.ordering)
          assertTrue(result == List(Json.String("a"), Json.String("b"), Json.String("c")))
        }
      ),
      suite("SchemaError as Exception")(
        test("SchemaError extends Exception with NoStackTrace") {
          val error = SchemaError("test message")
          assertTrue(
            error.isInstanceOf[Exception],
            error.isInstanceOf[scala.util.control.NoStackTrace],
            error.getStackTrace.isEmpty
          )
        },
        test("SchemaError getMessage returns formatted string") {
          val error = SchemaError("test message")
          assertTrue(error.getMessage == "test message")
        },
        test("SchemaError with path is formatted correctly") {
          val error = SchemaError("test message").atField("users").atIndex(0)
          assertTrue(error.getMessage.contains("users") && error.getMessage.contains("0"))
        },
        test("JsonSelection.oneUnsafe throws SchemaError directly") {
          val result =
            try {
              JsonSelection.empty.oneUnsafe
              None
            } catch {
              case e: SchemaError => Some(e)
              case _: Throwable   => None
            }
          assertTrue(result.isDefined)
        },
        test("JsonSelection.anyUnsafe throws SchemaError directly") {
          val result =
            try {
              JsonSelection.empty.anyUnsafe
              None
            } catch {
              case e: SchemaError => Some(e)
              case _: Throwable   => None
            }
          assertTrue(result.isDefined)
        }
      ),
      suite("SchemaError additional methods")(
        test("++ aggregates errors") {
          val error1 = SchemaError("first error")
          val error2 = SchemaError("second error")
          val result = error1 ++ error2
          assertTrue(
            result.errors.length == 2,
            result.message.contains("first error"),
            result.message.contains("second error")
          )
        },
        test("apply with message and path") {
          val error = SchemaError.message("field missing", DynamicOptic.root.field("users").at(0))
          assertTrue(
            error.message.contains("field missing"),
            error.errors.head.source == DynamicOptic.root.field("users").at(0)
          )
        }
      ),
      suite("Chunk-based encoding and parsing")(
        test("printChunk produces valid Chunk[Byte]") {
          val json   = Json.Object("name" -> Json.String("Alice"), "age" -> Json.Number(30))
          val result = json.printChunk
          assertTrue(result == Chunk.fromArray("""{"name":"Alice","age":30}""".getBytes("UTF-8")))
        },
        test("parse from Chunk[Byte] works correctly") {
          val json    = Json.Object("key" -> Json.String("value"))
          val result1 = Json.parse(json.printChunk)
          val result2 = Json.parse(Chunk.single[Byte]('0'))
          assertTrue(
            result1 == Right(json),
            result2 == Right(Json.Number(0))
          )
        },
        test("roundtrip printChunk and parse(Chunk) preserves data") {
          val json = Json.Object(
            "users" -> Json.Array(
              Json.Object("name" -> Json.String("Alice"), "age" -> Json.Number(30)),
              Json.Object("name" -> Json.String("Bob"), "age"   -> Json.Number(25))
            ),
            "count"  -> Json.Number(2),
            "active" -> Json.Boolean(true)
          )
          val result = Json.parse(json.printChunk)
          assertTrue(result == Right(json))
        },
        test("printChunk with custom WriterConfig") {
          val json   = Json.Object("a" -> Json.Number(1))
          val result = Json.parse(json.printChunk(WriterConfig.withIndentionStep2))
          assertTrue(result == Right(json))
        },
        test("parse from Chunk[Byte] with custom ReaderConfig") {
          val json    = Json.Object("a" -> Json.Number(1))
          val result1 = Json.parse(json.printChunk ++ Chunk.single[Byte](0), ReaderConfig.withCheckForEndOfInput(false))
          val result2 = Json.parse(json.printChunk, ReaderConfig.withCheckForEndOfInput(false))
          assertTrue(
            result1 == Right(json),
            result2 == Right(json)
          )
        }
      ),
      suite("MergeStrategy.Custom")(
        test("Custom merge strategy allows user-defined logic") {
          val json1  = Json.Object("a" -> Json.Number(1), "b" -> Json.Number(2))
          val json2  = Json.Object("a" -> Json.Number(10), "c" -> Json.Number(3))
          val result = json1.merge(
            json2,
            MergeStrategy.Custom { (_, l, r) =>
              (l, r) match {
                case (Json.Number(lv), Json.Number(rv)) => Json.Number(lv + rv)
                case _                                  => r
              }
            }
          )
          assertTrue(
            result.get("a").any == Right(Json.Number(11)),
            result.get("b").any == Right(Json.Number(2)),
            result.get("c").any == Right(Json.Number(3))
          )
        },
        test("Custom merge strategy receives correct path") {
          var result = List.empty[String]
          val json1  = Json.Object("outer" -> Json.Object("inner" -> Json.Number(1)))
          val json2  = Json.Object("outer" -> Json.Object("inner" -> Json.Number(2)))
          json1.merge(
            json2,
            MergeStrategy.Custom { (path, _, r) =>
              result = result :+ path.toString
              r
            }
          )
          assertTrue(result.exists(_.contains("inner")))
        },
        test("Custom merge strategy falls back to user function for non-objects") {
          val json1  = Json.Array(Json.Number(1))
          val json2  = Json.Array(Json.Number(2))
          val result = json1.merge(
            json2,
            MergeStrategy.Custom(
              f = { (_, l, r) =>
                (l, r) match {
                  case (Json.Array(lv), Json.Array(rv)) => Json.Array((lv ++ rv): _*)
                  case _                                => r
                }
              },
              r = (_, _) => false
            )
          )
          assertTrue(result == Json.Array(Json.Number(1), Json.Number(2)))
        },
        test("Custom merge strategy with default recursion behaves like Auto for arrays") {
          val json1  = Json.Array(Json.Number(1))
          val json2  = Json.Array(Json.Number(2))
          val result = json1.merge(json2, MergeStrategy.Custom((_, _, r) => r))
          assertTrue(result == Json.Array(Json.Number(2)))
        }
      )
    ),
    suite("Schema instances")(
      suite("DynamicValue roundtrip")(
        test("Json.Null roundtrips") {
          dynamicValueRoundTrip(Json.Null)
        },
        test("Json.Boolean roundtrips") {
          dynamicValueRoundTrip(Json.Boolean(true)) && dynamicValueRoundTrip(Json.Boolean(false))
        },
        test("Json.Number roundtrips") {
          dynamicValueRoundTrip(Json.Number(123.456)) && dynamicValueRoundTrip(Json.Number(-42))
        },
        test("Json.String roundtrips") {
          dynamicValueRoundTrip(Json.String("hello world"))
        },
        test("Json.Array roundtrips") {
          dynamicValueRoundTrip(Json.Array(Json.Number(1), Json.String("two"), Json.Null))
        },
        test("Json.Object roundtrips") {
          dynamicValueRoundTrip(Json.Object("a" -> Json.Number(1), "b" -> Json.String("two")))
        },
        test("Json (Null) roundtrips as variant") {
          dynamicValueRoundTrip(Json.Null: Json)
        },
        test("Json (Boolean) roundtrips as variant") {
          dynamicValueRoundTrip(Json.Boolean(true): Json)
        },
        test("Json (Number) roundtrips as variant") {
          dynamicValueRoundTrip(Json.Number(42): Json)
        },
        test("Json (String) roundtrips as variant") {
          dynamicValueRoundTrip(Json.String("test"): Json)
        },
        test("Json (Array) roundtrips as variant") {
          dynamicValueRoundTrip(Json.Array(Json.Number(1)): Json)
        },
        test("Json (Object) roundtrips as variant") {
          dynamicValueRoundTrip(Json.Object("x" -> Json.Null): Json)
        },
        test("Nested Json roundtrips") {
          dynamicValueRoundTrip(
            Json.Object(
              "users" -> Json.Array(
                Json.Object("name" -> Json.String("Alice"), "age" -> Json.Number(30)),
                Json.Object("name" -> Json.String("Bob"), "age"   -> Json.Number(25))
              ),
              "meta" -> Json.Object("count" -> Json.Number(2))
            )
          )
        }
      ),
      suite("JSON roundtrip")(
        test("Json.Null serializes to JSON") {
          JsonTestUtils.roundTrip(Json.Null, """{}""")
        },
        test("Json.Boolean serializes to JSON") {
          JsonTestUtils.roundTrip(Json.Boolean(true), """{"value":true}""")
        },
        test("Json.Number serializes to JSON") {
          JsonTestUtils.roundTrip(Json.Number(42), """{"value":42}""")
        },
        test("Json.String serializes to JSON") {
          JsonTestUtils.roundTrip(Json.String("hello"), """{"value":"hello"}""")
        },
        test("Json.Array serializes to JSON") {
          JsonTestUtils.roundTrip(Json.Array(Json.Number(1)), """{"value":[{"Number":{"value":1}}]}""")
        },
        test("Json (variant) serializes to JSON") {
          JsonTestUtils.roundTrip(Json.Null: Json, """{"Null":{}}""") &&
          JsonTestUtils.roundTrip(Json.Boolean(true): Json, """{"Boolean":{"value":true}}""") &&
          JsonTestUtils.roundTrip(Json.Number(1): Json, """{"Number":{"value":1}}""") &&
          JsonTestUtils.roundTrip(Json.String("x"): Json, """{"String":{"value":"x"}}""")
        }
      ),
      suite("Schema roundtrip")(
        test("Json.Null roundtrips through DynamicValue") {
          val json   = Json.Null
          val result = Schema[Json].fromDynamicValue(Schema[Json].toDynamicValue(json))
          assertTrue(result == Right(json))
        },
        test("Json.Boolean roundtrips") {
          val json   = Json.Boolean(true)
          val result = Schema[Json].fromDynamicValue(Schema[Json].toDynamicValue(json))
          assertTrue(result == Right(json))
        },
        test("Json.String roundtrips") {
          val json   = Json.String("hello")
          val result = Schema[Json].fromDynamicValue(Schema[Json].toDynamicValue(json))
          assertTrue(result == Right(json))
        },
        test("Json.Number roundtrips") {
          val json   = Json.Number(123.456)
          val result = Schema[Json].fromDynamicValue(Schema[Json].toDynamicValue(json))
          assertTrue(result == Right(json))
        },
        test("Json.Array roundtrips") {
          val json   = Json.Array(Chunk(Json.Number(1), Json.String("two")))
          val result = Schema[Json].fromDynamicValue(Schema[Json].toDynamicValue(json))
          assertTrue(result == Right(json))
        },
        test("Json.Object roundtrips") {
          val json   = Json.Object(Chunk("a" -> Json.Number(1), "b" -> Json.Boolean(true)))
          val result = Schema[Json].fromDynamicValue(Schema[Json].toDynamicValue(json))
          assertTrue(result == Right(json))
        }
      )
    ),
    suite("JsonDecoder combinators")(
      test("map transforms decoded value") {
        val decoder = JsonDecoder.intDecoder.map(_ * 2)
        val result  = decoder.decode(Json.Number(21))
        assertTrue(result == Right(42))
      },
      test("flatMap chains decoders") {
        val decoder = JsonDecoder.intDecoder.flatMap { n =>
          if (n > 0) Right(n * 2)
          else Left(SchemaError("Expected positive number"))
        }
        val result1 = decoder.decode(Json.Number(21))
        val result2 = decoder.decode(Json.Number(-5))
        assertTrue(result1 == Right(42)) &&
        assertTrue(result2.isLeft)
      },
      test("orElse tries alternative decoder on failure") {
        val decoder = JsonDecoder.intDecoder.orElse(JsonDecoder.stringDecoder.map(_.toInt))
        val result1 = decoder.decode(Json.Number(42))
        val result2 = decoder.decode(Json.String("42"))
        assertTrue(result1 == Right(42)) &&
        assertTrue(result2 == Right(42))
      },
      test("tuple2Decoder decodes pairs from Json.Array") {
        val decoder = JsonDecoder.tuple2Decoder[Int, String]
        val result  = decoder.decode(Json.Array(Chunk(Json.Number(42), Json.String("test"))))
        assertTrue(result == Right((42, "test")))
      },
      test("tuple2Decoder fails on wrong size") {
        val decoder = JsonDecoder.tuple2Decoder[Int, String]
        val result  = decoder.decode(Json.Array(Chunk(Json.Number(42))))
        assertTrue(result.isLeft)
      },
      test("tuple3Decoder decodes triples from Json.Array") {
        val decoder = JsonDecoder.tuple3Decoder[Int, String, Boolean]
        val result  = decoder.decode(Json.Array(Chunk(Json.Number(42), Json.String("test"), Json.Boolean(true))))
        assertTrue(result == Right((42, "test", true)))
      },
      test("tuple3Decoder fails on wrong size") {
        val decoder = JsonDecoder.tuple3Decoder[Int, String, Boolean]
        val result  = decoder.decode(Json.Array(Chunk(Json.Number(42), Json.String("test"))))
        assertTrue(result.isLeft)
      },
      test("eitherDecoder decodes Left from Json.Object") {
        val decoder = JsonDecoder.eitherDecoder[Int, String]
        val result  = decoder.decode(Json.Object(Chunk("Left" -> Json.Number(42))))
        assertTrue(result == Right(Left(42)))
      },
      test("eitherDecoder decodes Right from Json.Object") {
        val decoder = JsonDecoder.eitherDecoder[Int, String]
        val result  = decoder.decode(Json.Object(Chunk("Right" -> Json.String("test"))))
        assertTrue(result == Right(Right("test")))
      },
      test("eitherDecoder fails on invalid structure") {
        val decoder = JsonDecoder.eitherDecoder[Int, String]
        val result  = decoder.decode(Json.Object(Chunk("Invalid" -> Json.Number(42))))
        assertTrue(result.isLeft)
      },
      test("setDecoder decodes Set from Json.Array") {
        val decoder = JsonDecoder.setDecoder[Int]
        val result  = decoder.decode(Json.Array(Chunk(Json.Number(1), Json.Number(2), Json.Number(3))))
        assertTrue(result == Right(Set(1, 2, 3)))
      },
      test("seqDecoder decodes Seq from Json.Array") {
        val decoder = JsonDecoder.seqDecoder[Int]
        val result  = decoder.decode(Json.Array(Chunk(Json.Number(1), Json.Number(2), Json.Number(3))))
        assertTrue(result == Right(Seq(1, 2, 3)))
      },
      test("charDecoder decodes single character") {
        assertTrue(JsonDecoder.charDecoder.decode(Json.String("a")) == Right('a'))
      },
      test("charDecoder fails on multi-char string") {
        assertTrue(JsonDecoder.charDecoder.decode(Json.String("abc")).isLeft)
      },
      test("unitDecoder decodes null") {
        assertTrue(JsonDecoder.unitDecoder.decode(Json.Null) == Right(()))
      },
      test("unitDecoder fails on non-null") {
        assertTrue(JsonDecoder.unitDecoder.decode(Json.Number(42)).isLeft)
      },
      test("byteDecoder decodes valid byte") {
        assertTrue(JsonDecoder.byteDecoder.decode(Json.Number(127)) == Right(127.toByte))
      },
      test("byteDecoder fails on out-of-range number") {
        assertTrue(JsonDecoder.byteDecoder.decode(Json.Number(1000)).isLeft)
      },
      test("shortDecoder decodes valid short") {
        assertTrue(JsonDecoder.shortDecoder.decode(Json.Number(32767)) == Right(32767.toShort))
      },
      test("shortDecoder fails on out-of-range number") {
        assertTrue(JsonDecoder.shortDecoder.decode(Json.Number(100000)).isLeft)
      }
    ),
    suite("JsonEncoder combinators")(
      test("contramap transforms input before encoding") {
        val encoder = JsonEncoder.intEncoder.contramap[String](_.toInt)
        val result  = encoder.encode("42")
        assertTrue(result == Json.Number(42))
      },
      test("tuple2Encoder encodes pairs to Json.Array") {
        val encoder = JsonEncoder.tuple2Encoder[Int, String]
        val result  = encoder.encode((42, "test"))
        assertTrue(result == Json.Array(Chunk(Json.Number(42), Json.String("test"))))
      },
      test("tuple3Encoder encodes triples to Json.Array") {
        val encoder = JsonEncoder.tuple3Encoder[Int, String, Boolean]
        val result  = encoder.encode((42, "test", true))
        assertTrue(result == Json.Array(Chunk(Json.Number(42), Json.String("test"), Json.Boolean(true))))
      },
      test("eitherEncoder encodes Left to Json.Object") {
        val encoder = JsonEncoder.eitherEncoder[Int, String]
        val result  = encoder.encode(Left(42))
        assertTrue(result == Json.Object(Chunk("Left" -> Json.Number(42))))
      },
      test("eitherEncoder encodes Right to Json.Object") {
        val encoder = JsonEncoder.eitherEncoder[Int, String]
        val result  = encoder.encode(Right("test"))
        assertTrue(result == Json.Object(Chunk("Right" -> Json.String("test"))))
      },
      test("setEncoder encodes Set to Json.Array") {
        val encoder = JsonEncoder.setEncoder[Int]
        val result  = encoder.encode(Set(1, 2, 3))
        assertTrue(result.isInstanceOf[Json.Array])
      },
      test("seqEncoder encodes Seq to Json.Array") {
        val encoder = JsonEncoder.seqEncoder[Int]
        val result  = encoder.encode(Seq(1, 2, 3))
        assertTrue(result == Json.Array(Chunk(Json.Number(1), Json.Number(2), Json.Number(3))))
      },
      test("byteEncoder encodes byte") {
        assertTrue(JsonEncoder.byteEncoder.encode(127.toByte) == Json.Number(127))
      },
      test("shortEncoder encodes short") {
        assertTrue(JsonEncoder.shortEncoder.encode(32767.toShort) == Json.Number(32767))
      },
      test("charEncoder encodes char") {
        assertTrue(JsonEncoder.charEncoder.encode('a') == Json.String("a"))
      },
      test("unitEncoder encodes unit") {
        assertTrue(JsonEncoder.unitEncoder.encode(()) == Json.Null)
      },
      test("bigIntEncoder encodes BigInt") {
        val result = JsonEncoder.bigIntEncoder.encode(BigInt("123456789012345678901234567890"))
        assertTrue(result == Json.Number(BigDecimal("123456789012345678901234567890")))
      }
    ),
    suite("JsonDecoder Java time types")(
      test("dayOfWeekDecoder decodes valid day") {
        assertTrue(JsonDecoder.dayOfWeekDecoder.decode(Json.String("MONDAY")) == Right(DayOfWeek.MONDAY))
      },
      test("dayOfWeekDecoder fails on invalid day") {
        assertTrue(JsonDecoder.dayOfWeekDecoder.decode(Json.String("NOTADAY")).isLeft)
      },
      test("durationDecoder decodes valid duration") {
        assertTrue(JsonDecoder.durationDecoder.decode(Json.String("PT1H30M")) == Right(Duration.parse("PT1H30M")))
      },
      test("durationDecoder fails on invalid duration") {
        assertTrue(JsonDecoder.durationDecoder.decode(Json.String("invalid")).isLeft)
      },
      test("instantDecoder decodes valid instant") {
        val result = JsonDecoder.instantDecoder.decode(Json.String("2023-01-01T00:00:00Z"))
        assertTrue(result == Right(Instant.parse("2023-01-01T00:00:00Z")))
      },
      test("instantDecoder fails on invalid instant") {
        assertTrue(JsonDecoder.instantDecoder.decode(Json.String("invalid")).isLeft)
      },
      test("localDateDecoder decodes valid date") {
        val result = JsonDecoder.localDateDecoder.decode(Json.String("2023-01-01"))
        assertTrue(result == Right(LocalDate.parse("2023-01-01")))
      },
      test("localDateDecoder fails on invalid date") {
        assertTrue(JsonDecoder.localDateDecoder.decode(Json.String("invalid")).isLeft)
      },
      test("localTimeDecoder decodes valid time") {
        assertTrue(JsonDecoder.localTimeDecoder.decode(Json.String("12:30:45")) == Right(LocalTime.parse("12:30:45")))
      },
      test("localTimeDecoder fails on invalid time") {
        assertTrue(JsonDecoder.localTimeDecoder.decode(Json.String("invalid")).isLeft)
      },
      test("localDateTimeDecoder decodes valid datetime") {
        val result = JsonDecoder.localDateTimeDecoder.decode(Json.String("2023-01-01T12:30:45"))
        assertTrue(result == Right(LocalDateTime.parse("2023-01-01T12:30:45")))
      },
      test("localDateTimeDecoder fails on invalid datetime") {
        assertTrue(JsonDecoder.localDateTimeDecoder.decode(Json.String("invalid")).isLeft)
      },
      test("monthDecoder decodes valid month") {
        assertTrue(JsonDecoder.monthDecoder.decode(Json.String("JANUARY")) == Right(Month.JANUARY))
      },
      test("monthDecoder fails on invalid month") {
        assertTrue(JsonDecoder.monthDecoder.decode(Json.String("NOTAMONTH")).isLeft)
      },
      test("monthDayDecoder decodes valid month-day") {
        assertTrue(JsonDecoder.monthDayDecoder.decode(Json.String("--01-15")) == Right(MonthDay.parse("--01-15")))
      },
      test("monthDayDecoder fails on invalid month-day") {
        assertTrue(JsonDecoder.monthDayDecoder.decode(Json.String("invalid")).isLeft)
      },
      test("offsetDateTimeDecoder decodes valid offset datetime") {
        val result = JsonDecoder.offsetDateTimeDecoder.decode(Json.String("2023-01-01T12:30:45+01:00"))
        assertTrue(result == Right(OffsetDateTime.parse("2023-01-01T12:30:45+01:00")))
      },
      test("offsetDateTimeDecoder fails on invalid offset datetime") {
        assertTrue(JsonDecoder.offsetDateTimeDecoder.decode(Json.String("invalid")).isLeft)
      },
      test("offsetTimeDecoder decodes valid offset time") {
        val result = JsonDecoder.offsetTimeDecoder.decode(Json.String("12:30:45+01:00"))
        assertTrue(result == Right(OffsetTime.parse("12:30:45+01:00")))
      },
      test("offsetTimeDecoder fails on invalid offset time") {
        assertTrue(JsonDecoder.offsetTimeDecoder.decode(Json.String("invalid")).isLeft)
      },
      test("periodDecoder decodes valid period") {
        assertTrue(JsonDecoder.periodDecoder.decode(Json.String("P1Y2M3D")) == Right(Period.parse("P1Y2M3D")))
      },
      test("periodDecoder fails on invalid period") {
        assertTrue(JsonDecoder.periodDecoder.decode(Json.String("invalid")).isLeft)
      },
      test("yearDecoder decodes valid year") {
        assertTrue(JsonDecoder.yearDecoder.decode(Json.String("2023")) == Right(Year.parse("2023")))
      },
      test("yearDecoder fails on invalid year") {
        assertTrue(JsonDecoder.yearDecoder.decode(Json.String("invalid")).isLeft)
      },
      test("yearMonthDecoder decodes valid year-month") {
        assertTrue(JsonDecoder.yearMonthDecoder.decode(Json.String("2023-01")) == Right(YearMonth.parse("2023-01")))
      },
      test("yearMonthDecoder fails on invalid year-month") {
        assertTrue(JsonDecoder.yearMonthDecoder.decode(Json.String("invalid")).isLeft)
      },
      test("zoneOffsetDecoder decodes valid zone offset") {
        assertTrue(JsonDecoder.zoneOffsetDecoder.decode(Json.String("+01:00")) == Right(ZoneOffset.of("+01:00")))
      },
      test("zoneOffsetDecoder fails on invalid zone offset") {
        assertTrue(JsonDecoder.zoneOffsetDecoder.decode(Json.String("invalid")).isLeft)
      },
      test("zoneIdDecoder decodes valid zone id") {
        assertTrue(JsonDecoder.zoneIdDecoder.decode(Json.String("UTC")) == Right(ZoneId.of("UTC")))
      },
      test("zoneIdDecoder fails on invalid zone id") {
        assertTrue(JsonDecoder.zoneIdDecoder.decode(Json.String("Invalid/Zone")).isLeft)
      },
      test("zonedDateTimeDecoder decodes valid zoned datetime") {
        val result = JsonDecoder.zonedDateTimeDecoder.decode(Json.String("2023-01-01T12:30:45+01:00[Europe/Paris]"))
        assertTrue(result == Right(ZonedDateTime.parse("2023-01-01T12:30:45+01:00[Europe/Paris]")))
      },
      test("zonedDateTimeDecoder fails on invalid zoned datetime") {
        assertTrue(JsonDecoder.zonedDateTimeDecoder.decode(Json.String("invalid")).isLeft)
      },
      test("uuidDecoder decodes valid UUID") {
        val result = JsonDecoder.uuidDecoder.decode(Json.String("550e8400-e29b-41d4-a716-446655440000"))
        assertTrue(result == Right(UUID.fromString("550e8400-e29b-41d4-a716-446655440000")))
      },
      test("uuidDecoder fails on invalid UUID") {
        assertTrue(JsonDecoder.uuidDecoder.decode(Json.String("invalid")).isLeft)
      },
      test("currencyDecoder decodes valid currency") {
        assertTrue(JsonDecoder.currencyDecoder.decode(Json.String("USD")) == Right(Currency.getInstance("USD")))
      },
      test("currencyDecoder fails on invalid currency") {
        assertTrue(JsonDecoder.currencyDecoder.decode(Json.String("INVALID")).isLeft)
      }
    ),
    suite("JsonEncoder Java time types")(
      test("dayOfWeekEncoder encodes day") {
        assertTrue(JsonEncoder.dayOfWeekEncoder.encode(DayOfWeek.MONDAY) == Json.String("MONDAY"))
      },
      test("durationEncoder encodes duration") {
        assertTrue(JsonEncoder.durationEncoder.encode(Duration.parse("PT1H30M")) == Json.String("PT1H30M"))
      },
      test("instantEncoder encodes instant") {
        val result = JsonEncoder.instantEncoder.encode(Instant.parse("2023-01-01T00:00:00Z"))
        assertTrue(result == Json.String("2023-01-01T00:00:00Z"))
      },
      test("localDateEncoder encodes date") {
        assertTrue(JsonEncoder.localDateEncoder.encode(LocalDate.parse("2023-01-01")) == Json.String("2023-01-01"))
      },
      test("localTimeEncoder encodes time") {
        assertTrue(JsonEncoder.localTimeEncoder.encode(LocalTime.parse("12:30:45")) == Json.String("12:30:45"))
      },
      test("localDateTimeEncoder encodes datetime") {
        val result = JsonEncoder.localDateTimeEncoder.encode(LocalDateTime.parse("2023-01-01T12:30:45"))
        assertTrue(result == Json.String("2023-01-01T12:30:45"))
      },
      test("monthEncoder encodes month") {
        assertTrue(JsonEncoder.monthEncoder.encode(Month.JANUARY) == Json.String("JANUARY"))
      },
      test("monthDayEncoder encodes month-day") {
        assertTrue(JsonEncoder.monthDayEncoder.encode(MonthDay.parse("--01-15")) == Json.String("--01-15"))
      },
      test("offsetDateTimeEncoder encodes offset datetime") {
        val result = JsonEncoder.offsetDateTimeEncoder.encode(OffsetDateTime.parse("2023-01-01T12:30:45+01:00"))
        assertTrue(result == Json.String("2023-01-01T12:30:45+01:00"))
      },
      test("offsetTimeEncoder encodes offset time") {
        val result = JsonEncoder.offsetTimeEncoder.encode(OffsetTime.parse("12:30:45+01:00"))
        assertTrue(result == Json.String("12:30:45+01:00"))
      },
      test("periodEncoder encodes period") {
        assertTrue(JsonEncoder.periodEncoder.encode(Period.parse("P1Y2M3D")) == Json.String("P1Y2M3D"))
      },
      test("yearEncoder encodes year") {
        assertTrue(JsonEncoder.yearEncoder.encode(Year.parse("2023")) == Json.String("2023"))
      },
      test("yearMonthEncoder encodes year-month") {
        assertTrue(JsonEncoder.yearMonthEncoder.encode(YearMonth.parse("2023-01")) == Json.String("2023-01"))
      },
      test("zoneOffsetEncoder encodes zone offset") {
        assertTrue(JsonEncoder.zoneOffsetEncoder.encode(ZoneOffset.of("+01:00")) == Json.String("+01:00"))
      },
      test("zoneIdEncoder encodes zone id") {
        assertTrue(JsonEncoder.zoneIdEncoder.encode(ZoneId.of("America/New_York")) == Json.String("America/New_York"))
      },
      test("zonedDateTimeEncoder encodes zoned datetime") {
        val result =
          JsonEncoder.zonedDateTimeEncoder.encode(ZonedDateTime.parse("2023-01-01T12:30:45+01:00[Europe/Paris]"))
        assertTrue(result == Json.String("2023-01-01T12:30:45+01:00[Europe/Paris]"))
      },
      test("uuidEncoder encodes UUID") {
        val result = JsonEncoder.uuidEncoder.encode(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))
        assertTrue(result == Json.String("550e8400-e29b-41d4-a716-446655440000"))
      },
      test("currencyEncoder encodes currency") {
        assertTrue(JsonEncoder.currencyEncoder.encode(Currency.getInstance("USD")) == Json.String("USD"))
      }
    ),
    suite("JsonDecoder error paths")(
      test("vectorDecoder propagates element errors") {
        val result =
          JsonDecoder.vectorDecoder[Int].decode(Json.Array(Chunk(Json.Number(1), Json.String("not a number"))))
        assertTrue(result.isLeft)
      },
      test("listDecoder propagates element errors") {
        val result = JsonDecoder.listDecoder[Int].decode(Json.Array(Chunk(Json.Number(1), Json.String("not a number"))))
        assertTrue(result.isLeft)
      },
      test("mapDecoder propagates value errors") {
        val result = JsonDecoder
          .mapDecoder[Int]
          .decode(Json.Object(Chunk("a" -> Json.Number(1), "b" -> Json.String("not a number"))))
        assertTrue(result.isLeft)
      },
      test("parseString fails on non-string") {
        assertTrue(JsonDecoder.uuidDecoder.decode(Json.Number(123)).isLeft)
      }
    ),
    suite("fromDynamicValue primitive coverage")(
      test("converts all PrimitiveValue types to Json") {
        val testCases: List[(PrimitiveValue, Json => Boolean)] = List(
          (PrimitiveValue.Unit, _ == Json.Object.empty),
          (PrimitiveValue.Boolean(true), _ == Json.True),
          (PrimitiveValue.Boolean(false), _ == Json.False),
          (PrimitiveValue.Byte(42.toByte), j => j.as(JsonType.Number).exists(_.value == BigDecimal(42))),
          (PrimitiveValue.Short(100.toShort), j => j.as(JsonType.Number).exists(_.value == BigDecimal(100))),
          (PrimitiveValue.Int(1000), j => j.as(JsonType.Number).exists(_.value == BigDecimal(1000))),
          (PrimitiveValue.Long(10000L), j => j.as(JsonType.Number).exists(_.value == BigDecimal(10000))),
          (PrimitiveValue.Float(3.5f), j => j.as(JsonType.Number).isDefined),
          (PrimitiveValue.Double(3.14159), j => j.as(JsonType.Number).isDefined),
          (PrimitiveValue.Char('X'), j => j.as(JsonType.String).exists(_.value == "X")),
          (PrimitiveValue.String("hello"), j => j.as(JsonType.String).exists(_.value == "hello")),
          (PrimitiveValue.BigInt(BigInt("123456789012345678901234567890")), j => j.as(JsonType.Number).isDefined),
          (PrimitiveValue.BigDecimal(BigDecimal("123.456789")), j => j.as(JsonType.Number).isDefined),
          (PrimitiveValue.DayOfWeek(DayOfWeek.MONDAY), j => j.as(JsonType.String).exists(_.value == "MONDAY")),
          (PrimitiveValue.Month(Month.JANUARY), j => j.as(JsonType.String).exists(_.value == "JANUARY")),
          (PrimitiveValue.Duration(Duration.ofHours(1)), j => j.as(JsonType.String).isDefined),
          (PrimitiveValue.Instant(Instant.EPOCH), j => j.as(JsonType.String).isDefined),
          (PrimitiveValue.LocalDate(LocalDate.of(2024, 1, 15)), j => j.as(JsonType.String).isDefined),
          (PrimitiveValue.LocalDateTime(LocalDateTime.of(2024, 1, 15, 12, 30)), j => j.as(JsonType.String).isDefined),
          (PrimitiveValue.LocalTime(LocalTime.of(12, 30, 45)), j => j.as(JsonType.String).isDefined),
          (PrimitiveValue.MonthDay(MonthDay.of(1, 15)), j => j.as(JsonType.String).isDefined),
          (
            PrimitiveValue.OffsetDateTime(OffsetDateTime.of(2024, 1, 15, 12, 30, 0, 0, ZoneOffset.UTC)),
            j => j.as(JsonType.String).isDefined
          ),
          (
            PrimitiveValue.OffsetTime(OffsetTime.of(12, 30, 0, 0, ZoneOffset.UTC)),
            j => j.as(JsonType.String).isDefined
          ),
          (PrimitiveValue.Period(Period.ofDays(30)), j => j.as(JsonType.String).isDefined),
          (PrimitiveValue.Year(Year.of(2024)), j => j.as(JsonType.String).isDefined),
          (PrimitiveValue.YearMonth(YearMonth.of(2024, 1)), j => j.as(JsonType.String).isDefined),
          (PrimitiveValue.ZoneId(ZoneId.of("UTC")), j => j.as(JsonType.String).isDefined),
          (PrimitiveValue.ZoneOffset(ZoneOffset.UTC), j => j.as(JsonType.String).isDefined),
          (
            PrimitiveValue.ZonedDateTime(ZonedDateTime.of(2024, 1, 15, 12, 30, 0, 0, ZoneId.of("UTC"))),
            j => j.as(JsonType.String).isDefined
          ),
          (PrimitiveValue.Currency(Currency.getInstance("USD")), j => j.as(JsonType.String).exists(_.value == "USD")),
          (
            PrimitiveValue.UUID(UUID.fromString("12345678-1234-1234-1234-123456789012")),
            j => j.as(JsonType.String).isDefined
          )
        )
        testCases.foldLeft(assertTrue(true)) { case (acc, (pv, check)) =>
          val json = Json.fromDynamicValue(DynamicValue.Primitive(pv))
          acc && assertTrue(check(json))
        }
      }
    ),
    suite("JsonDecoder error branches")(
      test("stringDecoder fails on non-string Json values") {
        assertTrue(JsonDecoder[String].decode(Json.Number(42)).isLeft) &&
        assertTrue(JsonDecoder[String].decode(Json.True).isLeft) &&
        assertTrue(JsonDecoder[String].decode(Json.Null).isLeft) &&
        assertTrue(JsonDecoder[String].decode(Json.Array.empty).isLeft) &&
        assertTrue(JsonDecoder[String].decode(Json.Object.empty).isLeft)
      },
      test("booleanDecoder fails on non-boolean Json values") {
        assertTrue(JsonDecoder[Boolean].decode(Json.String("true")).isLeft) &&
        assertTrue(JsonDecoder[Boolean].decode(Json.Number(1)).isLeft) &&
        assertTrue(JsonDecoder[Boolean].decode(Json.Null).isLeft)
      },
      test("intDecoder fails on non-number Json values") {
        assertTrue(JsonDecoder[Int].decode(Json.String("42")).isLeft) &&
        assertTrue(JsonDecoder[Int].decode(Json.True).isLeft) &&
        assertTrue(JsonDecoder[Int].decode(Json.Null).isLeft)
      },
      test("intDecoder fails on non-integer number") {
        assertTrue(JsonDecoder[Int].decode(Json.Number(3.14)).isLeft)
      },
      test("longDecoder fails on non-number Json values") {
        assertTrue(JsonDecoder[Long].decode(Json.String("42")).isLeft) &&
        assertTrue(JsonDecoder[Long].decode(Json.True).isLeft)
      },
      test("doubleDecoder fails on non-number Json values") {
        assertTrue(JsonDecoder[Double].decode(Json.String("3.14")).isLeft) &&
        assertTrue(JsonDecoder[Double].decode(Json.Null).isLeft)
      },
      test("floatDecoder fails on non-number Json values") {
        assertTrue(JsonDecoder[Float].decode(Json.String("3.14")).isLeft)
      },
      test("byteDecoder fails on out-of-range values") {
        assertTrue(JsonDecoder[Byte].decode(Json.Number(128)).isLeft) &&
        assertTrue(JsonDecoder[Byte].decode(Json.Number(-129)).isLeft)
      },
      test("shortDecoder fails on out-of-range values") {
        assertTrue(JsonDecoder[Short].decode(Json.Number(32768)).isLeft) &&
        assertTrue(JsonDecoder[Short].decode(Json.Number(-32769)).isLeft)
      },
      test("optionDecoder handles None for null") {
        assert(JsonDecoder[Option[Int]].decode(Json.Null))(isRight(equalTo(None)))
      },
      test("optionDecoder handles Some for non-null") {
        assert(JsonDecoder[Option[Int]].decode(Json.Number(42)))(isRight(equalTo(Some(42))))
      },
      test("listDecoder fails on non-array") {
        assertTrue(JsonDecoder[List[Int]].decode(Json.Object.empty).isLeft)
      },
      test("mapDecoder fails on non-object") {
        assertTrue(JsonDecoder[Map[String, Int]].decode(Json.Array.empty).isLeft)
      }
    ),
    suite("Json path operation coverage")(
      test("modify with DynamicOptic.root.field modifies nested value") {
        val json   = Json.parse("""{"a": {"x": 1}, "b": 2}""").getOrElse(Json.Null)
        val result = json.modify(DynamicOptic.root.field("a").field("x"))(_ => Json.Number(99))
        assertTrue(result.get("a").get("x").one == Right(Json.Number(99)))
      },
      test("modify returns original when path does not exist") {
        val json   = Json.parse("""{"a": 1}""").getOrElse(Json.Null)
        val result = json.modify(DynamicOptic.root.field("nonexistent"))(_ => Json.Number(99))
        assertTrue(result == json)
      },
      test("modifyOrFail fails when path does not exist") {
        val json   = Json.parse("""{"a": 1}""").getOrElse(Json.Null)
        val result = json.modifyOrFail(DynamicOptic.root.field("nonexistent")) { case _ => Json.Number(99) }
        assertTrue(result.isLeft)
      },
      test("delete removes value at path") {
        val json   = Json.parse("""{"a": 1, "b": 2}""").getOrElse(Json.Null)
        val result = json.delete(DynamicOptic.root.field("a"))
        assertTrue(result.get("a").isFailure, result.get("b").isSuccess)
      },
      test("delete returns original when path does not exist") {
        val json   = Json.parse("""{"a": 1}""").getOrElse(Json.Null)
        val result = json.delete(DynamicOptic.root.field("nonexistent"))
        assertTrue(result == json)
      },
      test("deleteOrFail fails when path does not exist") {
        val json   = Json.parse("""{"a": 1}""").getOrElse(Json.Null)
        val result = json.deleteOrFail(DynamicOptic.root.field("nonexistent"))
        assertTrue(result.isLeft)
      },
      test("insert adds value at new path in object") {
        val json   = Json.parse("""{"a": 1}""").getOrElse(Json.Null)
        val result = json.insert(DynamicOptic.root.field("b"), Json.Number(2))
        assertTrue(result.get("a").isSuccess, result.get("b").isSuccess)
      },
      test("insert adds value at array index") {
        val json   = Json.parse("""{"items": [1, 3]}""").getOrElse(Json.Null)
        val result = json.insert(DynamicOptic.root.field("items").at(1), Json.Number(2))
        assertTrue(result.get("items").isSuccess)
      },
      test("insertOrFail fails when path already exists") {
        val json   = Json.parse("""{"a": 1}""").getOrElse(Json.Null)
        val result = json.insertOrFail(DynamicOptic.root.field("a"), Json.Number(99))
        assertTrue(result.isLeft) // insertOrFail should fail because "a" already exists
      },
      test("parse handles malformed JSON") {
        assertTrue(Json.parse("{invalid}").isLeft) &&
        assertTrue(Json.parse("{\"key\":").isLeft) &&
        assertTrue(Json.parse("").isLeft)
      },
      test("compare orders different Json types correctly") {
        assertTrue(Json.Null.compare(Json.True) < 0) &&
        assertTrue(Json.True.compare(Json.False) > 0) &&
        assertTrue(Json.String("a").compare(Json.String("b")) < 0) &&
        assertTrue(Json.Number(1).compare(Json.Number(2)) < 0)
      },
      test("modify with Elements path modifies all array elements") {
        val json   = Json.parse("""[1, 2, 3]""").getOrElse(Json.Null)
        val result = json.modify(DynamicOptic.root.elements)(_ => Json.Number(0))
        assertTrue(result.as(JsonType.Array) match {
          case Some(arr) => arr.value.forall(_ == Json.Number(0))
          case None      => false
        })
      }
    )
  )

  private def dynamicValueRoundTrip[A](value: A)(implicit schema: Schema[A]): TestResult =
    assertTrue(schema.fromDynamicValue(schema.toDynamicValue(value)) == Right(value))
}
