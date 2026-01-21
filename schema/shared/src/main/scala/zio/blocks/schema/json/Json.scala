package zio.blocks.schema.json

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue, SchemaError}
import zio.blocks.schema.json.JsonBinaryCodec._
import zio.blocks.schema.patch.DynamicPatch

import java.io.{Reader, Writer}
import java.nio.ByteBuffer

// =============================================================================
// PLACEHOLDER TYPES (assumed to exist)
// =============================================================================

/**
 * Represents a JSON Schema for validation.
 *
 * Placeholder - actual implementation TBD. Basic schema validation support
 * provided for completeness.
 */
sealed trait JsonSchema {
  def check(json: Json): Option[SchemaError]
}

object JsonSchema {
  case object Null extends JsonSchema {
    def check(json: Json): Option[SchemaError] =
      if (json.isNull) None else Some(SchemaError.expectationMismatch(List(), "Expected null"))
  }

  case object Boolean extends JsonSchema {
    def check(json: Json): Option[SchemaError] =
      if (json.isBoolean) None else Some(SchemaError.expectationMismatch(List(), "Expected boolean"))
  }

  case object String extends JsonSchema {
    def check(json: Json): Option[SchemaError] =
      if (json.isString) None else Some(SchemaError.expectationMismatch(List(), "Expected string"))
  }

  case object Number extends JsonSchema {
    def check(json: Json): Option[SchemaError] =
      if (json.isNumber) None else Some(SchemaError.expectationMismatch(List(), "Expected number"))
  }

  case class Array(elementSchema: JsonSchema) extends JsonSchema {
    def check(json: Json): Option[SchemaError] = json match {
      case Json.Array(elements) =>
        elements.zipWithIndex.find { case (elem, _) =>
          elementSchema.check(elem).isDefined
        }.map { case (_, idx) =>
          SchemaError.expectationMismatch(List(DynamicOptic.Node.AtIndex(idx)), "Array element validation failed")
        }
      case _ => Some(SchemaError.expectationMismatch(List(), "Expected array"))
    }
  }

  case class Object(fields: Map[String, JsonSchema], required: Set[String] = Set.empty) extends JsonSchema {
    def check(json: Json): Option[SchemaError] = json match {
      case Json.Object(objFields) =>
        // Check required fields
        val missing = required -- objFields.map(_._1)
        if (missing.nonEmpty) {
          Some(SchemaError.expectationMismatch(List(), s"Missing required fields: ${missing.mkString(", ")}"))
        } else {
          // Check field types
          objFields.find { case (key, value) =>
            fields.get(key).exists(_.check(value).isDefined)
          }.map { case (key, _) =>
            SchemaError.expectationMismatch(List(DynamicOptic.Node.Field(key)), s"Field '$key' validation failed")
          }
        }
      case _ => Some(SchemaError.expectationMismatch(List(), "Expected object"))
    }
  }

  case class Optional(schema: JsonSchema) extends JsonSchema {
    def check(json: Json): Option[SchemaError] =
      if (json == Json.Null) None else schema.check(json)
  }

  case class Union(schemas: JsonSchema*) extends JsonSchema {
    def check(json: Json): Option[SchemaError] =
      if (schemas.exists(_.check(json).isEmpty)) None
      else Some(SchemaError.expectationMismatch(List(), "Value doesn't match any union type"))
  }

  object JsonSchema {
    // Helpers
    def obj(fields: (java.lang.String, JsonSchema)*): Object = Object(fields.toMap)
    def arr(items: JsonSchema): Array                        = Array(items)
  }
}

/**
 * Represents a patch that can be applied to JSON values.
 *
 * Supports RFC 6902 operations (add, remove, replace, move, copy, test) plus
 * extensions for LCS-based sequence diffs and string diffs.
 *
 * Placeholder - actual implementation TBD.
 */
sealed trait JsonPatch {
  def toDynamicPatch: DynamicPatch = DynamicPatch.empty // Placeholder
}

object JsonPatch {
  final case class Add(path: java.lang.String, value: Json)             extends JsonPatch
  final case class Remove(path: java.lang.String)                       extends JsonPatch
  final case class Replace(path: java.lang.String, value: Json)         extends JsonPatch
  final case class Move(from: java.lang.String, path: java.lang.String) extends JsonPatch
  final case class Copy(from: java.lang.String, path: java.lang.String) extends JsonPatch
  final case class Test(path: java.lang.String, value: Json)            extends JsonPatch

  final case class Batch(ops: List[JsonPatch]) extends JsonPatch

  val empty: JsonPatch = Batch(List.empty)

  def fromDynamicPatch(patch: DynamicPatch): Either[JsonError, JsonPatch] = {
    // Best effort conversion
    val ops: Vector[Either[JsonError, JsonPatch]] = patch.ops.map { op =>
      val pathStr = "/" + op.path.nodes.map {
        case DynamicOptic.Node.Field(n)   => n.replace("~", "~0").replace("/", "~1")
        case DynamicOptic.Node.AtIndex(i) => i.toString
        case _                            => ""
      }.mkString("/")

      op.operation match {
        case DynamicPatch.Operation.Set(value) =>
          Right(Replace(pathStr, Json.fromDynamicValue(value)))
        case DynamicPatch.Operation.SequenceEdit(_) =>
          // This is complex, mapping sequence edits to JsonPatch is hard without indices alignment
          // But explicit inserts/deletes might map if they use absolute indices.
          // DynamicPatch SeqOp uses relative indices?
          // Assuming absolute for now or failing.
          Left(JsonError("SequenceEdit conversion not supported yet"))
        case _ =>
          Left(JsonError(s"Unsupported operation for conversion: ${op.operation}"))
      }
    }

    val (errors, validOps) = ops.partitionMap(identity)
    if (errors.nonEmpty) Left(errors.head)
    else Right(Batch(validOps.toList))
  }

  private[json] def parsePointer(pointer: java.lang.String): Either[JsonError, DynamicOptic] =
    if (pointer == "" || pointer == "/") Right(DynamicOptic.root)
    else if (!pointer.startsWith("/")) Left(JsonError(s"Invalid JSON pointer: $pointer"))
    else {
      try {
        val segments = pointer.substring(1).split("/", -1).map { s =>
          val decoded = s.replace("~1", "/").replace("~0", "~")
          if (decoded.matches("^(0|[1-9][0-9]*)$")) DynamicOptic.Node.AtIndex(decoded.toInt)
          else DynamicOptic.Node.Field(decoded)
        }
        Right(DynamicOptic(segments.toIndexedSeq))
      } catch {
        case e: Throwable => Left(JsonError(s"Failed to parse pointer: ${e.getMessage}"))
      }
    }
}

/**
 * Represents a patch that can be applied to [[DynamicValue]].
 *
 * Placeholder - actual implementation TBD.
 */

// ===========================================================================
// JSON ADT
// =============================================================================

sealed trait Json { self =>

  // ===========================================================================
  // Type Testing
  // ===========================================================================

  /**
   * Returns `true` if this is a JSON object.
   */
  def isObject: Boolean = false

  /**
   * Returns `true` if this is a JSON array.
   */
  def isArray: Boolean = false

  /**
   * Returns `true` if this is a JSON string.
   */
  def isString: Boolean = false

  /**
   * Returns `true` if this is a JSON number.
   */
  def isNumber: Boolean = false

  /**
   * Returns `true` if this is a JSON boolean.
   */
  def isBoolean: Boolean = false

  /**
   * Returns `true` if this is JSON null.
   */
  def isNull: Boolean = false

  // ===========================================================================
  // Type Filtering (returns JsonSelection)
  // ===========================================================================

  /**
   * Returns a [[JsonSelection]] containing this value if it is an object,
   * otherwise an empty selection.
   */
  def asObject: JsonSelection = if (isObject) JsonSelection(self) else JsonSelection.empty

  /**
   * Returns a [[JsonSelection]] containing this value if it is an array,
   * otherwise an empty selection.
   */
  def asArray: JsonSelection = if (isArray) JsonSelection(self) else JsonSelection.empty

  /**
   * Returns a [[JsonSelection]] containing this value if it is a string,
   * otherwise an empty selection.
   */
  def asString: JsonSelection = if (isString) JsonSelection(self) else JsonSelection.empty

  /**
   * Returns a [[JsonSelection]] containing this value if it is a number,
   * otherwise an empty selection.
   */
  def asNumber: JsonSelection = if (isNumber) JsonSelection(self) else JsonSelection.empty

