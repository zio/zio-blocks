package zio.blocks.schema.json

import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue}

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * A JSON value represented as a Scala data type.
 *
 * Json provides a type-safe representation of JSON data with support for:
 *   - Type checking (isObject, isArray, etc.)
 *   - Type casting (asObject, asArray, etc.)
 *   - Navigation (get, apply)
 *   - Modification (set, delete, insert, modify)
 *   - Serialization (print, encode)
 */
sealed trait Json extends Product with Serializable { self =>

  // ============ Type Tests ============

  /** Returns true if this is a JSON object. */
  def isObject: scala.Boolean = false

  /** Returns true if this is a JSON array. */
  def isArray: scala.Boolean = false

  /** Returns true if this is a JSON string. */
  def isString: scala.Boolean = false

  /** Returns true if this is a JSON number. */
  def isNumber: scala.Boolean = false

  /** Returns true if this is a JSON boolean. */
  def isBoolean: scala.Boolean = false

  /** Returns true if this is JSON null. */
  def isNull: scala.Boolean = false

  // ============ Type Accessors (returning JsonSelection) ============

  /**
   * Returns a JsonSelection containing this value if it's an object, or an
   * error otherwise.
   */
  def asObject: JsonSelection = JsonSelection.error(JsonError.typeMismatch("object", typeLabel))

  /**
   * Returns a JsonSelection containing this value if it's an array, or an error
   * otherwise.
   */
  def asArray: JsonSelection = JsonSelection.error(JsonError.typeMismatch("array", typeLabel))

  /**
   * Returns a JsonSelection containing this value if it's a string, or an error
   * otherwise.
   */
  def asString: JsonSelection = JsonSelection.error(JsonError.typeMismatch("string", typeLabel))

  /**
   * Returns a JsonSelection containing this value if it's a number, or an error
   * otherwise.
   */
  def asNumber: JsonSelection = JsonSelection.error(JsonError.typeMismatch("number", typeLabel))

  /**
   * Returns a JsonSelection containing this value if it's a boolean, or an
   * error otherwise.
   */
  def asBoolean: JsonSelection = JsonSelection.error(JsonError.typeMismatch("boolean", typeLabel))

  /**
   * Returns a JsonSelection containing this value if it's null, or an error
   * otherwise.
   */
  def asNull: JsonSelection = JsonSelection.error(JsonError.typeMismatch("null", typeLabel))

  // ============ Direct Accessors ============

  /** Returns the fields if this is an object, or None otherwise. */
  def fields: Option[Vector[(Predef.String, Json)]] = None

  /** Returns the elements if this is an array, or None otherwise. */
  def elements: Option[Vector[Json]] = None

  /** Returns the string value if this is a string, or None otherwise. */
  def stringValue: Option[String] = None

  /**
   * Returns the number value as a string if this is a number, or None
   * otherwise.
   */
  def numberValue: Option[String] = None

  /** Returns the boolean value if this is a boolean, or None otherwise. */
  def booleanValue: Option[Boolean] = None

  // ============ Navigation ============

  /** Navigates to a value at the given path. */
  def get(path: DynamicOptic): JsonSelection = JsonSelection.single(this).get(path)

  /** Navigates to a field in a JSON object. */
  def apply(@annotation.unused key: Predef.String): JsonSelection =
    JsonSelection.error(JsonError.typeMismatch("object", typeLabel))

  /** Navigates to an element in a JSON array. */
  def apply(@annotation.unused index: Int): JsonSelection =
    JsonSelection.error(JsonError.typeMismatch("array", typeLabel))

  // ============ Modification ============

  /** Modifies values at the given path using the provided function. */
  def modify(path: DynamicOptic)(f: Json => Json): Json = modifyOrFail(path)(j => Right(f(j))).getOrElse(this)

  /** Modifies values at the given path using a function that may fail. */
  def modifyOrFail(path: DynamicOptic)(f: Json => Either[JsonError, Json]): Either[JsonError, Json] =
    if (path.nodes.isEmpty) f(this)
    else modifyAtPath(path.nodes, 0, f)

  /** Sets the value at the given path. */
  def set(path: DynamicOptic, value: Json): Json = modify(path)(_ => value)

  /**
   * Sets the value at the given path, returning an error if the path doesn't
   * exist.
   */
  def setOrFail(path: DynamicOptic, value: Json): Either[JsonError, Json] = modifyOrFail(path)(_ => Right(value))

  /** Deletes the value at the given path. */
  def delete(path: DynamicOptic): Json = deleteOrFail(path).getOrElse(this)

  /**
   * Deletes the value at the given path, returning an error if the path doesn't
   * exist.
   */
  def deleteOrFail(path: DynamicOptic): Either[JsonError, Json] =
    if (path.nodes.isEmpty) Left(JsonError("Cannot delete root"))
    else deleteAtPath(path.nodes, 0)

  /** Inserts a value at the given path. */
  def insert(path: DynamicOptic, value: Json): Json = insertOrFail(path, value).getOrElse(this)

  /**
   * Inserts a value at the given path, returning an error if insertion fails.
   */
  def insertOrFail(path: DynamicOptic, value: Json): Either[JsonError, Json] =
    if (path.nodes.isEmpty) Right(value)
    else insertAtPath(path.nodes, 0, value)

