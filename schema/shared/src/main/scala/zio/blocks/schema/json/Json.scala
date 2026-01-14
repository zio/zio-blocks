package zio.blocks.schema.json

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue, SchemaError}
import java.io.{Reader, Writer}
import java.nio.ByteBuffer

/**
 * Represents a JSON value.
 *
 * The JSON data model consists of:
 *  - '''Objects''': Unordered collections of key-value pairs
 *  - '''Arrays''': Ordered sequences of values
 *  - '''Strings''': Unicode text
 *  - '''Numbers''': Numeric values (stored as strings for precision)
 *  - '''Booleans''': `true` or `false`
 *  - '''Null''': The null value
 *
 * ==Construction==
 * {{{
 * val obj = Json.Object("name" -> Json.String("Alice"), "age" -> Json.number(30))
 * val arr = Json.Array(Json.String("a"), Json.String("b"))
 * val str = Json.String("hello")
 * val num = Json.number(42)
 * val bool = Json.Boolean(true)
 * val nul = Json.Null
 * }}}
 *
 * ==Navigation==
 * {{{
 * json.get(p"users[0].name")   // JsonSelection
 * json("users")(0)("name")     // JsonSelection
 * json.fields                  // for objects
 * json.elements                // for arrays
 * }}}
 *
 * ==Pattern Matching==
 * {{{
 * json match {
 *   case Json.Object(fields) => ...
 *   case Json.Array(elements) => ...
 *   case Json.String(value) => ...
 *   case Json.Number(value) => ...
 *   case Json.Boolean(value) => ...
 *   case Json.Null => ...
 * }
 * }}}
 */
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
   * Returns a [[JsonSelection]] containing this value if it is null,
   * otherwise an empty selection.
   */
  def asNull: JsonSelection = if (isNull) JsonSelection(self) else JsonSelection.empty

  // ===========================================================================
  // Direct Accessors
  // ===========================================================================

  /**
   * If this is an object, returns its fields as key-value pairs.
   * Otherwise returns an empty sequence.
   */
  def fields: Seq[(String, Json)] = Seq.empty

  /**
   * If this is an array, returns its elements.
   * Otherwise returns an empty sequence.
   */
  def elements: Seq[Json] = Seq.empty

  /**
   * If this is a string, returns its value.
   * Otherwise returns `None`.
   */
  def stringValue: Option[String] = None

  /**
   * If this is a number, returns its string representation.
   * Otherwise returns `None`.
   */
  def numberValue: Option[String] = None

  /**
   * If this is a boolean, returns its value.
   * Otherwise returns `None`.
   */
  def booleanValue: Option[scala.Boolean] = None

  // ===========================================================================
  // Navigation
  // ===========================================================================

  /**
   * Navigates to values at the given path.
   *
   * {{{
   * json.get(p"users[0].name")
   * json.get(DynamicOptic.root.field("users").at(0).field("name"))
   * }}}
   *
   * @param path The path to navigate
   * @return A [[JsonSelection]] containing values at the path
   */
  def get(path: DynamicOptic): JsonSelection = {
    def navigate(current: Vector[Json], nodes: List[DynamicOptic.PathNode]): Vector[Json] = nodes match {
      case Nil => current
      case head :: tail => head match {
        case DynamicOptic.PathNode.Field(key) =>
          val next = current.flatMap {
            case Json.Object(flds) => flds.collectFirst { case (k, v) if k == key => v }
            case _ => None
          }
          navigate(next, tail)
        case DynamicOptic.PathNode.Index(idx) =>
          val next = current.flatMap {
            case Json.Array(elems) if idx >= 0 && idx < elems.size => Some(elems(idx))
            case _ => None
          }
          navigate(next, tail)
        case DynamicOptic.PathNode.Wildcard =>
          val next = current.flatMap {
            case Json.Array(elems) => elems
            case _ => Vector.empty
          }
          navigate(next, tail)
      }
    }
    
    val result = navigate(Vector(self), path.nodes.toList)
    if (result.isEmpty) JsonSelection.empty else JsonSelection(Right(result))
  }

  /**
   * Alias for [[get]].
   */
  def apply(path: DynamicOptic): JsonSelection = get(path)

  /**
   * If this is an array, returns a selection containing the element at the given index.
   * Returns an empty selection if not an array or index is out of bounds.
   *
   * @param index The array index (0-based)
   */
  def apply(index: Int): JsonSelection = self match {
    case Json.Array(elems) if index >= 0 && index < elems.size =>
      JsonSelection(elems(index))
    case _ =>
      JsonSelection.empty
  }

  /**
   * If this is an object, returns a selection containing the value at the given key.
   * Returns an empty selection if not an object or key is not present.
   *
   * @param key The object key
   */
  def apply(key: String): JsonSelection = self match {
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

  /**
   * Modifies values at the given path using the provided function.
   *
   * If the path does not exist, returns this JSON unchanged.
   *
   * {{{
   * json.modify(p"users[*].age", {
   *   case Json.Number(n) => Json.number(n.toInt + 1)
   *   case other => other
   * })
   * }}}
   *
   * @param path The path to values to modify
   * @param f The modification function
   * @return The modified JSON
   */
  def modify(path: DynamicOptic, f: Json => Json): Json = {
    def go(current: Json, nodes: List[DynamicOptic.PathNode]): Json = nodes match {
      case Nil => f(current)
      case DynamicOptic.PathNode.Field(key) :: tail => current match {
        case Json.Object(flds) =>
          Json.Object(flds.map {
            case (k, v) if k == key => (k, go(v, tail))
            case other => other
          })
        case other => other
      }
      case DynamicOptic.PathNode.Index(idx) :: tail => current match {
        case Json.Array(elems) if idx >= 0 && idx < elems.size =>
          Json.Array(elems.updated(idx, go(elems(idx), tail)))
        case other => other
      }
      case DynamicOptic.PathNode.Wildcard :: tail => current match {
        case Json.Array(elems) =>
          Json.Array(elems.map(e => go(e, tail)))
        case other => other
      }
    }
    go(self, path.nodes.toList)
  }

  /**
   * Modifies values at the given path using a partial function.
   *
   * Values for which the partial function is not defined are left unchanged.
   *
   * @param path The path to values to modify
   * @param pf The partial modification function
   * @return Either an error if the path is invalid, or the modified JSON
   */
  def modifyOrFail(path: DynamicOptic, pf: PartialFunction[Json, Json]): Either[JsonError, Json] = {
    Right(modify(path, j => pf.applyOrElse(j, (_: Json) => j)))
  }

  /**
   * Sets the value at the given path.
   *
   * If the path does not exist, attempts to create intermediate structure.
   * For array indices, the array must already exist and have sufficient length.
   *
   * {{{
   * json.set(p"user.name", Json.String("Bob"))
   * }}}
   *
   * @param path The path to set
   * @param value The value to set
   * @return The modified JSON
   */
  def set(path: DynamicOptic, value: Json): Json = modify(path, _ => value)

  /**
   * Sets the value at the given path, returning an error if the path is invalid.
   *
   * @param path The path to set
   * @param value The value to set
   * @return Either an error or the modified JSON
   */
  def setOrFail(path: DynamicOptic, value: Json): Either[JsonError, Json] = Right(set(path, value))

  /**
   * Deletes values at the given path.
   *
   * For object fields, removes the key-value pair.
   * For array elements, removes the element and shifts subsequent elements.
   *
   * @param path The path to delete
   * @return The modified JSON
   */
  def delete(path: DynamicOptic): Json = {
    def go(current: Json, nodes: List[DynamicOptic.PathNode]): Json = nodes match {
      case Nil => Json.Null  // Delete the value itself
      case DynamicOptic.PathNode.Field(key) :: Nil => current match {
        case Json.Object(flds) =>
          Json.Object(flds.filterNot(_._1 == key))
        case other => other
      }
      case DynamicOptic.PathNode.Field(key) :: tail => current match {
        case Json.Object(flds) =>
          Json.Object(flds.map {
            case (k, v) if k == key => (k, go(v, tail))
            case other => other
          })
        case other => other
      }
      case DynamicOptic.PathNode.Index(idx) :: Nil => current match {
        case Json.Array(elems) if idx >= 0 && idx < elems.size =>
          Json.Array(elems.take(idx) ++ elems.drop(idx + 1))
        case other => other
      }
      case DynamicOptic.PathNode.Index(idx) :: tail => current match {
        case Json.Array(elems) if idx >= 0 && idx < elems.size =>
          Json.Array(elems.updated(idx, go(elems(idx), tail)))
        case other => other
      }
      case DynamicOptic.PathNode.Wildcard :: tail => current match {
        case Json.Array(elems) =>
          Json.Array(elems.map(e => go(e, tail)))
        case other => other
      }
    }
    go(self, path.nodes.toList)
  }

  /**
   * Deletes values at the given path, returning an error if the path is invalid.
   *
   * @param path The path to delete
   * @return Either an error or the modified JSON
   */
  def deleteOrFail(path: DynamicOptic): Either[JsonError, Json] = Right(delete(path))

  /**
   * Inserts a value at the given path.
   *
   * For arrays, inserts at the specified index, shifting subsequent elements.
   * For objects, adds or replaces the key.
   *
   * @param path The path where to insert
   * @param value The value to insert
   * @return The modified JSON
   */
  def insert(path: DynamicOptic, value: Json): Json = {
    def go(current: Json, nodes: List[DynamicOptic.PathNode]): Json = nodes match {
      case Nil => value
      case DynamicOptic.PathNode.Field(key) :: Nil => current match {
        case Json.Object(flds) =>
          Json.Object(flds :+ (key -> value))
        case _ =>
          Json.Object(Vector((key, value)))
      }
      case DynamicOptic.PathNode.Field(key) :: tail => current match {
        case Json.Object(flds) =>
          flds.indexWhere(_._1 == key) match {
            case -1 =>
              Json.Object(flds :+ (key -> go(Json.Object(Vector.empty), tail)))
            case idx =>
              Json.Object(flds.updated(idx, (key, go(flds(idx)._2, tail))))
          }
        case _ =>
          Json.Object(Vector((key, go(Json.Object(Vector.empty), tail))))
      }
      case DynamicOptic.PathNode.Index(idx) :: Nil => current match {
        case Json.Array(elems) if idx >= 0 && idx <= elems.size =>
          Json.Array(elems.take(idx) ++ (value +: elems.drop(idx)))
        case other => other
      }
      case DynamicOptic.PathNode.Index(idx) :: tail => current match {
        case Json.Array(elems) if idx >= 0 && idx < elems.size =>
          Json.Array(elems.updated(idx, go(elems(idx), tail)))
        case other => other
      }
      case DynamicOptic.PathNode.Wildcard :: _ => current  // Can't insert with wildcard
    }
    go(self, path.nodes.toList)
  }

  /**
   * Inserts a value at the given path, returning an error if invalid.
   *
   * @param path The path where to insert
   * @param value The value to insert
   * @return Either an error or the modified JSON
   */
  def insertOrFail(path: DynamicOptic, value: Json): Either[JsonError, Json] = Right(insert(path, value))

  // ===========================================================================
  // Merging
  // ===========================================================================

  /**
   * Merges this JSON with another using the specified strategy.
   *
   * {{{
   * val merged = json1.merge(json2, MergeStrategy.Deep)
   * }}}
   *
   * @param other The JSON to merge with
   * @param strategy The merge strategy (default: [[MergeStrategy.Auto]])
   * @return The merged JSON
   */
  def merge(other: Json, strategy: MergeStrategy = MergeStrategy.Auto): Json = strategy.merge(self, other)

  // ===========================================================================
  // Patching
  // ===========================================================================

  /**
   * Applies a [[JsonPatch]] to this JSON.
   *
   * @param patch The patch to apply
   * @return Either an error if the patch cannot be applied, or the patched JSON
   */
  def patch(patch: JsonPatch): Either[JsonError, Json] = Left(JsonError("JsonPatch is not implemented (out of scope)", DynamicOptic.root))

  /**
   * Applies a [[JsonPatch]], throwing on failure.
   *
   * @param patch The patch to apply
   * @return The patched JSON
   * @throws JsonError if the patch cannot be applied
   */
  def patchUnsafe(patch: JsonPatch): Json = this.patch(patch).fold(throw _, identity)

  // ===========================================================================
  // Transformation
  // ===========================================================================

  /**
   * Transforms all values in this JSON bottom-up (children before parents).
   *
   * @param f The transformation function receiving path and value
   * @return The transformed JSON
   */
  def transformUp(f: (DynamicOptic, Json) => Json): Json = {
    def go(json: Json, path: DynamicOptic): Json = {
      val transformed = json match {
        case Json.Object(flds) =>
          Json.Object(flds.map { case (k, v) => (k, go(v, path.field(k))) })
        case Json.Array(elems) =>
          Json.Array(elems.zipWithIndex.map { case (e, i) => go(e, path.at(i)) })
        case other => other
      }
      f(path, transformed)
    }
    go(self, DynamicOptic.root)
  }

  /**
   * Transforms all values in this JSON top-down (parents before children).
   *
   * @param f The transformation function receiving path and value
   * @return The transformed JSON
   */
  def transformDown(f: (DynamicOptic, Json) => Json): Json = {
    def go(json: Json, path: DynamicOptic): Json = {
      val transformed = f(path, json)
      transformed match {
        case Json.Object(flds) =>
          Json.Object(flds.map { case (k, v) => (k, go(v, path.field(k))) })
        case Json.Array(elems) =>
          Json.Array(elems.zipWithIndex.map { case (e, i) => go(e, path.at(i)) })
        case other => other
      }
    }
    go(self, DynamicOptic.root)
  }

  /**
   * Transforms all object keys in this JSON.
   *
   * @param f The key transformation function receiving path and key
   * @return The transformed JSON
   */
  def transformKeys(f: (DynamicOptic, String) => String): Json = {
    def go(json: Json, path: DynamicOptic): Json = json match {
      case Json.Object(flds) =>
        Json.Object(flds.map { case (k, v) =>
          val newKey = f(path, k)
          (newKey, go(v, path.field(newKey)))
        })
      case Json.Array(elems) =>
        Json.Array(elems.zipWithIndex.map { case (e, i) => go(e, path.at(i)) })
      case other => other
    }
    go(self, DynamicOptic.root)
  }

  // ===========================================================================
  // Filtering
  // ===========================================================================

  /**
   * Removes entries matching the predicate.
   *
   * For objects, removes matching key-value pairs.
   * For arrays, removes matching elements.
   *
   * @param p The predicate receiving path and value
   * @return The filtered JSON
   */
  def filterNot(p: (DynamicOptic, Json) => scala.Boolean): Json = {
    def go(json: Json, path: DynamicOptic): Json = json match {
      case Json.Object(flds) =>
        Json.Object(flds.filterNot { case (k, v) => p(path.field(k), v) }.map {
          case (k, v) => (k, go(v, path.field(k)))
        })
      case Json.Array(elems) =>
        Json.Array(elems.zipWithIndex.filterNot { case (e, i) => p(path.at(i), e) }.map {
          case (e, i) => go(e, path.at(i))
        })
      case other => other
    }
    go(self, DynamicOptic.root)
  }

  /**
   * Keeps only entries matching the predicate.
   *
   * @param p The predicate receiving path and value
   * @return The filtered JSON
   */
  def filter(p: (DynamicOptic, Json) => scala.Boolean): Json =
    filterNot((path, json) => !p(path, json))

  // ===========================================================================
  // Projection
  // ===========================================================================

  /**
   * Projects this JSON to include only the specified paths.
   *
   * Paths that don't exist are ignored. Structure is preserved.
   *
   * {{{
   * json.project(p"user.name", p"user.email", p"meta.created")
   * }}}
   *
   * @param paths The paths to include
   * @return A new JSON containing only the specified paths
   */
  def project(paths: DynamicOptic*): Json = {
    if (paths.isEmpty) return Json.Object(Vector.empty)
    
    val pathSet = paths.toSet
    filter((path, _) => pathSet.exists(p => path.nodes.startsWith(p.nodes) || p.nodes.startsWith(path.nodes)))
  }

  // ===========================================================================
  // Splitting / Partitioning
  // ===========================================================================

  /**
   * Partitions this JSON into two based on a predicate.
   *
   * Returns a tuple where the first element contains entries satisfying
   * the predicate, and the second contains entries that don't.
   *
   * @param p The predicate receiving path and value
   * @return A tuple of (matching, non-matching) JSON values
   */
  def partition(p: (DynamicOptic, Json) => scala.Boolean): (Json, Json) = {
    (filter(p), filterNot(p))
  }

  // ===========================================================================
  // Normalization
  // ===========================================================================

  /**
   * Returns a normalized version of this JSON.
   *
   * Normalization includes:
   *  - Sorting object keys alphabetically
   *  - Normalizing number representations
   *
   * Useful for comparison and hashing.
   */
  def normalize: Json = self match {
    case Json.Object(flds) =>
      Json.Object(flds.map { case (k, v) => (k, v.normalize) }.sortBy(_._1))
    case Json.Array(elems) =>
      Json.Array(elems.map(_.normalize))
    case Json.Number(v) =>
      // Normalize number representation
      val bd = BigDecimal(v)
      Json.Number(bd.toString)
    case other => other
  }

  /**
   * Returns this JSON with all object keys sorted alphabetically (recursive).
   */
  def sortKeys: Json = self match {
    case Json.Object(flds) =>
      Json.Object(flds.map { case (k, v) => (k, v.sortKeys) }.sortBy(_._1))
    case Json.Array(elems) =>
      Json.Array(elems.map(_.sortKeys))
    case other =>
      other
  }

  /**
   * Returns this JSON with all null values removed from objects.
   */
  def dropNulls: Json = self match {
    case Json.Object(flds) =>
      Json.Object(flds.collect { case (k, v) if !v.isNull => (k, v.dropNulls) })
    case Json.Array(elems) =>
      Json.Array(elems.map(_.dropNulls))
    case other =>
      other
  }

  /**
   * Returns this JSON with empty objects and arrays removed.
   */
  def dropEmpty: Json = self match {
    case Json.Object(flds) =>
      val filtered = flds.collect {
        case (k, v) =>
          val dropped = v.dropEmpty
          dropped match {
            case Json.Object(f) if f.isEmpty => None
            case Json.Array(e) if e.isEmpty  => None
            case other                       => Some((k, other))
          }
      }.flatten
      Json.Object(filtered)
    case Json.Array(elems) =>
      val filtered = elems.map(_.dropEmpty).filter {
        case Json.Object(f) if f.isEmpty => false
        case Json.Array(e) if e.isEmpty  => false
        case _                           => true
      }
      Json.Array(filtered)
    case other =>
      other
  }

  // ===========================================================================
  // Diffing
  // ===========================================================================

  /**
   * Computes a [[JsonPatch]] that transforms this JSON into the target.
   *
   * {{{
   * val patch = source.diff(target)
   * source.patch(patch) == Right(target) // true
   * }}}
   *
   * @param target The target JSON
   * @return A patch that transforms this into target
   */
  def diff(target: Json): JsonPatch = {
    // Stub - JsonPatch is out of scope
    JsonPatch.empty
  }

  // ===========================================================================
  // Folding
  // ===========================================================================

  /**
   * Folds over this JSON top-down (parents before children).
   *
   * @param z The initial accumulator value
   * @param f The fold function receiving path, value, and accumulator
   * @tparam B The accumulator type
   * @return The final accumulated value
   */
  def foldDown[B](z: B)(f: (DynamicOptic, Json, B) => B): B = {
    def go(json: Json, path: DynamicOptic, acc: B): B = {
      val newAcc = f(path, json, acc)
      json match {
        case Json.Object(flds) =>
          flds.foldLeft(newAcc) { case (a, (k, v)) => go(v, path.field(k), a) }
        case Json.Array(elems) =>
          elems.zipWithIndex.foldLeft(newAcc) { case (a, (e, i)) => go(e, path.at(i), a) }
        case _ => newAcc
      }
    }
    go(self, DynamicOptic.root, z)
  }

  /**
   * Folds over this JSON bottom-up (children before parents).
   *
   * @param z The initial accumulator value
   * @param f The fold function receiving path, value, and accumulator
   * @tparam B The accumulator type
   * @return The final accumulated value
   */
  def foldUp[B](z: B)(f: (DynamicOptic, Json, B) => B): B = {
    def go(json: Json, path: DynamicOptic, acc: B): B = {
      val newAcc = json match {
        case Json.Object(flds) =>
          flds.foldLeft(acc) { case (a, (k, v)) => go(v, path.field(k), a) }
        case Json.Array(elems) =>
          elems.zipWithIndex.foldLeft(acc) { case (a, (e, i)) => go(e, path.at(i), a) }
        case _ => acc
      }
      f(path, json, newAcc)
    }
    go(self, DynamicOptic.root, z)
  }

  /**
   * Folds over this JSON top-down, allowing the fold function to fail.
   *
   * Short-circuits on first failure.
   *
   * @param z The initial accumulator value
   * @param f The fold function that may fail
   * @tparam B The accumulator type
   * @return Either an error or the final accumulated value
   */
  def foldDownOrFail[B](z: B)(f: (DynamicOptic, Json, B) => Either[JsonError, B]): Either[JsonError, B] = {
    def go(json: Json, path: DynamicOptic, acc: B): Either[JsonError, B] = {
      f(path, json, acc).flatMap { newAcc =>
        json match {
          case Json.Object(flds) =>
            flds.foldLeft[Either[JsonError, B]](Right(newAcc)) {
              case (Right(a), (k, v)) => go(v, path.field(k), a)
              case (left, _) => left
            }
          case Json.Array(elems) =>
            elems.zipWithIndex.foldLeft[Either[JsonError, B]](Right(newAcc)) {
              case (Right(a), (e, i)) => go(e, path.at(i), a)
              case (left, _) => left
            }
          case _ => Right(newAcc)
        }
      }
    }
    go(self, DynamicOptic.root, z)
  }

  /**
   * Folds over this JSON bottom-up, allowing the fold function to fail.
   *
   * Short-circuits on first failure.
   *
   * @param z The initial accumulator value
   * @param f The fold function that may fail
   * @tparam B The accumulator type
   * @return Either an error or the final accumulated value
   */
  def foldUpOrFail[B](z: B)(f: (DynamicOptic, Json, B) => Either[JsonError, B]): Either[JsonError, B] = {
    def go(json: Json, path: DynamicOptic, acc: B): Either[JsonError, B] = {
      val result = json match {
        case Json.Object(flds) =>
          flds.foldLeft[Either[JsonError, B]](Right(acc)) {
            case (Right(a), (k, v)) => go(v, path.field(k), a)
            case (left, _) => left
          }
        case Json.Array(elems) =>
          elems.zipWithIndex.foldLeft[Either[JsonError, B]](Right(acc)) {
            case (Right(a), (e, i)) => go(e, path.at(i), a)
            case (left, _) => left
          }
        case _ => Right(acc)
      }
      result.flatMap(newAcc => f(path, json, newAcc))
    }
    go(self, DynamicOptic.root, z)
  }

  // ===========================================================================
  // Querying
  // ===========================================================================

  /**
   * Selects all values matching the predicate.
   *
   * @param p The predicate receiving path and value
   * @return A [[JsonSelection]] containing matching values
   */
  def query(p: (DynamicOptic, Json) => scala.Boolean): JsonSelection = {
    val results = foldDown(Vector.empty[Json]) { (path, json, acc) =>
      if (p(path, json)) acc :+ json else acc
    }
    if (results.isEmpty) JsonSelection.empty else JsonSelection(Right(results))
  }

  // ===========================================================================
  // Validation
  // ===========================================================================

  /**
   * Validates this JSON against a [[JsonSchema]].
   *
   * @param schema The schema to validate against
   * @return `None` if valid, `Some(error)` if invalid
   */
  def check(schema: JsonSchema): Option[SchemaError] = schema.validate(self, DynamicOptic.root)

  /**
   * Returns `true` if this JSON conforms to the given [[JsonSchema]].
   */
  def conforms(schema: JsonSchema): scala.Boolean = check(schema).isEmpty

  // ===========================================================================
  // KV Representation
  // ===========================================================================

  /**
   * Flattens this JSON to a sequence of path-value pairs.
   *
   * Only leaf values (primitives, empty arrays, empty objects) are included.
   *
   * {{{
   * Json.parse("""{"a": {"b": 1}, "c": [2, 3]}""").toKV
   * // Seq(
   * //   (p"a.b", Json.Number("1")),
   * //   (p"c[0]", Json.Number("2")),
   * //   (p"c[1]", Json.Number("3"))
   * // )
   * }}}
   */
  def toKV: Seq[(DynamicOptic, Json)] = {
    def isLeaf(json: Json): scala.Boolean = json match {
      case Json.Object(flds) if flds.isEmpty => true
      case Json.Array(elems) if elems.isEmpty => true
      case Json.Object(_) | Json.Array(_) => false
      case _ => true
    }
    
    def go(json: Json, path: DynamicOptic, acc: Vector[(DynamicOptic, Json)]): Vector[(DynamicOptic, Json)] = {
      if (isLeaf(json)) {
        acc :+ (path -> json)
      } else {
        json match {
          case Json.Object(flds) =>
            flds.foldLeft(acc) { case (a, (k, v)) => go(v, path.field(k), a) }
          case Json.Array(elems) =>
            elems.zipWithIndex.foldLeft(acc) { case (a, (e, i)) => go(e, path.at(i), a) }
          case _ => acc :+ (path -> json)
        }
      }
    }
    go(self, DynamicOptic.root, Vector.empty)
  }

  // ===========================================================================
  // Comparison
  // ===========================================================================

  /**
   * Compares this JSON to another for ordering.
   *
   * Ordering is defined as:
   *  1. Null < Boolean < Number < String < Array < Object
   *  2. Within types, natural ordering applies
   */
  def compare(that: Json): Int = (self, that) match {
    case (Json.Null, Json.Null)               => 0
    case (Json.Null, _)                       => -1
    case (_, Json.Null)                       => 1
    case (Json.Boolean(a), Json.Boolean(b))   => a.compare(b)
    case (Json.Boolean(_), _)                 => -1
    case (_, Json.Boolean(_))                 => 1
    case (Json.Number(a), Json.Number(b))     => BigDecimal(a).compare(BigDecimal(b))
    case (Json.Number(_), _)                  => -1
    case (_, Json.Number(_))                  => 1
    case (Json.String(a), Json.String(b))     => a.compare(b)
    case (Json.String(_), _)                  => -1
    case (_, Json.String(_))                  => 1
    case (Json.Array(a), Json.Array(b))       => compareArrays(a, b)
    case (Json.Array(_), _)                   => -1
    case (_, Json.Array(_))                   => 1
    case (Json.Object(a), Json.Object(b))     => compareObjects(a, b)
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

  private def compareObjects(a: Vector[(String, Json)], b: Vector[(String, Json)]): Int = {
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
  // DynamicValue Interop
  // ===========================================================================

  /**
   * Converts this JSON to a [[DynamicValue]].
   *
   * This conversion is lossless; all JSON values can be represented as DynamicValue.
   */
  def toDynamicValue: DynamicValue = self match {
    case Json.Null =>
      DynamicValue.Primitive(PrimitiveValue.Unit)
    case Json.Boolean(v) =>
      DynamicValue.Primitive(PrimitiveValue.Boolean(v))
    case Json.Number(v) =>
      // Preserve as BigDecimal for maximum precision
      DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(v)))
    case Json.String(v) =>
      DynamicValue.Primitive(PrimitiveValue.String(v))
    case Json.Array(elems) =>
      DynamicValue.Sequence(elems.map(_.toDynamicValue))
    case Json.Object(flds) =>
      DynamicValue.Record(flds.map { case (k, v) => (k, v.toDynamicValue) })
  }

  // ===========================================================================
  // Typed Decoding (Json => A)
  // ===========================================================================

  /**
   * Decodes this JSON to a typed value.
   *
   * Uses implicit [[JsonDecoder]] which prefers explicit codecs over schema derivation.
   *
   * {{{
   * val person: Either[JsonError, Person] = json.as[Person]
   * }}}
   *
   * @tparam A The target type
   * @return Either an error or the decoded value
   */
  def as[A](implicit decoder: JsonDecoder[A]): Either[JsonError, A] = decoder.decode(self)

  /**
   * Decodes this JSON to a typed value, throwing on failure.
   *
   * @tparam A The target type
   * @return The decoded value
   * @throws JsonError if decoding fails
   */
  def asUnsafe[A](implicit decoder: JsonDecoder[A]): A = as[A].fold(throw _, identity)

  /**
   * Internal: decode using an explicit codec.
   */
  private[json] def decodeWith[A](codec: JsonBinaryCodec[A]): Either[JsonError, A] =
    JsonBridge.decodeJsonWith(self, codec)

  // ===========================================================================
  // Encoding (Json => String/Bytes)
  // ===========================================================================

  /**
   * Encodes this JSON to a compact string (no extra whitespace).
   */
  def print: String = encode(WriterConfig)

  /**
   * Encodes this JSON to a string using the specified configuration.
   *
   * @param config Writer configuration (indentation, unicode escaping, etc.)
   */
  def print(config: WriterConfig): String = encode(config)

  /**
   * Alias for [[print]].
   */
  def encode: String = encode(WriterConfig)

  /**
   * Encodes this JSON to a string using the specified configuration.
   *
   * @param config Writer configuration
   */
  def encode(config: WriterConfig): String = {
    val writer = new JsonWriter(
      buf = Array.emptyByteArray,
      limit = 0,
      config = config,
      stack = zio.blocks.schema.binding.Registers(0)
    )
    JsonBridge.writeJson(self, writer)
    new String(writer.toByteArray, java.nio.charset.StandardCharsets.UTF_8)
  }

  /**
   * Encodes this JSON and writes to the provided [[Writer]].
   *
   * @param writer The writer to write to
   */
  def printTo(writer: Writer): Unit = printTo(writer, WriterConfig)

  /**
   * Encodes this JSON and writes to the provided [[Writer]] with configuration.
   *
   * @param writer The writer to write to
   * @param config Writer configuration
   */
  def printTo(writer: Writer, config: WriterConfig): Unit = {
    writer.write(encode(config))
  }

  /**
   * Encodes this JSON to a byte array (UTF-8).
   */
  def encodeToBytes: Array[Byte] = encodeToBytes(WriterConfig)

  /**
   * Encodes this JSON to a byte array (UTF-8) with configuration.
   *
   * @param config Writer configuration
   */
  def encodeToBytes(config: WriterConfig): Array[Byte] = {
    val writer = new JsonWriter(
      buf = Array.emptyByteArray,
      limit = 0,
      config = config,
      stack = zio.blocks.schema.binding.Registers(0)
    )
    JsonBridge.writeJson(self, writer)
    writer.toByteArray
  }

  /**
   * Encodes this JSON to a [[Chunk]] of bytes (UTF-8).
   */
  def encodeToChunk: Chunk[Byte] = encodeToChunk(WriterConfig)

  /**
   * Encodes this JSON to a [[Chunk]] of bytes (UTF-8) with configuration.
   *
   * @param config Writer configuration
   */
  def encodeToChunk(config: WriterConfig): Chunk[Byte] = 
    Chunk.fromArray(encodeToBytes(config))

  /**
   * Encodes this JSON into the provided [[ByteBuffer]].
   *
   * @param buffer The buffer to write to
   */
  def encodeTo(buffer: ByteBuffer): Unit = encodeTo(buffer, WriterConfig)

  /**
   * Encodes this JSON into the provided [[ByteBuffer]] with configuration.
   *
   * @param buffer The buffer to write to
   * @param config Writer configuration
   */
  def encodeTo(buffer: ByteBuffer, config: WriterConfig): Unit = {
    val bytes = encodeToBytes(config)
    buffer.put(bytes)
  }

  // ===========================================================================
  // Standard Methods
  // ===========================================================================

  override def hashCode(): Int = self match {
    case Json.Null           => 0
    case Json.Boolean(v)     => v.hashCode()
    case Json.Number(v)      => BigDecimal(v).hashCode()
    case Json.String(v)      => v.hashCode()
    case Json.Array(elems)   => elems.hashCode()
    case Json.Object(flds)   => flds.sortBy(_._1).hashCode()
  }

  override def equals(that: Any): Boolean = that match {
    case other: Json => compare(other) == 0
    case _           => false
  }

  override def toString: String = print
}

