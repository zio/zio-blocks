package zio.blocks.schema.json

import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue, SchemaError}
import zio.blocks.schema.json.{JsonError, JsonSelection, MergeStrategy}
import zio.blocks.schema.json.JsonBinaryCodec.{JsonReader, JsonWriter}
import scala.util.control.NoStackTrace

/**
 * Represents a JSON value following RFC 8259.
 *
 * The JSON data model consists of:
 * - '''Objects''': Unordered collections of key-value pairs
 * - '''Arrays''': Ordered sequences of values
 * - '''Strings''': Unicode text
 * - '''Numbers''': Numeric values (stored as strings for precision)
 * - '''Booleans''': `true` or `false`
 * - '''Null''': The null value
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
 * json.get(DynamicOptic.root.field("users").at(0).field("name")) // JsonSelection
 * json("users")(0)("name") // JsonSelection
 * json.fields // for objects
 * json.elements // for arrays
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
  // Navigation (Enhanced)
  // ===========================================================================

  /**
   * Navigates to values at the given path.
   *
   * Walks through DynamicOptic.Node elements to access nested structures.
   *
   * @param path The path to navigate
   * @return A selection with values at the path
   */
  def get(path: DynamicOptic): JsonSelection = {
    if (path.nodes.isEmpty) {
      JsonSelection(self)
    } else {
      navigatePath(self, path.nodes.toList) match {
        case Some(result) => JsonSelection(result)
        case None => JsonSelection.empty
      }
    }
  }

  private def navigatePath(current: Json, nodes: List[DynamicOptic.Node]): Option[Json] = nodes match {
    case Nil => Some(current)
    case head :: tail => head match {
      case DynamicOptic.Node.Field(name) =>
        current match {
          case Json.Object(flds) =>
            flds.collectFirst { case (k, v) if k == name => v }.flatMap(navigatePath(_, tail))
          case _ => None
        }
      case DynamicOptic.Node.AtIndex(index) =>
        current match {
          case Json.Array(elems) if index >= 0 && index < elems.size =>
            navigatePath(elems(index), tail)
          case _ => None
        }
      case _ => None // Other node types not yet supported
    }
  }

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
  // Modification Operations
  // ===========================================================================

  /**
   * Modifies values at the given path using the provided function.
   *
   * If the path does not exist, returns this JSON unchanged.
   *
   * @param path The path to values to modify
   * @param f    The modification function
   * @return The modified JSON
   */
  def modify(path: DynamicOptic, f: Json => Json): Json = {
    if (path.nodes.isEmpty) {
      f(self)
    } else {
      modifyAtPath(self, path.nodes.toList, f).getOrElse(self)
    }
  }

  private def modifyAtPath(current: Json, nodes: List[DynamicOptic.Node], f: Json => Json): Option[Json] = nodes match {
    case Nil => Some(f(current))
    case head :: tail => head match {
      case DynamicOptic.Node.Field(name) =>
        current match {
          case Json.Object(flds) =>
            val modified = flds.map {
              case (k, v) if k == name =>
                modifyAtPath(v, tail, f).map(newV => (k, newV)).getOrElse((k, v))
              case kv => kv
            }
            Some(Json.Object(modified))
          case _ => None
        }
      case DynamicOptic.Node.AtIndex(index) =>
        current match {
          case Json.Array(elems) if index >= 0 && index < elems.size =>
            val modified = elems.zipWithIndex.map {
              case (v, idx) if idx == index =>
                modifyAtPath(v, tail, f).getOrElse(v)
              case (v, _) => v
            }
            Some(Json.Array(modified))
          case _ => None
        }
      case _ => None
    }
  }

  /**
   * Sets the value at the given path.
   *
   * If the path does not exist, attempts to create intermediate structure.
   *
   * @param path  The path to set
   * @param value The value to set
   * @return The modified JSON
   */
  def set(path: DynamicOptic, value: Json): Json = {
    if (path.nodes.isEmpty) {
      value
    } else {
      setAtPath(self, path.nodes.toList, value).getOrElse(self)
    }
  }

  private def setAtPath(current: Json, nodes: List[DynamicOptic.Node], value: Json): Option[Json] = nodes match {
    case Nil => Some(value)
    case head :: tail => head match {
      case DynamicOptic.Node.Field(name) =>
        current match {
          case Json.Object(flds) =>
            if (tail.isEmpty) {
              // Set at this level
              val updated = flds.map {
                case (k, _) if k == name => (k, value)
                case kv => kv
              }
              // If key doesn't exist, add it
              if (flds.exists(_._1 == name)) Some(Json.Object(updated))
              else Some(Json.Object(flds :+ (name, value)))
            } else {
              // Recurse deeper
              val updated = flds.map {
                case (k, v) if k == name =>
                  setAtPath(v, tail, value).map(newV => (k, newV)).getOrElse((k, v))
                case kv => kv
              }
              Some(Json.Object(updated))
            }
          case _ => None
        }
      case DynamicOptic.Node.AtIndex(index) =>
        current match {
          case Json.Array(elems) if index >= 0 && index < elems.size =>
            if (tail.isEmpty) {
              Some(Json.Array(elems.updated(index, value)))
            } else {
              val updated = elems.zipWithIndex.map {
                case (v, idx) if idx == index =>
                  setAtPath(v, tail, value).getOrElse(v)
                case (v, _) => v
              }
              Some(Json.Array(updated))
            }
          case _ => None
        }
      case _ => None
    }
  }

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
    if (path.nodes.isEmpty) {
      Json.Null
    } else {
      deleteAtPath(self, path.nodes.toList).getOrElse(self)
    }
  }

  private def deleteAtPath(current: Json, nodes: List[DynamicOptic.Node]): Option[Json] = nodes match {
    case Nil => Some(Json.Null)
    case head :: Nil => head match {
      case DynamicOptic.Node.Field(name) =>
        current match {
          case Json.Object(flds) =>
            Some(Json.Object(flds.filterNot(_._1 == name)))
          case _ => None
        }
      case DynamicOptic.Node.AtIndex(index) =>
        current match {
          case Json.Array(elems) if index >= 0 && index < elems.size =>
            Some(Json.Array(elems.patch(index, Nil, 1)))
          case _ => None
        }
      case _ => None
    }
    case head :: tail => head match {
      case DynamicOptic.Node.Field(name) =>
        current match {
          case Json.Object(flds) =>
            val updated = flds.map {
              case (k, v) if k == name =>
                deleteAtPath(v, tail).map(newV => (k, newV)).getOrElse((k, v))
              case kv => kv
            }
            Some(Json.Object(updated))
          case _ => None
        }
      case DynamicOptic.Node.AtIndex(index) =>
        current match {
          case Json.Array(elems) if index >= 0 && index < elems.size =>
            val updated = elems.zipWithIndex.map {
              case (v, idx) if idx == index =>
                deleteAtPath(v, tail).getOrElse(v)
              case (v, _) => v
            }
            Some(Json.Array(updated))
          case _ => None
        }
      case _ => None
    }
  }

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
    def recurse(current: Json, currentPath: DynamicOptic): Json = {
      val transformed = current match {
        case Json.Object(flds) =>
          Json.Object(flds.map { case (k, v) =>
            val fieldPath = currentPath.field(k)
            (k, recurse(v, fieldPath))
          })
        case Json.Array(elems) =>
          Json.Array(elems.zipWithIndex.map { case (elem, idx) =>
            val elemPath = currentPath.at(idx)
            recurse(elem, elemPath)
          })
        case other =>
          other
      }
      f(currentPath, transformed)
    }
    recurse(self, DynamicOptic.root)
  }

  /**
   * Transforms all values in this JSON top-down (parents before children).
   *
   * @param f The transformation function receiving path and value
   * @return The transformed JSON
   */
  def transformDown(f: (DynamicOptic, Json) => Json): Json = {
    def recurse(current: Json, currentPath: DynamicOptic): Json = {
      val transformed = f(currentPath, current)
      transformed match {
        case Json.Object(flds) =>
          Json.Object(flds.map { case (k, v) =>
            val fieldPath = currentPath.field(k)
            (k, recurse(v, fieldPath))
          })
        case Json.Array(elems) =>
          Json.Array(elems.zipWithIndex.map { case (elem, idx) =>
            val elemPath = currentPath.at(idx)
            recurse(elem, elemPath)
          })
        case other =>
          other
      }
    }
    recurse(self, DynamicOptic.root)
  }

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
    def recurse(current: Json, currentPath: DynamicOptic): Json = current match {
      case Json.Object(flds) =>
        Json.Object(flds.collect {
          case (k, v) =>
            val fieldPath = currentPath.field(k)
            if (!p(fieldPath, v)) Some((k, recurse(v, fieldPath)))
            else None
        }.flatten)
      case Json.Array(elems) =>
        Json.Array(elems.zipWithIndex.collect {
          case (elem, idx) =>
            val elemPath = currentPath.at(idx)
            if (!p(elemPath, elem)) Some(recurse(elem, elemPath))
            else None
        }.flatten)
      case other =>
        other
    }
    recurse(self, DynamicOptic.root)
  }

  /**
   * Keeps only entries matching the predicate.
   *
   * @param p The predicate receiving path and value
   * @return The filtered JSON
   */
  def filter(p: (DynamicOptic, Json) => scala.Boolean): Json = {
    def notP(path: DynamicOptic, json: Json): scala.Boolean = !p(path, json)
    filterNot(notP _)
  }

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
   * @param other    The JSON to merge with
   * @param strategy The merge strategy (default: [[MergeStrategy.Auto]])
   * @return The merged JSON
   */
  def merge(other: Json, strategy: MergeStrategy = MergeStrategy.Auto): Json = {
    strategy match {
      case MergeStrategy.Replace => other
      case MergeStrategy.Auto =>
        (self, other) match {
          case (Json.Object(f1), Json.Object(f2)) => mergeObjects(f1, f2, deep = true)
          case (Json.Array(e1), Json.Array(e2))   => Json.Array(e1 ++ e2)
          case _                                  => other
        }
      case MergeStrategy.Deep =>
        (self, other) match {
          case (Json.Object(f1), Json.Object(f2)) => mergeObjects(f1, f2, deep = true)
          case (Json.Array(e1), Json.Array(e2))   => Json.Array(e1 ++ e2)
          case _                                  => other
        }
      case MergeStrategy.Shallow =>
        (self, other) match {
          case (Json.Object(f1), Json.Object(f2)) => mergeObjects(f1, f2, deep = false)
          case _                                  => other
        }
      case MergeStrategy.Concat =>
        (self, other) match {
          case (Json.Array(e1), Json.Array(e2)) => Json.Array(e1 ++ e2)
          case _                                => other
        }
      case MergeStrategy.Custom(f) =>
        f(DynamicOptic.root, self, other)
    }
  }

  private def mergeObjects(
    f1: Vector[(String, Json)],
    f2: Vector[(String, Json)],
    deep: Boolean
  ): Json.Object = {
    val f1Map = f1.toMap
    val f2Map = f2.toMap
    val allKeys = (f1Map.keySet ++ f2Map.keySet).toVector.sorted
    val merged = allKeys.map { key =>
      (f1Map.get(key), f2Map.get(key)) match {
        case (Some(v1), Some(v2)) if deep => (key, v1.merge(v2, MergeStrategy.Deep))
        case (Some(v1), Some(v2))         => (key, v2)
        case (Some(v1), None)             => (key, v1)
        case (None, Some(v2))             => (key, v2)
        case (None, None)                 => (key, Json.Null) // Should not happen
      }
    }
    Json.Object(merged)
  }

  // ===========================================================================
  // Normalization
  // ===========================================================================

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
      DynamicValue.Sequence(elems.map(_.toDynamicValue).toVector)
    case Json.Object(flds) =>
      DynamicValue.Record(flds.map { case (k, v) => (k, v.toDynamicValue) }.toVector)
  }

  // ===========================================================================
  // Comparison
  // ===========================================================================

  /**
   * Compares this JSON to another for ordering.
   *
   * Ordering is defined as:
   * 1. Null < Boolean < Number < String < Array < Object
   * 2. Within types, natural ordering applies
   */
  def compare(that: Json): Int = (self, that) match {
    case (Json.Null, Json.Null) => 0
    case (Json.Null, _)         => -1
    case (_, Json.Null)         => 1

    case (Json.Boolean(a), Json.Boolean(b)) => a.compare(b)
    case (Json.Boolean(_), _)               => -1
    case (_, Json.Boolean(_))               => 1

    case (Json.Number(a), Json.Number(b)) => BigDecimal(a).compare(BigDecimal(b))
    case (Json.Number(_), _)              => -1
    case (_, Json.Number(_))              => 1

    case (Json.String(a), Json.String(b)) => a.compare(b)
    case (Json.String(_), _)              => -1
    case (_, Json.String(_))              => 1

    case (Json.Array(a), Json.Array(b)) => compareArrays(a, b)
    case (Json.Array(_), _)             => -1
    case (_, Json.Array(_))             => 1

    case (Json.Object(a), Json.Object(b)) => compareObjects(a, b)
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
  // Standard Methods
  // ===========================================================================

  override def hashCode(): Int = self match {
    case Json.Null          => 0
    case Json.Boolean(v)    => v.hashCode()
    case Json.Number(v)     => BigDecimal(v).hashCode()
    case Json.String(v)     => v.hashCode()
    case Json.Array(elems)  => elems.hashCode()
    case Json.Object(flds)  => flds.sortBy(_._1).hashCode()
  }

  override def equals(that: Any): Boolean = that match {
    case other: Json => compare(other) == 0
    case _           => false
  }

  override def toString: String = self match {
    case Json.Null         => "null"
    case Json.Boolean(v)   => v.toString
    case Json.Number(v)    => v
    case Json.String(v)    => s""""$v""""
    case Json.Array(elems) => elems.mkString("[", ",", "]")
    case Json.Object(flds) => flds.map { case (k, v) => s""""$k":$v""" }.mkString("{", ",", "}")
  }
}