  // ============ Merging ============

  /** Merges this JSON value with another using the default merge strategy. */
  def merge(that: Json): Json = merge(that, MergeStrategy.Auto)

  /** Merges this JSON value with another using the specified merge strategy. */
  def merge(that: Json, strategy: MergeStrategy): Json = strategy.merge(DynamicOptic.root, this, that)

  // ============ Transformations ============

  /** Transforms all values in a bottom-up fashion. */
  def transformUp(f: Json => Json): Json = f(transformChildren(_.transformUp(f)))

  /** Transforms all values in a top-down fashion. */
  def transformDown(f: Json => Json): Json = f(this).transformChildren(_.transformDown(f))

  /** Transforms all object keys. */
  def transformKeys(f: String => String): Json = transformChildren(_.transformKeys(f)) match {
    case Json.Object(fields) => Json.Object(fields.map { case (k, v) => (f(k), v) })
    case other               => other
  }

  /** Filters object fields or array elements. */
  def filter(p: Json => Boolean): Json = this match {
    case Json.Object(fields)  => Json.Object(fields.filter { case (_, v) => p(v) })
    case Json.Array(elements) => Json.Array(elements.filter(p))
    case _                    => this
  }

  /** Removes null values from objects (not recursive). */
  def dropNulls: Json = this match {
    case Json.Object(fields) => Json.Object(fields.filterNot(_._2.isNull))
    case _                   => this
  }

  /** Removes empty objects and arrays. */
  def dropEmpty: Json = this match {
    case Json.Object(fields)  => Json.Object(fields.filterNot { case (_, v) => v.isEmpty })
    case Json.Array(elements) => Json.Array(elements.filterNot(_.isEmpty))
    case _                    => this
  }

  /** Sorts object keys alphabetically. */
  def sortKeys: Json = transformChildren(_.sortKeys) match {
    case Json.Object(fields) => Json.Object(fields.sortBy(_._1))
    case other               => other
  }

  /**
   * Returns a normalized version of this JSON.
   *
   * Normalization includes sorting object keys alphabetically (recursive).
   * Useful for comparison and hashing.
   */
  def normalize: Json = sortKeys

  /**
   * Flattens this JSON to a sequence of path-value pairs.
   *
   * Only leaf values (primitives, empty arrays, empty objects) are included.
   */
  def toKV: Seq[(DynamicOptic, Json)] = toKVInternal(DynamicOptic.root)

  private def toKVInternal(path: DynamicOptic): Seq[(DynamicOptic, Json)] = this match {
    case Json.Object(fields) if fields.nonEmpty =>
      fields.flatMap { case (k, v) => v.toKVInternal(path.field(k)) }
    case Json.Array(elems) if elems.nonEmpty =>
      elems.zipWithIndex.flatMap { case (v, i) => v.toKVInternal(path.at(i)) }
    case _ =>
      Seq((path, this))
  }

  /** Folds over all values in a bottom-up fashion. */
  def foldUp[A](z: A)(f: (A, Json) => A): A = {
    val childResult = this match {
      case Json.Object(fields)  => fields.foldLeft(z) { case (acc, (_, v)) => v.foldUp(acc)(f) }
      case Json.Array(elements) => elements.foldLeft(z)((acc, v) => v.foldUp(acc)(f))
      case _                    => z
    }
    f(childResult, this)
  }

  /** Folds over all values in a top-down fashion. */
  def foldDown[A](z: A)(f: (A, Json) => A): A = {
    val thisResult = f(z, this)
    this match {
      case Json.Object(fields)  => fields.foldLeft(thisResult) { case (acc, (_, v)) => v.foldDown(acc)(f) }
      case Json.Array(elements) => elements.foldLeft(thisResult)((acc, v) => v.foldDown(acc)(f))
      case _                    => thisResult
    }
  }

  // ============ Serialization ============

  /** Prints this JSON value as a compact string. */
  def print: String = {
    val sb = new StringBuilder
    printTo(sb)
    sb.toString
  }

  /**
   * Encodes this JSON to a string using the specified configuration.
   *
   * @param config
   *   Writer configuration (indentation, unicode escaping, etc.)
   */
  def print(config: WriterConfig): String =
    if (config.indentionStep > 0) printPretty(config.indentionStep)
    else print

  /**
   * Encodes this JSON to a string using the specified configuration. Alias for
   * [[print(WriterConfig)]].
   */
  def encode(config: WriterConfig): String = print(config)

  /** Alias for [[print]]. */
  def encode: String = print

  /** Prints this JSON value as a pretty-printed string. */
  def printPretty: String = printPretty(2)

  /**
   * Prints this JSON value as a pretty-printed string with the given
   * indentation.
   */
  def printPretty(indent: Int): String = {
    val sb = new StringBuilder
    printPrettyTo(sb, indent, 0)
    sb.toString
  }

  /** Encodes this JSON value to UTF-8 bytes. */
  def encodeToBytes: Array[Byte] = print.getBytes(StandardCharsets.UTF_8)

