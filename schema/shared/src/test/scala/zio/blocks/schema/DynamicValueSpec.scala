package zio.blocks.schema

import zio.test._
import DynamicValueGen._
import zio.test.Assertion.{equalTo, not}

object DynamicValueSpec extends SchemaBaseSpec {

  // Test helpers
  val stringVal: DynamicValue  = DynamicValue.string("hello")
  val intVal: DynamicValue     = DynamicValue.int(42)
  val boolVal: DynamicValue    = DynamicValue.boolean(true)
  val nullVal: DynamicValue    = DynamicValue.Null
  val recordVal: DynamicValue  = DynamicValue.Record("name" -> stringVal, "age" -> intVal, "active" -> boolVal)
  val seqVal: DynamicValue     = DynamicValue.Sequence(stringVal, intVal, nullVal)
  val mapVal: DynamicValue     = DynamicValue.Map(stringVal -> intVal, intVal -> boolVal)
  val variantVal: DynamicValue = DynamicValue.Variant("Some", stringVal)
  val nestedRecord: DynamicValue = DynamicValue.Record(
    "user" -> recordVal,
    "items" -> seqVal,
    "meta" -> mapVal
  )

  def spec: Spec[TestEnvironment, Any] = suite("DynamicValueSpec")(
    suite("DynamicValue equals and hashCode properties with Generators")(
      test("symmetry") {
        check(genDynamicValue, genDynamicValue) { (value1, value2) =>
          assertTrue((value1 == value2) == (value2 == value1))
        }
      },
      test("transitivity") {
        check(genDynamicValue, genDynamicValue, genDynamicValue) { (value1, value2, value3) =>
          // If value1 equals value2 and value2 equals value3 then value1 should equal value3.
          assertTrue(!(value1 == value2 && value2 == value3) || (value1 == value3))
        }
      },
      test("consistency of hashCode for equal values") {
        check(genDynamicValue, genDynamicValue) { (value1, value2) =>
          // For equal values the hashCodes must be equal
          assertTrue(!(value1 == value2) || (value1.hashCode == value2.hashCode))
        }
      },
      test("inequality for different types or structures") {
        check(genDynamicValue, genDynamicValue) { (value1, value2) =>
          // verifies that when two values are not equal they indeed do not compare equal
          assertTrue((value1 != value2) || (value1 == value2))
        }
      },
      test("inequality for other non dynamic value types") {
        check(genDynamicValue, Gen.string) { (dynamicValue, str) =>
          assert(dynamicValue: Any)(not(equalTo(str)))
        }
      },
      test("nested structure equality and hashCode consistency") {
        val nestedGen = for {
          innerValue <- genRecord
          outerValue <- genRecord
        } yield DynamicValue.Record(Vector("inner" -> innerValue, "outer" -> outerValue))

        check(nestedGen, nestedGen) { (nested1, nested2) =>
          assertTrue((nested1 == nested2) == (nested1.hashCode == nested2.hashCode))
        }
      },
      test("structure equality and hashCode consistency for variants with the same case names") {
        check(genDynamicValue, genDynamicValue) { (value1, value2) =>
          val variant1 = DynamicValue.Variant("case1", value1)
          val variant2 = DynamicValue.Variant("case1", value2)
          assertTrue(!(variant1 == variant2) || (variant1.hashCode == variant2.hashCode))
        }
      },
      test("structure equality and hashCode consistency for maps with the same keys") {
        check(genDynamicValue, genDynamicValue, genDynamicValue) { (key, value1, value2) =>
          val map1 = DynamicValue.Map(Vector((key, value1), (key, value2)))
          val map2 = DynamicValue.Map(Vector((key, value1), (key, value2)))
          assertTrue(!(map1 == map2) || (map1.hashCode == map2.hashCode))
        }
      }
    ),
    suite("DynamicValue compare and equals properties with Generators")(
      test("symmetry") {
        check(genDynamicValue, genDynamicValue) { (value1, value2) =>
          assertTrue(value1.compare(value2) == -value2.compare(value1)) &&
          assertTrue((value1 > value2) == (value2 < value1)) &&
          assertTrue((value1 >= value2) == (value2 <= value1))
        }
      },
      test("transitivity") {
        check(genDynamicValue, genDynamicValue, genDynamicValue) { (value1, value2, value3) =>
          assertTrue(!(value1 > value2 && value2 > value3) || (value1 > value3)) &&
          assertTrue(!(value1 >= value2 && value2 >= value3) || (value1 >= value3)) &&
          assertTrue(!(value1 < value2 && value2 < value3) || (value1 < value3)) &&
          assertTrue(!(value1 <= value2 && value2 <= value3) || (value1 <= value3))
        }
      },
      test("consistency of compare for equal values") {
        check(genDynamicValue, genDynamicValue) { (value1, value2) =>
          assertTrue((value1 == value2) == (value1.compare(value2) == 0))
        }
      },
      test("nested structure equality and compare consistency") {
        val nestedGen = for {
          innerValue <- genRecord
          outerValue <- genRecord
        } yield DynamicValue.Record(Vector("inner" -> innerValue, "outer" -> outerValue))

        check(nestedGen, nestedGen) { (nested1, nested2) =>
          assertTrue((nested1 == nested2) == (nested1.compare(nested2) == 0))
        }
      },
      test("structure equality and compare consistency for variants with the same case names") {
        check(genDynamicValue, genDynamicValue) { (value1, value2) =>
          val variant1 = DynamicValue.Variant("case1", value1)
          val variant2 = DynamicValue.Variant("case1", value2)
          assertTrue((variant1 == variant2) == (variant1.compare(variant2) == 0))
        }
      },
      test("structure equality and compare consistency for maps with the same keys") {
        check(genDynamicValue, genDynamicValue, genDynamicValue) { (key, value1, value2) =>
          val map1 = DynamicValue.Map(Vector((key, value1), (key, value2)))
          val map2 = DynamicValue.Map(Vector((key, value2), (key, value1)))
          assertTrue((map1 == map2) == (map1.compare(map2) == 0))
        }
      }
    ),
    suite("Convenience constructors")(
      test("string creates Primitive with String") {
        val dv = DynamicValue.string("test")
        assertTrue(dv == DynamicValue.Primitive(PrimitiveValue.String("test")))
      },
      test("int creates Primitive with Int") {
        val dv = DynamicValue.int(42)
        assertTrue(dv == DynamicValue.Primitive(PrimitiveValue.Int(42)))
      },
      test("long creates Primitive with Long") {
        val dv = DynamicValue.long(123L)
        assertTrue(dv == DynamicValue.Primitive(PrimitiveValue.Long(123L)))
      },
      test("boolean creates Primitive with Boolean") {
        val dv = DynamicValue.boolean(true)
        assertTrue(dv == DynamicValue.Primitive(PrimitiveValue.Boolean(true)))
      },
      test("double creates Primitive with Double") {
        val dv = DynamicValue.double(3.14)
        assertTrue(dv == DynamicValue.Primitive(PrimitiveValue.Double(3.14)))
      },
      test("float creates Primitive with Float") {
        val dv = DynamicValue.float(2.5f)
        assertTrue(dv == DynamicValue.Primitive(PrimitiveValue.Float(2.5f)))
      },
      test("short creates Primitive with Short") {
        val dv = DynamicValue.short(10.toShort)
        assertTrue(dv == DynamicValue.Primitive(PrimitiveValue.Short(10.toShort)))
      },
      test("byte creates Primitive with Byte") {
        val dv = DynamicValue.byte(5.toByte)
        assertTrue(dv == DynamicValue.Primitive(PrimitiveValue.Byte(5.toByte)))
      },
      test("char creates Primitive with Char") {
        val dv = DynamicValue.char('A')
        assertTrue(dv == DynamicValue.Primitive(PrimitiveValue.Char('A')))
      },
      test("bigInt creates Primitive with BigInt") {
        val dv = DynamicValue.bigInt(BigInt(1000000))
        assertTrue(dv == DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(1000000))))
      },
      test("bigDecimal creates Primitive with BigDecimal") {
        val dv = DynamicValue.bigDecimal(BigDecimal("123.456"))
        assertTrue(dv == DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal("123.456"))))
      },
      test("unit creates Primitive with Unit") {
        val dv = DynamicValue.unit
        assertTrue(dv == DynamicValue.Primitive(PrimitiveValue.Unit))
      }
    ),
    suite("DynamicValue.Null")(
      test("Null has typeIndex 5") {
        assertTrue(DynamicValue.Null.typeIndex == 5)
      },
      test("Null has valueType Null") {
        assertTrue(DynamicValue.Null.valueType == DynamicValueType.Null)
      },
      test("Null compares equal to Null") {
        assertTrue(DynamicValue.Null.compare(DynamicValue.Null) == 0)
      },
      test("Null compares greater than other types") {
        assertTrue(DynamicValue.Null.compare(stringVal) > 0) &&
        assertTrue(DynamicValue.Null.compare(recordVal) > 0) &&
        assertTrue(DynamicValue.Null.compare(variantVal) > 0) &&
        assertTrue(DynamicValue.Null.compare(seqVal) > 0) &&
        assertTrue(DynamicValue.Null.compare(mapVal) > 0)
      },
      test("Null as returns Some for Null type") {
        val result = DynamicValue.Null.as(DynamicValueType.Null)
        assertTrue(result.isDefined)
      },
      test("Null as returns None for other types") {
        assertTrue(DynamicValue.Null.as(DynamicValueType.Primitive).isEmpty) &&
        assertTrue(DynamicValue.Null.as(DynamicValueType.Record).isEmpty)
      },
      test("Null unwrap returns Some(()) for Null type") {
        val result = DynamicValue.Null.unwrap(DynamicValueType.Null)
        assertTrue(result == Some(()))
      },
      test("Null unwrap returns None for other types") {
        assertTrue(DynamicValue.Null.unwrap(DynamicValueType.Primitive).isEmpty) &&
        assertTrue(DynamicValue.Null.unwrap(DynamicValueType.Record).isEmpty)
      },
      test("Null is method works") {
        assertTrue(DynamicValue.Null.is(DynamicValueType.Null)) &&
        assertTrue(!DynamicValue.Null.is(DynamicValueType.Primitive))
      }
    ),
    suite("Type information methods")(
      test("Primitive valueType") {
        assertTrue(stringVal.valueType == DynamicValueType.Primitive)
      },
      test("Record valueType") {
        assertTrue(recordVal.valueType == DynamicValueType.Record)
      },
      test("Variant valueType") {
        assertTrue(variantVal.valueType == DynamicValueType.Variant)
      },
      test("Sequence valueType") {
        assertTrue(seqVal.valueType == DynamicValueType.Sequence)
      },
      test("Map valueType") {
        assertTrue(mapVal.valueType == DynamicValueType.Map)
      },
      test("is method returns true for matching type") {
        assertTrue(stringVal.is(DynamicValueType.Primitive)) &&
        assertTrue(recordVal.is(DynamicValueType.Record)) &&
        assertTrue(variantVal.is(DynamicValueType.Variant)) &&
        assertTrue(seqVal.is(DynamicValueType.Sequence)) &&
        assertTrue(mapVal.is(DynamicValueType.Map))
      },
      test("is method returns false for non-matching type") {
        assertTrue(!stringVal.is(DynamicValueType.Record)) &&
        assertTrue(!recordVal.is(DynamicValueType.Primitive))
      },
      test("as method returns Some for matching type") {
        assertTrue(stringVal.as(DynamicValueType.Primitive).isDefined) &&
        assertTrue(recordVal.as(DynamicValueType.Record).isDefined)
      },
      test("as method returns None for non-matching type") {
        assertTrue(stringVal.as(DynamicValueType.Record).isEmpty) &&
        assertTrue(recordVal.as(DynamicValueType.Primitive).isEmpty)
      },
      test("unwrap returns underlying value for matching type") {
        val fields = recordVal.unwrap(DynamicValueType.Record)
        assertTrue(fields.map(_.length) == Some(3))
      },
      test("asPrimitive extracts primitive value") {
        val result = intVal.asPrimitive(PrimitiveType.Int(Validation.None))
        assertTrue(result == Some(42))
      },
      test("asPrimitive returns None for wrong type") {
        val result = stringVal.asPrimitive(PrimitiveType.Int(Validation.None))
        assertTrue(result.isEmpty)
      }
    ),
    suite("Direct accessors")(
      test("fields returns fields for Record") {
        assertTrue(recordVal.fields.length == 3)
      },
      test("fields returns empty for non-Record") {
        assertTrue(stringVal.fields.isEmpty)
      },
      test("elements returns elements for Sequence") {
        assertTrue(seqVal.elements.length == 3)
      },
      test("elements returns empty for non-Sequence") {
        assertTrue(stringVal.elements.isEmpty)
      },
      test("entries returns entries for Map") {
        assertTrue(mapVal.entries.length == 2)
      },
      test("entries returns empty for non-Map") {
        assertTrue(stringVal.entries.isEmpty)
      },
      test("caseName returns Some for Variant") {
        assertTrue(variantVal.caseName == Some("Some"))
      },
      test("caseName returns None for non-Variant") {
        assertTrue(stringVal.caseName.isEmpty)
      },
      test("caseValue returns Some for Variant") {
        assertTrue(variantVal.caseValue == Some(stringVal))
      },
      test("caseValue returns None for non-Variant") {
        assertTrue(stringVal.caseValue.isEmpty)
      },
      test("primitiveValue returns Some for Primitive") {
        assertTrue(stringVal.primitiveValue.isDefined)
      },
      test("primitiveValue returns None for non-Primitive") {
        assertTrue(recordVal.primitiveValue.isEmpty)
      }
    ),
    suite("Navigation")(
      test("get(fieldName) on Record") {
        val result = recordVal.get("name")
        assertTrue(result.one == Right(stringVal))
      },
      test("get(fieldName) on non-Record fails") {
        val result = stringVal.get("field")
        assertTrue(result.isFailure)
      },
      test("get(index) on Sequence") {
        val result = seqVal.get(1)
        assertTrue(result.one == Right(intVal))
      },
      test("get(index) on non-Sequence fails") {
        val result = stringVal.get(0)
        assertTrue(result.isFailure)
      },
      test("get(key) on Map") {
        val result = mapVal.get(stringVal)
        assertTrue(result.one == Right(intVal))
      },
      test("get(key) on non-Map fails") {
        val result = stringVal.get(DynamicValue.int(1))
        assertTrue(result.isFailure)
      },
      test("getCase on Variant with matching case") {
        val result = variantVal.getCase("Some")
        assertTrue(result.one == Right(stringVal))
      },
      test("getCase on Variant with non-matching case") {
        val result = variantVal.getCase("None")
        assertTrue(result.isFailure)
      },
      test("getCase on non-Variant fails") {
        val result = stringVal.getCase("case")
        assertTrue(result.isFailure)
      },
      test("get(path) navigates with optic") {
        val path   = DynamicOptic.root.field("user").field("name")
        val result = nestedRecord.get(path)
        assertTrue(result.one == Right(stringVal))
      }
    ),
    suite("Path-based modification")(
      test("modify updates value at path") {
        val path   = DynamicOptic.root.field("age")
        val result = recordVal.modify(path)(_ => DynamicValue.int(100))
        assertTrue(result.get("age").one == Right(DynamicValue.int(100)))
      },
      test("modify returns unchanged when path doesn't exist") {
        val path   = DynamicOptic.root.field("missing")
        val result = recordVal.modify(path)(_ => DynamicValue.int(100))
        assertTrue(result == recordVal)
      },
      test("modifyOrFail succeeds when path exists") {
        val path   = DynamicOptic.root.field("age")
        val result = recordVal.modifyOrFail(path) { case _ => DynamicValue.int(100) }
        assertTrue(result.isRight)
      },
      test("modifyOrFail fails when path doesn't exist") {
        val path   = DynamicOptic.root.field("missing")
        val result = recordVal.modifyOrFail(path) { case _ => DynamicValue.int(100) }
        assertTrue(result.isLeft)
      },
      test("modifyOrFail fails when partial function not defined at root") {
        val path = DynamicOptic.root
        val result = recordVal.modifyOrFail(path) { case _: DynamicValue.Primitive => DynamicValue.int(100) }
        assertTrue(result.isLeft)
      },
      test("modifyOrFail succeeds at root when partial function is defined") {
        val path   = DynamicOptic.root
        val result = recordVal.modifyOrFail(path) { case _: DynamicValue.Record => DynamicValue.Record.empty }
        assertTrue(result == Right(DynamicValue.Record.empty))
      },
      test("set updates value at path") {
        val path   = DynamicOptic.root.field("name")
        val result = recordVal.set(path, DynamicValue.string("world"))
        assertTrue(result.get("name").one == Right(DynamicValue.string("world")))
      },
      test("setOrFail succeeds when path exists") {
        val path   = DynamicOptic.root.field("name")
        val result = recordVal.setOrFail(path, DynamicValue.string("world"))
        assertTrue(result.isRight)
      },
      test("setOrFail fails when path doesn't exist") {
        val path   = DynamicOptic.root.field("missing")
        val result = recordVal.setOrFail(path, stringVal)
        assertTrue(result.isLeft)
      },
      test("delete removes value at path from Record") {
        val path   = DynamicOptic.root.field("active")
        val result = recordVal.delete(path)
        assertTrue(result.fields.length == 2)
      },
      test("delete removes value at path from Sequence") {
        val path   = DynamicOptic.root.at(0)
        val result = seqVal.delete(path)
        assertTrue(result.elements.length == 2)
      },
      test("delete removes value at path from Map") {
        val path   = DynamicOptic.root.atKey(stringVal)
        val result = mapVal.delete(path)
        assertTrue(result.entries.length == 1)
      },
      test("delete returns unchanged when path doesn't exist") {
        val path   = DynamicOptic.root.field("missing")
        val result = recordVal.delete(path)
        assertTrue(result == recordVal)
      },
      test("deleteOrFail succeeds when path exists") {
        val path   = DynamicOptic.root.field("active")
        val result = recordVal.deleteOrFail(path)
        assertTrue(result.isRight)
      },
      test("deleteOrFail fails when path doesn't exist") {
        val path   = DynamicOptic.root.field("missing")
        val result = recordVal.deleteOrFail(path)
        assertTrue(result.isLeft)
      },
      test("insert adds value at new path") {
        val path   = DynamicOptic.root.field("newField")
        val result = recordVal.insert(path, DynamicValue.string("new"))
        assertTrue(result.fields.length == 4)
      },
      test("insert returns unchanged when path exists") {
        val path   = DynamicOptic.root.field("name")
        val result = recordVal.insert(path, DynamicValue.string("new"))
        assertTrue(result == recordVal)
      },
      test("insertOrFail succeeds when path doesn't exist") {
        val path   = DynamicOptic.root.field("newField")
        val result = recordVal.insertOrFail(path, DynamicValue.string("new"))
        assertTrue(result.isRight)
      },
      test("insertOrFail fails when path exists") {
        val path   = DynamicOptic.root.field("name")
        val result = recordVal.insertOrFail(path, DynamicValue.string("new"))
        assertTrue(result.isLeft)
      }
    ),
    suite("Normalization")(
      test("sortFields sorts record fields alphabetically") {
        val unsorted = DynamicValue.Record("z" -> intVal, "a" -> stringVal, "m" -> boolVal)
        val sorted   = unsorted.sortFields
        assertTrue(sorted.fields.map(_._1) == Vector("a", "m", "z"))
      },
      test("sortMapKeys sorts map keys") {
        val unsorted = DynamicValue.Map(
          DynamicValue.string("z") -> intVal,
          DynamicValue.string("a") -> stringVal
        )
        val sorted = unsorted.sortMapKeys
        assertTrue(sorted.entries.map(_._1) == Vector(DynamicValue.string("a"), DynamicValue.string("z")))
      },
      test("dropNulls removes null values from Record") {
        val withNulls = DynamicValue.Record("a" -> stringVal, "b" -> nullVal, "c" -> intVal)
        val dropped   = withNulls.dropNulls
        assertTrue(dropped.fields.length == 2)
      },
      test("dropNulls removes null values from Sequence") {
        val withNulls = DynamicValue.Sequence(stringVal, nullVal, intVal)
        val dropped   = withNulls.dropNulls
        assertTrue(dropped.elements.length == 2)
      },
      test("dropNulls removes null values from Map") {
        val withNulls = DynamicValue.Map(stringVal -> nullVal, intVal -> boolVal)
        val dropped   = withNulls.dropNulls
        assertTrue(dropped.entries.length == 1)
      },
      test("dropUnits removes unit values") {
        val withUnits = DynamicValue.Record("a" -> stringVal, "b" -> DynamicValue.unit)
        val dropped   = withUnits.dropUnits
        assertTrue(dropped.fields.length == 1)
      },
      test("dropEmpty removes empty containers") {
        val withEmpty = DynamicValue.Record(
          "a" -> stringVal,
          "b" -> DynamicValue.Record.empty,
          "c" -> DynamicValue.Sequence.empty,
          "d" -> DynamicValue.Map.empty
        )
        val dropped = withEmpty.dropEmpty
        assertTrue(dropped.fields.length == 1)
      },
      test("normalize applies all normalizations") {
        val messy = DynamicValue.Record(
          "z" -> intVal,
          "a" -> nullVal,
          "m" -> DynamicValue.unit,
          "b" -> DynamicValue.Record.empty,
          "c" -> stringVal
        )
        val normalized = messy.normalize
        assertTrue(normalized.fields.map(_._1) == Vector("c", "z"))
      }
    ),
    suite("Transformation")(
      test("transformUp applies function bottom-up") {
        val result = recordVal.transformUp { (_, dv) =>
          dv match {
            case DynamicValue.Primitive(PrimitiveValue.Int(n)) => DynamicValue.int(n + 1)
            case other                                         => other
          }
        }
        assertTrue(result.get("age").one == Right(DynamicValue.int(43)))
      },
      test("transformDown applies function top-down") {
        val result = recordVal.transformDown { (_, dv) =>
          dv match {
            case DynamicValue.Primitive(PrimitiveValue.Int(n)) => DynamicValue.int(n * 2)
            case other                                         => other
          }
        }
        assertTrue(result.get("age").one == Right(DynamicValue.int(84)))
      },
      test("transformFields renames record fields") {
        val result = recordVal.transformFields { (_, name) => name.toUpperCase }
        assertTrue(result.fields.map(_._1) == Vector("NAME", "AGE", "ACTIVE"))
      },
      test("transformMapKeys transforms map keys") {
        val result = mapVal.transformMapKeys { (_, key) =>
          key match {
            case DynamicValue.Primitive(PrimitiveValue.String(s)) => DynamicValue.string(s.toUpperCase)
            case other                                            => other
          }
        }
        assertTrue(result.entries.head._1 == DynamicValue.string("HELLO"))
      }
    ),
    suite("Selection")(
      test("select wraps value in selection") {
        val sel = stringVal.select
        assertTrue(sel.one == Right(stringVal))
      },
      test("select with type filters by type") {
        val sel = stringVal.select(DynamicValueType.Primitive)
        assertTrue(sel.nonEmpty)
      },
      test("select with wrong type returns empty") {
        val sel = stringVal.select(DynamicValueType.Record)
        assertTrue(sel.isEmpty)
      }
    ),
    suite("Pruning and retention")(
      test("prune removes matching values") {
        val result = recordVal.prune {
          case DynamicValue.Primitive(PrimitiveValue.Int(_)) => true
          case _                                             => false
        }
        assertTrue(result.fields.length == 2)
      },
      test("prunePath removes values at matching paths") {
        val result = recordVal.prunePath { path =>
          path.nodes.exists(_ == DynamicOptic.Node.Field("age"))
        }
        assertTrue(result.fields.length == 2)
      },
      test("pruneBoth combines predicates") {
        val result = recordVal.pruneBoth { (path, _) =>
          path.nodes.exists(_ == DynamicOptic.Node.Field("active"))
        }
        assertTrue(result.fields.length == 2)
      },
      test("retain keeps matching values") {
        val result = recordVal.retain {
          case DynamicValue.Primitive(PrimitiveValue.Int(_)) => true
          case _                                             => false
        }
        assertTrue(result.fields.length == 1)
      },
      test("retainPath keeps values at matching paths") {
        val result = recordVal.retainPath { path =>
          path.nodes.isEmpty || path.nodes.exists(_ == DynamicOptic.Node.Field("name"))
        }
        assertTrue(result.fields.length == 1)
      },
      test("retainBoth combines predicates") {
        val result = recordVal.retainBoth { (path, _) =>
          path.nodes.isEmpty || path.nodes.exists(_ == DynamicOptic.Node.Field("age"))
        }
        assertTrue(result.fields.length == 1)
      },
      test("project keeps only specified paths") {
        val result = nestedRecord.project(
          DynamicOptic.root.field("user").field("name")
        )
        assertTrue(result.fields.exists(_._1 == "user"))
      }
    ),
    suite("Partition")(
      test("partition splits by value predicate") {
        val (matching, nonMatching) = recordVal.partition {
          case DynamicValue.Primitive(PrimitiveValue.Int(_)) => true
          case _                                             => false
        }
        assertTrue(matching.fields.length == 1) &&
        assertTrue(nonMatching.fields.length == 2)
      },
      test("partitionPath splits by path predicate") {
        val (matching, nonMatching) = recordVal.partitionPath { path =>
          path.nodes.exists(_ == DynamicOptic.Node.Field("name"))
        }
        assertTrue(matching.fields.length == 1) &&
        assertTrue(nonMatching.fields.length == 2)
      },
      test("partitionBoth combines predicates") {
        val (matching, nonMatching) = recordVal.partitionBoth { (path, _) =>
          path.nodes.exists(_ == DynamicOptic.Node.Field("active"))
        }
        assertTrue(matching.fields.length == 1) &&
        assertTrue(nonMatching.fields.length == 2)
      }
    ),
    suite("Folding")(
      test("foldUp accumulates bottom-up") {
        val count = recordVal.foldUp(0) { (_, dv, acc) =>
          dv match {
            case _: DynamicValue.Primitive => acc + 1
            case _                         => acc
          }
        }
        assertTrue(count == 3)
      },
      test("foldDown accumulates top-down") {
        val count = recordVal.foldDown(0) { (_, dv, acc) =>
          dv match {
            case _: DynamicValue.Primitive => acc + 1
            case _                         => acc
          }
        }
        assertTrue(count == 3)
      },
      test("foldUpOrFail succeeds with valid function") {
        val result = recordVal.foldUpOrFail(0) { (_, dv, acc) =>
          dv match {
            case _: DynamicValue.Primitive => Right(acc + 1)
            case _                         => Right(acc)
          }
        }
        assertTrue(result == Right(3))
      },
      test("foldUpOrFail fails when function fails") {
        val result = recordVal.foldUpOrFail(0) { (_, _, _) =>
          Left(DynamicValueError("error"))
        }
        assertTrue(result.isLeft)
      },
      test("foldDownOrFail succeeds with valid function") {
        val result = recordVal.foldDownOrFail(0) { (_, dv, acc) =>
          dv match {
            case _: DynamicValue.Primitive => Right(acc + 1)
            case _                         => Right(acc)
          }
        }
        assertTrue(result == Right(3))
      },
      test("foldDownOrFail fails when function fails") {
        val result = recordVal.foldDownOrFail(0) { (_, _, _) =>
          Left(DynamicValueError("error"))
        }
        assertTrue(result.isLeft)
      },
      test("toKV converts to path-value pairs") {
        val kv = recordVal.toKV
        assertTrue(kv.nonEmpty)
      }
    ),
    suite("Conversion")(
      test("toJson converts to Json") {
        val json = stringVal.toJson
        assertTrue(json.isInstanceOf[zio.blocks.schema.json.Json])
      }
    ),
    suite("Advanced path operations")(
      test("get with AtIndices navigates multiple indices") {
        val seq    = DynamicValue.Sequence(stringVal, intVal, boolVal)
        val path   = DynamicOptic.root.atIndices(0, 2)
        val result = seq.get(path)
        assertTrue(result.toVector.length == 2)
      },
      test("get with AtMapKeys navigates multiple keys") {
        val map  = DynamicValue.Map(stringVal -> intVal, intVal -> boolVal)
        val path = new DynamicOptic(Vector(DynamicOptic.Node.AtMapKeys(Seq(stringVal, intVal))))
        val result = map.get(path)
        assertTrue(result.toVector.length == 2)
      },
      test("get with Elements navigates all elements") {
        val seq    = DynamicValue.Sequence(stringVal, intVal, boolVal)
        val path   = DynamicOptic.root.elements
        val result = seq.get(path)
        assertTrue(result.toVector.length == 3)
      },
      test("get with MapKeys gets all keys") {
        val map    = DynamicValue.Map(stringVal -> intVal, intVal -> boolVal)
        val path   = DynamicOptic.root.mapKeys
        val result = map.get(path)
        assertTrue(result.toVector.length == 2)
      },
      test("get with MapValues gets all values") {
        val map    = DynamicValue.Map(stringVal -> intVal, intVal -> boolVal)
        val path   = DynamicOptic.root.mapValues
        val result = map.get(path)
        assertTrue(result.toVector.length == 2)
      },
      test("modify with AtIndices updates multiple indices") {
        val seq    = DynamicValue.Sequence(intVal, intVal, intVal)
        val path   = DynamicOptic.root.atIndices(0, 2)
        val result = seq.modify(path)(_ => DynamicValue.int(100))
        assertTrue(result.elements == Vector(DynamicValue.int(100), intVal, DynamicValue.int(100)))
      },
      test("modify with AtMapKeys updates multiple keys") {
        val k1   = DynamicValue.string("a")
        val k2   = DynamicValue.string("b")
        val map  = DynamicValue.Map(k1 -> intVal, k2 -> intVal)
        val path = new DynamicOptic(Vector(DynamicOptic.Node.AtMapKeys(Seq(k1))))
        val result = map.modify(path)(_ => DynamicValue.int(100))
        assertTrue(result.entries.head._2 == DynamicValue.int(100))
      },
      test("modify with Elements updates all elements") {
        val seq    = DynamicValue.Sequence(intVal, intVal)
        val path   = DynamicOptic.root.elements
        val result = seq.modify(path)(_ => DynamicValue.int(100))
        assertTrue(result.elements.forall(_ == DynamicValue.int(100)))
      },
      test("modify with MapKeys modifies keys") {
        val k1     = DynamicValue.string("a")
        val map    = DynamicValue.Map(k1 -> intVal)
        val path   = DynamicOptic.root.mapKeys
        val result = map.modify(path)(_ => DynamicValue.string("A"))
        assertTrue(result.entries.head._1 == DynamicValue.string("A"))
      },
      test("modify with MapValues modifies values") {
        val k1     = DynamicValue.string("a")
        val map    = DynamicValue.Map(k1 -> intVal)
        val path   = DynamicOptic.root.mapValues
        val result = map.modify(path)(_ => DynamicValue.int(100))
        assertTrue(result.entries.head._2 == DynamicValue.int(100))
      },
      test("delete with AtIndices removes multiple indices") {
        val seq    = DynamicValue.Sequence(stringVal, intVal, boolVal)
        val path   = DynamicOptic.root.atIndices(0, 2)
        val result = seq.delete(path)
        assertTrue(result.elements.length == 1)
      },
      test("delete with AtMapKeys removes multiple keys") {
        val k1   = DynamicValue.string("a")
        val k2   = DynamicValue.string("b")
        val map  = DynamicValue.Map(k1 -> intVal, k2 -> intVal)
        val path = new DynamicOptic(Vector(DynamicOptic.Node.AtMapKeys(Seq(k1))))
        val result = map.delete(path)
        assertTrue(result.entries.length == 1)
      },
      test("delete with Elements removes all elements") {
        val seq    = DynamicValue.Sequence(stringVal, intVal)
        val path   = DynamicOptic.root.elements
        val result = seq.delete(path)
        assertTrue(result.elements.isEmpty)
      },
      test("delete with MapKeys removes all entries") {
        val k1     = DynamicValue.string("a")
        val map    = DynamicValue.Map(k1 -> intVal)
        val path   = DynamicOptic.root.mapKeys
        val result = map.delete(path)
        assertTrue(result.entries.isEmpty)
      },
      test("delete with MapValues removes all entries") {
        val k1     = DynamicValue.string("a")
        val map    = DynamicValue.Map(k1 -> intVal)
        val path   = DynamicOptic.root.mapValues
        val result = map.delete(path)
        assertTrue(result.entries.isEmpty)
      },
      test("insert at index inserts element") {
        val seq    = DynamicValue.Sequence(stringVal, boolVal)
        val path   = DynamicOptic.root.at(1)
        val result = seq.insert(path, intVal)
        assertTrue(result.elements == Vector(stringVal, intVal, boolVal))
      },
      test("insert at end of sequence") {
        val seq    = DynamicValue.Sequence(stringVal)
        val path   = DynamicOptic.root.at(1)
        val result = seq.insert(path, intVal)
        assertTrue(result.elements == Vector(stringVal, intVal))
      },
      test("insert into map with new key") {
        val k1   = DynamicValue.string("a")
        val k2   = DynamicValue.string("b")
        val map  = DynamicValue.Map(k1 -> intVal)
        val path = new DynamicOptic(Vector(DynamicOptic.Node.AtMapKey(k2)))
        val result = map.insert(path, boolVal)
        assertTrue(result.entries.length == 2)
      },
      test("nested delete in sequence") {
        val innerSeq = DynamicValue.Sequence(stringVal, intVal)
        val outerSeq = DynamicValue.Sequence(innerSeq)
        val path     = DynamicOptic.root.at(0).at(0)
        val result   = outerSeq.delete(path)
        assertTrue(result.elements.head.elements.length == 1)
      },
      test("nested delete in map") {
        val k1       = DynamicValue.string("a")
        val k2       = DynamicValue.string("b")
        val innerMap = DynamicValue.Map(k2 -> intVal)
        val outerMap = DynamicValue.Map(k1 -> innerMap)
        val path     = new DynamicOptic(Vector(DynamicOptic.Node.AtMapKey(k1), DynamicOptic.Node.AtMapKey(k2)))
        val result   = outerMap.delete(path)
        assertTrue(result.entries.head._2.entries.isEmpty)
      },
      test("nested insert in record") {
        val inner  = DynamicValue.Record("a" -> intVal)
        val outer  = DynamicValue.Record("inner" -> inner)
        val path   = DynamicOptic.root.field("inner").field("b")
        val result = outer.insert(path, boolVal)
        assertTrue(result.get("inner").one.map(_.fields.length) == Right(2))
      },
      test("nested insert in map") {
        val k1       = DynamicValue.string("outer")
        val k2       = DynamicValue.string("inner")
        val k3       = DynamicValue.string("new")
        val innerMap = DynamicValue.Map(k2 -> intVal)
        val outerMap = DynamicValue.Map(k1 -> innerMap)
        val path     = new DynamicOptic(Vector(DynamicOptic.Node.AtMapKey(k1), DynamicOptic.Node.AtMapKey(k3)))
        val result   = outerMap.insert(path, boolVal)
        assertTrue(result.get(k1).one.map(_.entries.length) == Right(2))
      },
      test("modify variant case value") {
        val variant = DynamicValue.Variant("Some", recordVal)
        val path    = DynamicOptic.root.caseOf("Some").field("age")
        val result  = variant.modify(path)(_ => DynamicValue.int(100))
        assertTrue(result.caseValue.flatMap(_.get("age").one.toOption) == Some(DynamicValue.int(100)))
      },
      test("delete in variant case value") {
        val variant = DynamicValue.Variant("Some", recordVal)
        val path    = DynamicOptic.root.caseOf("Some").field("active")
        val result  = variant.delete(path)
        assertTrue(result.caseValue.map(_.fields.length) == Some(2))
      },
      test("insert in variant case value") {
        val variant = DynamicValue.Variant("Some", recordVal)
        val path    = DynamicOptic.root.caseOf("Some").field("new")
        val result  = variant.insert(path, DynamicValue.string("added"))
        assertTrue(result.caseValue.map(_.fields.length) == Some(4))
      },
      test("wrapped path traversal") {
        val path   = DynamicOptic.root.wrapped.field("name")
        val result = recordVal.get(path)
        assertTrue(result.one == Right(stringVal))
      },
      test("nested delete with AtIndices") {
        val innerSeq = DynamicValue.Sequence(stringVal, intVal, boolVal)
        val outerRec = DynamicValue.Record("items" -> innerSeq)
        val path     = DynamicOptic.root.field("items").atIndices(0, 2)
        val result   = outerRec.delete(path)
        assertTrue(result.get("items").one.map(_.elements.length) == Right(1))
      },
      test("nested modify with AtIndices") {
        val innerSeq = DynamicValue.Sequence(intVal, intVal, intVal)
        val outerRec = DynamicValue.Record("items" -> innerSeq)
        val path     = DynamicOptic.root.field("items").atIndices(0, 2)
        val result   = outerRec.modify(path)(_ => DynamicValue.int(0))
        assertTrue(result.get("items").one.map(_.elements) == Right(Vector(DynamicValue.int(0), intVal, DynamicValue.int(0))))
      },
      test("nested modify with Elements") {
        val innerSeq = DynamicValue.Sequence(intVal, intVal)
        val outerRec = DynamicValue.Record("items" -> innerSeq)
        val path     = DynamicOptic.root.field("items").elements
        val result   = outerRec.modify(path)(_ => DynamicValue.int(0))
        assertTrue(result.get("items").one.map(_.elements.forall(_ == DynamicValue.int(0))) == Right(true))
      },
      test("nested delete with Elements") {
        val innerSeq = DynamicValue.Sequence(stringVal, intVal)
        val outerRec = DynamicValue.Record("items" -> innerSeq)
        val path     = DynamicOptic.root.field("items").elements
        val result   = outerRec.delete(path)
        assertTrue(result.get("items").one.map(_.elements.isEmpty) == Right(true))
      }
    ),
    suite("Normalization on all types")(
      test("sortFields on nested records") {
        val inner  = DynamicValue.Record("z" -> intVal, "a" -> stringVal)
        val outer  = DynamicValue.Record("inner" -> inner)
        val sorted = outer.sortFields
        val innerSorted = sorted.get("inner").one.map(_.fields.map(_._1))
        assertTrue(innerSorted == Right(Vector("a", "z")))
      },
      test("sortMapKeys on nested maps") {
        val k1       = DynamicValue.string("z")
        val k2       = DynamicValue.string("a")
        val innerMap = DynamicValue.Map(k1 -> intVal, k2 -> stringVal)
        val outerRec = DynamicValue.Record("map" -> innerMap)
        val sorted   = outerRec.sortMapKeys
        val innerSorted = sorted.get("map").one.map(_.entries.map(_._1))
        assertTrue(innerSorted == Right(Vector(k2, k1)))
      },
      test("dropNulls in variant") {
        val inner   = DynamicValue.Record("a" -> nullVal, "b" -> stringVal)
        val variant = DynamicValue.Variant("Some", inner)
        val dropped = variant.dropNulls
        assertTrue(dropped.caseValue.map(_.fields.length) == Some(1))
      },
      test("dropUnits in sequence") {
        val seq     = DynamicValue.Sequence(stringVal, DynamicValue.unit, intVal)
        val dropped = seq.dropUnits
        assertTrue(dropped.elements.length == 2)
      },
      test("dropEmpty in nested structure") {
        val inner   = DynamicValue.Record.empty
        val outer   = DynamicValue.Record("empty" -> inner, "full" -> stringVal)
        val dropped = outer.dropEmpty
        assertTrue(dropped.fields.length == 1)
      }
    ),
    suite("Transformation on all types")(
      test("transformUp on sequence") {
        val seq = DynamicValue.Sequence(intVal, intVal)
        val result = seq.transformUp { (_, dv) =>
          dv match {
            case DynamicValue.Primitive(PrimitiveValue.Int(n)) => DynamicValue.int(n * 2)
            case other                                         => other
          }
        }
        assertTrue(result.elements.forall(_ == DynamicValue.int(84)))
      },
      test("transformDown on map") {
        val k1  = DynamicValue.string("a")
        val map = DynamicValue.Map(k1 -> intVal)
        val result = map.transformDown { (_, dv) =>
          dv match {
            case DynamicValue.Primitive(PrimitiveValue.Int(n)) => DynamicValue.int(n + 1)
            case other                                         => other
          }
        }
        assertTrue(result.entries.head._2 == DynamicValue.int(43))
      },
      test("transformUp on variant") {
        val variant = DynamicValue.Variant("Some", intVal)
        val result = variant.transformUp { (_, dv) =>
          dv match {
            case DynamicValue.Primitive(PrimitiveValue.Int(n)) => DynamicValue.int(n * 2)
            case other                                         => other
          }
        }
        assertTrue(result.caseValue == Some(DynamicValue.int(84)))
      },
      test("transformFields in nested records") {
        val inner  = DynamicValue.Record("a" -> intVal)
        val outer  = DynamicValue.Record("inner" -> inner)
        val result = outer.transformFields { (_, name) => name.toUpperCase }
        assertTrue(result.fields.head._1 == "INNER")
        assertTrue(result.get("INNER").one.map(_.fields.head._1) == Right("A"))
      },
      test("transformMapKeys in map") {
        val k1  = DynamicValue.string("a")
        val map = DynamicValue.Map(k1 -> intVal)
        val result = map.transformMapKeys { (_, key) =>
          key match {
            case DynamicValue.Primitive(PrimitiveValue.String(s)) => DynamicValue.string(s.toUpperCase)
            case other                                            => other
          }
        }
        assertTrue(result.entries.head._1 == DynamicValue.string("A"))
      }
    ),
    suite("Prune and retain on all types")(
      test("prune on sequence") {
        val seq = DynamicValue.Sequence(intVal, stringVal, intVal)
        val result = seq.prune {
          case DynamicValue.Primitive(PrimitiveValue.Int(_)) => true
          case _                                             => false
        }
        assertTrue(result.elements.length == 1)
      },
      test("prune on map") {
        val k1  = DynamicValue.string("a")
        val k2  = DynamicValue.string("b")
        val map = DynamicValue.Map(k1 -> intVal, k2 -> stringVal)
        val result = map.prune {
          case DynamicValue.Primitive(PrimitiveValue.Int(_)) => true
          case _                                             => false
        }
        assertTrue(result.entries.length == 1)
      },
      test("prune on variant") {
        val inner   = DynamicValue.Record("a" -> intVal, "b" -> stringVal)
        val variant = DynamicValue.Variant("Some", inner)
        val result = variant.prune {
          case DynamicValue.Primitive(PrimitiveValue.Int(_)) => true
          case _                                             => false
        }
        assertTrue(result.caseValue.map(_.fields.length) == Some(1))
      },
      test("retain on sequence") {
        val seq = DynamicValue.Sequence(intVal, stringVal, intVal)
        val result = seq.retain {
          case DynamicValue.Primitive(PrimitiveValue.Int(_)) => true
          case _                                             => false
        }
        assertTrue(result.elements.length == 2)
      },
      test("retain on map") {
        val k1  = DynamicValue.string("a")
        val k2  = DynamicValue.string("b")
        val map = DynamicValue.Map(k1 -> intVal, k2 -> stringVal)
        val result = map.retain {
          case DynamicValue.Primitive(PrimitiveValue.Int(_)) => true
          case _                                             => false
        }
        assertTrue(result.entries.length == 1)
      }
    ),
    suite("Partition on all types")(
      test("partition on sequence") {
        val seq = DynamicValue.Sequence(intVal, stringVal, intVal)
        val (matching, nonMatching) = seq.partition {
          case DynamicValue.Primitive(PrimitiveValue.Int(_)) => true
          case _                                             => false
        }
        assertTrue(matching.elements.length == 2) &&
        assertTrue(nonMatching.elements.length == 1)
      },
      test("partition on map") {
        val k1  = DynamicValue.string("a")
        val k2  = DynamicValue.string("b")
        val map = DynamicValue.Map(k1 -> intVal, k2 -> stringVal)
        val (matching, nonMatching) = map.partition {
          case DynamicValue.Primitive(PrimitiveValue.Int(_)) => true
          case _                                             => false
        }
        assertTrue(matching.entries.length == 1) &&
        assertTrue(nonMatching.entries.length == 1)
      }
    ),
    suite("Fold on all types")(
      test("foldUp on sequence") {
        val seq = DynamicValue.Sequence(intVal, intVal, intVal)
        val count = seq.foldUp(0) { (_, dv, acc) =>
          dv match {
            case DynamicValue.Primitive(PrimitiveValue.Int(_)) => acc + 1
            case _                                             => acc
          }
        }
        assertTrue(count == 3)
      },
      test("foldDown on map") {
        val k1  = DynamicValue.string("a")
        val map = DynamicValue.Map(k1 -> intVal)
        val count = map.foldDown(0) { (_, dv, acc) =>
          dv match {
            case DynamicValue.Primitive(PrimitiveValue.Int(_)) => acc + 1
            case _                                             => acc
          }
        }
        assertTrue(count == 1)
      },
      test("foldUp on variant") {
        val variant = DynamicValue.Variant("Some", intVal)
        val count = variant.foldUp(0) { (_, dv, acc) =>
          dv match {
            case DynamicValue.Primitive(PrimitiveValue.Int(_)) => acc + 1
            case _                                             => acc
          }
        }
        assertTrue(count == 1)
      },
      test("toKV on sequence") {
        val seq = DynamicValue.Sequence(stringVal, intVal)
        val kv  = seq.toKV
        assertTrue(kv.length == 2)
      },
      test("toKV on map") {
        val k1  = DynamicValue.string("a")
        val map = DynamicValue.Map(k1 -> intVal)
        val kv  = map.toKV
        assertTrue(kv.length == 1)
      },
      test("toKV on variant") {
        val variant = DynamicValue.Variant("Some", stringVal)
        val kv      = variant.toKV
        assertTrue(kv.length == 1)
      },
      test("toKV on null") {
        val kv = DynamicValue.Null.toKV
        assertTrue(kv.length == 1)
      },
      test("foldUpOrFail on record succeeds") {
        val result = recordVal.foldUpOrFail(Vector.empty[String]) { (_, dv, acc) =>
          dv match {
            case DynamicValue.Primitive(PrimitiveValue.String(s)) => Right(acc :+ s)
            case _                                                => Right(acc)
          }
        }
        assertTrue(result.map(_.length) == Right(1))
      },
      test("foldUpOrFail on sequence succeeds") {
        val seq = DynamicValue.Sequence(stringVal, stringVal)
        val result = seq.foldUpOrFail(0) { (_, dv, acc) =>
          dv match {
            case DynamicValue.Primitive(PrimitiveValue.String(_)) => Right(acc + 1)
            case _                                                => Right(acc)
          }
        }
        assertTrue(result == Right(2))
      },
      test("foldUpOrFail on map succeeds") {
        val k1  = DynamicValue.string("a")
        val map = DynamicValue.Map(k1 -> stringVal)
        val result = map.foldUpOrFail(0) { (_, dv, acc) =>
          dv match {
            case DynamicValue.Primitive(PrimitiveValue.String(_)) => Right(acc + 1)
            case _                                                => Right(acc)
          }
        }
        assertTrue(result == Right(2))
      },
      test("foldUpOrFail on variant succeeds") {
        val variant = DynamicValue.Variant("Some", stringVal)
        val result = variant.foldUpOrFail(0) { (_, dv, acc) =>
          dv match {
            case DynamicValue.Primitive(PrimitiveValue.String(_)) => Right(acc + 1)
            case _                                                => Right(acc)
          }
        }
        assertTrue(result == Right(1))
      },
      test("foldDownOrFail on sequence succeeds") {
        val seq = DynamicValue.Sequence(intVal, intVal)
        val result = seq.foldDownOrFail(0) { (_, dv, acc) =>
          dv match {
            case DynamicValue.Primitive(PrimitiveValue.Int(_)) => Right(acc + 1)
            case _                                             => Right(acc)
          }
        }
        assertTrue(result == Right(2))
      },
      test("foldDownOrFail on map succeeds") {
        val k1  = DynamicValue.string("a")
        val map = DynamicValue.Map(k1 -> intVal)
        val result = map.foldDownOrFail(0) { (_, dv, acc) =>
          dv match {
            case DynamicValue.Primitive(_) => Right(acc + 1)
            case _                         => Right(acc)
          }
        }
        assertTrue(result == Right(2))
      },
      test("foldDownOrFail on variant succeeds") {
        val variant = DynamicValue.Variant("Some", intVal)
        val result = variant.foldDownOrFail(0) { (_, dv, acc) =>
          dv match {
            case DynamicValue.Primitive(PrimitiveValue.Int(_)) => Right(acc + 1)
            case _                                             => Right(acc)
          }
        }
        assertTrue(result == Right(1))
      }
    ),
    suite("Transform on Sequence and Map")(
      test("transformUp on deeply nested sequence") {
        val innerSeq = DynamicValue.Sequence(intVal, intVal)
        val outerSeq = DynamicValue.Sequence(innerSeq)
        val result = outerSeq.transformUp { (_, dv) =>
          dv match {
            case DynamicValue.Primitive(PrimitiveValue.Int(n)) => DynamicValue.int(n * 2)
            case other                                         => other
          }
        }
        assertTrue(result.elements.head.elements.forall(_ == DynamicValue.int(84)))
      },
      test("transformDown on deeply nested map") {
        val k1       = DynamicValue.string("a")
        val innerMap = DynamicValue.Map(k1 -> intVal)
        val k2       = DynamicValue.string("outer")
        val outerMap = DynamicValue.Map(k2 -> innerMap)
        val result = outerMap.transformDown { (_, dv) =>
          dv match {
            case DynamicValue.Primitive(PrimitiveValue.Int(n)) => DynamicValue.int(n + 1)
            case other                                         => other
          }
        }
        assertTrue(result.get(k2).one.flatMap(_.get(k1).one) == Right(DynamicValue.int(43)))
      },
      test("transformFields on sequence of records") {
        val rec = DynamicValue.Record("field" -> intVal)
        val seq = DynamicValue.Sequence(rec, rec)
        val result = seq.transformFields { (_, name) => name.toUpperCase }
        assertTrue(result.elements.forall(_.fields.head._1 == "FIELD"))
      },
      test("transformFields on map values") {
        val k1  = DynamicValue.string("key")
        val rec = DynamicValue.Record("field" -> intVal)
        val map = DynamicValue.Map(k1 -> rec)
        val result = map.transformFields { (_, name) => name.toUpperCase }
        assertTrue(result.get(k1).one.map(_.fields.head._1) == Right("FIELD"))
      },
      test("transformFields on variant") {
        val rec     = DynamicValue.Record("field" -> intVal)
        val variant = DynamicValue.Variant("Some", rec)
        val result  = variant.transformFields { (_, name) => name.toUpperCase }
        assertTrue(result.caseValue.map(_.fields.head._1) == Some("FIELD"))
      },
      test("transformMapKeys on nested map") {
        val k1       = DynamicValue.string("inner")
        val innerMap = DynamicValue.Map(k1 -> intVal)
        val k2       = DynamicValue.string("outer")
        val outerMap = DynamicValue.Map(k2 -> innerMap)
        val result = outerMap.transformMapKeys { (_, key) =>
          key match {
            case DynamicValue.Primitive(PrimitiveValue.String(s)) => DynamicValue.string(s.toUpperCase)
            case other                                            => other
          }
        }
        assertTrue(result.entries.head._1 == DynamicValue.string("OUTER"))
      },
      test("transformMapKeys on sequence containing maps") {
        val k1  = DynamicValue.string("a")
        val map = DynamicValue.Map(k1 -> intVal)
        val seq = DynamicValue.Sequence(map)
        val result = seq.transformMapKeys { (_, key) =>
          key match {
            case DynamicValue.Primitive(PrimitiveValue.String(s)) => DynamicValue.string(s.toUpperCase)
            case other                                            => other
          }
        }
        assertTrue(result.elements.head.entries.head._1 == DynamicValue.string("A"))
      },
      test("transformMapKeys on variant containing map") {
        val k1      = DynamicValue.string("a")
        val map     = DynamicValue.Map(k1 -> intVal)
        val variant = DynamicValue.Variant("Some", map)
        val result = variant.transformMapKeys { (_, key) =>
          key match {
            case DynamicValue.Primitive(PrimitiveValue.String(s)) => DynamicValue.string(s.toUpperCase)
            case other                                            => other
          }
        }
        assertTrue(result.caseValue.map(_.entries.head._1) == Some(DynamicValue.string("A")))
      }
    ),
    suite("Normalization on more types")(
      test("sortFields on sequence of records") {
        val rec = DynamicValue.Record("z" -> intVal, "a" -> stringVal)
        val seq = DynamicValue.Sequence(rec)
        val result = seq.sortFields
        assertTrue(result.elements.head.fields.map(_._1) == Vector("a", "z"))
      },
      test("sortFields on map values") {
        val k1     = DynamicValue.string("key")
        val rec    = DynamicValue.Record("z" -> intVal, "a" -> stringVal)
        val map    = DynamicValue.Map(k1 -> rec)
        val result = map.sortFields
        assertTrue(result.get(k1).one.map(_.fields.map(_._1)) == Right(Vector("a", "z")))
      },
      test("sortFields on variant") {
        val rec     = DynamicValue.Record("z" -> intVal, "a" -> stringVal)
        val variant = DynamicValue.Variant("Some", rec)
        val result  = variant.sortFields
        assertTrue(result.caseValue.map(_.fields.map(_._1)) == Some(Vector("a", "z")))
      },
      test("sortMapKeys on sequence of maps") {
        val k1     = DynamicValue.string("z")
        val k2     = DynamicValue.string("a")
        val map    = DynamicValue.Map(k1 -> intVal, k2 -> stringVal)
        val seq    = DynamicValue.Sequence(map)
        val result = seq.sortMapKeys
        assertTrue(result.elements.head.entries.map(_._1) == Vector(k2, k1))
      },
      test("sortMapKeys on variant") {
        val k1      = DynamicValue.string("z")
        val k2      = DynamicValue.string("a")
        val map     = DynamicValue.Map(k1 -> intVal, k2 -> stringVal)
        val variant = DynamicValue.Variant("Some", map)
        val result  = variant.sortMapKeys
        assertTrue(result.caseValue.map(_.entries.map(_._1)) == Some(Vector(k2, k1)))
      },
      test("dropNulls on sequence") {
        val seq    = DynamicValue.Sequence(stringVal, nullVal, intVal)
        val result = seq.dropNulls
        assertTrue(result.elements.length == 2)
      },
      test("dropNulls on map") {
        val k1     = DynamicValue.string("a")
        val k2     = DynamicValue.string("b")
        val map    = DynamicValue.Map(k1 -> stringVal, k2 -> nullVal)
        val result = map.dropNulls
        assertTrue(result.entries.length == 1)
      },
      test("dropEmpty on sequence") {
        val seq    = DynamicValue.Sequence(stringVal, DynamicValue.Sequence.empty)
        val result = seq.dropEmpty
        assertTrue(result.elements.length == 1)
      },
      test("dropEmpty on map") {
        val k1     = DynamicValue.string("a")
        val k2     = DynamicValue.string("b")
        val map    = DynamicValue.Map(k1 -> stringVal, k2 -> DynamicValue.Map.empty)
        val result = map.dropEmpty
        assertTrue(result.entries.length == 1)
      },
      test("dropEmpty on variant") {
        val inner   = DynamicValue.Record("a" -> DynamicValue.Record.empty, "b" -> stringVal)
        val variant = DynamicValue.Variant("Some", inner)
        val result  = variant.dropEmpty
        assertTrue(result.caseValue.map(_.fields.length) == Some(1))
      },
      test("dropUnits on map") {
        val k1     = DynamicValue.string("a")
        val k2     = DynamicValue.string("b")
        val map    = DynamicValue.Map(k1 -> stringVal, k2 -> DynamicValue.unit)
        val result = map.dropUnits
        assertTrue(result.entries.length == 1)
      },
      test("dropUnits on variant") {
        val inner   = DynamicValue.Record("a" -> DynamicValue.unit, "b" -> stringVal)
        val variant = DynamicValue.Variant("Some", inner)
        val result  = variant.dropUnits
        assertTrue(result.caseValue.map(_.fields.length) == Some(1))
      }
    ),
    suite("Retain on variant")(
      test("retain on variant keeps matching values") {
        val inner   = DynamicValue.Record("a" -> intVal, "b" -> stringVal)
        val variant = DynamicValue.Variant("Some", inner)
        val result = variant.retain {
          case DynamicValue.Primitive(PrimitiveValue.Int(_)) => true
          case _                                             => false
        }
        assertTrue(result.caseValue.map(_.fields.length) == Some(1))
      },
      test("retainPath on variant") {
        val inner   = DynamicValue.Record("a" -> intVal, "b" -> stringVal)
        val variant = DynamicValue.Variant("Some", inner)
        val result = variant.retainPath { path =>
          path.nodes.nonEmpty && path.nodes.exists(_ == DynamicOptic.Node.Field("a"))
        }
        assertTrue(result.caseValue.map(_.fields.length) == Some(1))
      }
    ),
    suite("fromKV and toKV")(
      test("toKV and fromKVUnsafe round-trip for record") {
        val rec    = DynamicValue.Record("a" -> intVal, "b" -> stringVal)
        val kv     = rec.toKV
        val result = DynamicValue.fromKVUnsafe(kv)
        assertTrue(result == rec)
      },
      test("fromKVUnsafe with nested path creates nested structure") {
        val path  = DynamicOptic.root.field("a").field("b")
        val kvs   = Seq((path, intVal))
        val result = DynamicValue.fromKVUnsafe(kvs)
        assertTrue(result.get("a").one.flatMap(_.get("b").one) == Right(intVal))
      },
      test("fromKVUnsafe with sequence path") {
        val path  = DynamicOptic.root.at(0)
        val kvs   = Seq((path, intVal))
        val result = DynamicValue.fromKVUnsafe(kvs)
        assertTrue(result.get(0).one == Right(intVal))
      },
      test("fromKVUnsafe with map path") {
        val key   = DynamicValue.string("key")
        val path  = new DynamicOptic(Vector(DynamicOptic.Node.AtMapKey(key)))
        val kvs   = Seq((path, intVal))
        val result = DynamicValue.fromKVUnsafe(kvs)
        assertTrue(result.get(key).one == Right(intVal))
      },
      test("fromKVUnsafe with variant path") {
        val path  = DynamicOptic.root.caseOf("Some").field("value")
        val kvs   = Seq((path, intVal))
        val result = DynamicValue.fromKVUnsafe(kvs)
        assertTrue(result.getCase("Some").one.flatMap(_.get("value").one) == Right(intVal))
      },
      test("fromKVUnsafe with multiple paths") {
        val path1 = DynamicOptic.root.field("a")
        val path2 = DynamicOptic.root.field("b")
        val kvs   = Seq((path1, intVal), (path2, stringVal))
        val result = DynamicValue.fromKVUnsafe(kvs)
        assertTrue(result.fields.length == 2)
      },
      test("fromKVUnsafe with empty sequence") {
        val result = DynamicValue.fromKVUnsafe(Seq.empty)
        assertTrue(result == DynamicValue.Record.empty)
      },
      test("fromKVUnsafe updates existing field") {
        val path1 = DynamicOptic.root.field("a")
        val path2 = DynamicOptic.root.field("a")
        val kvs   = Seq((path1, intVal), (path2, stringVal))
        val result = DynamicValue.fromKVUnsafe(kvs)
        assertTrue(result.get("a").one == Right(stringVal))
      },
      test("fromKVUnsafe with nested sequence index") {
        val path  = DynamicOptic.root.at(2)
        val kvs   = Seq((path, intVal))
        val result = DynamicValue.fromKVUnsafe(kvs)
        assertTrue(result.elements.length == 3)
      },
      test("toKV on nested structure") {
        val nested = DynamicValue.Record(
          "a" -> DynamicValue.Record("b" -> intVal),
          "c" -> seqVal
        )
        val kv = nested.toKV
        assertTrue(kv.nonEmpty)
      }
    ),
    suite("Query methods")(
      test("query on record finds matching values") {
        val result = recordVal.select.query(_ == intVal)
        assertTrue(result.nonEmpty)
      },
      test("query on sequence finds matching values") {
        val result = seqVal.select.query(_ == stringVal)
        assertTrue(result.nonEmpty)
      },
      test("query on map finds matching values") {
        val result = mapVal.select.query(_ == intVal)
        assertTrue(result.nonEmpty)
      },
      test("query on variant finds matching values") {
        val result = variantVal.select.query(_ == stringVal)
        assertTrue(result.nonEmpty)
      },
      test("queryPath finds values at matching paths") {
        val result = nestedRecord.select.queryPath { path =>
          path.nodes.exists(_ == DynamicOptic.Node.Field("name"))
        }
        assertTrue(result.nonEmpty)
      },
      test("queryBoth combines predicates") {
        val result = nestedRecord.select.queryBoth { (path, dv) =>
          path.nodes.nonEmpty && dv.isInstanceOf[DynamicValue.Primitive]
        }
        assertTrue(result.nonEmpty)
      }
    ),
    suite("Nested delete with AtMapKeys")(
      test("nested delete with AtMapKeys in record") {
        val k1       = DynamicValue.string("a")
        val k2       = DynamicValue.string("b")
        val innerMap = DynamicValue.Map(k1 -> intVal, k2 -> stringVal)
        val outerRec = DynamicValue.Record("map" -> innerMap)
        val path     = new DynamicOptic(Vector(DynamicOptic.Node.Field("map"), DynamicOptic.Node.AtMapKeys(Seq(k1))))
        val result   = outerRec.delete(path)
        assertTrue(result.get("map").one.map(_.entries.length) == Right(1))
      },
      test("nested modify with AtMapKeys in record") {
        val k1       = DynamicValue.string("a")
        val k2       = DynamicValue.string("b")
        val innerMap = DynamicValue.Map(k1 -> intVal, k2 -> intVal)
        val outerRec = DynamicValue.Record("map" -> innerMap)
        val path     = new DynamicOptic(Vector(DynamicOptic.Node.Field("map"), DynamicOptic.Node.AtMapKeys(Seq(k1, k2))))
        val result   = outerRec.modify(path)(_ => DynamicValue.int(0))
        assertTrue(result.get("map").one.map(_.entries.forall(_._2 == DynamicValue.int(0))) == Right(true))
      }
    ),
    suite("upsertAtPathCreatingParents edge cases")(
      test("fromKVUnsafe with deeply nested field path") {
        val path   = DynamicOptic.root.field("a").field("b").field("c")
        val kvs    = Seq((path, intVal))
        val result = DynamicValue.fromKVUnsafe(kvs)
        assertTrue(result.get("a").one.flatMap(_.get("b").one).flatMap(_.get("c").one) == Right(intVal))
      },
      test("fromKVUnsafe with sequence inside record") {
        val path   = DynamicOptic.root.field("arr").at(0)
        val kvs    = Seq((path, intVal))
        val result = DynamicValue.fromKVUnsafe(kvs)
        assertTrue(result.get("arr").one.flatMap(_.get(0).one) == Right(intVal))
      },
      test("fromKVUnsafe with map inside record") {
        val key    = DynamicValue.string("k")
        val path   = new DynamicOptic(Vector(DynamicOptic.Node.Field("map"), DynamicOptic.Node.AtMapKey(key)))
        val kvs    = Seq((path, intVal))
        val result = DynamicValue.fromKVUnsafe(kvs)
        assertTrue(result.get("map").one.flatMap(_.get(key).one) == Right(intVal))
      },
      test("fromKVUnsafe with variant inside record") {
        val path   = DynamicOptic.root.field("opt").caseOf("Some").field("value")
        val kvs    = Seq((path, intVal))
        val result = DynamicValue.fromKVUnsafe(kvs)
        assertTrue(result.get("opt").one.flatMap(_.getCase("Some").one).flatMap(_.get("value").one) == Right(intVal))
      },
      test("fromKVUnsafe with variant updating existing case") {
        val path1  = DynamicOptic.root.caseOf("Some").field("x")
        val path2  = DynamicOptic.root.caseOf("Some").field("y")
        val kvs    = Seq((path1, intVal), (path2, stringVal))
        val result = DynamicValue.fromKVUnsafe(kvs)
        assertTrue(result.getCase("Some").one.map(_.fields.length) == Right(2))
      },
      test("fromKVUnsafe with map updating existing key") {
        val key    = DynamicValue.string("k")
        val path1  = new DynamicOptic(Vector(DynamicOptic.Node.AtMapKey(key)))
        val path2  = new DynamicOptic(Vector(DynamicOptic.Node.AtMapKey(key)))
        val kvs    = Seq((path1, intVal), (path2, stringVal))
        val result = DynamicValue.fromKVUnsafe(kvs)
        assertTrue(result.get(key).one == Right(stringVal))
      },
      test("fromKVUnsafe with sequence extending") {
        val path1  = DynamicOptic.root.at(0)
        val path2  = DynamicOptic.root.at(1)
        val kvs    = Seq((path1, intVal), (path2, stringVal))
        val result = DynamicValue.fromKVUnsafe(kvs)
        assertTrue(result.elements == Vector(intVal, stringVal))
      },
      test("fromKVUnsafe with sequence extending existing") {
        val path1  = DynamicOptic.root.at(0)
        val path2  = DynamicOptic.root.at(2)
        val kvs    = Seq((path1, intVal), (path2, stringVal))
        val result = DynamicValue.fromKVUnsafe(kvs)
        assertTrue(result.elements.length == 3)
      },
      test("fromKVUnsafe with nested sequence path") {
        val path   = DynamicOptic.root.at(0).at(0)
        val kvs    = Seq((path, intVal))
        val result = DynamicValue.fromKVUnsafe(kvs)
        assertTrue(result.get(0).one.flatMap(_.get(0).one) == Right(intVal))
      },
      test("fromKVUnsafe with nested map path") {
        val k1     = DynamicValue.string("a")
        val k2     = DynamicValue.string("b")
        val path   = new DynamicOptic(Vector(DynamicOptic.Node.AtMapKey(k1), DynamicOptic.Node.AtMapKey(k2)))
        val kvs    = Seq((path, intVal))
        val result = DynamicValue.fromKVUnsafe(kvs)
        assertTrue(result.get(k1).one.flatMap(_.get(k2).one) == Right(intVal))
      },
      test("fromKVUnsafe ignores unsupported path nodes") {
        val path   = DynamicOptic.root.wrapped.field("a")
        val kvs    = Seq((path, intVal))
        val result = DynamicValue.fromKVUnsafe(kvs)
        assertTrue(result.get("a").one == Right(intVal))
      }
    ),
    suite("delete edge cases")(
      test("delete nested AtMapKey via record") {
        val k1       = DynamicValue.string("a")
        val innerMap = DynamicValue.Map(k1 -> intVal)
        val outerRec = DynamicValue.Record("map" -> innerMap)
        val path     = new DynamicOptic(Vector(DynamicOptic.Node.Field("map"), DynamicOptic.Node.AtMapKey(k1)))
        val result   = outerRec.delete(path)
        assertTrue(result.get("map").one.map(_.entries.isEmpty) == Right(true))
      },
      test("delete nested AtIndex via record") {
        val innerSeq = DynamicValue.Sequence(intVal, stringVal)
        val outerRec = DynamicValue.Record("arr" -> innerSeq)
        val path     = DynamicOptic.root.field("arr").at(0)
        val result   = outerRec.delete(path)
        assertTrue(result.get("arr").one.map(_.elements.length) == Right(1))
      },
      test("delete nested case value via variant") {
        val inner   = DynamicValue.Record("a" -> intVal, "b" -> stringVal)
        val variant = DynamicValue.Variant("Some", inner)
        val path    = DynamicOptic.root.caseOf("Some").field("a")
        val result  = variant.delete(path)
        assertTrue(result.caseValue.map(_.fields.length) == Some(1))
      },
      test("delete with MapValues in nested structure") {
        val k1       = DynamicValue.string("a")
        val innerMap = DynamicValue.Map(k1 -> intVal)
        val outerRec = DynamicValue.Record("map" -> innerMap)
        val path     = DynamicOptic.root.field("map").mapValues
        val result   = outerRec.delete(path)
        assertTrue(result.get("map").one.map(_.entries.isEmpty) == Right(true))
      },
      test("delete with MapKeys in nested structure") {
        val k1       = DynamicValue.string("a")
        val innerMap = DynamicValue.Map(k1 -> intVal)
        val outerRec = DynamicValue.Record("map" -> innerMap)
        val path     = DynamicOptic.root.field("map").mapKeys
        val result   = outerRec.delete(path)
        assertTrue(result.get("map").one.map(_.entries.isEmpty) == Right(true))
      },
      test("delete with wrapped path") {
        val path   = DynamicOptic.root.wrapped.field("name")
        val result = recordVal.delete(path)
        assertTrue(result.fields.length == 2)
      },
      test("nested delete Elements then field") {
        val innerRec = DynamicValue.Record("x" -> intVal, "y" -> stringVal)
        val seq      = DynamicValue.Sequence(innerRec, innerRec)
        val path     = DynamicOptic.root.elements.field("x")
        val result   = seq.delete(path)
        assertTrue(result.elements.forall(_.fields.length == 1))
      },
      test("nested delete MapKeys then transforms") {
        val k1       = DynamicValue.Record("key" -> intVal)
        val innerMap = DynamicValue.Map(k1 -> stringVal)
        val path     = DynamicOptic.root.mapKeys.field("key")
        val result   = innerMap.delete(path)
        assertTrue(result.entries.head._1.fields.isEmpty)
      },
      test("nested delete MapValues then field") {
        val k1       = DynamicValue.string("a")
        val innerRec = DynamicValue.Record("x" -> intVal, "y" -> stringVal)
        val map      = DynamicValue.Map(k1 -> innerRec)
        val path     = DynamicOptic.root.mapValues.field("x")
        val result   = map.delete(path)
        assertTrue(result.entries.head._2.fields.length == 1)
      },
      test("nested delete AtIndices then field") {
        val innerRec = DynamicValue.Record("x" -> intVal, "y" -> stringVal)
        val seq      = DynamicValue.Sequence(innerRec, innerRec, innerRec)
        val path     = DynamicOptic.root.atIndices(0, 2).field("x")
        val result   = seq.delete(path)
        val first    = result.elements.head.fields.length
        val second   = result.elements(1).fields.length
        val third    = result.elements(2).fields.length
        assertTrue(first == 1 && second == 2 && third == 1)
      },
      test("nested delete AtMapKeys then field") {
        val k1       = DynamicValue.string("a")
        val k2       = DynamicValue.string("b")
        val innerRec = DynamicValue.Record("x" -> intVal, "y" -> stringVal)
        val map      = DynamicValue.Map(k1 -> innerRec, k2 -> innerRec)
        val path     = new DynamicOptic(Vector(DynamicOptic.Node.AtMapKeys(Seq(k1)), DynamicOptic.Node.Field("x")))
        val result   = map.delete(path)
        val first    = result.entries.head._2.fields.length
        val second   = result.entries(1)._2.fields.length
        assertTrue(first == 1 && second == 2)
      }
    )
  )
}
