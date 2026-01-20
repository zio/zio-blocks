package zio.blocks.schema.json

import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue}

/**
 * Represents a JSON value.
 *
 * The JSON data model consists of:
 *   - '''Objects''': Unordered collections of key-value pairs
 *   - '''Arrays''': Ordered sequences of values
 *   - '''Strings''': Unicode text
 *   - '''Numbers''': Numeric values (stored as strings for precision)
 *   - '''Booleans''': `true` or `false`
 *   - '''Null''': The null value
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
  def isObject: scala.Boolean = false

  /**
   * Returns `true` if this is a JSON array.
   */
  def isArray: scala.Boolean = false

  /**
   * Returns `true` if this is a JSON string.
   */
  def isString: scala.Boolean = false

  /**
   * Returns `true` if this is a JSON number.
   */
  def isNumber: scala.Boolean = false

  /**
   * Returns `true` if this is a JSON boolean.
   */
  def isBoolean: scala.Boolean = false

  /**
   * Returns `true` if this is JSON null.
   */
  def isNull: scala.Boolean = false

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

  /**
   * If this is an object, returns its fields as key-value pairs. Otherwise
   * returns an empty sequence.
   */
  def fields: Seq[(java.lang.String, Json)] = Seq.empty

  /**
   * If this is an array, returns its elements. Otherwise returns an empty
   * sequence.
   */
  def elements: Seq[Json] = Seq.empty

  /**
   * If this is a string, returns its value. Otherwise returns `None`.
   */
  def stringValue: Option[java.lang.String] = None

  /**
   * If this is a number, returns its string representation. Otherwise returns
   * `None`.
   */
  def numberValue: Option[java.lang.String] = None

  /**
   * If this is a boolean, returns its value. Otherwise returns `None`.
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
   * @param path
   *   The path to navigate
   * @return
   *   A [[JsonSelection]] containing values at the path
   */
  def get(path: DynamicOptic): JsonSelection = {
    import DynamicOptic.Node

    def navigate(current: Vector[Json], nodes: IndexedSeq[Node]): Vector[Json] = {
      if (nodes.isEmpty) return current

      val node = nodes.head
      val rest = nodes.tail

      val next = node match {
        case Node.Field(name) =>
          current.flatMap {
            case Json.Object(flds) => flds.collectFirst { case (k, v) if k == name => v }
            case _                 => None
          }

        case Node.AtIndex(index) =>
          current.flatMap {
            case Json.Array(elems) if index >= 0 && index < elems.size => Some(elems(index))
            case _                                                     => None
          }

        case Node.Elements =>
          current.flatMap {
            case Json.Array(elems) => elems
            case _                 => Vector.empty
          }

        case Node.AtIndices(indices) =>
          current.flatMap {
            case Json.Array(elems) =>
              indices.flatMap { idx =>
                if (idx >= 0 && idx < elems.size) Some(elems(idx)) else None
              }
            case _ => Vector.empty
          }

        case Node.MapKeys =>
          current.flatMap {
            case Json.Object(flds) => flds.map { case (k, _) => Json.String(k) }
            case _                 => Vector.empty
          }

        case Node.MapValues =>
          current.flatMap {
            case Json.Object(flds) => flds.map(_._2)
            case _                 => Vector.empty
          }

        case Node.AtMapKey(key) =>
          // For JSON, map keys are strings, so convert DynamicValue to string
          val keyStr = key match {
            case DynamicValue.Primitive(PrimitiveValue.String(s)) => s
            case _                                                => key.toString
          }
          current.flatMap {
            case Json.Object(flds) => flds.collectFirst { case (k, v) if k == keyStr => v }
            case _                 => None
          }

        case Node.AtMapKeys(keys) =>
          val keyStrs = keys.map {
            case DynamicValue.Primitive(PrimitiveValue.String(s)) => s
            case other                                            => other.toString
          }
          current.flatMap {
            case Json.Object(flds) =>
              keyStrs.flatMap { keyStr =>
                flds.collectFirst { case (k, v) if k == keyStr => v }
              }
            case _ => Vector.empty
          }

        case Node.Case(_) | Node.Wrapped =>
          // Not applicable to JSON (these are for variants/newtypes)
          current
      }

      navigate(next, rest)
    }

    JsonSelection(navigate(Vector(self), path.nodes))
  }

  /**
   * Alias for [[get]].
   */
  def apply(path: DynamicOptic): JsonSelection = get(path)

  /**
   * If this is an array, returns a selection containing the element at the
   * given index. Returns an empty selection if not an array or index is out of
   * bounds.
   *
   * @param index
   *   The array index (0-based)
   */
  def apply(index: Int): JsonSelection = self match {
    case Json.Array(elems) if index >= 0 && index < elems.size =>
      JsonSelection(elems(index))
    case _ =>
      JsonSelection.empty
  }

  /**
   * If this is an object, returns a selection containing the value at the given
   * key. Returns an empty selection if not an object or key is not present.
   *
   * @param key
   *   The object key
   */
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
  // Comparison
  // ===========================================================================

  /**
   * Compares this JSON to another for ordering.
   *
   * Ordering is defined as:
   *   1. Null < Boolean < Number < String < Array < Object
   *   2. Within types, natural ordering applies
   */
  def compare(that: Json): Int = (self, that) match {
    case (Json.Null, Json.Null)             => 0
    case (Json.Null, _)                     => -1
    case (_, Json.Null)                     => 1
    case (Json.Boolean(a), Json.Boolean(b)) => a.compare(b)
    case (Json.Boolean(_), _)               => -1
    case (_, Json.Boolean(_))               => 1
    case (Json.Number(a), Json.Number(b))   => BigDecimal(a).compare(BigDecimal(b))
    case (Json.Number(_), _)                => -1
    case (_, Json.Number(_))                => 1
    case (Json.String(a), Json.String(b))   => a.compare(b)
    case (Json.String(_), _)                => -1
    case (_, Json.String(_))                => 1
    case (Json.Array(a), Json.Array(b))     => compareArrays(a, b)
    case (Json.Array(_), _)                 => -1
    case (_, Json.Array(_))                 => 1
    case (Json.Object(a), Json.Object(b))   => compareObjects(a, b)
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

  // Note: We rely on case class structural equality for equals/hashCode
  // Custom implementations would cause infinite recursion with pattern matching

  override def toString: String =
    // TODO: Implement proper JSON encoding
    this match {
      case Json.Null         => "null"
      case Json.Boolean(v)   => v.toString
      case Json.Number(v)    => v
      case Json.String(v)    => s""""$v""""
      case Json.Array(elems) => elems.mkString("[", ",", "]")
      case Json.Object(flds) => flds.map { case (k, v) => s""""$k":$v""" }.mkString("{", ",", "}")
    }

  // ===========================================================================
  // DynamicValue Conversion
  // ===========================================================================

  /**
   * Converts this JSON value to a [[DynamicValue]].
   *
   * The conversion is lossless and follows these rules:
   *   - `Null` → `Primitive(Unit)`
   *   - `Boolean` → `Primitive(Boolean)`
   *   - `Number` → `Primitive(BigDecimal)` (preserves precision)
   *   - `String` → `Primitive(String)`
   *   - `Array` → `Sequence`
   *   - `Object` → `Record`
   *
   * {{{
   * val json = Json.Object("name" -> Json.String("Alice"), "age" -> Json.number(30))
   * val dv: DynamicValue = json.toDynamicValue
   * }}}
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
  // Manipulation & Transformation
  // ===========================================================================

  /**
   * Modifies values at the given path using a function.
   *
   * {{{
   * json.modify(p"user.age", age => Json.number(age.numberValue.get.toInt + 1))
   * }}}
   *
   * @param path
   *   The path to values to modify
   * @param f
   *   The modification function
   * @return
   *   The modified JSON
   */
  def modify(path: DynamicOptic, f: Json => Json): Json = {
    import DynamicOptic.Node

    def go(current: Json, nodes: IndexedSeq[Node]): Json = {
      if (nodes.isEmpty) return f(current)

      val node = nodes.head
      val rest = nodes.tail

      node match {
        case Node.Field(name) =>
          current match {
            case Json.Object(flds) =>
              Json.Object(flds.map { case (k, v) =>
                if (k == name) k -> go(v, rest)
                else k           -> v
              })
            case other => other
          }

        case Node.AtIndex(index) =>
          current match {
            case Json.Array(elems) if index >= 0 && index < elems.size =>
              Json.Array(elems.updated(index, go(elems(index), rest)))
            case other => other
          }

        case Node.Elements =>
          current match {
            case Json.Array(elems) =>
              Json.Array(elems.map(e => go(e, rest)))
            case other => other
          }

        case _ => current // Other node types not supported for modification
      }
    }

    go(self, path.nodes)
  }

  /**
   * Modifies values at the given path using a partial function.
   *
   * Values for which the partial function is not defined are left unchanged.
   *
   * @param path
   *   The path to values to modify
   * @param pf
   *   The partial modification function
   * @return
   *   Either an error if the path is invalid, or the modified JSON
   */
  def modifyOrFail(path: DynamicOptic, pf: PartialFunction[Json, Json]): Either[JsonError, Json] =
    try {
      Right(modify(path, v => if (pf.isDefinedAt(v)) pf(v) else v))
    } catch {
      case e: Exception =>
        Left(JsonError(e.getMessage, path, None, None, None))
    }

  /**
   * Sets the value at the given path.
   *
   * If the path does not exist, attempts to create intermediate structure. For
   * array indices, the array must already exist and have sufficient length.
   *
   * {{{
   * json.set(p"user.name", Json.String("Bob"))
   * }}}
   *
   * @param path
   *   The path to set
   * @param value
   *   The value to set
   * @return
   *   The modified JSON
   */
  def set(path: DynamicOptic, value: Json): Json =
    modify(path, _ => value)

  /**
   * Sets the value at the given path, returning an error if the path is
   * invalid.
   *
   * @param path
   *   The path to set
   * @param value
   *   The value to set
   * @return
   *   Either an error or the modified JSON
   */
  def setOrFail(path: DynamicOptic, value: Json): Either[JsonError, Json] =
    try {
      Right(set(path, value))
    } catch {
      case e: Exception =>
        Left(JsonError(e.getMessage, path, None, None, None))
    }

  /**
   * Deletes values at the given path.
   *
   * For object fields, removes the key-value pair. For array elements, removes
   * the element and shifts subsequent elements.
   *
   * @param path
   *   The path to delete
   * @return
   *   The modified JSON
   */
  def delete(path: DynamicOptic): Json = {
    import DynamicOptic.Node

    def go(current: Json, nodes: IndexedSeq[Node]): Json = {
      if (nodes.isEmpty) return Json.Null // Delete by replacing with null

      val node = nodes.head
      val rest = nodes.tail

      if (rest.isEmpty) {
        // Last node - perform deletion
        node match {
          case Node.Field(name) =>
            current match {
              case Json.Object(flds) =>
                Json.Object(flds.filterNot(_._1 == name))
              case other => other
            }

          case Node.AtIndex(index) =>
            current match {
              case Json.Array(elems) if index >= 0 && index < elems.size =>
                Json.Array(elems.patch(index, Nil, 1))
              case other => other
            }

          case _ => current
        }
      } else {
        // Intermediate node - recurse
        node match {
          case Node.Field(name) =>
            current match {
              case Json.Object(flds) =>
                Json.Object(flds.map { case (k, v) =>
                  if (k == name) k -> go(v, rest)
                  else k           -> v
                })
              case other => other
            }

          case Node.AtIndex(index) =>
            current match {
              case Json.Array(elems) if index >= 0 && index < elems.size =>
                Json.Array(elems.updated(index, go(elems(index), rest)))
              case other => other
            }

          case _ => current
        }
      }
    }

    go(self, path.nodes)
  }

  /**
   * Deletes values at the given path, returning an error if the path is
   * invalid.
   *
   * @param path
   *   The path to delete
   * @return
   *   Either an error or the modified JSON
   */
  def deleteOrFail(path: DynamicOptic): Either[JsonError, Json] =
    try {
      Right(delete(path))
    } catch {
      case e: Exception =>
        Left(JsonError(e.getMessage, path, None, None, None))
    }

  /**
   * Merges this JSON with another using automatic strategy.
   *
   * Objects are merged recursively, arrays are concatenated, primitives prefer
   * `other`.
   *
   * {{{
   * val json1 = Json.Object("a" -> Json.number(1), "b" -> Json.number(2))
   * val json2 = Json.Object("b" -> Json.number(3), "c" -> Json.number(4))
   * val merged = json1.merge(json2) // {"a": 1, "b": 3, "c": 4}
   * }}}
   *
   * @param other
   *   The JSON to merge with
   * @return
   *   The merged JSON
   */
  def merge(other: Json): Json = self match {
    case Json.Object(flds1) =>
      other match {
        case Json.Object(flds2) =>
          val keys   = (flds1.map(_._1) ++ flds2.map(_._1)).distinct
          val merged = keys.map { key =>
            val v1 = flds1.collectFirst { case (k, v) if k == key => v }
            val v2 = flds2.collectFirst { case (k, v) if k == key => v }
            (v1, v2) match {
              case (Some(a), Some(b)) => key -> a.merge(b)
              case (Some(a), None)    => key -> a
              case (None, Some(b))    => key -> b
              case (None, None)       => key -> Json.Null // Should never happen
            }
          }
          Json.Object(merged.toVector)
        case _ => other
      }
    case Json.Array(elems1) =>
      other match {
        case Json.Array(elems2) => Json.Array(elems1 ++ elems2)
        case _                  => other
      }
    case _ => other
  }

  // ===========================================================================
  // Transformation
  // ===========================================================================

  /**
   * Transforms all values in this JSON bottom-up (children before parents).
   *
   * @param f
   *   The transformation function receiving path and value
   * @return
   *   The transformed JSON
   */
  def transformUp(f: (DynamicOptic, Json) => Json): Json = {
    def go(path: DynamicOptic, json: Json): Json = {
      val transformed = json match {
        case Json.Object(flds) =>
          Json.Object(flds.map { case (k, v) =>
            k -> go(path.field(k), v)
          })
        case Json.Array(elems) =>
          Json.Array(elems.zipWithIndex.map { case (v, i) =>
            go(path.at(i), v)
          })
        case other => other
      }
      f(path, transformed)
    }
    go(DynamicOptic.root, self)
  }

  /**
   * Transforms all values in this JSON top-down (parents before children).
   *
   * @param f
   *   The transformation function receiving path and value
   * @return
   *   The transformed JSON
   */
  def transformDown(f: (DynamicOptic, Json) => Json): Json = {
    def go(path: DynamicOptic, json: Json): Json = {
      val transformed = f(path, json)
      transformed match {
        case Json.Object(flds) =>
          Json.Object(flds.map { case (k, v) =>
            k -> go(path.field(k), v)
          })
        case Json.Array(elems) =>
          Json.Array(elems.zipWithIndex.map { case (v, i) =>
            go(path.at(i), v)
          })
        case other => other
      }
    }
    go(DynamicOptic.root, self)
  }

  /**
   * Transforms all object keys in this JSON.
   *
   * @param f
   *   The key transformation function receiving path and key
   * @return
   *   The transformed JSON
   */
  def transformKeys(f: (DynamicOptic, java.lang.String) => java.lang.String): Json = {
    def go(path: DynamicOptic, json: Json): Json = json match {
      case Json.Object(flds) =>
        Json.Object(flds.map { case (k, v) =>
          val newKey = f(path, k)
          newKey -> go(path.field(k), v)
        })
      case Json.Array(elems) =>
        Json.Array(elems.zipWithIndex.map { case (v, i) =>
          go(path.at(i), v)
        })
      case other => other
    }
    go(DynamicOptic.root, self)
  }

  // ===========================================================================
  // Filtering
  // ===========================================================================

  /**
   * Removes entries matching the predicate.
   *
   * For objects, removes matching key-value pairs. For arrays, removes matching
   * elements.
   *
   * @param p
   *   The predicate receiving path and value
   * @return
   *   The filtered JSON
   */
  def filterNot(p: (DynamicOptic, Json) => scala.Boolean): Json = {
    def go(path: DynamicOptic, json: Json): Json = json match {
      case Json.Object(flds) =>
        Json.Object(flds.flatMap { case (k, v) =>
          val fieldPath = path.field(k)
          if (p(fieldPath, v)) None
          else Some(k -> go(fieldPath, v))
        })
      case Json.Array(elems) =>
        Json.Array(elems.zipWithIndex.flatMap { case (v, i) =>
          val elemPath = path.at(i)
          if (p(elemPath, v)) None
          else Some(go(elemPath, v))
        })
      case other => other
    }
    go(DynamicOptic.root, self)
  }

  /**
   * Keeps only entries matching the predicate.
   *
   * @param p
   *   The predicate receiving path and value
   * @return
   *   The filtered JSON
   */
  def filter(p: (DynamicOptic, Json) => scala.Boolean): Json =
    filterNot((path, json) => !p(path, json))

  // ===========================================================================
  // Normalization
  // ===========================================================================

  /**
   * Returns a normalized version of this JSON.
   *
   * Normalization includes:
   *   - Sorting object keys alphabetically
   *   - Normalizing number representations
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
      try {
        val bd = BigDecimal(v)
        Json.Number(bd.toString)
      } catch {
        case _: Exception => self
      }
    case other => other
  }

  // ===========================================================================
  // Folding
  // ===========================================================================

  /**
   * Folds over this JSON top-down (parents before children).
   *
   * @param z
   *   The initial accumulator value
   * @param f
   *   The fold function receiving path, value, and accumulator
   * @tparam B
   *   The accumulator type
   * @return
   *   The final accumulated value
   */
  def foldDown[B](z: B)(f: (DynamicOptic, Json, B) => B): B = {
    def go(path: DynamicOptic, json: Json, acc: B): B = {
      val newAcc = f(path, json, acc)
      json match {
        case Json.Object(flds) =>
          flds.foldLeft(newAcc) { case (a, (k, v)) =>
            go(path.field(k), v, a)
          }
        case Json.Array(elems) =>
          elems.zipWithIndex.foldLeft(newAcc) { case (a, (v, i)) =>
            go(path.at(i), v, a)
          }
        case _ => newAcc
      }
    }
    go(DynamicOptic.root, self, z)
  }

  /**
   * Folds over this JSON bottom-up (children before parents).
   *
   * @param z
   *   The initial accumulator value
   * @param f
   *   The fold function receiving path, value, and accumulator
   * @tparam B
   *   The accumulator type
   * @return
   *   The final accumulated value
   */
  def foldUp[B](z: B)(f: (DynamicOptic, Json, B) => B): B = {
    def go(path: DynamicOptic, json: Json, acc: B): B = {
      val childAcc = json match {
        case Json.Object(flds) =>
          flds.foldLeft(acc) { case (a, (k, v)) =>
            go(path.field(k), v, a)
          }
        case Json.Array(elems) =>
          elems.zipWithIndex.foldLeft(acc) { case (a, (v, i)) =>
            go(path.at(i), v, a)
          }
        case _ => acc
      }
      f(path, json, childAcc)
    }
    go(DynamicOptic.root, self, z)
  }

  // ===========================================================================
  // Querying
  // ===========================================================================

  /**
   * Selects all values matching the predicate.
   *
   * @param p
   *   The predicate receiving path and value
   * @return
   *   A [[JsonSelection]] containing matching values
   */
  def query(p: (DynamicOptic, Json) => scala.Boolean): JsonSelection = {
    val matches = foldDown(Vector.empty[Json]) { (path, json, acc) =>
      if (p(path, json)) acc :+ json else acc
    }
    JsonSelection(matches)
  }

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
      case Json.Object(flds) if flds.isEmpty  => true
      case Json.Array(elems) if elems.isEmpty => true
      case Json.Object(_) | Json.Array(_)     => false
      case _                                  => true
    }

    foldDown(Vector.empty[(DynamicOptic, Json)]) { (path, json, acc) =>
      if (isLeaf(json)) acc :+ (path -> json) else acc
    }
  }

  // ===========================================================================
  // Typed Decoding (Json => A)
  // ===========================================================================

  /**
   * Decodes this JSON to a typed value.
   *
   * Uses implicit [[JsonDecoder]] which prefers explicit codecs over schema
   * derivation.
   *
   * {{{
   * case class Person(name: String, age: Int)
   * object Person {
   *   implicit val schema: Schema[Person] = Schema.derived
   * }
   *
   * val json: Json = Json.Object("name" -> Json.String("Alice"), "age" -> Json.number(30))
   * val person: Either[JsonError, Person] = json.as[Person]
   * }}}
   *
   * @tparam A
   *   The target type
   * @return
   *   Either an error or the decoded value
   */
  def as[A](implicit decoder: JsonDecoder[A]): Either[JsonError, A] = decoder.decode(self)

  /**
   * Internal: decode using an explicit codec.
   *
   * This method is used by [[JsonDecoder]] implementations to decode JSON
   * values using a specific codec.
   */
  private[json] def decodeWith[A](codec: JsonBinaryCodec[A]): Either[JsonError, A] =
    try {
      val jsonString = Json.encode(self)
      codec.decode(jsonString).left.map { schemaError =>
        JsonError(
          message = schemaError.message,
          path = DynamicOptic.root,
          offset = None,
          line = None,
          column = None
        )
      }
    } catch {
      case e: Exception =>
        Left(
          JsonError(
            message = s"Failed to decode JSON: ${e.getMessage}",
            path = DynamicOptic.root,
            offset = None,
            line = None,
            column = None
          )
        )
    }
}