  /**
   * Encodes this JSON value to UTF-8 bytes using the specified configuration.
   */
  def encodeToBytes(config: WriterConfig): Array[Byte] =
    print(config).getBytes(StandardCharsets.UTF_8)

  /** Encodes this JSON value to a ByteBuffer. */
  def encodeTo(buffer: ByteBuffer): Unit = buffer.put(encodeToBytes)

  /**
   * Encodes this JSON value to a ByteBuffer using the specified configuration.
   */
  def encodeTo(buffer: ByteBuffer, config: WriterConfig): Unit =
    buffer.put(encodeToBytes(config))

  // ============ Equality and Hashing ============

  /** Compares this JSON value with another for structural equality. */
  def structuralEquals(that: Json): Boolean = (this, that) match {
    case (Json.Object(f1), Json.Object(f2))   => f1.toMap == f2.toMap
    case (Json.Array(e1), Json.Array(e2))     => e1 == e2
    case (Json.String(s1), Json.String(s2))   => s1 == s2
    case (Json.Number(n1), Json.Number(n2))   => n1 == n2
    case (Json.Boolean(b1), Json.Boolean(b2)) => b1 == b2
    case (Json.Null, Json.Null)               => true
    case _                                    => false
  }

  // ============ DynamicValue Interop ============

  /**
   * Converts this JSON to a [[DynamicValue]].
   *
   * This conversion is lossless; all JSON values can be represented as
   * DynamicValue.
   */
  def toDynamicValue: DynamicValue = this match {
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

  // ============ Typed Decoding ============

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
  def as[A](implicit decoder: JsonDecoder[A]): Either[JsonError, A] = decoder.decode(this)

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

  // ============ Comparison ============

  /**
   * Compares this JSON to another for ordering.
   *
   * Ordering is defined as: Null < Boolean < Number < String < Array < Object
   */
  def compare(that: Json): Int = (this, that) match {
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
    case (Json.Array(a), Json.Array(b))     => Json.compareArrays(a, b)
    case (Json.Array(_), _)                 => -1
    case (_, Json.Array(_))                 => 1
    case (Json.Object(a), Json.Object(b))   => Json.compareObjects(a, b)
  }

  // ============ Internal Helpers ============

  /** Returns the type label for error messages. */
  protected def typeLabel: Predef.String

  /**
   * Returns true if this value is empty (null, empty object, or empty array).
   */
  protected def isEmpty: Boolean = this match {
    case Json.Null            => true
    case Json.Object(fields)  => fields.isEmpty
    case Json.Array(elements) => elements.isEmpty
    case _                    => false
  }

  /** Transforms immediate children. */
  protected def transformChildren(f: Json => Json): Json = this match {
    case Json.Object(fields)  => Json.Object(fields.map { case (k, v) => (k, f(v)) })
    case Json.Array(elements) => Json.Array(elements.map(f))
    case _                    => this
  }

  /** Internal path modification. */
  protected def modifyAtPath(
    nodes: IndexedSeq[DynamicOptic.Node],
    idx: Int,
    f: Json => Either[JsonError, Json]
  ): Either[JsonError, Json]

  /** Internal path deletion. */
  protected def deleteAtPath(nodes: IndexedSeq[DynamicOptic.Node], idx: Int): Either[JsonError, Json]

  /** Internal path insertion. */
  protected def insertAtPath(nodes: IndexedSeq[DynamicOptic.Node], idx: Int, value: Json): Either[JsonError, Json]

  /** Prints to a StringBuilder. */
  protected def printTo(sb: StringBuilder): Unit

  /** Pretty prints to a StringBuilder. */
  protected def printPrettyTo(sb: StringBuilder, indent: Int, depth: Int): Unit
}

object Json {

  // Type aliases to avoid confusion with Json.String and Json.Boolean
  private type Str  = Predef.String
  private type Bool = scala.Boolean

  // ============ JSON Value Types ============