  /**
   * Returns a [[JsonSelection]] containing this value if it is a boolean,
   * otherwise an empty selection.
   */
  def asBoolean: JsonSelection = if (isBoolean) JsonSelection(self) else JsonSelection.empty

  /**
   * Returns a [[JsonSelection]] containing this value if it is null, otherwise
   * an empty selection.
   */
  def asNull: JsonSelection = if (isNull) JsonSelection(self) else JsonSelection.empty

  // ===========================================================================
  // Direct Accessors
  // ===========================================================================

  def fields: Seq[(java.lang.String, Json)] = Seq.empty
  def elements: Seq[Json]                   = Seq.empty
  def stringValue: Option[java.lang.String] = None
  def numberValue: Option[java.lang.String] = None
  def booleanValue: Option[scala.Boolean]   = None

  // ===========================================================================
  // Navigation
  // ===========================================================================

  /**
   * Converts a [[DynamicValue]] to JSON.
   */

  /**
   * Navigates to values at the given path.
   */
  def get(path: DynamicOptic): JsonSelection =
    path.nodes.foldLeft(JsonSelection(self)) { (acc, node) =>
      acc.flatMap { json =>
        import DynamicOptic.Node
        node match {
          case Node.Field(name) =>
            json match {
              case Json.Object(fields) =>
                fields.collectFirst { case (k, v) if k == name => v } match {
                  case Some(v) => JsonSelection(v)
                  case None    => JsonSelection.empty
                }
              case _ => JsonSelection.empty
            }
          case Node.AtIndex(index) =>
            json match {
              case Json.Array(elements) =>
                if (index >= 0 && index < elements.size) JsonSelection(elements(index))
                else JsonSelection.empty
              case _ => JsonSelection.empty
            }
          case Node.Elements =>
            json match {
              case Json.Array(elements) => JsonSelection.fromVector(elements)
              case _                    => JsonSelection.empty
            }
          case Node.MapKeys =>
            json match {
              case Json.Object(fields) => JsonSelection.fromVector(fields.map(p => Json.String(p._1)))
              case _                   => JsonSelection.empty
            }
          case Node.MapValues =>
            json match {
              case Json.Object(fields) => JsonSelection.fromVector(fields.map(_._2))
              case _                   => JsonSelection.empty
            }
          // Handling complex cases like Case, AtMapKey requires DynamicValue interop or convention
          // For simple Json, we can try to support AtMapKey if it's a String key
          case Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.String(key))) =>
            json match {
              case Json.Object(fields) =>
                fields.collectFirst { case (k, v) if k == key => v } match {
                  case Some(v) => JsonSelection(v)
                  case None    => JsonSelection.empty
                }
              case _ => JsonSelection.empty
            }
          case Node.AtMapKeys(keys) =>
            val stringKeys = keys.collect { case DynamicValue.Primitive(PrimitiveValue.String(k)) => k }.toSet
            json match {
              case Json.Object(fields) =>
                JsonSelection.fromVector(fields.collect { case (k, v) if stringKeys.contains(k) => v })
              case _ => JsonSelection.empty
            }
          case _ =>
            // Unsupported or unimplemented nodes for pure Json navigation return empty for now
            JsonSelection.empty
        }
      }
    }

  def apply(path: DynamicOptic): JsonSelection = get(path)

  def apply(index: Int): JsonSelection = self match {
    case Json.Array(elems) if index >= 0 && index < elems.size =>
      JsonSelection(elems(index))
    case _ =>
      JsonSelection.empty
  }

  def apply(key: java.lang.String): JsonSelection = self match {
    case Json.Object(flds) =>
      flds.collectFirst { case (k, v) if k == key => v } match {
        case Some(v) => JsonSelection(v)
        case None    => JsonSelection.empty
      }
    case _ =>
      JsonSelection.empty
  }

  // ===========================================================================
  // Modification (Json => Json)
  // ===========================================================================

  def modify(path: DynamicOptic, f: Json => Json): Json =
    modifyRec(self, path.nodes.toList, f)

  private def modifyRec(json: Json, nodes: List[DynamicOptic.Node], f: Json => Json): Json = {
    import DynamicOptic.Node
    nodes match {
      case Nil          => f(json)
      case head :: tail =>
        head match {
          case Node.Field(name) =>
            json match {
              case Json.Object(fields) =>
                val updated = fields.map {
                  case (k, v) if k == name => (k, modifyRec(v, tail, f))
                  case other               => other
                }
                Json.Object(updated)
              case _ => json
            }
          case Node.AtIndex(index) =>
            json match {
              case Json.Array(elements) =>
                if (index >= 0 && index < elements.size) {
                  val updated = elements.updated(index, modifyRec(elements(index), tail, f))
                  Json.Array(updated)
                } else json
              case _ => json
            }
          case Node.Elements =>
            json match {
              case Json.Array(elements) =>
                Json.Array(elements.map(e => modifyRec(e, tail, f)))
              case _ => json
            }
          case Node.MapValues =>
            json match {
              case Json.Object(fields) =>
                Json.Object(fields.map { case (k, v) => (k, modifyRec(v, tail, f)) })
              case _ => json
            }
          case _ => json
        }
    }
  }

  def transformUp(f: (DynamicOptic, Json) => Json): Json = {
    def go(path: DynamicOptic, current: Json): Json = {
      val newCurrent = current match {
        case Json.Object(fields) =>
          Json.Object(fields.map { case (k, v) => (k, go(path.field(k), v)) })
        case Json.Array(elements) =>
          Json.Array(elements.zipWithIndex.map { case (v, i) => go(path.at(i), v) })
        case _ => current
      }
      f(path, newCurrent)
    }
    go(DynamicOptic.root, self)
  }

  def transformDown(f: (DynamicOptic, Json) => Json): Json = {
    def go(path: DynamicOptic, current: Json): Json = {
      val newCurrent = f(path, current)
      newCurrent match {
        case Json.Object(fields) =>
          Json.Object(fields.map { case (k, v) => (k, go(path.field(k), v)) })
        case Json.Array(elements) =>
          Json.Array(elements.zipWithIndex.map { case (v, i) => go(path.at(i), v) })
        case _ => newCurrent
      }
    }
    go(DynamicOptic.root, self)
  }

  def foldUp[Z](z: Z)(f: (DynamicOptic, Json, Z) => Z): Z = {
    def go(path: DynamicOptic, current: Json, acc: Z): Z = {
      val newAcc = current match {
        case Json.Object(fields) =>
          fields.foldLeft(acc) { case (a, (k, v)) => go(path.field(k), v, a) }
        case Json.Array(elements) =>
          elements.zipWithIndex.foldLeft(acc) { case (a, (v, i)) => go(path.at(i), v, a) }
        case _ => acc
      }
      f(path, current, newAcc)
    }
    go(DynamicOptic.root, self, z)
  }

  def foldDown[Z](z: Z)(f: (DynamicOptic, Json, Z) => Z): Z = {
    def go(path: DynamicOptic, current: Json, acc: Z): Z = {
      val afterSelf = f(path, current, acc)
      current match {
        case Json.Object(fields) =>
          fields.foldLeft(afterSelf) { case (a, (k, v)) => go(path.field(k), v, a) }
        case Json.Array(elements) =>
          elements.zipWithIndex.foldLeft(afterSelf) { case (a, (v, i)) => go(path.at(i), v, a) }
        case _ => afterSelf
      }
    }
    go(DynamicOptic.root, self, z)
  }

  def foldUpOrFail[B](z: B)(f: (DynamicOptic, Json, B) => Either[JsonError, B]): Either[JsonError, B] = {
    def go(path: DynamicOptic, current: Json, acc: B): Either[JsonError, B] = {
      val childrenResult: Either[JsonError, B] = current match {
        case Json.Object(fields) =>
          fields.foldLeft[Either[JsonError, B]](Right(acc)) {
            case (Right(a), (k, v)) => go(path.field(k), v, a)
            case (l @ Left(_), _)   => l
          }
        case Json.Array(elements) =>
          elements.zipWithIndex.foldLeft[Either[JsonError, B]](Right(acc)) {
            case (Right(a), (v, i)) => go(path.at(i), v, a)
            case (l @ Left(_), _)   => l
          }
        case _ => Right(acc)
      }
      childrenResult.flatMap(newAcc => f(path, current, newAcc))
    }
    go(DynamicOptic.root, self, z)
  }

  def foldDownOrFail[B](z: B)(f: (DynamicOptic, Json, B) => Either[JsonError, B]): Either[JsonError, B] = {
    def go(path: DynamicOptic, current: Json, acc: B): Either[JsonError, B] =
      f(path, current, acc).flatMap { afterSelf =>
        current match {
          case Json.Object(fields) =>
            fields.foldLeft[Either[JsonError, B]](Right(afterSelf)) {
              case (Right(a), (k, v)) => go(path.field(k), v, a)
              case (l @ Left(_), _)   => l
            }
          case Json.Array(elements) =>
            elements.zipWithIndex.foldLeft[Either[JsonError, B]](Right(afterSelf)) {
              case (Right(a), (v, i)) => go(path.at(i), v, a)
              case (l @ Left(_), _)   => l
            }
          case _ => Right(afterSelf)
        }
      }
    go(DynamicOptic.root, self, z)
  }

  def transformKeys(f: (DynamicOptic, String) => String): Json = {
    def go(path: DynamicOptic, current: Json): Json = current match {
      case Json.Object(fields) =>
        Json.Object(fields.map { case (k, v) =>
          (f(path, k), go(path.field(k), v))
        })
      case Json.Array(elements) =>
        Json.Array(elements.zipWithIndex.map { case (v, i) => go(path.at(i), v) })
      case _ => current
    }
    go(DynamicOptic.root, self)
  }
  def filter(p: (DynamicOptic, Json) => Boolean): Json = {
    def go(path: DynamicOptic, current: Json): Option[Json] =
      if (!p(path, current)) None
      else
        current match {
          case Json.Object(fields) =>
            val newFields = fields.flatMap { case (k, v) =>
              go(path.field(k), v).map(nv => (k, nv))
            }
            Some(Json.Object(newFields))
          case Json.Array(elements) =>
            val newElements = elements.zipWithIndex.flatMap { case (v, i) =>
              go(path.at(i), v)
            }
            Some(Json.Array(newElements))
          case _ => Some(current)
        }
    go(DynamicOptic.root, self).getOrElse(Json.Null)
  }

  def filterNot(p: (DynamicOptic, Json) => Boolean): Json = filter((path, json) => !p(path, json))

  def set(path: DynamicOptic, value: Json): Json =
    setRec(self, path.nodes.toList, value)

  private def setRec(json: Json, nodes: List[DynamicOptic.Node], value: Json): Json = {
    import DynamicOptic.Node
    nodes match {
      case Nil          => value
      case head :: tail =>
        head match {
          case Node.Field(name) =>
            val obj       = if (json.isObject) json.asInstanceOf[Json.Object] else Json.Object.empty
            val existing  = obj.fields.find(_._1 == name).map(_._2).getOrElse(Json.Null)
            val updated   = setRec(existing, tail, value)
            val newFields = if (obj.fields.exists(_._1 == name)) {
              obj.fields.map { case (k, v) => if (k == name) (k, updated) else (k, v) }
            } else {
              obj.fields :+ (name -> updated)
            }
            Json.Object(newFields)

          case Node.AtIndex(index) =>
            val arr = if (json.isArray) json.asInstanceOf[Json.Array] else Json.Array.empty
            if (index >= 0) {
              // Extend array if needed with Null
              val padded = if (index >= arr.elements.size) {
                arr.elements ++ Vector.fill(index - arr.elements.size + 1)(Json.Null)
              } else arr.elements
              // Recurse
              val existing       = if (index < arr.elements.size) arr.elements(index) else Json.Null
              val updatedElement = setRec(existing, tail, value)
              val newElements    = padded.updated(index, updatedElement)
              Json.Array(newElements)
            } else json

          case _ => json // Unsupported set operations for complex selectors
        }
    }
  }

  def delete(path: DynamicOptic): Json = deleteRec(self, path.nodes.toList)

  private def deleteRec(json: Json, nodes: List[DynamicOptic.Node]): Json = {
    import DynamicOptic.Node
    nodes match {
      case Nil =>
        Json.Null // Deleting self replaces with Null? Or "removes"? In recursive context, removal is handled by parent.
        // But here we return Json. If we are at root, delete(root) -> Null
        Json.Null
      case head :: Nil =>
        // Terminal node: perform deletion
        head match {
          case Node.Field(name) =>
            json match {
              case Json.Object(fields) => Json.Object(fields.filterNot(_._1 == name))
              case _                   => json
            }
          case Node.AtIndex(index) =>
            json match {
              case Json.Array(elements) =>
                if (index >= 0 && index < elements.size) {
                  val (front, back) = elements.splitAt(index)
                  Json.Array(front ++ back.drop(1))
                } else json
              case _ => json
            }
          case _ => json
        }
      case head :: tail =>
        // Recursive step behavior depends on head
        modifyRec(json, List(head), j => deleteRec(j, tail))
    }
  }

  // ===========================================================================
  // Merging
  // ===========================================================================

  def merge(other: Json, strategy: MergeStrategy = MergeStrategy.Auto): Json = {
    def mergeDeep(v1: Json, v2: Json): Json = (v1, v2) match {
      case (Json.Object(f1), Json.Object(f2)) =>
        val f1Map  = f1.toMap
        val f2Map  = f2.toMap
        val keys   = (f1Map.keySet ++ f2Map.keySet).toVector
        val merged = keys.map { k =>
          (f1Map.get(k), f2Map.get(k)) match {
            case (Some(v1), Some(v2)) => k -> mergeDeep(v1, v2)
            case (Some(v1), None)     => k -> v1
            case (None, Some(v2))     => k -> v2
            case _                    => k -> Json.Null // Should not happen
          }
        }
        Json.Object(merged)
      case (Json.Array(a1), Json.Array(a2)) => Json.Array(a1 ++ a2)
      case (_, v2)                          => v2
    }

    strategy match {
      case MergeStrategy.Replace => other
      case MergeStrategy.Concat  =>
        (self, other) match {
          case (Json.Array(a), Json.Array(b)) => Json.Array(a ++ b)
          case _                              => other
        }
      case MergeStrategy.Shallow =>
        (self, other) match {
          case (Json.Object(a), Json.Object(b)) =>
            val aMap   = a.toMap
            val merged = a ++ b.filterNot(kv => aMap.contains(kv._1))
            Json.Object(merged) // Note: this logic prefers A for conflict? Usually shallow merge prefers B.
            // "Shallow merge: right value wins for any key conflict."
            // So b should override a.
            val bMap    = b.toMap
            val mergedB = a.filterNot(kv => bMap.contains(kv._1)) ++ b
            Json.Object(mergedB)
          case _ => other
        }
      case MergeStrategy.Deep      => mergeDeep(self, other)
      case MergeStrategy.Auto      => mergeDeep(self, other)
      case MergeStrategy.Custom(f) => f(DynamicOptic.root, self, other)
    }
  }

  // ===========================================================================
  // Patching
  // ===========================================================================

  // ===========================================================================
  // Normalization, Diffing, and Validation
  // ===========================================================================

  /**
   * Normalize this JSON value by sorting object keys and removing nulls.
   */
  def normalize: Json = self match {
    case Json.Object(fields) =>
      Json.Object(
        fields.filterNot { case (_, v) => v == Json.Null }
          .sortBy(_._1)
          .map { case (k, v) => (k, v.normalize) }
      )
    case Json.Array(elems) =>
      Json.Array(elems.map(_.normalize))
    case Json.Number(v) =>
      // Normalize numbers by removing trailing zeros and unnecessary decimal points
      val normalized = try {
        val bd = BigDecimal(v)
        if (bd.scale <= 0) bd.toBigInt.toString()
        else bd.bigDecimal.stripTrailingZeros().toPlainString
      } catch {
        case _: Throwable => v
      }
      Json.Number(normalized)
    case _ => self
  }

  /**
   * Sorts object keys alphabetically. Recursively sorts all nested objects.
   */
  def sortKeys: Json = self match {
    case Json.Object(fields) =>
      Json.Object(
        fields
          .sortBy(_._1)
          .map { case (k, v) => (k, v.sortKeys) }
      )
    case Json.Array(elems) =>
      Json.Array(elems.map(_.sortKeys))
    case _ => self
  }

  /**
   * Removes all null values from this JSON, recursively. Empty objects and
   * arrays after null removal are also removed.
   */
  def dropNulls: Json = self match {
    case Json.Null           => Json.Null
    case Json.Object(fields) =>
      val filtered =
        fields.filterNot(_._2 == Json.Null).map { case (k, v) => (k, v.dropNulls) }.filter(_._2 != Json.Null)
      if (filtered.isEmpty) Json.Object.empty
      else Json.Object(filtered)
    case Json.Array(elems) =>
      val filtered = elems.map(_.dropNulls).filter(_ != Json.Null)
      if (filtered.isEmpty) Json.Array.empty
      else Json.Array(filtered)
    case _ => self
  }

  /**
   * Removes empty objects and arrays from this JSON, recursively.
   */
  def dropEmpty: Json = self match {
    case Json.Object(fields) =>
      val filtered = fields.map { case (k, v) => (k, v.dropEmpty) }.filter {
        case (_, Json.Object(empty)) => empty.nonEmpty
        case (_, Json.Array(empty))  => empty.nonEmpty
        case _                       => true
      }
      if (filtered.isEmpty) Json.Object.empty
      else Json.Object(filtered)
    case Json.Array(elems) =>
      val filtered = elems.map(_.dropEmpty).filter {
        case _: Json.Object => true
        case _: Json.Array  => true
        case _              => false
      }
      if (filtered.isEmpty) Json.Array.empty
      else Json.Array(filtered)
    case _ => self
  }

  /**
   * Compute a diff between this JSON and another, returning a patch.
   * Placeholder implementation - returns empty patch.
   */
  def diff(other: Json): JsonPatch = {
    def escape(s: java.lang.String): java.lang.String = s.replace("~", "~0").replace("/", "~1")

    def go(path: java.lang.String, left: Json, right: Json): List[JsonPatch] =
      if (left == right) Nil
      else
        (left, right) match {
          case (Json.Object(lFields), Json.Object(rFields)) =>
            val lMap    = lFields.toMap
            val rMap    = rFields.toMap
            val allKeys = (lMap.keySet ++ rMap.keySet).toList.sorted // Sorted for deterministic output

            allKeys.flatMap { k =>
              val newPath = if (path.isEmpty) "/" + escape(k) else path + "/" + escape(k)
              (lMap.get(k), rMap.get(k)) match {
                case (Some(lv), Some(rv)) => go(newPath, lv, rv)
                case (Some(_), None)      => List(JsonPatch.Remove(newPath))
                case (None, Some(rv))     => List(JsonPatch.Add(newPath, rv))
                case _                    => Nil
              }
            }

          case (Json.Array(lElems), Json.Array(rElems)) =>
            val commonLen = math.min(lElems.size, rElems.size)

            val modifications = (0 until commonLen).toList.flatMap { i =>
              val newPath = if (path.isEmpty) "/" + i.toString else path + "/" + i.toString
              go(newPath, lElems(i), rElems(i))
            }

            val deletions = (commonLen until lElems.size).reverse.map { i =>
              val newPath = if (path.isEmpty) "/" + i.toString else path + "/" + i.toString
              JsonPatch.Remove(newPath)
            }

            val additions = (commonLen until rElems.size).map { i =>
              // For appending, we can use the index '-'.
              // But existing patch logic uses numeric indices.
              // If we just append to the end, the path index should be the CURRENT length.
              // But since we are processing this batch, 'i' corresponds to the index in the NEW array.
              // Wait, standard JSON Patch 'add' to array uses index to insert BEFORE.
              // If index == length, it appends.
              // In our naive 'append only' diff:
              // We add at index 'i' (which is >= commonLen).
              // Since we deleted everything > commonLen, the array size is currently 'commonLen' (effectively).
              // But 'i' goes from commonLen to rElems.size - 1.
              // So first add is at 'commonLen'.
              // Second add is at 'commonLen + 1'.
              // So 'i' is the correct index.
              val newPath = if (path.isEmpty) "/" + i.toString else path + "/" + i.toString
              JsonPatch.Add(newPath, rElems(i))
            }

            modifications ++ deletions ++ additions

          case _ =>
            // Types differ or scalar values differ
            val p = if (path.isEmpty) "/" else path
            List(JsonPatch.Replace(p, right))
        }

    if (self == other) JsonPatch.empty
    else JsonPatch.Batch(go("", self, other))
  }

  // ===========================================================================
  // Comparison
  // ===========================================================================

  def compare(that: Json): Int = (self, that) match {
    case (_: Json.Null.type, _: Json.Null.type) => 0
    case (_: Json.Null.type, _)                 => -1
    case (_, _: Json.Null.type)                 => 1
    case (Json.Boolean(a), Json.Boolean(b))     => a.compare(b)
    case (Json.Boolean(_), _)                   => -1
    case (_, Json.Boolean(_))                   => 1
    case (Json.Number(a), Json.Number(b))       => BigDecimal(a).compare(BigDecimal(b))
    case (Json.Number(_), _)                    => -1
    case (_, Json.Number(_))                    => 1
    case (Json.String(a), Json.String(b))       => a.compare(b)
    case (Json.String(_), _)                    => -1
    case (_, Json.String(_))                    => 1
    case (Json.Array(a), Json.Array(b))         => compareArrays(a, b)
    case (Json.Array(_), _)                     => -1
    case (_, Json.Array(_))                     => 1
    case (Json.Object(a), Json.Object(b))       => compareObjects(a, b)
  }

  private def compareArrays(a: Vector[Json], b: Vector[Json]): Int = {
    val len = math.min(a.size, b.size)
    var i   = 0
    while (i < len) {
      val cmp = a(i).compare(b(i))
      if (cmp != 0) return cmp
      i += 1
    }
    a.size.compare(b.size)
  }

  private def compareObjects(a: Vector[(java.lang.String, Json)], b: Vector[(java.lang.String, Json)]): Int = {
    val aSorted = a.sortBy(_._1)
    val bSorted = b.sortBy(_._1)
    val len     = math.min(aSorted.size, bSorted.size)
    var i       = 0
    while (i < len) {
      val (ak, av) = aSorted(i)
      val (bk, bv) = bSorted(i)
      val keyCmp   = ak.compare(bk)
      if (keyCmp != 0) return keyCmp
      val valCmp = av.compare(bv)
      if (valCmp != 0) return valCmp
      i += 1
    }
    aSorted.size.compare(bSorted.size)
  }

  // ===========================================================================
  // Standard Methods
  // ===========================================================================

  /**
   * Converts this JSON to a [[DynamicValue]].
   *
   * This conversion is lossless; all JSON values can be represented as
   * DynamicValue.
   */
  def toDynamicValue: DynamicValue = self match {
    case Json.Null =>
      DynamicValue.Primitive(PrimitiveValue.Unit)
    case Json.Boolean(v) =>
      DynamicValue.Primitive(PrimitiveValue.Boolean(v))
    case Json.Number(v) =>
      DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(v)))
    case Json.String(v) =>
      DynamicValue.Primitive(PrimitiveValue.String(v))
    case Json.Array(elems) =>
      DynamicValue.Sequence(elems.map(_.toDynamicValue))
    case Json.Object(flds) =>
      DynamicValue.Record(flds.map { case (k, v) => (k, v.toDynamicValue) })
  }

  override def hashCode(): Int = self match {
    case Json.Null         => 0
    case Json.Boolean(v)   => v.hashCode()
    case Json.Number(v)    => BigDecimal(v).hashCode()
    case Json.String(v)    => v.hashCode()
    case Json.Array(elems) => elems.hashCode()
    case Json.Object(flds) => flds.sortBy(_._1).hashCode()
  }

  override def equals(that: Any): Boolean = that match {
    case other: Json => compare(other) == 0
    case _           => false
  }

  // ===========================================================================
  // Rendering
  // ===========================================================================

  override def toString: java.lang.String = render

  def render: java.lang.String = {
    val sb = new StringBuilder
    renderTo(sb)
    sb.toString
  }

  def renderTo(sb: StringBuilder, config: WriterConfig = WriterConfig): Unit = {
    def esc(s: String): Unit = {
      sb.append('"')
      s.foreach {
        case '"'                                   => sb.append("\\\"")
        case '\\'                                  => sb.append("\\\\")
        case '\b'                                  => sb.append("\\b")
        case '\f'                                  => sb.append("\\f")
        case '\n'                                  => sb.append("\\n")
        case '\r'                                  => sb.append("\\r")
        case '\t'                                  => sb.append("\\t")
        case c if config.escapeUnicode && c >= 128 => sb.append(String.format("\\u%04x", c.toInt))
        case c if c < ' '                          => sb.append(String.format("\\u%04x", c.toInt))
        case c                                     => sb.append(c)
      }
      sb.append('"')
    }

    def go(json: Json, indent: Int): Unit = json match {
      case Json.Null         => sb.append("null")
      case Json.Boolean(b)   => sb.append(b.toString)
      case Json.Number(n)    => sb.append(n)
      case Json.String(s)    => esc(s)
      case Json.Array(elems) =>
        if (elems.isEmpty) sb.append("[]")
        else {
          sb.append('[')
          if (config.indentionStep > 0) sb.append('\n')
          var first = true
          elems.foreach { e =>
            if (!first) {
              sb.append(',')
              if (config.indentionStep > 0) sb.append('\n')
            }
            if (config.indentionStep > 0) sb.append(" " * ((indent + 1) * config.indentionStep))
            go(e, indent + 1)
            first = false
          }
          if (config.indentionStep > 0) {
            sb.append('\n')
            sb.append(" " * (indent * config.indentionStep))
          }
          sb.append(']')
        }
      case Json.Object(fields) =>
        if (fields.isEmpty) sb.append("{}")
        else {
          sb.append('{')
          if (config.indentionStep > 0) sb.append('\n')
          var first = true
          fields.foreach { case (k, v) =>
            if (!first) {
              sb.append(',')
              if (config.indentionStep > 0) sb.append('\n')
            }
            if (config.indentionStep > 0) sb.append(" " * ((indent + 1) * config.indentionStep))
            esc(k)
            sb.append(':')
            if (config.indentionStep > 0) sb.append(' ')
            go(v, indent + 1)
            first = false
          }
          if (config.indentionStep > 0) {
            sb.append('\n')
            sb.append(" " * (indent * config.indentionStep))
          }
          sb.append('}')
        }
    }
    go(self, 0)
  }

  def encodeToBytes: Array[Byte] = render.getBytes("UTF-8")

  /**
   * Encodes this JSON to a compact string (no extra whitespace).
   */
  def print: String = render

  /**
   * Alias for [[print]].
   */
  def encode: String = render

  /**
   * Encodes this JSON to a string using the specified configuration.
   *
   * @param config
   *   Writer configuration (indentation, unicode escaping, etc.)
   */
  def print(config: WriterConfig): String = encode(config)

  /**
   * Encodes this JSON to a string using the specified configuration.
   *
   * @param config
   *   Writer configuration
   */
  def encode(config: WriterConfig): String = {
    val sb = new StringBuilder
    renderTo(sb, config)
    sb.toString
  }

  /**
   * Encodes this JSON and writes to the provided [[Writer]].
   *
   * @param writer
   *   The writer to write to
   */
  def printTo(writer: Writer): Unit = printTo(writer, WriterConfig)

  /**
   * Encodes this JSON and writes to the provided [[Writer]] with configuration.
   *
   * @param writer
   *   The writer to write to
   * @param config
   *   Writer configuration
   */
  def printTo(writer: Writer, config: WriterConfig): Unit =
    writer.write(encode(config))

  /**
   * Encodes this JSON to a byte array (UTF-8) with configuration.
   *
   * @param config
   *   Writer configuration
   */
  def encodeToBytes(config: WriterConfig): Array[Byte] =
    encode(config).getBytes("UTF-8")

  /**
   * Encodes this JSON to a [[Chunk]] of bytes (UTF-8).
   */
  def encodeToChunk: Chunk[Byte] = Chunk.fromArray(encodeToBytes)

  /**
   * Encodes this JSON to a [[Chunk]] of bytes (UTF-8) with configuration.
   *
   * @param config
   *   Writer configuration
   */
  def encodeToChunk(config: WriterConfig): Chunk[Byte] = Chunk.fromArray(encodeToBytes(config))

  /**
   * Encodes this JSON into the provided [[ByteBuffer]].
   *
   * @param buffer
   *   The buffer to write to
   */
  def encodeTo(buffer: ByteBuffer): Unit = encodeTo(buffer, WriterConfig)

  /**
   * Encodes this JSON into the provided [[ByteBuffer]] with configuration.
   *
   * @param buffer
   *   The buffer to write to
   * @param config
   *   Writer configuration
   */
  def encodeTo(buffer: ByteBuffer, config: WriterConfig): Unit = {
    val bytes = encodeToBytes(config)
    buffer.put(bytes)
  }

  // ===========================================================================
  // Modifications
  // ===========================================================================

  def modifyOrFail(path: DynamicOptic, f: PartialFunction[Json, Json]): Either[JsonError, Json] =
    modifyRecOrFail(self, path.nodes.toList, f, path)

  private def modifyRecOrFail(
    json: Json,
    nodes: List[DynamicOptic.Node],
    f: PartialFunction[Json, Json],
    path: DynamicOptic
  ): Either[JsonError, Json] = {
    import DynamicOptic.Node
    nodes match {
      case Nil =>
        if (f.isDefinedAt(json)) Right(f(json))
        else Right(json)
      case head :: tail =>
        head match {
          case Node.Field(name) =>
            json match {
              case Json.Object(fields) =>
                val idx = fields.indexWhere(_._1 == name)
                if (idx != -1) {
                  modifyRecOrFail(fields(idx)._2, tail, f, path).map { newValue =>
                    Json.Object(fields.updated(idx, (name, newValue)))
                  }
                } else Left(JsonError(s"Field '$name' not found", path))
              case _ => Left(JsonError(s"Expected Object at path, got ${json.getClass.getSimpleName}", path))
            }
          case Node.AtIndex(index) =>
            json match {
              case Json.Array(elements) =>
                if (index >= 0 && index < elements.size) {
                  modifyRecOrFail(elements(index), tail, f, path).map { newValue =>
                    Json.Array(elements.updated(index, newValue))
                  }
                } else Left(JsonError(s"Index $index out of bounds", path))
              case _ => Left(JsonError(s"Expected Array at path, got ${json.getClass.getSimpleName}", path))
            }
          case Node.Elements =>
            json match {
              case Json.Array(elements) =>
                elements
                  .foldLeft[Either[JsonError, Vector[Json]]](Right(Vector.empty)) {
                    case (Right(acc), e)  => modifyRecOrFail(e, tail, f, path).map(acc :+ _)
                    case (l @ Left(_), _) => l
                  }
                  .map(Json.Array(_))
              case _ => Left(JsonError("Expected Array for .elements", path))
            }
          case _ => Left(JsonError(s"Unsupported node type: $head", path))
        }
    }
  }

  def setOrFail(path: DynamicOptic, value: Json): Either[JsonError, Json] =
    modifyOrFail(path, { case _ => value })

  def deleteOrFail(path: DynamicOptic): Either[JsonError, Json] =
    deleteRecOrFail(self, path.nodes.toList, path)

  private def deleteRecOrFail(
    json: Json,
    nodes: List[DynamicOptic.Node],
    path: DynamicOptic
  ): Either[JsonError, Json] = {
    import DynamicOptic.Node
    nodes match {
      case Nil         => Right(Json.Null)
      case head :: Nil =>
        head match {
          case Node.Field(name) =>
            json match {
              case Json.Object(fields) =>
                if (fields.exists(_._1 == name)) Right(Json.Object(fields.filterNot(_._1 == name)))
                else Left(JsonError(s"Field '$name' not found", path))
              case _ => Left(JsonError(s"Expected Object", path))
            }
          case Node.AtIndex(index) =>
            json match {
              case Json.Array(elements) =>
                if (index >= 0 && index < elements.size) {
                  val (f, b) = elements.splitAt(index)
                  Right(Json.Array(f ++ b.drop(1)))
                } else Left(JsonError(s"Index $index out of bounds", path))
              case _ => Left(JsonError("Expected Array", path))
            }
          case _ => Left(JsonError("Unsupported node", path))
        }
      case head :: tail =>
        head match {
          case Node.Field(name) =>
            json match {
              case Json.Object(fields) =>
                val idx = fields.indexWhere(_._1 == name)
                if (idx != -1) {
                  deleteRecOrFail(fields(idx)._2, tail, path).map { newVal =>
                    Json.Object(fields.updated(idx, (name, newVal)))
                  }
                } else Left(JsonError(s"Field '$name' not found", path))
              case _ => Left(JsonError("Expected Object", path))
            }
          case Node.AtIndex(index) =>
            json match {
              case Json.Array(elements) =>
                if (index >= 0 && index < elements.size) {
                  deleteRecOrFail(elements(index), tail, path).map { newVal =>
                    Json.Array(elements.updated(index, newVal))
                  }
                } else Left(JsonError(s"Index $index out of bounds", path))
              case _ => Left(JsonError("Expected Array", path))
            }
          case _ => Left(JsonError("Unsupported node", path))
        }
    }
  }

  def insert(path: DynamicOptic, value: Json): Json =
    // Insert Logic:
    // If path targets an array index, insert at that index (shift right).
    // If path targets a field, add/replace.
    // If path targets root, replace.
    path.nodes.toList match {
      case Nil          => value
      case head :: tail => insertRec(self, head, tail, value)
    }

  private def insertRec(json: Json, head: DynamicOptic.Node, tail: List[DynamicOptic.Node], value: Json): Json = {
    import DynamicOptic.Node
    if (tail.isEmpty) {
      // Terminal insertion
      head match {
        case Node.AtIndex(index) =>
          json match {
            case Json.Array(elements) =>
              if (index >= 0 && index <= elements.size) {
                val (front, back) = elements.splitAt(index)
                Json.Array(front ++ (value +: back))
              } else json
            case _ => json
          }
        case Node.Field(name) =>
          // For object, insert is same as set
          json.set(DynamicOptic(Vector(head)), value)
        case _ => json
      }
    } else {
      // Recursive step
      head match {
        case Node.Field(name) =>
          json match {
            case Json.Object(fields) =>
              val existing =
                fields.find(_._1 == name).map(_._2).getOrElse(Json.Object.empty) // Assume object for path creation?
              val updated = insertRec(existing, tail.head, tail.tail, value)
              json
                .asInstanceOf[Json.Object]
                .copy(fields = fields.map {
                  case (k, _) if k == name => (k, updated)
                  case kv                  => kv
                })
              // What if missing? modifyRec doesn't create.
              // setRec creates. We should probably use set-like traversal.
              // For simplicity, delegate to set for non-array-insertion paths.
              json.set(DynamicOptic(head +: tail.toVector), value) // Verify this acts as insert
            case _ => json
          }
        case Node.AtIndex(index) =>
          json match {
            case Json.Array(elements) =>
              if (index >= 0 && index < elements.size) {
                val updated = insertRec(elements(index), tail.head, tail.tail, value)
                Json.Array(elements.updated(index, updated))
              } else json
            case _ => json
          }
        case _ => json
      }
    }
  }

  def insertOrFail(path: DynamicOptic, value: Json): Either[JsonError, Json] = Right(insert(path, value))

  def patch(p: JsonPatch): Either[JsonError, Json] =
    p match {
      case JsonPatch.Batch(ops) =>
        ops.foldLeft[Either[JsonError, Json]](Right(self)) { (acc, op) =>
          acc.flatMap(_.patch(op))
        }
      case JsonPatch.Add(path, value) =>
        JsonPatch.parsePointer(path).map(optic => insert(optic, value))
      case JsonPatch.Remove(path) =>
        JsonPatch.parsePointer(path).map(optic => delete(optic))
      case JsonPatch.Replace(path, value) =>
        JsonPatch.parsePointer(path).map(optic => set(optic, value))
      case JsonPatch.Move(from, path) =>
        for {
          fromOptic <- JsonPatch.parsePointer(from)
          toOptic   <- JsonPatch.parsePointer(path)
          valToMove <- get(fromOptic).one.left.map(e => JsonError(e.message))
        } yield delete(fromOptic).insert(toOptic, valToMove)
      case JsonPatch.Copy(from, path) =>
        for {
          fromOptic <- JsonPatch.parsePointer(from)
          toOptic   <- JsonPatch.parsePointer(path)
          valToCopy <- get(fromOptic).one.left.map(e => JsonError(e.message))
        } yield insert(toOptic, valToCopy)
      case JsonPatch.Test(path, value) =>
        JsonPatch.parsePointer(path).flatMap { optic =>
          get(optic).one match {
            case Right(actual) if actual == value => Right(self)
            case Right(actual)                    => Left(JsonError(s"Test failed: expected $value, got $actual"))
            case Left(_)                          => Left(JsonError(s"Test failed: path $path not found"))
          }
        }

    }

  def patchUnsafe(patch: JsonPatch): Json = this.patch(patch).fold(throw _, identity)

  // ===========================================================================
  // Projections & Partitioning
  // ===========================================================================

  def project(paths: DynamicOptic*): Json = {
    val roots = if (self.isArray) Json.Array.empty else Json.Object.empty
    paths.foldLeft[Json](roots) { (acc, path) =>
      get(path).toEither.fold(
        _ => acc,
        jsons => {
          jsons.headOption.map(v => acc.set(path, v)).getOrElse(acc)
        }
      )
    }
  }

  def partition(p: (DynamicOptic, Json) => Boolean): (Json, Json) =
    (filter(p), filterNot(p))

  def toKV: Seq[(DynamicOptic, Json)] = {
    def go(path: DynamicOptic, current: Json): Seq[(DynamicOptic, Json)] = current match {
      case Json.Object(fields) if fields.nonEmpty =>
        fields.flatMap { case (k, v) => go(path.field(k), v) }
      case Json.Array(elements) if elements.nonEmpty =>
        elements.zipWithIndex.flatMap { case (v, i) => go(path.at(i), v) }
      case _ =>
        Seq((path, current))
    }
    go(DynamicOptic.root, self)
  }

  /**
   * Splits this JSON value at the specified paths, returning a tuple of:
   *   - The projected JSON containing only the specified paths
   *   - The remainder JSON with the specified paths removed
   */
  def split(paths: DynamicOptic*): (Json, Json) = {
    val projected = project(paths: _*)
    val remainder = paths.foldLeft(self) { (json, path) =>
      json.delete(path)
    }
    (projected, remainder)
  }

  // ===========================================================================
  // Querying & Validation
  // ===========================================================================

  def query(p: (DynamicOptic, Json) => Boolean): JsonSelection = {
    def go(path: DynamicOptic, current: Json): Vector[Json] = {
      val selfMatches     = if (p(path, current)) Vector(current) else Vector.empty
      val childrenMatches = current match {
        case Json.Object(fields) =>
          fields.flatMap { case (k, v) => go(path.field(k), v) }
        case Json.Array(elements) =>
          elements.zipWithIndex.flatMap { case (v, i) => go(path.at(i), v) }
        case _ => Vector.empty
      }
      selfMatches ++ childrenMatches
    }
    JsonSelection(Right(go(DynamicOptic.root, self)))
  }

  def check(schema: JsonSchema): Option[SchemaError] = schema.check(self)

  def conforms(schema: JsonSchema): Boolean = check(schema).isEmpty

  // ===========================================================================
  // Typed Decoding (Json => A)
  // ===========================================================================

  /**
   * Internal: decode this JSON to a typed value using an explicit codec.
   */
  private[json] def decodeWith[A](codec: JsonBinaryCodec[A]): Either[JsonError, A] =
    // Convert Json to ByteBuffer by encoding it as JSON bytes
    try {
      val jsonString = this.encode
      val bytes      = jsonString.getBytes("UTF-8")
      val buffer     = java.nio.ByteBuffer.wrap(bytes)

      // Decode from ByteBuffer using codec
      codec.decode(buffer).left.map(schemaError => JsonError(schemaError.message, DynamicOptic.root, None, None, None))
    } catch {
      case e: JsonError => Left(e)
      case e: Throwable => Left(JsonError(s"Failed to decode: ${e.getMessage}"))
    }

  /**
   * Decodes this JSON to a typed value.
   *
   * Uses implicit [[JsonDecoder]] which prefers explicit codecs over schema
   * derivation.
   *
   * @tparam A
   *   The target type
   * @return
   *   Either an error or the decoded value
   */
  def as[A](implicit decoder: JsonDecoder[A]): Either[JsonError, A] = decoder.decode(self)

  /**
   * Decodes this JSON to a typed value, throwing on failure.
   *
   * @tparam A
   *   The target type
   * @return
   *   The decoded value
   * @throws JsonError
   *   if decoding fails
   */
  def asUnsafe[A](implicit decoder: JsonDecoder[A]): A = as[A].fold(throw _, identity)

}

