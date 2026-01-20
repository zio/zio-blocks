package zio.blocks.schema.json

import zio.blocks.schema._
import zio.test._
// import zio.test.Assertion._

object JsonSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment, Any] = suite("JsonSpec")(
    adtSuite,
    typeTestingSuite,
    navigationSuite,
    modificationSuite,
    mergeSuite,
    transformationSuite,
    filteringSuite,
    normalizationSuite,
    foldingSuite,
    kvSuite,
    dynamicValueInteropSuite,
    encodingDecodingSuite,
    comparisonSuite,
    jsonSelectionSuite,
    jsonErrorSuite,
    jsonDecoderEncoderSuite
  )

  // ===========================================================================
  // ADT Construction Suite
  // ===========================================================================
  val adtSuite: Spec[Any, Nothing] = suite("ADT Construction")(
    test("Json.Object construction") {
      val obj = Json.Object("name" -> Json.String("Alice"), "age" -> Json.number(30))
      assertTrue(obj.isObject) &&
      assertTrue(obj.fields.size == 2) &&
      assertTrue(obj.fields.head._1 == "name")
    },
    test("Json.Object.empty") {
      val obj = Json.Object.empty
      assertTrue(obj.isObject) &&
      assertTrue(obj.fields.isEmpty)
    },
    test("Json.Array construction") {
      val arr = Json.Array(Json.String("a"), Json.String("b"), Json.String("c"))
      assertTrue(arr.isArray) &&
      assertTrue(arr.elements.size == 3)
    },
    test("Json.Array.empty") {
      val arr = Json.Array.empty
      assertTrue(arr.isArray) &&
      assertTrue(arr.elements.isEmpty)
    },
    test("Json.String construction") {
      val str = Json.String("hello")
      assertTrue(str.isString) &&
      assertTrue(str.stringValue.contains("hello"))
    },
    test("Json.Number construction from various types") {
      assertTrue(Json.number(42).numberValue.contains("42")) &&
      assertTrue(Json.number(42L).numberValue.contains("42")) &&
      assertTrue(Json.number(3.14).numberValue.exists(_.startsWith("3.14"))) &&
      assertTrue(Json.number(3.14f).numberValue.exists(_.startsWith("3.14"))) &&
      assertTrue(Json.number(BigInt(100)).numberValue.contains("100")) &&
      assertTrue(Json.number(BigDecimal("99.99")).numberValue.contains("99.99")) &&
      assertTrue(Json.number(1: Short).numberValue.contains("1")) &&
      assertTrue(Json.number(1: Byte).numberValue.contains("1"))
    },
    test("Json.Number conversions") {
      val num = Json.Number("123.456")
      assertTrue(num.toInt == 123) &&
      assertTrue(num.toLong == 123L) &&
      assertTrue(num.toDouble == 123.456) &&
      assertTrue(num.toBigDecimal == BigDecimal("123.456"))
    },
    test("Json.Boolean construction") {
      assertTrue(Json.Boolean(true).booleanValue.contains(true)) &&
      assertTrue(Json.Boolean(false).booleanValue.contains(false)) &&
      assertTrue(Json.Boolean.True.booleanValue.contains(true)) &&
      assertTrue(Json.Boolean.False.booleanValue.contains(false))
    },
    test("Json.Null") {
      assertTrue(Json.Null.isNull)
    }
  )

  // ===========================================================================
  // Type Testing Suite
  // ===========================================================================
  val typeTestingSuite: Spec[Any, Nothing] = suite("Type Testing")(
    test("isObject returns true only for objects") {
      assertTrue(Json.Object.empty.isObject) &&
      assertTrue(!Json.Array.empty.isObject) &&
      assertTrue(!Json.String("").isObject) &&
      assertTrue(!Json.Number("0").isObject) &&
      assertTrue(!Json.Boolean(true).isObject) &&
      assertTrue(!Json.Null.isObject)
    },
    test("isArray returns true only for arrays") {
      assertTrue(Json.Array.empty.isArray) &&
      assertTrue(!Json.Object.empty.isArray) &&
      assertTrue(!Json.String("").isArray)
    },
    test("isString returns true only for strings") {
      assertTrue(Json.String("test").isString) &&
      assertTrue(!Json.Number("1").isString) &&
      assertTrue(!Json.Null.isString)
    },
    test("isNumber returns true only for numbers") {
      assertTrue(Json.Number("42").isNumber) &&
      assertTrue(!Json.String("42").isNumber)
    },
    test("isBoolean returns true only for booleans") {
      assertTrue(Json.Boolean(true).isBoolean) &&
      assertTrue(!Json.String("true").isBoolean)
    },
    test("isNull returns true only for null") {
      assertTrue(Json.Null.isNull) &&
      assertTrue(!Json.String("null").isNull)
    }
  )

  // ===========================================================================
  // Navigation Suite
  // ===========================================================================
  val navigationSuite: Spec[Any, Nothing] = suite("Navigation")(
    test("apply(key) on object returns value") {
      val obj       = Json.Object("name" -> Json.String("Alice"))
      val selection = obj("name")
      assertTrue(selection.nonEmpty) &&
      assertTrue(selection.first.toOption.contains(Json.String("Alice")))
    },
    test("apply(key) on object with missing key returns empty") {
      val obj       = Json.Object("name" -> Json.String("Alice"))
      val selection = obj("missing")
      assertTrue(selection.isEmpty)
    },
    test("apply(index) on array returns element") {
      val arr       = Json.Array(Json.String("a"), Json.String("b"), Json.String("c"))
      val selection = arr(1)
      assertTrue(selection.first.toOption.contains(Json.String("b")))
    },
    test("apply(index) out of bounds returns empty") {
      val arr = Json.Array(Json.String("a"))
      assertTrue(arr(5).isEmpty) &&
      assertTrue(arr(-1).isEmpty)
    },
    test("get with DynamicOptic.field") {
      val obj       = Json.Object("user" -> Json.Object("name" -> Json.String("Bob")))
      val path      = DynamicOptic.root.field("user").field("name")
      val selection = obj.get(path)
      assertTrue(selection.first.toOption.contains(Json.String("Bob")))
    },
    test("get with DynamicOptic.at") {
      val arr       = Json.Array(Json.number(1), Json.number(2), Json.number(3))
      val path      = DynamicOptic.root.at(2)
      val selection = arr.get(path)
      assertTrue(selection.first.toOption.contains(Json.number(3)))
    },
    test("chained navigation") {
      val json = Json.Object(
        "users" -> Json.Array(
          Json.Object("name" -> Json.String("Alice")),
          Json.Object("name" -> Json.String("Bob"))
        )
      )
      val selection = json("users")(0)("name")
      assertTrue(selection.first.toOption.contains(Json.String("Alice")))
    }
  )

  // ===========================================================================
  // Modification Suite
  // ===========================================================================
  val modificationSuite: Spec[Any, Nothing] = suite("Modification")(
    test("set replaces value at path") {
      val obj     = Json.Object("name" -> Json.String("Alice"))
      val path    = DynamicOptic.root.field("name")
      val updated = obj.set(path, Json.String("Bob"))
      assertTrue(updated("name").first.toOption.contains(Json.String("Bob")))
    },
    test("modify applies function at path") {
      val obj     = Json.Object("count" -> Json.number(5))
      val path    = DynamicOptic.root.field("count")
      val updated = obj.modify(
        path,
        {
          case Json.Number(n) => Json.number(n.toInt + 1)
          case other          => other
        }
      )
      assertTrue(updated("count").first.toOption.contains(Json.number(6)))
    },
    test("delete removes field from object") {
      val obj     = Json.Object("a" -> Json.number(1), "b" -> Json.number(2))
      val path    = DynamicOptic.root.field("a")
      val updated = obj.delete(path)
      assertTrue(updated("a").isEmpty) &&
      assertTrue(updated("b").nonEmpty)
    },
    test("delete removes element from array") {
      val arr     = Json.Array(Json.String("a"), Json.String("b"), Json.String("c"))
      val path    = DynamicOptic.root.at(1)
      val updated = arr.delete(path)
      updated match {
        case Json.Array(elems) => assertTrue(elems.size == 2)
        case _                 => assertTrue(false)
      }
    },
    test("insert adds field to object") {
      val obj     = Json.Object("a" -> Json.number(1))
      val path    = DynamicOptic.root.field("b")
      val updated = obj.insert(path, Json.number(2))
      assertTrue(updated("b").first.toOption.contains(Json.number(2)))
    },
    test("setOrFail returns error for missing path") {
      val obj    = Json.Object.empty
      val path   = DynamicOptic.root.field("missing").field("nested")
      val result = obj.setOrFail(path, Json.String("value"))
      assertTrue(result.isLeft)
    }
  )

  // ===========================================================================
  // Merge Suite
  // ===========================================================================
  val mergeSuite: Spec[Any, Nothing] = suite("Merge")(
    test("Auto strategy deep merges objects") {
      val left   = Json.Object("a" -> Json.Object("x" -> Json.number(1)))
      val right  = Json.Object("a" -> Json.Object("y" -> Json.number(2)))
      val merged = left.merge(right, MergeStrategy.Auto)
      merged match {
        case Json.Object(fields) =>
          val a = fields.find(_._1 == "a").map(_._2)
          a match {
            case Some(Json.Object(innerFields)) =>
              assertTrue(innerFields.exists(_._1 == "x")) &&
              assertTrue(innerFields.exists(_._1 == "y"))
            case _ => assertTrue(false)
          }
        case _ => assertTrue(false)
      }
    },
    test("Auto strategy concatenates arrays") {
      val left   = Json.Array(Json.number(1), Json.number(2))
      val right  = Json.Array(Json.number(3))
      val merged = left.merge(right, MergeStrategy.Auto)
      merged match {
        case Json.Array(elems) => assertTrue(elems.size == 3)
        case _                 => assertTrue(false)
      }
    },
    test("Replace strategy replaces entirely") {
      val left   = Json.Object("a" -> Json.number(1))
      val right  = Json.Object("b" -> Json.number(2))
      val merged = left.merge(right, MergeStrategy.Replace)
      assertTrue(merged == right)
    },
    test("Shallow strategy replaces at field level") {
      val left   = Json.Object("a" -> Json.Object("x" -> Json.number(1)))
      val right  = Json.Object("a" -> Json.Object("y" -> Json.number(2)))
      val merged = left.merge(right, MergeStrategy.Shallow)
      merged match {
        case Json.Object(fields) =>
          val a = fields.find(_._1 == "a").map(_._2)
          assertTrue(a.contains(Json.Object(Vector("y" -> Json.number(2)))))
        case _ => assertTrue(false)
      }
    },
    test("Custom strategy applies function") {
      val left     = Json.number(10)
      val right    = Json.number(5)
      val strategy = MergeStrategy.Custom { (_, l, r) =>
        (l, r) match {
          case (Json.Number(a), Json.Number(b)) => Json.number(a.toInt + b.toInt)
          case _                                => r
        }
      }
      val merged = left.merge(right, strategy)
      assertTrue(merged == Json.number(15))
    }
  )

  // ===========================================================================
  // Transformation Suite
  // ===========================================================================
  val transformationSuite: Spec[Any, Nothing] = suite("Transformation")(
    test("transformUp processes children before parents") {
      val json  = Json.Object("value" -> Json.number(1))
      var order = List.empty[String]
      json.transformUp { (path, j) =>
        order = path.toString :: order
        j
      }
      // Children are processed first, so "." (parent) should be last
      assertTrue(order.head == ".")
    },
    test("transformDown processes parents before children") {
      val json  = Json.Object("value" -> Json.number(1))
      var order = List.empty[String]
      json.transformDown { (path, j) =>
        order = path.toString :: order
        j
      }
      // Parents are processed first, so "." (root) should be last (added last = at head when reversing)
      assertTrue(order.last == ".")
    },
    test("transformKeys renames object keys") {
      val json        = Json.Object("old_key" -> Json.String("value"))
      val transformed = json.transformKeys { (_, key) =>
        key.toUpperCase
      }
      transformed match {
        case Json.Object(fields) => assertTrue(fields.head._1 == "OLD_KEY")
        case _                   => assertTrue(false)
      }
    }
  )

  // ===========================================================================
  // Filtering Suite
  // ===========================================================================
  val filteringSuite: Spec[Any, Nothing] = suite("Filtering")(
    test("filter keeps matching entries") {
      val json = Json.Object(
        "a" -> Json.number(1),
        "b" -> Json.number(2),
        "c" -> Json.number(3)
      )
      val filtered = json.filter { (_, j) =>
        j match {
          case Json.Number(n) => n.toInt > 1
          case _              => false
        }
      }
      filtered match {
        case Json.Object(fields) => assertTrue(fields.size == 2)
        case _                   => assertTrue(false)
      }
    },
    test("filterNot removes matching entries") {
      val json     = Json.Object("keep" -> Json.number(1), "remove" -> Json.Null)
      val filtered = json.filterNot((_, j) => j.isNull)
      filtered match {
        case Json.Object(fields) =>
          assertTrue(fields.size == 1) &&
          assertTrue(fields.head._1 == "keep")
        case _ => assertTrue(false)
      }
    },
    test("partition splits by predicate") {
      val json = Json.Object(
        "a" -> Json.number(1),
        "b" -> Json.number(2)
      )
      val (matching, _) = json.partition { (path, _) =>
        path.toString.contains("a")
      }
      matching match {
        case Json.Object(fields) => assertTrue(fields.size == 1)
        case _                   => assertTrue(false)
      }
    }
  )

  // ===========================================================================
  // Normalization Suite
  // ===========================================================================
  val normalizationSuite: Spec[Any, Nothing] = suite("Normalization")(
    test("sortKeys sorts object keys alphabetically") {
      val json   = Json.Object("z" -> Json.number(1), "a" -> Json.number(2), "m" -> Json.number(3))
      val sorted = json.sortKeys
      sorted match {
        case Json.Object(fields) =>
          assertTrue(fields.map(_._1) == Vector("a", "m", "z"))
        case _ => assertTrue(false)
      }
    },
    test("sortKeys is recursive") {
      val json = Json.Object(
        "outer" -> Json.Object("z" -> Json.number(1), "a" -> Json.number(2))
      )
      val sorted = json.sortKeys
      sorted match {
        case Json.Object(fields) =>
          fields.find(_._1 == "outer").map(_._2) match {
            case Some(Json.Object(innerFields)) =>
              assertTrue(innerFields.map(_._1) == Vector("a", "z"))
            case _ => assertTrue(false)
          }
        case _ => assertTrue(false)
      }
    },
    test("dropNulls removes null values from objects") {
      val json    = Json.Object("a" -> Json.number(1), "b" -> Json.Null, "c" -> Json.number(3))
      val dropped = json.dropNulls
      dropped match {
        case Json.Object(fields) =>
          assertTrue(fields.size == 2) &&
          assertTrue(!fields.exists(_._1 == "b"))
        case _ => assertTrue(false)
      }
    },
    test("dropEmpty removes empty objects and arrays") {
      val json = Json.Object(
        "a" -> Json.number(1),
        "b" -> Json.Object.empty,
        "c" -> Json.Array.empty
      )
      val dropped = json.dropEmpty
      dropped match {
        case Json.Object(fields) =>
          assertTrue(fields.size == 1) &&
          assertTrue(fields.head._1 == "a")
        case _ => assertTrue(false)
      }
    },
    test("normalize sorts keys and normalizes numbers") {
      val json       = Json.Object("z" -> Json.Number("1.0"), "a" -> Json.Number("2.00"))
      val normalized = json.normalize
      normalized match {
        case Json.Object(fields) =>
          assertTrue(fields.map(_._1) == Vector("a", "z"))
        case _ => assertTrue(false)
      }
    }
  )

  // ===========================================================================
  // Folding Suite
  // ===========================================================================
  val foldingSuite: Spec[Any, Nothing] = suite("Folding")(
    test("foldDown visits all nodes") {
      val json = Json.Object(
        "a" -> Json.number(1),
        "b" -> Json.Array(Json.number(2), Json.number(3))
      )
      val count = json.foldDown(0)((_, _, acc) => acc + 1)
      assertTrue(count == 5) // root + a + b + 2 + 3
    },
    test("foldUp visits all nodes") {
      val json  = Json.Object("x" -> Json.number(1))
      val count = json.foldUp(0)((_, _, acc) => acc + 1)
      assertTrue(count == 2) // x + root
    },
    test("foldDownOrFail short-circuits on error") {
      val json   = Json.Array(Json.number(1), Json.number(2), Json.number(3))
      val result = json.foldDownOrFail(0) { (_, j, acc) =>
        j match {
          case Json.Number(n) if n == "2" => Left(JsonError("found 2"))
          case _                          => Right(acc + 1)
        }
      }
      assertTrue(result.isLeft)
    }
  )

  // ===========================================================================
  // KV Suite
  // ===========================================================================
  val kvSuite: Spec[Any, Nothing] = suite("KV Representation")(
    test("toKV flattens to path-value pairs") {
      val json = Json.Object(
        "a" -> Json.number(1),
        "b" -> Json.Object("c" -> Json.number(2))
      )
      val kv = json.toKV
      assertTrue(kv.size == 2) // Only leaf values
    },
    test("fromKV assembles from path-value pairs") {
      val path1  = DynamicOptic.root.field("a")
      val path2  = DynamicOptic.root.field("b")
      val kvs    = Seq((path1, Json.number(1)), (path2, Json.number(2)))
      val result = Json.fromKV(kvs)
      assertTrue(result.isRight)
    }
  )

  // ===========================================================================
  // DynamicValue Interop Suite
  // ===========================================================================
  val dynamicValueInteropSuite: Spec[Any, Nothing] = suite("DynamicValue Interop")(
    test("toDynamicValue converts Json.Null to Unit primitive") {
      val dv = Json.Null.toDynamicValue
      dv match {
        case DynamicValue.Primitive(PrimitiveValue.Unit) => assertTrue(true)
        case _                                           => assertTrue(false)
      }
    },
    test("toDynamicValue converts Json.Boolean") {
      val dv = Json.Boolean(true).toDynamicValue
      dv match {
        case DynamicValue.Primitive(PrimitiveValue.Boolean(v)) => assertTrue(v)
        case _                                                 => assertTrue(false)
      }
    },
    test("toDynamicValue converts Json.Number to BigDecimal") {
      val dv = Json.number(42).toDynamicValue
      dv match {
        case DynamicValue.Primitive(PrimitiveValue.BigDecimal(v)) => assertTrue(v == BigDecimal(42))
        case _                                                    => assertTrue(false)
      }
    },
    test("toDynamicValue converts Json.String") {
      val dv = Json.String("test").toDynamicValue
      dv match {
        case DynamicValue.Primitive(PrimitiveValue.String(v)) => assertTrue(v == "test")
        case _                                                => assertTrue(false)
      }
    },
    test("toDynamicValue converts Json.Array to Sequence") {
      val dv = Json.Array(Json.number(1), Json.number(2)).toDynamicValue
      dv match {
        case DynamicValue.Sequence(elems) => assertTrue(elems.size == 2)
        case _                            => assertTrue(false)
      }
    },
    test("toDynamicValue converts Json.Object to Record") {
      val dv = Json.Object("a" -> Json.number(1)).toDynamicValue
      dv match {
        case DynamicValue.Record(fields) => assertTrue(fields.size == 1)
        case _                           => assertTrue(false)
      }
    },
    test("fromDynamicValue converts Primitive.Unit to Null") {
      val json = Json.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Unit))
      assertTrue(json == Json.Null)
    },
    test("fromDynamicValue converts Record to Object") {
      val dv   = DynamicValue.Record(Vector("x" -> DynamicValue.Primitive(PrimitiveValue.Int(5))))
      val json = Json.fromDynamicValue(dv)
      json match {
        case Json.Object(fields) => assertTrue(fields.size == 1)
        case _                   => assertTrue(false)
      }
    },
    test("fromDynamicValue converts Variant with discriminator") {
      val dv   = DynamicValue.Variant("MyCase", DynamicValue.Primitive(PrimitiveValue.String("value")))
      val json = Json.fromDynamicValue(dv)
      json match {
        case Json.Object(fields) =>
          assertTrue(fields.exists(_._1 == "_type")) &&
          assertTrue(fields.exists(_._1 == "_value"))
        case _ => assertTrue(false)
      }
    }
  )

  // ===========================================================================
  // Encoding/Decoding Suite
  // ===========================================================================
  val encodingDecodingSuite: Spec[Any, Nothing] = suite("Encoding/Decoding")(
    test("encode produces valid JSON string") {
      val json    = Json.Object("name" -> Json.String("Alice"), "age" -> Json.number(30))
      val encoded = json.encode
      assertTrue(encoded.contains("\"name\"")) &&
      assertTrue(encoded.contains("\"Alice\"")) &&
      assertTrue(encoded.contains("\"age\"")) &&
      assertTrue(encoded.contains("30"))
    },
    test("parse decodes valid JSON") {
      val input  = """{"name":"Bob","active":true}"""
      val result = Json.parse(input)
      assertTrue(result.isRight)
    },
    test("parse fails on invalid JSON") {
      val input  = """{"name":}"""
      val result = Json.parse(input)
      assertTrue(result.isLeft)
    },
    test("encode/decode roundtrip") {
      val original = Json.Object(
        "string"  -> Json.String("hello"),
        "number"  -> Json.number(42),
        "boolean" -> Json.Boolean(true),
        "null"    -> Json.Null,
        "array"   -> Json.Array(Json.number(1), Json.number(2)),
        "nested"  -> Json.Object("inner" -> Json.String("value"))
      )
      val encoded = original.encode
      val decoded = Json.parse(encoded)
      assertTrue(decoded.toOption.contains(original))
    },
    test("encodeToBytes produces UTF-8 bytes") {
      val json  = Json.String("test")
      val bytes = json.encodeToBytes
      assertTrue(bytes.nonEmpty) &&
      assertTrue(new String(bytes, "UTF-8").contains("test"))
    },
    test("encodeToChunk produces Chunk") {
      val json  = Json.number(123)
      val chunk = json.encodeToChunk
      assertTrue(chunk.length > 0)
    },
    test("pretty printing with indentation") {
      val json   = Json.Object("a" -> Json.number(1))
      val config = WriterConfig.withIndentionStep(2)
      val pretty = json.encode(config)
      assertTrue(pretty.contains("\n"))
    },
    test("parse from byte array") {
      val bytes  = """{"x":1}""".getBytes("UTF-8")
      val result = Json.parse(bytes)
      assertTrue(result.isRight)
    },
    test("toString equals encode") {
      val json = Json.Object("key" -> Json.String("value"))
      assertTrue(json.toString == json.encode)
    }
  )

  // ===========================================================================
  // Comparison Suite
  // ===========================================================================
  val comparisonSuite: Spec[Any, Nothing] = suite("Comparison")(
    test("Null < Boolean < Number < String < Array < Object") {
      assertTrue(Json.Null.compare(Json.Boolean(true)) < 0) &&
      assertTrue(Json.Boolean(true).compare(Json.number(1)) < 0) &&
      assertTrue(Json.number(1).compare(Json.String("a")) < 0) &&
      assertTrue(Json.String("a").compare(Json.Array.empty) < 0) &&
      assertTrue(Json.Array.empty.compare(Json.Object.empty) < 0)
    },
    test("equals compares by value") {
      val a = Json.Object("x" -> Json.number(1))
      val b = Json.Object("x" -> Json.number(1))
      assertTrue(a == b)
    },
    test("equals ignores field order in objects") {
      val a = Json.Object(Vector("a" -> Json.number(1), "b" -> Json.number(2)))
      val b = Json.Object(Vector("b" -> Json.number(2), "a" -> Json.number(1)))
      assertTrue(a == b)
    },
    test("hashCode is consistent with equals") {
      val a = Json.Object("x" -> Json.number(1))
      val b = Json.Object("x" -> Json.number(1))
      assertTrue(a.hashCode == b.hashCode)
    },
    test("ordering is available implicitly") {
      val jsons: List[Json] = List(Json.String("b"), Json.String("a"), Json.String("c"))
      val sorted            = jsons.sorted
      assertTrue(sorted.head == Json.String("a"))
    }
  )

  // ===========================================================================
  // JsonSelection Suite
  // ===========================================================================
  val jsonSelectionSuite: Spec[Any, Nothing] = suite("JsonSelection")(
    test("map transforms all values") {
      val selection = JsonSelection.fromVector(Vector(Json.number(1), Json.number(2)))
      val mapped    = selection.map {
        case Json.Number(n) => Json.number(n.toInt * 2)
        case other          => other
      }
      mapped.toEither match {
        case Right(values) =>
          assertTrue(values.contains(Json.number(2))) &&
          assertTrue(values.contains(Json.number(4)))
        case _ => assertTrue(false)
      }
    },
    test("filter removes non-matching values") {
      val selection = JsonSelection.fromVector(Vector(Json.number(1), Json.String("x"), Json.number(2)))
      val filtered  = selection.numbers
      filtered.toEither match {
        case Right(values) => assertTrue(values.size == 2)
        case _             => assertTrue(false)
      }
    },
    test("flatMap chains selections") {
      val json = Json.Object(
        "items" -> Json.Array(
          Json.Object("name" -> Json.String("a")),
          Json.Object("name" -> Json.String("b"))
        )
      )
      val selection = json("items").flatMap {
        case Json.Array(elems) => JsonSelection.fromVector(elems).flatMap(_.apply("name"))
        case _                 => JsonSelection.empty
      }
      selection.toEither match {
        case Right(values) => assertTrue(values.size == 2)
        case _             => assertTrue(false)
      }
    },
    test("one returns single value") {
      val selection = JsonSelection(Json.String("single"))
      assertTrue(selection.one.toOption.contains(Json.String("single")))
    },
    test("first returns first value") {
      val selection = JsonSelection.fromVector(Vector(Json.number(1), Json.number(2)))
      assertTrue(selection.first.toOption.contains(Json.number(1)))
    },
    test("toArray wraps in array") {
      val selection = JsonSelection.fromVector(Vector(Json.number(1), Json.number(2)))
      selection.toArray match {
        case Right(Json.Array(elems)) => assertTrue(elems.size == 2)
        case _                        => assertTrue(false)
      }
    },
    test("++ combines selections") {
      val s1       = JsonSelection(Json.number(1))
      val s2       = JsonSelection(Json.number(2))
      val combined = s1 ++ s2
      combined.toEither match {
        case Right(values) => assertTrue(values.size == 2)
        case _             => assertTrue(false)
      }
    },
    test("empty selection") {
      assertTrue(JsonSelection.empty.isEmpty) &&
      assertTrue(JsonSelection.empty.size == 0)
    }
  )

  // ===========================================================================
  // JsonError Suite
  // ===========================================================================
  val jsonErrorSuite: Spec[Any, Nothing] = suite("JsonError")(
    test("simple error creation") {
      val error = JsonError("test error")
      assertTrue(error.message == "test error") &&
      assertTrue(error.path == DynamicOptic.root)
    },
    test("error with path") {
      val path  = DynamicOptic.root.field("user")
      val error = JsonError("not found", path)
      assertTrue(error.getMessage.contains("at path"))
    },
    test("error with position info") {
      val error = JsonError("parse error", DynamicOptic.root, Some(42L), Some(3), Some(10))
      assertTrue(error.getMessage.contains("line 3")) &&
      assertTrue(error.getMessage.contains("column 10"))
    },
    test("error ++ combines messages") {
      val e1       = JsonError("error 1")
      val e2       = JsonError("error 2")
      val combined = e1 ++ e2
      assertTrue(combined.message.contains("error 1")) &&
      assertTrue(combined.message.contains("error 2"))
    },
    test("fromSchemaError converts") {
      val schemaError = SchemaError.expectationMismatch(Nil, "schema error")
      val jsonError   = JsonError.fromSchemaError(schemaError)
      assertTrue(jsonError.message.nonEmpty)
    }
  )

  // ===========================================================================
  // JsonDecoder/Encoder Suite
  // ===========================================================================
  val jsonDecoderEncoderSuite: Spec[Any, Nothing] = suite("JsonDecoder/Encoder")(
    test("from[A] encodes value to Json") {
      case class Person(name: String, age: Int)
      implicit val schema: Schema[Person] = Schema.derived

      val person = Person("Alice", 30)
      val json   = Json.from(person)

      assertTrue(json.isObject)
    },
    test("as[A] decodes Json to value") {
      case class Simple(value: Int)
      implicit val schema: Schema[Simple] = Schema.derived

      val json   = Json.Object("value" -> Json.number(42))
      val result = json.as[Simple]

      result match {
        case Right(Simple(v)) => assertTrue(v == 42)
        case Left(_)          => assertTrue(false)
      }
    }
  )
}