  /** A JSON object with ordered fields. */
  final case class Object(value: Vector[(Str, Json)]) extends Json {
    override def isObject: Bool                      = true
    override def asObject: JsonSelection             = JsonSelection.single(this)
    override def fields: Option[Vector[(Str, Json)]] = Some(value)
    override protected def typeLabel: Str            = "object"

    override def apply(key: Str): JsonSelection =
      value.find(_._1 == key) match {
        case Some((_, v)) => JsonSelection.single(v)
        case None         => JsonSelection.error(JsonError.keyNotFound(key))
      }

    override protected def modifyAtPath(
      nodes: IndexedSeq[DynamicOptic.Node],
      idx: Int,
      f: Json => Either[JsonError, Json]
    ): Either[JsonError, Json] =
      nodes(idx) match {
        case DynamicOptic.Node.Field(name) =>
          val fieldIdx = value.indexWhere(_._1 == name)
          if (fieldIdx < 0) Left(JsonError.keyNotFound(name))
          else {
            val (k, v) = value(fieldIdx)
            val result = if (idx == nodes.length - 1) f(v) else v.modifyAtPath(nodes, idx + 1, f)
            result.map(newV => Object(value.updated(fieldIdx, (k, newV))))
          }
        case _ => Left(JsonError.typeMismatch("field access", nodes(idx).toString))
      }

    override protected def deleteAtPath(
      nodes: IndexedSeq[DynamicOptic.Node],
      idx: Int
    ): Either[JsonError, Json] =
      nodes(idx) match {
        case DynamicOptic.Node.Field(name) =>
          val fieldIdx = value.indexWhere(_._1 == name)
          if (fieldIdx < 0) Left(JsonError.keyNotFound(name))
          else if (idx == nodes.length - 1) {
            Right(Object(value.patch(fieldIdx, Nil, 1)))
          } else {
            value(fieldIdx)._2.deleteAtPath(nodes, idx + 1).map { newV =>
              Object(value.updated(fieldIdx, (name, newV)))
            }
          }
        case _ => Left(JsonError.typeMismatch("field access", nodes(idx).toString))
      }

    override protected def insertAtPath(
      nodes: IndexedSeq[DynamicOptic.Node],
      idx: Int,
      newValue: Json
    ): Either[JsonError, Json] =
      nodes(idx) match {
        case DynamicOptic.Node.Field(name) =>
          val fieldIdx = value.indexWhere(_._1 == name)
          if (idx == nodes.length - 1) {
            if (fieldIdx < 0) Right(Object(value :+ (name, newValue)))
            else Right(Object(value.updated(fieldIdx, (name, newValue))))
          } else {
            if (fieldIdx < 0) Left(JsonError.keyNotFound(name))
            else
              value(fieldIdx)._2.insertAtPath(nodes, idx + 1, newValue).map { newV =>
                Object(value.updated(fieldIdx, (name, newV)))
              }
          }
        case _ => Left(JsonError.typeMismatch("field access", nodes(idx).toString))
      }

    override protected def printTo(sb: StringBuilder): Unit = {
      sb.append('{')
      var first = true
      value.foreach { case (k, v) =>
        if (!first) sb.append(',')
        first = false
        printString(sb, k)
        sb.append(':')
        v.printTo(sb)
      }
      sb.append('}')
    }

    override protected def printPrettyTo(sb: StringBuilder, indent: Int, depth: Int): Unit =
      if (value.isEmpty) {
        sb.append("{}")
      } else {
        sb.append("{\n")
        var first = true
        value.foreach { case (k, v) =>
          if (!first) sb.append(",\n")
          first = false
          sb.append(" " * (indent * (depth + 1)))
          printString(sb, k)
          sb.append(": ")
          v.printPrettyTo(sb, indent, depth + 1)
        }
        sb.append('\n')
        sb.append(" " * (indent * depth))
        sb.append('}')
      }
  }

  object Object {
    val empty: Object = Object(Vector.empty)

    def apply(fields: (Str, Json)*): Object = Object(fields.toVector)
  }

  /** A JSON array. */
  final case class Array(value: Vector[Json]) extends Json {
    override def isArray: Bool                  = true
    override def asArray: JsonSelection         = JsonSelection.single(this)
    override def elements: Option[Vector[Json]] = Some(value)
    override protected def typeLabel: Str       = "array"

    override def apply(index: Int): JsonSelection =
      if (index >= 0 && index < value.length) JsonSelection.single(value(index))
      else JsonSelection.error(JsonError.indexOutOfBounds(index, value.length))

    override protected def modifyAtPath(
      nodes: IndexedSeq[DynamicOptic.Node],
      idx: Int,
      f: Json => Either[JsonError, Json]
    ): Either[JsonError, Json] =
      nodes(idx) match {
        case DynamicOptic.Node.AtIndex(i) =>
          if (i < 0 || i >= value.length) Left(JsonError.indexOutOfBounds(i, value.length))
          else {
            val result = if (idx == nodes.length - 1) f(value(i)) else value(i).modifyAtPath(nodes, idx + 1, f)
            result.map(newV => Array(value.updated(i, newV)))
          }
        case DynamicOptic.Node.Elements =>
          var errors      = Vector.empty[JsonError]
          val newElements = value.zipWithIndex.map { case (elem, i) =>
            val result = if (idx == nodes.length - 1) f(elem) else elem.modifyAtPath(nodes, idx + 1, f)
            result match {
              case Right(v)  => v
              case Left(err) => errors = errors :+ err.atIndex(i); elem
            }
          }
          if (errors.isEmpty) Right(Array(newElements))
          else Left(errors.reduce(_ ++ _))
        case _ => Left(JsonError.typeMismatch("array access", nodes(idx).toString))
      }

    override protected def deleteAtPath(
      nodes: IndexedSeq[DynamicOptic.Node],
      idx: Int
    ): Either[JsonError, Json] =
      nodes(idx) match {
        case DynamicOptic.Node.AtIndex(i) =>
          if (i < 0 || i >= value.length) Left(JsonError.indexOutOfBounds(i, value.length))
          else if (idx == nodes.length - 1) Right(Array(value.patch(i, Nil, 1)))
          else value(i).deleteAtPath(nodes, idx + 1).map(newV => Array(value.updated(i, newV)))
        case _ => Left(JsonError.typeMismatch("array access", nodes(idx).toString))
      }

    override protected def insertAtPath(
      nodes: IndexedSeq[DynamicOptic.Node],
      idx: Int,
      newValue: Json
    ): Either[JsonError, Json] =
      nodes(idx) match {
        case DynamicOptic.Node.AtIndex(i) =>
          if (idx == nodes.length - 1) {
            if (i < 0) Left(JsonError.indexOutOfBounds(i, value.length))
            else if (i >= value.length) Right(Array(value :+ newValue))
            else Right(Array(value.updated(i, newValue)))
          } else {
            if (i < 0 || i >= value.length) Left(JsonError.indexOutOfBounds(i, value.length))
            else value(i).insertAtPath(nodes, idx + 1, newValue).map(newV => Array(value.updated(i, newV)))
          }
        case _ => Left(JsonError.typeMismatch("array access", nodes(idx).toString))
      }

    override protected def printTo(sb: StringBuilder): Unit = {
      sb.append('[')
      var first = true
      value.foreach { v =>
        if (!first) sb.append(',')
        first = false
        v.printTo(sb)
      }
      sb.append(']')
    }

    override protected def printPrettyTo(sb: StringBuilder, indent: Int, depth: Int): Unit =
      if (value.isEmpty) {
        sb.append("[]")
      } else {
        sb.append("[\n")
        var first = true
        value.foreach { v =>
          if (!first) sb.append(",\n")
          first = false
          sb.append(" " * (indent * (depth + 1)))
          v.printPrettyTo(sb, indent, depth + 1)
        }
        sb.append('\n')
        sb.append(" " * (indent * depth))
        sb.append(']')
      }
  }

