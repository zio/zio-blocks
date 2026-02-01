package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.test._

/**
 * Tests for MigrationAction serialization and path parsing.
 *
 * Covers all path parsing branches and action encode/decode roundtrips.
 */
object MigrationActionParsingSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment, Any] = suite("MigrationActionParsingSpec")(
    suite("Path parsing - field access")(
      test("parses simple field path") {
        val encoded = encodePath(DynamicOptic.root.field("name"))
        val decoded = decodePath(encoded)
        assertTrue(decoded == Right(DynamicOptic.root.field("name")))
      },
      test("parses nested field path") {
        val path    = DynamicOptic.root.field("address").field("city")
        val encoded = encodePath(path)
        val decoded = decodePath(encoded)
        assertTrue(decoded == Right(path))
      },
      test("parses deeply nested fields") {
        val path    = DynamicOptic.root.field("a").field("b").field("c").field("d")
        val encoded = encodePath(path)
        val decoded = decodePath(encoded)
        assertTrue(decoded == Right(path))
      },
      test("rejects empty field name") {
        val result = decodePath("..name")
        assertTrue(result.isLeft)
      }
    ),
    suite("Path parsing - case selection")(
      test("parses when clause") {
        val path    = DynamicOptic.root.caseOf("Active")
        val encoded = encodePath(path)
        val decoded = decodePath(encoded)
        assertTrue(decoded == Right(path))
      },
      test("parses when clause with nested field") {
        val path    = DynamicOptic.root.caseOf("Pending").field("reason")
        val encoded = encodePath(path)
        val decoded = decodePath(encoded)
        assertTrue(decoded == Right(path))
      },
      test("rejects malformed when clause - missing bracket") {
        val result = decodePath(".when[Active")
        assertTrue(result.isLeft)
        assertTrue(result.left.exists(_.contains("Malformed")))
      }
    ),
    suite("Path parsing - collection traversal")(
      test("parses each for sequences") {
        val path    = DynamicOptic.root.field("items").elements
        val encoded = encodePath(path)
        val decoded = decodePath(encoded)
        assertTrue(decoded == Right(path))
      },
      test("parses eachKey for maps") {
        val path    = DynamicOptic.root.field("settings").mapKeys
        val encoded = encodePath(path)
        val decoded = decodePath(encoded)
        assertTrue(decoded == Right(path))
      },
      test("parses eachValue for maps") {
        val path    = DynamicOptic.root.field("settings").mapValues
        val encoded = encodePath(path)
        val decoded = decodePath(encoded)
        assertTrue(decoded == Right(path))
      }
    ),
    suite("Path parsing - indexing")(
      test("parses index access") {
        val path    = DynamicOptic.root.field("items").at(0)
        val encoded = encodePath(path)
        val decoded = decodePath(encoded)
        assertTrue(decoded == Right(path))
      },
      test("parses multiple indices") {
        val path    = DynamicOptic.root.field("matrix").at(1).at(2)
        val encoded = encodePath(path)
        val decoded = decodePath(encoded)
        assertTrue(decoded == Right(path))
      },
      test("rejects invalid index - non-numeric") {
        val result = decodePath(".items.at(abc)")
        assertTrue(result.isLeft)
        assertTrue(result.left.exists(_.contains("Invalid index")))
      }
    ),
    suite("Path parsing - wrapped")(
      test("parses wrapped node") {
        val path    = DynamicOptic.root.field("value").wrapped
        val encoded = encodePath(path)
        val decoded = decodePath(encoded)
        assertTrue(decoded == Right(path))
      }
    ),
    suite("Path parsing - special cases")(
      test("parses root path - dot") {
        val result = decodePath(".")
        assertTrue(result == Right(DynamicOptic.root))
      },
      test("parses root path - empty string") {
        val result = decodePath("")
        assertTrue(result == Right(DynamicOptic.root))
      },
      test("rejects unexpected character") {
        val result = decodePath("@invalid")
        assertTrue(result.isLeft)
        assertTrue(result.left.exists(_.contains("Unexpected")))
      }
    ),
    suite("Path parsing - complex combinations")(
      test("parses complex path with all node types") {
        val path = DynamicOptic.root
          .field("users")
          .elements
          .field("profile")
          .caseOf("Premium")
          .field("settings")
          .mapValues
        val encoded = encodePath(path)
        val decoded = decodePath(encoded)
        assertTrue(decoded == Right(path))
      }
    ),
    suite("Action roundtrip - AddField")(
      test("encodes and decodes AddField") {
        val action  = MigrationAction.AddField(DynamicOptic.root.field("person"), "age", Resolved.Literal.int(25))
        val encoded = encodeAction(action)
        val decoded = decodeAction(encoded)
        assertTrue(decoded == Right(action))
      }
    ),
    suite("Action roundtrip - DropField")(
      test("encodes and decodes DropField with default") {
        val action  = MigrationAction.DropField(DynamicOptic.root, "temp", Resolved.Literal.string("backup"))
        val encoded = encodeAction(action)
        val decoded = decodeAction(encoded)
        assertTrue(decoded == Right(action))
      },
      test("encodes and decodes DropField without default") {
        val action  = MigrationAction.DropField(DynamicOptic.root, "temp", Resolved.Literal.int(0))
        val encoded = encodeAction(action)
        val decoded = decodeAction(encoded)
        assertTrue(decoded == Right(action))
      }
    ),
    suite("Action roundtrip - Rename")(
      test("encodes and decodes Rename") {
        val action  = MigrationAction.Rename(DynamicOptic.root.field("address"), "street", "streetAddress")
        val encoded = encodeAction(action)
        val decoded = decodeAction(encoded)
        assertTrue(decoded == Right(action))
      }
    ),
    suite("Action roundtrip - TransformValue")(
      test("encodes and decodes TransformValue") {
        val action = MigrationAction.TransformValue(
          DynamicOptic.root,
          "count",
          Resolved.Literal.int(1),
          Resolved.Literal.int(2)
        )
        val encoded = encodeAction(action)
        val decoded = decodeAction(encoded)
        decoded match {
          case Right(MigrationAction.TransformValue(at, fieldName, _, _)) =>
            assertTrue(at == action.at && fieldName == "count")
          case _ => assertTrue(false)
        }
      }
    ),
    suite("Action roundtrip - Mandate")(
      test("encodes and decodes Mandate") {
        val action  = MigrationAction.Mandate(DynamicOptic.root, "required", Resolved.Literal.boolean(false))
        val encoded = encodeAction(action)
        val decoded = decodeAction(encoded)
        assertTrue(decoded == Right(action))
      }
    ),
    suite("Action roundtrip - Optionalize")(
      test("encodes and decodes Optionalize") {
        val action  = MigrationAction.Optionalize(DynamicOptic.root.field("user"), "nickname")
        val encoded = encodeAction(action)
        val decoded = decodeAction(encoded)
        assertTrue(decoded == Right(action))
      }
    ),
    suite("Action roundtrip - ChangeType")(
      test("encodes and decodes ChangeType") {
        val action = MigrationAction.ChangeType(
          DynamicOptic.root,
          "value",
          Resolved.Literal.int(1),
          Resolved.Literal.int(2)
        )
        val encoded = encodeAction(action)
        val decoded = decodeAction(encoded)
        decoded match {
          case Right(MigrationAction.ChangeType(at, fieldName, _, _)) =>
            assertTrue(at == action.at && fieldName == "value")
          case _ => assertTrue(false)
        }
      }
    ),
    suite("Action roundtrip - RenameCase")(
      test("encodes and decodes RenameCase") {
        val action  = MigrationAction.RenameCase(DynamicOptic.root, "OldCase", "NewCase")
        val encoded = encodeAction(action)
        val decoded = decodeAction(encoded)
        assertTrue(decoded == Right(action))
      }
    ),
    suite("Action roundtrip - TransformCase")(
      test("encodes and decodes TransformCase with nested actions") {
        val nested = Chunk(
          MigrationAction.Rename(DynamicOptic.root, "a", "b"),
          MigrationAction.AddField(DynamicOptic.root, "c", Resolved.Literal.int(0))
        )
        val action  = MigrationAction.TransformCase(DynamicOptic.root, "SomeCase", nested)
        val encoded = encodeAction(action)
        val decoded = decodeAction(encoded)
        assertTrue(decoded == Right(action))
      },
      test("encodes and decodes TransformCase with empty nested actions") {
        val action  = MigrationAction.TransformCase(DynamicOptic.root.caseOf("X"), "Case", Chunk.empty)
        val encoded = encodeAction(action)
        val decoded = decodeAction(encoded)
        assertTrue(decoded == Right(action))
      }
    ),
    suite("Action roundtrip - TransformElements")(
      test("encodes and decodes TransformElements") {
        val action = MigrationAction.TransformElements(
          DynamicOptic.root.field("items"),
          Resolved.Literal.int(1),
          Resolved.Literal.int(2)
        )
        val encoded = encodeAction(action)
        val decoded = decodeAction(encoded)
        decoded match {
          case Right(MigrationAction.TransformElements(at, _, _)) =>
            assertTrue(at == action.at)
          case _ => assertTrue(false)
        }
      }
    ),
    suite("Action roundtrip - TransformKeys")(
      test("encodes and decodes TransformKeys") {
        val action = MigrationAction.TransformKeys(
          DynamicOptic.root.field("map"),
          Resolved.Literal.int(1),
          Resolved.Literal.int(2)
        )
        val encoded = encodeAction(action)
        val decoded = decodeAction(encoded)
        decoded match {
          case Right(MigrationAction.TransformKeys(at, _, _)) =>
            assertTrue(at == action.at)
          case _ => assertTrue(false)
        }
      }
    ),
    suite("Action roundtrip - TransformValues")(
      test("encodes and decodes TransformValues") {
        val action = MigrationAction.TransformValues(
          DynamicOptic.root.field("config"),
          Resolved.Literal.int(1),
          Resolved.Literal.int(2)
        )
        val encoded = encodeAction(action)
        val decoded = decodeAction(encoded)
        decoded match {
          case Right(MigrationAction.TransformValues(at, _, _)) =>
            assertTrue(at == action.at)
          case _ => assertTrue(false)
        }
      }
    ),
    suite("Action roundtrip - Join")(
      test("encodes and decodes Join") {
        val action = MigrationAction.Join(
          DynamicOptic.root,
          "fullName",
          Chunk(DynamicOptic.root.field("first"), DynamicOptic.root.field("last")),
          Resolved.Literal.int(1),
          Resolved.Literal.int(2)
        )
        val encoded = encodeAction(action)
        val decoded = decodeAction(encoded)
        decoded match {
          case Right(MigrationAction.Join(at, targetFieldName, sourcePaths, _, _)) =>
            assertTrue(at == action.at && targetFieldName == "fullName" && sourcePaths.size == 2)
          case _ => assertTrue(false)
        }
      }
    ),
    suite("Action roundtrip - Split")(
      test("encodes and decodes Split") {
        val action = MigrationAction.Split(
          DynamicOptic.root,
          "fullName",
          Chunk(DynamicOptic.root.field("first"), DynamicOptic.root.field("last")),
          Resolved.Literal.int(1),
          Resolved.Literal.int(2)
        )
        val encoded = encodeAction(action)
        val decoded = decodeAction(encoded)
        decoded match {
          case Right(MigrationAction.Split(at, sourceFieldName, targetPaths, _, _)) =>
            assertTrue(at == action.at && sourceFieldName == "fullName" && targetPaths.size == 2)
          case _ => assertTrue(false)
        }
      }
    ),
    suite("Action schema")(
      test("action schema converts to dynamic value") {
        val action  = MigrationAction.Rename(DynamicOptic.root, "old", "new")
        val dynamic = MigrationAction.schema.toDynamicValue(action)
        dynamic match {
          case DynamicValue.Variant("Rename", _) => assertTrue(true)
          case _                                 => assertTrue(false)
        }
      },
      test("action schema roundtrips through dynamic value") {
        val action  = MigrationAction.AddField(DynamicOptic.root, "f", Resolved.Literal.string("v"))
        val dynamic = MigrationAction.schema.toDynamicValue(action)
        val back    = MigrationAction.schema.fromDynamicValue(dynamic)
        assertTrue(back == Right(action))
      }
    ),
    suite("Action reverse")(
      test("Rename reverse swaps from/to") {
        val action   = MigrationAction.Rename(DynamicOptic.root, "old", "new")
        val reversed = action.reverse
        reversed match {
          case r: MigrationAction.Rename =>
            assertTrue(r.from == "new" && r.to == "old")
          case _ => assertTrue(false)
        }
      },
      test("DropField reverse is AddField") {
        val action   = MigrationAction.DropField(DynamicOptic.root, "field", Resolved.Literal.int(0))
        val reversed = action.reverse
        reversed match {
          case a: MigrationAction.AddField =>
            assertTrue(a.fieldName == "field")
          case _ => assertTrue(false)
        }
      },
      test("AddField reverse is DropField") {
        val action   = MigrationAction.AddField(DynamicOptic.root, "field", Resolved.Literal.int(0))
        val reversed = action.reverse
        reversed match {
          case d: MigrationAction.DropField =>
            assertTrue(d.fieldName == "field")
          case _ => assertTrue(false)
        }
      }
    ),
    suite("Action prefixPath")(
      test("prefixPath updates path for Rename") {
        val action   = MigrationAction.Rename(DynamicOptic.root, "a", "b")
        val prefixed = action.prefixPath(DynamicOptic.root.field("nested"))
        prefixed match {
          case r: MigrationAction.Rename =>
            assertTrue(r.at.nodes.size == 1)
          case _ => assertTrue(false)
        }
      },
      test("prefixPath updates paths for Join") {
        val action = MigrationAction.Join(
          DynamicOptic.root,
          "target",
          Chunk(DynamicOptic.root.field("x")),
          Resolved.Identity,
          Resolved.Identity
        )
        val prefixed = action.prefixPath(DynamicOptic.root.field("outer"))
        prefixed match {
          case j: MigrationAction.Join =>
            assertTrue(j.at.nodes.nonEmpty && j.sourcePaths.forall(_.nodes.nonEmpty))
          case _ => assertTrue(false)
        }
      },
      test("prefixPath updates paths for Split") {
        val action = MigrationAction.Split(
          DynamicOptic.root,
          "source",
          Chunk(DynamicOptic.root.field("a")),
          Resolved.Identity,
          Resolved.Identity
        )
        val prefixed = action.prefixPath(DynamicOptic.root.field("outer"))
        prefixed match {
          case s: MigrationAction.Split =>
            assertTrue(s.at.nodes.nonEmpty && s.targetPaths.forall(_.nodes.nonEmpty))
          case _ => assertTrue(false)
        }
      }
    )
  )

  // Helper functions for path encoding/decoding
  private def encodePath(path: DynamicOptic): String = path.toScalaString

  private def decodePath(s: String): Either[String, DynamicOptic] = {
    import DynamicOptic.Node

    if (s == "." || s.isEmpty) {
      Right(DynamicOptic.root)
    } else {
      var nodes                 = Vector.empty[Node]
      var remaining             = s
      var error: Option[String] = None

      while (remaining.nonEmpty && error.isEmpty) {
        if (remaining.startsWith(".when[")) {
          // Case: .when[CaseName]
          val endIdx = remaining.indexOf(']')
          if (endIdx < 0) {
            error = Some(s"Malformed when clause in: $remaining")
          } else {
            val caseName = remaining.substring(6, endIdx)
            nodes = nodes :+ Node.Case(caseName)
            remaining = remaining.substring(endIdx + 1)
          }
        } else if (remaining.startsWith(".eachKey")) {
          // MapKeys: .eachKey
          nodes = nodes :+ Node.MapKeys
          remaining = remaining.substring(8)
        } else if (remaining.startsWith(".eachValue")) {
          // MapValues: .eachValue
          nodes = nodes :+ Node.MapValues
          remaining = remaining.substring(10)
        } else if (remaining.startsWith(".each")) {
          // Elements: .each
          nodes = nodes :+ Node.Elements
          remaining = remaining.substring(5)
        } else if (remaining.startsWith(".wrapped")) {
          // Wrapped: .wrapped
          nodes = nodes :+ Node.Wrapped
          remaining = remaining.substring(8)
        } else if (remaining.startsWith(".at(")) {
          // AtIndex: .at(index)
          val endIdx = remaining.indexOf(')')
          if (endIdx < 0) {
            error = Some(s"Malformed at clause in: $remaining")
          } else {
            val idxStr = remaining.substring(4, endIdx)
            try {
              nodes = nodes :+ Node.AtIndex(idxStr.toInt)
              remaining = remaining.substring(endIdx + 1)
            } catch {
              case _: NumberFormatException =>
                error = Some(s"Invalid index: $idxStr")
            }
          }
        } else if (remaining.startsWith(".")) {
          // Field: .fieldName
          remaining = remaining.substring(1)
          val endIdx   = remaining.indexWhere(c => c == '.' || c == '[')
          val fieldEnd = if (endIdx < 0) remaining.length else endIdx
          if (fieldEnd == 0) {
            error = Some(s"Empty field name in: $s")
          } else {
            val fieldName = remaining.substring(0, fieldEnd)
            nodes = nodes :+ Node.Field(fieldName)
            remaining = remaining.substring(fieldEnd)
          }
        } else {
          error = Some(s"Unexpected character in path: $remaining")
        }
      }

      error match {
        case Some(e) => Left(e)
        case None    => Right(new DynamicOptic(nodes.toIndexedSeq))
      }
    }
  }

  private def encodeAction(action: MigrationAction): DynamicValue =
    MigrationAction.schema.toDynamicValue(action)

  private def decodeAction(dv: DynamicValue): Either[String, MigrationAction] =
    MigrationAction.schema.fromDynamicValue(dv).left.map(_.message)
}