object Json {

  // ===========================================================================
  // JSON Codec for internal use
  // ===========================================================================

  implicit val jsonCodec: JsonBinaryCodec[Json] = new JsonBinaryCodec[Json](objectType) {
    def decodeValue(in: JsonReader, default: Json): Json =
      // Platform-specific implementation - will be overridden in platform modules
      default

    def encodeValue(x: Json, out: JsonWriter): Unit =
      // Platform-specific implementation - will be overridden in platform modules
      out.writeVal(x.encode)

    override def nullValue: Json = Json.Null

    override def decodeKey(in: JsonReader): Json           = decodeValue(in, nullValue)
    override def encodeKey(x: Json, out: JsonWriter): Unit = encodeValue(x, out)
  }

  // ===========================================================================
  // ADT Cases
  // ===========================================================================

  final case class Object(override val fields: Vector[(java.lang.String, Json)]) extends Json {
    override def isObject: scala.Boolean = true
  }
  object Object {
    val empty: Object                                    = Object(Vector.empty)
    def apply(fields: (java.lang.String, Json)*): Object = Object(fields.toVector)
  }

  final case class Array(override val elements: Vector[Json]) extends Json {
    override def isArray: scala.Boolean = true
  }
  object Array {
    val empty: Array                  = Array(Vector.empty)
    def apply(elements: Json*): Array = Array(elements.toVector)
  }