  object Array {
    val empty: Array = Array(Vector.empty)

    def apply(elements: Json*): Array = Array(elements.toVector)
  }

  /** A JSON string. */
  final case class String(value: java.lang.String) extends Json {
    override def isString: Bool                           = true
    override def asString: JsonSelection                  = JsonSelection.single(this)
    override def stringValue: Option[scala.Predef.String] = Some(value)
    override protected def typeLabel: java.lang.String    = "string"

    override protected def modifyAtPath(
      nodes: IndexedSeq[DynamicOptic.Node],
      idx: Int,
      f: Json => Either[JsonError, Json]
    ): Either[JsonError, Json] = Left(JsonError.typeMismatch("object or array", "string"))

    override protected def deleteAtPath(
      nodes: IndexedSeq[DynamicOptic.Node],
      idx: Int
    ): Either[JsonError, Json] = Left(JsonError.typeMismatch("object or array", "string"))

    override protected def insertAtPath(
      nodes: IndexedSeq[DynamicOptic.Node],
      idx: Int,
      newValue: Json
    ): Either[JsonError, Json] = Left(JsonError.typeMismatch("object or array", "string"))

    override protected def printTo(sb: StringBuilder): Unit = printString(sb, value)

    override protected def printPrettyTo(sb: StringBuilder, indent: Int, depth: Int): Unit = printTo(sb)
  }

  /** A JSON number stored as a string to preserve precision. */
  final case class Number(value: java.lang.String) extends Json {
    override def isNumber: Bool                        = true
    override def asNumber: JsonSelection               = JsonSelection.single(this)
    override def numberValue: Option[java.lang.String] = Some(value)
    override protected def typeLabel: java.lang.String = "number"

    def toInt: Option[Int]               = scala.util.Try(value.toInt).toOption
    def toLong: Option[Long]             = scala.util.Try(value.toLong).toOption
    def toDouble: Option[Double]         = scala.util.Try(value.toDouble).toOption
    def toBigInt: Option[BigInt]         = scala.util.Try(BigInt(value)).toOption
    def toBigDecimal: Option[BigDecimal] = scala.util.Try(BigDecimal(value)).toOption

    override protected def modifyAtPath(
      nodes: IndexedSeq[DynamicOptic.Node],
      idx: Int,
      f: Json => Either[JsonError, Json]
    ): Either[JsonError, Json] = Left(JsonError.typeMismatch("object or array", "number"))

    override protected def deleteAtPath(
      nodes: IndexedSeq[DynamicOptic.Node],
      idx: Int
    ): Either[JsonError, Json] = Left(JsonError.typeMismatch("object or array", "number"))

    override protected def insertAtPath(
      nodes: IndexedSeq[DynamicOptic.Node],
      idx: Int,
      newValue: Json
    ): Either[JsonError, Json] = Left(JsonError.typeMismatch("object or array", "number"))

    override protected def printTo(sb: StringBuilder): Unit = sb.append(value)

    override protected def printPrettyTo(sb: StringBuilder, indent: Int, depth: Int): Unit = printTo(sb)
  }

  object Number {
    def apply(value: Int): Number        = Number(value.toString)
    def apply(value: Long): Number       = Number(value.toString)
    def apply(value: Double): Number     = Number(value.toString)
    def apply(value: BigInt): Number     = Number(value.toString)
    def apply(value: BigDecimal): Number = Number(value.toString)
  }

