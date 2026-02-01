package zio.blocks.schema

import zio.blocks.chunk.Chunk
import zio.blocks.schema.json._
import zio.blocks.schema.patch._
import zio.test._

import java.nio.ByteBuffer

object CoverageEnhancementSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("CoverageEnhancementSpec")(
    suite("Json path operations")(
      test("get with DynamicOptic for non-existent path") {
        val json = Json.Object("a" -> Json.Number(1))
        val path = DynamicOptic.root.field("nonexistent")
        assertTrue(json.get(path).isEmpty)
      },
      test("modifyAtPath returns unchanged when path doesn't exist in array") {
        val json     = Json.Array(Json.Number(1), Json.Number(2))
        val path     = DynamicOptic.root.at(10)
        val modified = json.modify(path)(_ => Json.Number(99))
        assertTrue(modified == json)
      },
      test("modifyAtPath returns unchanged when path doesn't exist in object") {
        val json     = Json.Object("a" -> Json.Number(1))
        val path     = DynamicOptic.root.field("nonexistent")
        val modified = json.modify(path)(_ => Json.Number(99))
        assertTrue(modified == json)
      },
      test("modifyOrFail fails when path doesn't exist") {
        val json   = Json.Object("a" -> Json.Number(1))
        val path   = DynamicOptic.root.field("nonexistent")
        val result = json.modifyOrFail(path) { case j => j }
        assertTrue(result.isLeft)
      },
      test("modifyOrFail fails when partial function not defined") {
        val json   = Json.Object("a" -> Json.Number(1))
        val path   = DynamicOptic.root.field("a")
        val result = json.modifyOrFail(path) { case Json.String(_) => Json.String("modified") }
        assertTrue(result.isLeft)
      },
      test("insertAtPath succeeds for new field") {
        val json     = Json.Object("a" -> Json.Number(1))
        val path     = DynamicOptic.root.field("b")
        val inserted = json.insert(path, Json.Number(2))
        assertTrue(inserted == Json.Object("a" -> Json.Number(1), "b" -> Json.Number(2)))
      },
      test("insertAtPath returns unchanged when path exists") {
        val json     = Json.Object("a" -> Json.Number(1))
        val path     = DynamicOptic.root.field("a")
        val inserted = json.insert(path, Json.Number(99))
        assertTrue(inserted == json)
      },
      test("insertOrFail fails when path exists") {
        val json   = Json.Object("a" -> Json.Number(1))
        val path   = DynamicOptic.root.field("a")
        val result = json.insertOrFail(path, Json.Number(99))
        assertTrue(result.isLeft)
      },
      test("insertOrFail fails for deeply nested non-existent parent") {
        val json   = Json.Object("a" -> Json.Number(1))
        val path   = DynamicOptic.root.field("nonexistent").field("child")
        val result = json.insertOrFail(path, Json.Number(99))
        assertTrue(result.isLeft)
      },
      test("printTo writes to ByteBuffer") {
        val json   = Json.Object("a" -> Json.Number(1))
        val buffer = ByteBuffer.allocate(1024)
        json.printTo(buffer)
        buffer.flip()
        val bytes = new Array[Byte](buffer.remaining())
        buffer.get(bytes)
        val str = new String(bytes, "UTF-8")
        assertTrue(str.contains("\"a\""))
      },
      test("printTo with config writes to ByteBuffer") {
        val json   = Json.Object("a" -> Json.Number(1))
        val buffer = ByteBuffer.allocate(1024)
        json.printTo(buffer, WriterConfig.withIndentionStep2)
        buffer.flip()
        val bytes = new Array[Byte](buffer.remaining())
        buffer.get(bytes)
        val str = new String(bytes, "UTF-8")
        assertTrue(str.contains("\"a\""))
      }
    ),
    suite("Json parse error branches")(
      test("parse handles invalid JSON") {
        val result = Json.parse("{invalid json}")
        assertTrue(result.isLeft)
      },
      test("parse handles truncated JSON") {
        val result = Json.parse("{\"a\": ")
        assertTrue(result.isLeft)
      },
      test("parse handles empty input") {
        val result = Json.parse("")
        assertTrue(result.isLeft)
      },
      test("parse with config handles invalid JSON") {
        val result = Json.parse("{broken", ReaderConfig)
        assertTrue(result.isLeft)
      },
      test("parseUnsafe throws on invalid JSON") {
        val thrown = try {
          Json.parseUnsafe("{invalid}")
          false
        } catch {
          case _: SchemaError => true
          case _: Throwable   => false
        }
        assertTrue(thrown)
      }
    ),
    suite("SchemaError coverage")(
      test("remapSource for all error types") {
        val conversionFailed = SchemaError.conversionFailed(Nil, "test")
        val missingField     = SchemaError.missingField(Nil, "field")
        val duplicatedField  = SchemaError.duplicatedField(Nil, "field")
        val expectation      = SchemaError.expectationMismatch(Nil, "expected X")
        val unknownCase      = SchemaError.unknownCase(Nil, "case")
        val message          = SchemaError.message("test message")

        val convMapped    = conversionFailed.atField("x")
        val missingMapped = missingField.atField("x")
        val dupMapped     = duplicatedField.atField("x")
        val expectMapped  = expectation.atField("x")
        val unknownMapped = unknownCase.atField("x")
        val messageMapped = message.atField("x")

        assertTrue(
          convMapped.errors.head.source.nodes.nonEmpty,
          missingMapped.errors.head.source.nodes.nonEmpty,
          dupMapped.errors.head.source.nodes.nonEmpty,
          expectMapped.errors.head.source.nodes.nonEmpty,
          unknownMapped.errors.head.source.nodes.nonEmpty,
          messageMapped.errors.head.source.nodes.nonEmpty
        )
      },
      test("atIndex adds index to path") {
        val err     = SchemaError.conversionFailed(Nil, "test")
        val indexed = err.atIndex(5)
        assertTrue(indexed.errors.head.source.nodes.contains(DynamicOptic.Node.AtIndex(5)))
      },
      test("atCase adds case to path") {
        val err   = SchemaError.conversionFailed(Nil, "test")
        val cased = err.atCase("MyCase")
        assertTrue(cased.errors.head.source.nodes.contains(DynamicOptic.Node.Case("MyCase")))
      },
      test("atKey adds key to path") {
        val err   = SchemaError.conversionFailed(Nil, "test")
        val key   = DynamicValue.string("myKey")
        val keyed = err.atKey(key)
        assertTrue(keyed.errors.head.source.nodes.nonEmpty)
      },
      test("ConversionFailed with cause formats correctly") {
        val inner = SchemaError.conversionFailed(Nil, "inner error")
        val outer = SchemaError.conversionFailed("outer context", inner)
        assertTrue(
          outer.message.contains("outer context"),
          outer.message.contains("inner error")
        )
      },
      test("SchemaError aggregation with ++") {
        val err1   = SchemaError.conversionFailed(Nil, "error 1")
        val err2   = SchemaError.conversionFailed(Nil, "error 2")
        val merged = err1 ++ err2
        assertTrue(
          merged.message.contains("error 1"),
          merged.message.contains("error 2")
        )
      }
    ),
    suite("JsonSelection error handling")(
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
    suite("DynamicPatch error branches")(
      test("patch fails when field not found in strict mode") {
        val value  = DynamicValue.Record(Chunk(("a", DynamicValue.int(1))))
        val op     = Patch.Operation.Set(DynamicValue.int(99))
        val path   = DynamicOptic.root.field("nonexistent")
        val patch  = DynamicPatch(Vector(DynamicPatch.DynamicPatchOp(path, op)))
        val result = patch(value, PatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("patch skips operation when field not found in lenient mode") {
        val value  = DynamicValue.Record(Chunk(("a", DynamicValue.int(1))))
        val op     = Patch.Operation.Set(DynamicValue.int(99))
        val path   = DynamicOptic.root.field("nonexistent")
        val patch  = DynamicPatch(Vector(DynamicPatch.DynamicPatchOp(path, op)))
        val result = patch(value, PatchMode.Lenient)
        assertTrue(result == Right(value))
      },
      test("patch fails on index out of bounds") {
        val value  = DynamicValue.Sequence(Chunk(DynamicValue.int(1), DynamicValue.int(2)))
        val op     = Patch.Operation.Set(DynamicValue.int(99))
        val path   = DynamicOptic.root.at(10)
        val patch  = DynamicPatch(Vector(DynamicPatch.DynamicPatchOp(path, op)))
        val result = patch(value, PatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("patch fails on type mismatch - expected Record got Sequence") {
        val value  = DynamicValue.Sequence(Chunk(DynamicValue.int(1)))
        val op     = Patch.Operation.Set(DynamicValue.int(99))
        val path   = DynamicOptic.root.field("a")
        val patch  = DynamicPatch(Vector(DynamicPatch.DynamicPatchOp(path, op)))
        val result = patch(value, PatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("patch fails on type mismatch - expected Sequence got Record") {
        val value  = DynamicValue.Record(Chunk(("a", DynamicValue.int(1))))
        val op     = Patch.Operation.Set(DynamicValue.int(99))
        val path   = DynamicOptic.root.at(0)
        val patch  = DynamicPatch(Vector(DynamicPatch.DynamicPatchOp(path, op)))
        val result = patch(value, PatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("patch fails on type mismatch - expected Map got Record") {
        val value  = DynamicValue.Record(Chunk(("a", DynamicValue.int(1))))
        val op     = Patch.Operation.Set(DynamicValue.int(99))
        val path   = DynamicOptic.root.atKey(DynamicValue.string("key"))
        val patch  = DynamicPatch(Vector(DynamicPatch.DynamicPatchOp(path, op)))
        val result = patch(value, PatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("patch fails on map key not found") {
        val value  = DynamicValue.Map(Chunk((DynamicValue.string("a"), DynamicValue.int(1))))
        val op     = Patch.Operation.Set(DynamicValue.int(99))
        val path   = DynamicOptic.root.atKey(DynamicValue.string("nonexistent"))
        val patch  = DynamicPatch(Vector(DynamicPatch.DynamicPatchOp(path, op)))
        val result = patch(value, PatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("patch fails on variant case mismatch") {
        val value  = DynamicValue.Variant("CaseA", DynamicValue.int(1))
        val op     = Patch.Operation.Set(DynamicValue.int(99))
        val path   = DynamicOptic.root.caseOf("CaseB")
        val patch  = DynamicPatch(Vector(DynamicPatch.DynamicPatchOp(path, op)))
        val result = patch(value, PatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("patch fails on expected Variant got Record") {
        val value  = DynamicValue.Record(Chunk(("a", DynamicValue.int(1))))
        val op     = Patch.Operation.Set(DynamicValue.int(99))
        val path   = DynamicOptic.root.caseOf("SomeCase")
        val patch  = DynamicPatch(Vector(DynamicPatch.DynamicPatchOp(path, op)))
        val result = patch(value, PatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("patch fails on empty sequence with Elements path in strict mode") {
        val value  = DynamicValue.Sequence(Chunk.empty)
        val op     = Patch.Operation.Set(DynamicValue.int(99))
        val path   = DynamicOptic.root.elements
        val patch  = DynamicPatch(Vector(DynamicPatch.DynamicPatchOp(path, op)))
        val result = patch(value, PatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("patch succeeds on empty sequence with Elements path in lenient mode") {
        val value  = DynamicValue.Sequence(Chunk.empty)
        val op     = Patch.Operation.Set(DynamicValue.int(99))
        val path   = DynamicOptic.root.elements
        val patch  = DynamicPatch(Vector(DynamicPatch.DynamicPatchOp(path, op)))
        val result = patch(value, PatchMode.Lenient)
        assertTrue(result == Right(value))
      },
      test("patch fails on AtIndices not supported") {
        val value  = DynamicValue.Sequence(Chunk(DynamicValue.int(1), DynamicValue.int(2)))
        val op     = Patch.Operation.Set(DynamicValue.int(99))
        val path   = DynamicOptic.root.atIndices(0, 1)
        val patch  = DynamicPatch(Vector(DynamicPatch.DynamicPatchOp(path, op)))
        val result = patch(value, PatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("patch fails on AtMapKeys not supported") {
        val value  = DynamicValue.Map(Chunk((DynamicValue.string("a"), DynamicValue.int(1))))
        val op     = Patch.Operation.Set(DynamicValue.int(99))
        val path   = DynamicOptic.root.atKeys(DynamicValue.string("a"))
        val patch  = DynamicPatch(Vector(DynamicPatch.DynamicPatchOp(path, op)))
        val result = patch(value, PatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("patch fails on MapKeys not supported") {
        val value  = DynamicValue.Map(Chunk((DynamicValue.string("a"), DynamicValue.int(1))))
        val op     = Patch.Operation.Set(DynamicValue.int(99))
        val path   = DynamicOptic.root.mapKeys
        val patch  = DynamicPatch(Vector(DynamicPatch.DynamicPatchOp(path, op)))
        val result = patch(value, PatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("patch fails on MapValues not supported") {
        val value  = DynamicValue.Map(Chunk((DynamicValue.string("a"), DynamicValue.int(1))))
        val op     = Patch.Operation.Set(DynamicValue.int(99))
        val path   = DynamicOptic.root.mapValues
        val patch  = DynamicPatch(Vector(DynamicPatch.DynamicPatchOp(path, op)))
        val result = patch(value, PatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("Wrapped path passes through") {
        val value  = DynamicValue.int(42)
        val op     = Patch.Operation.Set(DynamicValue.int(99))
        val path   = new DynamicOptic(IndexedSeq(DynamicOptic.Node.Wrapped))
        val patch  = DynamicPatch(Vector(DynamicPatch.DynamicPatchOp(path, op)))
        val result = patch(value, PatchMode.Strict)
        assertTrue(result == Right(DynamicValue.int(99)))
      }
    ),
    suite("applyPrimitiveDelta branches")(
      test("IntDelta on non-primitive fails") {
        val value  = DynamicValue.Sequence(Chunk(DynamicValue.int(1)))
        val op     = Patch.Operation.PrimitiveDelta(Patch.PrimitiveOp.IntDelta(5))
        val patch  = DynamicPatch.root(op)
        val result = patch(value, PatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("IntDelta on wrong primitive type fails") {
        val value  = DynamicValue.string("hello")
        val op     = Patch.Operation.PrimitiveDelta(Patch.PrimitiveOp.IntDelta(5))
        val patch  = DynamicPatch.root(op)
        val result = patch(value, PatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("StringEdit Delete out of bounds fails") {
        val value = DynamicValue.string("hello")
        val op    = Patch.Operation.PrimitiveDelta(
          Patch.PrimitiveOp.StringEdit(Vector(Patch.StringOp.Delete(10, 5)))
        )
        val patch  = DynamicPatch.root(op)
        val result = patch(value, PatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("StringEdit Insert out of bounds fails") {
        val value = DynamicValue.string("hello")
        val op    = Patch.Operation.PrimitiveDelta(
          Patch.PrimitiveOp.StringEdit(Vector(Patch.StringOp.Insert(100, "text")))
        )
        val patch  = DynamicPatch.root(op)
        val result = patch(value, PatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("StringEdit Modify out of bounds fails") {
        val value = DynamicValue.string("hello")
        val op    = Patch.Operation.PrimitiveDelta(
          Patch.PrimitiveOp.StringEdit(Vector(Patch.StringOp.Modify(10, 5, "new")))
        )
        val patch  = DynamicPatch.root(op)
        val result = patch(value, PatchMode.Strict)
        assertTrue(result.isLeft)
      }
    ),
    suite("JsonSchema withNullable branches")(
      test("withNullable on True returns True") {
        assertTrue(JsonSchema.True.withNullable == JsonSchema.True)
      },
      test("withNullable on False returns Null schema") {
        val result = JsonSchema.False.withNullable
        assertTrue(result match {
          case obj: JsonSchema.Object =>
            obj.`type`.exists {
              case SchemaType.Single(t) => t == JsonSchemaType.Null
              case _                    => false
            }
          case _ => false
        })
      },
      test("withNullable on schema already with Null type returns unchanged") {
        val schema = JsonSchema.ofType(JsonSchemaType.Null)
        assertTrue(schema.withNullable == schema)
      },
      test("withNullable on single non-null type adds Null to union") {
        val schema = JsonSchema.ofType(JsonSchemaType.String)
        val result = schema.withNullable
        assertTrue(result match {
          case obj: JsonSchema.Object =>
            obj.`type`.exists {
              case SchemaType.Union(ts) => ts.contains(JsonSchemaType.Null) && ts.contains(JsonSchemaType.String)
              case _                    => false
            }
          case _ => false
        })
      },
      test("withNullable on union already with Null returns unchanged") {
        val schema =
          JsonSchema.Object(`type` = Some(SchemaType.Union(new ::(JsonSchemaType.Null, JsonSchemaType.String :: Nil))))
        assertTrue(schema.withNullable == schema)
      },
      test("withNullable on union without Null adds Null") {
        val schema = JsonSchema.Object(`type` =
          Some(SchemaType.Union(new ::(JsonSchemaType.String, JsonSchemaType.Integer :: Nil)))
        )
        val result = schema.withNullable
        assertTrue(result match {
          case obj: JsonSchema.Object =>
            obj.`type`.exists {
              case SchemaType.Union(ts) =>
                ts.contains(JsonSchemaType.Null) &&
                ts.contains(JsonSchemaType.String) &&
                ts.contains(JsonSchemaType.Integer)
              case _ => false
            }
          case _ => false
        })
      },
      test("withNullable on schema with no type uses anyOf") {
        val schema = JsonSchema.Object(minLength = Some(NonNegativeInt.one))
        val result = schema.withNullable
        assertTrue(result match {
          case obj: JsonSchema.Object => obj.anyOf.isDefined
          case _                      => false
        })
      }
    ),
    suite("FormatValidator via schema validation")(
      test("uri-reference validation succeeds for valid reference") {
        val schema = JsonSchema.string(format = Some("uri-reference"))
        assertTrue(schema.conforms(Json.String("/path/to/resource"), ValidationOptions.formatAssertion))
      },
      test("uri-reference validation fails for invalid reference") {
        val schema = JsonSchema.string(format = Some("uri-reference"))
        assertTrue(!schema.conforms(Json.String("://invalid"), ValidationOptions.formatAssertion))
      },
      test("uri-reference validation with relative path") {
        val schema = JsonSchema.string(format = Some("uri-reference"))
        assertTrue(schema.conforms(Json.String("relative/path"), ValidationOptions.formatAssertion))
      },
      test("uri-reference validation with fragment") {
        val schema = JsonSchema.string(format = Some("uri-reference"))
        assertTrue(schema.conforms(Json.String("#fragment"), ValidationOptions.formatAssertion))
      }
    ),
    suite("Patch[S] failure branches")(
      test("apply returns original on internal failure") {
        case class Person(name: String, age: Int)
        implicit val personSchema: Schema[Person] = Schema.derived

        val person = Person("John", 30)
        val dp     = DynamicPatch(
          Vector(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("nonexistent"),
              Patch.Operation.Set(DynamicValue.int(99))
            )
          )
        )
        val patch  = new Patch[Person](dp, personSchema)
        val result = patch(person)
        assertTrue(result == person)
      },
      test("applyOption returns None on failure") {
        case class Person(name: String, age: Int)
        implicit val personSchema: Schema[Person] = Schema.derived

        val person = Person("John", 30)
        val dp     = DynamicPatch(
          Vector(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("nonexistent"),
              Patch.Operation.Set(DynamicValue.int(99))
            )
          )
        )
        val patch  = new Patch[Person](dp, personSchema)
        val result = patch.applyOption(person)
        assertTrue(result.isEmpty)
      },
      test("apply with mode returns Left on failure") {
        case class Person(name: String, age: Int)
        implicit val personSchema: Schema[Person] = Schema.derived

        val person = Person("John", 30)
        val dp     = DynamicPatch(
          Vector(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("nonexistent"),
              Patch.Operation.Set(DynamicValue.int(99))
            )
          )
        )
        val patch  = new Patch[Person](dp, personSchema)
        val result = patch(person, PatchMode.Strict)
        assertTrue(result.isLeft)
      }
    ),
    suite("DynamicValue.fromKV")(
      test("fromKV with empty sequence returns empty record") {
        val result = DynamicValue.fromKV(Seq.empty)
        assertTrue(result == Right(DynamicValue.Record.empty))
      }
    ),
    suite("Json get on primitives")(
      test("get field on non-object returns error selection") {
        val json      = Json.Number(42)
        val selection = json.get("field")
        assertTrue(selection.isFailure)
      },
      test("get index on non-array returns error selection") {
        val json      = Json.String("hello")
        val selection = json.get(0)
        assertTrue(selection.isFailure)
      }
    ),
    suite("JsonReader error branches")(
      test("parseString fails on non-string") {
        val json   = Json.Number(42)
        val result = json.as[String]
        assertTrue(result.isLeft)
      },
      test("parseInt fails on non-number") {
        val json   = Json.String("hello")
        val result = json.as[Int]
        assertTrue(result.isLeft)
      }
    ),
    suite("DynamicPatch Clobber mode")(
      test("clobber mode propagates errors for missing fields") {
        val value  = DynamicValue.Record(Chunk(("a", DynamicValue.int(1))))
        val op     = Patch.Operation.Set(DynamicValue.int(99))
        val path   = DynamicOptic.root.field("nonexistent")
        val patch  = DynamicPatch(Vector(DynamicPatch.DynamicPatchOp(path, op)))
        val result = patch(value, PatchMode.Clobber)
        assertTrue(result == Right(value))
      }
    ),
    suite("Json insertAtPathOrFail additional branches")(
      test("insertAtPathOrFail into nested object succeeds") {
        val json   = Json.Object("outer" -> Json.Object("inner" -> Json.Number(1)))
        val path   = DynamicOptic.root.field("outer").field("newField")
        val result = json.insertOrFail(path, Json.Number(99))
        assertTrue(result.isRight)
      },
      test("insertAtPathOrFail into nested array succeeds") {
        val json   = Json.Object("arr" -> Json.Array(Json.Number(1), Json.Number(2)))
        val path   = DynamicOptic.root.field("arr").at(2)
        val result = json.insertOrFail(path, Json.Number(3))
        assertTrue(result.isRight)
      },
      test("insertAtPathOrFail at array end extends array") {
        val json   = Json.Array(Json.Number(1))
        val path   = DynamicOptic.root.at(1)
        val result = json.insertOrFail(path, Json.Number(2))
        assertTrue(result.isRight)
      },
      test("insertAtPathOrFail fails when nested field exists") {
        val json   = Json.Object("outer" -> Json.Object("inner" -> Json.Number(1)))
        val path   = DynamicOptic.root.field("outer").field("inner")
        val result = json.insertOrFail(path, Json.Number(99))
        assertTrue(result.isLeft)
      },
      test("insertAtPathOrFail fails when object is not an object") {
        val json   = Json.Object("a" -> Json.Number(1))
        val path   = DynamicOptic.root.field("a").field("b")
        val result = json.insertOrFail(path, Json.Number(99))
        assertTrue(result.isLeft)
      }
    ),
    suite("Json fold operations additional coverage")(
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
        val path   = DynamicOptic.root.field("outer").field("inner")
        val result = json.modifyOrFail(path) { case Json.Number(_) => Json.Number(99) }
        assertTrue(result == Right(Json.Object("outer" -> Json.Object("inner" -> Json.Number(99)))))
      },
      test("modifyAtPathOrFail with nested array path succeeds") {
        val json   = Json.Object("arr" -> Json.Array(Json.Number(1), Json.Number(2)))
        val path   = DynamicOptic.root.field("arr").at(0)
        val result = json.modifyOrFail(path) { case Json.Number(_) => Json.Number(99) }
        assertTrue(result == Right(Json.Object("arr" -> Json.Array(Json.Number(99), Json.Number(2)))))
      },
      test("modifyAtPathOrFail fails when inner path not found") {
        val json   = Json.Object("outer" -> Json.Object("inner" -> Json.Number(1)))
        val path   = DynamicOptic.root.field("outer").field("nonexistent")
        val result = json.modifyOrFail(path) { case j => j }
        assertTrue(result.isLeft)
      },
      test("modifyAtPathOrFail on elements path") {
        val json   = Json.Array(Json.Number(1), Json.Number(2))
        val path   = DynamicOptic.root.elements
        val result = json.modifyOrFail(path) { case Json.Number(n) => Json.Number(BigDecimal(n.toInt * 10)) }
        assertTrue(result == Right(Json.Array(Json.Number(10), Json.Number(20))))
      },
      test("modifyAtPathOrFail on mapValues path") {
        val json   = Json.Object("a" -> Json.Number(1), "b" -> Json.Number(2))
        val path   = DynamicOptic.root.mapValues
        val result = json.modifyOrFail(path) { case Json.Number(n) => Json.Number(BigDecimal(n.toInt * 10)) }
        assertTrue(result.isRight)
      }
    ),
    suite("Json fromKV edge cases")(
      test("fromKVUnsafe with single path creates structure") {
        val kv     = Seq((DynamicOptic.root.field("a"), Json.Number(1)))
        val result = Json.fromKVUnsafe(kv)
        assertTrue(result == Json.Object("a" -> Json.Number(1)))
      },
      test("fromKVUnsafe with multiple paths") {
        val kv = Seq(
          (DynamicOptic.root.field("a"), Json.Number(1)),
          (DynamicOptic.root.field("b"), Json.Number(2))
        )
        val result = Json.fromKVUnsafe(kv)
        assertTrue(result.get("a").one == Right(Json.Number(1)))
        assertTrue(result.get("b").one == Right(Json.Number(2)))
      }
    ),
    suite("ConversionFailed message formatting")(
      test("ConversionFailed message with empty cause shows details") {
        val err = SchemaError.conversionFailed(Nil, "conversion failed")
        assertTrue(err.message.contains("conversion failed"))
      },
      test("ConversionFailed message with multiple errors shows all") {
        val err1   = SchemaError.conversionFailed(Nil, "error 1")
        val err2   = SchemaError.conversionFailed(Nil, "error 2")
        val err3   = SchemaError.conversionFailed(Nil, "error 3")
        val merged = err1 ++ err2 ++ err3
        assertTrue(
          merged.message.contains("error 1"),
          merged.message.contains("error 2"),
          merged.message.contains("error 3")
        )
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
    suite("Nested path operations in patches")(
      test("nested field navigation with isLast=false") {
        val value = DynamicValue.Record(
          Chunk(
            ("outer", DynamicValue.Record(Chunk(("inner", DynamicValue.int(1)))))
          )
        )
        val op     = Patch.Operation.Set(DynamicValue.int(99))
        val path   = DynamicOptic.root.field("outer").field("inner")
        val patch  = DynamicPatch(Vector(DynamicPatch.DynamicPatchOp(path, op)))
        val result = patch(value, PatchMode.Strict)
        assertTrue(result.isRight)
      },
      test("nested index navigation with isLast=false") {
        val value = DynamicValue.Sequence(
          Chunk(
            DynamicValue.Record(Chunk(("a", DynamicValue.int(1))))
          )
        )
        val op     = Patch.Operation.Set(DynamicValue.int(99))
        val path   = DynamicOptic.root.at(0).field("a")
        val patch  = DynamicPatch(Vector(DynamicPatch.DynamicPatchOp(path, op)))
        val result = patch(value, PatchMode.Strict)
        assertTrue(result.isRight)
      },
      test("nested map key navigation with isLast=false") {
        val value = DynamicValue.Map(
          Chunk(
            (DynamicValue.string("key"), DynamicValue.Record(Chunk(("a", DynamicValue.int(1)))))
          )
        )
        val op     = Patch.Operation.Set(DynamicValue.int(99))
        val path   = DynamicOptic.root.atKey(DynamicValue.string("key")).field("a")
        val patch  = DynamicPatch(Vector(DynamicPatch.DynamicPatchOp(path, op)))
        val result = patch(value, PatchMode.Strict)
        assertTrue(result.isRight)
      },
      test("nested variant case navigation with isLast=false") {
        val value  = DynamicValue.Variant("MyCase", DynamicValue.Record(Chunk(("a", DynamicValue.int(1)))))
        val op     = Patch.Operation.Set(DynamicValue.int(99))
        val path   = DynamicOptic.root.caseOf("MyCase").field("a")
        val patch  = DynamicPatch(Vector(DynamicPatch.DynamicPatchOp(path, op)))
        val result = patch(value, PatchMode.Strict)
        assertTrue(result.isRight)
      },
      test("nested Wrapped path with isLast=false") {
        val value  = DynamicValue.Record(Chunk(("a", DynamicValue.int(1))))
        val op     = Patch.Operation.Set(DynamicValue.int(99))
        val path   = new DynamicOptic(IndexedSeq(DynamicOptic.Node.Wrapped, DynamicOptic.Node.Field("a")))
        val patch  = DynamicPatch(Vector(DynamicPatch.DynamicPatchOp(path, op)))
        val result = patch(value, PatchMode.Strict)
        assertTrue(result.isRight)
      },
      test("Elements path applies to all elements with isLast=false") {
        val value = DynamicValue.Sequence(
          Chunk(
            DynamicValue.Record(Chunk(("a", DynamicValue.int(1)))),
            DynamicValue.Record(Chunk(("a", DynamicValue.int(2))))
          )
        )
        val op     = Patch.Operation.Set(DynamicValue.int(99))
        val path   = DynamicOptic.root.elements.field("a")
        val patch  = DynamicPatch(Vector(DynamicPatch.DynamicPatchOp(path, op)))
        val result = patch(value, PatchMode.Strict)
        assertTrue(result.isRight)
      }
    )
  )
}
