package zio.blocks.schema.json

import zio.blocks.chunk.{Chunk, ChunkBuilder}
import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue}
import java.nio.ByteBuffer
import scala.util.control.NonFatal

/**
 * A sealed trait representing a JSON value.
 *
 * The `Json` type provides a complete representation of JSON data with six
 * possible cases: Object, Array, String, Number, Boolean, and Null.
 *
 * Key design decisions:
 *   - Objects use `Chunk[(String, Json)]` to preserve insertion order
 *   - Object equality is order-independent (keys are compared as sets)
 *   - Numbers use `String` to preserve exact representation
 *   - All navigation methods return `JsonSelection` for fluent chaining
 */
sealed trait Json {

  // ─────────────────────────────────────────────────────────────────────────
  // Type Testing
  // ─────────────────────────────────────────────────────────────────────────

  /** Returns true if this is a JSON object. */
  def isObject: Boolean = false

  /** Returns true if this is a JSON array. */
  def isArray: Boolean = false

  /** Returns true if this is a JSON string. */
  def isString: Boolean = false

  /** Returns true if this is a JSON number. */
  def isNumber: Boolean = false

  /** Returns true if this is a JSON boolean. */
  def isBoolean: Boolean = false

  /** Returns true if this is JSON null. */
  def isNull: Boolean = false

  // ─────────────────────────────────────────────────────────────────────────
  // Type Filtering (returns JsonSelection for fluent chaining)
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * If this is an object, returns it wrapped in a JsonSelection, otherwise
   * returns an empty selection.
   */
  def asObject: JsonSelection = JsonSelection.empty

  /**
   * If this is an array, returns it wrapped in a JsonSelection, otherwise
   * returns an empty selection.
   */
  def asArray: JsonSelection = JsonSelection.empty

  /**
   * If this is a string, returns it wrapped in a JsonSelection, otherwise
   * returns an empty selection.
   */
  def asString: JsonSelection = JsonSelection.empty

  /**
   * If this is a number, returns it wrapped in a JsonSelection, otherwise
   * returns an empty selection.
   */
  def asNumber: JsonSelection = JsonSelection.empty

  /**
   * If this is a boolean, returns it wrapped in a JsonSelection, otherwise
   * returns an empty selection.
   */
  def asBoolean: JsonSelection = JsonSelection.empty

  /**
   * If this is null, returns it wrapped in a JsonSelection, otherwise returns
   * an empty selection.
   */
  def asNull: JsonSelection = JsonSelection.empty

  // ─────────────────────────────────────────────────────────────────────────
  // Direct Accessors
  // ─────────────────────────────────────────────────────────────────────────

  /** Returns the fields if this is an object, otherwise an empty Chunk. */
  def fields: Chunk[(String, Json)] = Chunk.empty

  /** Returns the elements if this is an array, otherwise an empty Chunk. */
  def elements: Chunk[Json] = Chunk.empty

  // ─────────────────────────────────────────────────────────────────────────
  // Navigation
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Navigates to a field in an object by key. Returns a JsonSelection
   * containing the value, or an error if not found.
   */
  def get(key: String): JsonSelection =
    JsonSelection.fail(JsonError(s"Cannot get field '$key' from non-object JSON value"))

  /**
   * Navigates to an element in an array by index. Returns a JsonSelection
   * containing the value, or an error if index is out of bounds.
   */
  def get(index: Int): JsonSelection =
    JsonSelection.fail(JsonError(s"Cannot get index $index from non-array JSON value"))

  // ─────────────────────────────────────────────────────────────────────────
  // Path-based Navigation and Modification (DynamicOptic)
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Navigates to the value(s) at the given path. Returns a JsonSelection that
   * may contain zero or more values.
   */
  def get(path: DynamicOptic): JsonSelection = Json.getAtPath(this, path)

  /**
   * Modifies the value at the given path using a function. If the path doesn't
   * exist, returns the original JSON unchanged.
   */
  def modify(path: DynamicOptic)(f: Json => Json): Json = Json.modifyAtPath(this, path, f).getOrElse(this)

  /**
   * Modifies the value at the given path using a partial function. Returns Left
   * with an error if the path doesn't exist or the partial function is not
   * defined.
   */
  def modifyOrFail(path: DynamicOptic)(pf: PartialFunction[Json, Json]): Either[JsonError, Json] =
    Json.modifyAtPathOrFail(this, path, pf)

  /**
   * Sets a value at the given path. If the path doesn't exist, returns the
   * original JSON unchanged.
   */
  def set(path: DynamicOptic, value: Json): Json = modify(path)(_ => value)

  /**
   * Sets a value at the given path. Returns Left with an error if the path
   * doesn't exist.
   */
  def setOrFail(path: DynamicOptic, value: Json): Either[JsonError, Json] =
    Json.modifyAtPathOrFail(this, path, { case _ => value })

  /**
   * Deletes the value at the given path. If the path doesn't exist, returns the
   * original JSON unchanged.
   */
  def delete(path: DynamicOptic): Json = Json.deleteAtPath(this, path).getOrElse(this)

  /**
   * Deletes the value at the given path. Returns Left with an error if the path
   * doesn't exist.
   */
  def deleteOrFail(path: DynamicOptic): Either[JsonError, Json] = Json.deleteAtPathOrFail(this, path)

  /**
   * Inserts a value at the given path. For arrays, inserts at the specified
   * index. For objects, adds the field. If the path already exists, returns the
   * original JSON unchanged.
   */
  def insert(path: DynamicOptic, value: Json): Json = Json.insertAtPath(this, path, value).getOrElse(this)

  /**
   * Inserts a value at the given path. Returns Left with an error if the path
   * already exists or the parent doesn't exist.
   */
  def insertOrFail(path: DynamicOptic, value: Json): Either[JsonError, Json] =
    Json.insertAtPathOrFail(this, path, value)

  // ─────────────────────────────────────────────────────────────────────────
  // Merging
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Merges this JSON value with another using the specified strategy.
   */
  def merge(that: Json, strategy: MergeStrategy = MergeStrategy.Auto): Json = Json.merge(this, that, strategy)

  // ─────────────────────────────────────────────────────────────────────────
  // Normalization
  // ─────────────────────────────────────────────────────────────────────────

  /** Recursively sorts all object keys alphabetically. */
  def sortKeys: Json = this

  /** Recursively removes all null values from objects. */
  def dropNulls: Json = this

  /** Recursively removes empty objects and arrays. */
  def dropEmpty: Json = this

  /** Applies sortKeys, dropNulls, and dropEmpty. */
  def normalize: Json = sortKeys.dropNulls.dropEmpty

  // ─────────────────────────────────────────────────────────────────────────
  // Encoding
  // ─────────────────────────────────────────────────────────────────────────

  /** Prints this JSON value to a string using the default WriterConfig. */
  def print: String = Json.jsonCodec.encodeToString(this)

  /** Prints this JSON value to a string using the specified WriterConfig. */
  def print(config: WriterConfig): String = Json.jsonCodec.encodeToString(this, config)

  /** Prints this JSON value to a byte array. */
  def printBytes: Array[Byte] = Json.jsonCodec.encode(this)

  /** Prints this JSON value to a byte array with the specified config. */
  def printBytes(config: WriterConfig): Array[Byte] = Json.jsonCodec.encode(this, config)

  /** Prints this JSON value to a Chunk of bytes (UTF-8). */
  def printChunk: Chunk[Byte] = Chunk.fromArray(printBytes)

  /** Prints this JSON value to a Chunk of bytes (UTF-8) with configuration. */
  def printChunk(config: WriterConfig): Chunk[Byte] = Chunk.fromArray(printBytes(config))

  /** Prints this JSON value into the provided ByteBuffer. */
  def printTo(buffer: ByteBuffer): Unit = printTo(buffer, WriterConfig)

  /** Prints this JSON value into the provided ByteBuffer with config. */
  def printTo(buffer: ByteBuffer, config: WriterConfig): Unit = buffer.put(printBytes(config))

  // ─────────────────────────────────────────────────────────────────────────
  // Conversion
  // ─────────────────────────────────────────────────────────────────────────

  /** Converts this JSON value to a DynamicValue. */
  def toDynamicValue: DynamicValue = Json.toDynamicValue(this)