object Json {

  // ===========================================================================
  // ADT Cases
  // ===========================================================================

  /**
   * A JSON object: an unordered collection of key-value pairs.
   *
   * @param fields The key-value pairs. Keys should be unique; if duplicates
   *               are present, behavior of accessors is undefined.
   */
  final case class Object(fields: Vector[(String, Json)]) extends Json {
    override def isObject: scala.Boolean                = true
    override def fields: Seq[(String, Json)]            = fields
  }

  object Object {

    /**
     * Creates an empty JSON object.
     */
    val empty: Object = Object(Vector.empty)

    /**
     * Creates a JSON object from key-value pairs.
     */
    def apply(fields: (String, Json)*): Object = Object(fields.toVector)
  }

  /**
   * A JSON array: an ordered sequence of values.
   *
   * @param elements The array elements
   */
  final case class Array(elements: Vector[Json]) extends Json {
    override def isArray: scala.Boolean  = true
    override def elements: Seq[Json]     = elements
  }

  object Array {

    /**
     * Creates an empty JSON array.
     */
    val empty: Array = Array(Vector.empty)

    /**
     * Creates a JSON array from elements.
     */
    def apply(elements: Json*): Array = Array(elements.toVector)
  }

  /**
   * A JSON string.
   *
   * @param value The string value (unescaped)
   */
  final case class String(value: java.lang.String) extends Json {
    override def isString: scala.Boolean              = true
    override def stringValue: Option[java.lang.String] = Some(value)
  }