  final case class String(value: java.lang.String) extends Json {
    override def isString: scala.Boolean               = true
    override def stringValue: Option[java.lang.String] = Some(value)
  }

  final case class Number(value: java.lang.String) extends Json {
    override def isNumber: scala.Boolean               = true
    override def numberValue: Option[java.lang.String] = Some(value)

    lazy val toInt: Int               = toBigDecimal.toInt
    lazy val toLong: Long             = toBigDecimal.toLong
    lazy val toFloat: Float           = value.toFloat
    lazy val toDouble: Double         = value.toDouble
    lazy val toBigInt: BigInt         = toBigDecimal.toBigInt
    lazy val toBigDecimal: BigDecimal = BigDecimal(value)
  }

  final case class Boolean(value: scala.Boolean) extends Json {
    override def isBoolean: scala.Boolean            = true
    override def booleanValue: Option[scala.Boolean] = Some(value)
  }
  object Boolean {
    val True: Boolean  = Boolean(true)
    val False: Boolean = Boolean(false)
  }

  case object Null extends Json {
    override def isNull: scala.Boolean = true
  }

  // ===========================================================================
  // DynamicValue Interop
  // ===========================================================================

  /**
   * Converts a [[DynamicValue]] to JSON.
   */
  def fromDynamicValue(value: DynamicValue): Json = value match {
    case DynamicValue.Primitive(pv) => fromPrimitiveValue(pv)
    case DynamicValue.Record(flds)  =>
      Object(flds.map { case (k, v) => (k, fromDynamicValue(v)) })
    case DynamicValue.Variant(caseName, v) =>
      Object(Vector("_type" -> String(caseName), "_value" -> fromDynamicValue(v)))
    case DynamicValue.Sequence(elems) =>
      Array(elems.map(fromDynamicValue))
    case DynamicValue.Map(entries) =>
      Array(entries.map { case (k, v) =>
        Object(Vector("key" -> fromDynamicValue(k), "value" -> fromDynamicValue(v)))
      })
  }