  /**
   * Decodes this JSON value to a value of type A using the implicit
   * JsonDecoder.
   */
  def as[A](implicit decoder: JsonDecoder[A]): Either[JsonError, A] = decoder.decode(this)

  /**
   * Decodes this JSON value to a value of type A, throwing JsonError on
   * failure.
   */
  def asUnsafe[A](implicit decoder: JsonDecoder[A]): A = as[A].fold(e => throw e, identity)

  /** Extracts the string value if this is a string, otherwise returns None. */
  def stringValue: Option[String] = this match {
    case s: Json.String => Some(s.value)
    case _              => None
  }

  /** Extracts the number value if this is a number, otherwise returns None. */
  def numberValue: Option[BigDecimal] = this match {
    case n: Json.Number => n.toBigDecimalOption
    case _              => None
  }

  /**
   * Extracts the boolean value if this is a boolean, otherwise returns None.
   */
  def booleanValue: Option[Boolean] = this match {
    case b: Json.Boolean => Some(b.value)
    case _               => None
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Comparison
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Compares this JSON value with another for ordering. Objects are compared as
   * sorted key-value pairs (order-independent).
   */
  def compare(that: Json): Int

  /**
   * Returns the type index for ordering: Null=0, Boolean=1, Number=2, String=3,
   * Array=4, Object=5
   */
  def typeIndex: Int

  // ─────────────────────────────────────────────────────────────────────────
  // Transformation Methods
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Transforms this JSON value bottom-up using the given function. The function
   * receives the current path and the JSON value at that path. Child values are
   * transformed before their parents.
   */
  def transformUp(f: (DynamicOptic, Json) => Json): Json = Json.transformUpImpl(this, DynamicOptic.root, f)

  /**
   * Transforms this JSON value top-down using the given function. The function
   * receives the current path and the JSON value at that path. Parent values
   * are transformed before their children.
   */
  def transformDown(f: (DynamicOptic, Json) => Json): Json = Json.transformDownImpl(this, DynamicOptic.root, f)

  /**
   * Transforms all object keys using the given function. The function receives
   * the current path and the key at that path.
   */
  def transformKeys(f: (DynamicOptic, String) => String): Json = Json.transformKeysImpl(this, DynamicOptic.root, f)

  // ─────────────────────────────────────────────────────────────────────────
  // Filtering Methods
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Filters elements/fields based on a predicate. The function receives the
   * current path and the JSON value at that path. Only values for which the
   * predicate returns true are kept.
   */
  def filter(p: (DynamicOptic, Json) => Boolean): Json = Json.filterImpl(this, DynamicOptic.root, p)

  /**
   * Filters elements/fields based on the negation of a predicate.
   */
  def filterNot(p: (DynamicOptic, Json) => Boolean): Json = filter((path, json) => !p(path, json))

  /**
   * Projects only the specified paths from this JSON value. Creates a new JSON
   * containing only values at the given paths.
   */
  def project(paths: DynamicOptic*): Json = Json.projectImpl(this, paths)

  /**
   * Partitions elements/fields based on a predicate. Returns a tuple of
   * (matching, non-matching) JSON values.
   */
  def partition(p: (DynamicOptic, Json) => Boolean): (Json, Json) = Json.partitionImpl(this, DynamicOptic.root, p)

  // ─────────────────────────────────────────────────────────────────────────
  // Folding Methods
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Folds over the JSON structure bottom-up. The function receives the current
   * path, the JSON value, and the accumulator. Child values are folded before
   * their parents.
   */
  def foldUp[B](z: B)(f: (DynamicOptic, Json, B) => B): B = Json.foldUpImpl(this, DynamicOptic.root, z, f)

  /**
   * Folds over the JSON structure top-down. The function receives the current
   * path, the JSON value, and the accumulator. Parent values are folded before
   * their children.
   */
  def foldDown[B](z: B)(f: (DynamicOptic, Json, B) => B): B = Json.foldDownImpl(this, DynamicOptic.root, z, f)

  /**
   * Folds over the JSON structure bottom-up, allowing failure.
   */
  def foldUpOrFail[B](z: B)(f: (DynamicOptic, Json, B) => Either[JsonError, B]): Either[JsonError, B] =
    Json.foldUpOrFailImpl(this, DynamicOptic.root, z, f)

  /**
   * Folds over the JSON structure top-down, allowing failure.
   */
  def foldDownOrFail[B](z: B)(f: (DynamicOptic, Json, B) => Either[JsonError, B]): Either[JsonError, B] =
    Json.foldDownOrFailImpl(this, DynamicOptic.root, z, f)

  // ─────────────────────────────────────────────────────────────────────────
  // Query Methods
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Queries the JSON using a predicate function. Returns a JsonSelection
   * containing all values for which the predicate returns true.
   */
  def query(p: (DynamicOptic, Json) => Boolean): JsonSelection = Json.queryImpl(this, DynamicOptic.root, p)

  /**
   * Converts this JSON to a Chunk of path-value pairs. Each pair contains the
   * path to a leaf value and the value itself.
   */
  def toKV: Chunk[(DynamicOptic, Json)] = Json.toKVImpl(this, DynamicOptic.root)

  // ─────────────────────────────────────────────────────────────────────────
  // Stubbed Methods (to be implemented later)
  // ─────────────────────────────────────────────────────────────────────────

  /** Computes the difference between this JSON and another. */
  def diff(that: Json): Json = ???

  /** Applies a JSON patch to this value. */
  def patch(patch: Json): Either[JsonError, Json] = ???

  /** Checks if this JSON conforms to a JSON Schema. */
  def check(schema: Json): Either[JsonError, Unit] = ???

  /** Returns true if this JSON conforms to a JSON Schema. */
  def conforms(schema: Json): Boolean = ???
}

object Json {

  // ─────────────────────────────────────────────────────────────────────────
  // ADT Cases
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Represents a JSON object with ordered key-value pairs. Equality is
   * order-independent (compared as sorted key-value pairs).
   */
  final case class Object(value: Chunk[(java.lang.String, Json)]) extends Json {
    override def isObject: scala.Boolean = true

    override def asObject: JsonSelection = JsonSelection.succeed(this)

    override def fields: Chunk[(java.lang.String, Json)] = value

    override def typeIndex: Int = 5

    override def get(key: java.lang.String): JsonSelection = {
      var idx = 0
      val len = value.length
      while (idx < len) {
        val kv = value(idx)
        if (kv._1 == key) return JsonSelection.succeed(kv._2)
        idx += 1
      }
      JsonSelection.fail(JsonError(s"Key '$key' not found"))
    }

    override def sortKeys: Json =
      new Object(value.map { case (k, v) => (k, v.sortKeys) }.sortBy(_._1)(Ordering.String))

    override def dropNulls: Json =
      new Object(value.filterNot(_._2.isNull).map { case (k, v) => (k, v.dropNulls) })

    override def dropEmpty: Json = {
      val processed = value.map { case (k, v) => (k, v.dropEmpty) }
      new Object(processed.filterNot { case (_, v) =>
        v match {
          case obj: Object => obj.value.isEmpty
          case arr: Array  => arr.value.isEmpty
          case _           => false
        }
      })
    }

    override def compare(that: Json): Int = that match {
      case thatObj: Object =>
        // Compare as sorted key-value pairs for order-independent comparison
        val thisFields = value.sortBy(_._1)(Ordering.String)
        val thatFields = thatObj.value.sortBy(_._1)(Ordering.String)
        val minLen     = Math.min(thisFields.length, thatFields.length)
        var idx        = 0
        while (idx < minLen) {
          val (k1, v1) = thisFields(idx)
          val (k2, v2) = thatFields(idx)
          val keyCmp   = k1.compareTo(k2)
          if (keyCmp != 0) return keyCmp
          val valCmp = v1.compare(v2)
          if (valCmp != 0) return valCmp
          idx += 1
        }
        thisFields.length.compareTo(thatFields.length)
      case _ => typeIndex - that.typeIndex
    }

    override def equals(obj: Any): scala.Boolean = obj match {
      case thatObj: Object =>
        // Order-independent equality: compare sorted fields
        if (value.length != thatObj.value.length) return false
        val thisFields = value.sortBy(_._1)(Ordering.String)
        val thatFields = thatObj.value.sortBy(_._1)(Ordering.String)
        thisFields == thatFields
      case _ => false
    }

    override def hashCode(): Int =
      // Order-independent hash: sort before hashing
      value.sortBy(_._1)(Ordering.String).hashCode()
  }