  /**
   * A JSON number.
   *
   * Stored as a string to preserve exact representation (precision, trailing zeros, etc.).
   * Provides lazy conversion to numeric types.
   *
   * @param value The number as a string (should be valid JSON number syntax)
   */
  final case class Number(value: java.lang.String) extends Json {
    override def isNumber: scala.Boolean                = true
    override def numberValue: Option[java.lang.String]  = Some(value)

    /**
     * Converts to `Int`, truncating if necessary.
     */
    lazy val toInt: Int = toBigDecimal.toInt

    /**
     * Converts to `Long`, truncating if necessary.
     */
    lazy val toLong: Long = toBigDecimal.toLong

    /**
     * Converts to `Float`.
     */
    lazy val toFloat: Float = value.toFloat

    /**
     * Converts to `Double`.
     */
    lazy val toDouble: Double = value.toDouble

    /**
     * Converts to `BigInt`, truncating fractional part.
     */
    lazy val toBigInt: BigInt = toBigDecimal.toBigInt

    /**
     * Converts to `BigDecimal` (lossless).
     */
    lazy val toBigDecimal: BigDecimal = BigDecimal(value)
  }

  /**
   * A JSON boolean.
   *
   * @param value The boolean value
   */
  final case class Boolean(value: scala.Boolean) extends Json {
    override def isBoolean: scala.Boolean              = true
    override def booleanValue: Option[scala.Boolean]   = Some(value)
  }