  /** A JSON boolean. */
  final case class Boolean(value: scala.Boolean) extends Json {
    override def isBoolean: Bool                       = true
    override def asBoolean: JsonSelection              = JsonSelection.single(this)
    override def booleanValue: Option[scala.Boolean]   = Some(value)
    override protected def typeLabel: java.lang.String = "boolean"

    override protected def modifyAtPath(
      nodes: IndexedSeq[DynamicOptic.Node],
      idx: Int,
      f: Json => Either[JsonError, Json]
    ): Either[JsonError, Json] = Left(JsonError.typeMismatch("object or array", "boolean"))

    override protected def deleteAtPath(
      nodes: IndexedSeq[DynamicOptic.Node],
      idx: Int
    ): Either[JsonError, Json] = Left(JsonError.typeMismatch("object or array", "boolean"))

    override protected def insertAtPath(
      nodes: IndexedSeq[DynamicOptic.Node],
      idx: Int,
      newValue: Json
    ): Either[JsonError, Json] = Left(JsonError.typeMismatch("object or array", "boolean"))

    override protected def printTo(sb: StringBuilder): Unit = sb.append(if (value) "true" else "false")

    override protected def printPrettyTo(sb: StringBuilder, indent: Int, depth: Int): Unit = printTo(sb)
  }

  object Boolean {
    val True: Boolean  = Boolean(true)
    val False: Boolean = Boolean(false)
  }

  /** JSON null. */
  case object Null extends Json {
    override def isNull: Bool             = true
    override def asNull: JsonSelection    = JsonSelection.single(this)
    override protected def typeLabel: Str = "null"

    override protected def modifyAtPath(
      nodes: IndexedSeq[DynamicOptic.Node],
      idx: Int,
      f: Json => Either[JsonError, Json]
    ): Either[JsonError, Json] = Left(JsonError.typeMismatch("object or array", "null"))

    override protected def deleteAtPath(
      nodes: IndexedSeq[DynamicOptic.Node],
      idx: Int
    ): Either[JsonError, Json] = Left(JsonError.typeMismatch("object or array", "null"))

    override protected def insertAtPath(
      nodes: IndexedSeq[DynamicOptic.Node],
      idx: Int,
      newValue: Json
    ): Either[JsonError, Json] = Left(JsonError.typeMismatch("object or array", "null"))

    override protected def printTo(sb: StringBuilder): Unit = sb.append("null")

    override protected def printPrettyTo(sb: StringBuilder, indent: Int, depth: Int): Unit = printTo(sb)
  }

  // ============ Parsing ============

  /** Parses a JSON string. */
  def parse(input: java.lang.String): Either[JsonError, Json] = {
    val reader = new JsonParser(input)
    reader.parse()
  }

  /** Parses JSON from a byte array. */
  def parse(input: scala.Array[Byte]): Either[JsonError, Json] =
    parse(new java.lang.String(input, StandardCharsets.UTF_8))

  /** Parses JSON from a ByteBuffer. */
  def parse(input: ByteBuffer): Either[JsonError, Json] = {
    val bytes = new scala.Array[Byte](input.remaining())
    input.get(bytes)
    parse(bytes)
  }

  // ============ KV Interop ============

  /**
   * Assembles JSON from a sequence of path-value pairs.
   *
   * @param kvs
   *   The path-value pairs
   * @return
   *   Either an error (for conflicting paths) or the assembled JSON
   */
  def fromKV(kvs: Seq[(DynamicOptic, Json)]): Either[JsonError, Json] = {
    var result: Json = Null
    kvs.foreach { case (path, value) =>
      result = result.insert(path, value)
    }
    Right(result)
  }

  /**
   * Assembles JSON from path-value pairs, throwing on conflict.
   */
  def fromKVUnsafe(kvs: Seq[(DynamicOptic, Json)]): Json =
    fromKV(kvs).fold(throw _, identity)

  // ============ Typed Encoding ============

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

  // ============ Constructors ============

  /** Creates a JSON object from key-value pairs. */
  def obj(fields: (java.lang.String, Json)*): Object = Object(fields.toVector)

  /** Creates a JSON array from elements. */
  def arr(elements: Json*): Array = Array(elements.toVector)

  /** Creates a JSON string. */
  def str(value: java.lang.String): String = String(value)

  /** Creates a JSON number from an Int. */
  def num(value: Int): Number = Number(value)

  /** Creates a JSON number from a Long. */
  def num(value: Long): Number = Number(value)

  /** Creates a JSON number from a Double. */
  def num(value: Double): Number = Number(value)

  /** Creates a JSON boolean. */
  def bool(value: scala.Boolean): Boolean = Boolean(value)

  /** JSON null value. */
  val `null`: Null.type = Null

  // ============ DynamicValue Interop ============

  /**
   * Converts a [[DynamicValue]] to JSON.
   *
   * This conversion is lossy for `DynamicValue` types that have no JSON
   * equivalent:
   *   - `PrimitiveValue` types like `java.time.*` are converted to strings
   *   - `DynamicValue.Variant` uses a discriminator field
   */
  def fromDynamicValue(value: DynamicValue): Json = value match {
    case DynamicValue.Primitive(pv)    => fromPrimitiveValue(pv)
    case DynamicValue.Record(flds)     => Object(flds.map { case (k, v) => (k, fromDynamicValue(v)) })
    case DynamicValue.Variant(name, v) =>
      Object(Vector("_type" -> String(name), "_value" -> fromDynamicValue(v)))
    case DynamicValue.Sequence(elems) => Array(elems.map(fromDynamicValue))
    case DynamicValue.Map(entries)    =>
      Array(entries.map { case (k, v) =>
        Object(Vector("key" -> fromDynamicValue(k), "value" -> fromDynamicValue(v)))
      })
  }

