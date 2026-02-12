package zio.blocks.schema.json

import zio.blocks.chunk.{Chunk, ChunkBuilder}
import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue, Reflect, Schema, SchemaError}
import zio.blocks.typeid.TypeId
import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.schema.patch.PatchMode
import java.nio.ByteBuffer
import java.util
import scala.collection.mutable
import scala.util.control.NonFatal
import scala.util.hashing.MurmurHash3

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

  /** Pretty print Json value to string. */
  override def toString: String = print(WriterConfig.withIndentionStep2)

  // ─────────────────────────────────────────────────────────────────────────
  // Type Information
  // ─────────────────────────────────────────────────────────────────────────

  /** Returns the [[JsonType]] of this JSON value. */
  def jsonType: JsonType

  // ─────────────────────────────────────────────────────────────────────────
  // Unified Type Operations
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Returns true if this JSON value is of the specified type.
   *
   * @example
   *   {{{ json.is(JsonType.Object) // true if json is an object
   *   json.is(JsonType.String) // true if json is a string }}}
   */
  def is(jsonType: JsonType): Boolean = this.jsonType eq jsonType

  /**
   * Narrows this JSON value to the specified type, returning `Some` if the
   * types match or `None` otherwise. The return type is path-dependent on the
   * `jsonType` parameter.
   *
   * @example
   *   {{{ json.as(JsonType.Object) // Option[Json.Object]
   *   json.as(JsonType.String) // Option[Json.String] }}}
   */
  def as(jsonType: JsonType): Option[jsonType.Type]

  /**
   * Extracts the underlying value from this JSON if it matches the specified
   * type. The return type is path-dependent on the `jsonType` parameter.
   *
   * @example
   *   {{{ json.unwrap(JsonType.String) // Option[String]
   *   json.unwrap(JsonType.Number) // Option[BigDecimal]
   *   json.unwrap(JsonType.Object) // Option[Chunk[(String, Json)]] }}}
   */
  def unwrap(jsonType: JsonType): Option[jsonType.Unwrap]

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
    JsonSelection.fail(SchemaError(s"Cannot get field '$key' from non-object JSON value"))

  /**
   * Navigates to an element in an array by index. Returns a JsonSelection
   * containing the value, or an error if index is out of bounds.
   */
  def get(index: Int): JsonSelection =
    JsonSelection.fail(SchemaError(s"Cannot get index $index from non-array JSON value"))

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
  def modify(path: DynamicOptic)(f: Json => Json): Json = Json.modifyAtPath(this, path, f) match {
    case Some(json) => json
    case _          => this
  }

  /**
   * Modifies the value at the given path using a partial function. Returns Left
   * with an error if the path doesn't exist or the partial function is not
   * defined.
   */
  def modifyOrFail(path: DynamicOptic)(pf: PartialFunction[Json, Json]): Either[SchemaError, Json] =
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
  def setOrFail(path: DynamicOptic, value: Json): Either[SchemaError, Json] =
    Json.modifyAtPathOrFail(this, path, { case _ => value })

  /**
   * Deletes the value at the given path. If the path doesn't exist, returns the
   * original JSON unchanged.
   */
  def delete(path: DynamicOptic): Json = Json.deleteAtPath(this, path) match {
    case Some(json) => json
    case _          => this
  }

  /**
   * Deletes the value at the given path. Returns Left with an error if the path
   * doesn't exist.
   */
  def deleteOrFail(path: DynamicOptic): Either[SchemaError, Json] =
    Json.deleteAtPath(this, path) match {
      case Some(json) => new Right(json)
      case _          => new Left(SchemaError(s"Path not found: $path"))
    }

  /**
   * Inserts a value at the given path. For arrays, inserts at the specified
   * index. For objects, adds the field. If the path already exists, returns the
   * original JSON unchanged.
   */
  def insert(path: DynamicOptic, value: Json): Json = Json.insertAtPath(this, path, value) match {
    case Some(json) => json
    case _          => this
  }

  /**
   * Inserts a value at the given path. Returns Left with an error if the path
   * already exists or the parent doesn't exist.
   */
  def insertOrFail(path: DynamicOptic, value: Json): Either[SchemaError, Json] =
    Json.insertAtPathOrFail(this, path, value)

  // ─────────────────────────────────────────────────────────────────────────
  // Merging
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Merges this JSON value with another using the specified strategy.
   */
  def merge(that: Json, strategy: MergeStrategy = MergeStrategy.Auto): Json =
    Json.merge(DynamicOptic.root, this, that, strategy)

  // ─────────────────────────────────────────────────────────────────────────
  // Normalization
  // ─────────────────────────────────────────────────────────────────────────

  /** Recursively sorts all object keys alphabetically. */
  def sortKeys: Json = this

  /** Recursively removes all null values from objects. */
  def dropNulls: Json = this

  /** Recursively removes empty objects and arrays. */
  def dropEmpty: Json = this

  /**
   * Applies dropNulls, dropEmpty, and sortKeys, but more efficiently than just
   * their calls in a sequence.
   */
  def normalize: Json = this

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
  def printTo(buffer: ByteBuffer): Unit = Json.jsonCodec.encode(this, buffer)

  /** Prints this JSON value into the provided ByteBuffer with config. */
  def printTo(buffer: ByteBuffer, config: WriterConfig): Unit = Json.jsonCodec.encode(this, buffer, config)

  // ─────────────────────────────────────────────────────────────────────────
  // Conversion
  // ─────────────────────────────────────────────────────────────────────────

  /** Converts this JSON value to a DynamicValue. */
  def toDynamicValue: DynamicValue = Json.toDynamicValue(this)

  /**
   * Decodes this JSON value to a value of type A using the implicit
   * JsonDecoder.
   */
  def as[A](implicit decoder: JsonDecoder[A]): Either[SchemaError, A] = decoder.decode(this)

  /**
   * Decodes this JSON value to a value of type A, throwing SchemaError on
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
  def transformUp(f: (DynamicOptic, Json) => Json): Json = Json.transformUp(this, DynamicOptic.root, f)

  /**
   * Transforms this JSON value top-down using the given function. The function
   * receives the current path and the JSON value at that path. Parent values
   * are transformed before their children.
   */
  def transformDown(f: (DynamicOptic, Json) => Json): Json = Json.transformDown(this, DynamicOptic.root, f)

  /**
   * Transforms all object keys using the given function. The function receives
   * the current path and the key at that path.
   */
  def transformKeys(f: (DynamicOptic, String) => String): Json = Json.transformKeys(this, DynamicOptic.root, f)

  // ─────────────────────────────────────────────────────────────────────────
  // Selection Methods (flat, lift to JsonSelection)
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Wraps this JSON value in a [[JsonSelection]].
   *
   * @example
   *   {{{json.select // JsonSelection containing this value}}}
   */
  def select: JsonSelection = JsonSelection.succeed(this)

  /**
   * Wraps this JSON value in a [[JsonSelection]] if its type matches the
   * specified [[JsonType]], otherwise returns an empty selection.
   *
   * This is a flat (non-recursive) type check on the current node only.
   *
   * @example
   *   {{{json.select(JsonType.Object) // selection if json is object, else empty}}}
   */
  def select(jsonType: JsonType): JsonSelection =
    if (this.jsonType eq jsonType) JsonSelection.succeed(this)
    else JsonSelection.empty

  // ─────────────────────────────────────────────────────────────────────────
  // Pruning Methods (remove matching nodes)
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Recursively removes elements/fields for which the predicate returns true.
   *
   * @example
   *   {{{json.prune(_.is(JsonType.Null)) // removes all null values}}}
   */
  def prune(p: Json => Boolean): Json = Json.prune(this, DynamicOptic.root, (_, json) => p(json))

  /**
   * Recursively removes elements/fields at paths for which the predicate
   * returns true.
   */
  def prunePath(p: DynamicOptic => Boolean): Json = Json.prune(this, DynamicOptic.root, (path, _) => p(path))

  /**
   * Recursively removes elements/fields for which the predicate on both path
   * and value returns true.
   */
  def pruneBoth(p: (DynamicOptic, Json) => Boolean): Json = Json.prune(this, DynamicOptic.root, p)

  // ─────────────────────────────────────────────────────────────────────────
  // Retention Methods (keep only matching nodes)
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Recursively keeps only elements/fields for which the predicate returns
   * true.
   *
   * @example
   *   {{{json.retain(_.is(JsonType.Number)) // keeps only numbers}}}
   */
  def retain(p: Json => Boolean): Json = Json.retain(this, DynamicOptic.root, (_, json) => p(json))

  /**
   * Recursively keeps only elements/fields at paths for which the predicate
   * returns true.
   */
  def retainPath(p: DynamicOptic => Boolean): Json = Json.retain(this, DynamicOptic.root, (path, _) => p(path))

  /**
   * Recursively keeps only elements/fields for which the predicate on both path
   * and value returns true.
   */
  def retainBoth(p: (DynamicOptic, Json) => Boolean): Json = Json.retain(this, DynamicOptic.root, p)

  /**
   * Projects only the specified paths from this JSON value. Creates a new JSON
   * containing only values at the given paths.
   */
  def project(paths: DynamicOptic*): Json = Json.project(this, paths)

  /**
   * Partitions elements/fields based on a predicate on the value. Returns a
   * tuple of (matching, non-matching) JSON values.
   *
   * @example
   *   {{{json.partition(_.is(JsonType.Number)) // (numbers, non-numbers)}}}
   */
  def partition(p: Json => Boolean): (Json, Json) = Json.partition(this, DynamicOptic.root, (_, json) => p(json))

  /**
   * Partitions elements/fields based on a predicate on the path. Returns a
   * tuple of (matching, non-matching) JSON values.
   */
  def partitionPath(p: DynamicOptic => Boolean): (Json, Json) =
    Json.partition(this, DynamicOptic.root, (path, _) => p(path))

  /**
   * Partitions elements/fields based on a predicate on both path and value.
   * Returns a tuple of (matching, non-matching) JSON values.
   */
  def partitionBoth(p: (DynamicOptic, Json) => Boolean): (Json, Json) = Json.partition(this, DynamicOptic.root, p)

  // ─────────────────────────────────────────────────────────────────────────
  // Folding Methods
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Folds over the JSON structure bottom-up. The function receives the current
   * path, the JSON value, and the accumulator. Child values are folded before
   * their parents.
   */
  def foldUp[B](z: B)(f: (DynamicOptic, Json, B) => B): B = Json.foldUp(this, DynamicOptic.root, z, f)

  /**
   * Folds over the JSON structure top-down. The function receives the current
   * path, the JSON value, and the accumulator. Parent values are folded before
   * their children.
   */
  def foldDown[B](z: B)(f: (DynamicOptic, Json, B) => B): B = Json.foldDown(this, DynamicOptic.root, z, f)

  /**
   * Folds over the JSON structure bottom-up, allowing failure.
   */
  def foldUpOrFail[B](z: B)(f: (DynamicOptic, Json, B) => Either[SchemaError, B]): Either[SchemaError, B] =
    Json.foldUpOrFail(this, DynamicOptic.root, z, f)

  /**
   * Folds over the JSON structure top-down, allowing failure.
   */
  def foldDownOrFail[B](z: B)(f: (DynamicOptic, Json, B) => Either[SchemaError, B]): Either[SchemaError, B] =
    Json.foldDownOrFail(this, DynamicOptic.root, z, f)

  /**
   * Converts this JSON to a Chunk of path-value pairs. Each pair contains the
   * path to a leaf value and the value itself.
   */
  def toKV: Chunk[(DynamicOptic, Json)] = Json.toKV(this, DynamicOptic.root)

  // ─────────────────────────────────────────────────────────────────────────
  // Patch Methods
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Computes the difference between this JSON and another.
   *
   * @param that
   *   The target JSON value
   * @return
   *   A JsonPatch that transforms this to that
   */
  def diff(that: Json): JsonPatch = JsonPatch.diff(this, that)

  /**
   * Applies a JSON patch to this value.
   *
   * @param patch
   *   The patch to apply
   * @param mode
   *   The patch mode controlling failure handling (default: Strict)
   * @return
   *   Either an error or the patched value
   */
  def patch(patch: JsonPatch, mode: PatchMode = PatchMode.Strict): Either[SchemaError, Json] = patch.apply(this, mode)

  /** Checks if this JSON conforms to a JSON Schema. */
  def check(schema: JsonSchema): Option[SchemaError] = schema.check(this)

  /** Returns true if this JSON conforms to a JSON Schema. */
  def conforms(schema: JsonSchema): scala.Boolean = schema.conforms(this)
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
    override def jsonType: JsonType = JsonType.Object

    override def as(jsonType: JsonType): Option[jsonType.Type] =
      if (jsonType eq JsonType.Object) new Some(this.asInstanceOf[jsonType.Type])
      else None

    override def unwrap(jsonType: JsonType): Option[jsonType.Unwrap] =
      if (jsonType eq JsonType.Object) new Some(value.asInstanceOf[jsonType.Unwrap])
      else None

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
      JsonSelection.fail(SchemaError(s"Key '$key' not found"))
    }

    override def sortKeys: Json = {
      val len = value.length
      if (len == 0) return this
      val arr = new scala.Array[(java.lang.String, Json)](len)
      var idx = 0
      while (idx < len) {
        val kv = value(idx)
        val v1 = kv._2
        val v2 = v1.sortKeys
        arr(idx) =
          if (v1 eq v2) kv
          else (kv._1, v2)
        idx += 1
      }
      util.Arrays.sort(arr, 0, arr.length, Object.ordering)
      new Object(Chunk.fromArray(arr))
    }

    override def dropNulls: Json = {
      val len = value.length
      if (len == 0) return this
      var arr       = new scala.Array[(java.lang.String, Json)](len)
      var size, idx = 0
      while (idx < len) {
        val kv = value(idx)
        val v1 = kv._2
        if (v1 ne Json.Null) {
          val v2 = v1.dropNulls
          arr(size) =
            if (v1 eq v2) kv
            else (kv._1, v2)
          size += 1
        }
        idx += 1
      }
      if (arr.length != size) arr = util.Arrays.copyOf(arr, size)
      new Object(Chunk.fromArray(arr))
    }

    override def dropEmpty: Json = {
      val len = value.length
      if (len == 0) return this
      var arr       = new scala.Array[(java.lang.String, Json)](len)
      var size, idx = 0
      while (idx < len) {
        val kv = value(idx)
        val v1 = kv._2
        val v2 = v1.dropEmpty
        if (
          v2 match {
            case obj: Object => obj.value.nonEmpty
            case arr: Array  => arr.value.nonEmpty
            case _           => true
          }
        ) {
          arr(size) =
            if (v1 eq v2) kv
            else (kv._1, v2)
          size += 1
        }
        idx += 1
      }
      if (arr.length != size) arr = util.Arrays.copyOf(arr, size)
      new Object(Chunk.fromArray(arr))
    }

    override def normalize: Json = {
      val len = value.length
      if (len == 0) return this
      var arr       = new scala.Array[(java.lang.String, Json)](len)
      var size, idx = 0
      while (idx < len) {
        val kv = value(idx)
        val v1 = kv._2
        val v2 = v1.normalize
        if (
          v2 match {
            case obj: Object  => obj.value.nonEmpty
            case arr: Array   => arr.value.nonEmpty
            case _: Null.type => false
            case _            => true
          }
        ) {
          arr(size) =
            if (v1 eq v2) kv
            else (kv._1, v2)
          size += 1
        }
        idx += 1
      }
      if (arr.length != size) arr = util.Arrays.copyOf(arr, size)
      util.Arrays.sort(arr, 0, arr.length, Object.ordering)
      new Object(Chunk.fromArray(arr))
    }

    override def compare(that: Json): Int = that match {
      case thatObj: Object =>
        // Compare as sorted key-value pairs for order-independent comparison
        val thisFields = value.sorted(Object.ordering)
        val thatFields = thatObj.value.sorted(Object.ordering)
        val minLen     = Math.min(thisFields.length, thatFields.length)
        var idx        = 0
        while (idx < minLen) {
          val kv1 = thisFields(idx)
          val kv2 = thatFields(idx)
          var cmp = kv1._1.compareTo(kv2._1)
          if (cmp != 0) return cmp
          cmp = kv1._2.compare(kv2._2)
          if (cmp != 0) return cmp
          idx += 1
        }
        thisFields.length.compareTo(thatFields.length)
      case _ => typeIndex - that.typeIndex
    }

    override def equals(obj: Any): scala.Boolean = obj match {
      case thatObj: Object =>
        if (value.length != thatObj.value.length) return false
        value.sorted(Object.ordering) == thatObj.value.sorted(Object.ordering) // Order-independent equality
      case _ => false
    }

    override def hashCode(): Int = MurmurHash3.unorderedHash(value)
  }

  object Object {
    val empty: Object = Object(Chunk.empty)

    def apply(fields: (java.lang.String, Json)*): Object = new Object(Chunk.from(fields))

    private val ordering: Ordering[(java.lang.String, Json)] = new Ordering[(java.lang.String, Json)] {
      override def compare(x: (java.lang.String, Json), y: (java.lang.String, Json)): Int = x._1.compareTo(y._1)
    }
  }

  /**
   * Represents a JSON array.
   */
  final case class Array(value: Chunk[Json]) extends Json {
    override def jsonType: JsonType = JsonType.Array

    override def as(jsonType: JsonType): Option[jsonType.Type] =
      if (jsonType eq JsonType.Array) new Some(this.asInstanceOf[jsonType.Type])
      else None

    override def unwrap(jsonType: JsonType): Option[jsonType.Unwrap] =
      if (jsonType eq JsonType.Array) new Some(value.asInstanceOf[jsonType.Unwrap])
      else None

    override def elements: Chunk[Json] = value

    override def typeIndex: Int = 4

    override def get(index: Int): JsonSelection =
      if (index >= 0 && index < value.length) JsonSelection.succeed(value(index))
      else JsonSelection.fail(SchemaError(s"Index $index out of bounds (size: ${value.length})").atIndex(index))

    override def sortKeys: Json = {
      val len = value.length
      if (len == 0) return this
      val arr = new scala.Array[Json](len)
      var idx = 0
      while (idx < len) {
        arr(idx) = value(idx).sortKeys
        idx += 1
      }
      new Array(Chunk.fromArray(arr))
    }

    override def dropNulls: Json = {
      val len = value.length
      if (len == 0) return this
      var arr       = new scala.Array[Json](len)
      var size, idx = 0
      while (idx < len) {
        val v = value(idx).dropNulls
        if (v ne Json.Null) {
          arr(size) = v
          size += 1
        }
        idx += 1
      }
      if (arr.length != size) arr = util.Arrays.copyOf(arr, size)
      new Array(Chunk.fromArray(arr))
    }

    override def dropEmpty: Json = {
      val len = value.length
      if (len == 0) return this
      var arr       = new scala.Array[Json](len)
      var size, idx = 0
      while (idx < len) {
        val v = value(idx).dropEmpty
        if (
          v match {
            case obj: Object => obj.value.nonEmpty
            case arr: Array  => arr.value.nonEmpty
            case _           => true
          }
        ) {
          arr(size) = v
          size += 1
        }
        idx += 1
      }
      if (arr.length != size) arr = util.Arrays.copyOf(arr, size)
      new Array(Chunk.fromArray(arr))
    }

    override def normalize: Json = {
      val len = value.length
      if (len == 0) return this
      var arr       = new scala.Array[Json](len)
      var size, idx = 0
      while (idx < len) {
        val v = value(idx).normalize
        if (
          v match {
            case obj: Object  => obj.value.nonEmpty
            case arr: Array   => arr.value.nonEmpty
            case _: Null.type => false
            case _            => true
          }
        ) {
          arr(size) = v
          size += 1
        }
        idx += 1
      }
      if (arr.length != size) arr = util.Arrays.copyOf(arr, size)
      new Array(Chunk.fromArray(arr))
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
    override def jsonType: JsonType = JsonType.String

    override def as(jsonType: JsonType): Option[jsonType.Type] =
      if (jsonType eq JsonType.String) new Some(this.asInstanceOf[jsonType.Type])
      else None

    override def unwrap(jsonType: JsonType): Option[jsonType.Unwrap] =
      if (jsonType eq JsonType.String) new Some(value.asInstanceOf[jsonType.Unwrap])
      else None

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
  final case class Number(value: BigDecimal) extends Json {
    override def jsonType: JsonType = JsonType.Number

    override def as(jsonType: JsonType): Option[jsonType.Type] =
      if (jsonType eq JsonType.Number) new Some(this.asInstanceOf[jsonType.Type])
      else None

    override def unwrap(jsonType: JsonType): Option[jsonType.Unwrap] =
      if (jsonType eq JsonType.Number) new Some(value).asInstanceOf[Option[jsonType.Unwrap]]
      else None

    override def typeIndex: Int = 2

    override def compare(that: Json): Int = that match {
      case thatNum: Number => value.compare(thatNum.value)
      case _               => typeIndex - that.typeIndex
    }
  }

  object Number {

    /** Creates a JSON number from an Int. */
    def apply(value: Int): Number = new Number(BigDecimal(value))

    /** Creates a JSON number from a Long. */
    def apply(value: Long): Number = new Number(BigDecimal(value))

    /**
     * Creates a JSON number from a Float.
     *
     * @throws java.lang.IllegalArgumentException
     *   in cases of NaN of infinity values
     */
    def apply(value: Float): Number = new Number(JsonWriter.toBigDecimal(value))

    /**
     * Creates a JSON number from a Double.
     *
     * @throws java.lang.IllegalArgumentException
     *   in cases of NaN of infinity values
     */
    def apply(value: Double): Number = new Number(JsonWriter.toBigDecimal(value))

    /** Creates a JSON number from a BigDecimal. */
    def apply(value: BigDecimal): Number = new Number(value)

    /** Creates a JSON number from a BigInt. */
    def apply(value: BigInt): Number =
      if (value.isValidLong) apply(value.toLong)
      else new Number(BigDecimal(value))

    /** Creates a JSON number from a Byte. */
    def apply(value: Byte): Number = new Number(BigDecimal(value))

    /** Creates a JSON number from a Short. */
    def apply(value: Short): Number = new Number(BigDecimal(value))
  }

  /**
   * Represents a JSON boolean.
   */
  final case class Boolean(value: scala.Boolean) extends Json {
    override def jsonType: JsonType = JsonType.Boolean

    override def as(jsonType: JsonType): Option[jsonType.Type] =
      if (jsonType eq JsonType.Boolean) new Some(this.asInstanceOf[jsonType.Type])
      else None

    override def unwrap(jsonType: JsonType): Option[jsonType.Unwrap] =
      if (jsonType eq JsonType.Boolean) new Some(value.asInstanceOf[jsonType.Unwrap])
      else None

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
    override def jsonType: JsonType = JsonType.Null

    override def as(jsonType: JsonType): Option[jsonType.Type] =
      if (jsonType eq JsonType.Null) new Some(this.asInstanceOf[jsonType.Type])
      else None

    override def unwrap(jsonType: JsonType): Option[jsonType.Unwrap] =
      if (jsonType eq JsonType.Null) new Some(().asInstanceOf[jsonType.Unwrap])
      else None

    override def typeIndex: Int = 0

    override def compare(that: Json): Int = -that.typeIndex
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
  def parse(input: java.lang.String): Either[SchemaError, Json] = jsonCodec.decode(input)

  /** Parses a JSON byte array into a Json value. */
  def parse(input: scala.Array[Byte]): Either[SchemaError, Json] = jsonCodec.decode(input)

  /** Parses a JSON byte array with config. */
  def parse(input: scala.Array[Byte], config: ReaderConfig): Either[SchemaError, Json] = jsonCodec.decode(input, config)

  /** Parses a JSON string with config. */
  def parse(input: java.lang.String, config: ReaderConfig): Either[SchemaError, Json] = jsonCodec.decode(input, config)

  /** Parses a JSON CharSequence into a Json value. */
  def parse(input: CharSequence): Either[SchemaError, Json] = parse(input.toString)

  /** Parses a JSON CharSequence with config. */
  def parse(input: CharSequence, config: ReaderConfig): Either[SchemaError, Json] = parse(input.toString, config)

  /** Parses a JSON ByteBuffer into a Json value. */
  def parse(input: ByteBuffer): Either[SchemaError, Json] = jsonCodec.decode(input)

  /** Parses a JSON ByteBuffer with config. */
  def parse(input: ByteBuffer, config: ReaderConfig): Either[SchemaError, Json] = jsonCodec.decode(input, config)

  /** Parses a JSON Chunk of bytes (UTF-8) into a Json value. */
  def parse(input: Chunk[Byte]): Either[SchemaError, Json] = parse(input.toArray)

  /** Parses a JSON Chunk of bytes (UTF-8) with config. */
  def parse(input: Chunk[Byte], config: ReaderConfig): Either[SchemaError, Json] = parse(input.toArray, config)

  /** Parses a JSON string, throwing SchemaError on failure. */
  def parseUnsafe(input: java.lang.String): Json = parse(input).fold(e => throw e, identity)

  // ─────────────────────────────────────────────────────────────────────────
  // Conversion from DynamicValue
  // ─────────────────────────────────────────────────────────────────────────

  /** Converts a DynamicValue to a Json value. */
  def fromDynamicValue(dv: DynamicValue): Json = dv match {
    case v: DynamicValue.Primitive => fromPrimitiveValue(v.value)
    case v: DynamicValue.Record    => new Object(v.fields.map(kv => (kv._1, fromDynamicValue(kv._2))))
    case v: DynamicValue.Variant   => new Object(Chunk.single((v.caseNameValue, fromDynamicValue(v.value))))
    case v: DynamicValue.Sequence  => new Array(v.elements.map(fromDynamicValue))
    case v: DynamicValue.Map       =>
      val entries = v.entries
      // For maps with string keys, convert to object; otherwise use array of pairs
      val allStringKeys = entries.forall {
        case (DynamicValue.Primitive(_: PrimitiveValue.String), _) => true
        case _                                                     => false
      }
      if (allStringKeys) {
        new Object(entries.collect { case (DynamicValue.Primitive(k: PrimitiveValue.String), v) =>
          (k.value, fromDynamicValue(v))
        })
      } else {
        new Array(entries.map { kv =>
          new Object(Chunk(("key", fromDynamicValue(kv._1)), ("value", fromDynamicValue(kv._2))))
        })
      }
    case _: DynamicValue.Null.type => Null
  }

  private[this] def fromPrimitiveValue(pv: PrimitiveValue): Json = pv match {
    case v: PrimitiveValue.String         => new String(v.value)
    case v: PrimitiveValue.Int            => Number(v.value)
    case v: PrimitiveValue.Long           => Number(v.value)
    case v: PrimitiveValue.Double         => Number(v.value)
    case v: PrimitiveValue.Float          => Number(v.value)
    case v: PrimitiveValue.Boolean        => Boolean(v.value)
    case v: PrimitiveValue.Byte           => Number(v.value)
    case v: PrimitiveValue.Short          => Number(v.value)
    case v: PrimitiveValue.Char           => new String(v.value.toString)
    case v: PrimitiveValue.BigInt         => Number(v.value)
    case v: PrimitiveValue.BigDecimal     => Number(v.value)
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
    case _: PrimitiveValue.Unit.type      => Object.empty
  }

  private def toDynamicValue(json: Json): DynamicValue = json match {
    case str: String   => new DynamicValue.Primitive(new PrimitiveValue.String(str.value))
    case bool: Boolean => new DynamicValue.Primitive(new PrimitiveValue.Boolean(bool.value))
    case num: Number   =>
      new DynamicValue.Primitive({
        // Try to preserve Int/Long if possible
        val bd        = num.value
        val longValue = bd.bigDecimal.longValue
        if (bd == BigDecimal(longValue)) {
          val intValue = longValue.toInt
          if (longValue == intValue) new PrimitiveValue.Int(intValue)
          else new PrimitiveValue.Long(longValue)
        } else new PrimitiveValue.BigDecimal(bd)
      })
    case arr: Array   => new DynamicValue.Sequence(arr.value.map(toDynamicValue))
    case obj: Object  => new DynamicValue.Record(obj.value.map(kv => (kv._1, toDynamicValue(kv._2))))
    case _: Null.type => DynamicValue.Null
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Merging
  // ─────────────────────────────────────────────────────────────────────────

  /** Merges two JSON values using the specified strategy. */
  private def merge(path: DynamicOptic, left: Json, right: Json, s: MergeStrategy): Json =
    (left, right) match {
      case (lo: Object, ro: Object) if s.recurse(path, JsonType.Object) => mergeByKey(path, lo, ro, s)
      case (la: Array, ra: Array) if s.recurse(path, JsonType.Array)    => mergeByIndex(path, la, ra, s)
      case _                                                            => s(path, left, right)
    }

  private[this] def mergeByKey(path: DynamicOptic, left: Object, right: Object, s: MergeStrategy): Object = {
    val leftFields  = left.value
    val rightFields = right.value
    val leftMap     = leftFields.toMap
    val rightMap    = rightFields.toMap
    val allKeys     = mutable.ArrayBuilder.make[java.lang.String]
    allKeys.sizeHint(leftFields.length + rightFields.length)
    leftFields.foreach(kv => allKeys.addOne(kv._1))
    rightFields.foreach { kv =>
      val key = kv._1
      if (!leftMap.contains(key)) allKeys.addOne(key)
    }
    new Object(Chunk.fromArray(allKeys.result().map { key =>
      val rvOpt = rightMap.get(key)
      (
        key,
        leftMap.get(key) match {
          case Some(lv) =>
            rvOpt match {
              case Some(rv) => merge(path.field(key), lv, rv, s)
              case _        => lv
            }
          case _ =>
            rvOpt match {
              case Some(rv) => rv
              case _        => throw new IllegalStateException("Key should exist in at least one map")
            }
        }
      )
    }))
  }

  private[this] def mergeByIndex(path: DynamicOptic, left: Array, right: Array, s: MergeStrategy): Array = {
    val maxLen = Math.max(left.value.length, right.value.length)
    val arr    = new scala.Array[Json](maxLen)
    var idx    = 0
    while (idx < maxLen) {
      val rvOpt = right.value.lift(idx)
      arr(idx) = left.value.lift(idx) match {
        case Some(lv) =>
          rvOpt match {
            case Some(rv) => merge(path.at(idx), lv, rv, s)
            case _        => lv
          }
        case _ =>
          rvOpt match {
            case Some(rv) => rv
            case _        => throw new IllegalStateException("Index should exist in at least one array")
          }
      }
      idx += 1
    }
    new Array(Chunk.fromArray(arr))
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Transformation Implementations
  // ─────────────────────────────────────────────────────────────────────────

  private def transformUp(json: Json, path: DynamicOptic, f: (DynamicOptic, Json) => Json): Json =
    f(
      path,
      json match {
        case obj: Object => new Object(obj.value.map { case (k, v) => (k, transformUp(v, path.field(k), f)) })
        case arr: Array  =>
          new Array(arr.value.map {
            var idx = -1
            e =>
              idx += 1
              transformUp(e, path.at(idx), f)
          })
        case other => other
      }
    )

  private def transformDown(json: Json, path: DynamicOptic, f: (DynamicOptic, Json) => Json): Json =
    f(path, json) match {
      case obj: Object => new Object(obj.value.map { case (k, v) => (k, transformDown(v, path.field(k), f)) })
      case arr: Array  =>
        new Array(arr.value.map {
          var idx = -1
          e =>
            idx += 1
            transformDown(e, path.at(idx), f)
        })
      case other => other
    }

  private def transformKeys(
    json: Json,
    path: DynamicOptic,
    f: (DynamicOptic, java.lang.String) => java.lang.String
  ): Json =
    json match {
      case obj: Object =>
        new Object(obj.value.map { case (k, v) =>
          val newKey = f(path.field(k), k)
          (newKey, transformKeys(v, path.field(newKey), f))
        })
      case arr: Array =>
        new Array(arr.value.map {
          var idx = -1
          e =>
            idx += 1
            transformKeys(e, path.at(idx), f)
        })
      case other => other
    }

  // ─────────────────────────────────────────────────────────────────────────
  // Prune/Retain Implementations
  // ─────────────────────────────────────────────────────────────────────────

  private def prune(json: Json, path: DynamicOptic, p: (DynamicOptic, Json) => scala.Boolean): Json =
    json match {
      case obj: Object =>
        val fields = ChunkBuilder.make[(java.lang.String, Json)]()
        obj.value.foreach { kv =>
          val newPath = path.field(kv._1)
          if (!p(newPath, kv._2)) fields.addOne((kv._1, prune(kv._2, newPath, p)))
        }
        new Object(fields.result())
      case arr: Array =>
        val values = ChunkBuilder.make[Json]()
        arr.value.foreach {
          var idx = -1
          e =>
            idx += 1
            val newPath = path.at(idx)
            if (!p(newPath, e)) values.addOne(prune(e, newPath, p))
        }
        new Array(values.result())
      case other => other
    }

  private def retain(json: Json, path: DynamicOptic, p: (DynamicOptic, Json) => scala.Boolean): Json =
    json match {
      case obj: Object =>
        val fields = ChunkBuilder.make[(java.lang.String, Json)]()
        obj.value.foreach { kv =>
          val newPath = path.field(kv._1)
          if (p(newPath, kv._2)) fields.addOne((kv._1, retain(kv._2, newPath, p)))
        }
        new Object(fields.result())
      case arr: Array =>
        val values = ChunkBuilder.make[Json]()
        arr.value.foreach {
          var idx = -1
          e =>
            idx += 1
            val newPath = path.at(idx)
            if (p(newPath, e)) values.addOne(retain(e, newPath, p))
        }
        new Array(values.result())
      case other => other
    }

  private def project(json: Json, paths: Seq[DynamicOptic]): Json = {
    if (paths.isEmpty) return Null
    // For each path, get the value and build a sparse result
    fromKVUnsafe(paths.flatMap(p => json.get(p).toChunk.map(v => (p, v))))
  }

  private def partition(json: Json, path: DynamicOptic, p: (DynamicOptic, Json) => scala.Boolean): (Json, Json) =
    json match {
      case obj: Object =>
        val initCapacity = obj.value.length >> 1
        val matching     = ChunkBuilder.make[(java.lang.String, Json)](initCapacity)
        val nonMatching  = ChunkBuilder.make[(java.lang.String, Json)](initCapacity)
        obj.value.foreach { case (k, v) =>
          val newPath = path.field(k)
          val result  = partition(v, newPath, p)
          if (p(newPath, v)) matching.addOne((k, result._1))
          else nonMatching.addOne((k, result._2))
        }
        (new Object(matching.result()), new Object(nonMatching.result()))
      case arr: Array =>
        val initCapacity = arr.value.length >> 1
        val matching     = ChunkBuilder.make[Json](initCapacity)
        val nonMatching  = ChunkBuilder.make[Json](initCapacity)
        arr.value.foreach {
          var idx = -1
          elem =>
            idx += 1
            val newPath = path.at(idx)
            val result  = partition(elem, newPath, p)
            if (p(newPath, elem)) matching.addOne(result._1)
            else nonMatching.addOne(result._2)
        }
        (new Array(matching.result()), new Array(nonMatching.result()))
      case other =>
        if (p(path, other)) (other, Null)
        else (Null, other)
    }

  // ─────────────────────────────────────────────────────────────────────────
  // Fold Implementations
  // ─────────────────────────────────────────────────────────────────────────

  private def foldUp[B](json: Json, path: DynamicOptic, z: B, f: (DynamicOptic, Json, B) => B): B =
    f(
      path,
      json,
      json match {
        case obj: Object => obj.value.foldLeft(z)((acc, kv) => foldUp(kv._2, path.field(kv._1), acc, f))
        case arr: Array  =>
          arr.value.foldLeft(z) {
            var idx = -1
            (acc, e) =>
              idx += 1
              foldUp(e, path.at(idx), acc, f)
          }
        case _ => z
      }
    )

  private def foldDown[B](json: Json, path: DynamicOptic, z: B, f: (DynamicOptic, Json, B) => B): B = {
    val afterThis = f(path, json, z)
    json match {
      case obj: Object => obj.value.foldLeft(afterThis)((acc, kv) => foldDown(kv._2, path.field(kv._1), acc, f))
      case arr: Array  =>
        arr.value.foldLeft(afterThis) {
          var idx = -1
          (acc, elem) =>
            idx += 1
            foldDown(elem, path.at(idx), acc, f)
        }
      case _ => afterThis
    }
  }

  private def foldUpOrFail[B](
    json: Json,
    path: DynamicOptic,
    z: B,
    f: (DynamicOptic, Json, B) => Either[SchemaError, B]
  ): Either[SchemaError, B] =
    f(
      path,
      json,
      json match {
        case obj: Object =>
          var b      = z
          val fields = obj.value
          val len    = fields.length
          var idx    = 0
          while (idx < len) {
            val kv = fields(idx)
            foldUpOrFail(kv._2, path.field(kv._1), b, f) match {
              case Right(b1) => b = b1
              case l         => return l
            }
            idx += 1
          }
          b
        case arr: Array =>
          var b     = z
          val elems = arr.value
          val len   = elems.length
          var idx   = 0
          while (idx < len) {
            val elem = elems(idx)
            foldUpOrFail(elem, path.at(idx), b, f) match {
              case Right(b1) => b = b1
              case l         => return l
            }
            idx += 1
          }
          b
        case _ => z
      }
    )

  private def foldDownOrFail[B](
    json: Json,
    path: DynamicOptic,
    z: B,
    f: (DynamicOptic, Json, B) => Either[SchemaError, B]
  ): Either[SchemaError, B] =
    f(path, json, z) match {
      case Right(afterThis) =>
        new Right(json match {
          case obj: Object =>
            var b      = afterThis
            val fields = obj.value
            val len    = fields.length
            var idx    = 0
            while (idx < len) {
              val kv = fields(idx)
              foldDownOrFail(kv._2, path.field(kv._1), b, f) match {
                case Right(b1) => b = b1
                case l         => return l
              }
              idx += 1
            }
            b
          case arr: Array =>
            var b     = afterThis
            val elems = arr.value
            val len   = elems.length
            var idx   = 0
            while (idx < len) {
              val elem = elems(idx)
              foldDownOrFail(elem, path.at(idx), b, f) match {
                case Right(b1) => b = b1
                case l         => return l
              }
              idx += 1
            }
            b
          case _ => afterThis
        })
      case l => l
    }

  // ─────────────────────────────────────────────────────────────────────────
  // Query Implementations
  // ─────────────────────────────────────────────────────────────────────────

  private[json] def query(json: Json, path: DynamicOptic, p: (DynamicOptic, Json) => scala.Boolean): JsonSelection = {
    val results = ChunkBuilder.make[Json]()

    def collect(j: Json, currentPath: DynamicOptic): Unit = {
      if (p(currentPath, j)) results.addOne(j)
      j match {
        case obj: Object => obj.value.foreach(kv => collect(kv._2, currentPath.field(kv._1)))
        case arr: Array  =>
          arr.value.foreach {
            var idx = -1
            elem =>
              idx += 1
              collect(elem, currentPath.at(idx))
          }
        case _ => ()
      }
    }

    collect(json, path)
    JsonSelection.succeedMany(results.result())
  }

  private def toKV(json: Json, path: DynamicOptic): Chunk[(DynamicOptic, Json)] =
    json match {
      case obj: Object =>
        val fields = obj.value
        if (fields.isEmpty) Chunk.single((path, obj))
        else fields.flatMap(kv => toKV(kv._2, path.field(kv._1)))
      case arr: Array =>
        val elems = arr.value
        if (elems.isEmpty) Chunk.single((path, arr))
        else
          elems.flatMap {
            var idx = -1
            elem =>
              idx += 1
              toKV(elem, path.at(idx))
          }
      case leaf => Chunk.single((path, leaf))
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
  def fromKV(kvs: Seq[(DynamicOptic, Json)]): Either[SchemaError, Json] = {
    if (kvs.isEmpty) return new Right(Null)
    try new Right(fromKVUnsafe(kvs))
    catch {
      case e: Exception => new Left(SchemaError(s"Failed to construct JSON from KV pairs: ${e.getMessage}"))
    }
  }

  /**
   * Reconstructs a JSON value from path-value pairs. Throws on inconsistent
   * paths.
   */
  def fromKVUnsafe(kvs: Seq[(DynamicOptic, Json)]): Json = {
    val len = kvs.length
    if (len == 0) return Null
    if (len == 1 && kvs.head._1.nodes.isEmpty) return kvs.head._2 // Simple case: single root value
    kvs.foldLeft[Json](Null)((acc, pv) => setOrCreatePath(acc, pv._1, pv._2))
  }

  private[this] def setOrCreatePath(json: Json, path: DynamicOptic, value: Json): Json = {
    val nodes = path.nodes
    if (nodes.isEmpty) return value

    def inferContainer(node: DynamicOptic.Node): Json = node match {
      case _: DynamicOptic.Node.Field   => Object.empty
      case _: DynamicOptic.Node.AtIndex => Array.empty
      case _                            => Null
    }

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
                new Object(obj.value.appended((name, newValue)))
              }
            case _ =>
              val newValue = go(if (idx + 1 < nodes.length) inferContainer(nodes(idx + 1)) else Null, idx + 1)
              new Object(Chunk.single((name, newValue)))
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

  // ─────────────────────────────────────────────────────────────────────────
  // DynamicOptic-based Path Operations
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Gets the value(s) at the given path in the JSON structure.
   */
  private def getAtPath(json: Json, path: DynamicOptic): JsonSelection = {
    val nodes = path.nodes
    val len   = nodes.length
    if (len == 0) return JsonSelection.succeed(json)
    var jsons = Chunk.single(json)
    var idx   = 0
    while (idx < len) {
      jsons = nodes(idx) match {
        case field: DynamicOptic.Node.Field =>
          val name    = field.name
          val results = ChunkBuilder.make[Json]()
          jsons.foreach {
            case obj: Object =>
              var kv: (java.lang.String, Json) = null
              val kvs                          = obj.value
              val len                          = kvs.length
              var idx                          = 0
              while (
                idx < len && {
                  kv = kvs(idx)
                  kv._1 != name
                }
              ) idx += 1
              if (idx < len) results.addOne(kv._2)
            case _ => ()
          }
          if (results.knownSize == 0 && jsons.nonEmpty) {
            return JsonSelection.fail(SchemaError(s"Field '$name' not found"))
          }
          results.result()
        case atIndex: DynamicOptic.Node.AtIndex =>
          val index   = atIndex.index
          val results = jsons.collect {
            case arr: Array if index < arr.value.length => arr.value.apply(index)
          }
          if (results.isEmpty && jsons.nonEmpty) {
            return JsonSelection.fail(SchemaError(s"Index $index out of bounds"))
          }
          results
        case _: DynamicOptic.Node.Elements.type =>
          jsons.flatMap {
            case arr: Array => arr.value
            case _          => Chunk.empty[Json]
          }
        case _: DynamicOptic.Node.MapKeys.type =>
          val results = ChunkBuilder.make[Json]()
          jsons.foreach {
            case obj: Object => obj.value.foreach(kv => results.addOne(new String(kv._1)))
            case _           => ()
          }
          results.result()
        case _: DynamicOptic.Node.MapValues.type =>
          val results = ChunkBuilder.make[Json]()
          jsons.foreach {
            case obj: Object => obj.value.foreach(kv => results.addOne(kv._2))
            case _           => ()
          }
          results.result()
        case atIndices: DynamicOptic.Node.AtIndices =>
          val indices = atIndices.index
          val results = ChunkBuilder.make[Json]()
          jsons.foreach {
            case arr: Array =>
              indices.foreach {
                val elems = arr.value
                index => if (index >= 0 && index < elems.length) results.addOne(elems(index))
              }
            case _ => ()
          }
          results.result()
        case atMapKey: DynamicOptic.Node.AtMapKey =>
          // Convert DynamicValue key to string for JSON objects
          atMapKey.key match {
            case DynamicValue.Primitive(pv: PrimitiveValue.String) =>
              val name    = pv.value
              val results = ChunkBuilder.make[Json]()
              jsons.foreach {
                case obj: Object =>
                  var kv: (java.lang.String, Json) = null
                  val kvs                          = obj.value
                  val len                          = kvs.length
                  var idx                          = 0
                  while (
                    idx < len && {
                      kv = kvs(idx)
                      kv._1 != name
                    }
                  ) idx += 1
                  if (idx < len) results.addOne(kv._2)
                case _ => ()
              }
              results.result()
            case _ => return JsonSelection.fail(SchemaError("AtMapKey requires a string key for JSON objects"))
          }
        case atMapKeys: DynamicOptic.Node.AtMapKeys =>
          val keys    = atMapKeys.keys
          val keyStrs = new util.HashSet[java.lang.String](keys.size)
          keys.foreach {
            case DynamicValue.Primitive(pv: PrimitiveValue.String) => keyStrs.add(pv.value)
            case _                                                 => ()
          }
          val results = ChunkBuilder.make[Json]()
          jsons.foreach {
            case obj: Object =>
              val kvs = obj.value
              val len = kvs.length
              var idx = 0
              while (idx < len) {
                val kv = kvs(idx)
                if (keyStrs.contains(kv._1)) results.addOne(kv._2)
                idx += 1
              }
            case _ => ()
          }
          results.result()
        case _ => // Case and Wrapped are not applicable to raw JSON
          jsons
      }
      idx += 1
    }
    JsonSelection.succeedMany(jsons)
  }

  /**
   * Modifies the value at the given path, returning Some(modified) or None if
   * the path doesn't exist.
   */
  private def modifyAtPath(json: Json, path: DynamicOptic, f: Json => Json): Option[Json] =
    modifyAtPathRecursive(json, path.nodes, 0, f)

  private[this] def modifyAtPathRecursive(
    json: Json,
    nodes: IndexedSeq[DynamicOptic.Node],
    nodeIdx: Int,
    f: Json => Json
  ): Option[Json] = {
    if (nodeIdx >= nodes.length) return new Some(f(json))
    nodes(nodeIdx) match {
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
              modifyAtPathRecursive(fields(fieldIdx)._2, nodes, nodeIdx + 1, f) match {
                case Some(newValue) => new Some(new Object(fields.updated(fieldIdx, (name, newValue))))
                case _              => None
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
              modifyAtPathRecursive(elems(index), nodes, nodeIdx + 1, f) match {
                case Some(newValue) => new Some(new Array(elems.updated(index, newValue)))
                case _              => None
              }
            } else None
          case _ => None
        }
      case _: DynamicOptic.Node.Elements.type =>
        json match {
          case arr: Array =>
            new Some(new Array(arr.value.map { elem =>
              modifyAtPathRecursive(elem, nodes, nodeIdx + 1, f) match {
                case Some(json) => json
                case _          => elem
              }
            }))
          case _ => None
        }
      case _: DynamicOptic.Node.MapValues.type =>
        json match {
          case obj: Object =>
            new Some(new Object(obj.value.map { case (k, v) =>
              (
                k,
                modifyAtPathRecursive(v, nodes, nodeIdx + 1, f) match {
                  case Some(json) => json
                  case _          => v
                }
              )
            }))
          case _ => None
        }
      case atIndices: DynamicOptic.Node.AtIndices =>
        json match {
          case arr: Array =>
            val indexSet = atIndices.index.toSet
            new Some(new Array(arr.value.map {
              var idx = -1
              elem =>
                idx += 1
                if (indexSet.contains(idx)) {
                  modifyAtPathRecursive(elem, nodes, nodeIdx + 1, f) match {
                    case Some(json) => json
                    case _          => elem
                  }
                } else elem
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
                  modifyAtPathRecursive(fields(fieldIdx)._2, nodes, nodeIdx + 1, f) match {
                    case Some(newValue) => new Some(new Object(fields.updated(fieldIdx, (keyStr, newValue))))
                    case _              => None
                  }
                } else None
              case _ => None
            }
          case _ => None
        }
      case atMapKeys: DynamicOptic.Node.AtMapKeys =>
        val keyStrs = new util.HashSet[java.lang.String]
        atMapKeys.keys.foreach {
          case DynamicValue.Primitive(pv: PrimitiveValue.String) => keyStrs.add(pv.value)
          case _                                                 => ()
        }
        json match {
          case obj: Object =>
            new Some(new Object(obj.value.map { kv =>
              val k = kv._1
              if (keyStrs.contains(k)) {
                val v = kv._2
                (
                  k,
                  modifyAtPathRecursive(v, nodes, nodeIdx + 1, f) match {
                    case Some(json) => json
                    case _          => v
                  }
                )
              } else kv
            }))
          case _ => None
        }
      case _: DynamicOptic.Node.MapKeys.type =>
        None // Cannot modify map keys in JSON (keys are strings, not values)
      case _ =>
        modifyAtPathRecursive(json, nodes, nodeIdx + 1, f) // Case and Wrapped pass through for JSON
    }
  }

  /**
   * Modifies the value at the given path using a partial function. Returns Left
   * with error if the path doesn't exist or the partial function is not
   * defined.
   */
  private def modifyAtPathOrFail(
    json: Json,
    path: DynamicOptic,
    pf: PartialFunction[Json, Json]
  ): Either[SchemaError, Json] = modifyAtPathOrFailRecursive(json, path.nodes, 0, pf)

  private[this] def modifyAtPathOrFailRecursive(
    json: Json,
    nodes: IndexedSeq[DynamicOptic.Node],
    nodeIdx: Int,
    pf: PartialFunction[Json, Json]
  ): Either[SchemaError, Json] =
    if (nodeIdx >= nodes.length) {
      if (pf.isDefinedAt(json)) new Right(pf(json))
      else new Left(SchemaError("Partial function not defined for value at path"))
    } else {
      nodes(nodeIdx) match {
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
                modifyAtPathOrFailRecursive(fields(fieldIdx)._2, nodes, nodeIdx + 1, pf) match {
                  case Right(newValue) => new Right(new Object(fields.updated(fieldIdx, (name, newValue))))
                  case l               => l
                }
              } else new Left(SchemaError(s"Field '$name' not found"))
            case _ => new Left(SchemaError(s"Cannot access field '$name' on non-object"))
          }
        case atIndex: DynamicOptic.Node.AtIndex =>
          val index = atIndex.index
          json match {
            case arr: Array =>
              val elems = arr.value
              if (index >= 0 && index < elems.length) {
                modifyAtPathOrFailRecursive(elems(index), nodes, nodeIdx + 1, pf) match {
                  case Right(newValue) => new Right(new Array(elems.updated(index, newValue)))
                  case l               => l
                }
              } else new Left(SchemaError(s"Index $index out of bounds (size: ${elems.length})"))
            case _ => new Left(SchemaError(s"Cannot access index $index on non-array"))
          }
        case _: DynamicOptic.Node.Elements.type =>
          json match {
            case arr: Array =>
              val builder = ChunkBuilder.make[Json]()
              val elems   = arr.value
              val len     = elems.length
              var idx     = 0
              while (idx < len) {
                val elem = elems(idx)
                modifyAtPathOrFailRecursive(elem, nodes, nodeIdx + 1, pf) match {
                  case Right(newV) => builder.addOne(newV)
                  case l           => return l
                }
                idx += 1
              }
              new Right(new Array(builder.result()))
            case _ => new Left(SchemaError("Cannot iterate elements on non-array"))
          }
        case _: DynamicOptic.Node.MapValues.type =>
          json match {
            case obj: Object =>
              val builder = ChunkBuilder.make[(java.lang.String, Json)]()
              val fields  = obj.value
              val len     = fields.length
              var idx     = 0
              while (idx < len) {
                val kv = fields(idx)
                modifyAtPathOrFailRecursive(kv._2, nodes, nodeIdx + 1, pf) match {
                  case Right(newV) => builder.addOne((kv._1, newV))
                  case l           => return l
                }
                idx += 1
              }
              new Right(new Object(builder.result()))
            case _ => new Left(SchemaError("Cannot iterate map values on non-object"))
          }
        case _ =>
          // For other node types, delegate to a non-failing version and wrap the result
          modifyAtPathRecursive(json, nodes.drop(nodeIdx), 0, pf.lift.andThen(_.getOrElse(json))) match {
            case some: Some[_] => new Right(some.value)
            case _             => new Left(SchemaError(s"Path not found: ${new DynamicOptic(nodes)}"))
          }
      }
    }

  /**
   * Deletes the value at the given path, returning Some(modified) or None if
   * the path doesn't exist.
   */
  private def deleteAtPath(json: Json, path: DynamicOptic): Option[Json] = {
    val nodes = path.nodes
    if (nodes.isEmpty) return None // Can't delete root
    deleteAtPathRecursive(json, nodes, 0)
  }

  private[this] def deleteAtPathRecursive(json: Json, nodes: IndexedSeq[DynamicOptic.Node], idx: Int): Option[Json] = {
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
                deleteAtPathRecursive(fields(fieldIdx)._2, nodes, idx + 1) match {
                  case Some(newValue) => new Some(new Object(fields.updated(fieldIdx, (name, newValue))))
                  case _              => None
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
            if (index >= 0 && index < elems.length) {
              if (isLast) {
                // Delete this element
                new Some(new Array(elems.take(index) ++ elems.drop(index + 1)))
              } else {
                // Navigate into the element and continue
                deleteAtPathRecursive(elems(index), nodes, idx + 1) match {
                  case Some(newValue) => new Some(new Array(elems.updated(index, newValue)))
                  case _              => None
                }
              }
            } else None
          case _ => None
        }
      case _: DynamicOptic.Node.Elements.type =>
        json match {
          case arr: Array =>
            new Some(if (isLast) {
              // Delete all elements
              Array.empty
            } else {
              // Apply delete to each element
              new Array(arr.value.flatMap(e => deleteAtPathRecursive(e, nodes, idx + 1)))
            })
          case _ => None
        }
      case _ => None // Other node types not supported for delete
    }
  }

  /**
   * Inserts a value at the given path, returning Some(modified) or None if the
   * path already exists.
   */
  private def insertAtPath(json: Json, path: DynamicOptic, value: Json): Option[Json] = {
    val nodes = path.nodes
    if (nodes.isEmpty) return None // Can't insert at root
    insertAtPathRecursive(json, nodes, 0, value)
  }

  private[this] def insertAtPathRecursive(
    json: Json,
    nodes: IndexedSeq[DynamicOptic.Node],
    nodeIdx: Int,
    value: Json
  ): Option[Json] = {
    val isLast = nodeIdx == nodes.length - 1
    nodes(nodeIdx) match {
      case field: DynamicOptic.Node.Field =>
        json match {
          case obj: Object =>
            val name   = field.name
            val fields = obj.value
            if (isLast) {
              // Insert this field (only if it doesn't exist)
              val exists = fields.exists(_._1 == name)
              if (!exists) new Some(new Object(fields.appended((name, value))))
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
                insertAtPathRecursive(fields(fieldIdx)._2, nodes, nodeIdx + 1, value) match {
                  case Some(newValue) => new Some(new Object(fields.updated(fieldIdx, (name, newValue))))
                  case _              => None
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
                new Some(new Array(elems.take(index).appended(value) ++ elems.drop(index)))
              } else None
            } else {
              // Navigate into the element and continue
              if (index >= 0 && index < elems.length) {
                insertAtPathRecursive(elems(index), nodes, nodeIdx + 1, value) match {
                  case Some(newValue) => new Some(new Array(elems.updated(index, newValue)))
                  case _              => None
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
  private def insertAtPathOrFail(json: Json, path: DynamicOptic, value: Json): Either[SchemaError, Json] = {
    val nodes = path.nodes
    if (nodes.isEmpty) return Left(SchemaError("Cannot insert at root path"))
    insertAtPathOrFailRecursive(json, nodes, 0, value)
  }

  private[this] def insertAtPathOrFailRecursive(
    json: Json,
    nodes: IndexedSeq[DynamicOptic.Node],
    nodeIdx: Int,
    value: Json
  ): Either[SchemaError, Json] = {
    val isLast = nodeIdx == nodes.length - 1
    nodes(nodeIdx) match {
      case field: DynamicOptic.Node.Field =>
        val name = field.name
        json match {
          case Object(fields) =>
            if (isLast) {
              val exists = fields.exists(_._1 == name)
              if (!exists) new Right(new Object(fields.appended((name, value))))
              else new Left(SchemaError(s"Field '$name' already exists"))
            } else {
              var found    = false
              var fieldIdx = 0
              val len      = fields.length
              while (fieldIdx < len && !found) {
                if (fields(fieldIdx)._1 == name) found = true
                else fieldIdx += 1
              }
              if (found) {
                insertAtPathOrFailRecursive(fields(fieldIdx)._2, nodes, nodeIdx + 1, value) match {
                  case Right(newValue) => new Right(new Object(fields.updated(fieldIdx, (name, newValue))))
                  case l               => l
                }
              } else new Left(SchemaError(s"Field '$name' not found"))
            }
          case _ => new Left(SchemaError(s"Cannot access field '$name' on non-object"))
        }
      case ai: DynamicOptic.Node.AtIndex =>
        val index = ai.index
        json match {
          case arr: Array =>
            val elems = arr.value
            if (isLast) {
              if (index >= 0 && index <= elems.length) {
                new Right(new Array(elems.take(index).appended(value) ++ elems.drop(index)))
              } else new Left(SchemaError(s"Index $index out of bounds for insert (size: ${elems.length})"))
            } else {
              if (index >= 0 && index < elems.length) {
                insertAtPathOrFailRecursive(elems(index), nodes, nodeIdx + 1, value) match {
                  case Right(newValue) => new Right(new Array(elems.updated(index, newValue)))
                  case l               => l
                }
              } else new Left(SchemaError(s"Index $index out of bounds (size: ${elems.length})"))
            }
          case _ => new Left(SchemaError(s"Cannot access index $index on non-array"))
        }
      case other => new Left(SchemaError(s"Insert not supported for path node type: $other"))
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Ordering
  // ─────────────────────────────────────────────────────────────────────────

  implicit val ordering: Ordering[Json] = (x: Json, y: Json) => x.compare(y)

  // ─────────────────────────────────────────────────────────────────────────
  // JsonBinaryCodec for Json
  // ─────────────────────────────────────────────────────────────────────────

  implicit lazy val nullSchema: Schema[Null.type] = new Schema(
    reflect = new Reflect.Record[Binding, Null.type](
      fields = Chunk.empty,
      typeId = TypeId.of[Null.type],
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[Null.type](Null),
        deconstructor = new ConstantDeconstructor[Null.type]
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val booleanSchema: Schema[Boolean] = new Schema(
    reflect = new Reflect.Record[Binding, Boolean](
      fields = Chunk.single(Schema[scala.Boolean].reflect.asTerm("value")),
      typeId = TypeId.of[Boolean],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Boolean] {
          def usedRegisters: RegisterOffset                             = 1
          def construct(in: Registers, offset: RegisterOffset): Boolean = Boolean(in.getBoolean(offset))
        },
        deconstructor = new Deconstructor[Boolean] {
          def usedRegisters: RegisterOffset                                          = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: Boolean): Unit =
            out.setBoolean(offset, in.value)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val numberSchema: Schema[Number] = new Schema(
    reflect = new Reflect.Record[Binding, Number](
      fields = Chunk.single(Schema[BigDecimal].reflect.asTerm("value")),
      typeId = TypeId.of[Number],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Number] {
          def usedRegisters: RegisterOffset                            = 1
          def construct(in: Registers, offset: RegisterOffset): Number =
            new Number(in.getObject(offset).asInstanceOf[BigDecimal])
        },
        deconstructor = new Deconstructor[Number] {
          def usedRegisters: RegisterOffset                                         = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: Number): Unit =
            out.setObject(offset, in.value)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val stringSchema: Schema[String] = new Schema(
    reflect = new Reflect.Record[Binding, String](
      fields = Chunk.single(Schema[java.lang.String].reflect.asTerm("value")),
      typeId = TypeId.of[String],
      recordBinding = new Binding.Record(
        constructor = new Constructor[String] {
          def usedRegisters: RegisterOffset                            = 1
          def construct(in: Registers, offset: RegisterOffset): String =
            new String(in.getObject(offset).asInstanceOf[java.lang.String])
        },
        deconstructor = new Deconstructor[String] {
          def usedRegisters: RegisterOffset                                         = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: String): Unit =
            out.setObject(offset, in.value)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val arraySchema: Schema[Array] = new Schema(
    reflect = new Reflect.Record[Binding, Array](
      fields = Chunk.single(Reflect.Deferred(() => Reflect.indexedSeq(schema.reflect)).asTerm("value")),
      typeId = TypeId.of[Array],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Array] {
          def usedRegisters: RegisterOffset                           = 1
          def construct(in: Registers, offset: RegisterOffset): Array =
            new Array(Chunk.from(in.getObject(offset).asInstanceOf[IndexedSeq[Json]]))
        },
        deconstructor = new Deconstructor[Array] {
          def usedRegisters: RegisterOffset                                        = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: Array): Unit =
            out.setObject(offset, in.value)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  private lazy val tupleReflect: Reflect[Binding, (java.lang.String, Json)] = {
    val stringReflect = Schema[java.lang.String].reflect
    new Reflect.Record[Binding, (java.lang.String, Json)](
      fields = Chunk(stringReflect.asTerm("_1"), new Reflect.Deferred(() => schema.reflect).asTerm("_2")),
      typeId = TypeId.of[(java.lang.String, Json)],
      recordBinding = new Binding.Record(
        constructor = new Constructor[(java.lang.String, Json)] {
          def usedRegisters: RegisterOffset                                              = 2
          def construct(in: Registers, offset: RegisterOffset): (java.lang.String, Json) =
            (in.getObject(offset).asInstanceOf[java.lang.String], in.getObject(offset + 1).asInstanceOf[Json])
        },
        deconstructor = new Deconstructor[(java.lang.String, Json)] {
          def usedRegisters: RegisterOffset                                                           = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: (java.lang.String, Json)): Unit = {
            out.setObject(offset, in._1)
            out.setObject(offset + 1, in._2)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  }

  implicit lazy val objectSchema: Schema[Object] = new Schema(
    reflect = new Reflect.Record[Binding, Object](
      fields = Chunk.single(Reflect.Deferred(() => Reflect.indexedSeq(tupleReflect)).asTerm("value")),
      typeId = TypeId.of[Object],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Object] {
          def usedRegisters: RegisterOffset                            = 1
          def construct(in: Registers, offset: RegisterOffset): Object =
            new Object(Chunk.from(in.getObject(offset).asInstanceOf[IndexedSeq[(java.lang.String, Json)]]))
        },
        deconstructor = new Deconstructor[Object] {
          def usedRegisters: RegisterOffset                                         = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: Object): Unit =
            out.setObject(offset, in.value)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val schema: Schema[Json] = new Schema(
    reflect = new Reflect.Variant[Binding, Json](
      cases = Chunk(
        nullSchema.reflect.asTerm("Null"),
        booleanSchema.reflect.asTerm("Boolean"),
        numberSchema.reflect.asTerm("Number"),
        stringSchema.reflect.asTerm("String"),
        arraySchema.reflect.asTerm("Array"),
        objectSchema.reflect.asTerm("Object")
      ),
      typeId = TypeId.of[Json],
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[Json] {
          def discriminate(a: Json): Int = a match {
            case _: Null.type => 0
            case _: Boolean   => 1
            case _: Number    => 2
            case _: String    => 3
            case _: Array     => 4
            case _: Object    => 5
          }
        },
        matchers = Matchers(
          new Matcher[Null.type] {
            def downcastOrNull(a: Any): Null.type = a match {
              case x: Null.type => x
              case _            => null.asInstanceOf[Null.type]
            }
          },
          new Matcher[Boolean] {
            def downcastOrNull(a: Any): Boolean = a match {
              case x: Boolean => x
              case _          => null.asInstanceOf[Boolean]
            }
          },
          new Matcher[Number] {
            def downcastOrNull(a: Any): Number = a match {
              case x: Number => x
              case _         => null.asInstanceOf[Number]
            }
          },
          new Matcher[String] {
            def downcastOrNull(a: Any): String = a match {
              case x: String => x
              case _         => null.asInstanceOf[String]
            }
          },
          new Matcher[Array] {
            def downcastOrNull(a: Any): Array = a match {
              case x: Array => x
              case _        => null.asInstanceOf[Array]
            }
          },
          new Matcher[Object] {
            def downcastOrNull(a: Any): Object = a match {
              case x: Object => x
              case _         => null.asInstanceOf[Object]
            }
          }
        )
      ),
      modifiers = Chunk.empty
    )
  )

  implicit val jsonCodec: JsonBinaryCodec[Json] = new JsonBinaryCodec[Json] {
    override def decodeValue(in: JsonReader, default: Json): Json = {
      var x = in.nextToken().toInt
      if (x == '"') {
        in.rollbackToken()
        new String(in.readString(null))
      } else if (x == 'f' || x == 't') {
        in.rollbackToken()
        Boolean.apply(in.readBoolean())
      } else if (x >= '0' && x <= '9' || x == '-') {
        in.rollbackToken()
        new Number(in.readBigDecimal(null))
      } else if (x == '[') {
        if (in.isNextToken(']')) Array.empty
        else {
          in.rollbackToken()
          var arr    = new scala.Array[Json](4)
          var errIdx = 0
          x = 0
          try {
            while ({
              errIdx = x
              arr(x) = decodeValue(in, default)
              x += 1
              errIdx = -1
              in.isNextToken(',')
            }) {
              if (arr.length == x) arr = util.Arrays.copyOf(arr, x << 1)
            }
          } catch {
            case error if NonFatal(error) && errIdx >= 0 => in.decodeError(new DynamicOptic.Node.AtIndex(errIdx), error)
          }
          if (in.isCurrentToken(']')) {
            if (arr.length != x) arr = util.Arrays.copyOf(arr, x)
            new Array(Chunk.fromArray(arr))
          } else in.arrayEndOrCommaError()
        }
      } else if (x == '{') {
        if (in.isNextToken('}')) Object.empty
        else {
          in.rollbackToken()
          var arr                   = new scala.Array[(java.lang.String, Json)](4)
          var key: java.lang.String = null
          x = 0
          try {
            while ({
              key = in.readKeyAsString()
              arr(x) = new Tuple2(key, decodeValue(in, default))
              x += 1
              key = null
              in.isNextToken(',')
            }) {
              if (arr.length == x) arr = util.Arrays.copyOf(arr, x << 1)
            }
          } catch {
            case error if NonFatal(error) && (key ne null) => in.decodeError(new DynamicOptic.Node.Field(key), error)
          }
          if (in.isCurrentToken('}')) {
            if (arr.length != x) arr = util.Arrays.copyOf(arr, x)
            new Object(Chunk.fromArray(arr))
          } else in.objectEndOrCommaError()
        }
      } else {
        in.rollbackToken()
        in.readNullOrError(default, "expected JSON value")
      }
    }

    override def encodeValue(x: Json, out: JsonWriter): Unit = x match {
      case str: String   => out.writeVal(str.value)
      case bool: Boolean => out.writeVal(bool.value)
      case num: Number   => out.writeVal(num.value)
      case arr: Array    =>
        out.writeArrayStart()
        arr.value.foreach(encodeValue(_, out))
        out.writeArrayEnd()
      case obj: Object =>
        out.writeObjectStart()
        obj.value.foreach { kv =>
          out.writeKey(kv._1)
          encodeValue(kv._2, out)
        }
        out.writeObjectEnd()
      case _ => out.writeNull()
    }

    override def nullValue: Json = Null
  }
}