object Json {

  // ===========================================================================
  // Parsing and Encoding
  // ===========================================================================

  /**
   * Parses a JSON string into a Json ADT.
   *
   * @param input
   *   The JSON string to parse
   * @return
   *   Either a JsonError or the parsed Json value
   */
  def parse(input: java.lang.String): Either[JsonError, Json] =
    try {
      Right(
        jsonCodec
          .decode(input)
          .fold(
            err => throw new Exception(err.message),
            identity
          )
      )
    } catch {
      case e: Exception =>
        Left(JsonError(e.getMessage, DynamicOptic.root, None, None, None))
    }

  /**
   * Encodes a Json ADT to a JSON string.
   *
   * @param json
   *   The Json value to encode
   * @param config
   *   The writer configuration (default: WriterConfig)
   * @return
   *   The JSON string
   */
  def encode(json: Json, config: WriterConfig = WriterConfig): java.lang.String =
    new java.lang.String(jsonCodec.encode(json, config), java.nio.charset.StandardCharsets.UTF_8)

  // ===========================================================================
  // ADT Cases
  // ===========================================================================

  /**
   * Represents a JSON object (unordered collection of key-value pairs).
   *
   * @param fields
   *   The object's fields as key-value pairs
   */
  final case class Object(override val fields: Vector[(java.lang.String, Json)]) extends Json {
    override def isObject: scala.Boolean = true

    /**
     * Returns the value associated with the given key, if present.
     */
    def get(key: java.lang.String): Option[Json] =
      fields.collectFirst { case (k, v) if k == key => v }

    /**
     * Returns a new object with the given key-value pair added or updated.
     */
    def +(kv: (java.lang.String, Json)): Object = {
      val (key, value) = kv
      val filtered     = fields.filterNot(_._1 == key)
      Object(filtered :+ (key -> value))
    }

    /**
     * Returns a new object with the given key removed.
     */
    def -(key: java.lang.String): Object =
      Object(fields.filterNot(_._1 == key))
  }