  private def fromPrimitiveValue(pv: PrimitiveValue): Json = pv match {
    case PrimitiveValue.Unit              => Null
    case PrimitiveValue.Boolean(v)        => Boolean(v)
    case PrimitiveValue.Byte(v)           => Number(v.toInt)
    case PrimitiveValue.Short(v)          => Number(v.toInt)
    case PrimitiveValue.Int(v)            => Number(v)
    case PrimitiveValue.Long(v)           => Number(v)
    case PrimitiveValue.Float(v)          => Number(v.toDouble)
    case PrimitiveValue.Double(v)         => Number(v)
    case PrimitiveValue.Char(v)           => String(v.toString)
    case PrimitiveValue.String(v)         => String(v)
    case PrimitiveValue.BigInt(v)         => Number(v)
    case PrimitiveValue.BigDecimal(v)     => Number(v)
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

  // ============ Ordering ============

  /**
   * Ordering for JSON values.
   *
   * Order: Null < Boolean < Number < String < Array < Object
   */
  implicit val ordering: Ordering[Json] = (x: Json, y: Json) => x.compare(y)

  // ============ Comparison Helpers ============

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

  private def compareObjects(a: Vector[(Str, Json)], b: Vector[(Str, Json)]): Int = {
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

  // ============ Helpers ============

  private def printString(sb: StringBuilder, s: java.lang.String): Unit = {
    sb.append('"')
    var i = 0
    while (i < s.length) {
      val c = s.charAt(i)
      c match {
        case '"'         => sb.append("\\\"")
        case '\\'        => sb.append("\\\\")
        case '\b'        => sb.append("\\b")
        case '\f'        => sb.append("\\f")
        case '\n'        => sb.append("\\n")
        case '\r'        => sb.append("\\r")
        case '\t'        => sb.append("\\t")
        case _ if c < 32 =>
          sb.append("\\u")
          sb.append(f"${c.toInt}%04x")
        case _ => sb.append(c)
      }
      i += 1
    }
    sb.append('"')
  }
}

/**
 * A simple JSON parser.
 */
private class JsonParser(input: java.lang.String) {
  private var pos: Int     = 0
  private var line: Long   = 1
  private var column: Long = 1

  def parse(): Either[JsonError, Json] = {
    skipWhitespace()
    if (pos >= input.length) Left(JsonError.parseError("Unexpected end of input", pos, line, column))
    else {
      val result = parseValue()
      result.flatMap { json =>
        skipWhitespace()
        if (pos < input.length)
          Left(JsonError.parseError(s"Unexpected character: ${input.charAt(pos)}", pos, line, column))
        else Right(json)
      }
    }
  }

  private def parseValue(): Either[JsonError, Json] = {
    skipWhitespace()
    if (pos >= input.length) Left(JsonError.parseError("Unexpected end of input", pos, line, column))
    else {
      input.charAt(pos) match {
        case '{'                                     => parseObject()
        case '['                                     => parseArray()
        case '"'                                     => parseString()
        case 't'                                     => parseTrue()
        case 'f'                                     => parseFalse()
        case 'n'                                     => parseNull()
        case c if c == '-' || (c >= '0' && c <= '9') => parseNumber()
        case c                                       => Left(JsonError.parseError(s"Unexpected character: $c", pos, line, column))
      }
    }
  }

  private def parseObject(): Either[JsonError, Json] = {
    advance() // skip '{'
    skipWhitespace()
    if (pos < input.length && input.charAt(pos) == '}') {
      advance()
      Right(Json.Object.empty)
    } else {
      var fields                   = Vector.empty[(java.lang.String, Json)]
      var continue                 = true
      var error: Option[JsonError] = None

      while (continue && error.isEmpty) {
        skipWhitespace()
        parseString() match {
          case Left(err)      => error = Some(err)
          case Right(keyJson) =>
            val key = keyJson.stringValue.get
            skipWhitespace()
            if (pos >= input.length || input.charAt(pos) != ':') {
              error = Some(JsonError.parseError("Expected ':'", pos, line, column))
            } else {
              advance() // skip ':'
              parseValue() match {
                case Left(err)    => error = Some(err)
                case Right(value) =>
                  fields = fields :+ (key, value)
                  skipWhitespace()
                  if (pos < input.length) {
                    input.charAt(pos) match {
                      case ',' => advance()
                      case '}' => advance(); continue = false
                      case _   => error = Some(JsonError.parseError("Expected ',' or '}'", pos, line, column))
                    }
                  } else {
                    error = Some(JsonError.parseError("Unexpected end of input", pos, line, column))
                  }
              }
            }
        }
      }

      error match {
        case Some(err) => Left(err)
        case None      => Right(Json.Object(fields))
      }
    }
  }

  private def parseArray(): Either[JsonError, Json] = {
    advance() // skip '['
    skipWhitespace()
    if (pos < input.length && input.charAt(pos) == ']') {
      advance()
      Right(Json.Array.empty)
    } else {
      var elements                 = Vector.empty[Json]
      var continue                 = true
      var error: Option[JsonError] = None

      while (continue && error.isEmpty) {
        parseValue() match {
          case Left(err)    => error = Some(err)
          case Right(value) =>
            elements = elements :+ value
            skipWhitespace()
            if (pos < input.length) {
              input.charAt(pos) match {
                case ',' => advance()
                case ']' => advance(); continue = false
                case _   => error = Some(JsonError.parseError("Expected ',' or ']'", pos, line, column))
              }
            } else {
              error = Some(JsonError.parseError("Unexpected end of input", pos, line, column))
            }
        }
      }

      error match {
        case Some(err) => Left(err)
        case None      => Right(Json.Array(elements))
      }
    }
  }

  private def parseString(): Either[JsonError, Json] =
    if (pos >= input.length || input.charAt(pos) != '"') {
      Left(JsonError.parseError("Expected '\"'", pos, line, column))
    } else {
      advance() // skip opening '"'
      val sb                       = new StringBuilder
      var done                     = false
      var error: Option[JsonError] = None

      while (!done && error.isEmpty) {
        if (pos >= input.length) {
          error = Some(JsonError.parseError("Unterminated string", pos, line, column))
        } else {
          val c = input.charAt(pos)
          c match {
            case '"'  => advance(); done = true
            case '\\' =>
              advance()
              if (pos >= input.length) {
                error = Some(JsonError.parseError("Unterminated escape sequence", pos, line, column))
              } else {
                input.charAt(pos) match {
                  case '"'  => sb.append('"'); advance()
                  case '\\' => sb.append('\\'); advance()
                  case '/'  => sb.append('/'); advance()
                  case 'b'  => sb.append('\b'); advance()
                  case 'f'  => sb.append('\f'); advance()
                  case 'n'  => sb.append('\n'); advance()
                  case 'r'  => sb.append('\r'); advance()
                  case 't'  => sb.append('\t'); advance()
                  case 'u'  =>
                    advance()
                    if (pos + 4 > input.length) {
                      error = Some(JsonError.parseError("Invalid unicode escape", pos, line, column))
                    } else {
                      val hex = input.substring(pos, pos + 4)
                      try {
                        sb.append(Integer.parseInt(hex, 16).toChar)
                        pos += 4
                        column += 4
                      } catch {
                        case _: NumberFormatException =>
                          error = Some(JsonError.parseError("Invalid unicode escape", pos, line, column))
                      }
                    }
                  case other =>
                    error = Some(JsonError.parseError(s"Invalid escape character: $other", pos, line, column))
                }
              }
            case _ if c < 32 =>
              error = Some(JsonError.parseError("Control character in string", pos, line, column))
            case _ => sb.append(c); advance()
          }
        }
      }

      error match {
        case Some(err) => Left(err)
        case None      => Right(Json.String(sb.toString))
      }
    }

  private def parseNumber(): Either[JsonError, Json] = {
    val start = pos
    // Handle negative sign
    if (pos < input.length && input.charAt(pos) == '-') advance()
    // Integer part
    if (pos < input.length && input.charAt(pos) == '0') {
      advance()
    } else {
      while (pos < input.length && input.charAt(pos) >= '0' && input.charAt(pos) <= '9') advance()
    }
    // Fractional part
    if (pos < input.length && input.charAt(pos) == '.') {
      advance()
      while (pos < input.length && input.charAt(pos) >= '0' && input.charAt(pos) <= '9') advance()
    }
    // Exponent part
    if (pos < input.length && (input.charAt(pos) == 'e' || input.charAt(pos) == 'E')) {
      advance()
      if (pos < input.length && (input.charAt(pos) == '+' || input.charAt(pos) == '-')) advance()
      while (pos < input.length && input.charAt(pos) >= '0' && input.charAt(pos) <= '9') advance()
    }
    Right(Json.Number(input.substring(start, pos)))
  }

  private def parseTrue(): Either[JsonError, Json] =
    if (input.substring(pos).startsWith("true")) {
      pos += 4
      column += 4
      Right(Json.Boolean.True)
    } else {
      Left(JsonError.parseError("Expected 'true'", pos, line, column))
    }

  private def parseFalse(): Either[JsonError, Json] =
    if (input.substring(pos).startsWith("false")) {
      pos += 5
      column += 5
      Right(Json.Boolean.False)
    } else {
      Left(JsonError.parseError("Expected 'false'", pos, line, column))
    }

  private def parseNull(): Either[JsonError, Json] =
    if (input.substring(pos).startsWith("null")) {
      pos += 4
      column += 4
      Right(Json.Null)
    } else {
      Left(JsonError.parseError("Expected 'null'", pos, line, column))
    }

  private def skipWhitespace(): Unit =
    while (pos < input.length) {
      input.charAt(pos) match {
        case ' ' | '\t' | '\r' => advance()
        case '\n'              => pos += 1; line += 1; column = 1
        case _                 => return
      }
    }

  private def advance(): Unit = {
    pos += 1
    column += 1
  }
}