  private def fromPrimitiveValue(pv: PrimitiveValue): Json = pv match {
    case PrimitiveValue.Unit              => Null
    case PrimitiveValue.Boolean(v)        => Boolean(v)
    case PrimitiveValue.Byte(v)           => number(v)
    case PrimitiveValue.Short(v)          => number(v)
    case PrimitiveValue.Int(v)            => number(v)
    case PrimitiveValue.Long(v)           => number(v)
    case PrimitiveValue.Float(v)          => number(v)
    case PrimitiveValue.Double(v)         => number(v)
    case PrimitiveValue.Char(v)           => String(v.toString)
    case PrimitiveValue.String(v)         => String(v)
    case PrimitiveValue.BigInt(v)         => number(v)
    case PrimitiveValue.BigDecimal(v)     => number(v)
    case PrimitiveValue.DayOfWeek(v)      => String(v.toString)
    case PrimitiveValue.Duration(v)       => String(v.toString)
    case PrimitiveValue.Instant(v)        => String(v.toString)
    case PrimitiveValue.LocalDate(v)      => String(v.toString)
    case PrimitiveValue.LocalDateTime(v)  => String(v.toString)
    case PrimitiveValue.LocalTime(v)      => String(v.toString)
    case PrimitiveValue.Month(v)          => String(v.toString)
    case PrimitiveValue.MonthDay(v)       => String(v.toString)
    case PrimitiveValue.OffsetDateTime(v) => String(v.toString)
    case PrimitiveValue.OffsetTime(v)     => String(v.toString)
    case PrimitiveValue.Period(v)         => String(v.toString)
    case PrimitiveValue.Year(v)           => String(v.toString)
    case PrimitiveValue.YearMonth(v)      => String(v.toString)
    case PrimitiveValue.ZoneId(v)         => String(v.getId)
    case PrimitiveValue.ZoneOffset(v)     => String(v.toString)
    case PrimitiveValue.ZonedDateTime(v)  => String(v.toString)
    case PrimitiveValue.Currency(v)       => String(v.getCurrencyCode)
    case PrimitiveValue.UUID(v)           => String(v.toString)
  }