  object Object {

    /**
     * Creates an empty JSON object.
     */
    val empty: Object = Object(Vector.empty)

    /**
     * Creates a JSON object from key-value pairs.
     */
    def apply(fields: (java.lang.String, Json)*): Object =
      Object(fields.toVector)
  }

  /**
   * Represents a JSON array (ordered sequence of values).
   *
   * @param elements
   *   The array's elements
   */
  final case class Array(override val elements: Vector[Json]) extends Json {
    override def isArray: scala.Boolean = true

    /**
     * Returns the element at the given index, if present.
     */
    def get(index: Int): Option[Json] =
      if (index >= 0 && index < elements.size) Some(elements(index))
      else None

    /**
     * Returns a new array with the given element appended.
     */
    def :+(elem: Json): Array =
      Array(elements :+ elem)

    /**
     * Returns a new array with the given element prepended.
     */
    def +:(elem: Json): Array =
      Array(elem +: elements)

    /**
     * Returns the size of the array.
     */
    def size: Int = elements.size
  }

  object Array {

    /**
     * Creates an empty JSON array.
     */
    val empty: Array = Array(Vector.empty)

    /**
     * Creates a JSON array from elements.
     */
    def apply(elements: Json*): Array =
      Array(elements.toVector)
  }

  /**
   * Represents a JSON string.
   *
   * @param value
   *   The string value
   */
  final case class String(value: java.lang.String) extends Json {
    override def isString: scala.Boolean = true

    override def stringValue: Option[java.lang.String] = Some(value)
  }

