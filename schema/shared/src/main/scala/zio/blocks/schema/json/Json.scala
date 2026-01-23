package zio.blocks.schema.json

import zio.blocks.chunk.{Chunk, ChunkBuilder}
import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue}
import java.io.{Reader, Writer}
import java.nio.ByteBuffer
import scala.collection.immutable.VectorBuilder
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

  /** Returns the fields if this is an object, otherwise an empty Seq. */
  def fields: Seq[(String, Json)] = Seq.empty

  /** Returns the elements if this is an array, otherwise an empty Seq. */
  def elements: Seq[Json] = Seq.empty

  /** Returns the string value if this is a string, otherwise None. */
  def stringValue: Option[String] = None

  /** Returns the number value if this is a number, otherwise None. */
  def numberValue: Option[BigDecimal] = None

  /** Returns the boolean value if this is a boolean, otherwise None. */
  def booleanValue: Option[Boolean] = None

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
  def apply(index: Int): JsonSelection =
    JsonSelection.fail(JsonError(s"Cannot get index $index from non-array JSON value"))

  /**
   * Alias for get(key).
   */
  def apply(key: String): JsonSelection = get(key)

  // ─────────────────────────────────────────────────────────────────────────
  // Path-based Navigation and Modification (DynamicOptic)
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Navigates to the value(s) at the given path. Returns a JsonSelection that
   * may contain zero or more values.
   */
  def get(path: DynamicOptic): JsonSelection = Json.getAtPath(this, path)

  /**
   * Alias for get(path).
   */
  def apply(path: DynamicOptic): JsonSelection = get(path)

  /**
   * Modifies the value at the given path using a function. If the path doesn't
   * exist, returns the original JSON unchanged.
   */
  def modify(path: DynamicOptic)(f: Json => Json): Json =
    Json.modifyAtPath(this, path, f).getOrElse(this)

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
  def set(path: DynamicOptic, value: Json): Json =
    modify(path)(_ => value)

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
  def delete(path: DynamicOptic): Json =
    Json.deleteAtPath(this, path).getOrElse(this)

  /**
   * Deletes the value at the given path. Returns Left with an error if the path
   * doesn't exist.
   */
  def deleteOrFail(path: DynamicOptic): Either[JsonError, Json] =
    Json.deleteAtPathOrFail(this, path)

  /**
   * Inserts a value at the given path. For arrays, inserts at the specified
   * index. For objects, adds the field. If the path already exists, returns the
   * original JSON unchanged.
   */
  def insert(path: DynamicOptic, value: Json): Json =
    Json.insertAtPath(this, path, value).getOrElse(this)

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

  /** Encodes this JSON value to a string using the default WriterConfig. */
  def print: String = Json.jsonCodec.encodeToString(this)

  /** Encodes this JSON value to a string using the specified WriterConfig. */
  def print(config: WriterConfig): String = Json.jsonCodec.encodeToString(this, config)

  /** Alias for print. */
  def encode: String = print

  /** Alias for print with config. */
  def encode(config: WriterConfig): String = print(config)

  /** Encodes this JSON value to a byte array. */
  def encodeToBytes: Array[Byte] = Json.jsonCodec.encode(this)

  /** Encodes this JSON value to a byte array with the specified config. */
  def encodeToBytes(config: WriterConfig): Array[Byte] = Json.jsonCodec.encode(this, config)

  /** Encodes this JSON value to a Chunk of bytes (UTF-8). */
  def encodeToChunk: Chunk[Byte] = Chunk.fromArray(encodeToBytes)

  /** Encodes this JSON value to a Chunk of bytes (UTF-8) with configuration. */
  def encodeToChunk(config: WriterConfig): Chunk[Byte] = Chunk.fromArray(encodeToBytes(config))

  /** Encodes this JSON value and writes to the provided Writer. */
  def printTo(writer: Writer): Unit = printTo(writer, WriterConfig)

  /** Encodes this JSON value and writes to the provided Writer with config. */
  def printTo(writer: Writer, config: WriterConfig): Unit = {
    writer.write(print(config))
    writer.flush()
  }

  /** Encodes this JSON value into the provided ByteBuffer. */
  def encodeTo(buffer: ByteBuffer): Unit = encodeTo(buffer, WriterConfig)

  /** Encodes this JSON value into the provided ByteBuffer with config. */
  def encodeTo(buffer: ByteBuffer, config: WriterConfig): Unit =
    buffer.put(encodeToBytes(config))

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
  def transformUp(f: (DynamicOptic, Json) => Json): Json =
    Json.transformUpImpl(this, DynamicOptic.root, f)

  /**
   * Transforms this JSON value top-down using the given function. The function
   * receives the current path and the JSON value at that path. Parent values
   * are transformed before their children.
   */
  def transformDown(f: (DynamicOptic, Json) => Json): Json =
    Json.transformDownImpl(this, DynamicOptic.root, f)

  /**
   * Transforms all object keys using the given function. The function receives
   * the current path and the key at that path.
   */
  def transformKeys(f: (DynamicOptic, String) => String): Json =
    Json.transformKeysImpl(this, DynamicOptic.root, f)

  // ─────────────────────────────────────────────────────────────────────────
  // Filtering Methods
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Filters elements/fields based on a predicate. The function receives the
   * current path and the JSON value at that path. Only values for which the
   * predicate returns true are kept.
   */
  def filter(p: (DynamicOptic, Json) => Boolean): Json =
    Json.filterImpl(this, DynamicOptic.root, p)

  /**
   * Filters elements/fields based on the negation of a predicate.
   */
  def filterNot(p: (DynamicOptic, Json) => Boolean): Json =
    filter((path, json) => !p(path, json))

  /**
   * Projects only the specified paths from this JSON value. Creates a new JSON
   * containing only values at the given paths.
   */
  def project(paths: DynamicOptic*): Json =
    Json.projectImpl(this, paths)

  /**
   * Partitions elements/fields based on a predicate. Returns a tuple of
   * (matching, non-matching) JSON values.
   */
  def partition(p: (DynamicOptic, Json) => Boolean): (Json, Json) =
    Json.partitionImpl(this, DynamicOptic.root, p)

  // ─────────────────────────────────────────────────────────────────────────
  // Folding Methods
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Folds over the JSON structure bottom-up. The function receives the current
   * path, the JSON value, and the accumulator. Child values are folded before
   * their parents.
   */
  def foldUp[B](z: B)(f: (DynamicOptic, Json, B) => B): B =
    Json.foldUpImpl(this, DynamicOptic.root, z, f)

  /**
   * Folds over the JSON structure top-down. The function receives the current
   * path, the JSON value, and the accumulator. Parent values are folded before
   * their children.
   */
  def foldDown[B](z: B)(f: (DynamicOptic, Json, B) => B): B =
    Json.foldDownImpl(this, DynamicOptic.root, z, f)

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
  def query(p: (DynamicOptic, Json) => Boolean): JsonSelection =
    Json.queryImpl(this, DynamicOptic.root, p)

  /**
   * Converts this JSON to a sequence of path-value pairs. Each pair contains
   * the path to a leaf value and the value itself.
   */
  def toKV: Seq[(DynamicOptic, Json)] =
    Json.toKVImpl(this, DynamicOptic.root)

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
  final case class Object(value: Chunk[(scala.Predef.String, Json)]) extends Json {
    override def isObject: scala.Boolean                  = true
    override def asObject: JsonSelection                  = JsonSelection.succeed(this)
    override def fields: Seq[(scala.Predef.String, Json)] = value
    override def typeIndex: Int                           = 5

    override def get(key: scala.Predef.String): JsonSelection = {
      var idx = 0
      val len = value.length
      while (idx < len) {
        val kv = value(idx)
        if (kv._1 == key) return JsonSelection.succeed(kv._2)
        idx += 1
      }
      JsonSelection.fail(JsonError(s"Key '$key' not found").atField(key))
    }

    override def sortKeys: Json = {
      val sortedFields = value.map { case (k, v) => (k, v.sortKeys) }.sortBy(_._1)(Ordering.String)
      Object(sortedFields)
    }

    override def dropNulls: Json = {
      val filtered = value.filterNot(_._2.isNull).map { case (k, v) => (k, v.dropNulls) }
      Object(filtered)
    }

    override def dropEmpty: Json = {
      val processed = value.map { case (k, v) => (k, v.dropEmpty) }
      val filtered  = processed.filterNot { case (_, v) =>
        v match {
          case Object(fields) => fields.isEmpty
          case Array(elems)   => elems.isEmpty
          case _              => false
        }
      }
      Object(filtered)
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

    def apply(fields: (scala.Predef.String, Json)*): Object = new Object(Chunk.from(fields))
  }

  /**
   * Represents a JSON array.
   */
  final case class Array(value: Vector[Json]) extends Json {
    override def isArray: scala.Boolean = true
    override def asArray: JsonSelection = JsonSelection.succeed(this)
    override def elements: Seq[Json]    = value
    override def typeIndex: Int         = 4

    override def apply(index: Int): JsonSelection =
      if (index >= 0 && index < value.length) JsonSelection.succeed(value(index))
      else JsonSelection.fail(JsonError(s"Index $index out of bounds (size: ${value.length})").atIndex(index))

    override def sortKeys: Json = Array(value.map(_.sortKeys))

    override def dropNulls: Json = Array(value.filterNot(_.isNull).map(_.dropNulls))

    override def dropEmpty: Json = {
      val processed = value.map(_.dropEmpty)
      val filtered  = processed.filterNot {
        case Object(fields) => fields.isEmpty
        case Array(elems)   => elems.isEmpty
        case _              => false
      }
      Array(filtered)
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
    val empty: Array = Array(Vector.empty)

    def apply(elements: Json*): Array = new Array(elements.toVector)
  }

  /**
   * Represents a JSON string.
   */
  final case class String(value: scala.Predef.String) extends Json {
    override def isString: scala.Boolean                  = true
    override def asString: JsonSelection                  = JsonSelection.succeed(this)
    override def stringValue: Option[scala.Predef.String] = Some(value)
    override def typeIndex: Int                           = 3

    override def compare(that: Json): Int = that match {
      case thatStr: String => value.compareTo(thatStr.value)
      case _               => typeIndex - that.typeIndex
    }
  }

  /**
   * Represents a JSON number stored as a String to preserve exact
   * representation.
   */
  final case class Number(value: scala.Predef.String) extends Json {
    override def isNumber: scala.Boolean         = true
    override def asNumber: JsonSelection         = JsonSelection.succeed(this)
    override def numberValue: Option[BigDecimal] = scala.util.Try(BigDecimal(value)).toOption
    override def typeIndex: Int                  = 2

    /** Returns the underlying BigDecimal value. */
    def toBigDecimal: BigDecimal = BigDecimal(value)

    override def compare(that: Json): Int = that match {
      case thatNum: Number =>
        scala.util.Try(BigDecimal(value).compare(BigDecimal(thatNum.value))).getOrElse(value.compareTo(thatNum.value))
      case _ => typeIndex - that.typeIndex
    }
  }

  /**
   * Represents a JSON boolean.
   */
  final case class Boolean(value: scala.Boolean) extends Json {
    override def isBoolean: scala.Boolean            = true
    override def asBoolean: JsonSelection            = JsonSelection.succeed(this)
    override def booleanValue: Option[scala.Boolean] = Some(value)
    override def typeIndex: Int                      = 1

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
    override def typeIndex: Int        = 0

    override def compare(that: Json): Int = that match {
      case Null => 0
      case _    => typeIndex - that.typeIndex
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Constructors
  // ─────────────────────────────────────────────────────────────────────────

  /** Creates a JSON object from key-value pairs. */
  def obj(fields: (scala.Predef.String, Json)*): Object = Object(Chunk.from(fields))

  /** Creates a JSON array from elements. */
  def arr(elements: Json*): Array = Array(elements.toVector)

  /** Creates a JSON string. */
  def str(value: scala.Predef.String): String = String(value)

  /** Creates a JSON number. */
  def number(value: BigDecimal): Number = Number(value.toString)

  /** Creates a JSON number from an Int. */
  def number(value: Int): Number = Number(value.toString)

  /** Creates a JSON number from a Long. */
  def number(value: Long): Number = Number(value.toString)

  /** Creates a JSON number from a Float. */
  def number(value: Float): Number = Number(value.toString)

  /** Creates a JSON number from a Double. */
  def number(value: Double): Number = Number(value.toString)

  /** Creates a JSON boolean. */
  def bool(value: scala.Boolean): Boolean = Boolean(value)

  /** The JSON true value. */
  val True: Boolean = new Boolean(true)

  /** The JSON false value. */
  val False: Boolean = new Boolean(false)

  // ─────────────────────────────────────────────────────────────────────────
  // Parsing
  // ─────────────────────────────────────────────────────────────────────────

  /** Parses a JSON string into a Json value. */
  def parse(input: scala.Predef.String): Either[JsonError, Json] =
    jsonCodec.decode(input) match {
      case Right(j)    => Right(j)
      case Left(error) => Left(JsonError(error.message))
    }

  /** Parses a JSON byte array into a Json value. */
  def parse(input: scala.Array[Byte]): Either[JsonError, Json] =
    jsonCodec.decode(input) match {
      case Right(j)  => Right(j)
      case Left(err) => Left(JsonError(err.message))
    }

  /** Parses a JSON byte array with config. */
  def parse(input: scala.Array[Byte], config: ReaderConfig): Either[JsonError, Json] =
    jsonCodec.decode(input, config) match {
      case Right(j)  => Right(j)
      case Left(err) => Left(JsonError(err.message))
    }

  /** Parses a JSON string with config. */
  def parse(input: scala.Predef.String, config: ReaderConfig): Either[JsonError, Json] =
    jsonCodec.decode(input, config) match {
      case Right(j)    => Right(j)
      case Left(error) => Left(JsonError(error.message))
    }

  /** Parses a JSON CharSequence into a Json value. */
  def parse(input: CharSequence): Either[JsonError, Json] =
    parse(input.toString)

  /** Parses a JSON CharSequence with config. */
  def parse(input: CharSequence, config: ReaderConfig): Either[JsonError, Json] =
    parse(input.toString, config)

  /** Parses a JSON ByteBuffer into a Json value. */
  def parse(input: ByteBuffer): Either[JsonError, Json] = {
    val bytes = new scala.Array[Byte](input.remaining())
    input.get(bytes)
    parse(bytes)
  }

  /** Parses a JSON ByteBuffer with config. */
  def parse(input: ByteBuffer, config: ReaderConfig): Either[JsonError, Json] = {
    val bytes = new scala.Array[Byte](input.remaining())
    input.get(bytes)
    parse(bytes, config)
  }

  /** Parses a JSON Reader into a Json value. */
  def parse(input: Reader): Either[JsonError, Json] = {
    val sb     = new StringBuilder
    val buffer = new scala.Array[Char](8192)
    var read   = input.read(buffer)
    while (read != -1) {
      sb.appendAll(buffer, 0, read)
      read = input.read(buffer)
    }
    parse(sb.toString)
  }

  /** Parses a JSON Reader with config. */
  def parse(input: Reader, config: ReaderConfig): Either[JsonError, Json] = {
    val sb     = new StringBuilder
    val buffer = new scala.Array[Char](8192)
    var read   = input.read(buffer)
    while (read != -1) {
      sb.appendAll(buffer, 0, read)
      read = input.read(buffer)
    }
    parse(sb.toString, config)
  }

  /** Parses a JSON Chunk of bytes (UTF-8) into a Json value. */
  def parse(input: Chunk[Byte]): Either[JsonError, Json] = parse(input.toArray)

  /** Parses a JSON Chunk of bytes (UTF-8) with config. */
  def parse(input: Chunk[Byte], config: ReaderConfig): Either[JsonError, Json] = parse(input.toArray, config)

  // Decode aliases
  /** Alias for parse. */
  def decode(input: scala.Predef.String): Either[JsonError, Json] = parse(input)

  /** Alias for parse with config. */
  def decode(input: scala.Predef.String, config: ReaderConfig): Either[JsonError, Json] = parse(input, config)

  /** Alias for parse. */
  def decode(input: scala.Array[Byte]): Either[JsonError, Json] = parse(input)

  /** Alias for parse with config. */
  def decode(input: scala.Array[Byte], config: ReaderConfig): Either[JsonError, Json] = parse(input, config)

  /** Alias for parse. */
  def decode(input: CharSequence): Either[JsonError, Json] = parse(input)

  /** Alias for parse with config. */
  def decode(input: CharSequence, config: ReaderConfig): Either[JsonError, Json] = parse(input, config)

  /** Alias for parse. */
  def decode(input: ByteBuffer): Either[JsonError, Json] = parse(input)

  /** Alias for parse with config. */
  def decode(input: ByteBuffer, config: ReaderConfig): Either[JsonError, Json] = parse(input, config)

  /** Alias for parse. */
  def decode(input: Reader): Either[JsonError, Json] = parse(input)

  /** Alias for parse with config. */
  def decode(input: Reader, config: ReaderConfig): Either[JsonError, Json] = parse(input, config)

  /** Alias for parse Chunk. */
  def decode(input: Chunk[Byte]): Either[JsonError, Json] = parse(input)

  /** Alias for parse Chunk with config. */
  def decode(input: Chunk[Byte], config: ReaderConfig): Either[JsonError, Json] = parse(input, config)

  /** Parses a JSON string, throwing JsonError on failure. */
  def parseUnsafe(input: scala.Predef.String): Json = parse(input).fold(e => throw e, identity)

  /** Alias for parseUnsafe. */
  def decodeUnsafe(input: scala.Predef.String): Json = parseUnsafe(input)

  // ─────────────────────────────────────────────────────────────────────────
  // Conversion from DynamicValue
  // ─────────────────────────────────────────────────────────────────────────

  /** Converts a DynamicValue to a Json value. */
  def fromDynamicValue(dv: DynamicValue): Json = dv match {
    case DynamicValue.Primitive(pv)  => fromPrimitiveValue(pv)
    case DynamicValue.Record(fields) =>
      Object(Chunk.from(fields.map { case (k, v) => (k, fromDynamicValue(v)) }))
    case DynamicValue.Variant(caseName, value) =>
      Object(Chunk((caseName, fromDynamicValue(value))))
    case DynamicValue.Sequence(elements) =>
      Array(elements.map(fromDynamicValue))
    case DynamicValue.Map(entries) =>
      // For maps with string keys, convert to object; otherwise use array of pairs
      val allStringKeys = entries.forall {
        case (DynamicValue.Primitive(_: PrimitiveValue.String), _) => true
        case _                                                     => false
      }
      if (allStringKeys) {
        Object(Chunk.from(entries.map {
          case (DynamicValue.Primitive(k: PrimitiveValue.String), v) => (k.value, fromDynamicValue(v))
          case _                                                     => throw new IllegalStateException("Expected string key")
        }))
      } else {
        Array(entries.map { case (k, v) =>
          Object(Chunk(("key", fromDynamicValue(k)), ("value", fromDynamicValue(v))))
        })
      }
  }

  private def fromPrimitiveValue(pv: PrimitiveValue): Json = pv match {
    case PrimitiveValue.Unit              => Null
    case v: PrimitiveValue.Boolean        => Boolean(v.value)
    case v: PrimitiveValue.Byte           => Number(v.value.toString)
    case v: PrimitiveValue.Short          => Number(v.value.toString)
    case v: PrimitiveValue.Int            => Number(v.value.toString)
    case v: PrimitiveValue.Long           => Number(v.value.toString)
    case v: PrimitiveValue.Float          => Number(v.value.toString)
    case v: PrimitiveValue.Double         => Number(v.value.toString)
    case v: PrimitiveValue.Char           => String(v.value.toString)
    case v: PrimitiveValue.String         => String(v.value)
    case v: PrimitiveValue.BigInt         => Number(v.value.toString)
    case v: PrimitiveValue.BigDecimal     => Number(v.value.toString)
    case v: PrimitiveValue.DayOfWeek      => String(v.value.toString)
    case v: PrimitiveValue.Duration       => String(v.value.toString)
    case v: PrimitiveValue.Instant        => String(v.value.toString)
    case v: PrimitiveValue.LocalDate      => String(v.value.toString)
    case v: PrimitiveValue.LocalDateTime  => String(v.value.toString)
    case v: PrimitiveValue.LocalTime      => String(v.value.toString)
    case v: PrimitiveValue.Month          => String(v.value.toString)
    case v: PrimitiveValue.MonthDay       => String(v.value.toString)
    case v: PrimitiveValue.OffsetDateTime => String(v.value.toString)
    case v: PrimitiveValue.OffsetTime     => String(v.value.toString)
    case v: PrimitiveValue.Period         => String(v.value.toString)
    case v: PrimitiveValue.Year           => String(v.value.toString)
    case v: PrimitiveValue.YearMonth      => String(v.value.toString)
    case v: PrimitiveValue.ZoneId         => String(v.value.toString)
    case v: PrimitiveValue.ZoneOffset     => String(v.value.toString)
    case v: PrimitiveValue.ZonedDateTime  => String(v.value.toString)
    case v: PrimitiveValue.Currency       => String(v.value.toString)
    case v: PrimitiveValue.UUID           => String(v.value.toString)
  }

  /** Converts a Json value to a DynamicValue. */
  def toDynamicValue(json: Json): DynamicValue = json match {
    case Null       => DynamicValue.Primitive(PrimitiveValue.Unit)
    case Boolean(v) => DynamicValue.Primitive(new PrimitiveValue.Boolean(v))
    case Number(v)  =>
      // Try to preserve Int/Long if possible
      val bd        = BigDecimal(v)
      val longValue = bd.bigDecimal.longValue
      if (bd == BigDecimal(longValue)) {
        val intValue = longValue.toInt
        if (longValue == intValue) DynamicValue.Primitive(new PrimitiveValue.Int(intValue))
        else DynamicValue.Primitive(new PrimitiveValue.Long(longValue))
      } else {
        DynamicValue.Primitive(new PrimitiveValue.BigDecimal(bd))
      }
    case String(v) => DynamicValue.Primitive(new PrimitiveValue.String(v))
    case Array(v)  => DynamicValue.Sequence(v.map(toDynamicValue))
    case Object(v) => DynamicValue.Record(v.map { case (k, v) => (k, toDynamicValue(v)) }.toVector)
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Merging
  // ─────────────────────────────────────────────────────────────────────────

  /** Merges two JSON values using the specified strategy. */
  def merge(left: Json, right: Json, strategy: MergeStrategy): Json = strategy match {
    case MergeStrategy.Replace => right

    case MergeStrategy.Auto =>
      (left, right) match {
        case (lo: Object, ro: Object) => mergeObjects(lo.value, ro.value, deep = true)
        case (la: Array, ra: Array)   => Array(la.value ++ ra.value)
        case _                        => right
      }

    case MergeStrategy.Deep =>
      (left, right) match {
        case (lo: Object, ro: Object) => mergeObjects(lo.value, ro.value, deep = true)
        case (la: Array, ra: Array)   => Array(la.value ++ ra.value)
        case _                        => right
      }

    case MergeStrategy.Shallow =>
      (left, right) match {
        case (lo: Object, ro: Object) => mergeObjects(lo.value, ro.value, deep = false)
        case (la: Array, ra: Array)   => Array(la.value ++ ra.value)
        case _                        => right
      }

    case MergeStrategy.Concat =>
      (left, right) match {
        case (lo: Object, ro: Object) => mergeObjects(lo.value, ro.value, deep = true)
        case (la: Array, ra: Array)   => Array(la.value ++ ra.value)
        case _                        => right
      }

    case MergeStrategy.Custom(f) =>
      mergeWithCustom(left, right, DynamicOptic.root, f)
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
        val merged   = allKeys.map { key =>
          val fieldPath = path.field(key)
          (leftMap.get(key), rightMap.get(key)) match {
            case (Some(lv), Some(rv)) => (key, mergeWithCustom(lv, rv, fieldPath, f))
            case (Some(lv), None)     => (key, lv)
            case (None, Some(rv))     => (key, rv)
            case (None, None)         => throw new IllegalStateException("Key should exist in at least one map")
          }
        }
        Object(Chunk.from(merged))
      case _ =>
        f(path, left, right)
    }

  private def mergeObjects(
    left: Chunk[(scala.Predef.String, Json)],
    right: Chunk[(scala.Predef.String, Json)],
    deep: scala.Boolean
  ): Object = {
    val leftMap  = left.toMap
    val rightMap = right.toMap
    val allKeys  = (left.map(_._1) ++ right.map(_._1)).distinct
    val merged   = allKeys.map { key =>
      (leftMap.get(key), rightMap.get(key)) match {
        case (Some(lv), Some(rv)) if deep =>
          (lv, rv) match {
            case (lo: Object, ro: Object) => (key, mergeObjects(lo.value, ro.value, deep = true))
            case _                        => (key, rv)
          }
        case (Some(_), Some(rv)) => (key, rv)
        case (Some(lv), None)    => (key, lv)
        case (None, Some(rv))    => (key, rv)
        case (None, None)        => throw new IllegalStateException("Key should exist in at least one map")
      }
    }
    Object(Chunk.from(merged))
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Transformation Implementations
  // ─────────────────────────────────────────────────────────────────────────

  private def transformUpImpl(json: Json, path: DynamicOptic, f: (DynamicOptic, Json) => Json): Json = {
    val transformed = json match {
      case obj: Object =>
        val newFields = obj.value.map { case (k, v) =>
          val childPath = path.field(k)
          (k, transformUpImpl(v, childPath, f))
        }
        Object(newFields)
      case arr: Array =>
        val newElems = arr.value.zipWithIndex.map { case (elem, i) =>
          val childPath = path.at(i)
          transformUpImpl(elem, childPath, f)
        }
        Array(newElems)
      case other => other
    }
    f(path, transformed)
  }

  private def transformDownImpl(json: Json, path: DynamicOptic, f: (DynamicOptic, Json) => Json): Json = {
    val transformed = f(path, json)
    transformed match {
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
        Array(newElems)
      case other => other
    }
  }

  private def transformKeysImpl(
    json: Json,
    path: DynamicOptic,
    f: (DynamicOptic, scala.Predef.String) => scala.Predef.String
  ): Json =
    json match {
      case obj: Object =>
        val newFields = obj.value.map { case (k, v) =>
          val newKey    = f(path.field(k), k)
          val childPath = path.field(newKey)
          (newKey, transformKeysImpl(v, childPath, f))
        }
        Object(newFields)
      case arr: Array =>
        val newElems = arr.value.zipWithIndex.map { case (elem, i) =>
          val childPath = path.at(i)
          transformKeysImpl(elem, childPath, f)
        }
        Array(newElems)
      case other => other
    }

  // ─────────────────────────────────────────────────────────────────────────
  // Filter Implementations
  // ─────────────────────────────────────────────────────────────────────────

  private def filterImpl(json: Json, path: DynamicOptic, p: (DynamicOptic, Json) => scala.Boolean): Json =
    json match {
      case obj: Object =>
        val filtered = obj.value.filter { case (k, v) =>
          val childPath = path.field(k)
          p(childPath, v)
        }.map { case (k, v) =>
          val childPath = path.field(k)
          (k, filterImpl(v, childPath, p))
        }
        Object(filtered)
      case arr: Array =>
        val filtered = arr.value.zipWithIndex.filter { case (elem, i) =>
          val childPath = path.at(i)
          p(childPath, elem)
        }.map { case (elem, i) =>
          val childPath = path.at(i)
          filterImpl(elem, childPath, p)
        }
        Array(filtered)
      case other => other
    }

  private def projectImpl(json: Json, paths: Seq[DynamicOptic]): Json = {
    if (paths.isEmpty) return Null

    // For each path, get the value and build a sparse result
    val kvPairs: Seq[(DynamicOptic, Json)] = paths.flatMap { p =>
      json.get(p).toVector.map(v => (p, v))
    }

    // Reconstruct from key-value pairs
    fromKVUnsafe(kvPairs)
  }

  private def partitionImpl(json: Json, path: DynamicOptic, p: (DynamicOptic, Json) => scala.Boolean): (Json, Json) =
    json match {
      case obj: Object =>
        val (matching, nonMatching) = obj.value.partition { case (k, v) =>
          val childPath = path.field(k)
          p(childPath, v)
        }
        val matchingFiltered = matching.map { case (k, v) =>
          val childPath = path.field(k)
          val (m, _)    = partitionImpl(v, childPath, p)
          (k, m)
        }
        val nonMatchingFiltered = nonMatching.map { case (k, v) =>
          val childPath = path.field(k)
          val (_, n)    = partitionImpl(v, childPath, p)
          (k, n)
        }
        (Object(matchingFiltered), Object(nonMatchingFiltered))
      case arr: Array =>
        val (matching, nonMatching) = arr.value.zipWithIndex.partition { case (elem, i) =>
          val childPath = path.at(i)
          p(childPath, elem)
        }
        val matchingFiltered = matching.map { case (elem, i) =>
          val childPath = path.at(i)
          val (m, _)    = partitionImpl(elem, childPath, p)
          m
        }
        val nonMatchingFiltered = nonMatching.map { case (elem, i) =>
          val childPath = path.at(i)
          val (_, n)    = partitionImpl(elem, childPath, p)
          n
        }
        (Array(matchingFiltered), Array(nonMatchingFiltered))
      case other =>
        if (p(path, other)) (other, Null)
        else (Null, other)
    }

  // ─────────────────────────────────────────────────────────────────────────
  // Fold Implementations
  // ─────────────────────────────────────────────────────────────────────────

  private def foldUpImpl[B](json: Json, path: DynamicOptic, z: B, f: (DynamicOptic, Json, B) => B): B = {
    val childResult = json match {
      case obj: Object =>
        obj.value.foldLeft(z) { case (acc, (k, v)) =>
          val childPath = path.field(k)
          foldUpImpl(v, childPath, acc, f)
        }
      case arr: Array =>
        arr.value.zipWithIndex.foldLeft(z) { case (acc, (elem, i)) =>
          val childPath = path.at(i)
          foldUpImpl(elem, childPath, acc, f)
        }
      case _ => z
    }
    f(path, json, childResult)
  }

  private def foldDownImpl[B](json: Json, path: DynamicOptic, z: B, f: (DynamicOptic, Json, B) => B): B = {
    val afterThis = f(path, json, z)
    json match {
      case obj: Object =>
        obj.value.foldLeft(afterThis) { case (acc, (k, v)) =>
          val childPath = path.field(k)
          foldDownImpl(v, childPath, acc, f)
        }
      case arr: Array =>
        arr.value.zipWithIndex.foldLeft(afterThis) { case (acc, (elem, i)) =>
          val childPath = path.at(i)
          foldDownImpl(elem, childPath, acc, f)
        }
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
        obj.value.foldLeft[Either[JsonError, B]](Right(z)) { case (acc, (k, v)) =>
          acc.flatMap { a =>
            val childPath = path.field(k)
            foldUpOrFailImpl(v, childPath, a, f)
          }
        }
      case arr: Array =>
        arr.value.zipWithIndex.foldLeft[Either[JsonError, B]](Right(z)) { case (acc, (elem, i)) =>
          acc.flatMap { a =>
            val childPath = path.at(i)
            foldUpOrFailImpl(elem, childPath, a, f)
          }
        }
      case _ => Right(z)
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
          obj.value.foldLeft[Either[JsonError, B]](Right(afterThis)) { case (acc, (k, v)) =>
            acc.flatMap { a =>
              val childPath = path.field(k)
              foldDownOrFailImpl(v, childPath, a, f)
            }
          }
        case arr: Array =>
          arr.value.zipWithIndex.foldLeft[Either[JsonError, B]](Right(afterThis)) { case (acc, (elem, i)) =>
            acc.flatMap { a =>
              val childPath = path.at(i)
              foldDownOrFailImpl(elem, childPath, a, f)
            }
          }
        case _ => Right(afterThis)
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
        case obj: Object =>
          obj.value.foreach { case (k, v) =>
            collect(v, currentPath.field(k))
          }
        case arr: Array =>
          arr.value.zipWithIndex.foreach { case (elem, i) =>
            collect(elem, currentPath.at(i))
          }
        case _ => ()
      }
    }

    collect(json, path)
    JsonSelection.succeedMany(results.result())
  }

  private def toKVImpl(json: Json, path: DynamicOptic): Seq[(DynamicOptic, Json)] =
    json match {
      case obj: Object =>
        if (obj.value.isEmpty) Seq((path, obj))
        else
          obj.value.flatMap { case (k, v) =>
            toKVImpl(v, path.field(k))
          }
      case arr: Array =>
        if (arr.value.isEmpty) Seq((path, arr))
        else
          arr.value.zipWithIndex.flatMap { case (elem, i) =>
            toKVImpl(elem, path.at(i))
          }
      case leaf => Seq((path, leaf))
    }

  // ─────────────────────────────────────────────────────────────────────────
  // KV Construction Methods
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Creates a Json value from an encoder.
   */
  def from[A](value: A)(implicit encoder: JsonEncoder[A]): Json =
    encoder.encode(value)

  /**
   * Reconstructs a JSON value from path-value pairs. Returns Left if the paths
   * are inconsistent.
   */
  def fromKV(kvs: Seq[(DynamicOptic, Json)]): Either[JsonError, Json] = {
    if (kvs.isEmpty) return Right(Null)

    try {
      Right(fromKVUnsafe(kvs))
    } catch {
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

      val node = nodes(idx)
      node match {
        case DynamicOptic.Node.Field(name) =>
          current match {
            case obj: Object =>
              val fieldIndex = obj.value.indexWhere(_._1 == name)
              if (fieldIndex >= 0) {
                val newValue = go(obj.value(fieldIndex)._2, idx + 1)
                Object(obj.value.updated(fieldIndex, (name, newValue)))
              } else {
                val newValue = go(if (idx + 1 < nodes.length) inferContainer(nodes(idx + 1)) else Null, idx + 1)
                Object(obj.value :+ (name, newValue))
              }
            case Null =>
              val newValue = go(if (idx + 1 < nodes.length) inferContainer(nodes(idx + 1)) else Null, idx + 1)
              Object(Chunk((name, newValue)))
            case _ =>
              val newValue = go(if (idx + 1 < nodes.length) inferContainer(nodes(idx + 1)) else Null, idx + 1)
              Object(Chunk((name, newValue)))
          }

        case DynamicOptic.Node.AtIndex(index) =>
          current match {
            case arr: Array =>
              val padded = if (index >= arr.value.length) {
                arr.value ++ Vector.fill(index - arr.value.length + 1)(Null)
              } else arr.value
              val newValue = go(padded(index), idx + 1)
              Array(padded.updated(index, newValue))
            case Null =>
              val padded   = Vector.fill(index + 1)(Null)
              val newValue = go(Null, idx + 1)
              Array(padded.updated(index, newValue))
            case _ =>
              val padded   = Vector.fill(index + 1)(Null)
              val newValue = go(Null, idx + 1)
              Array(padded.updated(index, newValue))
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

    var current: Either[JsonError, Vector[Json]] = Right(Vector(json))
    var idx                                      = 0
    val len                                      = nodes.length

    while (idx < len && current.isRight) {
      val node = nodes(idx)
      current = current.flatMap { jsons =>
        node match {
          case DynamicOptic.Node.Field(name) =>
            val results = jsons.flatMap {
              case obj @ Object(_) =>
                obj.get(name).single match {
                  case Right(v) => Vector(v)
                  case Left(_)  => Vector.empty[Json]
                }
              case _ => Vector.empty[Json]
            }
            if (results.isEmpty && jsons.nonEmpty)
              Left(JsonError(s"Field '$name' not found").atField(name))
            else
              Right(results)

          case DynamicOptic.Node.AtIndex(index) =>
            val results = jsons.flatMap {
              case arr @ Array(_) =>
                arr(index).single match {
                  case Right(v) => Vector(v)
                  case Left(_)  => Vector.empty[Json]
                }
              case _ => Vector.empty[Json]
            }
            if (results.isEmpty && jsons.nonEmpty)
              Left(JsonError(s"Index $index out of bounds").atIndex(index))
            else
              Right(results)

          case DynamicOptic.Node.Elements =>
            Right(jsons.flatMap {
              case Array(elems) => elems
              case _            => Vector.empty[Json]
            })

          case DynamicOptic.Node.MapKeys =>
            Right(jsons.flatMap {
              case Object(fields) => fields.map { case (k, _) => String(k) }
              case _              => Vector.empty[Json]
            })

          case DynamicOptic.Node.MapValues =>
            Right(jsons.flatMap {
              case Object(fields) => fields.map { case (_, v) => v }
              case _              => Vector.empty[Json]
            })

          case DynamicOptic.Node.AtIndices(indices) =>
            val results = jsons.flatMap {
              case Array(elems) =>
                indices.flatMap { i =>
                  if (i >= 0 && i < elems.length) Some(elems(i))
                  else None
                }
              case _ => Vector.empty[Json]
            }
            Right(results.toVector)

          case DynamicOptic.Node.AtMapKey(key) =>
            // Convert DynamicValue key to string for JSON objects
            val keyStr = key match {
              case DynamicValue.Primitive(pv: PrimitiveValue.String) => Some(pv.value)
              case _                                                 => None
            }
            keyStr match {
              case Some(k) =>
                val results = jsons.flatMap {
                  case obj @ Object(_) =>
                    obj.get(k).single match {
                      case Right(v) => Vector(v)
                      case Left(_)  => Vector.empty[Json]
                    }
                  case _ => Vector.empty[Json]
                }
                Right(results)
              case None =>
                Left(JsonError("AtMapKey requires a string key for JSON objects"))
            }

          case DynamicOptic.Node.AtMapKeys(keys) =>
            val keyStrs = keys.flatMap {
              case DynamicValue.Primitive(pv: PrimitiveValue.String) => Some(pv.value)
              case _                                                 => None
            }
            val results = jsons.flatMap {
              case Object(fields) =>
                val fieldMap = fields.toMap
                keyStrs.flatMap(k => fieldMap.get(k))
              case _ => Vector.empty[Json]
            }
            Right(results.toVector)

          case DynamicOptic.Node.Case(_) =>
            // Case is for sum types in schemas, not applicable to raw JSON
            Right(jsons)

          case DynamicOptic.Node.Wrapped =>
            // Wrapped is for newtypes, not applicable to raw JSON
            Right(jsons)
        }
      }
      idx += 1
    }

    current match {
      case Right(results) => new JsonSelection(Right(results))
      case Left(error)    => JsonSelection.fail(error)
    }
  }

  /**
   * Modifies the value at the given path, returning Some(modified) or None if
   * the path doesn't exist.
   */
  private[json] def modifyAtPath(json: Json, path: DynamicOptic, f: Json => Json): Option[Json] = {
    val nodes = path.nodes
    if (nodes.isEmpty) return Some(f(json))

    modifyAtPathRecursive(json, nodes, 0, f)
  }

  private def modifyAtPathRecursive(
    json: Json,
    nodes: IndexedSeq[DynamicOptic.Node],
    idx: Int,
    f: Json => Json
  ): Option[Json] = {
    if (idx >= nodes.length) return Some(f(json))

    val node = nodes(idx)
    node match {
      case DynamicOptic.Node.Field(name) =>
        json match {
          case Object(fields) =>
            var found    = false
            var fieldIdx = 0
            val len      = fields.length
            while (fieldIdx < len && !found) {
              if (fields(fieldIdx)._1 == name) found = true
              else fieldIdx += 1
            }
            if (found) {
              modifyAtPathRecursive(fields(fieldIdx)._2, nodes, idx + 1, f).map { newValue =>
                Object(fields.updated(fieldIdx, (name, newValue)))
              }
            } else None
          case _ => None
        }

      case DynamicOptic.Node.AtIndex(index) =>
        json match {
          case Array(elems) =>
            if (index >= 0 && index < elems.length) {
              modifyAtPathRecursive(elems(index), nodes, idx + 1, f).map { newValue =>
                Array(elems.updated(index, newValue))
              }
            } else None
          case _ => None
        }

      case DynamicOptic.Node.Elements =>
        json match {
          case Array(elems) =>
            val modified = elems.map(elem => modifyAtPathRecursive(elem, nodes, idx + 1, f).getOrElse(elem))
            Some(Array(modified))
          case _ => None
        }

      case DynamicOptic.Node.MapValues =>
        json match {
          case Object(fields) =>
            val modified = fields.map { case (k, v) =>
              (k, modifyAtPathRecursive(v, nodes, idx + 1, f).getOrElse(v))
            }
            Some(Object(modified))
          case _ => None
        }

      case DynamicOptic.Node.AtIndices(indices) =>
        json match {
          case Array(elems) =>
            val indexSet = indices.toSet
            val modified = elems.zipWithIndex.map { case (elem, i) =>
              if (indexSet.contains(i)) modifyAtPathRecursive(elem, nodes, idx + 1, f).getOrElse(elem)
              else elem
            }
            Some(Array(modified))
          case _ => None
        }

      case DynamicOptic.Node.AtMapKey(key) =>
        val keyStr = key match {
          case DynamicValue.Primitive(pv: PrimitiveValue.String) => Some(pv.value)
          case _                                                 => None
        }
        keyStr.flatMap { k =>
          json match {
            case Object(fields) =>
              var found    = false
              var fieldIdx = 0
              val len      = fields.length
              while (fieldIdx < len && !found) {
                if (fields(fieldIdx)._1 == k) found = true
                else fieldIdx += 1
              }
              if (found) {
                modifyAtPathRecursive(fields(fieldIdx)._2, nodes, idx + 1, f).map { newValue =>
                  Object(fields.updated(fieldIdx, (k, newValue)))
                }
              } else None
            case _ => None
          }
        }

      case DynamicOptic.Node.AtMapKeys(keys) =>
        val keyStrs = keys.flatMap {
          case DynamicValue.Primitive(pv: PrimitiveValue.String) => Some(pv.value)
          case _                                                 => None
        }.toSet
        json match {
          case Object(fields) =>
            val modified = fields.map { case (k, v) =>
              if (keyStrs.contains(k)) (k, modifyAtPathRecursive(v, nodes, idx + 1, f).getOrElse(v))
              else (k, v)
            }
            Some(Object(modified))
          case _ => None
        }

      case DynamicOptic.Node.MapKeys =>
        // Cannot modify map keys in JSON (keys are strings, not values)
        None

      case DynamicOptic.Node.Case(_) =>
        // Case is for sum types, pass through for JSON
        modifyAtPathRecursive(json, nodes, idx + 1, f)

      case DynamicOptic.Node.Wrapped =>
        // Wrapped is for newtypes, pass through for JSON
        modifyAtPathRecursive(json, nodes, idx + 1, f)
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
      if (pf.isDefinedAt(json)) Right(pf(json))
      else Left(JsonError("Partial function not defined for value at path"))
    } else {
      modifyAtPathOrFailRecursive(json, nodes, 0, pf)
    }
  }

  private def modifyAtPathOrFailRecursive(
    json: Json,
    nodes: IndexedSeq[DynamicOptic.Node],
    idx: Int,
    pf: PartialFunction[Json, Json]
  ): Either[JsonError, Json] =
    if (idx >= nodes.length) {
      if (pf.isDefinedAt(json)) Right(pf(json))
      else Left(JsonError("Partial function not defined for value at path"))
    } else {
      val node = nodes(idx)
      node match {
        case DynamicOptic.Node.Field(name) =>
          json match {
            case Object(fields) =>
              var found    = false
              var fieldIdx = 0
              val len      = fields.length
              while (fieldIdx < len && !found) {
                if (fields(fieldIdx)._1 == name) found = true
                else fieldIdx += 1
              }
              if (found) {
                modifyAtPathOrFailRecursive(fields(fieldIdx)._2, nodes, idx + 1, pf).map { newValue =>
                  Object(fields.updated(fieldIdx, (name, newValue)))
                }
              } else Left(JsonError(s"Field '$name' not found").atField(name))
            case _ => Left(JsonError(s"Cannot access field '$name' on non-object").atField(name))
          }

        case DynamicOptic.Node.AtIndex(index) =>
          json match {
            case Array(elems) =>
              if (index >= 0 && index < elems.length) {
                modifyAtPathOrFailRecursive(elems(index), nodes, idx + 1, pf).map { newValue =>
                  Array(elems.updated(index, newValue))
                }
              } else Left(JsonError(s"Index $index out of bounds (size: ${elems.length})").atIndex(index))
            case _ => Left(JsonError(s"Cannot access index $index on non-array").atIndex(index))
          }

        case DynamicOptic.Node.Elements =>
          json match {
            case Array(elems) =>
              val results = elems.zipWithIndex.foldLeft[Either[JsonError, Vector[Json]]](Right(Vector.empty)) {
                case (Left(err), _)          => Left(err)
                case (Right(acc), (elem, _)) =>
                  modifyAtPathOrFailRecursive(elem, nodes, idx + 1, pf).map(acc :+ _)
              }
              results.map(Array(_))
            case _ => Left(JsonError("Cannot iterate elements on non-array"))
          }

        case DynamicOptic.Node.MapValues =>
          json match {
            case obj: Object =>
              val results =
                obj.value.foldLeft[Either[JsonError, Chunk[(scala.Predef.String, Json)]]](Right(Chunk.empty)) {
                  case (Left(err), _)       => Left(err)
                  case (Right(acc), (k, v)) =>
                    modifyAtPathOrFailRecursive(v, nodes, idx + 1, pf).map(newV => acc :+ (k, newV))
                }
              results.map(Object(_))
            case _ => Left(JsonError("Cannot iterate map values on non-object"))
          }

        case _ =>
          // For other node types, delegate to a non-failing version and wrap the result
          modifyAtPath(json, new DynamicOptic(nodes.drop(idx)), pf.lift.andThen(_.getOrElse(json))) match {
            case Some(result) => Right(result)
            case None         => Left(JsonError(s"Path not found: ${new DynamicOptic(nodes).toString}"))
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
    val node   = nodes(idx)
    val isLast = idx == nodes.length - 1

    node match {
      case DynamicOptic.Node.Field(name) =>
        json match {
          case Object(fields) =>
            if (isLast) {
              // Delete this field
              val filtered = fields.filterNot(_._1 == name)
              if (filtered.length != fields.length) Some(Object(filtered))
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
                  Object(fields.updated(fieldIdx, (name, newValue)))
                }
              } else None
            }
          case _ => None
        }

      case DynamicOptic.Node.AtIndex(index) =>
        json match {
          case Array(elems) =>
            if (isLast) {
              // Delete this element
              if (index >= 0 && index < elems.length) {
                val (before, after) = elems.splitAt(index)
                Some(Array(before ++ after.tail))
              } else None
            } else {
              // Navigate into the element and continue
              if (index >= 0 && index < elems.length) {
                deleteAtPathRecursive(elems(index), nodes, idx + 1).map { newValue =>
                  Array(elems.updated(index, newValue))
                }
              } else None
            }
          case _ => None
        }

      case DynamicOptic.Node.Elements =>
        if (isLast) {
          // Delete all elements
          json match {
            case Array(_) => Some(Array.empty)
            case _        => None
          }
        } else {
          // Apply delete to each element
          json match {
            case Array(elems) =>
              val modified = elems.flatMap(elem => deleteAtPathRecursive(elem, nodes, idx + 1))
              Some(Array(modified))
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
    val node   = nodes(idx)
    val isLast = idx == nodes.length - 1

    node match {
      case DynamicOptic.Node.Field(name) =>
        json match {
          case Object(fields) =>
            if (isLast) {
              // Insert this field (only if it doesn't exist)
              val exists = fields.exists(_._1 == name)
              if (!exists) Some(Object(fields :+ (name, value)))
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
                  Object(fields.updated(fieldIdx, (name, newValue)))
                }
              } else None
            }
          case _ => None
        }

      case DynamicOptic.Node.AtIndex(index) =>
        json match {
          case Array(elems) =>
            if (isLast) {
              // Insert at this index (shifts elements right)
              if (index >= 0 && index <= elems.length) {
                val (before, after) = elems.splitAt(index)
                Some(Array(before ++ Vector(value) ++ after))
              } else None
            } else {
              // Navigate into the element and continue
              if (index >= 0 && index < elems.length) {
                insertAtPathRecursive(elems(index), nodes, idx + 1, value).map { newValue =>
                  Array(elems.updated(index, newValue))
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
    val node   = nodes(idx)
    val isLast = idx == nodes.length - 1

    node match {
      case DynamicOptic.Node.Field(name) =>
        json match {
          case Object(fields) =>
            if (isLast) {
              val exists = fields.exists(_._1 == name)
              if (!exists) Right(Object(fields :+ (name, value)))
              else Left(JsonError(s"Field '$name' already exists").atField(name))
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
                  Object(fields.updated(fieldIdx, (name, newValue)))
                }
              } else Left(JsonError(s"Field '$name' not found").atField(name))
            }
          case _ => Left(JsonError(s"Cannot access field '$name' on non-object").atField(name))
        }

      case DynamicOptic.Node.AtIndex(index) =>
        json match {
          case Array(elems) =>
            if (isLast) {
              if (index >= 0 && index <= elems.length) {
                val (before, after) = elems.splitAt(index)
                Right(Array(before ++ Vector(value) ++ after))
              } else Left(JsonError(s"Index $index out of bounds for insert (size: ${elems.length})").atIndex(index))
            } else {
              if (index >= 0 && index < elems.length) {
                insertAtPathOrFailRecursive(elems(index), nodes, idx + 1, value).map { newValue =>
                  Array(elems.updated(index, newValue))
                }
              } else Left(JsonError(s"Index $index out of bounds (size: ${elems.length})").atIndex(index))
            }
          case _ => Left(JsonError(s"Cannot access index $index on non-array").atIndex(index))
        }

      case other =>
        Left(JsonError(s"Insert not supported for path node type: $other"))
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Ordering
  // ─────────────────────────────────────────────────────────────────────────

  implicit val ordering: Ordering[Json] = (x: Json, y: Json) => x.compare(y)

  // ─────────────────────────────────────────────────────────────────────────
  // JsonBinaryCodec for Json
  // ─────────────────────────────────────────────────────────────────────────

  val jsonCodec: JsonBinaryCodec[Json] = new JsonBinaryCodec[Json]() {
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
        val v = in.readRawValAsBytes()
        new Number(new java.lang.String(v, 0, v.length))
      } else if (b == '[') {
        if (in.isNextToken(']')) Array.empty
        else {
          in.rollbackToken()
          val builder = new VectorBuilder[Json]
          var idx     = 0
          try {
            while ({
              builder.addOne(decodeValue(in, default))
              idx += 1
              in.isNextToken(',')
            }) ()
          } catch {
            case error if NonFatal(error) => in.decodeError(new DynamicOptic.Node.AtIndex(idx), error)
          }
          if (in.isCurrentToken(']')) new Array(builder.result())
          else in.arrayEndOrCommaError()
        }
      } else if (b == '{') {
        if (in.isNextToken('}')) Object.empty
        else {
          in.rollbackToken()
          val builder               = ChunkBuilder.make[(scala.Predef.String, Json)]()
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
        in.readNullOrError(Null, "expected JSON value")
      }
    }

    override def encodeValue(x: Json, out: JsonWriter): Unit = x match {
      case String(v)  => out.writeVal(v)
      case Boolean(v) => out.writeVal(v)
      case Number(v)  => out.writeRawVal(v.getBytes)
      case Array(v)   =>
        out.writeArrayStart()
        val it = v.iterator
        while (it.hasNext) {
          encodeValue(it.next(), out)
        }
        out.writeArrayEnd()
      case Object(v) =>
        out.writeObjectStart()
        val it = v.iterator
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