  object Object {
    val empty: Object = Object(Chunk.empty)

    def apply(fields: (java.lang.String, Json)*): Object = new Object(Chunk.from(fields))
  }

  /**
   * Represents a JSON array.
   */
  final case class Array(value: Chunk[Json]) extends Json {
    override def isArray: scala.Boolean = true

    override def asArray: JsonSelection = JsonSelection.succeed(this)

    override def elements: Chunk[Json] = value

    override def typeIndex: Int = 4

    override def get(index: Int): JsonSelection =
      if (index >= 0 && index < value.length) JsonSelection.succeed(value(index))
      else JsonSelection.fail(JsonError(s"Index $index out of bounds (size: ${value.length})").atIndex(index))

    override def sortKeys: Json = new Array(value.map(_.sortKeys))

    override def dropNulls: Json = new Array(value.collect { case x if !x.isNull => x.dropNulls })

    override def dropEmpty: Json = {
      val processed = value.map(_.dropEmpty)
      new Array(processed.filterNot {
        case obj: Object => obj.value.isEmpty
        case arr: Array  => arr.value.isEmpty
        case _           => false
      })
    }

    override def compare(that: Json): Int = that match {
      case thatArr: Array =>
        val minLen = Math.min(value.length, thatArr.value.length)
        var idx    = 0
        while (idx < minLen) {
          val cmp = value(idx).compare(thatArr.value(idx))
          if (cmp != 0) return cmp
          idx += 1
        }
        value.length.compareTo(thatArr.value.length)
      case _ => typeIndex - that.typeIndex
    }
  }

  object Array {
    val empty: Array = new Array(Chunk.empty)

    def apply(elements: Json*): Array = new Array(Chunk.from(elements))
  }

  /**
   * Represents a JSON string.
   */
  final case class String(value: java.lang.String) extends Json {
    override def isString: scala.Boolean = true

    override def asString: JsonSelection = JsonSelection.succeed(this)

    override def typeIndex: Int = 3

    override def compare(that: Json): Int = that match {
      case thatStr: String => value.compareTo(thatStr.value)
      case _               => typeIndex - that.typeIndex
    }
  }

  /**
   * Represents a JSON number stored as a String to preserve exact
   * representation.
   */
  final case class Number(value: java.lang.String) extends Json {
    override def isNumber: scala.Boolean = true

    override def asNumber: JsonSelection = JsonSelection.succeed(this)

    override def typeIndex: Int = 2

    /** Returns the underlying BigDecimal value. */
    def toBigDecimal: BigDecimal = BigDecimal(value)

    /** Returns the underlying BigDecimal value if parseable, otherwise None. */
    def toBigDecimalOption: Option[BigDecimal] =
      try Some(BigDecimal(value))
      catch { case _: NumberFormatException => None }

    override def compare(that: Json): Int = that match {
      case thatNum: Number =>
        try BigDecimal(value).compare(BigDecimal(thatNum.value))
        catch {
          case err if NonFatal(err) => value.compareTo(thatNum.value)
        }
      case _ => typeIndex - that.typeIndex
    }
  }

  /**
   * Represents a JSON boolean.
   */
  final case class Boolean(value: scala.Boolean) extends Json {
    override def isBoolean: scala.Boolean = true

    override def asBoolean: JsonSelection = JsonSelection.succeed(this)

    override def typeIndex: Int = 1

    override def compare(that: Json): Int = that match {
      case thatBool: Boolean => java.lang.Boolean.compare(value, thatBool.value)
      case _                 => typeIndex - that.typeIndex
    }
  }

  object Boolean {
    def apply(value: scala.Boolean): Boolean =
      if (value) Json.True
      else Json.False
  }