  object Boolean {
    val True: Boolean  = Boolean(true)
    val False: Boolean = Boolean(false)
  }

  /**
   * The JSON null value.
   */
  case object Null extends Json {
    override def isNull: scala.Boolean = true
  }

  // ===========================================================================
  // Convenience Constructors
  // ===========================================================================

  /**
   * Creates a JSON number from an `Int`.
   */
  def number(n: Int): Number = Number(n.toString)

  /**
   * Creates a JSON number from a `Long`.
   */
  def number(n: Long): Number = Number(n.toString)

  /**
   * Creates a JSON number from a `Float`.
   */
  def number(n: Float): Number = Number(n.toString)

  /**
   * Creates a JSON number from a `Double`.
   */
  def number(n: Double): Number = Number(n.toString)

  /**
   * Creates a JSON number from a `BigInt`.
   */
  def number(n: BigInt): Number = Number(n.toString)

  /**
   * Creates a JSON number from a `BigDecimal`.
   */
  def number(n: BigDecimal): Number = Number(n.toString)

  /**
   * Creates a JSON number from a `Short`.
   */
  def number(n: Short): Number = Number(n.toString)

  /**
   * Creates a JSON number from a `Byte`.
   */
  def number(n: Byte): Number = Number(n.toString)

  // ===========================================================================
  // Parsing / Decoding (String/Bytes => Json)
  // ===========================================================================

