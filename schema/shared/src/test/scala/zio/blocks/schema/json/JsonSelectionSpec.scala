package zio.blocks.schema.json

import zio.blocks.schema._
import zio.blocks.schema.SchemaError
import zio.test._

object JsonSelectionSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("JsonSelectionSpec")(
    suite("Unary methods")(
      suite("Normalization")(
        test("sortKeys sorts object keys in all selected values") {
          val json1     = Json.Object("z" -> Json.Number("1"), "a" -> Json.Number("2"))
          val json2     = Json.Object("b" -> Json.Number("3"), "a" -> Json.Number("4"))
          val selection = JsonSelection.succeedMany(Vector(json1, json2))
          val sorted    = selection.sortKeys
          val result    = sorted.either.map(_.map(_.print))
          assertTrue(
            result == Right(Vector("""{"a":2,"z":1}""", """{"a":4,"b":3}"""))
          )
        },
        test("dropNulls removes nulls from all selected values") {
          val json1     = Json.Object("a" -> Json.Number("1"), "b" -> Json.Null)
          val json2     = Json.Object("c" -> Json.Null, "d" -> Json.Number("2"))
          val selection = JsonSelection.succeedMany(Vector(json1, json2))
          val result    = selection.dropNulls.either
          assertTrue(
            result.map(_.map(_.fields.length)) == Right(Vector(1, 1))
          )
        },
        test("dropEmpty removes empty objects and arrays from all selected values") {
          val json1     = Json.Object("a" -> Json.Object.empty, "b" -> Json.Number("1"))
          val json2     = Json.Array(Json.Array.empty, Json.Number("2"))
          val selection = JsonSelection.succeedMany(Vector(json1, json2))
          val result    = selection.dropEmpty
          assertTrue(
            result.one.isLeft,
            result.size == 2
          )
        },
        test("normalize applies sortKeys, dropNulls, and dropEmpty") {
          val json = Json.Object(
            "z" -> Json.Number("1"),
            "a" -> Json.Null,
            "m" -> Json.Object.empty
          )
          val selection = JsonSelection.succeed(json)
          val result    = selection.normalize.one
          assertTrue(
            result.map(_.print) == Right("""{"z":1}""")
          )
        }
      ),
      suite("Path Operations")(
        test("modify applies function at path in all selected values") {
          val json1     = Json.Object("x" -> Json.Number("1"))
          val json2     = Json.Object("x" -> Json.Number("2"))
          val selection = JsonSelection.succeedMany(Vector(json1, json2))
          val path      = DynamicOptic.root.field("x")
          val result    = selection.modify(path) {
            case Json.Number(n) => Json.Number((BigDecimal(n) * 10).toString)
            case other          => other
          }
          assertTrue(
            result.either.map(_.map(_.get("x").one)) ==
              Right(Vector(Right(Json.Number("10")), Right(Json.Number("20"))))
          )
        },
        test("set replaces value at path in all selected values") {
          val json1     = Json.Object("a" -> Json.Number("1"))
          val json2     = Json.Object("a" -> Json.Number("2"))
          val selection = JsonSelection.succeedMany(Vector(json1, json2))
          val path      = DynamicOptic.root.field("a")
          val result    = selection.set(path, Json.Number("99"))
          assertTrue(
            result.either.map(_.map(_.get("a").one)) ==
              Right(Vector(Right(Json.Number("99")), Right(Json.Number("99"))))
          )
        },
        test("delete removes value at path in all selected values") {
          val json1     = Json.Object("a" -> Json.Number("1"), "b" -> Json.Number("2"))
          val json2     = Json.Object("a" -> Json.Number("3"), "c" -> Json.Number("4"))
          val selection = JsonSelection.succeedMany(Vector(json1, json2))
          val path      = DynamicOptic.root.field("a")
          val result    = selection.delete(path)
          assertTrue(
            result.either.map(_.map(_.get("a").isFailure)) == Right(Vector(true, true))
          )
        },
        test("insert adds value at path in all selected values") {
          val json1     = Json.Object("a" -> Json.Number("1"))
          val json2     = Json.Object("b" -> Json.Number("2"))
          val selection = JsonSelection.succeedMany(Vector(json1, json2))
          val path      = DynamicOptic.root.field("new")
          val result    = selection.insert(path, Json.String("inserted"))
          assertTrue(
            result.either.map(_.map(_.get("new").one)) ==
              Right(Vector(Right(Json.String("inserted")), Right(Json.String("inserted"))))
          )
        }
      ),
      suite("Transformation")(
        test("transformUp applies function bottom-up to all selected values") {
          val json      = Json.Object("a" -> Json.Number("1"))
          val selection = JsonSelection.succeed(json)
          val result    = selection.transformUp { (_, j) =>
            j match {
              case Json.Number(n) => Json.Number((BigDecimal(n) + 1).toString)
              case other          => other
            }
          }
          assertTrue(result.one.map(_.get("a").one) == Right(Right(Json.Number("2"))))
        },
        test("transformDown applies function top-down to all selected values") {
          val json      = Json.Object("a" -> Json.Number("1"))
          val selection = JsonSelection.succeed(json)
          val result    = selection.transformDown { (_, j) =>
            j match {
              case Json.Number(n) => Json.Number((BigDecimal(n) + 1).toString)
              case other          => other
            }
          }
          assertTrue(result.one.map(_.get("a").one) == Right(Right(Json.Number("2"))))
        },
        test("transformKeys applies function to all keys in all selected values") {
          val json      = Json.Object("a" -> Json.Number("1"), "b" -> Json.Number("2"))
          val selection = JsonSelection.succeed(json)
          val result    = selection.transformKeys((_, key) => key.toUpperCase)
          assertTrue(
            result.one.map(_.get("A").isSuccess) == Right(true),
            result.one.map(_.get("B").isSuccess) == Right(true)
          )
        }
      ),
      suite("Pruning")(
        test("prune removes matching values from all selected values") {
          val json1     = Json.Object("a" -> Json.Null, "b" -> Json.Number("1"))
          val json2     = Json.Array(Json.Null, Json.Number("2"))
          val selection = JsonSelection.succeedMany(Vector(json1, json2))
          val result    = selection.prune(_.is(JsonType.Null))
          assertTrue(
            result.either.map(v => (v(0).fields.length, v(1).elements.length)) == Right((1, 1))
          )
        },
        test("prunePath removes values at matching paths") {
          val json      = Json.Object("keep" -> Json.Number("1"), "drop" -> Json.Number("2"))
          val selection = JsonSelection.succeed(json)
          val result    = selection.prunePath { path =>
            path.nodes.exists {
              case f: DynamicOptic.Node.Field => f.name == "drop"
              case _                          => false
            }
          }
          assertTrue(
            result.one.map(_.get("keep").isSuccess) == Right(true),
            result.one.map(_.get("drop").isFailure) == Right(true)
          )
        },
        test("pruneBoth removes values matching both predicates") {
          val json = Json.Object(
            "nums" -> Json.Array(Json.Number("1"), Json.Number("100"))
          )
          val selection = JsonSelection.succeed(json)
          val result    = selection.pruneBoth { (path, value) =>
            path.nodes.exists {
              case _: DynamicOptic.Node.AtIndex => true
              case _                            => false
            } && value.unwrap(JsonType.Number).exists(_ > 50)
          }
          assertTrue(
            result.one.map(_.get("nums").as[Vector[Int]]) == Right(Right(Vector(1)))
          )
        }
      ),
      suite("Retention")(
        test("retain keeps only matching values in all selected values") {
          val json      = Json.Object("a" -> Json.Number("1"), "b" -> Json.String("hi"))
          val selection = JsonSelection.succeed(json)
          val result    = selection.retain(_.is(JsonType.Number))
          assertTrue(
            result.one.map(_.fields.length) == Right(1),
            result.one.map(_.get("a").isSuccess) == Right(true)
          )
        },
        test("retainPath keeps only values at matching paths") {
          val json      = Json.Object("keep" -> Json.Number("1"), "drop" -> Json.Number("2"))
          val selection = JsonSelection.succeed(json)
          val result    = selection.retainPath { path =>
            path.nodes.exists {
              case f: DynamicOptic.Node.Field => f.name == "keep"
              case _                          => false
            }
          }
          assertTrue(
            result.one.map(_.get("keep").isSuccess) == Right(true),
            result.one.map(_.get("drop").isFailure) == Right(true)
          )
        },
        test("retainBoth keeps only values matching both predicates") {
          val json = Json.Object(
            "nums" -> Json.Array(Json.Number("1"), Json.Number("100"))
          )
          val selection = JsonSelection.succeed(json)
          val result    = selection.retainBoth { (path, value) =>
            path.nodes.isEmpty ||
            path.nodes.exists {
              case f: DynamicOptic.Node.Field => f.name == "nums"
              case _                          => false
            } && !value.unwrap(JsonType.Number).exists(_ > 50)
          }
          assertTrue(
            result.one.map(_.get("nums").as[Vector[Int]]) == Right(Right(Vector(1)))
          )
        }
      ),
      suite("Projection")(
        test("project keeps only specified paths in all selected values") {
          val json = Json.Object(
            "a" -> Json.Number("1"),
            "b" -> Json.Number("2"),
            "c" -> Json.Number("3")
          )
          val selection = JsonSelection.succeed(json)
          val pathA     = DynamicOptic.root.field("a")
          val pathC     = DynamicOptic.root.field("c")
          val result    = selection.project(pathA, pathC)
          assertTrue(
            result.one.map(_.get("a").isSuccess) == Right(true),
            result.one.map(_.get("b").isFailure) == Right(true),
            result.one.map(_.get("c").isSuccess) == Right(true)
          )
        }
      )
    ),
    suite("merge (binary method)")(
      test("merge produces cartesian product (2 × 3 = 6 results)") {
        val left = JsonSelection.succeedMany(
          Vector(
            Json.Object("a" -> Json.Number("1")),
            Json.Object("a" -> Json.Number("2"))
          )
        )
        val right = JsonSelection.succeedMany(
          Vector(
            Json.Object("b" -> Json.Number("10")),
            Json.Object("b" -> Json.Number("20")),
            Json.Object("b" -> Json.Number("30"))
          )
        )
        val result = left.merge(right)
        assertTrue(
          result.size == 6,
          result.either.isRight
        )
      },
      test("merge combines values with default Auto strategy") {
        val left   = JsonSelection.succeed(Json.Object("a" -> Json.Number("1")))
        val right  = JsonSelection.succeed(Json.Object("b" -> Json.Number("2")))
        val result = left.merge(right)
        assertTrue(
          result.one.map(_.get("a").one) == Right(Right(Json.Number("1"))),
          result.one.map(_.get("b").one) == Right(Right(Json.Number("2")))
        )
      },
      test("merge with Replace strategy replaces left with right") {
        val left   = JsonSelection.succeed(Json.Object("a" -> Json.Number("1")))
        val right  = JsonSelection.succeed(Json.Object("b" -> Json.Number("2")))
        val result = left.merge(right, MergeStrategy.Replace)
        assertTrue(
          result.one.map(_.get("a").isFailure) == Right(true),
          result.one.map(_.get("b").one) == Right(Right(Json.Number("2")))
        )
      },
      test("merge propagates left error") {
        val left   = JsonSelection.fail(SchemaError("left error"))
        val right  = JsonSelection.succeed(Json.Object("b" -> Json.Number("2")))
        val result = left.merge(right)
        assertTrue(
          result.isFailure,
          result.error.map(_.message) == Some("left error")
        )
      },
      test("merge propagates right error") {
        val left   = JsonSelection.succeed(Json.Object("a" -> Json.Number("1")))
        val right  = JsonSelection.fail(SchemaError("right error"))
        val result = left.merge(right)
        assertTrue(
          result.isFailure,
          result.error.map(_.message) == Some("right error")
        )
      }
    ),
    suite("++ error aggregation")(
      test("++ with both failures aggregates errors") {
        val left     = JsonSelection.fail(SchemaError("first error"))
        val right    = JsonSelection.fail(SchemaError("second error"))
        val combined = left ++ right
        assertTrue(
          combined.isFailure,
          combined.error.exists(_.errors.length == 2),
          combined.error.exists(_.message.contains("first error")),
          combined.error.exists(_.message.contains("second error"))
        )
      }
    ),
    suite("Error handling")(
      test("one fails for empty selection") {
        val json      = Json.Object("a" -> Json.Number(1))
        val selection = json.get(DynamicOptic.root.field("nonexistent"))
        assertTrue(selection.one.isLeft)
      },
      test("one fails for multiple values") {
        val json      = Json.Array(Json.Number(1), Json.Number(2))
        val selection = json.get(DynamicOptic.root.elements)
        assertTrue(selection.one.isLeft)
      },
      test("any fails for empty selection") {
        val json      = Json.Object("a" -> Json.Number(1))
        val selection = json.get(DynamicOptic.root.field("nonexistent"))
        assertTrue(selection.any.isLeft)
      },
      test("flatMap propagates errors") {
        val json      = Json.Object("a" -> Json.Number(1))
        val selection = json.get(DynamicOptic.root.field("nonexistent"))
        val result    = selection.flatMap(j => JsonSelection.succeed(j))
        assertTrue(result.isFailure)
      },
      test("combined failures with ++") {
        val json       = Json.Object("a" -> Json.Number(1))
        val selection1 = json.get(DynamicOptic.root.field("x"))
        val selection2 = json.get(DynamicOptic.root.field("y"))
        val combined   = selection1 ++ selection2
        assertTrue(combined.isEmpty)
      },
      test("oneUnsafe throws SchemaError") {
        val json      = Json.Object("a" -> Json.Number(1))
        val selection = json.get(DynamicOptic.root.field("nonexistent"))
        val thrown    = try {
          selection.oneUnsafe
          false
        } catch {
          case _: SchemaError => true
          case _: Throwable   => false
        }
        assertTrue(thrown)
      },
      test("anyUnsafe throws SchemaError") {
        val json      = Json.Object("a" -> Json.Number(1))
        val selection = json.get(DynamicOptic.root.field("nonexistent"))
        val thrown    = try {
          selection.anyUnsafe
          false
        } catch {
          case _: SchemaError => true
          case _: Throwable   => false
        }
        assertTrue(thrown)
      }
    ),
    suite("Fallible mutation methods")(
      test("modifyOrFail succeeds when path exists and partial function is defined") {
        val json      = Json.Object("a" -> Json.Number("1"))
        val selection = JsonSelection.succeed(json)
        val path      = DynamicOptic.root.field("a")
        val result    = selection.modifyOrFail(path) { case Json.Number(n) =>
          Json.Number((BigDecimal(n) * 2).toString)
        }
        assertTrue(
          result.one.map(_.get("a").one) == Right(Right(Json.Number("2")))
        )
      },
      test("modifyOrFail fails when path does not exist") {
        val json      = Json.Object("a" -> Json.Number("1"))
        val selection = JsonSelection.succeed(json)
        val path      = DynamicOptic.root.field("nonexistent")
        val result    = selection.modifyOrFail(path) { case j => j }
        assertTrue(result.isFailure)
      },
      test("modifyOrFail fails when partial function is not defined") {
        val json      = Json.Object("a" -> Json.String("hello"))
        val selection = JsonSelection.succeed(json)
        val path      = DynamicOptic.root.field("a")
        val result    = selection.modifyOrFail(path) { case Json.Number(n) => Json.Number(n) }
        assertTrue(result.isFailure)
      },
      test("setOrFail succeeds when path exists") {
        val json      = Json.Object("a" -> Json.Number("1"))
        val selection = JsonSelection.succeed(json)
        val path      = DynamicOptic.root.field("a")
        val result    = selection.setOrFail(path, Json.Number("99"))
        assertTrue(
          result.one.map(_.get("a").one) == Right(Right(Json.Number("99")))
        )
      },
      test("setOrFail fails when path does not exist") {
        val json      = Json.Object("a" -> Json.Number("1"))
        val selection = JsonSelection.succeed(json)
        val path      = DynamicOptic.root.field("nonexistent")
        val result    = selection.setOrFail(path, Json.Number("99"))
        assertTrue(result.isFailure)
      },
      test("deleteOrFail succeeds when path exists") {
        val json      = Json.Object("a" -> Json.Number("1"), "b" -> Json.Number("2"))
        val selection = JsonSelection.succeed(json)
        val path      = DynamicOptic.root.field("a")
        val result    = selection.deleteOrFail(path)
        assertTrue(
          result.one.map(_.get("a").isFailure) == Right(true),
          result.one.map(_.get("b").isSuccess) == Right(true)
        )
      },
      test("deleteOrFail fails when path does not exist") {
        val json      = Json.Object("a" -> Json.Number("1"))
        val selection = JsonSelection.succeed(json)
        val path      = DynamicOptic.root.field("nonexistent")
        val result    = selection.deleteOrFail(path)
        assertTrue(result.isFailure)
      },
      test("insertOrFail succeeds when path does not exist and parent exists") {
        val json      = Json.Object("a" -> Json.Number("1"))
        val selection = JsonSelection.succeed(json)
        val path      = DynamicOptic.root.field("b")
        val result    = selection.insertOrFail(path, Json.Number("2"))
        assertTrue(
          result.one.map(_.get("b").one) == Right(Right(Json.Number("2")))
        )
      },
      test("insertOrFail fails when path already exists") {
        val json      = Json.Object("a" -> Json.Number("1"))
        val selection = JsonSelection.succeed(json)
        val path      = DynamicOptic.root.field("a")
        val result    = selection.insertOrFail(path, Json.Number("2"))
        assertTrue(result.isFailure)
      },
      test("fallible methods can be chained fluently") {
        val json      = Json.Object("a" -> Json.Number("1"))
        val selection = JsonSelection.succeed(json)
        val pathA     = DynamicOptic.root.field("a")
        val pathB     = DynamicOptic.root.field("b")
        val result    = selection
          .setOrFail(pathA, Json.Number("10"))
          .insertOrFail(pathB, Json.Number("20"))
        assertTrue(
          result.one.map(_.get("a").one) == Right(Right(Json.Number("10"))),
          result.one.map(_.get("b").one) == Right(Right(Json.Number("20")))
        )
      }
    ),
    suite("Type-directed extraction")(
      test("as(jsonType) returns typed value when single value matches") {
        val selection = JsonSelection.succeed(Json.Object("a" -> Json.Number("1")))
        val result    = selection.as(JsonType.Object)
        assertTrue(result.isRight)
      },
      test("as(jsonType) fails when type does not match") {
        val selection = JsonSelection.succeed(Json.String("hello"))
        val result    = selection.as(JsonType.Object)
        assertTrue(result.isLeft)
      },
      test("as(jsonType) fails when selection has multiple values") {
        val selection = JsonSelection.succeedMany(
          Vector(
            Json.Object("a" -> Json.Number("1")),
            Json.Object("b" -> Json.Number("2"))
          )
        )
        val result = selection.as(JsonType.Object)
        assertTrue(result.isLeft)
      },
      test("as(jsonType) fails when selection is empty") {
        val selection = JsonSelection.empty
        val result    = selection.as(JsonType.Object)
        assertTrue(result.isLeft)
      },
      test("asAll(jsonType) returns all matching values, dropping non-matching") {
        val selection = JsonSelection.succeedMany(
          Vector(
            Json.String("hello"),
            Json.Number("42"),
            Json.String("world")
          )
        )
        val result = selection.asAll(JsonType.String)
        assertTrue(
          result.isRight,
          result.map(_.length) == Right(2)
        )
      },
      test("asAll(jsonType) for all JsonTypes") {
        val objects  = JsonSelection.succeed(Json.Object.empty).asAll(JsonType.Object)
        val arrays   = JsonSelection.succeed(Json.Array.empty).asAll(JsonType.Array)
        val strings  = JsonSelection.succeed(Json.String("hi")).asAll(JsonType.String)
        val numbers  = JsonSelection.succeed(Json.Number("42")).asAll(JsonType.Number)
        val booleans = JsonSelection.succeed(Json.Boolean(true)).asAll(JsonType.Boolean)
        val nulls    = JsonSelection.succeed(Json.Null).asAll(JsonType.Null)
        assertTrue(
          objects.map(_.length) == Right(1),
          arrays.map(_.length) == Right(1),
          strings.map(_.length) == Right(1),
          numbers.map(_.length) == Right(1),
          booleans.map(_.length) == Right(1),
          nulls.map(_.length) == Right(1)
        )
      },
      test("unwrap(jsonType) extracts underlying Scala value") {
        val strSelection                               = JsonSelection.succeed(Json.String("hello"))
        val numSelection                               = JsonSelection.succeed(Json.Number("42"))
        val boolSelection                              = JsonSelection.succeed(Json.Boolean(true))
        val nullSelection                              = JsonSelection.succeed(Json.Null)
        val strResult: Either[SchemaError, String]     = strSelection.unwrap(JsonType.String)
        val numResult: Either[SchemaError, BigDecimal] = numSelection.unwrap(JsonType.Number)
        val boolResult: Either[SchemaError, Boolean]   = boolSelection.unwrap(JsonType.Boolean)
        val nullResult: Either[SchemaError, Unit]      = nullSelection.unwrap(JsonType.Null)
        assertTrue(
          strResult == Right("hello"),
          numResult == Right(BigDecimal(42)),
          boolResult == Right(true),
          nullResult == Right(())
        )
      },
      test("unwrap(jsonType) fails when type does not match") {
        val selection = JsonSelection.succeed(Json.String("hello"))
        val result    = selection.unwrap(JsonType.Number)
        assertTrue(result.isLeft)
      },
      test("unwrap(jsonType) fails for unparseable Number") {
        val selection = JsonSelection.succeed(Json.Number("not-a-number"))
        val result    = selection.unwrap(JsonType.Number)
        assertTrue(result.isLeft)
      },
      test("unwrapAll(jsonType) extracts all matching values") {
        val selection = JsonSelection.succeedMany(
          Vector(
            Json.Number("1"),
            Json.String("skip"),
            Json.Number("2"),
            Json.Number("3")
          )
        )
        val result: Either[SchemaError, Vector[BigDecimal]] = selection.unwrapAll(JsonType.Number)
        assertTrue(result == Right(Vector(BigDecimal(1), BigDecimal(2), BigDecimal(3))))
      },
      test("unwrapAll(jsonType) for Object extracts Chunk[(String, Json)]") {
        val obj       = Json.Object("a" -> Json.Number("1"), "b" -> Json.Number("2"))
        val selection = JsonSelection.succeed(obj)
        val result    = selection.unwrapAll(JsonType.Object)
        assertTrue(
          result.isRight,
          result.map(_.length) == Right(1),
          result.map(_.head.length) == Right(2)
        )
      },
      test("unwrapAll(jsonType) for Array extracts Chunk[Json]") {
        val arr       = Json.Array(Json.Number("1"), Json.Number("2"))
        val selection = JsonSelection.succeed(arr)
        val result    = selection.unwrapAll(JsonType.Array)
        assertTrue(
          result.isRight,
          result.map(_.length) == Right(1),
          result.map(_.head.length) == Right(2)
        )
      },
      test("unwrapAll silently drops unparseable Numbers") {
        val selection = JsonSelection.succeedMany(
          Vector(
            Json.Number("1"),
            Json.Number("not-a-number"),
            Json.Number("2")
          )
        )
        val result = selection.unwrapAll(JsonType.Number)
        assertTrue(result == Right(Vector(BigDecimal(1), BigDecimal(2))))
      }
    )
  )
}
