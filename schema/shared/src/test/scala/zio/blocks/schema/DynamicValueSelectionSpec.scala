package zio.blocks.schema

import zio.blocks.chunk.Chunk
import zio.test._

object DynamicValueSelectionSpec extends SchemaBaseSpec {

  // Test helpers
  val stringVal: DynamicValue    = DynamicValue.string("hello")
  val intVal: DynamicValue       = DynamicValue.int(42)
  val boolVal: DynamicValue      = DynamicValue.boolean(true)
  val nullVal: DynamicValue      = DynamicValue.Null
  val recordVal: DynamicValue    = DynamicValue.Record("name" -> stringVal, "age" -> intVal, "active" -> boolVal)
  val seqVal: DynamicValue       = DynamicValue.Sequence(stringVal, intVal, nullVal)
  val mapVal: DynamicValue       = DynamicValue.Map(stringVal -> intVal, intVal -> boolVal)
  val variantVal: DynamicValue   = DynamicValue.Variant("Some", stringVal)
  val nestedRecord: DynamicValue = DynamicValue.Record(
    "user"  -> recordVal,
    "items" -> seqVal,
    "meta"  -> mapVal
  )

  def spec: Spec[TestEnvironment, Any] = suite("DynamicValueSelectionSpec")(
    suite("Basic operations")(
      test("isSuccess returns true for success") {
        val sel = DynamicValueSelection.succeed(stringVal)
        assertTrue(sel.isSuccess)
      },
      test("isSuccess returns false for failure") {
        val sel = DynamicValueSelection.fail(SchemaError("error"))
        assertTrue(!sel.isSuccess)
      },
      test("isFailure returns true for failure") {
        val sel = DynamicValueSelection.fail(SchemaError("error"))
        assertTrue(sel.isFailure)
      },
      test("isFailure returns false for success") {
        val sel = DynamicValueSelection.succeed(stringVal)
        assertTrue(!sel.isFailure)
      },
      test("error returns Some for failure") {
        val err = SchemaError("test error")
        val sel = DynamicValueSelection.fail(err)
        assertTrue(sel.error == Some(err))
      },
      test("error returns None for success") {
        val sel = DynamicValueSelection.succeed(stringVal)
        assertTrue(sel.error.isEmpty)
      },
      test("values returns Some for success") {
        val sel = DynamicValueSelection.succeed(stringVal)
        assertTrue(sel.values == Some(Chunk(stringVal)))
      },
      test("values returns None for failure") {
        val sel = DynamicValueSelection.fail(SchemaError("error"))
        assertTrue(sel.values.isEmpty)
      },
      test("toChunk returns values for success") {
        val sel = DynamicValueSelection.succeedMany(Chunk(stringVal, intVal))
        assertTrue(sel.toChunk == Chunk(stringVal, intVal))
      },
      test("toChunk returns empty for failure") {
        val sel = DynamicValueSelection.fail(SchemaError("error"))
        assertTrue(sel.toChunk.isEmpty)
      },
      test("one succeeds with single value") {
        val sel = DynamicValueSelection.succeed(stringVal)
        assertTrue(sel.one == Right(stringVal))
      },
      test("one fails with empty selection") {
        val sel = DynamicValueSelection.empty
        assertTrue(sel.one.isLeft)
      },
      test("one fails with multiple values") {
        val sel = DynamicValueSelection.succeedMany(Chunk(stringVal, intVal))
        assertTrue(sel.one.isLeft)
      },
      test("isEmpty returns true for empty selection") {
        val sel = DynamicValueSelection.empty
        assertTrue(sel.isEmpty)
      },
      test("isEmpty returns true for failure") {
        val sel = DynamicValueSelection.fail(SchemaError("error"))
        assertTrue(sel.isEmpty)
      },
      test("isEmpty returns false for non-empty selection") {
        val sel = DynamicValueSelection.succeed(stringVal)
        assertTrue(!sel.isEmpty)
      },
      test("nonEmpty returns true for non-empty selection") {
        val sel = DynamicValueSelection.succeed(stringVal)
        assertTrue(sel.nonEmpty)
      },
      test("nonEmpty returns false for empty selection") {
        val sel = DynamicValueSelection.empty
        assertTrue(!sel.nonEmpty)
      },
      test("size returns count for success") {
        val sel = DynamicValueSelection.succeedMany(Chunk(stringVal, intVal, boolVal))
        assertTrue(sel.size == 3)
      },
      test("size returns 0 for failure") {
        val sel = DynamicValueSelection.fail(SchemaError("error"))
        assertTrue(sel.size == 0)
      },
      test("any succeeds with at least one value") {
        val sel = DynamicValueSelection.succeedMany(Chunk(stringVal, intVal))
        assertTrue(sel.any == Right(stringVal))
      },
      test("any fails with empty selection") {
        val sel = DynamicValueSelection.empty
        assertTrue(sel.any.isLeft)
      },
      test("all returns single value directly") {
        val sel = DynamicValueSelection.succeed(stringVal)
        assertTrue(sel.all == Right(stringVal))
      },
      test("all wraps multiple values in Sequence") {
        val sel = DynamicValueSelection.succeedMany(Chunk(stringVal, intVal))
        assertTrue(sel.all == Right(DynamicValue.Sequence(stringVal, intVal)))
      },
      test("all fails with empty selection") {
        val sel = DynamicValueSelection.empty
        assertTrue(sel.all.isLeft)
      },
      test("toSequence wraps values") {
        val sel = DynamicValueSelection.succeedMany(Chunk(stringVal, intVal))
        assertTrue(sel.toSequence == Right(DynamicValue.Sequence(stringVal, intVal)))
      },
      test("oneUnsafe returns value for single selection") {
        val sel = DynamicValueSelection.succeed(stringVal)
        assertTrue(sel.oneUnsafe == stringVal)
      },
      test("oneUnsafe throws for empty selection") {
        val sel    = DynamicValueSelection.empty
        val result = try {
          sel.oneUnsafe
          false
        } catch {
          case _: SchemaError => true
        }
        assertTrue(result)
      },
      test("anyUnsafe returns first value") {
        val sel = DynamicValueSelection.succeedMany(Chunk(stringVal, intVal))
        assertTrue(sel.anyUnsafe == stringVal)
      },
      test("anyUnsafe throws for empty selection") {
        val sel    = DynamicValueSelection.empty
        val result = try {
          sel.anyUnsafe
          false
        } catch {
          case _: SchemaError => true
        }
        assertTrue(result)
      }
    ),
    suite("Type filtering")(
      test("primitives keeps only primitives") {
        val sel = DynamicValueSelection.succeedMany(Chunk(stringVal, recordVal, intVal, nullVal))
        assertTrue(sel.primitives.toChunk == Chunk(stringVal, intVal))
      },
      test("records keeps only records") {
        val sel = DynamicValueSelection.succeedMany(Chunk(stringVal, recordVal, DynamicValue.Record.empty))
        assertTrue(sel.records.toChunk == Chunk(recordVal, DynamicValue.Record.empty))
      },
      test("variants keeps only variants") {
        val sel = DynamicValueSelection.succeedMany(Chunk(stringVal, variantVal))
        assertTrue(sel.variants.toChunk == Chunk(variantVal))
      },
      test("sequences keeps only sequences") {
        val sel = DynamicValueSelection.succeedMany(Chunk(stringVal, seqVal))
        assertTrue(sel.sequences.toChunk == Chunk(seqVal))
      },
      test("maps keeps only maps") {
        val sel = DynamicValueSelection.succeedMany(Chunk(stringVal, mapVal))
        assertTrue(sel.maps.toChunk == Chunk(mapVal))
      },
      test("nulls keeps only nulls") {
        val sel = DynamicValueSelection.succeedMany(Chunk(stringVal, nullVal, intVal))
        assertTrue(sel.nulls.toChunk == Chunk(nullVal))
      },
      test("filter by type works") {
        val sel = DynamicValueSelection.succeedMany(Chunk(stringVal, recordVal, intVal))
        assertTrue(sel.filter(DynamicValueType.Primitive).toChunk == Chunk(stringVal, intVal))
      }
    ),
    suite("Navigation")(
      test("get(fieldName) navigates into record") {
        val sel = recordVal.select.get("name")
        assertTrue(sel.one == Right(stringVal))
      },
      test("get(fieldName) fails for non-record") {
        val sel = stringVal.select.get("field")
        assertTrue(sel.isFailure)
      },
      test("apply(index) navigates into sequence") {
        val sel = seqVal.select.apply(1)
        assertTrue(sel.one == Right(intVal))
      },
      test("apply(index) fails for out of bounds") {
        val sel = seqVal.select.apply(10)
        assertTrue(sel.isFailure)
      },
      test("apply(fieldName) navigates into record") {
        val sel = recordVal.select.apply("age")
        assertTrue(sel.one == Right(intVal))
      },
      test("get(key: DynamicValue) navigates into map") {
        val sel = mapVal.select.get(stringVal)
        assertTrue(sel.one == Right(intVal))
      },
      test("get(key) fails for missing key") {
        val sel = mapVal.select.get(boolVal)
        assertTrue(sel.isFailure)
      },
      test("get(path) navigates with optic") {
        val path = DynamicOptic.root.field("user").field("name")
        val sel  = nestedRecord.select.get(path)
        assertTrue(sel.one == Right(stringVal))
      },
      test("apply(path) navigates with optic") {
        val path = DynamicOptic.root.field("items").at(0)
        val sel  = nestedRecord.select.apply(path)
        assertTrue(sel.one == Right(stringVal))
      },
      test("getCase navigates into matching variant") {
        val sel = variantVal.select.getCase("Some")
        assertTrue(sel.one == Right(stringVal))
      },
      test("getCase fails for non-matching case") {
        val sel = variantVal.select.getCase("None")
        assertTrue(sel.isFailure)
      }
    ),
    suite("Combinators")(
      test("map transforms values") {
        val sel    = DynamicValueSelection.succeed(intVal)
        val mapped = sel.map {
          case DynamicValue.Primitive(PrimitiveValue.Int(n)) =>
            DynamicValue.int(n * 2)
          case other => other
        }
        assertTrue(mapped.one == Right(DynamicValue.int(84)))
      },
      test("flatMap chains selections") {
        val sel    = recordVal.select
        val result = sel.flatMap { dv =>
          dv.get("name")
        }
        assertTrue(result.one == Right(stringVal))
      },
      test("filter keeps matching values") {
        val sel      = DynamicValueSelection.succeedMany(Chunk(stringVal, intVal, boolVal))
        val filtered = sel.filter {
          case _: DynamicValue.Primitive => true
          case _                         => false
        }
        assertTrue(filtered.size == 3)
      },
      test("filter removes non-matching values") {
        val sel      = DynamicValueSelection.succeedMany(Chunk(stringVal, recordVal, intVal))
        val filtered = sel.filter {
          case _: DynamicValue.Record => true
          case _                      => false
        }
        assertTrue(filtered.size == 1)
      },
      test("collect gathers matching values") {
        val sel       = DynamicValueSelection.succeedMany(Chunk(stringVal, intVal, boolVal))
        val collected = sel.collect { case DynamicValue.Primitive(PrimitiveValue.Int(n)) =>
          n
        }
        assertTrue(collected == Right(Chunk(42)))
      },
      test("orElse returns first on success") {
        val sel1 = DynamicValueSelection.succeed(stringVal)
        val sel2 = DynamicValueSelection.succeed(intVal)
        assertTrue(sel1.orElse(sel2).one == Right(stringVal))
      },
      test("orElse returns alternative on failure") {
        val sel1 = DynamicValueSelection.fail(SchemaError("error"))
        val sel2 = DynamicValueSelection.succeed(intVal)
        assertTrue(sel1.orElse(sel2).one == Right(intVal))
      },
      test("getOrElse returns values on success") {
        val sel = DynamicValueSelection.succeed(stringVal)
        assertTrue(sel.getOrElse(Chunk(intVal)) == Chunk(stringVal))
      },
      test("getOrElse returns default on failure") {
        val sel = DynamicValueSelection.fail(SchemaError("error"))
        assertTrue(sel.getOrElse(Chunk(intVal)) == Chunk(intVal))
      },
      test("++ combines two selections") {
        val sel1 = DynamicValueSelection.succeed(stringVal)
        val sel2 = DynamicValueSelection.succeed(intVal)
        assertTrue((sel1 ++ sel2).toChunk == Chunk(stringVal, intVal))
      },
      test("++ with failure propagates error") {
        val sel1 = DynamicValueSelection.fail(SchemaError("error"))
        val sel2 = DynamicValueSelection.succeed(intVal)
        assertTrue((sel1 ++ sel2).isFailure)
      },
      test("++ with second failure propagates error") {
        val sel1 = DynamicValueSelection.succeed(intVal)
        val sel2 = DynamicValueSelection.fail(SchemaError("error"))
        assertTrue((sel1 ++ sel2).isFailure)
      },
      test("++ with both failures aggregates errors") {
        val sel1     = DynamicValueSelection.fail(SchemaError("first"))
        val sel2     = DynamicValueSelection.fail(SchemaError("second"))
        val combined = sel1 ++ sel2
        assertTrue(
          combined.isFailure,
          combined.error.exists(_.errors.length == 2),
          combined.error.exists(_.message.contains("first")),
          combined.error.exists(_.message.contains("second"))
        )
      }
    ),
    suite("Query methods")(
      test("query finds matching values recursively") {
        val sel = nestedRecord.select.query { dv =>
          dv == stringVal
        }
        assertTrue(sel.nonEmpty)
      },
      test("queryPath finds values at matching paths") {
        val sel = nestedRecord.select.queryPath { path =>
          path.nodes.nonEmpty && path.nodes.last == DynamicOptic.Node.Field("name")
        }
        assertTrue(sel.nonEmpty)
      },
      test("queryBoth combines path and value predicates") {
        val sel = nestedRecord.select.queryBoth { (path, dv) =>
          path.nodes.nonEmpty && dv.isInstanceOf[DynamicValue.Primitive]
        }
        assertTrue(sel.nonEmpty)
      }
    ),
    suite("Normalization")(
      test("sortFields sorts record fields") {
        val unsorted = DynamicValue.Record("z" -> intVal, "a" -> stringVal)
        val sel      = unsorted.select.sortFields
        val result   = sel.one.map(_.fields.map(_._1))
        assertTrue(result == Right(Chunk("a", "z")))
      },
      test("sortMapKeys sorts map keys") {
        val unsorted = DynamicValue.Map(DynamicValue.string("z") -> intVal, DynamicValue.string("a") -> stringVal)
        val sel      = unsorted.select.sortMapKeys
        val result   = sel.one.map(_.entries.map(_._1))
        assertTrue(result == Right(Chunk(DynamicValue.string("a"), DynamicValue.string("z"))))
      },
      test("dropNulls removes null values") {
        val withNulls = DynamicValue.Record("a" -> stringVal, "b" -> nullVal)
        val sel       = withNulls.select.dropNulls
        val result    = sel.one.map(_.fields.length)
        assertTrue(result == Right(1))
      },
      test("dropUnits removes unit values") {
        val withUnits = DynamicValue.Record("a" -> stringVal, "b" -> DynamicValue.unit)
        val sel       = withUnits.select.dropUnits
        val result    = sel.one.map(_.fields.length)
        assertTrue(result == Right(1))
      },
      test("dropEmpty removes empty containers") {
        val withEmpty = DynamicValue.Record("a" -> stringVal, "b" -> DynamicValue.Record.empty)
        val sel       = withEmpty.select.dropEmpty
        val result    = sel.one.map(_.fields.length)
        assertTrue(result == Right(1))
      },
      test("normalize applies all normalizations") {
        val messy =
          DynamicValue.Record("z" -> intVal, "a" -> nullVal, "m" -> DynamicValue.Record.empty, "b" -> stringVal)
        val sel    = messy.select.normalize
        val result = sel.one.map(_.fields.map(_._1))
        assertTrue(result == Right(Chunk("b", "z")))
      }
    ),
    suite("Path operations")(
      test("modify updates value at path") {
        val path   = DynamicOptic.root.field("age")
        val sel    = recordVal.select.modify(path)(_ => DynamicValue.int(100))
        val result = sel.one.flatMap(_.get("age").one)
        assertTrue(result == Right(DynamicValue.int(100)))
      },
      test("set replaces value at path") {
        val path   = DynamicOptic.root.field("name")
        val sel    = recordVal.select.set(path, DynamicValue.string("world"))
        val result = sel.one.flatMap(_.get("name").one)
        assertTrue(result == Right(DynamicValue.string("world")))
      },
      test("delete removes value at path") {
        val path   = DynamicOptic.root.field("active")
        val sel    = recordVal.select.delete(path)
        val result = sel.one.map(_.fields.length)
        assertTrue(result == Right(2))
      },
      test("insert adds value at new path") {
        val path   = DynamicOptic.root.field("new")
        val sel    = recordVal.select.insert(path, DynamicValue.string("added"))
        val result = sel.one.map(_.fields.length)
        assertTrue(result == Right(4))
      }
    ),
    suite("Transformation")(
      test("transformUp applies function bottom-up") {
        val sel = recordVal.select.transformUp { (_, dv) =>
          dv match {
            case DynamicValue.Primitive(PrimitiveValue.Int(n)) => DynamicValue.int(n + 1)
            case other                                         => other
          }
        }
        val result = sel.one.flatMap(_.get("age").one)
        assertTrue(result == Right(DynamicValue.int(43)))
      },
      test("transformDown applies function top-down") {
        val sel = recordVal.select.transformDown { (_, dv) =>
          dv match {
            case DynamicValue.Primitive(PrimitiveValue.Int(n)) => DynamicValue.int(n * 2)
            case other                                         => other
          }
        }
        val result = sel.one.flatMap(_.get("age").one)
        assertTrue(result == Right(DynamicValue.int(84)))
      },
      test("transformFields renames fields") {
        val sel    = recordVal.select.transformFields((_, name) => name.toUpperCase)
        val result = sel.one.map(_.fields.map(_._1))
        assertTrue(result == Right(Chunk("NAME", "AGE", "ACTIVE")))
      },
      test("transformMapKeys transforms keys") {
        val sel = mapVal.select.transformMapKeys { (_, key) =>
          key match {
            case DynamicValue.Primitive(PrimitiveValue.String(s)) => DynamicValue.string(s.toUpperCase)
            case other                                            => other
          }
        }
        val result = sel.one.map(_.entries.head._1)
        assertTrue(result == Right(DynamicValue.string("HELLO")))
      }
    ),
    suite("Pruning/Retention")(
      test("prune removes matching values") {
        val sel = recordVal.select.prune(_.isInstanceOf[DynamicValue.Primitive])
        assertTrue(sel.one.map(_.fields.length) == Right(0))
      },
      test("prunePath removes values at matching paths") {
        val sel = recordVal.select.prunePath(_.nodes.exists(_ == DynamicOptic.Node.Field("name")))
        assertTrue(sel.one.map(_.fields.length) == Right(2))
      },
      test("pruneBoth combines predicates") {
        val sel = recordVal.select.pruneBoth { (path, dv) =>
          path.nodes.exists(_ == DynamicOptic.Node.Field("age")) && dv.isInstanceOf[DynamicValue.Primitive]
        }
        assertTrue(sel.one.map(_.fields.length) == Right(2))
      },
      test("retain keeps matching values") {
        val sel = DynamicValue
          .Record("a" -> intVal, "b" -> stringVal, "c" -> boolVal)
          .select
          .retain {
            case DynamicValue.Primitive(PrimitiveValue.Int(_)) => true
            case _                                             => false
          }
        assertTrue(sel.one.map(_.fields.length) == Right(1))
      },
      test("retainPath keeps values at matching paths") {
        val sel = recordVal.select.retainPath { path =>
          path.nodes.isEmpty || path.nodes.exists(_ == DynamicOptic.Node.Field("name"))
        }
        assertTrue(sel.one.map(_.fields.length) == Right(1))
      },
      test("retainBoth combines predicates") {
        val sel = recordVal.select.retainBoth { (path, dv) =>
          path.nodes.isEmpty || (path.nodes.exists(_ == DynamicOptic.Node.Field("age")) && dv
            .isInstanceOf[DynamicValue.Primitive])
        }
        assertTrue(sel.one.map(_.fields.length) == Right(1))
      },
      test("project keeps only specified paths") {
        val sel = nestedRecord.select.project(
          DynamicOptic.root.field("user").field("name")
        )
        assertTrue(sel.nonEmpty)
      }
    ),
    suite("Merge")(
      test("merge combines two selections") {
        val sel1   = DynamicValue.Record("a" -> intVal).select
        val sel2   = DynamicValue.Record("b" -> stringVal).select
        val result = sel1.merge(sel2).one.map { dv =>
          dv.fields.length
        }
        assertTrue(result == Right(2))
      },
      test("merge with strategy") {
        val sel1   = DynamicValue.Record("a" -> DynamicValue.int(1)).select
        val sel2   = DynamicValue.Record("a" -> DynamicValue.int(2)).select
        val result = sel1.merge(sel2, DynamicValueMergeStrategy.KeepLeft).one
        assertTrue(result.map(_.get("a").one) == Right(Right(DynamicValue.int(1))))
      }
    ),
    suite("Fallible mutations")(
      test("modifyOrFail succeeds when path exists") {
        val path = DynamicOptic.root.field("age")
        val sel  = recordVal.select.modifyOrFail(path) { case DynamicValue.Primitive(PrimitiveValue.Int(n)) =>
          DynamicValue.int(n + 10)
        }
        assertTrue(sel.isSuccess)
      },
      test("modifyOrFail fails when path does not exist") {
        val path = DynamicOptic.root.field("missing")
        val sel  = recordVal.select.modifyOrFail(path) { case dv =>
          dv
        }
        assertTrue(sel.isFailure)
      },
      test("setOrFail succeeds when path exists") {
        val path = DynamicOptic.root.field("name")
        val sel  = recordVal.select.setOrFail(path, DynamicValue.string("updated"))
        assertTrue(sel.isSuccess)
      },
      test("setOrFail fails when path does not exist") {
        val path = DynamicOptic.root.field("missing")
        val sel  = recordVal.select.setOrFail(path, stringVal)
        assertTrue(sel.isFailure)
      },
      test("deleteOrFail succeeds when path exists") {
        val path = DynamicOptic.root.field("active")
        val sel  = recordVal.select.deleteOrFail(path)
        assertTrue(sel.isSuccess)
      },
      test("deleteOrFail fails when path does not exist") {
        val path = DynamicOptic.root.field("missing")
        val sel  = recordVal.select.deleteOrFail(path)
        assertTrue(sel.isFailure)
      },
      test("insertOrFail succeeds when path does not exist") {
        val path = DynamicOptic.root.field("newField")
        val sel  = recordVal.select.insertOrFail(path, stringVal)
        assertTrue(sel.isSuccess)
      },
      test("insertOrFail fails when path already exists") {
        val path = DynamicOptic.root.field("name")
        val sel  = recordVal.select.insertOrFail(path, stringVal)
        assertTrue(sel.isFailure)
      }
    ),
    suite("Type-directed extraction")(
      test("as succeeds with matching type") {
        val sel    = recordVal.select
        val result = sel.as(DynamicValueType.Record)
        assertTrue(result.isRight)
      },
      test("as fails with non-matching type") {
        val sel    = stringVal.select
        val result = sel.as(DynamicValueType.Record)
        assertTrue(result.isLeft)
      },
      test("asAll extracts all matching types") {
        val sel    = DynamicValueSelection.succeedMany(Chunk(stringVal, recordVal, intVal))
        val result = sel.asAll(DynamicValueType.Primitive)
        assertTrue(result.map(_.length) == Right(2))
      },
      test("unwrap extracts underlying value") {
        val sel    = recordVal.select
        val result = sel.unwrap(DynamicValueType.Record)
        assertTrue(result.map(_.length) == Right(3))
      },
      test("unwrap fails with wrong type") {
        val sel    = stringVal.select
        val result = sel.unwrap(DynamicValueType.Record)
        assertTrue(result.isLeft)
      },
      test("unwrapAll extracts all underlying values") {
        val sel    = DynamicValueSelection.succeedMany(Chunk(stringVal, intVal))
        val result = sel.unwrapAll(DynamicValueType.Primitive)
        assertTrue(result.map(_.length) == Right(2))
      },
      test("asPrimitive extracts primitive value") {
        val sel    = intVal.select
        val result = sel.asPrimitive(PrimitiveType.Int(Validation.None))
        assertTrue(result == Right(42))
      },
      test("asPrimitive fails with wrong primitive type") {
        val sel    = stringVal.select
        val result = sel.asPrimitive(PrimitiveType.Int(Validation.None))
        assertTrue(result.isLeft)
      },
      test("asPrimitiveAll extracts all primitive values") {
        val sel    = DynamicValueSelection.succeedMany(Chunk(intVal, DynamicValue.int(100)))
        val result = sel.asPrimitiveAll(PrimitiveType.Int(Validation.None))
        assertTrue(result == Right(Chunk(42, 100)))
      }
    )
  )
}