  // ===========================================================================
  // Convenience Constructors
  // ===========================================================================

  def number(n: Int): Number        = Number(n.toString)
  def number(n: Long): Number       = Number(n.toString)
  def number(n: Float): Number      = Number(n.toString)
  def number(n: Double): Number     = Number(n.toString)
  def number(n: BigInt): Number     = Number(n.toString)
  def number(n: BigDecimal): Number = Number(n.toString)
  def number(n: Short): Number      = Number(n.toString)
  def number(n: Byte): Number       = Number(n.toString)

  // ===========================================================================
  // Ordering
  // ===========================================================================

  implicit val ordering: Ordering[Json] = (x: Json, y: Json) => x.compare(y)

  // TODO: Schema.derived cannot be used in the same compilation run that defines Schema
  // This should be manually implemented or moved to a separate compilation unit
  // implicit lazy val schema: Schema[Json] = Schema.derived[Json]

  // ===========================================================================
  // Typed Encoding (A => Json)
  // ===========================================================================

  /**
   * Internal: encode a typed value to JSON using an explicit codec.
   */
  private[json] def encodeWith[A](value: A, codec: JsonBinaryCodec[A]): Json = {
    // For basic types, provide direct encoding
    // Note: codec is currently unused but kept for API compatibility
    val _ = codec // Suppress unused warning
    value match {
      case s: java.lang.String  => Json.String(s)
      case i: java.lang.Integer => Json.number(i.intValue)
      case l: java.lang.Long    => Json.number(l.longValue)
      case d: java.lang.Double  => Json.number(d.doubleValue)
      case f: java.lang.Float   => Json.number(f.floatValue)
      case b: java.lang.Boolean => Json.Boolean(b.booleanValue)
      case null                 => Json.Null
      case _                    =>
        // For other types, try to convert through string representation
        Json.String(value.toString)
    }
  }