  /**
   * Represents a JSON number.
   *
   * Numbers are stored as strings to preserve precision and avoid
   * floating-point rounding errors.
   *
   * @param value
   *   The string representation of the number
   */
  final case class Number(value: java.lang.String) extends Json {
    override def isNumber: scala.Boolean = true

    override def numberValue: Option[java.lang.String] = Some(value)

    /**
     * Converts this number to a [[BigDecimal]].
     */
    def toBigDecimal: BigDecimal = BigDecimal(value)

    /**
     * Converts this number to an [[Int]], if possible.
     */
    def toInt: Option[Int] =
      try Some(BigDecimal(value).toIntExact)
      catch { case _: ArithmeticException => None }

    /**
     * Converts this number to a [[Long]], if possible.
     */
    def toLong: Option[Long] =
      try Some(BigDecimal(value).toLongExact)
      catch { case _: ArithmeticException => None }

    /**
     * Converts this number to a [[Double]].
     */
    def toDouble: Double = value.toDouble
  }

  /**
   * Represents a JSON boolean.
   *
   * @param value
   *   The boolean value
   */
  final case class Boolean(value: scala.Boolean) extends Json {
    override def isBoolean: scala.Boolean = true

    override def booleanValue: Option[scala.Boolean] = Some(value)
  }