  /**
   * Represents JSON null.
   */
  case object Null extends Json {

    override def isNull: scala.Boolean = true

    override def asNull: JsonSelection = JsonSelection.succeed(this)

    override def typeIndex: Int = 0

    override def compare(that: Json): Int = that match {
      case Null => 0
      case _    => typeIndex - that.typeIndex
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Constants
  // ─────────────────────────────────────────────────────────────────────────

  /** The JSON true value. */
  val True: Boolean = new Boolean(true)

  /** The JSON false value. */
  val False: Boolean = new Boolean(false)

  // ─────────────────────────────────────────────────────────────────────────
  // Parsing
  // ─────────────────────────────────────────────────────────────────────────

  /** Parses a JSON string into a Json value. */
  def parse(input: java.lang.String): Either[JsonError, Json] =
    jsonCodec.decode(input) match {
      case r: Right[_, _] => r.asInstanceOf[Either[JsonError, Json]]
      case Left(error)    => new Left(JsonError.fromSchemaError(error))
    }

  /** Parses a JSON byte array into a Json value. */
  def parse(input: scala.Array[Byte]): Either[JsonError, Json] =
    jsonCodec.decode(input) match {
      case r: Right[_, _] => r.asInstanceOf[Either[JsonError, Json]]
      case Left(error)    => new Left(JsonError.fromSchemaError(error))
    }

  /** Parses a JSON byte array with config. */
  def parse(input: scala.Array[Byte], config: ReaderConfig): Either[JsonError, Json] =
    jsonCodec.decode(input, config) match {
      case r: Right[_, _] => r.asInstanceOf[Either[JsonError, Json]]
      case Left(error)    => new Left(JsonError.fromSchemaError(error))
    }

  /** Parses a JSON string with config. */
  def parse(input: java.lang.String, config: ReaderConfig): Either[JsonError, Json] =
    jsonCodec.decode(input, config) match {
      case r: Right[_, _] => r.asInstanceOf[Either[JsonError, Json]]
      case Left(error)    => new Left(JsonError.fromSchemaError(error))
    }

  /** Parses a JSON CharSequence into a Json value. */
  def parse(input: CharSequence): Either[JsonError, Json] =
    parse(input.toString)

  /** Parses a JSON CharSequence with config. */
  def parse(input: CharSequence, config: ReaderConfig): Either[JsonError, Json] =
    parse(input.toString, config)

  /** Parses a JSON ByteBuffer into a Json value. */
  def parse(input: ByteBuffer): Either[JsonError, Json] =
    jsonCodec.decode(input) match {
      case r: Right[_, _] => r.asInstanceOf[Either[JsonError, Json]]
      case Left(error)    => new Left(JsonError.fromSchemaError(error))
    }

  /** Parses a JSON ByteBuffer with config. */
  def parse(input: ByteBuffer, config: ReaderConfig): Either[JsonError, Json] =
    jsonCodec.decode(input, config) match {
      case r: Right[_, _] => r.asInstanceOf[Either[JsonError, Json]]
      case Left(error)    => new Left(JsonError.fromSchemaError(error))
    }

  /** Parses a JSON Chunk of bytes (UTF-8) into a Json value. */
  def parse(input: Chunk[Byte]): Either[JsonError, Json] = parse(input.toArray)

  /** Parses a JSON Chunk of bytes (UTF-8) with config. */
  def parse(input: Chunk[Byte], config: ReaderConfig): Either[JsonError, Json] = parse(input.toArray, config)

  /** Parses a JSON string, throwing JsonError on failure. */
  def parseUnsafe(input: java.lang.String): Json = parse(input).fold(e => throw e, identity)

  // ─────────────────────────────────────────────────────────────────────────
  // Conversion from DynamicValue
  // ─────────────────────────────────────────────────────────────────────────

  /** Converts a DynamicValue to a Json value. */
  def fromDynamicValue(dv: DynamicValue): Json = dv match {
    case v: DynamicValue.Primitive => fromPrimitiveValue(v.value)
    case v: DynamicValue.Record    => new Object(Chunk.from(v.fields.map { case (k, v) => (k, fromDynamicValue(v)) }))
    case v: DynamicValue.Variant   => new Object(Chunk((v.caseName, fromDynamicValue(v.value))))
    case v: DynamicValue.Sequence  => new Array(Chunk.from(v.elements.map(fromDynamicValue)))
    case v: DynamicValue.Map       =>
      val entries = v.entries
      // For maps with string keys, convert to object; otherwise use array of pairs
      val allStringKeys = entries.forall {
        case (DynamicValue.Primitive(_: PrimitiveValue.String), _) => true
        case _                                                     => false
      }
      if (allStringKeys) {
        new Object(Chunk.from(entries.collect { case (DynamicValue.Primitive(k: PrimitiveValue.String), v) =>
          (k.value, fromDynamicValue(v))
        }))
      } else {
        new Array(Chunk.from(entries.map { case (k, v) =>
          new Object(Chunk(("key", fromDynamicValue(k)), ("value", fromDynamicValue(v))))
        }))
      }
  }

  private def fromPrimitiveValue(pv: PrimitiveValue): Json = pv match {
    case PrimitiveValue.Unit              => Null
    case v: PrimitiveValue.Boolean        => Boolean(v.value)
    case v: PrimitiveValue.Byte           => new Number(v.value.toString)
    case v: PrimitiveValue.Short          => new Number(v.value.toString)
    case v: PrimitiveValue.Int            => new Number(v.value.toString)
    case v: PrimitiveValue.Long           => new Number(v.value.toString)
    case v: PrimitiveValue.Float          => new Number(v.value.toString)
    case v: PrimitiveValue.Double         => new Number(v.value.toString)
    case v: PrimitiveValue.Char           => new String(v.value.toString)
    case v: PrimitiveValue.String         => new String(v.value)
    case v: PrimitiveValue.BigInt         => new Number(v.value.toString)
    case v: PrimitiveValue.BigDecimal     => new Number(v.value.toString)
    case v: PrimitiveValue.DayOfWeek      => new String(v.value.toString)
    case v: PrimitiveValue.Duration       => new String(v.value.toString)
    case v: PrimitiveValue.Instant        => new String(v.value.toString)
    case v: PrimitiveValue.LocalDate      => new String(v.value.toString)
    case v: PrimitiveValue.LocalDateTime  => new String(v.value.toString)
    case v: PrimitiveValue.LocalTime      => new String(v.value.toString)
    case v: PrimitiveValue.Month          => new String(v.value.toString)
    case v: PrimitiveValue.MonthDay       => new String(v.value.toString)
    case v: PrimitiveValue.OffsetDateTime => new String(v.value.toString)
    case v: PrimitiveValue.OffsetTime     => new String(v.value.toString)
    case v: PrimitiveValue.Period         => new String(v.value.toString)
    case v: PrimitiveValue.Year           => new String(v.value.toString)
    case v: PrimitiveValue.YearMonth      => new String(v.value.toString)
    case v: PrimitiveValue.ZoneId         => new String(v.value.toString)
    case v: PrimitiveValue.ZoneOffset     => new String(v.value.toString)
    case v: PrimitiveValue.ZonedDateTime  => new String(v.value.toString)
    case v: PrimitiveValue.Currency       => new String(v.value.toString)
    case v: PrimitiveValue.UUID           => new String(v.value.toString)
  }

  private def toDynamicValue(json: Json): DynamicValue = json match {
    case str: String   => new DynamicValue.Primitive(new PrimitiveValue.String(str.value))
    case bool: Boolean => new DynamicValue.Primitive(new PrimitiveValue.Boolean(bool.value))
    case num: Number   =>
      // Try to preserve Int/Long if possible
      val bd        = BigDecimal(num.value)
      val longValue = bd.bigDecimal.longValue
      if (bd == BigDecimal(longValue)) {
        val intValue = longValue.toInt
        if (longValue == intValue) new DynamicValue.Primitive(new PrimitiveValue.Int(intValue))
        else new DynamicValue.Primitive(new PrimitiveValue.Long(longValue))
      } else new DynamicValue.Primitive(new PrimitiveValue.BigDecimal(bd))
    case arr: Array  => new DynamicValue.Sequence(arr.value.toVector.map(toDynamicValue))
    case obj: Object =>
      new DynamicValue.Record(
        obj.value.toVector.map { case (k, v) => (k, toDynamicValue(v)) }
      )
    case _ => new DynamicValue.Primitive(PrimitiveValue.Unit)
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Merging
  // ─────────────────────────────────────────────────────────────────────────

  /** Merges two JSON values using the specified strategy. */
  private[json] def merge(left: Json, right: Json, strategy: MergeStrategy): Json = strategy match {
    case _: MergeStrategy.Replace.type => right
    case _: MergeStrategy.Shallow.type =>
      (left, right) match {
        case (lo: Object, ro: Object) => mergeObjects(lo.value, ro.value, deep = false)
        case (la: Array, ra: Array)   => new Array(la.value ++ ra.value)
        case _                        => right
      }
    case _: MergeStrategy.Custom =>
      mergeWithCustom(left, right, DynamicOptic.root, strategy.asInstanceOf[MergeStrategy.Custom].f)
    case _ => // Auto (default)
      (left, right) match {
        case (lo: Object, ro: Object) => mergeObjects(lo.value, ro.value, deep = true)
        case (la: Array, ra: Array)   => new Array(la.value ++ ra.value)
        case _                        => right
      }
  }

  private def mergeWithCustom(
    left: Json,
    right: Json,
    path: DynamicOptic,
    f: (DynamicOptic, Json, Json) => Json
  ): Json =
    (left, right) match {
      case (lo: Object, ro: Object) =>
        val leftMap  = lo.value.toMap
        val rightMap = ro.value.toMap
        val allKeys  = (lo.value.map(_._1) ++ ro.value.map(_._1)).distinct
        new Object(Chunk.from(allKeys.map { key =>
          val fieldPath = path.field(key)
          (leftMap.get(key), rightMap.get(key)) match {
            case (Some(lv), Some(rv)) => (key, mergeWithCustom(lv, rv, fieldPath, f))
            case (Some(lv), None)     => (key, lv)
            case (None, Some(rv))     => (key, rv)
            case (None, None)         => throw new IllegalStateException("Key should exist in at least one map")
          }
        }))
      case _ => f(path, left, right)
    }

  private def mergeObjects(
    left: Chunk[(java.lang.String, Json)],
    right: Chunk[(java.lang.String, Json)],
    deep: scala.Boolean
  ): Object = {
    val leftMap  = left.toMap
    val rightMap = right.toMap
    val allKeys  = (left.map(_._1) ++ right.map(_._1)).distinct
    new Object(Chunk.from(allKeys.map { key =>
      (leftMap.get(key), rightMap.get(key)) match {
        case (Some(lv), Some(rv)) =>
          if (deep) {
            (lv, rv) match {
              case (lo: Object, ro: Object) => (key, mergeObjects(lo.value, ro.value, deep = true))
              case _                        => (key, rv)
            }
          } else (key, rv)
        case (Some(lv), None) => (key, lv)
        case (None, Some(rv)) => (key, rv)
        case (None, None)     => throw new IllegalStateException("Key should exist in at least one map")
      }
    }))
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Transformation Implementations
  // ─────────────────────────────────────────────────────────────────────────

  private def transformUpImpl(json: Json, path: DynamicOptic, f: (DynamicOptic, Json) => Json): Json =
    f(
      path,
      json match {
        case obj: Object =>
          new Object(obj.value.map { case (k, v) =>
            val childPath = path.field(k)
            (k, transformUpImpl(v, childPath, f))
          })
        case arr: Array =>
          new Array(arr.value.zipWithIndex.map { case (elem, i) =>
            val childPath = path.at(i)
            transformUpImpl(elem, childPath, f)
          })
        case other => other
      }
    )

  private def transformDownImpl(json: Json, path: DynamicOptic, f: (DynamicOptic, Json) => Json): Json =
    f(path, json) match {
      case obj: Object =>
        val newFields = obj.value.map { case (k, v) =>
          val childPath = path.field(k)
          (k, transformDownImpl(v, childPath, f))
        }
        Object(newFields)
      case arr: Array =>
        val newElems = arr.value.zipWithIndex.map { case (elem, i) =>
          val childPath = path.at(i)
          transformDownImpl(elem, childPath, f)
        }
        new Array(newElems)
      case other => other
    }

  private def transformKeysImpl(
    json: Json,
    path: DynamicOptic,
    f: (DynamicOptic, java.lang.String) => java.lang.String
  ): Json =
    json match {
      case obj: Object =>
        new Object(obj.value.map { case (k, v) =>
          val newKey    = f(path.field(k), k)
          val childPath = path.field(newKey)
          (newKey, transformKeysImpl(v, childPath, f))
        })
      case arr: Array =>
        new Array(arr.value.zipWithIndex.map { case (elem, i) =>
          val childPath = path.at(i)
          transformKeysImpl(elem, childPath, f)
        })
      case other => other
    }

  // ─────────────────────────────────────────────────────────────────────────
  // Filter Implementations
  // ─────────────────────────────────────────────────────────────────────────

  private def filterImpl(json: Json, path: DynamicOptic, p: (DynamicOptic, Json) => scala.Boolean): Json =
    json match {
      case obj: Object =>
        new Object(obj.value.collect {
          case (k, v) if p(path.field(k), v) =>
            (k, filterImpl(v, path.field(k), p))
        })
      case arr: Array =>
        new Array(arr.value.zipWithIndex.collect {
          case (elem, i) if p(path.at(i), elem) =>
            filterImpl(elem, path.at(i), p)
        })
      case other => other
    }

  private def projectImpl(json: Json, paths: Seq[DynamicOptic]): Json = {
    if (paths.isEmpty) return Null
    // For each path, get the value and build a sparse result
    fromKVUnsafe(paths.flatMap { p =>
      json.get(p).toVector.map(v => (p, v))
    })
  }

  private def partitionImpl(json: Json, path: DynamicOptic, p: (DynamicOptic, Json) => scala.Boolean): (Json, Json) =
    json match {
      case obj: Object =>
        val (matching, nonMatching) = obj.value.partition { case (k, v) => p(path.field(k), v) }
        val matchingFiltered        = matching.map { case (k, v) =>
          val (m, _) = partitionImpl(v, path.field(k), p)
          (k, m)
        }
        val nonMatchingFiltered = nonMatching.map { case (k, v) =>
          val (_, n) = partitionImpl(v, path.field(k), p)
          (k, n)
        }
        (new Object(matchingFiltered), new Object(nonMatchingFiltered))
      case arr: Array =>
        val (matching, nonMatching) = arr.value.zipWithIndex.partition { case (elem, i) =>
          p(path.at(i), elem)
        }
        val matchingFiltered = matching.map { case (elem, i) =>
          val (m, _) = partitionImpl(elem, path.at(i), p)
          m
        }
        val nonMatchingFiltered = nonMatching.map { case (elem, i) =>
          val (_, n) = partitionImpl(elem, path.at(i), p)
          n
        }
        (new Array(matchingFiltered), new Array(nonMatchingFiltered))
      case other =>
        if (p(path, other)) (other, Null)
        else (Null, other)
    }

  // ─────────────────────────────────────────────────────────────────────────
  // Fold Implementations
  // ─────────────────────────────────────────────────────────────────────────

  private def foldUpImpl[B](json: Json, path: DynamicOptic, z: B, f: (DynamicOptic, Json, B) => B): B = {
    val childResult = json match {
      case obj: Object => obj.value.foldLeft(z) { case (acc, (k, v)) => foldUpImpl(v, path.field(k), acc, f) }
      case arr: Array  =>
        arr.value.zipWithIndex.foldLeft(z) { case (acc, (elem, i)) => foldUpImpl(elem, path.at(i), acc, f) }
      case _ => z
    }
    f(path, json, childResult)
  }

  private def foldDownImpl[B](json: Json, path: DynamicOptic, z: B, f: (DynamicOptic, Json, B) => B): B = {
    val afterThis = f(path, json, z)
    json match {
      case obj: Object => obj.value.foldLeft(afterThis) { case (acc, (k, v)) => foldDownImpl(v, path.field(k), acc, f) }
      case arr: Array  =>
        arr.value.zipWithIndex.foldLeft(afterThis) { case (acc, (elem, i)) => foldDownImpl(elem, path.at(i), acc, f) }
      case _ => afterThis
    }
  }

  private def foldUpOrFailImpl[B](
    json: Json,
    path: DynamicOptic,
    z: B,
    f: (DynamicOptic, Json, B) => Either[JsonError, B]
  ): Either[JsonError, B] = {
    val childResult = json match {
      case obj: Object =>
        obj.value.foldLeft[Either[JsonError, B]](new Right(z)) { case (acc, (k, v)) =>
          acc.flatMap(a => foldUpOrFailImpl(v, path.field(k), a, f))
        }
      case arr: Array =>
        arr.value.zipWithIndex.foldLeft[Either[JsonError, B]](new Right(z)) { case (acc, (elem, i)) =>
          acc.flatMap(a => foldUpOrFailImpl(elem, path.at(i), a, f))
        }
      case _ => new Right(z)
    }
    childResult.flatMap(r => f(path, json, r))
  }

  private def foldDownOrFailImpl[B](
    json: Json,
    path: DynamicOptic,
    z: B,
    f: (DynamicOptic, Json, B) => Either[JsonError, B]
  ): Either[JsonError, B] =
    f(path, json, z).flatMap { afterThis =>
      json match {
        case obj: Object =>
          obj.value.foldLeft[Either[JsonError, B]](new Right(afterThis)) { case (acc, (k, v)) =>
            acc.flatMap(a => foldDownOrFailImpl(v, path.field(k), a, f))
          }
        case arr: Array =>
          arr.value.zipWithIndex.foldLeft[Either[JsonError, B]](new Right(afterThis)) { case (acc, (elem, i)) =>
            acc.flatMap(a => foldDownOrFailImpl(elem, path.at(i), a, f))
          }
        case _ => new Right(afterThis)
      }
    }

  // ─────────────────────────────────────────────────────────────────────────
  // Query Implementations
  // ─────────────────────────────────────────────────────────────────────────

  private def queryImpl(json: Json, path: DynamicOptic, p: (DynamicOptic, Json) => scala.Boolean): JsonSelection = {
    val results = Vector.newBuilder[Json]

    def collect(j: Json, currentPath: DynamicOptic): Unit = {
      if (p(currentPath, j)) results += j
      j match {
        case obj: Object => obj.value.foreach { case (k, v) => collect(v, currentPath.field(k)) }
        case arr: Array  => arr.value.zipWithIndex.foreach { case (elem, i) => collect(elem, currentPath.at(i)) }
        case _           => ()
      }
    }

    collect(json, path)
    JsonSelection.succeedMany(results.result())
  }

  private def toKVImpl(json: Json, path: DynamicOptic): Chunk[(DynamicOptic, Json)] =
    json match {
      case obj: Object =>
        if (obj.value.isEmpty) Chunk((path, obj))
        else obj.value.flatMap { case (k, v) => toKVImpl(v, path.field(k)) }
      case arr: Array =>
        if (arr.value.isEmpty) Chunk((path, arr))
        else Chunk.from(arr.value.zipWithIndex.flatMap { case (elem, i) => toKVImpl(elem, path.at(i)) })
      case leaf => Chunk((path, leaf))
    }

  // ─────────────────────────────────────────────────────────────────────────
  // KV Construction Methods
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Creates a Json value from an encoder.
   */
  def from[A](value: A)(implicit encoder: JsonEncoder[A]): Json = encoder.encode(value)

  /**
   * Reconstructs a JSON value from path-value pairs. Returns Left if the paths
   * are inconsistent.
   */
  def fromKV(kvs: Seq[(DynamicOptic, Json)]): Either[JsonError, Json] = {
    if (kvs.isEmpty) return new Right(Null)
    try new Right(fromKVUnsafe(kvs))
    catch {
      case e: Exception => Left(JsonError(s"Failed to construct JSON from KV pairs: ${e.getMessage}"))
    }
  }

  /**
   * Reconstructs a JSON value from path-value pairs. Throws on inconsistent
   * paths.
   */
  def fromKVUnsafe(kvs: Seq[(DynamicOptic, Json)]): Json = {
    if (kvs.isEmpty) return Null
    // Simple case: single root value
    if (kvs.length == 1 && kvs.head._1.nodes.isEmpty) return kvs.head._2
    // Build the structure incrementally
    kvs.foldLeft[Json](Null) { case (acc, (path, value)) =>
      if (path.nodes.isEmpty) value
      else setOrCreatePath(acc, path, value)
    }
  }

  private def setOrCreatePath(json: Json, path: DynamicOptic, value: Json): Json = {
    val nodes = path.nodes
    if (nodes.isEmpty) return value

    def go(current: Json, idx: Int): Json = {
      if (idx >= nodes.length) return value
      nodes(idx) match {
        case field: DynamicOptic.Node.Field =>
          val name = field.name
          current match {
            case obj: Object =>
              val fieldIndex = obj.value.indexWhere(_._1 == name)
              if (fieldIndex >= 0) {
                val newValue = go(obj.value(fieldIndex)._2, idx + 1)
                new Object(obj.value.updated(fieldIndex, (name, newValue)))
              } else {
                val newValue = go(if (idx + 1 < nodes.length) inferContainer(nodes(idx + 1)) else Null, idx + 1)
                new Object(obj.value :+ (name, newValue))
              }
            case Null =>
              val newValue = go(if (idx + 1 < nodes.length) inferContainer(nodes(idx + 1)) else Null, idx + 1)
              new Object(Chunk((name, newValue)))
            case _ =>
              val newValue = go(if (idx + 1 < nodes.length) inferContainer(nodes(idx + 1)) else Null, idx + 1)
              new Object(Chunk((name, newValue)))
          }
        case atIndex: DynamicOptic.Node.AtIndex =>
          val index = atIndex.index
          current match {
            case arr: Array =>
              val padded = if (index >= arr.value.length) {
                arr.value ++ Chunk.fill(index - arr.value.length + 1)(Null)
              } else arr.value
              val newValue = go(padded(index), idx + 1)
              new Array(padded.updated(index, newValue))
            case Null =>
              val padded   = Chunk.fill(index + 1)(Null)
              val newValue = go(Null, idx + 1)
              new Array(padded.updated(index, newValue))
            case _ =>
              val padded   = Chunk.fill(index + 1)(Null)
              val newValue = go(Null, idx + 1)
              new Array(padded.updated(index, newValue))
          }
        case _ => current // Other node types not supported for construction
      }
    }

    go(json, 0)
  }

  private def inferContainer(node: DynamicOptic.Node): Json = node match {
    case _: DynamicOptic.Node.Field   => Object.empty
    case _: DynamicOptic.Node.AtIndex => Array.empty
    case _                            => Null
  }

  // ─────────────────────────────────────────────────────────────────────────
  // DynamicOptic-based Path Operations
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Gets the value(s) at the given path in the JSON structure.
   */
  private[json] def getAtPath(json: Json, path: DynamicOptic): JsonSelection = {
    val nodes = path.nodes
    if (nodes.isEmpty) return JsonSelection.succeed(json)
    var current: Either[JsonError, Chunk[Json]] = new Right(Chunk(json))
    var idx                                     = 0
    val len                                     = nodes.length
    while (idx < len && current.isRight) {
      val node = nodes(idx)
      current = current.flatMap { jsons =>
        node match {
          case field: DynamicOptic.Node.Field =>
            val name    = field.name
            val results = jsons.flatMap {
              case obj: Object =>
                obj.get(name).single match {
                  case Right(v) => Chunk(v)
                  case _        => Chunk.empty[Json]
                }
              case _ => Chunk.empty[Json]
            }
            if (results.isEmpty && jsons.nonEmpty) new Left(JsonError(s"Field '$name' not found"))
            else new Right(results)
          case atIndex: DynamicOptic.Node.AtIndex =>
            val index   = atIndex.index
            val results = jsons.flatMap {
              case arr: Array =>
                arr.get(index).single match {
                  case Right(v) => Chunk(v)
                  case _        => Chunk.empty[Json]
                }
              case _ => Chunk.empty[Json]
            }
            if (results.isEmpty && jsons.nonEmpty) new Left(JsonError(s"Index $index out of bounds").atIndex(index))
            else new Right(results)
          case _: DynamicOptic.Node.Elements.type =>
            new Right(jsons.flatMap {
              case arr: Array => arr.value
              case _          => Chunk.empty[Json]
            })
          case _: DynamicOptic.Node.MapKeys.type =>
            new Right(jsons.flatMap {
              case obj: Object => obj.value.map { case (k, _) => new String(k) }
              case _           => Chunk.empty[Json]
            })
          case _: DynamicOptic.Node.MapValues.type =>
            new Right(jsons.flatMap {
              case obj: Object => obj.value.map { case (_, v) => v }
              case _           => Chunk.empty[Json]
            })
          case atIndices: DynamicOptic.Node.AtIndices =>
            val indices = atIndices.index
            new Right(jsons.flatMap {
              case arr: Array =>
                val elems = arr.value
                Chunk.from(indices.collect { case i if i >= 0 && i < elems.length => elems(i) })
              case _ => Chunk.empty[Json]
            })
          case atMapKey: DynamicOptic.Node.AtMapKey =>
            // Convert DynamicValue key to string for JSON objects
            atMapKey.key match {
              case DynamicValue.Primitive(pv: PrimitiveValue.String) =>
                new Right(jsons.flatMap {
                  case obj: Object =>
                    obj.get(pv.value).single match {
                      case Right(v) => Chunk(v)
                      case _        => Chunk.empty[Json]
                    }
                  case _ => Chunk.empty[Json]
                })
              case _ =>
                new Left(JsonError("AtMapKey requires a string key for JSON objects"))
            }
          case atMapKeys: DynamicOptic.Node.AtMapKeys =>
            val keyStrs = atMapKeys.keys.collect { case DynamicValue.Primitive(pv: PrimitiveValue.String) =>
              pv.value
            }
            new Right(jsons.flatMap {
              case obj: Object =>
                val fieldMap = obj.value.toMap
                Chunk.from(keyStrs.flatMap(k => fieldMap.get(k)))
              case _ => Chunk.empty[Json]
            })
          case _: DynamicOptic.Node.Case => // Case is for sum types in schemas, not applicable to raw JSON
            new Right(jsons)
          case DynamicOptic.Node.Wrapped => // Wrapped is for newtypes, not applicable to raw JSON
            new Right(jsons)
        }
      }
      idx += 1
    }
    current match {
      case Right(jsons) => JsonSelection.succeedMany(jsons.toVector)
      case Left(error)  => JsonSelection.fail(error)
    }
  }

  /**
   * Modifies the value at the given path, returning Some(modified) or None if
   * the path doesn't exist.
   */
  private[json] def modifyAtPath(json: Json, path: DynamicOptic, f: Json => Json): Option[Json] = {
    val nodes = path.nodes
    if (nodes.isEmpty) return new Some(f(json))
    modifyAtPathRecursive(json, nodes, 0, f)
  }

  private def modifyAtPathRecursive(
    json: Json,
    nodes: IndexedSeq[DynamicOptic.Node],
    idx: Int,
    f: Json => Json
  ): Option[Json] = {
    if (idx >= nodes.length) return new Some(f(json))
    nodes(idx) match {
      case field: DynamicOptic.Node.Field =>
        json match {
          case obj: Object =>
            val name     = field.name
            val fields   = obj.value
            var found    = false
            var fieldIdx = 0
            val len      = fields.length
            while (fieldIdx < len && !found) {
              if (fields(fieldIdx)._1 == name) found = true
              else fieldIdx += 1
            }
            if (found) {
              modifyAtPathRecursive(fields(fieldIdx)._2, nodes, idx + 1, f).map { newValue =>
                new Object(fields.updated(fieldIdx, (name, newValue)))
              }
            } else None
          case _ => None
        }
      case atIndex: DynamicOptic.Node.AtIndex =>
        json match {
          case arr: Array =>
            val index = atIndex.index
            val elems = arr.value
            if (index >= 0 && index < elems.length) {
              modifyAtPathRecursive(elems(index), nodes, idx + 1, f).map { newValue =>
                new Array(elems.updated(index, newValue))
              }
            } else None
          case _ => None
        }
      case _: DynamicOptic.Node.Elements.type =>
        json match {
          case arr: Array =>
            new Some(new Array(arr.value.map(elem => modifyAtPathRecursive(elem, nodes, idx + 1, f).getOrElse(elem))))
          case _ => None
        }
      case _: DynamicOptic.Node.MapValues.type =>
        json match {
          case obj: Object =>
            new Some(new Object(obj.value.map { case (k, v) =>
              (k, modifyAtPathRecursive(v, nodes, idx + 1, f).getOrElse(v))
            }))
          case _ => None
        }
      case atIndices: DynamicOptic.Node.AtIndices =>
        json match {
          case arr: Array =>
            val indexSet = atIndices.index.toSet
            new Some(new Array(arr.value.zipWithIndex.map { case (elem, i) =>
              if (indexSet.contains(i)) modifyAtPathRecursive(elem, nodes, idx + 1, f).getOrElse(elem)
              else elem
            }))
          case _ => None
        }
      case atMapKey: DynamicOptic.Node.AtMapKey =>
        atMapKey.key match {
          case DynamicValue.Primitive(pv: PrimitiveValue.String) =>
            json match {
              case obj: Object =>
                val keyStr   = pv.value
                val fields   = obj.value
                var found    = false
                var fieldIdx = 0
                val len      = fields.length
                while (fieldIdx < len && !found) {
                  if (fields(fieldIdx)._1 == keyStr) found = true
                  else fieldIdx += 1
                }
                if (found) {
                  modifyAtPathRecursive(fields(fieldIdx)._2, nodes, idx + 1, f).map { newValue =>
                    new Object(fields.updated(fieldIdx, (keyStr, newValue)))
                  }
                } else None
              case _ => None
            }
          case _ => None
        }
      case atMapKeys: DynamicOptic.Node.AtMapKeys =>
        val keyStrs =
          atMapKeys.keys.collect { case DynamicValue.Primitive(pv: PrimitiveValue.String) => pv.value }.toSet
        json match {
          case obj: Object =>
            new Some(new Object(obj.value.map { case (k, v) =>
              if (keyStrs.contains(k)) (k, modifyAtPathRecursive(v, nodes, idx + 1, f).getOrElse(v))
              else (k, v)
            }))
          case _ => None
        }
      case _: DynamicOptic.Node.MapKeys.type =>
        None // Cannot modify map keys in JSON (keys are strings, not values)
      case _: DynamicOptic.Node.Case =>
        modifyAtPathRecursive(json, nodes, idx + 1, f) // Case is for sum types, pass through for JSON
      case _: DynamicOptic.Node.Wrapped.type =>
        modifyAtPathRecursive(json, nodes, idx + 1, f) // Wrapped is for newtypes, pass through for JSON
    }
  }

  /**
   * Modifies the value at the given path using a partial function. Returns Left
   * with error if the path doesn't exist or the partial function is not
   * defined.
   */
  private[json] def modifyAtPathOrFail(
    json: Json,
    path: DynamicOptic,
    pf: PartialFunction[Json, Json]
  ): Either[JsonError, Json] = {
    val nodes = path.nodes
    if (nodes.isEmpty) {
      if (pf.isDefinedAt(json)) new Right(pf(json))
      else new Left(JsonError("Partial function not defined for value at path"))
    } else modifyAtPathOrFailRecursive(json, nodes, 0, pf)
  }

  private def modifyAtPathOrFailRecursive(
    json: Json,
    nodes: IndexedSeq[DynamicOptic.Node],
    idx: Int,
    pf: PartialFunction[Json, Json]
  ): Either[JsonError, Json] =
    if (idx >= nodes.length) {
      if (pf.isDefinedAt(json)) new Right(pf(json))
      else new Left(JsonError("Partial function not defined for value at path"))
    } else {
      nodes(idx) match {
        case field: DynamicOptic.Node.Field =>
          val name = field.name
          json match {
            case obj: Object =>
              val fields   = obj.value
              var found    = false
              var fieldIdx = 0
              val len      = fields.length
              while (fieldIdx < len && !found) {
                if (fields(fieldIdx)._1 == name) found = true
                else fieldIdx += 1
              }
              if (found) {
                modifyAtPathOrFailRecursive(fields(fieldIdx)._2, nodes, idx + 1, pf).map { newValue =>
                  new Object(fields.updated(fieldIdx, (name, newValue)))
                }
              } else new Left(JsonError(s"Field '$name' not found"))
            case _ => new Left(JsonError(s"Cannot access field '$name' on non-object"))
          }
        case atIndex: DynamicOptic.Node.AtIndex =>
          val index = atIndex.index
          json match {
            case arr: Array =>
              val elems = arr.value
              if (index >= 0 && index < elems.length) {
                modifyAtPathOrFailRecursive(elems(index), nodes, idx + 1, pf).map { newValue =>
                  new Array(elems.updated(index, newValue))
                }
              } else new Left(JsonError(s"Index $index out of bounds (size: ${elems.length})"))
            case _ => new Left(JsonError(s"Cannot access index $index on non-array"))
          }
        case _: DynamicOptic.Node.Elements.type =>
          json match {
            case arr: Array =>
              arr.value.zipWithIndex
                .foldLeft[Either[JsonError, Chunk[Json]]](new Right(Chunk.empty)) {
                  case (Left(err), _)          => new Left(err)
                  case (Right(acc), (elem, _)) => modifyAtPathOrFailRecursive(elem, nodes, idx + 1, pf).map(acc :+ _)
                }
                .map(x => new Array(x))
            case _ => new Left(JsonError("Cannot iterate elements on non-array"))
          }
        case _: DynamicOptic.Node.MapValues.type =>
          json match {
            case obj: Object =>
              obj.value
                .foldLeft[Either[JsonError, Chunk[(java.lang.String, Json)]]](new Right(Chunk.empty)) {
                  case (Left(err), _)       => new Left(err)
                  case (Right(acc), (k, v)) =>
                    modifyAtPathOrFailRecursive(v, nodes, idx + 1, pf).map(newV => acc :+ (k, newV))
                }
                .map(x => new Object(x))
            case _ => new Left(JsonError("Cannot iterate map values on non-object"))
          }
        case _ =>
          // For other node types, delegate to a non-failing version and wrap the result
          modifyAtPath(json, new DynamicOptic(nodes.drop(idx)), pf.lift.andThen(_.getOrElse(json))) match {
            case some: Some[_] => new Right(some.value)
            case _             => new Left(JsonError(s"Path not found: ${new DynamicOptic(nodes)}"))
          }
      }
    }

  /**
   * Deletes the value at the given path, returning Some(modified) or None if
   * the path doesn't exist.
   */
  private[json] def deleteAtPath(json: Json, path: DynamicOptic): Option[Json] = {
    val nodes = path.nodes
    if (nodes.isEmpty) return None // Can't delete root
    deleteAtPathRecursive(json, nodes, 0)
  }

  private def deleteAtPathRecursive(
    json: Json,
    nodes: IndexedSeq[DynamicOptic.Node],
    idx: Int
  ): Option[Json] = {
    val isLast = idx == nodes.length - 1
    nodes(idx) match {
      case field: DynamicOptic.Node.Field =>
        json match {
          case obj: Object =>
            val name   = field.name
            val fields = obj.value
            if (isLast) {
              // Delete this field
              val filtered = fields.filterNot(_._1 == name)
              if (filtered.length != fields.length) new Some(new Object(filtered))
              else None
            } else {
              // Navigate into the field and continue
              var found    = false
              var fieldIdx = 0
              val len      = fields.length
              while (fieldIdx < len && !found) {
                if (fields(fieldIdx)._1 == name) found = true
                else fieldIdx += 1
              }
              if (found) {
                deleteAtPathRecursive(fields(fieldIdx)._2, nodes, idx + 1).map { newValue =>
                  new Object(fields.updated(fieldIdx, (name, newValue)))
                }
              } else None
            }
          case _ => None
        }
      case atIndex: DynamicOptic.Node.AtIndex =>
        json match {
          case arr: Array =>
            val index = atIndex.index
            val elems = arr.value
            if (isLast) {
              // Delete this element
              if (index >= 0 && index < elems.length) {
                val (before, after) = elems.splitAt(index)
                new Some(Array(before ++ after.tail))
              } else None
            } else {
              // Navigate into the element and continue
              if (index >= 0 && index < elems.length) {
                deleteAtPathRecursive(elems(index), nodes, idx + 1).map { newValue =>
                  new Array(elems.updated(index, newValue))
                }
              } else None
            }
          case _ => None
        }
      case _: DynamicOptic.Node.Elements.type =>
        if (isLast) {
          // Delete all elements
          json match {
            case _: Array => new Some(Array.empty)
            case _        => None
          }
        } else {
          // Apply delete to each element
          json match {
            case arr: Array =>
              new Some(new Array(arr.value.flatMap(elem => deleteAtPathRecursive(elem, nodes, idx + 1))))
            case _ => None
          }
        }
      case _ => None // Other node types not supported for delete
    }
  }

  /**
   * Deletes the value at the given path, returning Left with error if the path
   * doesn't exist.
   */
  private[json] def deleteAtPathOrFail(json: Json, path: DynamicOptic): Either[JsonError, Json] =
    deleteAtPath(json, path).toRight(JsonError(s"Path not found: ${path.toString}"))

  /**
   * Inserts a value at the given path, returning Some(modified) or None if the
   * path already exists.
   */
  private[json] def insertAtPath(json: Json, path: DynamicOptic, value: Json): Option[Json] = {
    val nodes = path.nodes
    if (nodes.isEmpty) return None // Can't insert at root
    insertAtPathRecursive(json, nodes, 0, value)
  }

  private def insertAtPathRecursive(
    json: Json,
    nodes: IndexedSeq[DynamicOptic.Node],
    idx: Int,
    value: Json
  ): Option[Json] = {
    val isLast = idx == nodes.length - 1
    nodes(idx) match {
      case field: DynamicOptic.Node.Field =>
        json match {
          case obj: Object =>
            val name   = field.name
            val fields = obj.value
            if (isLast) {
              // Insert this field (only if it doesn't exist)
              val exists = fields.exists(_._1 == name)
              if (!exists) new Some(new Object(fields :+ (name, value)))
              else None
            } else {
              // Navigate into the field and continue
              var found    = false
              var fieldIdx = 0
              val len      = fields.length
              while (fieldIdx < len && !found) {
                if (fields(fieldIdx)._1 == name) found = true
                else fieldIdx += 1
              }
              if (found) {
                insertAtPathRecursive(fields(fieldIdx)._2, nodes, idx + 1, value).map { newValue =>
                  new Object(fields.updated(fieldIdx, (name, newValue)))
                }
              } else None
            }
          case _ => None
        }
      case atIndex: DynamicOptic.Node.AtIndex =>
        json match {
          case arr: Array =>
            val index = atIndex.index
            val elems = arr.value
            if (isLast) {
              // Insert at this index (shifts elements right)
              if (index >= 0 && index <= elems.length) {
                val (before, after) = elems.splitAt(index)
                new Some(new Array(before ++ Vector(value) ++ after))
              } else None
            } else {
              // Navigate into the element and continue
              if (index >= 0 && index < elems.length) {
                insertAtPathRecursive(elems(index), nodes, idx + 1, value).map { newValue =>
                  new Array(elems.updated(index, newValue))
                }
              } else None
            }
          case _ => None
        }
      case _ => None // Other node types not supported for insert
    }
  }

  /**
   * Inserts a value at the given path, returning Left with error if the path
   * already exists or the parent doesn't exist.
   */
  private[json] def insertAtPathOrFail(json: Json, path: DynamicOptic, value: Json): Either[JsonError, Json] = {
    val nodes = path.nodes
    if (nodes.isEmpty) return Left(JsonError("Cannot insert at root path"))
    insertAtPathOrFailRecursive(json, nodes, 0, value)
  }

  private def insertAtPathOrFailRecursive(
    json: Json,
    nodes: IndexedSeq[DynamicOptic.Node],
    idx: Int,
    value: Json
  ): Either[JsonError, Json] = {
    val isLast = idx == nodes.length - 1
    nodes(idx) match {
      case field: DynamicOptic.Node.Field =>
        val name = field.name
        json match {
          case Object(fields) =>
            if (isLast) {
              val exists = fields.exists(_._1 == name)
              if (!exists) new Right(new Object(fields :+ (name, value)))
              else new Left(JsonError(s"Field '$name' already exists"))
            } else {
              var found    = false
              var fieldIdx = 0
              val len      = fields.length
              while (fieldIdx < len && !found) {
                if (fields(fieldIdx)._1 == name) found = true
                else fieldIdx += 1
              }
              if (found) {
                insertAtPathOrFailRecursive(fields(fieldIdx)._2, nodes, idx + 1, value).map { newValue =>
                  new Object(fields.updated(fieldIdx, (name, newValue)))
                }
              } else new Left(JsonError(s"Field '$name' not found"))
            }
          case _ => new Left(JsonError(s"Cannot access field '$name' on non-object"))
        }
      case ai: DynamicOptic.Node.AtIndex =>
        val index = ai.index
        json match {
          case arr: Array =>
            val elems = arr.value
            if (isLast) {
              if (index >= 0 && index <= elems.length) {
                val (before, after) = elems.splitAt(index)
                new Right(new Array((before :+ value) ++ after))
              } else new Left(JsonError(s"Index $index out of bounds for insert (size: ${elems.length})"))
            } else {
              if (index >= 0 && index < elems.length) {
                insertAtPathOrFailRecursive(elems(index), nodes, idx + 1, value).map { newValue =>
                  new Array(elems.updated(index, newValue))
                }
              } else new Left(JsonError(s"Index $index out of bounds (size: ${elems.length})"))
            }
          case _ => new Left(JsonError(s"Cannot access index $index on non-array"))
        }
      case other => new Left(JsonError(s"Insert not supported for path node type: $other"))
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Ordering
  // ─────────────────────────────────────────────────────────────────────────

  implicit val ordering: Ordering[Json] = (x: Json, y: Json) => x.compare(y)

  // ─────────────────────────────────────────────────────────────────────────
  // JsonBinaryCodec for Json
  // ─────────────────────────────────────────────────────────────────────────

  implicit val jsonCodec: JsonBinaryCodec[Json] = new JsonBinaryCodec[Json]() {
    override def decodeValue(in: JsonReader, default: Json): Json = {
      val b = in.nextToken()
      if (b == '"') {
        in.rollbackToken()
        new String(in.readString(null))
      } else if (b == 'f' || b == 't') {
        in.rollbackToken()
        Boolean.apply(in.readBoolean())
      } else if (b >= '0' && b <= '9' || b == '-') {
        in.rollbackToken()
        in.setMark()
        val _ = in.readBigDecimal(null)
        in.rollbackToMark()
        new Number(new java.lang.String(in.readRawValAsBytes()))
      } else if (b == '[') {
        if (in.isNextToken(']')) Array.empty
        else {
          in.rollbackToken()
          val builder     = ChunkBuilder.make[Json]()
          var idx, errIdx = 0
          try {
            while ({
              errIdx = idx
              builder.addOne(decodeValue(in, default))
              errIdx = -1
              idx += 1
              in.isNextToken(',')
            }) ()
          } catch {
            case error if NonFatal(error) && errIdx >= 0 => in.decodeError(new DynamicOptic.Node.AtIndex(errIdx), error)
          }
          if (in.isCurrentToken(']')) new Array(builder.result())
          else in.arrayEndOrCommaError()
        }
      } else if (b == '{') {
        if (in.isNextToken('}')) Object.empty
        else {
          in.rollbackToken()
          val builder               = ChunkBuilder.make[(java.lang.String, Json)]()
          var key: java.lang.String = null
          try {
            while ({
              key = in.readKeyAsString()
              builder.addOne((key, decodeValue(in, default)))
              key = null
              in.isNextToken(',')
            }) ()
          } catch {
            case error if NonFatal(error) && (key ne null) => in.decodeError(new DynamicOptic.Node.Field(key), error)
          }
          if (in.isCurrentToken('}')) new Object(builder.result())
          else in.objectEndOrCommaError()
        }
      } else {
        in.rollbackToken()
        in.readNullOrError(default, "expected JSON value")
      }
    }

    override def encodeValue(x: Json, out: JsonWriter): Unit = x match {
      case str: String   => out.writeVal(str.value)
      case bool: Boolean => out.writeVal(bool.value)
      case num: Number   => out.writeRawVal(num.value.getBytes)
      case arr: Array    =>
        out.writeArrayStart()
        val it = arr.value.iterator
        while (it.hasNext) {
          encodeValue(it.next(), out)
        }
        out.writeArrayEnd()
      case obj: Object =>
        out.writeObjectStart()
        val it = obj.value.iterator
        while (it.hasNext) {
          val kv = it.next()
          out.writeKey(kv._1)
          encodeValue(kv._2, out)
        }
        out.writeObjectEnd()
      case _ => out.writeNull()
    }

    override def nullValue: Json = Null
  }
}