  /**
   * Parses a JSON value from a string.
   *
   * @param s The JSON string
   * @return Either a [[JsonError]] or the parsed JSON
   */
  def parse(s: java.lang.String): Either[JsonError, Json] = decode(s)

  /**
   * Parses a JSON value from a `CharSequence`.
   *
   * @param s The JSON character sequence
   * @return Either a [[JsonError]] or the parsed JSON
   */
  def parse(s: CharSequence): Either[JsonError, Json] = decode(s)

  /**
   * Parses a JSON value from a byte array (UTF-8).
   *
   * @param bytes The JSON bytes
   * @return Either a [[JsonError]] or the parsed JSON
   */
  def parse(bytes: scala.Array[Byte]): Either[JsonError, Json] = decode(bytes)

  /**
   * Parses a JSON value from a [[Chunk]] of bytes (UTF-8).
   *
   * @param chunk The JSON bytes
   * @return Either a [[JsonError]] or the parsed JSON
   */
  def parse(chunk: Chunk[Byte]): Either[JsonError, Json] = decode(chunk)

  /**
   * Parses a JSON value from a [[ByteBuffer]] (UTF-8).
   *
   * @param buffer The JSON bytes
   * @return Either a [[JsonError]] or the parsed JSON
   */
  def parse(buffer: ByteBuffer): Either[JsonError, Json] = decode(buffer)