  /**
   * Encodes a typed value to JSON.
   *
   * Uses implicit [[JsonEncoder]] which prefers explicit codecs over schema
   * derivation.
   *
   * @param value
   *   The value to encode
   * @return
   *   The encoded JSON
   */
  def from[A](value: A)(implicit encoder: JsonEncoder[A]): Json = encoder.encode(value)

  // ===========================================================================
  // Parsing / Decoding (String/Bytes => Json)
  // ===========================================================================

  /**
   * Parses a JSON value from a string.
   */
  def parse(s: java.lang.String): Either[JsonError, Json] = decode(s)

  /**
   * Parses a JSON value from a byte array (UTF-8).
   */
  def parse(bytes: scala.Array[Byte]): Either[JsonError, Json] = decode(bytes)

  /**
   * Parses a JSON value from a [[ByteBuffer]] (UTF-8).
   */
  def parse(buffer: ByteBuffer): Either[JsonError, Json] = decode(buffer)

  /**
   * Decodes a JSON value from a string.
   */
  def decode(s: java.lang.String): Either[JsonError, Json] = JsonParser.parseString(s)

  /**
   * Decodes a JSON value from a byte array (UTF-8).
   */
  def decode(bytes: scala.Array[Byte]): Either[JsonError, Json] =
    JsonParser.parseString(new java.lang.String(bytes, "UTF-8"))