object Json {

  // ===========================================================================
  // ADT Cases
  // ===========================================================================

  /**
   * A JSON object: an unordered collection of key-value pairs.
   *
   * Note: Json.Object preserves insertion order and does not enforce key uniqueness.
   *
   * @param fields The key-value pairs.
   */
  final case class Object(fields: Vector[(String, Json)]) extends Json {
    override def isObject: scala.Boolean            = true
    override def fields: Seq[(String, Json)] = fields
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
    override def isArray: scala.Boolean   = true
    override def elements: Seq[Json] = elements
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
    override def isString: scala.Boolean                  = true
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
    override def isNumber: scala.Boolean                  = true
    override def numberValue: Option[java.lang.String] = Some(value)

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
    override def isBoolean: scala.Boolean                  = true
    override def booleanValue: Option[scala.Boolean] = Some(value)
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
  // DynamicValue Interop
  // ===========================================================================

  /**
   * Converts a [[DynamicValue]] to JSON.
   *
   * This conversion is lossy for `DynamicValue` types that have no JSON equivalent:
   * - `PrimitiveValue` types like `java.time.*` are converted to strings
   * - `DynamicValue.Variant` uses a discriminator field
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
  // Ordering
  // ===========================================================================

  /**
   * Ordering for JSON values.
   *
   * Order: Null < Boolean < Number < String < Array < Object
   */
  implicit val ordering: Ordering[Json] = (x: Json, y: Json) => x.compare(y)

  // ===========================================================================
  // Parsing / Decoding
  // ===========================================================================

  /**
   * Parses a JSON string into a [[Json]] value.
   *
   * @param input The JSON string to parse
   * @return Either a parse error or the parsed JSON
   */
  def parse(input: java.lang.String): Either[JsonError, Json] = {
    codec.decode(input).left.map(JsonError.fromSchemaError)
  }

  /**
   * Parses JSON from a byte array.
   *
   * @param input The byte array containing UTF-8 encoded JSON
   * @return Either a parse error or the parsed JSON
   */
  def parse(input: Array[Byte]): Either[JsonError, Json] = {
    codec.decode(input).left.map(JsonError.fromSchemaError)
  }

  // ===========================================================================
  // Type Classes
  // ===========================================================================

  /**
   * Type class for encoding values of type `A` as JSON.
   *
   * Instances are resolved with the following priority:
   * 1. Explicit instances in scope
   * 2. Instances derived from [[JsonBinaryCodec]]
   * 3. Instances derived from [[zio.blocks.schema.Schema]] (via implicit)
   */
  trait JsonEncoder[A] {
    def encode(value: A): Json
  }

  object JsonEncoder {
    /**
     * Summons an encoder instance.
     */
    def apply[A](implicit encoder: JsonEncoder[A]): JsonEncoder[A] = encoder

    /**
     * Creates an encoder from the given function.
     */
    def instance[A](f: A => Json): JsonEncoder[A] = new JsonEncoder[A] {
      def encode(value: A): Json = f(value)
    }

    /**
     * Derives an encoder from a JsonBinaryCodec (highest priority).
     */
    implicit def fromCodec[A](implicit codec: JsonBinaryCodec[A]): JsonEncoder[A] =
      instance(codec.encodeToJson)

    // Primitives
    implicit val stringEncoder: JsonEncoder[java.lang.String] = instance(String(_))
    implicit val intEncoder: JsonEncoder[Int] = instance(number(_))
    implicit val longEncoder: JsonEncoder[Long] = instance(number(_))
    implicit val doubleEncoder: JsonEncoder[Double] = instance(number(_))
    implicit val booleanEncoder: JsonEncoder[scala.Boolean] = instance(Boolean(_))
    implicit val bigDecimalEncoder: JsonEncoder[BigDecimal] = instance(number(_))
  }

  /**
   * Type class for decoding values of type `A` from JSON.
   *
   * Instances are resolved with the following priority:
   * 1. Explicit instances in scope
   * 2. Instances derived from [[JsonBinaryCodec]]
   * 3. Instances derived from [[zio.blocks.schema.Schema]] (via implicit)
   */
  trait JsonDecoder[A] {
    def decode(json: Json): Either[JsonError, A]
  }

  object JsonDecoder {
    /**
     * Summons a decoder instance.
     */
    def apply[A](implicit decoder: JsonDecoder[A]): JsonDecoder[A] = decoder

    /**
     * Creates a decoder from the given function.
     */
    def instance[A](f: Json => Either[JsonError, A]): JsonDecoder[A] = new JsonDecoder[A] {
      def decode(json: Json): Either[JsonError, A] = f(json)
    }

    /**
     * Derives a decoder from a JsonBinaryCodec (highest priority).
     */
    implicit def fromCodec[A](implicit codec: JsonBinaryCodec[A]): JsonDecoder[A] =
      instance(_.decodeAs[A])

    // Primitives
    implicit val stringDecoder: JsonDecoder[java.lang.String] = instance {
      case String(v) => Right(v)
      case other => Left(JsonError(s"Expected string, got ${other.getClass.getSimpleName}"))
    }

    implicit val intDecoder: JsonDecoder[Int] = instance {
      case Number(v) => 
        try Right(BigDecimal(v).toInt)
        catch { case _: NumberFormatException => Left(JsonError(s"Invalid int: $v")) }
      case other => Left(JsonError(s"Expected number, got ${other.getClass.getSimpleName}"))
    }

    implicit val longDecoder: JsonDecoder[Long] = instance {
      case Number(v) =>
        try Right(BigDecimal(v).toLong)
        catch { case _: NumberFormatException => Left(JsonError(s"Invalid long: $v")) }
      case other => Left(JsonError(s"Expected number, got ${other.getClass.getSimpleName}"))
    }

    implicit val doubleDecoder: JsonDecoder[Double] = instance {
      case Number(v) =>
        try Right(v.toDouble)
        catch { case _: NumberFormatException => Left(JsonError(s"Invalid double: $v")) }
      case other => Left(JsonError(s"Expected number, got ${other.getClass.getSimpleName}"))
    }

    implicit val booleanDecoder: JsonDecoder[scala.Boolean] = instance {
      case Boolean(v) => Right(v)
      case other => Left(JsonError(s"Expected boolean, got ${other.getClass.getSimpleName}"))
    }

    implicit val bigDecimalDecoder: JsonDecoder[BigDecimal] = instance {
      case Number(v) =>
        try Right(BigDecimal(v))
        catch { case _: NumberFormatException => Left(JsonError(s"Invalid decimal: $v")) }
      case other => Left(JsonError(s"Expected number, got ${other.getClass.getSimpleName}"))
    }
  }

  // ===========================================================================
  // JsonBinaryCodec Integration
  // ===========================================================================

  /**
   * Codec for encoding/decoding [[Json]] values.
   *
   * This integrates with the existing JsonBinaryCodec infrastructure.
   */
  implicit lazy val codec: JsonBinaryCodec[Json] = new JsonBinaryCodec[Json]() {
    import scala.collection.immutable.VectorBuilder

    def decodeValue(in: JsonReader, default: Json): Json = {
      val b = in.nextToken()
      if (b == '"') {
        in.rollbackToken()
        String(in.readString(null))
      } else if (b == 'f' || b == 't') {
        in.rollbackToken()
        Boolean(in.readBoolean())
      } else if (b >= '0' && b <= '9' || b == '-') {
        in.rollbackToken()
        val n = in.readBigDecimal(null)
        Number(n.toString)
      } else if (b == '[') {
        if (in.isNextToken(']')) Array.empty
        else {
          in.rollbackToken()
          val builder = new VectorBuilder[Json]
          while ({
            builder.addOne(decodeValue(in, default))
            in.isNextToken(',')
          }) ()
          if (in.isCurrentToken(']')) Array(builder.result())
          else in.arrayEndOrCommaError()
        }
      } else if (b == '{') {
        if (in.isNextToken('}')) Object.empty
        else {
          in.rollbackToken()
          val builder = new VectorBuilder[(java.lang.String, Json)]
          while ({
            builder.addOne((in.readKeyAsString(), decodeValue(in, default)))
            in.isNextToken(',')
          }) ()
          if (in.isCurrentToken('}')) Object(builder.result())
          else in.objectEndOrCommaError()
        }
      } else {
        in.rollbackToken()
        in.readNullOrError(Null, "expected JSON value")
      }
    }

    def encodeValue(x: Json, out: JsonWriter): Unit = x match {
      case Null =>
        out.writeNull()
      case Boolean(v) =>
        out.writeVal(v)
      case Number(v) =>
        // Write as raw number (string representation)
        out.writeVal(BigDecimal(v))
      case String(v) =>
        out.writeVal(v)
      case Array(elems) =>
        out.writeArrayStart()
        val it = elems.iterator
        while (it.hasNext) {
          encodeValue(it.next(), out)
        }
        out.writeArrayEnd()
      case Object(flds) =>
        out.writeObjectStart()
        val it = flds.iterator
        while (it.hasNext) {
          val (k, v) = it.next()
          out.writeKey(k)
          encodeValue(v, out)
        }
        out.writeObjectEnd()
    }
  }

  // ===========================================================================
  // Extension Methods (added to Json trait via implicit class)
  // ===========================================================================

  implicit class JsonOps(private val json: Json) extends AnyVal {
    /**
     * Encodes this JSON as a string (pretty-printed).
     */
    def encode: java.lang.String = codec.encodeToString(json)

    /**
     * Encodes this JSON as a byte array.
     */
    def encodeToBytes: Array[Byte] = codec.encode(json)

    /**
     * Decodes this JSON as a value of type `A`.
     *
     * @tparam A The target type
     * @return Either a decode error or the decoded value
     */
    def as[A](implicit decoder: JsonDecoder[A]): Either[JsonError, A] =
      decoder.decode(json)

    /**
     * Converts to JSON using the given encoder.
     */
    def toJson: Json = json
  }

  /**
   * Encodes a value of type `A` as JSON.
   *
   * @param value The value to encode
   * @tparam A The source type
   * @return The JSON representation
   */
  def from[A](value: A)(implicit encoder: JsonEncoder[A]): Json =
    encoder.encode(value)

  // Internal helper for JsonBinaryCodec integration
  private implicit class JsonBinaryCodecOps[A](codec: JsonBinaryCodec[A]) {
    def encodeToJson(value: A): Json = {
      val bytes = codec.encode(value)
      // Parse the encoded bytes back as Json
      Json.parse(bytes).fold(
        error => throw new RuntimeException(s"Failed to encode via codec: $error"),
        identity
      )
    }

    def decodeAs(json: Json): Either[JsonError, A] = {
      val bytes = json.encodeToBytes
      codec.decode(bytes).left.map(JsonError.fromSchemaError)
    }
  }
}