  /**
   * Parses a JSON value from a [[Reader]].
   *
   * @param reader The reader to read from
   * @return Either a [[JsonError]] or the parsed JSON
   */
  def parse(reader: Reader): Either[JsonError, Json] = decode(reader)

  /**
   * Decodes a JSON value from a string.
   */
  def decode(s: java.lang.String): Either[JsonError, Json] = {
    try {
      val bytes = s.getBytes(java.nio.charset.StandardCharsets.UTF_8)
      val reader = new JsonReader(
        buf = bytes,
        charBuf = new Array[Char](ReaderConfig.preferredCharBufSize),
        config = ReaderConfig,
        stack = zio.blocks.schema.binding.Registers(0)
      )
      Right(JsonBridge.readJson(reader))
    } catch {
      case e: JsonBinaryCodecError =>
        Left(JsonError.parseError(e.getMessage, e.offset, e.line, e.column))
      case e: Exception =>
        Left(JsonError.parseError(e.getMessage, 0, 0, 0))
    }
  }

  /**
   * Decodes a JSON value from a `CharSequence`.
   */
  def decode(s: CharSequence): Either[JsonError, Json] = 
    decode(s.toString)

  /**
   * Decodes a JSON value from a byte array (UTF-8).
   */
  def decode(bytes: scala.Array[Byte]): Either[JsonError, Json] = {
    try {
      val reader = new JsonReader(
        buf = bytes,
        charBuf = new Array[Char](ReaderConfig.preferredCharBufSize),
        config = ReaderConfig,
        stack = zio.blocks.schema.binding.Registers(0)
      )
      Right(JsonBridge.readJson(reader))
    } catch {
      case e: JsonBinaryCodecError =>
        Left(JsonError.parseError(e.getMessage, e.offset, e.line, e.column))
      case e: Exception =>
        Left(JsonError.parseError(e.getMessage, 0, 0, 0))
    }
  }