  /**
   * Represents JSON null.
   */
  case object Null extends Json {
    override def isNull: scala.Boolean = true
  }

  // ===========================================================================
  // Smart Constructors
  // ===========================================================================

  /**
   * Creates a JSON number from an [[Int]].
   */
  def number(value: Int): Number = Number(value.toString)

  /**
   * Creates a JSON number from a [[Long]].
   */
  def number(value: Long): Number = Number(value.toString)

  /**
   * Creates a JSON number from a [[Double]].
   */
  def number(value: Double): Number = Number(value.toString)

  /**
   * Creates a JSON number from a [[BigDecimal]].
   */
  def number(value: BigDecimal): Number = Number(value.toString)

  /**
   * Creates a JSON number from a string representation.
   */
  def number(value: java.lang.String): Number = Number(value)

  // ===========================================================================
  // DynamicValue Conversion
  // ===========================================================================

  /**
   * Converts a [[DynamicValue]] to JSON.
   *
   * The conversion follows these rules:
   *   - `Primitive` → JSON primitive (null, boolean, number, string)
   *   - `Record` → JSON object
   *   - `Variant` → JSON object with `_type` and `_value` fields
   *   - `Sequence` → JSON array
   *   - `Map` → JSON array of objects with `key` and `value` fields
   *
   * {{{
   * val dv = DynamicValue.Record(Vector("name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice"))))
   * val json: Json = Json.fromDynamicValue(dv)
   * }}}
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

  /**
   * Converts a [[PrimitiveValue]] to JSON.
   */
  private def fromPrimitiveValue(pv: PrimitiveValue): Json = pv match {
    case PrimitiveValue.Unit              => Null
    case PrimitiveValue.Boolean(v)        => Boolean(v)
    case PrimitiveValue.Byte(v)           => number(v.toLong)
    case PrimitiveValue.Short(v)          => number(v.toLong)
    case PrimitiveValue.Int(v)            => number(v)
    case PrimitiveValue.Long(v)           => number(v)
    case PrimitiveValue.Float(v)          => number(v.toDouble)
    case PrimitiveValue.Double(v)         => number(v)
    case PrimitiveValue.BigInt(v)         => number(BigDecimal(v))
    case PrimitiveValue.BigDecimal(v)     => number(v)
    case PrimitiveValue.Char(v)           => String(v.toString)
    case PrimitiveValue.String(v)         => String(v)
    case PrimitiveValue.Instant(v)        => String(v.toString)
    case PrimitiveValue.LocalDate(v)      => String(v.toString)
    case PrimitiveValue.LocalTime(v)      => String(v.toString)
    case PrimitiveValue.LocalDateTime(v)  => String(v.toString)
    case PrimitiveValue.OffsetTime(v)     => String(v.toString)
    case PrimitiveValue.OffsetDateTime(v) => String(v.toString)
    case PrimitiveValue.ZonedDateTime(v)  => String(v.toString)
    case PrimitiveValue.Duration(v)       => String(v.toString)
    case PrimitiveValue.Period(v)         => String(v.toString)
    case PrimitiveValue.Year(v)           => String(v.toString)
    case PrimitiveValue.YearMonth(v)      => String(v.toString)
    case PrimitiveValue.MonthDay(v)       => String(v.toString)
    case PrimitiveValue.DayOfWeek(v)      => String(v.toString)
    case PrimitiveValue.Month(v)          => String(v.toString)
    case PrimitiveValue.ZoneId(v)         => String(v.toString)
    case PrimitiveValue.ZoneOffset(v)     => String(v.toString)
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
   *   DynamicOptic.root.field("a").field("b") -> Json.number(1),
   *   DynamicOptic.root.field("a").field("c") -> Json.String("x"),
   *   DynamicOptic.root.field("d").at(0) -> Json.Boolean(true)
   * ))
   * // Right({"a": {"b": 1, "c": "x"}, "d": [true]})
   * }}}
   *
   * @param kvs
   *   The path-value pairs
   * @return
   *   Either an error (for conflicting paths) or the assembled JSON
   */
  def fromKV(kvs: Seq[(DynamicOptic, Json)]): Either[JsonError, Json] = {
    import DynamicOptic.Node

    def insertAt(json: Json, path: IndexedSeq[Node], value: Json): Either[JsonError, Json] =
      if (path.isEmpty) {
        Right(value)
      } else {
        val node = path.head
        val rest = path.tail

        node match {
          case Node.Field(name) =>
            json match {
              case Json.Object(flds) =>
                val existing = flds.collectFirst { case (k, v) if k == name => v }
                existing match {
                  case Some(v) =>
                    insertAt(v, rest, value).map { newV =>
                      Json.Object(flds.map { case (k, oldV) =>
                        if (k == name) k -> newV else k -> oldV
                      })
                    }
                  case None =>
                    if (rest.isEmpty) {
                      Right(Json.Object(flds :+ (name -> value)))
                    } else {
                      // Need to create intermediate structure
                      val intermediate = rest.head match {
                        case _: Node.Field   => Json.Object(Vector.empty)
                        case _: Node.AtIndex => Json.Array(Vector.empty)
                        case _               => Json.Null
                      }
                      insertAt(intermediate, rest, value).map { newV =>
                        Json.Object(flds :+ (name -> newV))
                      }
                    }
                }
              case Json.Null =>
                if (rest.isEmpty) {
                  Right(Json.Object(Vector(name -> value)))
                } else {
                  val intermediate = rest.head match {
                    case _: Node.Field   => Json.Object(Vector.empty)
                    case _: Node.AtIndex => Json.Array(Vector.empty)
                    case _               => Json.Null
                  }
                  insertAt(intermediate, rest, value).map { newV =>
                    Json.Object(Vector(name -> newV))
                  }
                }
              case _ =>
                Left(JsonError(s"Cannot insert field '$name' into non-object", DynamicOptic(path), None, None, None))
            }

          case Node.AtIndex(index) =>
            json match {
              case Json.Array(elems) =>
                if (index < 0 || index > elems.size) {
                  Left(JsonError(s"Array index $index out of bounds", DynamicOptic(path), None, None, None))
                } else if (index == elems.size) {
                  // Append
                  if (rest.isEmpty) {
                    Right(Json.Array(elems :+ value))
                  } else {
                    val intermediate = rest.head match {
                      case _: Node.Field   => Json.Object(Vector.empty)
                      case _: Node.AtIndex => Json.Array(Vector.empty)
                      case _               => Json.Null
                    }
                    insertAt(intermediate, rest, value).map { newV =>
                      Json.Array(elems :+ newV)
                    }
                  }
                } else {
                  // Update existing
                  insertAt(elems(index), rest, value).map { newV =>
                    Json.Array(elems.updated(index, newV))
                  }
                }
              case Json.Null =>
                if (index == 0) {
                  if (rest.isEmpty) {
                    Right(Json.Array(Vector(value)))
                  } else {
                    val intermediate = rest.head match {
                      case _: Node.Field   => Json.Object(Vector.empty)
                      case _: Node.AtIndex => Json.Array(Vector.empty)
                      case _               => Json.Null
                    }
                    insertAt(intermediate, rest, value).map { newV =>
                      Json.Array(Vector(newV))
                    }
                  }
                } else {
                  Left(JsonError(s"Cannot create array with gap at index $index", DynamicOptic(path), None, None, None))
                }
              case _ =>
                Left(JsonError(s"Cannot insert index $index into non-array", DynamicOptic(path), None, None, None))
            }

          case _ =>
            Left(JsonError(s"Unsupported path node: $node", DynamicOptic(path), None, None, None))
        }
      }

    kvs.foldLeft[Either[JsonError, Json]](Right(Json.Null)) { case (acc, (path, value)) =>
      acc.flatMap { json =>
        insertAt(json, path.nodes, value)
      }
    }
  }

  // ===========================================================================
  // Typed Encoding (A => Json)
  // ===========================================================================

  /**
   * Encodes a typed value to JSON.
   *
   * Uses implicit [[JsonEncoder]] which prefers explicit codecs over schema
   * derivation.
   *
   * {{{
   * case class Person(name: String, age: Int)
   * object Person {
   *   implicit val schema: Schema[Person] = Schema.derived
   * }
   *
   * val person = Person("Alice", 30)
   * val json: Json = Json.from(person)
   * }}}
   *
   * @param value
   *   The value to encode
   * @return
   *   The encoded JSON
   */
  def from[A](value: A)(implicit encoder: JsonEncoder[A]): Json = encoder.encode(value)

  /**
   * Internal: encode using an explicit codec.
   *
   * This method is used by [[JsonEncoder]] implementations to encode values
   * using a specific codec.
   */
  private[json] def encodeWith[A](value: A, codec: JsonBinaryCodec[A]): Json =
    try {
      val bytes      = codec.encode(value, WriterConfig)
      val jsonString = new java.lang.String(bytes, java.nio.charset.StandardCharsets.UTF_8)
      parse(jsonString) match {
        case Right(json) => json
        case Left(error) => throw error
      }
    } catch {
      case e: JsonError => throw e
      case e: Exception =>
        throw JsonError(
          message = s"Failed to encode value: ${e.getMessage}",
          path = DynamicOptic.root,
          offset = None,
          line = None,
          column = None
        )
    }

  // ===========================================================================
  // JsonBinaryCodec for Json ADT
  // ===========================================================================

  /**
   * A JsonBinaryCodec that can parse and encode the Json ADT itself. This
   * allows us to parse JSON strings into our Json ADT and encode them back.
   */
  private[json] lazy val jsonCodec: JsonBinaryCodec[Json] = new JsonBinaryCodec[Json] {
    import zio.blocks.schema.json.{JsonReader, JsonWriter}

    override def nullValue: Json = Null

    override def decodeValue(in: JsonReader, default: Json): Json = {
      // Read the next token to determine the JSON type
      val b = in.nextToken()

      if (b == 'n') {
        in.rollbackToken()
        in.readNullOrError(default, "expected JSON value")
        Null
      } else if (b == 't' || b == 'f') {
        in.rollbackToken()
        Boolean(in.readBoolean())
      } else if (b == '"') {
        in.rollbackToken()
        String(in.readString(null))
      } else if ((b >= '0' && b <= '9') || b == '-') {
        in.rollbackToken()
        Number(in.readBigDecimal(null).toString)
      } else if (b == '[') {
        // Array
        if (in.isNextToken(']')) {
          Array(Vector.empty)
        } else {
          in.rollbackToken()
          val builder = Vector.newBuilder[Json]
          while ({
            builder += decodeValue(in, default)
            in.isNextToken(',')
          }) ()
          if (in.isCurrentToken(']')) Array(builder.result())
          else in.arrayEndOrCommaError()
        }
      } else if (b == '{') {
        // Object
        if (in.isNextToken('}')) {
          Object(Vector.empty)
        } else {
          in.rollbackToken()
          val builder = Vector.newBuilder[(java.lang.String, Json)]
          while ({
            val key   = in.readKeyAsString()
            val value = decodeValue(in, default)
            builder += (key -> value)
            in.isNextToken(',')
          }) ()
          if (in.isCurrentToken('}')) Object(builder.result())
          else in.objectEndOrCommaError()
        }
      } else {
        in.decodeError("expected JSON value")
      }
    }

    override def encodeValue(x: Json, out: JsonWriter): Unit = x match {
      case Null         => out.writeNull()
      case Boolean(v)   => out.writeVal(v)
      case String(v)    => out.writeVal(v)
      case Number(v)    => out.writeVal(BigDecimal(v))
      case Array(elems) =>
        out.writeArrayStart()
        var i = 0
        while (i < elems.length) {
          encodeValue(elems(i), out)
          i += 1
        }
        out.writeArrayEnd()
      case Object(flds) =>
        out.writeObjectStart()
        var i = 0
        while (i < flds.length) {
          val (k, v) = flds(i)
          out.writeKey(k)
          encodeValue(v, out)
          i += 1
        }
        out.writeObjectEnd()
    }
  }
}