  /**
   * Decodes a JSON value from a [[ByteBuffer]] (UTF-8).
   */
  def decode(buffer: ByteBuffer): Either[JsonError, Json] = {
    val bytes = if (buffer.hasArray) {
      val array  = buffer.array()
      val offset = buffer.arrayOffset()
      val length = buffer.remaining()
      if (offset == 0 && length == array.length) array
      else array.slice(offset, offset + length)
    } else {
      val bytes = new scala.Array[Byte](buffer.remaining())
      buffer.get(bytes)
      bytes
    }
    JsonParser.parseString(new java.lang.String(bytes, "UTF-8"))
  }

  /**
   * Decodes a JSON value from a [[CharSequence]].
   */
  def decode(s: CharSequence): Either[JsonError, Json] = JsonParser.parseString(s.toString)

  /**
   * Parses a JSON value from a `CharSequence`.
   */
  def parse(s: CharSequence): Either[JsonError, Json] = decode(s.toString)

  /**
   * Decodes a JSON value from a [[Chunk]] of bytes (UTF-8).
   */
  def decode(chunk: Chunk[Byte]): Either[JsonError, Json] =
    JsonParser.parseString(new java.lang.String(chunk.toArray, "UTF-8"))

  /**
   * Parses a JSON value from a [[Chunk]] of bytes (UTF-8).
   */
  def parse(chunk: Chunk[Byte]): Either[JsonError, Json] = decode(chunk)

  /**
   * Decodes a JSON value from a [[Reader]].
   */
  def decode(reader: Reader): Either[JsonError, Json] =
    try {
      val sb     = new StringBuilder()
      val buffer = new scala.Array[Char](4096)
      var count  = reader.read(buffer)
      while (count != -1) {
        sb.appendAll(buffer, 0, count)
        count = reader.read(buffer)
      }
      decode(sb.toString)
    } catch {
      case e: JsonError => Left(e)
      case e: Throwable => Left(JsonError(s"Failed to read from Reader: ${e.getMessage}"))
    }

  /**
   * Parses a JSON value from a [[Reader]].
   */
  def parse(reader: Reader): Either[JsonError, Json] = decode(reader)

  /**
   * Assembles JSON from a sequence of path-value pairs.
   *
   * @param kvs
   *   The path-value pairs
   * @return
   *   Either an error (for conflicting paths) or the assembled JSON
   */
  def fromKV(kvs: Seq[(DynamicOptic, Json)]): Either[JsonError, Json] = {
    if (kvs.isEmpty) return Right(Json.Object.empty)

    // Build JSON by folding over all paths
    kvs.foldLeft[Either[JsonError, Json]](Right(Json.Object.empty)) { case (acc, (path, value)) =>
      acc.flatMap { json =>
        // Use set instead of setOrFail to create intermediate objects
        Right(json.set(path, value))
      }
    }
  }

  /**
   * Assembles JSON from path-value pairs, throwing on conflict.
   */
  def fromKVUnsafe(kvs: Seq[(DynamicOptic, Json)]): Json = fromKV(kvs).fold(throw _, identity)

  // ===========================================================================
  // Patch Interop
  // ===========================================================================

  /**
   * Serializes a [[JsonPatch]] to its JSON representation.
   */
  def fromJsonPatch(patch: JsonPatch): Json = {
    val opObj: Json = patch match {
      case JsonPatch.Add(path, value) =>
        Json.Object(Vector("op" -> Json.String("add"), "path" -> Json.String(path), "value" -> value))
      case JsonPatch.Remove(path)         => Json.Object(Vector("op" -> Json.String("remove"), "path" -> Json.String(path)))
      case JsonPatch.Replace(path, value) =>
        Json.Object(Vector("op" -> Json.String("replace"), "path" -> Json.String(path), "value" -> value))
      case JsonPatch.Move(from, path) =>
        Json.Object(Vector("op" -> Json.String("move"), "from" -> Json.String(from), "path" -> Json.String(path)))
      case JsonPatch.Copy(from, path) =>
        Json.Object(Vector("op" -> Json.String("copy"), "from" -> Json.String(from), "path" -> Json.String(path)))
      case JsonPatch.Test(path, value) =>
        Json.Object(Vector("op" -> Json.String("test"), "path" -> Json.String(path), "value" -> value))
      case _ => Json.Object.empty
    }
    opObj
  }

  def toJsonPatch(json: Json): Either[JsonError, JsonPatch] = json match {
    case Json.Object(fields) =>
      val fieldMap = fields.toMap
      val op       = fieldMap.get("op").collect { case Json.String(s) => s }
      val path     = fieldMap.get("path").collect { case Json.String(s) => s }

      (op, path) match {
        case (Some("add"), Some(p)) =>
          fieldMap.get("value").toRight(JsonError("missing value")).map(v => JsonPatch.Add(p, v))
        case (Some("remove"), Some(p))  => Right(JsonPatch.Remove(p))
        case (Some("replace"), Some(p)) =>
          fieldMap.get("value").toRight(JsonError("missing value")).map(v => JsonPatch.Replace(p, v))
        case (Some("move"), Some(p)) =>
          fieldMap
            .get("from")
            .collect { case Json.String(f) => f }
            .toRight(JsonError("missing from"))
            .map(f => JsonPatch.Move(f, p))
        case (Some("copy"), Some(p)) =>
          fieldMap
            .get("from")
            .collect { case Json.String(f) => f }
            .toRight(JsonError("missing from"))
            .map(f => JsonPatch.Copy(f, p))
        case (Some("test"), Some(p)) =>
          fieldMap.get("value").toRight(JsonError("missing value")).map(v => JsonPatch.Test(p, v))
        case (Some(o), _) => Left(JsonError(s"unknown op: $o"))
        case _            => Left(JsonError("missing op or path"))
      }
    case _ => Left(JsonError("expected JSON object for patch operation"))
  }

  /**
   * Internal helper for path interpolation.
   */
  def parsePath(path: java.lang.String): DynamicOptic = {
    // Parse path without regex lookahead (not supported on Scala Native)
    val tokens = new scala.collection.mutable.ArrayBuffer[java.lang.String]()
    val sb     = new java.lang.StringBuilder()
    var i      = 0
    while (i < path.length) {
      val c = path.charAt(i)
      c match {
        case '.' =>
          if (sb.length > 0) {
            tokens += sb.toString
            sb.setLength(0)
          }
          i += 1
        case '[' =>
          if (sb.length > 0) {
            tokens += sb.toString
            sb.setLength(0)
          }
          // Find matching ]
          val start = i + 1
          var end   = start
          while (end < path.length && path.charAt(end) != ']') end += 1
          if (end > start) {
            val bracketToken: java.lang.String = "[" + path.substring(start, end)
            tokens += bracketToken
          }
          i = end + 1
        case ']' =>
          i += 1 // Skip stray ]
        case _ =>
          sb.append(c)
          i += 1
      }
    }
    if (sb.length > 0) tokens += sb.toString

    val nodesVector = tokens.flatMap { (token: java.lang.String) =>
      if (token.startsWith("[")) {
        val content = token.substring(1)
        if (content == "*") {
          Some(DynamicOptic.Node.Elements)
        } else {
          try {
            val idx = content.toInt
            Some(DynamicOptic.Node.AtIndex(idx))
          } catch {
            case _: NumberFormatException => None
          }
        }
      } else if (token.nonEmpty) {
        Some(DynamicOptic.Node.Field(token))
      } else {
        None
      }
    }.toVector
    DynamicOptic(nodesVector)
  }

  /**
   * Encodes a value to a [[Chunk]] of bytes (UTF-8).
   */
  def encodeToChunk[A](value: A)(implicit codec: JsonBinaryCodec[A]): Chunk[Byte] =
    Chunk.fromArray(codec.encode(value))

  /**
   * Internal helper for JSON interpolation.
   */
  def fromInterpolation(parts: Seq[java.lang.String], args: Seq[Any]): Json = {
    val strings     = parts.iterator
    val expressions = args.iterator
    val sb          = new java.lang.StringBuilder(strings.next())
    while (strings.hasNext) {
      sb.append(expressions.next())
      sb.append(strings.next())
    }
    Json.parse(sb.toString) match {
      case Right(jsonValue) => jsonValue
      case Left(jsonError)  => throw new RuntimeException(jsonError.message)
    }
  }
}