  /**
   * Decodes a JSON value from a [[Chunk]] of bytes (UTF-8).
   */
  def decode(chunk: Chunk[Byte]): Either[JsonError, Json] = 
    decode(chunk.toArray)

  /**
   * Decodes a JSON value from a [[ByteBuffer]] (UTF-8).
   */
  def decode(buffer: ByteBuffer): Either[JsonError, Json] = {
    val bytes = new Array[Byte](buffer.remaining())
    buffer.get(bytes)
    decode(bytes)
  }

  /**
   * Decodes a JSON value from a [[Reader]].
   */
  def decode(reader: Reader): Either[JsonError, Json] = {
    try {
      val sb = new StringBuilder
      val buffer = new Array[Char](8192)
      var n = reader.read(buffer)
      while (n != -1) {
        sb.appendAll(buffer, 0, n)
        n = reader.read(buffer)
      }
      decode(sb.toString)
    } catch {
      case e: Exception =>
        Left(JsonError.parseError(s"Failed to read from Reader: ${e.getMessage}", 0, 0, 0))
    }
  }

  /**
   * Parses a JSON value from a string, throwing on failure.
   *
   * @param s The JSON string
   * @return The parsed JSON
   * @throws JsonError if parsing fails
   */
  def parseUnsafe(s: java.lang.String): Json = decode(s).fold(throw _, identity)

