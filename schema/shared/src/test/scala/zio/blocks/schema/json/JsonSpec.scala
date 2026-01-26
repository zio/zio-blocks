package zio.blocks.schema.json

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.test._
import zio.test.Assertion.equalTo

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
          assert(objResult.isDefined)(equalTo(true)) &&
          assert(arrResult.isDefined)(equalTo(true)) &&
          assert(strResult.isDefined)(equalTo(true)) &&
          assert(numResult.isDefined)(equalTo(true)) &&
          assert(boolResult.isDefined)(equalTo(true)) &&
          assert(nullResult.isDefined)(equalTo(true))
        },
        test("as returns None when type does not match") {
          val obj: Json = Json.Object.empty
          assert(obj.as(JsonType.Array).isEmpty)(equalTo(true)) &&
          assert(obj.as(JsonType.String).isEmpty)(equalTo(true)) &&
          assert(obj.as(JsonType.Number).isEmpty)(equalTo(true)) &&
          assert(obj.as(JsonType.Boolean).isEmpty)(equalTo(true)) &&
          assert(obj.as(JsonType.Null).isEmpty)(equalTo(true))
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
          val obj: Json = Json.Object.empty
          assert(obj.unwrap(JsonType.Array).isEmpty)(equalTo(true)) &&
          assert(obj.unwrap(JsonType.String).isEmpty)(equalTo(true)) &&
          assert(obj.unwrap(JsonType.Number).isEmpty)(equalTo(true)) &&
          assert(obj.unwrap(JsonType.Boolean).isEmpty)(equalTo(true)) &&
          assert(obj.unwrap(JsonType.Null).isEmpty)(equalTo(true))
        },
        test("unwrap for Number returns None when value is not parseable") {
          val invalidNum: Json = Json.Number("not-a-number")
          assert(invalidNum.unwrap(JsonType.Number).isEmpty)(equalTo(true))
        },
        test("JsonType.apply works as a predicate function") {
          val obj: Json  = Json.Object.empty
          val arr: Json  = Json.Array.empty
          val str: Json  = Json.String("test")
          val num: Json  = Json.Number("42")
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
          assertTrue(
            selection.isSuccess,
            selection.one == Right(Json.String("hello"))
          )
        },
        test("select(jsonType) returns selection when type matches") {
          val json = Json.Object("a" -> Json.Number("1"))
          assertTrue(
            json.select(JsonType.Object).isSuccess,
            json.select(JsonType.Object).one == Right(json)
          )
        },
        test("select(jsonType) returns empty when type does not match") {
          val json = Json.Object("a" -> Json.Number("1"))
          assertTrue(
            json.select(JsonType.Array).isEmpty,
            json.select(JsonType.String).isEmpty,
            json.select(JsonType.Number).isEmpty
          )
        }
      ),
      suite("prune/retain methods")(
        test("prune removes matching values from object") {
          val json = Json.Object(
            "a" -> Json.Number("1"),
            "b" -> Json.Null,
            "c" -> Json.Number("2")
          )
          val pruned  = json.prune(_.is(JsonType.Null))
          val pruned2 = pruned.as(JsonType.Object).get
          assertTrue(
            pruned2.fields.length == 2,
            pruned2.get("a").isSuccess,
            pruned2.get("b").isFailure,
            pruned2.get("c").isSuccess
          )
        },
        test("prune removes matching values from array") {
          val json = Json.Array(
            Json.Number("1"),
            Json.Null,
            Json.Number("2"),
            Json.Null
          )
          val pruned = json.prune(_.is(JsonType.Null))
          assertTrue(
            pruned.elements.length == 2,
            pruned.elements == Chunk(Json.Number("1"), Json.Number("2"))
          )
        },
        test("prune works recursively") {
          val json = Json.Object(
            "user" -> Json.Object(
              "name" -> Json.String("Alice"),
              "age"  -> Json.Null
            )
          )
          val pruned = json.prune(_.is(JsonType.Null))
          assertTrue(
            pruned.get("user").get("name").isSuccess,
            pruned.get("user").get("age").isFailure
          )
        },
        test("prunePath removes values at matching paths") {
          val json = Json.Object(
            "keep" -> Json.Number("1"),
            "drop" -> Json.Number("2")
          )
          val pruned = json.prunePath { path =>
            path.nodes.exists {
              case f: DynamicOptic.Node.Field => f.name == "drop"
              case _                          => false
            }
          }
          assertTrue(
            pruned.get("keep").isSuccess,
            pruned.get("drop").isFailure
          )
        },
        test("pruneBoth removes values matching both path and value predicates") {
          val json = Json.Object(
            "nums" -> Json.Array(Json.Number("1"), Json.Number("100"), Json.Number("5")),
            "strs" -> Json.Array(Json.String("a"))
          )
          val pruned = json.pruneBoth { (path, value) =>
            val inNums = path.nodes.exists {
              case f: DynamicOptic.Node.Field => f.name == "nums"
              case _                          => false
            }
            val isLarge = value.unwrap(JsonType.Number).exists(_ > 10)
            inNums && isLarge
          }
          assertTrue(
            pruned.get("nums").as[Vector[Int]] == Right(Vector(1, 5)),
            pruned.get("strs").isSuccess
          )
        },
        test("retain keeps only matching values in object") {
          val json = Json.Object(
            "a" -> Json.Number("1"),
            "b" -> Json.String("hi"),
            "c" -> Json.Number("2")
          )
          val retained = json.retain(_.is(JsonType.Number))
          val fields   = retained.as(JsonType.Object).get.fields
          assertTrue(
            fields.length == 2,
            retained.get("a").isSuccess,
            retained.get("b").isFailure,
            retained.get("c").isSuccess
          )
        },
        test("retain keeps only matching values in array") {
          val json = Json.Array(
            Json.Number("1"),
            Json.String("x"),
            Json.Number("2")
          )
          val retained = json.retain(_.is(JsonType.Number))
          assertTrue(
            retained.elements.length == 2,
            retained.elements == Chunk(Json.Number("1"), Json.Number("2"))
          )
        },
        test("retainPath keeps values at matching paths") {
          val json = Json.Object(
            "keep" -> Json.Number("1"),
            "drop" -> Json.Number("2")
          )
          val retained = json.retainPath { path =>
            path.nodes.exists {
              case f: DynamicOptic.Node.Field => f.name == "keep"
              case _                          => false
            }
          }
          assertTrue(
            retained.get("keep").isSuccess,
            retained.get("drop").isFailure
          )
        },
        test("retainBoth keeps values matching both path and value predicates") {
          val json = Json.Object(
            "keep" -> Json.Number("100"),
            "drop" -> Json.Number("5")
          )
          val retained = json.retainBoth { (path, value) =>
            val hasKeepField = path.nodes.exists {
              case f: DynamicOptic.Node.Field => f.name == "keep"
              case _                          => false
            }
            val isLarge = value.unwrap(JsonType.Number).exists(_ > 10)
            hasKeepField && isLarge
          }
          assertTrue(
            retained.get("keep").isSuccess,
            retained.get("drop").isFailure
          )
        }
      ),
      suite("jsonType")(
        test("jsonType returns correct type for each Json subtype") {
          assertTrue(
            Json.Object.empty.jsonType == JsonType.Object,
            Json.Array.empty.jsonType == JsonType.Array,
            Json.String("test").jsonType == JsonType.String,
            Json.Number("42").jsonType == JsonType.Number,
            Json.Boolean(true).jsonType == JsonType.Boolean,
            Json.Null.jsonType == JsonType.Null
          )
        },
        test("JsonType.typeIndex matches Json.typeIndex") {
          assertTrue(
            Json.Null.typeIndex == JsonType.Null.typeIndex,
            Json.Boolean(true).typeIndex == JsonType.Boolean.typeIndex,
            Json.Number("1").typeIndex == JsonType.Number.typeIndex,
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
          val json = Json.Object("a" -> Json.Number("1"), "b" -> Json.Number("2"))
          assertTrue(json.fields.nonEmpty, json.fields.length == 2)
        },
        test("fields returns empty Seq for non-objects") {
          assertTrue(
            Json.Array().fields.isEmpty,
            Json.String("test").fields.isEmpty,
            Json.Number("42").fields.isEmpty,
            Json.Boolean(true).fields.isEmpty,
            Json.Null.fields.isEmpty
          )
        },
        test("elements returns non-empty Seq for arrays") {
          val json = Json.Array(Json.Number("1"), Json.Number("2"), Json.Number("3"))
          assertTrue(json.elements.nonEmpty, json.elements.length == 3)
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
          assertTrue(j.get(path).as[String] == Right("Bob"))
        },
        test("get retrieves field from object") {
          val json = Json.Object("name" -> Json.String("Alice"), "age" -> Json.Number("30"))
          assertTrue(
            json.get("name").one == Right(Json.String("Alice")),
            json.get("age").one == Right(Json.Number("30"))
          )
        },
        test("get returns error for missing field") {
          val json = Json.Object("name" -> Json.String("Alice"))
          assertTrue(json.get("missing").isFailure)
        },
        test("apply(index) retrieves element from array") {
          val arr = Json.Array(Json.String("a"), Json.String("b"), Json.String("c"))
          assertTrue(
            arr.get(0).one == Right(Json.String("a")),
            arr.get(1).one == Right(Json.String("b")),
            arr.get(2).one == Right(Json.String("c"))
          )
        },
        test("apply(index) returns error for out of bounds") {
          val arr = Json.Array(Json.Number("1"))
          assertTrue(arr.get(1).isFailure, arr.get(-1).isFailure)
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
          val json    = Json.Object("a" -> Json.Number("1"))
          val path    = DynamicOptic.root.field("a")
          val updated = json.set(path, Json.Number("99"))
          assertTrue(updated.get("a").one == Right(Json.Number("99")))
        },
        test("set returns unchanged json if field doesn't exist") {
          val json    = Json.Object("a" -> Json.Number("1"))
          val path    = DynamicOptic.root.field("b")
          val updated = json.set(path, Json.Number("2"))
          // set on non-existent path returns original unchanged
          assertTrue(updated == json)
        },
        test("set updates element in array") {
          val arr     = Json.Array(Json.Number("1"), Json.Number("2"), Json.Number("3"))
          val path    = DynamicOptic.root.at(1)
          val updated = arr.set(path, Json.Number("99"))
          assertTrue(updated.get(1).one == Right(Json.Number("99")))
        },
        test("setOrFail fails for non-existent path") {
          val json   = Json.Object("a" -> Json.Number("1"))
          val path   = DynamicOptic.root.field("b")
          val result = json.setOrFail(path, Json.Number("2"))
          assertTrue(result.isLeft)
        },
        test("delete removes field from object") {
          val json    = Json.Object("a" -> Json.Number("1"), "b" -> Json.Number("2"))
          val path    = DynamicOptic.root.field("a")
          val updated = json.delete(path)
          assertTrue(updated.get("a").isFailure, updated.get("b").isSuccess)
        },
        test("delete removes element from array") {
          val arr     = Json.Array(Json.Number("1"), Json.Number("2"), Json.Number("3"))
          val path    = DynamicOptic.root.at(1)
          val updated = arr.delete(path)
          assertTrue(
            updated.elements.length == 2,
            updated.get(0).one == Right(Json.Number("1")),
            updated.get(1).one == Right(Json.Number("3"))
          )
        },
        test("deleteOrFail fails for non-existent path") {
          val json   = Json.Object("a" -> Json.Number("1"))
          val path   = DynamicOptic.root.field("missing")
          val result = json.deleteOrFail(path)
          assertTrue(result.isLeft)
        },
        test("modify transforms value at path") {
          val json    = Json.Object("count" -> Json.Number("5"))
          val path    = DynamicOptic.root.field("count")
          val updated = json.modify(path) {
            case Json.Number(n) => Json.Number((BigDecimal(n) * 2).toString)
            case other          => other
          }
          assertTrue(updated.get("count").as[BigDecimal] == Right(BigDecimal(10)))
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
          val json    = Json.Object("a" -> Json.Number("1"))
          val path    = DynamicOptic.root.field("b")
          val updated = json.insert(path, Json.Number("2"))
          assertTrue(updated.get("b").one == Right(Json.Number("2")))
        },
        test("insert does nothing if field already exists") {
          val json    = Json.Object("a" -> Json.Number("1"))
          val path    = DynamicOptic.root.field("a")
          val updated = json.insert(path, Json.Number("99"))
          assertTrue(updated.get("a").one == Right(Json.Number("1"))) // Original value unchanged
        },
        test("insertOrFail fails if field already exists") {
          val json   = Json.Object("a" -> Json.Number("1"))
          val path   = DynamicOptic.root.field("a")
          val result = json.insertOrFail(path, Json.Number("99"))
          assertTrue(result.isLeft)
        },
        test("insert at array index shifts elements") {
          val json    = Json.Array(Json.Number("1"), Json.Number("3"))
          val path    = DynamicOptic.root.at(1)
          val updated = json.insert(path, Json.Number("2"))
          assertTrue(updated.elements == Chunk(Json.Number("1"), Json.Number("2"), Json.Number("3")))
        },
        test("nested path modification works") {
          val json = Json.Object(
            "user" -> Json.Object(
              "profile" -> Json.Object(
                "age" -> Json.Number("25")
              )
            )
          )
          val path    = DynamicOptic.root.field("user").field("profile").field("age")
          val updated = json.set(path, Json.Number("26"))
          assertTrue(updated.get("user").get("profile").get("age").as[BigDecimal] == Right(BigDecimal(26)))
        },
        test("get with DynamicOptic navigates nested structure") {
          val json = Json.Object(
            "users" -> Json.Array(
              Json.Object("name" -> Json.String("Alice")),
              Json.Object("name" -> Json.String("Bob"))
            )
          )
          val path = DynamicOptic.root.field("users").at(0).field("name")
          assertTrue(json.get(path).as[String] == Right("Alice"))
        },
        test("get with elements returns all array elements") {
          val json      = Json.Array(Json.Number("1"), Json.Number("2"), Json.Number("3"))
          val path      = DynamicOptic.elements
          val selection = json.get(path)
          assertTrue(selection.either == Right(Vector(Json.Number("1"), Json.Number("2"), Json.Number("3"))))
        },
        test("modify with elements transforms all array elements") {
          val json    = Json.Array(Json.Number("1"), Json.Number("2"), Json.Number("3"))
          val path    = DynamicOptic.elements
          val updated = json.modify(path) {
            case Json.Number(n) => Json.Number((BigDecimal(n) * 10).toString)
            case other          => other
          }
          assertTrue(updated.elements == Chunk(Json.Number("10"), Json.Number("20"), Json.Number("30")))
        }
      ),
      suite("normalization")(
        test("sortKeys sorts object keys alphabetically") {
          val json   = Json.Object("c" -> Json.Number("3"), "a" -> Json.Number("1"), "b" -> Json.Number("2"))
          val sorted = json.sortKeys
          assertTrue(sorted.fields.map(_._1) == Chunk("a", "b", "c"))
        },
        test("sortKeys works recursively") {
          val json = Json.Object(
            "z" -> Json.Object("b" -> Json.Number("1"), "a" -> Json.Number("2")),
            "a" -> Json.Number("0")
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
            "a" -> Json.Number("1"),
            "b" -> Json.Null,
            "c" -> Json.Number("3")
          )
          val dropped = json.dropNulls
          assertTrue(dropped.fields.length == 2, dropped.get("b").isFailure)
        },
        test("dropEmpty removes empty objects and arrays") {
          val json = Json.Object(
            "a" -> Json.Number("1"),
            "b" -> Json.Object(),
            "c" -> Json.Array()
          )
          val dropped = json.dropEmpty
          assertTrue(dropped.fields.length == 1, dropped.get("a").isSuccess)
        }
      ),
      suite("merging")(
        test("merge with Auto strategy merges objects deeply") {
          val left   = Json.Object("a" -> Json.Object("x" -> Json.Number("1")))
          val right  = Json.Object("a" -> Json.Object("y" -> Json.Number("2")))
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
          val left   = Json.Object("a" -> Json.Number("1"))
          val right  = Json.Object("b" -> Json.Number("2"))
          val merged = left.merge(right, MergeStrategy.Replace)
          assertTrue(merged == right)
        },
        test("merge arrays by index with Auto") {
          val left: Json  = Json.Array(Json.Number("1"), Json.Number("2"))
          val right: Json = Json.Array(Json.Number("3"), Json.Number("4"))
          val merged      = left.merge(right)
          assertTrue(merged.elements == zio.blocks.chunk.Chunk(Json.Number("3"), Json.Number("4")))
        },
        test("merge arrays concatenates them with Concat") {
          val left: Json  = Json.Array(Json.Number("1"), Json.Number("2"))
          val right: Json = Json.Array(Json.Number("3"), Json.Number("4"))
          val merged      = left.merge(right, MergeStrategy.Concat)
          assertTrue(merged.elements.length == 4)
        },
        test("merge arrays by index preserves extra elements from longer array") {
          val left: Json  = Json.Array(Json.Number("1"), Json.Number("2"), Json.Number("3"))
          val right: Json = Json.Array(Json.Number("10"), Json.Number("20"))
          val merged      = left.merge(right)
          assertTrue(
            merged.elements == zio.blocks.chunk.Chunk(Json.Number("10"), Json.Number("20"), Json.Number("3"))
          )
        },
        test("merge arrays by index with right longer than left") {
          val left: Json  = Json.Array(Json.Number("1"))
          val right: Json = Json.Array(Json.Number("10"), Json.Number("20"), Json.Number("30"))
          val merged      = left.merge(right)
          assertTrue(
            merged.elements == zio.blocks.chunk.Chunk(Json.Number("10"), Json.Number("20"), Json.Number("30"))
          )
        },
        test("merge nested arrays recursively with Auto") {
          val left   = Json.Object("arr" -> Json.Array(Json.Number("1"), Json.Number("2")))
          val right  = Json.Object("arr" -> Json.Array(Json.Number("10")))
          val merged = left.merge(right)
          assertTrue(
            merged.get("arr").one.map(_.elements) == Right(zio.blocks.chunk.Chunk(Json.Number("10"), Json.Number("2")))
          )
        },
        test("merge with Shallow only merges at root level") {
          val left = Json.Object(
            "a" -> Json.Object("x" -> Json.Number("1"), "y" -> Json.Number("2"))
          )
          val right = Json.Object(
            "a" -> Json.Object("z" -> Json.Number("3"))
          )
          val merged = left.merge(right, MergeStrategy.Shallow)
          assertTrue(
            merged.get("a").get("z").as[BigDecimal] == Right(BigDecimal(3)),
            merged.get("a").get("x").isFailure,
            merged.get("a").get("y").isFailure
          )
        },
        test("merge with Shallow on nested arrays replaces at root") {
          val left: Json  = Json.Array(Json.Object("a" -> Json.Number("1")))
          val right: Json = Json.Array(Json.Object("b" -> Json.Number("2")))
          val merged      = left.merge(right, MergeStrategy.Shallow)
          assertTrue(merged.get(0).get("b").as[BigDecimal] == Right(BigDecimal(2)))
        }
      ),
      suite("parsing and encoding")(
        test("parse valid JSON string") {
          val result = Json.parse("""{"name": "Alice", "age": 30}""")
          assertTrue(
            result.isRight,
            result.toOption.get.get("name").as[String] == Right("Alice"),
            result.toOption.get.get("age").as[BigDecimal] == Right(BigDecimal(30))
          )
        },
        test("parse JSON array") {
          val result = Json.parse("""[1, 2, 3]""")
          assertTrue(result.isRight, result.toOption.get.elements.length == 3)
        },
        test("parse JSON primitives") {
          assertTrue(
            Json.parse("\"hello\"").toOption.get == Json.String("hello"),
            Json.parse("42").toOption.get == Json.Number("42"),
            Json.parse("true").toOption.get == Json.Boolean(true),
            Json.parse("false").toOption.get == Json.Boolean(false),
            Json.parse("null").toOption.get == Json.Null
          )
        },
        test("encode produces valid JSON") {
          val obj = Json.Object(
            "name"   -> Json.String("Alice"),
            "scores" -> Json.Array(Json.Number("100"), Json.Number("95"))
          )
          val encoded = obj.print
          assertTrue(encoded.contains("\"name\":\"Alice\"") || encoded.contains("\"name\": \"Alice\""))
        },
        test("roundtrip parsing and encoding") {
          val original = Json.Object(
            "string" -> Json.String("hello"),
            "number" -> Json.Number("42.5"),
            "bool"   -> Json.Boolean(true),
            "null"   -> Json.Null,
            "array"  -> Json.Array(Json.Number("1"), Json.Number("2")),
            "nested" -> Json.Object("x" -> Json.Number("1"))
          )
          val encoded = original.print
          val parsed  = Json.parse(encoded)
          assertTrue(parsed.isRight, parsed.toOption.get == original)
        }
      ),
      suite("equality and comparison")(
        test("object equality is order-independent") {
          val obj1 = Json.Object("a" -> Json.Number("1"), "b" -> Json.Number("2"))
          val obj2 = Json.Object("b" -> Json.Number("2"), "a" -> Json.Number("1"))
          assertTrue(obj1 == obj2, obj1.hashCode() == obj2.hashCode())
        },
        test("array equality is order-dependent") {
          val arr1 = Json.Array(Json.Number("1"), Json.Number("2"))
          val arr2 = Json.Array(Json.Number("2"), Json.Number("1"))
          assertTrue(arr1 != arr2)
        },
        test("compare orders by type then value") {
          assertTrue(
            Json.Null.compare(Json.Boolean(true)) < 0,
            Json.Boolean(true).compare(Json.Number("1")) < 0,
            Json.Number("1").compare(Json.String("a")) < 0,
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
          val dv = Json.Number("42").toDynamicValue
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
          assertTrue(
            json.get("name").as[String] == Right("test"),
            json.get("count").as[BigDecimal] == Right(BigDecimal(10))
          )
        }
      ),
      suite("transformation methods")(
        test("transformUp applies function bottom-up") {
          val json = Json.Object(
            "a" -> Json.Object("b" -> Json.Number("1")),
            "c" -> Json.Number("2")
          )
          // Double all numbers
          val transformed = json.transformUp { (_, j) =>
            j match {
              case Json.Number(n) => Json.Number((BigDecimal(n) * 2).toString)
              case other          => other
            }
          }
          assertTrue(
            transformed.get("a").get("b").as[BigDecimal] == Right(BigDecimal(2)),
            transformed.get("c").as[BigDecimal] == Right(BigDecimal(4))
          )
        },
        test("transformDown applies function top-down") {
          val json  = Json.Object("x" -> Json.Number("10"))
          var order = Vector.empty[String]
          json.transformDown { (path, j) =>
            order = order :+ path.toString
            j
          }
          // Root should be visited before children (root path renders as ".")
          assertTrue(order.head == ".", order.contains(".x"))
        },
        test("transformKeys renames object keys") {
          val json        = Json.Object("old_name" -> Json.Number("1"), "another_key" -> Json.Number("2"))
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
      suite("prune/retain methods")(
        test("retain keeps matching elements in arrays") {
          val json     = Json.Array(Json.Number("1"), Json.Number("2"), Json.Number("3"), Json.Number("4"))
          val retained = json.retainBoth { (_, j) =>
            j match {
              case Json.Number(n) => BigDecimal(n) > BigDecimal(2)
              case _              => true
            }
          }
          assertTrue(retained.elements == Chunk(Json.Number("3"), Json.Number("4")))
        },
        test("retain keeps matching fields in objects") {
          val json     = Json.Object("a" -> Json.Number("1"), "b" -> Json.Number("2"), "c" -> Json.Number("3"))
          val retained = json.retainBoth { (_, j) =>
            j match {
              case Json.Number(n) => BigDecimal(n) >= BigDecimal(2)
              case _              => true
            }
          }
          assertTrue(retained.get("a").isFailure, retained.get("b").isSuccess, retained.get("c").isSuccess)
        },
        test("prune removes matching elements") {
          val json   = Json.Array(Json.Number("1"), Json.Null, Json.Number("2"), Json.Null)
          val pruned = json.prune(j => j.is(JsonType.Null))
          assertTrue(pruned.elements == Chunk(Json.Number("1"), Json.Number("2")))
        },
        test("partition splits by value predicate") {
          val json          = Json.Array(Json.Number("1"), Json.Number("2"), Json.Number("3"), Json.Number("4"))
          val (evens, odds) = json.partition {
            case Json.Number(n) => n.toInt % 2 == 0
            case _              => false
          }
          assertTrue(
            evens.elements == Chunk(Json.Number("2"), Json.Number("4")),
            odds.elements == Chunk(Json.Number("1"), Json.Number("3"))
          )
        },
        test("partitionPath splits by path predicate") {
          val json = Json.Object(
            "keep" -> Json.Object("a" -> Json.Number("1"), "b" -> Json.Number("2")),
            "drop" -> Json.Object("c" -> Json.Number("3"))
          )
          val (kept, dropped) = json.partitionPath { path =>
            path.nodes.exists {
              case DynamicOptic.Node.Field("keep") => true
              case _                               => false
            }
          }
          assertTrue(
            kept.get("keep").get("a").one == Right(Json.Number("1")),
            kept.get("drop").isFailure,
            dropped.get("drop").get("c").one == Right(Json.Number("3")),
            dropped.get("keep").isFailure
          )
        },
        test("partitionBoth splits by path and value predicate") {
          val json = Json.Object(
            "a" -> Json.Number("1"),
            "b" -> Json.String("x"),
            "c" -> Json.Number("2")
          )
          val (matching, nonMatching) = json.partitionBoth { (path, j) =>
            path.nodes.lastOption.exists {
              case DynamicOptic.Node.Field("a") | DynamicOptic.Node.Field("c") => true
              case _                                                           => false
            } && j.is(JsonType.Number)
          }
          assertTrue(
            matching.get("a").one == Right(Json.Number("1")),
            matching.get("c").one == Right(Json.Number("2")),
            matching.get("b").isFailure,
            nonMatching.get("b").one == Right(Json.String("x")),
            nonMatching.get("a").isFailure
          )
        },
        test("project extracts specific paths") {
          val json = Json.Object(
            "user" -> Json.Object(
              "name"  -> Json.String("Alice"),
              "age"   -> Json.Number("30"),
              "email" -> Json.String("alice@example.com")
            ),
            "extra" -> Json.String("ignored")
          )
          val namePath  = DynamicOptic.root.field("user").field("name")
          val agePath   = DynamicOptic.root.field("user").field("age")
          val projected = json.project(namePath, agePath)
          assertTrue(
            projected.get("user").get("name").as[String] == Right("Alice"),
            projected.get("user").get("age").as[BigDecimal] == Right(BigDecimal(30)),
            projected.get("user").get("email").isFailure,
            projected.get("extra").isFailure
          )
        }
      ),
      suite("folding methods")(
        test("foldUp accumulates bottom-up") {
          val json = Json.Object(
            "a" -> Json.Number("1"),
            "b" -> Json.Object("c" -> Json.Number("2"), "d" -> Json.Number("3"))
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
          val json = Json.Array(Json.Number("1"), Json.Number("2"), Json.Number("3"))
          // Collect paths in order
          val paths = json.foldDown(Vector.empty[String]) { (path, _, acc) =>
            acc :+ path.toString
          }
          // Root visited first, then elements (root path renders as ".")
          assertTrue(paths.head == ".", paths.length == 4) // root + 3 elements
        },
        test("foldUpOrFail stops on error") {
          val json   = Json.Array(Json.Number("1"), Json.String("oops"), Json.Number("3"))
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
          val json   = Json.Object("a" -> Json.Number("1"), "b" -> Json.String("error"))
          val result = json.foldDownOrFail(0) { (_, j, acc) =>
            j match {
              case Json.String(s) if s == "error" => Left(JsonError("Found error"))
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
          val activeUsers = json.select.query {
            case Json.Boolean(true) => true
            case _                  => false
          }
          assertTrue(activeUsers.size == 2)
        },
        test("query returns empty selection when nothing matches") {
          val json   = Json.Object("a" -> Json.Number("1"))
          val result = json.select.query(JsonType.String)
          assertTrue(result.isEmpty)
        },
        test("toKV converts to path-value pairs") {
          val json = Json.Object(
            "a" -> Json.Number("1"),
            "b" -> Json.Object("c" -> Json.Number("2"))
          )
          val kvs = json.toKV
          assertTrue(kvs.length == 2, kvs.exists(_._2 == Json.Number("1")), kvs.exists(_._2 == Json.Number("2")))
        },
        test("fromKV reconstructs JSON from path-value pairs") {
          val json = Json.Object(
            "a" -> Json.Number("1"),
            "b" -> Json.Object("c" -> Json.Number("2"))
          )
          val kvs           = json.toKV
          val reconstructed = Json.fromKV(kvs)
          assertTrue(
            reconstructed.isRight,
            reconstructed.toOption.get.get("a").as[BigDecimal] == Right(BigDecimal(1)),
            reconstructed.toOption.get.get("b").get("c").as[BigDecimal] == Right(BigDecimal(2))
          )
        }
      ),
      suite("Json.from constructor")(
        test("from creates Json from encodable value") {
          assertTrue(
            Json.from("hello") == Json.String("hello"),
            Json.from(42) == Json.Number("42"),
            Json.from(true) == Json.Boolean(true),
            Json.from(Vector(1, 2, 3)) == Json.Array(Json.Number("1"), Json.Number("2"), Json.Number("3"))
          )
        }
      )
    ),
    suite("JsonSelection")(
      test("fluent navigation through nested structure") {
        val json = Json.Object(
          "data" -> Json.Object(
            "users" -> Json.Array(
              Json.Object("name" -> Json.String("Alice"), "age" -> Json.Number("30")),
              Json.Object("name" -> Json.String("Bob"), "age"   -> Json.Number("25"))
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
        val sel1     = JsonSelection.succeed(Json.Number("1"))
        val sel2     = JsonSelection.succeed(Json.Number("2"))
        val combined = sel1 ++ sel2
        assertTrue(combined.either == Right(Vector(Json.Number("1"), Json.Number("2"))))
      },
      test("++ propagates errors") {
        val sel1      = JsonSelection.fail(JsonError("error"))
        val sel2      = JsonSelection.succeed(Json.Number("2"))
        val combined1 = sel1 ++ sel2
        val combined2 = sel2 ++ sel1
        assertTrue(combined1.isFailure, combined2.isFailure)
      },
      test("size operations") {
        val empty    = JsonSelection.empty
        val single   = JsonSelection.succeed(Json.Number("1"))
        val multiple = JsonSelection.succeedMany(Vector(Json.Number("1"), Json.Number("2"), Json.Number("3")))
        assertTrue(empty.isEmpty, empty.size == 0, single.nonEmpty, single.size == 1, multiple.size == 3)
      },
      test("all returns single value or wraps multiple in array") {
        val single   = JsonSelection.succeed(Json.Number("1"))
        val multiple = JsonSelection.succeedMany(Vector(Json.Number("1"), Json.Number("2")))
        assertTrue(
          single.all == Right(Json.Number("1")),
          multiple.all == Right(Json.Array(Json.Number("1"), Json.Number("2")))
        )
      },
      test("any returns first value") {
        val multiple = JsonSelection.succeedMany(Vector(Json.Number("1"), Json.Number("2"), Json.Number("3")))
        assertTrue(multiple.any == Right(Json.Number("1")))
      },
      test("toArray wraps values in array") {
        val selection = JsonSelection.succeedMany(Vector(Json.Number("1"), Json.Number("2")))
        assertTrue(selection.toArray == Right(Json.Array(Json.Number("1"), Json.Number("2"))))
      },
      test("objects/arrays filters by type") {
        val mixed = JsonSelection.succeedMany(
          Vector(
            Json.Object("a" -> Json.Number("1")),
            Json.Array(Json.Number("1")),
            Json.String("hello"),
            Json.Object("b" -> Json.Number("2"))
          )
        )
        assertTrue(mixed.objects.size == 2, mixed.arrays.size == 1)
      },
      test("strings/numbers/booleans filters by type") {
        val mixed = JsonSelection.succeedMany(
          Vector(
            Json.String("hello"),
            Json.Number("42"),
            Json.Boolean(true),
            Json.String("world")
          )
        )
        assertTrue(mixed.strings.size == 2, mixed.numbers.size == 1, mixed.booleans.size == 1)
      }
    ),
    suite("JsonDecoder")(
      test("decode primitives") {
        assertTrue(
          Json.String("hello").as[String] == Right("hello"),
          Json.Number("42").as[Int] == Right(42),
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
        val json = Json.Array(Json.Number("1"), Json.Number("2"), Json.Number("3"))
        assertTrue(json.as[Vector[Int]] == Right(Vector(1, 2, 3)))
      },
      test("decode Map") {
        val json = Json.Object("a" -> Json.Number("1"), "b" -> Json.Number("2"))
        assertTrue(json.as[Map[String, Int]] == Right(Map("a" -> 1, "b" -> 2)))
      }
    ),
    suite("JsonEncoder")(
      test("encode primitives") {
        assertTrue(
          JsonEncoder[String].encode("hello") == Json.String("hello"),
          JsonEncoder[Int].encode(42) == Json.Number("42"),
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
            .encode(Vector(1, 2, 3)) == Json.Array(Json.Number("1"), Json.Number("2"), Json.Number("3"))
        )
      },
      test("encode Map") {
        val encoded = JsonEncoder[Map[String, Int]].encode(Map("a" -> 1, "b" -> 2))
        assert(encoded.is(JsonType.Object))(equalTo(true)) &&
        assertTrue(
          encoded.get("a").as[BigDecimal] == Right(BigDecimal(1)),
          encoded.get("b").as[BigDecimal] == Right(BigDecimal(2))
        )
      }
    ),
    suite("additional coverage")(
      suite("merge strategies")(
        test("merge with Auto strategy merges objects deeply") {
          val left   = Json.Object("a" -> Json.Object("x" -> Json.Number("1"), "y" -> Json.Number("2")))
          val right  = Json.Object("a" -> Json.Object("y" -> Json.Number("3"), "z" -> Json.Number("4")))
          val merged = left.merge(right, MergeStrategy.Auto)
          assertTrue(
            merged.get("a").get("x").as[BigDecimal] == Right(BigDecimal(1)),
            merged.get("a").get("y").as[BigDecimal] == Right(BigDecimal(3)),
            merged.get("a").get("z").as[BigDecimal] == Right(BigDecimal(4))
          )
        },
        test("merge with Shallow strategy replaces nested objects") {
          val left   = Json.Object("a" -> Json.Object("x" -> Json.Number("1"), "y" -> Json.Number("2")))
          val right  = Json.Object("a" -> Json.Object("z" -> Json.Number("3")))
          val merged = left.merge(right, MergeStrategy.Shallow)
          assertTrue(
            merged.get("a").get("x").isFailure,
            merged.get("a").get("z").as[BigDecimal] == Right(BigDecimal(3))
          )
        },
        test("merge with Concat strategy concatenates arrays") {
          val left   = Json.Array(Json.Number("1"), Json.Number("2"))
          val right  = Json.Array(Json.Number("3"), Json.Number("4"))
          val merged = left.merge(right, MergeStrategy.Concat)
          assertTrue(merged.elements == Chunk(Json.Number("1"), Json.Number("2"), Json.Number("3"), Json.Number("4")))
        },
        test("merge non-matching types replaces with right") {
          val left   = Json.Number("1")
          val right  = Json.String("hello")
          val merged = left.merge(right)
          assertTrue(merged == right)
        }
      ),
      suite("encoding methods")(
        test("printBytes produces valid bytes") {
          val obj    = Json.Object("key" -> Json.String("value"))
          val bytes  = obj.printBytes
          val parsed = Json.parse(bytes)
          assertTrue(parsed.isRight, parsed.toOption.get == obj)
        },
        test("parse from bytes works") {
          val jsonStr = """{"name":"Alice"}"""
          val bytes   = jsonStr.getBytes("UTF-8")
          val parsed  = Json.parse(bytes)
          assertTrue(parsed.isRight, parsed.toOption.get.get("name").as[String] == Right("Alice"))
        }
      ),
      suite("normalization")(
        test("normalize applies sortKeys, dropNulls, and dropEmpty") {
          val json = Json.Object(
            "z" -> Json.Number("1"),
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
          val json    = Json.Array(Json.Number("1"), Json.Null, Json.Number("2"), Json.Null)
          val dropped = json.dropNulls
          assertTrue(dropped.elements == Chunk(Json.Number("1"), Json.Number("2")))
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
          val json      = Json.Object("a" -> Json.Number("1"), "b" -> Json.Number("2"), "c" -> Json.Number("3"))
          val path      = DynamicOptic.mapValues
          val selection = json.get(path)
          assertTrue(
            selection.either.toOption.get.toSet == Set[Json](Json.Number("1"), Json.Number("2"), Json.Number("3"))
          )
        },
        test("get with mapKeys returns all object keys as strings") {
          val json      = Json.Object("a" -> Json.Number("1"), "b" -> Json.Number("2"))
          val path      = DynamicOptic.mapKeys
          val selection = json.get(path)
          assertTrue(selection.either.toOption.get.toSet == Set[Json](Json.String("a"), Json.String("b")))
        },
        test("modify with mapValues transforms all values") {
          val json    = Json.Object("a" -> Json.Number("1"), "b" -> Json.Number("2"))
          val path    = DynamicOptic.mapValues
          val updated = json.modify(path) {
            case Json.Number(n) => Json.Number((BigDecimal(n) * 10).toString)
            case other          => other
          }
          assertTrue(
            updated.get("a").as[BigDecimal] == Right(BigDecimal(10)),
            updated.get("b").as[BigDecimal] == Right(BigDecimal(20))
          )
        },
        test("get with atIndices returns multiple elements") {
          val json      = Json.Array(Json.String("a"), Json.String("b"), Json.String("c"), Json.String("d"))
          val path      = DynamicOptic.root.atIndices(0, 2)
          val selection = json.get(path)
          assertTrue(selection.either == Right(Vector(Json.String("a"), Json.String("c"))))
        },
        test("modify with atIndices transforms specific elements") {
          val arr     = Json.Array(Json.Number("1"), Json.Number("2"), Json.Number("3"), Json.Number("4"))
          val path    = DynamicOptic.root.atIndices(1, 3)
          val updated = arr.modify(path) {
            case Json.Number(n) => Json.Number((BigDecimal(n) * 10).toString)
            case other          => other
          }
          assertTrue(
            updated.get(0).one == Right(Json.Number("1")),
            updated.get(1).one == Right(Json.Number("20")),
            updated.get(2).one == Right(Json.Number("3")),
            updated.get(3).one == Right(Json.Number("40"))
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
            (DynamicOptic.root.field("a"), Json.Number("1")),
            (DynamicOptic.root.field("b").field("c"), Json.Number("2"))
          )
          val json = Json.fromKVUnsafe(kvs)
          assertTrue(
            json.get("a").as[BigDecimal] == Right(BigDecimal(1)),
            json.get("b").get("c").as[BigDecimal] == Right(BigDecimal(2))
          )
        },
        test("fromKV with empty seq returns Null") {
          val result = Json.fromKV(Seq.empty)
          assertTrue(result == Right(Json.Null))
        }
      ),
      suite("JsonSelection additional methods")(
        test("one returns error for empty selection") {
          val empty = JsonSelection.empty
          assertTrue(empty.one.isLeft)
        },
        test("one returns error for multiple values") {
          val multiple = JsonSelection.succeedMany(Vector(Json.Number("1"), Json.Number("2")))
          assertTrue(multiple.one.isLeft)
        },
        test("one returns value for single element") {
          val single = JsonSelection.succeed(Json.Number("42"))
          assertTrue(single.one == Right(Json.Number("42")))
        },
        test("collect extracts matching values") {
          val selection = JsonSelection.succeedMany(Vector(Json.Number("1"), Json.String("a"), Json.Number("2")))
          val numbers   = selection.collect { case Json.Number(n) => n }
          assertTrue(numbers == Right(Vector("1", "2")))
        },
        test("orElse returns alternative on failure") {
          val failed   = JsonSelection.fail(JsonError("error"))
          val fallback = JsonSelection.succeed(Json.Number("42"))
          val result   = failed.orElse(fallback)
          assertTrue(result.one == Right(Json.Number("42")))
        },
        test("orElse returns original on success") {
          val success  = JsonSelection.succeed(Json.Number("1"))
          val fallback = JsonSelection.succeed(Json.Number("2"))
          val result   = success.orElse(fallback)
          assertTrue(result.one == Right(Json.Number("1")))
        },
        test("getOrElse returns values on success") {
          val selection = JsonSelection.succeedMany(Vector(Json.Number("1"), Json.Number("2")))
          val result    = selection.getOrElse(Vector(Json.Null))
          assertTrue(result == Vector(Json.Number("1"), Json.Number("2")))
        },
        test("getOrElse returns default on failure") {
          val failed = JsonSelection.fail(JsonError("error"))
          val result = failed.getOrElse(Vector(Json.Null))
          assertTrue(result == Vector(Json.Null))
        },
        test("map transforms all values") {
          val selection = JsonSelection.succeedMany(Vector(Json.Number("1"), Json.Number("2")))
          val mapped    = selection.map {
            case Json.Number(n) => Json.Number((BigDecimal(n) * 2).toString)
            case other          => other
          }
          assertTrue(mapped.either == Right(Vector(Json.Number("2"), Json.Number("4"))))
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
          val selection = JsonSelection.succeedMany(Vector(Json.Number("1"), Json.Number("2"), Json.Number("3")))
          val filtered  = selection.filter {
            case Json.Number(n) => BigDecimal(n) > BigDecimal(1)
            case _              => false
          }
          assertTrue(filtered.either == Right(Vector(Json.Number("2"), Json.Number("3"))))
        },
        test("as decodes single value") {
          val selection = JsonSelection.succeed(Json.Number("42"))
          assertTrue(selection.as[Int] == Right(42))
        },
        test("asAll decodes all values") {
          val selection = JsonSelection.succeedMany(Vector(Json.Number("1"), Json.Number("2"), Json.Number("3")))
          assertTrue(selection.asAll[Int] == Right(Vector(1, 2, 3)))
        },
        test("nulls filters to only nulls") {
          val selection = JsonSelection.succeedMany(Vector(Json.Null, Json.Number("1"), Json.Null))
          assertTrue(selection.nulls.size == 2)
        },
        test("one fails on empty selection") {
          val empty = JsonSelection.empty
          assertTrue(empty.one.isLeft)
        },
        test("any fails on empty selection") {
          val empty = JsonSelection.empty
          assertTrue(empty.any.isLeft)
        },
        test("error returns Some on failure") {
          val failed = JsonSelection.fail(JsonError("test error"))
          assertTrue(failed.error.isDefined, failed.error.get.message == "test error")
        },
        test("error returns None on success") {
          val success = JsonSelection.succeed(Json.Number("1"))
          assertTrue(success.error.isEmpty)
        },
        test("values returns Some on success") {
          val success = JsonSelection.succeed(Json.Number("1"))
          assertTrue(success.values.isDefined)
        },
        test("values returns None on failure") {
          val failed = JsonSelection.fail(JsonError("error"))
          assertTrue(failed.values.isEmpty)
        },
        test("any.toOption returns first value") {
          val selection = JsonSelection.succeedMany(Vector(Json.Number("1"), Json.Number("2")))
          assertTrue(selection.any.toOption.contains(Json.Number("1")))
        },
        test("any.toOption returns None for empty") {
          val empty = JsonSelection.empty
          assertTrue(empty.any.toOption.isEmpty)
        },
        test("toVector returns empty on failure") {
          val failed = JsonSelection.fail(JsonError("error"))
          assertTrue(failed.toVector.isEmpty)
        },
        test("numbers/booleans/nulls type filtering") {
          val numSel  = JsonSelection.succeed(Json.Number("42"))
          val boolSel = JsonSelection.succeed(Json.Boolean(true))
          val nullSel = JsonSelection.succeed(Json.Null)
          assertTrue(
            numSel.numbers.isSuccess,
            numSel.booleans.isEmpty,
            boolSel.booleans.isSuccess,
            boolSel.nulls.isEmpty,
            nullSel.nulls.isSuccess,
            nullSel.numbers.isEmpty
          )
        },
        test("query with predicate finds all matching values recursively") {
          val json = Json.Object(
            "name"  -> Json.String("Alice"),
            "age"   -> Json.Number("30"),
            "items" -> Json.Array(
              Json.String("apple"),
              Json.Number("42"),
              Json.String("banana")
            )
          )
          val strings = json.select.query(JsonType.String).toVector
          assertTrue(
            strings.length == 3,
            strings.contains(Json.String("Alice")),
            strings.contains(Json.String("apple")),
            strings.contains(Json.String("banana"))
          )
        },
        test("query with value predicate finds matching values") {
          val json = Json.Array(
            Json.Number("1"),
            Json.Number("10"),
            Json.Number("5"),
            Json.Number("20")
          )
          val largeNumbers = json.select.query { j =>
            j.unwrap(JsonType.Number).exists(_ > 5)
          }.toVector
          assertTrue(
            largeNumbers.length == 2,
            largeNumbers.contains(Json.Number("10")),
            largeNumbers.contains(Json.Number("20"))
          )
        },
        test("queryPath finds values at paths matching predicate") {
          val json = Json.Object(
            "user" -> Json.Object(
              "name" -> Json.String("Alice"),
              "age"  -> Json.Number("30")
            ),
            "metadata" -> Json.Object(
              "created" -> Json.String("2024-01-01")
            )
          )
          val userFields = json.select.queryPath { path =>
            path.nodes.headOption.exists {
              case f: DynamicOptic.Node.Field => f.name == "user"
              case _                          => false
            }
          }.toVector
          assertTrue(userFields.nonEmpty)
        },
        test("queryPath finds values at specific indices") {
          val json = Json.Array(
            Json.String("zero"),
            Json.String("one"),
            Json.String("two")
          )
          val atIndexOne = json.select.queryPath { path =>
            path.nodes.exists {
              case idx: DynamicOptic.Node.AtIndex => idx.index == 1
              case _                              => false
            }
          }.toVector
          assertTrue(atIndexOne == Vector(Json.String("one")))
        },
        test("queryBoth finds values matching both path and value predicates") {
          val json = Json.Object(
            "numbers" -> Json.Array(
              Json.Number("1"),
              Json.Number("100"),
              Json.Number("5")
            ),
            "strings" -> Json.Array(
              Json.String("a"),
              Json.String("b")
            )
          )
          val largeNumbersInNumbersField = json.select.queryBoth { (path, value) =>
            val inNumbersField = path.nodes.exists {
              case f: DynamicOptic.Node.Field => f.name == "numbers"
              case _                          => false
            }
            val isLargeNumber = value.unwrap(JsonType.Number).exists(_ > 10)
            inNumbersField && isLargeNumber
          }.toVector
          assertTrue(largeNumbersInNumbersField == Vector(Json.Number("100")))
        },
        test("queryBoth returns empty when no matches") {
          val json    = Json.Object("a" -> Json.Number("1"))
          val results = json.select.queryBoth { (_, value) =>
            value.is(JsonType.String)
          }.toVector
          assertTrue(results.isEmpty)
        }
      ),
      suite("DynamicValue conversion edge cases")(
        test("fromDynamicValue handles Variant") {
          val dv   = DynamicValue.Variant("SomeCase", DynamicValue.Primitive(PrimitiveValue.Int(42)))
          val json = Json.fromDynamicValue(dv)
          assertTrue(json.get("SomeCase").as[BigDecimal] == Right(BigDecimal(42)))
        },
        test("fromDynamicValue handles Sequence") {
          val dv = DynamicValue.Sequence(
            Vector(
              DynamicValue.Primitive(PrimitiveValue.Int(1)),
              DynamicValue.Primitive(PrimitiveValue.Int(2))
            )
          )
          val json = Json.fromDynamicValue(dv)
          assertTrue(json.elements == Chunk(Json.Number("1"), Json.Number("2")))
        },
        test("fromDynamicValue handles Map with string keys") {
          val dv = DynamicValue.Map(
            Vector(
              (DynamicValue.Primitive(PrimitiveValue.String("a")), DynamicValue.Primitive(PrimitiveValue.Int(1))),
              (DynamicValue.Primitive(PrimitiveValue.String("b")), DynamicValue.Primitive(PrimitiveValue.Int(2)))
            )
          )
          val json = Json.fromDynamicValue(dv)
          assert(json.is(JsonType.Object))(equalTo(true)) &&
          assertTrue(
            json.get("a").as[BigDecimal] == Right(BigDecimal(1)),
            json.get("b").as[BigDecimal] == Right(BigDecimal(2))
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
          assert(json.is(JsonType.Array))(equalTo(true)) &&
          assertTrue(json.elements.length == 2)
        },
        test("toDynamicValue converts Long when out of Int range") {
          val json = Json.Number(Long.MaxValue)
          val dv   = json.toDynamicValue
          dv match {
            case DynamicValue.Primitive(pv: PrimitiveValue.Long) =>
              assertTrue(pv.value == Long.MaxValue)
            case _ => assertTrue(false)
          }
        },
        test("toDynamicValue converts BigDecimal for decimals") {
          val json = Json.Number("123.456")
          val dv   = json.toDynamicValue
          dv match {
            case DynamicValue.Primitive(pv: PrimitiveValue.BigDecimal) =>
              assertTrue(pv.value == BigDecimal("123.456"))
            case _ => assertTrue(false)
          }
        },
        test("toDynamicValue converts arrays") {
          val json = Json.Array(Json.Number("1"), Json.Number("2"))
          val dv   = json.toDynamicValue
          dv match {
            case DynamicValue.Sequence(elems) =>
              assertTrue(elems.length == 2)
            case _ => assertTrue(false)
          }
        },
        test("toDynamicValue converts objects") {
          val json = Json.Object("a" -> Json.Number("1"))
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
            Json.Number("1").compare(Json.Number("2")) < 0,
            Json.Number("2").compare(Json.Number("1")) > 0,
            Json.Number("1").compare(Json.Number("1")) == 0,
            Json.String("a").compare(Json.String("b")) < 0,
            Json.Boolean(false).compare(Json.Boolean(true)) < 0
          )
        },
        test("compare arrays element by element") {
          assertTrue(
            Json.Array(Json.Number("1")).compare(Json.Array(Json.Number("2"))) < 0,
            Json.Array(Json.Number("1"), Json.Number("2")).compare(Json.Array(Json.Number("1"))) > 0
          )
        },
        test("compare objects by sorted keys") {
          val obj1 = Json.Object("a" -> Json.Number("1"))
          val obj2 = Json.Object("b" -> Json.Number("1"))
          assertTrue(obj1.compare(obj2) < 0)
        }
      ),
      suite("constructors")(
        test("Json.True and Json.False constants") {
          assertTrue(Json.True == Json.Boolean(true), Json.False == Json.Boolean(false))
        },
        test("Json.Number with different representations") {
          assertTrue(
            Json.Number("42") == Json.Number("42"),
            Json.Number("42") == Json.Number("42"),
            Json.Number("3.14") == Json.Number("3.14")
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
              "inner_key" -> Json.Number("1")
            )
          )
          val transformed = json.transformKeys((_, k) => k.toUpperCase)
          assertTrue(
            transformed.get("OUTER_KEY").isSuccess,
            transformed.get("OUTER_KEY").get("INNER_KEY").isSuccess
          )
        },
        test("transformKeys works on arrays containing objects") {
          val arr = Json.Array(
            Json.Object("snake_case"  -> Json.Number("1")),
            Json.Object("another_key" -> Json.Number("2"))
          )
          val transformed = arr.transformKeys((_, k) => k.replace("_", "-"))
          assertTrue(
            transformed.get(0).get("snake-case").isSuccess,
            transformed.get(1).get("another-key").isSuccess
          )
        }
      ),
      suite("retain/prune/partition edge cases")(
        test("retain on primitives returns unchanged") {
          val json     = Json.Number("42")
          val retained = json.retainBoth((_, _) => true)
          assertTrue(retained == json)
        },
        test("prune on primitives returns unchanged") {
          val json   = Json.Number("42")
          val pruned = json.pruneBoth((_, _) => false)
          assertTrue(pruned == json)
        },
        test("partition on primitives") {
          val json                    = Json.String("hello")
          val (matching, nonMatching) = json.partition(_.is(JsonType.String))
          assertTrue(matching == json, nonMatching == Json.Null)
        }
      ),
      suite("project edge cases")(
        test("project with empty paths returns Null") {
          val json      = Json.Object("a" -> Json.Number("1"))
          val projected = json.project()
          assertTrue(projected == Json.Null)
        },
        test("project with non-existent paths") {
          val json      = Json.Object("a" -> Json.Number("1"))
          val path      = DynamicOptic.root.field("nonexistent")
          val projected = json.project(path)
          assertTrue(projected == Json.Null)
        }
      ),
      suite("AtMapKey operations")(
        test("get with atKey retrieves value by key") {
          val json   = Json.Object("alice" -> Json.Number("1"), "bob" -> Json.Number("2"))
          val path   = DynamicOptic.root.atKey("alice")(Schema.string)
          val result = json.get(path)
          assertTrue(result.one == Right(Json.Number("1")))
        },
        test("get with atKey returns empty for missing key") {
          val json   = Json.Object("alice" -> Json.Number("1"))
          val path   = DynamicOptic.root.atKey("missing")(Schema.string)
          val result = json.get(path)
          assertTrue(result.toVector.isEmpty)
        },
        test("modify with atKey updates value at key") {
          val json    = Json.Object("alice" -> Json.Number("1"), "bob" -> Json.Number("2"))
          val path    = DynamicOptic.root.atKey("alice")(Schema.string)
          val updated = json.modify(path) {
            case Json.Number(n) => Json.Number((BigDecimal(n) * 10).toString)
            case other          => other
          }
          assertTrue(
            updated.get("alice").as[BigDecimal] == Right(BigDecimal(10)),
            updated.get("bob").as[BigDecimal] == Right(BigDecimal(2))
          )
        },
        test("modify with atKey does nothing for missing key") {
          val json    = Json.Object("alice" -> Json.Number("1"))
          val path    = DynamicOptic.root.atKey("missing")(Schema.string)
          val updated = json.modify(path)(_ => Json.Number("99"))
          assertTrue(updated == json)
        }
      ),
      suite("AtMapKeys operations")(
        test("get with atKeys retrieves multiple values") {
          val json   = Json.Object("a" -> Json.Number("1"), "b" -> Json.Number("2"), "c" -> Json.Number("3"))
          val path   = DynamicOptic.root.atKeys("a", "c")(Schema.string)
          val result = json.get(path)
          assertTrue(result.either == Right(Vector(Json.Number("1"), Json.Number("3"))))
        },
        test("get with atKeys returns only existing keys") {
          val json   = Json.Object("a" -> Json.Number("1"), "b" -> Json.Number("2"))
          val path   = DynamicOptic.root.atKeys("a", "missing", "b")(Schema.string)
          val result = json.get(path)
          assertTrue(result.either == Right(Vector(Json.Number("1"), Json.Number("2"))))
        },
        test("modify with atKeys updates multiple values") {
          val json    = Json.Object("a" -> Json.Number("1"), "b" -> Json.Number("2"), "c" -> Json.Number("3"))
          val path    = DynamicOptic.root.atKeys("a", "c")(Schema.string)
          val updated = json.modify(path) {
            case Json.Number(n) => Json.Number((BigDecimal(n) * 10).toString)
            case other          => other
          }
          assertTrue(
            updated.get("a").as[BigDecimal] == Right(BigDecimal(10)),
            updated.get("b").as[BigDecimal] == Right(BigDecimal(2)),
            updated.get("c").as[BigDecimal] == Right(BigDecimal(30))
          )
        },
        test("get with atKeys on non-object returns empty") {
          val json   = Json.Array(Json.Number("1"), Json.Number("2"))
          val path   = DynamicOptic.root.atKeys("a")(Schema.string)
          val result = json.get(path)
          assertTrue(result.toVector.isEmpty)
        }
      ),
      suite("Elements delete operations")(
        test("delete with elements removes all array elements") {
          val json    = Json.Array(Json.Number("1"), Json.Number("2"), Json.Number("3"))
          val path    = DynamicOptic.elements
          val deleted = json.delete(path)
          assertTrue(deleted == Json.Array())
        },
        test("delete with elements on non-array returns unchanged") {
          val json    = Json.Object("a" -> Json.Number("1"))
          val path    = DynamicOptic.elements
          val deleted = json.delete(path)
          assertTrue(deleted == json)
        },
        test("delete nested elements through field path") {
          val json = Json.Object(
            "items" -> Json.Array(Json.Number("1"), Json.Number("2"), Json.Number("3"))
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
              "age"  -> Json.Number("30")
            )
          )
          val path    = DynamicOptic.root.field("user").field("name")
          val deleted = json.delete(path)
          assertTrue(
            deleted.get("user").get("age").as[BigDecimal] == Right(BigDecimal(30)),
            deleted.get("user").get("name").toVector.isEmpty
          )
        },
        test("delete nested element through array path") {
          val json = Json.Object(
            "items" -> Json.Array(Json.Number("1"), Json.Number("2"), Json.Number("3"))
          )
          val path    = DynamicOptic.root.field("items").at(1)
          val deleted = json.delete(path)
          assertTrue(
            deleted.get("items").toVector == Vector(Json.Array(Json.Number("1"), Json.Number("3")))
          )
        },
        test("delete deeply nested field") {
          val json = Json.Object(
            "a" -> Json.Object(
              "b" -> Json.Object(
                "c" -> Json.Number("1"),
                "d" -> Json.Number("2")
              )
            )
          )
          val path    = DynamicOptic.root.field("a").field("b").field("c")
          val deleted = json.delete(path)
          assertTrue(
            deleted.get("a").get("b").get("d").as[BigDecimal] == Right(BigDecimal(2)),
            deleted.get("a").get("b").get("c").toVector.isEmpty
          )
        }
      ),
      suite("modifyOrFail with Elements and MapValues")(
        test("modifyOrFail with elements succeeds when all match") {
          val json   = Json.Array(Json.Number("1"), Json.Number("2"), Json.Number("3"))
          val path   = DynamicOptic.elements
          val result = json.modifyOrFail(path) { case Json.Number(n) =>
            Json.Number((BigDecimal(n) * 2).toString)
          }
          assertTrue(result == Right(Json.Array(Json.Number("2"), Json.Number("4"), Json.Number("6"))))
        },
        test("modifyOrFail with elements fails when partial function not defined") {
          val json   = Json.Array(Json.Number("1"), Json.String("not a number"), Json.Number("3"))
          val path   = DynamicOptic.elements
          val result = json.modifyOrFail(path) { case Json.Number(n) =>
            Json.Number((BigDecimal(n) * 2).toString)
          }
          assertTrue(result.isLeft)
        },
        test("modifyOrFail with mapValues succeeds when all match") {
          val json   = Json.Object("a" -> Json.Number("1"), "b" -> Json.Number("2"))
          val path   = DynamicOptic.mapValues
          val result = json.modifyOrFail(path) { case Json.Number(n) =>
            Json.Number((BigDecimal(n) * 10).toString)
          }
          assertTrue(
            result.map(_.get("a").as[BigDecimal]) == Right(Right(BigDecimal(10))),
            result.map(_.get("b").as[BigDecimal]) == Right(Right(BigDecimal(20)))
          )
        },
        test("modifyOrFail with mapValues fails when partial function not defined") {
          val json   = Json.Object("a" -> Json.Number("1"), "b" -> Json.String("not a number"))
          val path   = DynamicOptic.mapValues
          val result = json.modifyOrFail(path) { case Json.Number(n) =>
            Json.Number((BigDecimal(n) * 10).toString)
          }
          assertTrue(result.isLeft)
        },
        test("modifyOrFail with nested path and elements") {
          val json = Json.Object(
            "items" -> Json.Array(Json.Number("1"), Json.Number("2"))
          )
          val path   = DynamicOptic.root.field("items").elements
          val result = json.modifyOrFail(path) { case Json.Number(n) =>
            Json.Number((BigDecimal(n) + 100).toString)
          }
          assertTrue(result == Right(Json.Object("items" -> Json.Array(Json.Number("101"), Json.Number("102")))))
        }
      ),
      suite("insertOrFail edge cases")(
        test("insertOrFail fails for non-existent nested path") {
          val json   = Json.Object()
          val path   = DynamicOptic.root.field("a").field("b")
          val result = json.insertOrFail(path, Json.Number("42"))
          assertTrue(result.isLeft)
        },
        test("insertOrFail at array index extends array") {
          val json   = Json.Array(Json.Number("1"), Json.Number("2"))
          val path   = DynamicOptic.root.at(2)
          val result = json.insertOrFail(path, Json.Number("3"))
          assertTrue(result == Right(Json.Array(Json.Number("1"), Json.Number("2"), Json.Number("3"))))
        },
        test("insertOrFail fails when field already exists") {
          val json   = Json.Object("a" -> Json.Number("1"))
          val path   = DynamicOptic.root.field("a")
          val result = json.insertOrFail(path, Json.Number("99"))
          assertTrue(result.isLeft)
        },
        test("insertOrFail succeeds for new field") {
          val json   = Json.Object("a" -> Json.Number("1"))
          val path   = DynamicOptic.root.field("b")
          val result = json.insertOrFail(path, Json.Number("2"))
          assertTrue(result == Right(Json.Object("a" -> Json.Number("1"), "b" -> Json.Number("2"))))
        }
      ),
      suite("JSON ordering")(
        test("ordering sorts json values correctly") {
          val values = List(
            Json.Number("3"),
            Json.Number("1"),
            Json.Number("2")
          )
          val sorted = values.sorted(Json.ordering)
          assertTrue(sorted == List(Json.Number("1"), Json.Number("2"), Json.Number("3")))
        },
        test("ordering handles mixed types by type order") {
          val values = List(
            Json.Object("a" -> Json.Number("1")),
            Json.Null,
            Json.Boolean(true),
            Json.Number("1"),
            Json.String("hello"),
            Json.Array(Json.Number("1"))
          )
          val sorted = values.sorted(Json.ordering)
          // Null < Boolean < Number < String < Array < Object
          assertTrue(
            sorted(0) == Json.Null,
            sorted(1) == Json.Boolean(true),
            sorted(2) == Json.Number("1"),
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
          val json   = Json.Object("a" -> Json.Number("1"))
          val result = json.print
          assertTrue(result.contains("a") && result.contains("1"))
        },
        test("printBytes produces valid output") {
          val obj     = Json.Object("name" -> Json.String("test"))
          val bytes   = obj.printBytes
          val decoded = Json.parse(new String(bytes, "UTF-8"))
          assertTrue(decoded == Right(obj))
        },
        test("parse handles whitespace correctly") {
          val input  = """  {  "a"  :  1  }  """
          val result = Json.parse(input)
          assertTrue(result == Right(Json.Object("a" -> Json.Number("1"))))
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
        test("JsonSelection.anyUnsafe throws JsonError directly") {
          val thrown =
            try {
              JsonSelection.empty.anyUnsafe
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
          val obj   = Json.Object("name" -> Json.String("Alice"), "age" -> Json.Number("30"))
          val chunk = obj.printChunk
          assertTrue(chunk.length > 0)
        },
        test("parse from Chunk[Byte] works correctly") {
          val obj    = Json.Object("key" -> Json.String("value"))
          val chunk  = obj.printChunk
          val parsed = Json.parse(chunk)
          assertTrue(parsed == Right(obj))
        },
        test("roundtrip printChunk and parse(Chunk) preserves data") {
          val obj = Json.Object(
            "users" -> Json.Array(
              Json.Object("name" -> Json.String("Alice"), "age" -> Json.Number("30")),
              Json.Object("name" -> Json.String("Bob"), "age"   -> Json.Number("25"))
            ),
            "count"  -> Json.Number("2"),
            "active" -> Json.Boolean(true)
          )
          val chunk  = obj.printChunk
          val parsed = Json.parse(chunk)
          assertTrue(parsed == Right(obj))
        },
        test("printChunk with custom WriterConfig") {
          val obj    = Json.Object("a" -> Json.Number("1"))
          val chunk  = obj.printChunk(WriterConfig)
          val parsed = Json.parse(chunk)
          assertTrue(parsed == Right(obj))
        },
        test("parse from Chunk[Byte] with custom ReaderConfig") {
          val obj    = Json.Object("a" -> Json.Number("1"))
          val chunk  = obj.printChunk
          val parsed = Json.parse(chunk, ReaderConfig)
          assertTrue(parsed == Right(obj))
        },
        test("parse works same for Chunk") {
          val obj    = Json.Object("test" -> Json.String("value"))
          val chunk  = obj.printChunk
          val parsed = Json.parse(chunk)
          assertTrue(parsed == Right(obj))
        },
        test("parse with config works for Chunk") {
          val obj    = Json.Object("test" -> Json.String("value"))
          val chunk  = obj.printChunk
          val parsed = Json.parse(chunk, ReaderConfig)
          assertTrue(parsed == Right(obj))
        }
      ),
      suite("MergeStrategy.Custom")(
        test("Custom merge strategy allows user-defined logic") {
          val left           = Json.Object("a" -> Json.Number("1"), "b" -> Json.Number("2"))
          val right          = Json.Object("a" -> Json.Number("10"), "c" -> Json.Number("3"))
          val customStrategy = MergeStrategy.Custom { (_, l, r) =>
            (l, r) match {
              case (Json.Number(lv), Json.Number(rv)) => Json.Number((BigDecimal(lv) + BigDecimal(rv)).toString)
              case _                                  => r
            }
          }
          val result = left.merge(right, customStrategy)
          assertTrue(
            result.get("a").any == Right(Json.Number("11")),
            result.get("b").any == Right(Json.Number("2")),
            result.get("c").any == Right(Json.Number("3"))
          )
        },
        test("Custom merge strategy receives correct path") {
          var capturedPaths  = List.empty[String]
          val left           = Json.Object("outer" -> Json.Object("inner" -> Json.Number("1")))
          val right          = Json.Object("outer" -> Json.Object("inner" -> Json.Number("2")))
          val customStrategy = MergeStrategy.Custom { (path, _, r) =>
            capturedPaths = capturedPaths :+ path.toString
            r
          }
          left.merge(right, customStrategy)
          assertTrue(capturedPaths.exists(_.contains("inner")))
        },
        test("Custom merge strategy falls back to user function for non-objects") {
          val left: Json     = Json.Array(Json.Number("1"))
          val right: Json    = Json.Array(Json.Number("2"))
          val customStrategy = MergeStrategy.Custom(
            f = { (_, l, r) =>
              (l, r) match {
                case (Json.Array(lv), Json.Array(rv)) => Json.Array((lv ++ rv): _*)
                case _                                => r
              }
            },
            r = (_, _) => false
          )
          val result = left.merge(right, customStrategy)
          assertTrue(result == Json.Array(Json.Number("1"), Json.Number("2")))
        },
        test("Custom merge strategy with default recursion behaves like Auto for arrays") {
          val left: Json     = Json.Array(Json.Number("1"))
          val right: Json    = Json.Array(Json.Number("2"))
          val customStrategy = MergeStrategy.Custom((_, _, r) => r)
          val result         = left.merge(right, customStrategy)
          assertTrue(result == Json.Array(Json.Number("2")))
        }
      )
    )
  )
}