  /**
   * Alias for [[parseUnsafe]].
   */
  def decodeUnsafe(s: java.lang.String): Json = parseUnsafe(s)

  // ===========================================================================
  // Typed Encoding (A => Json)
  // ===========================================================================

  /**
   * Encodes a typed value to JSON.
   *
   * Uses implicit [[JsonEncoder]] which prefers explicit codecs over schema derivation.
   *
   * {{{
   * val json = Json.from(Person("Alice", 30))
   * }}}
   *
   * @param value The value to encode
   * @return The encoded JSON
   */
  def from[A](value: A)(implicit encoder: JsonEncoder[A]): Json = encoder.encode(value)

  /**
   * Internal: encode using an explicit codec.
   */
  private[json] def encodeWith[A](value: A, codec: JsonBinaryCodec[A]): Json =
    JsonBridge.encodeJsonWith(value, codec)

  // ===========================================================================
  // DynamicValue Interop
  // ===========================================================================

  /**
   * Converts a [[DynamicValue]] to JSON.
   *
   * This conversion is lossy for `DynamicValue` types that have no JSON equivalent:
   *  - `PrimitiveValue` types like `java.time.*` are converted to strings
   *  - `DynamicValue.Variant` uses a discriminator field
   *
   * @param value The dynamic value to convert
   * @return The JSON representation
   */
  def fromDynamicValue(value: DynamicValue): Json = value match {
    case DynamicValue.Primitive(pv) => fromPrimitiveValue(pv)
    case DynamicValue.Record(flds) =>
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
  // KV Interop
  // ===========================================================================

  /**
   * Assembles JSON from a sequence of path-value pairs.
   *
   * {{{
   * Json.fromKV(Seq(
   *   p"a.b" -> Json.number(1),
   *   p"a.c" -> Json.String("x"),
   *   p"d[0]" -> Json.Boolean(true)
   * ))
   * // {"a": {"b": 1, "c": "x"}, "d": [true]}
   * }}}
   *
   * @param kvs The path-value pairs
   * @return Either an error (for conflicting paths) or the assembled JSON
   */
  def fromKV(kvs: Seq[(DynamicOptic, Json)]): Either[JsonError, Json] = {
    if (kvs.isEmpty) return Right(Json.Object(Vector.empty))
    
    // Build up the JSON structure by setting each path
    kvs.foldLeft[Either[JsonError, Json]](Right(Json.Object(Vector.empty))) {
      case (Right(acc), (path, value)) =>
        if (path.nodes.isEmpty) {
          Right(value)
        } else {
          Right(acc.set(path, value))
        }
      case (left, _) => left
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
   *
   * The format follows RFC 6902 (JSON Patch) for standard operations,
   * with extensions for LCS-based sequence diffs.
   *
   * @param patch The patch to serialize
   * @return The JSON representation of the patch
   */
  def fromJsonPatch(patch: JsonPatch): Json = {
    // Stub - JsonPatch is out of scope
    Json.Array(Vector.empty)
  }

  /**
   * Deserializes a JSON representation into a [[JsonPatch]].
   *
   * @param json The JSON patch representation
   * @return Either an error or the parsed patch
   */
  def toJsonPatch(json: Json): Either[JsonError, JsonPatch] = {
    // Stub - JsonPatch is out of scope
    Left(JsonError("JsonPatch is not implemented (out of scope)", DynamicOptic.root))
  }

  // ===========================================================================
  // Ordering
  // ===========================================================================

  /**
   * Ordering for JSON values.
   *
   * Order: Null < Boolean < Number < String < Array < Object
   */
  implicit val ordering: Ordering[Json] = (x: Json, y: Json) => x.compare(y)
}