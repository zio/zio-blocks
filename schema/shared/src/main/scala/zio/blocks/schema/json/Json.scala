/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.schema.json

import zio.blocks.chunk.{Chunk, ChunkBuilder}
import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue, Reflect, Schema, SchemaError, SchemaRepr}
import zio.blocks.typeid.TypeId
import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.schema.patch.PatchMode
import java.nio.ByteBuffer
import java.time._
import java.util
import scala.util.control.NonFatal
import scala.util.hashing.MurmurHash3

/**
 * An immutable JSON value: a [[Json.Object]], [[Json.Array]], [[Json.String]],
 * [[Json.Number]], [[Json.Boolean]] or [[Json.Null]]. Use it to read, build and
 * change JSON that no case class describes, and to convert between JSON and
 * typed values with a [[zio.blocks.schema.Schema]] (`as`, [[Json.from]]).
 *
 *   - An object keeps its fields in order, duplicates included, but `==`,
 *     `hashCode` and `compare` ignore the order.
 *   - A number holds a `BigDecimal`. `Json.parse` rounds a number to 34
 *     significant digits and rejects one with more than 308 digits or an
 *     exponent beyond the reader's scale limit.
 *   - Navigation (`get`) returns a [[JsonSelection]], which holds zero or more
 *     values or an error.
 *   - `toString` prints the value indented by two spaces; `print` prints it
 *     compact.
 *
 * {{{
 * import zio.blocks.schema.json._
 *
 * val book = Json.Object(
 *   "title"   -> Json.String("Zionomicon"),
 *   "authors" -> Json.Array(Json.String("John De Goes"), Json.String("Adam Fraser")),
 *   "year"    -> Json.Number(2021)
 * )
 *
 * book.get("title").as[String]          // Right(Zionomicon)
 * book.print                            // {"title":"Zionomicon","authors":["John De Goes","Adam Fraser"],"year":2021}
 * Json.parse(book.print) == Right(book) // true
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/built-in-codecs/json/json/ Json]]
 */
sealed trait Json {

  /** Prints this value as JSON indented by two spaces. */
  override def toString: String = Json.jsonCodec.encodeToString(this, WriterConfig.withIndentionStep2)

  // ─────────────────────────────────────────────────────────────────────────
  // Type Information
  // ─────────────────────────────────────────────────────────────────────────

  /** Returns the [[JsonType]] of this JSON value. */
  def jsonType: JsonType

  // ─────────────────────────────────────────────────────────────────────────
  // Unified Type Operations
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Returns `true` if this value is of the given type.
   *
   * {{{
   * import zio.blocks.schema.json._
   *
   * Json.String("Zionomicon").is(JsonType.String) // true
   * Json.String("Zionomicon").is(JsonType.Number) // false
   * }}}
   */
  def is(jsonType: JsonType): Boolean = this.jsonType eq jsonType

  /**
   * Returns this value as the case of the given type, such as `Json.String` for
   * `JsonType.String`, or `None` when it is of another type.
   *
   * {{{
   * import zio.blocks.schema.json._
   *
   * val title: Option[Json.String] = Json.String("Zionomicon").as(JsonType.String) // Some("Zionomicon")
   * Json.String("Zionomicon").as(JsonType.Number)                                  // None
   * }}}
   */
  def as(jsonType: JsonType): Option[jsonType.Type]

  /**
   * Returns the Scala value inside this value when it is of the given type (a
   * `String`, a `BigDecimal`, a `Boolean`, the fields, the elements, or `()`
   * for null), or `None` when it is of another type.
   *
   * {{{
   * import zio.blocks.schema.json._
   *
   * val title: Option[String]    = Json.String("Zionomicon").unwrap(JsonType.String) // Some(Zionomicon)
   * val year: Option[BigDecimal] = Json.Number(2021).unwrap(JsonType.Number)        // Some(2021)
   * }}}
   */
  def unwrap(jsonType: JsonType): Option[jsonType.Unwrap]

  // ─────────────────────────────────────────────────────────────────────────
  // Direct Accessors
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Returns the fields of an object, or an empty `Chunk` for any other value.
   */
  def fields: Chunk[(String, Json)] = Chunk.empty

  /**
   * Returns the elements of an array, or an empty `Chunk` for any other value.
   */
  def elements: Chunk[Json] = Chunk.empty

  // ─────────────────────────────────────────────────────────────────────────
  // Navigation
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Selects the value of the field `key` of an object. The selection fails when
   * the key is missing (`Key 'isbn' not found`) or this is not an object. With
   * duplicate keys, it selects the first one.
   */
  def get(key: String): JsonSelection =
    JsonSelection.fail(SchemaError(s"Cannot get field '$key' from non-object JSON value"))

  /**
   * Selects the element at `index` of an array. The selection fails when the
   * index is out of bounds (`Index 5 out of bounds (size: 2) at: [5]`) or this
   * is not an array.
   */
  def get(index: Int): JsonSelection =
    JsonSelection.fail(SchemaError(s"Cannot get index $index from non-array JSON value"))

  // ─────────────────────────────────────────────────────────────────────────
  // Path-based Navigation and Modification (DynamicOptic)
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Selects every value at `path`. A field or index step fails the selection
   * only when no current value has that field or index, so `[*].isbn` skips the
   * elements without an `isbn`. `[*]`, map keys and values, a string-keyed
   * `AtMapKey` step and a schema search select many values; a type search
   * fails, and case and wrapper steps are skipped.
   *
   * {{{
   * import zio.blocks.schema.DynamicOptic
   * import zio.blocks.schema.json._
   *
   * val shelf = Json.parseUnsafe("""{"books":[{"title":"Zionomicon"},{"title":"Programming in Scala"}]}""")
   *
   * shelf.get(DynamicOptic.root.field("books").elements.field("title")).toChunk
   * // Chunk("Zionomicon","Programming in Scala")
   * }}}
   */
  def get(path: DynamicOptic): JsonSelection = Json.getAtPath(this, path)

  /**
   * Replaces each value at `path` with `f` of it. When the path matches
   * nothing, returns this value unchanged and reports nothing; use
   * [[modifyOrFail]] to find out. Through `[*]`, an element where the rest of
   * the path does not match is kept as it is.
   */
  def modify(path: DynamicOptic)(f: Json => Json): Json = Json.modifyAtPath(this, path, f) match {
    case Some(json) => json
    case _          => this
  }

  /**
   * Replaces the value at `path` with `pf` of it, or returns `Left` when a
   * field or index on the path is missing, a step meets a value of the wrong
   * kind, or `pf` is not defined at a value reached through field, index, `[*]`
   * and map-value steps. From any other step on, an undefined `pf` does not
   * fail: the value is replaced by the value the path had reached before that
   * step, and a path that matches nothing gives `Path not found`.
   */
  def modifyOrFail(path: DynamicOptic)(pf: PartialFunction[Json, Json]): Either[SchemaError, Json] =
    Json.modifyAtPathOrFail(this, path, pf)

  /**
   * Replaces each value at `path` with `value`. It never adds a missing field:
   * when the path matches nothing, it returns this value unchanged.
   */
  def set(path: DynamicOptic, value: Json): Json = modify(path)(_ => value)

  /**
   * Replaces the value at `path` with `value`, or returns `Left` when the path
   * does not exist (`Field 'isbn' not found`).
   */
  def setOrFail(path: DynamicOptic, value: Json): Either[SchemaError, Json] =
    Json.modifyAtPathOrFail(this, path, { case _ => value })

  /**
   * Removes the field or element at `path`, or returns this value unchanged
   * when nothing is removed. Only field, index, `[*]` and schema-search steps
   * are followed; a path with other steps, such as a quoted key, and the root
   * path change nothing. A last `[*]` empties the array, and through `[*]` an
   * element where the rest of the path does not match is dropped (a known bug).
   */
  def delete(path: DynamicOptic): Json = Json.deleteAtPath(this, path) match {
    case Some(json) => json
    case _          => this
  }

  /**
   * Removes the field or element at `path` as [[delete]] does, or returns
   * `Left` (`Path not found: <path>`) when nothing is removed.
   */
  def deleteOrFail(path: DynamicOptic): Either[SchemaError, Json] = Json.deleteAtPath(this, path) match {
    case Some(json) => new Right(json)
    case _          => new Left(SchemaError(s"Path not found: $path"))
  }

  /**
   * Adds `value` at `path`: as a new last field of an object, or at an index of
   * an array (from 0 to its size), shifting later elements. Returns this value
   * unchanged when the field already exists, the index is out of range, the
   * parent is missing, the path is the root, or it has a step other than a
   * field or an index.
   */
  def insert(path: DynamicOptic, value: Json): Json = Json.insertAtPath(this, path, value) match {
    case Some(json) => json
    case _          => this
  }

  /**
   * Adds `value` at `path` as [[insert]] does, or returns `Left` saying why it
   * could not: the field exists, the index is out of range, the parent is
   * missing, the root path, or an unsupported step.
   */
  def insertOrFail(path: DynamicOptic, value: Json): Either[SchemaError, Json] =
    Json.insertAtPathOrFail(this, path, value)

  // ─────────────────────────────────────────────────────────────────────────
  // Merging
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Merges `that` into this value with `strategy`. The default,
   * [[MergeStrategy.Auto]], merges objects by key and arrays by index, and
   * takes `that` value elsewhere. Everything the strategy does not recurse
   * into, including two values of different kinds, goes to its function; the
   * built-in strategies return `that` there, except that
   * [[MergeStrategy.Concat]] joins two arrays.
   *
   * {{{
   * import zio.blocks.schema.json._
   *
   * val left  = Json.parseUnsafe("""{"tags":["fp"],"info":{"year":2021,"lang":"en"}}""")
   * val right = Json.parseUnsafe("""{"tags":["scala","zio"],"info":{"year":2022}}""")
   *
   * left.merge(right).print                       // {"tags":["scala","zio"],"info":{"year":2022,"lang":"en"}}
   * left.merge(right, MergeStrategy.Concat).print // {"tags":["fp","scala","zio"],"info":{"year":2022,"lang":"en"}}
   * }}}
   */
  def merge(that: Json, strategy: MergeStrategy = MergeStrategy.Auto): Json =
    Json.merge(DynamicOptic.root, this, that, strategy)

  // ─────────────────────────────────────────────────────────────────────────
  // Normalization
  // ─────────────────────────────────────────────────────────────────────────

  /** Sorts the fields of every object by key, at every depth. */
  def sortKeys: Json = this

  /**
   * Removes every null field of an object and null element of an array, at
   * every depth.
   */
  def dropNulls: Json = this

  /**
   * Removes every empty object and empty array inside this value, at every
   * depth, including ones that become empty. This value itself stays, even when
   * empty.
   */
  def dropEmpty: Json = this

  /**
   * Removes nulls and empty containers and sorts keys, as [[dropNulls]],
   * [[dropEmpty]] and [[sortKeys]] do, in one pass.
   */
  def normalize: Json = this

  // ─────────────────────────────────────────────────────────────────────────
  // Encoding
  // ─────────────────────────────────────────────────────────────────────────

  /** Prints this value as compact JSON. */
  def print: String = Json.jsonCodec.encodeToString(this)

  /**
   * Prints this value as JSON formatted by `config`.
   *
   * {{{
   * import zio.blocks.schema.json._
   *
   * Json.Object("title" -> Json.String("Zionomicon")).print(WriterConfig.withIndentionStep(2))
   * // {
   * //   "title": "Zionomicon"
   * // }
   * }}}
   */
  def print(config: WriterConfig): String = Json.jsonCodec.encodeToString(this, config)

  /** Prints this value as compact JSON in UTF-8 bytes. */
  def printBytes: Array[Byte] = Json.jsonCodec.encode(this)

  /** Prints this value as JSON in UTF-8 bytes, formatted by `config`. */
  def printBytes(config: WriterConfig): Array[Byte] = Json.jsonCodec.encode(this, config)

  /** Prints this value as compact JSON in a `Chunk` of UTF-8 bytes. */
  def printChunk: Chunk[Byte] = Chunk.fromArray(Json.jsonCodec.encode(this))

  /**
   * Prints this value as JSON in a `Chunk` of UTF-8 bytes, formatted by
   * `config`.
   */
  def printChunk(config: WriterConfig): Chunk[Byte] = Chunk.fromArray(Json.jsonCodec.encode(this, config))

  /**
   * Writes this value as compact JSON into `buffer`, from its position.
   *
   * @throws java.nio.BufferOverflowException
   *   when the JSON does not fit; the buffer then holds part of it (a known
   *   bug)
   */
  def printTo(buffer: ByteBuffer): Unit = Json.jsonCodec.encode(this, buffer)

  /**
   * Writes this value as JSON formatted by `config` into `buffer`, from its
   * position.
   *
   * @throws java.nio.BufferOverflowException
   *   when the JSON does not fit; the buffer then holds part of it (a known
   *   bug)
   */
  def printTo(buffer: ByteBuffer, config: WriterConfig): Unit = Json.jsonCodec.encode(this, buffer, config)

  // ─────────────────────────────────────────────────────────────────────────
  // Conversion
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Converts this value to a [[zio.blocks.schema.DynamicValue]]: an object
   * becomes a `Record`, an array a `Sequence`, and a number an `Int`, a `Long`
   * or a `BigDecimal`, the first that holds it exactly.
   */
  def toDynamicValue: DynamicValue = Json.toDynamicValue(this)

  /**
   * Decodes this value into an `A` with the JSON codec of `A`'s schema, or
   * returns the first error.
   *
   * {{{
   * import zio.blocks.schema.json._
   *
   * case class Book(title: String, year: Int)
   * object Book {
   *   implicit val schema: Schema[Book] = Schema.derived
   * }
   *
   * Json.parseUnsafe("""{"title":"Zionomicon","year":2021}""").as[Book] // Right(Book(Zionomicon,2021))
   * }}}
   */
  def as[A](implicit schema: Schema[A]): Either[SchemaError, A] = schema.jsonCodec.decode(this)

  /**
   * Decodes this value into an `A` as `as` does.
   *
   * @throws zio.blocks.schema.SchemaError
   *   when decoding fails
   */
  def asUnsafe[A](implicit schema: Schema[A]): A = schema.jsonCodec.decode(this) match {
    case Right(value) => value
    case Left(err)    => throw err
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Comparison
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Orders this value against `that`: values of different kinds by
   * [[typeIndex]], numbers numerically, strings by `compareTo`, arrays element
   * by element, and objects by their key-sorted fields.
   */
  def compare(that: Json): Int

  /**
   * The rank of this value's kind in [[compare]]: null 0, boolean 1, number 2,
   * string 3, array 4, object 5.
   */
  def typeIndex: Int

  // ─────────────────────────────────────────────────────────────────────────
  // Transformation Methods
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Rebuilds this value bottom-up: `f` receives each path and value, children
   * before their parent, and the parent sees its transformed children.
   */
  def transformUp(f: (DynamicOptic, Json) => Json): Json = Json.transformUp(this, DynamicOptic.root, f)

  /**
   * Rebuilds this value top-down: `f` receives each path and value, a parent
   * before its children, and the children of `f`'s result are transformed next.
   */
  def transformDown(f: (DynamicOptic, Json) => Json): Json = Json.transformDown(this, DynamicOptic.root, f)

  /**
   * Renames the keys of every object at every depth with `f`, which receives
   * the path of the field (with its old key) and the key.
   */
  def transformKeys(f: (DynamicOptic, String) => String): Json = Json.transformKeys(this, DynamicOptic.root, f)

  // ─────────────────────────────────────────────────────────────────────────
  // Selection Methods (flat, lift to JsonSelection)
  // ─────────────────────────────────────────────────────────────────────────

  /** Returns a [[JsonSelection]] of this value alone. */
  def select: JsonSelection = JsonSelection.succeed(this)

  /**
   * Returns a [[JsonSelection]] of this value when it is of the given type, or
   * an empty selection. It checks this value only, not its children.
   */
  def select(jsonType: JsonType): JsonSelection =
    if (this.jsonType eq jsonType) JsonSelection.succeed(this)
    else JsonSelection.empty

  // ─────────────────────────────────────────────────────────────────────────
  // Pruning Methods (remove matching nodes)
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Removes every field or element for which `p` returns `true`, at every
   * depth; the children of a removed value are not visited. `p` never sees this
   * value itself.
   */
  def prune(p: Json => Boolean): Json = Json.prune(this, DynamicOptic.root, (_, json) => p(json))

  /**
   * Removes every field or element whose path satisfies `p`, as [[prune]] does.
   */
  def prunePath(p: DynamicOptic => Boolean): Json = Json.prune(this, DynamicOptic.root, (path, _) => p(path))

  /**
   * Removes every field or element whose path and value satisfy `p`, as
   * [[prune]] does.
   */
  def pruneBoth(p: (DynamicOptic, Json) => Boolean): Json = Json.prune(this, DynamicOptic.root, p)

  // ─────────────────────────────────────────────────────────────────────────
  // Retention Methods (keep only matching nodes)
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Keeps only the fields and elements for which `p` returns `true`, at every
   * depth. A nested object or array stays only when `p` holds for the container
   * itself, so `retain(_.is(JsonType.String))` drops every nested object along
   * with the strings in it. `p` never sees this value itself.
   *
   * {{{
   * import zio.blocks.schema.json._
   *
   * Json.parseUnsafe("""{"title":"Zionomicon","year":2021,"tags":["fp"]}""").retain(_.is(JsonType.String)).print
   * // {"title":"Zionomicon"}
   * }}}
   */
  def retain(p: Json => Boolean): Json = Json.retain(this, DynamicOptic.root, (_, json) => p(json))

  /**
   * Keeps only the fields and elements whose path satisfies `p`, as [[retain]]
   * does.
   */
  def retainPath(p: DynamicOptic => Boolean): Json = Json.retain(this, DynamicOptic.root, (path, _) => p(path))

  /**
   * Keeps only the fields and elements whose path and value satisfy `p`, as
   * [[retain]] does.
   */
  def retainBoth(p: (DynamicOptic, Json) => Boolean): Json = Json.retain(this, DynamicOptic.root, p)

  /**
   * Builds a new value from the values at `paths`, each placed at its path, as
   * [[Json.fromKVUnsafe]] does, so only field and index steps are rebuilt. With
   * no paths, or when no path matches, it returns `Json.Null` (a known bug).
   */
  def project(paths: DynamicOptic*): Json = Json.project(this, paths)

  /**
   * Splits this value into the fields and elements for which `p` holds and the
   * rest, as a pair of the same kind as this value. Each field or element goes
   * to the side `p` picks for it and carries only that side's part of its
   * children, so a matching value inside a non-matching container appears in
   * neither half: partitioning `{"editions":[2021,2022]}` by "is a number"
   * gives `{}` and `{"editions":[]}` (a known bug). A leaf at the root goes to
   * one side and `Json.Null` to the other.
   */
  def partition(p: Json => Boolean): (Json, Json) = Json.partition(this, DynamicOptic.root, (_, json) => p(json))

  /** Splits this value by a predicate on the path, as [[partition]] does. */
  def partitionPath(p: DynamicOptic => Boolean): (Json, Json) =
    Json.partition(this, DynamicOptic.root, (path, _) => p(path))

  /**
   * Splits this value by a predicate on the path and value, as [[partition]]
   * does.
   */
  def partitionBoth(p: (DynamicOptic, Json) => Boolean): (Json, Json) = Json.partition(this, DynamicOptic.root, p)

  // ─────────────────────────────────────────────────────────────────────────
  // Folding Methods
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Folds `f` over every value with its path, children before their parent,
   * fields and elements in order.
   */
  def foldUp[B](z: B)(f: (DynamicOptic, Json, B) => B): B = Json.foldUp(this, DynamicOptic.root, z, f)

  /**
   * Folds `f` over every value with its path, a parent before its children,
   * fields and elements in order.
   */
  def foldDown[B](z: B)(f: (DynamicOptic, Json, B) => B): B = Json.foldDown(this, DynamicOptic.root, z, f)

  /**
   * Folds as [[foldUp]] does, stopping at the first `Left` that `f` returns.
   */
  def foldUpOrFail[B](z: B)(f: (DynamicOptic, Json, B) => Either[SchemaError, B]): Either[SchemaError, B] =
    Json.foldUpOrFail(this, DynamicOptic.root, z, f)

  /**
   * Folds as [[foldDown]] does, stopping at the first `Left` that `f` returns.
   */
  def foldDownOrFail[B](z: B)(f: (DynamicOptic, Json, B) => Either[SchemaError, B]): Either[SchemaError, B] =
    Json.foldDownOrFail(this, DynamicOptic.root, z, f)

  /**
   * Flattens this value into its leaves with their paths, in order. An empty
   * object or array counts as a leaf. [[Json.fromKV]] rebuilds the value.
   *
   * {{{
   * import zio.blocks.schema.json._
   *
   * Json.parseUnsafe("""{"title":"Zionomicon","year":2021}""").toKV
   * // Chunk((.title,"Zionomicon"),(.year,2021))
   * }}}
   */
  def toKV: Chunk[(DynamicOptic, Json)] = Json.toKV(this, DynamicOptic.root)

  // ─────────────────────────────────────────────────────────────────────────
  // Patch Methods
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Returns the patch that turns this value into `that`, as [[JsonPatch.diff]]
   * does.
   */
  def diff(that: Json): JsonPatch = JsonPatch.diff(this, that)

  /** Applies `patch` to this value in `mode`, as [[JsonPatch#apply]] does. */
  def patch(patch: JsonPatch, mode: PatchMode = PatchMode.Strict): Either[SchemaError, Json] = patch.apply(this, mode)

  /**
   * Validates this value against `schema`, as `JsonSchema#check` does: `None`
   * when it conforms, or every violation found.
   */
  def check(schema: JsonSchema): Option[SchemaError] = schema.check(this)

  /** Returns `true` if this value conforms to `schema`. */
  def conforms(schema: JsonSchema): scala.Boolean = schema.conforms(this)
}

/**
 * The cases of [[Json]], their constructors, and the parsers, conversions,
 * schemas and codec of JSON values.
 */
object Json {

  // ─────────────────────────────────────────────────────────────────────────
  // ADT Cases
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * A JSON object: its fields in order, duplicate keys included. `==` and
   * `hashCode` ignore the order of the fields.
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
            case obj: Object => obj.value.nonEmpty
            case arr: Array  => arr.value.nonEmpty
            case _           => v2 ne Null
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

  /** Constructors for [[Json.Object]]. */
  object Object {

    /** The object with no fields, `{}`. */
    val empty: Object = Object(Chunk.empty)

    /** Creates an object with the given fields, in order. */
    def apply(fields: (java.lang.String, Json)*): Object = new Object(Chunk.from(fields))

    private val ordering: Ordering[(java.lang.String, Json)] = new Ordering[(java.lang.String, Json)] {
      override def compare(x: (java.lang.String, Json), y: (java.lang.String, Json)): Int = x._1.compareTo(y._1)
    }
  }

  /** A JSON array. */
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
            case obj: Object => obj.value.nonEmpty
            case arr: Array  => arr.value.nonEmpty
            case _           => v ne Null
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

  /** Constructors for [[Json.Array]]. */
  object Array {

    /** The array with no elements, `[]`. */
    val empty: Array = new Array(Chunk.empty)

    /** Creates an array with the given elements. */
    def apply(elements: Json*): Array = new Array(Chunk.from(elements))
  }

  /** A JSON string. */
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
   * A JSON number, held as a `BigDecimal`. Two numbers are `==` when their
   * values are, whatever their scale: `Json.Number(BigDecimal("2.50"))` equals
   * `Json.Number(2.5)`.
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

  /** Constructors for [[Json.Number]] from each Scala number type. */
  object Number {

    /** Creates a JSON number from an Int. */
    def apply(value: Int): Number = new Number(BigDecimal(value))

    /** Creates a JSON number from a Long. */
    def apply(value: Long): Number = new Number(BigDecimal(value))

    /**
     * Creates a JSON number from a Float, with the shortest decimal digits that
     * read back as the same `Float`.
     *
     * @throws java.lang.IllegalArgumentException
     *   for NaN and the infinities
     */
    def apply(value: Float): Number = new Number(JsonWriter.toBigDecimal(value))

    /**
     * Creates a JSON number from a Double, with the shortest decimal digits
     * that read back as the same `Double`. The scale can keep one trailing
     * zero, so `Json.Number(49.99).print` gives `49.990` (a known bug).
     *
     * @throws java.lang.IllegalArgumentException
     *   for NaN and the infinities
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

  /** A JSON boolean; [[Json.True]] and [[Json.False]] are its two values. */
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

  /** Constructor for [[Json.Boolean]]. */
  object Boolean {

    /** Returns [[Json.True]] or [[Json.False]]. */
    def apply(value: scala.Boolean): Boolean =
      if (value) Json.True
      else Json.False
  }

  /** JSON `null`. */
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

  /**
   * Parses one JSON value from `input`, or returns `Left` with the reader's
   * message and path, such as `unexpected end of input at: .`. Text after the
   * value, other than whitespace, is an error. Numbers are rounded to 34
   * significant digits.
   *
   * {{{
   * import zio.blocks.schema.json._
   *
   * Json.parse("""{"title":"Zionomicon","year":2021}""").map(_.print) // Right({"title":"Zionomicon","year":2021})
   * Json.parse("""{"title":"Zionomicon",""").isLeft                  // true
   * }}}
   */
  def parse(input: java.lang.String): Either[SchemaError, Json] = jsonCodec.decode(input)

  /** Parses one JSON value from UTF-8 `input`, as `parse(String)` does. */
  def parse(input: scala.Array[Byte]): Either[SchemaError, Json] = jsonCodec.decode(input)

  /**
   * Parses one JSON value from UTF-8 `input` with the limits and checks of
   * `config`.
   */
  def parse(input: scala.Array[Byte], config: ReaderConfig): Either[SchemaError, Json] = jsonCodec.decode(input, config)

  /**
   * Parses one JSON value from `input` with the limits and checks of `config`.
   */
  def parse(input: java.lang.String, config: ReaderConfig): Either[SchemaError, Json] = jsonCodec.decode(input, config)

  /** Parses one JSON value from the characters of `input`. */
  def parse(input: CharSequence): Either[SchemaError, Json] = jsonCodec.decode(input.toString)

  /** Parses one JSON value from the characters of `input` with `config`. */
  def parse(input: CharSequence, config: ReaderConfig): Either[SchemaError, Json] =
    jsonCodec.decode(input.toString, config)

  /**
   * Parses one JSON value from the UTF-8 bytes of `input`, from its position.
   */
  def parse(input: ByteBuffer): Either[SchemaError, Json] = jsonCodec.decode(input)

  /**
   * Parses one JSON value from the UTF-8 bytes of `input`, from its position,
   * with `config`.
   */
  def parse(input: ByteBuffer, config: ReaderConfig): Either[SchemaError, Json] = jsonCodec.decode(input, config)

  /**
   * Parses one JSON value from UTF-8 `input`, without copying an array-backed
   * chunk.
   */
  def parse(input: Chunk[Byte]): Either[SchemaError, Json] = input match {
    case ba: Chunk.ByteArray =>
      val from = ba.offset
      jsonCodec.decode(ba.array, from, ba.length + from)
    case _ =>
      jsonCodec.decode(input.toArray)
  }

  /** Parses one JSON value from UTF-8 `input` with `config`. */
  def parse(input: Chunk[Byte], config: ReaderConfig): Either[SchemaError, Json] = input match {
    case ba: Chunk.ByteArray =>
      val from = ba.offset
      jsonCodec.decode(ba.array, from, ba.length + from, config)
    case _ =>
      jsonCodec.decode(input.toArray, config)
  }

  /**
   * Parses one JSON value from `input`, as `parse(String)` does.
   *
   * @throws zio.blocks.schema.SchemaError
   *   when the input is not valid JSON
   */
  def parseUnsafe(input: java.lang.String): Json = parse(input) match {
    case Right(json) => json
    case Left(err)   => throw err
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Conversion from DynamicValue
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Converts a [[zio.blocks.schema.DynamicValue]] to JSON: a record becomes an
   * object, a variant `{"<case>": value}`, a sequence an array, and a map an
   * object when every key is a `String`, or else an array of
   * `{"key": ..., "value": ...}` objects. Temporal values become ISO-8601
   * strings and `Unit` becomes `{}`.
   *
   * @throws java.lang.IllegalArgumentException
   *   for a `Float` or `Double` that is NaN or infinite
   */
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
    case v: PrimitiveValue.Char           => new String(java.lang.String.valueOf(v.value))
    case v: PrimitiveValue.BigInt         => Number(v.value)
    case v: PrimitiveValue.BigDecimal     => Number(v.value)
    case v: PrimitiveValue.DayOfWeek      => new String(v.value.toString)
    case v: PrimitiveValue.Duration       => new String(Json.durationRawCodec.encodeToString(v.value))
    case v: PrimitiveValue.Instant        => new String(Json.instantRawCodec.encodeToString(v.value))
    case v: PrimitiveValue.LocalDate      => new String(Json.localDateRawCodec.encodeToString(v.value))
    case v: PrimitiveValue.LocalDateTime  => new String(Json.localDateTimeRawCodec.encodeToString(v.value))
    case v: PrimitiveValue.LocalTime      => new String(Json.localTimeRawCodec.encodeToString(v.value))
    case v: PrimitiveValue.Month          => new String(v.value.toString)
    case v: PrimitiveValue.MonthDay       => new String(Json.monthDayRawCodec.encodeToString(v.value))
    case v: PrimitiveValue.OffsetDateTime => new String(Json.offsetDateTimeRawCodec.encodeToString(v.value))
    case v: PrimitiveValue.OffsetTime     => new String(Json.offsetTimeRawCodec.encodeToString(v.value))
    case v: PrimitiveValue.Period         => new String(Json.periodRawCodec.encodeToString(v.value))
    case v: PrimitiveValue.Year           => new String(v.value.toString)
    case v: PrimitiveValue.YearMonth      => new String(v.value.toString)
    case v: PrimitiveValue.ZoneId         => new String(v.value.getId)
    case v: PrimitiveValue.ZoneOffset     => new String(v.value.getId)
    case v: PrimitiveValue.ZonedDateTime  => new String(Json.zonedDateTimeRawCodec.encodeToString(v.value))
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
  private def merge(path: DynamicOptic, left: Json, right: Json, s: MergeStrategy): Json = {
    left match {
      case lo: Object =>
        right match {
          case ro: Object if s.recurse(path, JsonType.Object) => return mergeByKey(path, lo, ro, s)
          case _                                              =>
        }
      case la: Array =>
        right match {
          case ra: Array if s.recurse(path, JsonType.Array) => return mergeByIndex(path, la, ra, s)
          case _                                            =>
        }
      case _ =>
    }
    s(path, left, right)
  }

  private[this] def mergeByKey(path: DynamicOptic, left: Object, right: Object, s: MergeStrategy): Object = {
    val leftFields  = left.value
    val rightFields = right.value
    val leftLen     = leftFields.length
    val rightLen    = rightFields.length
    val leftSeenAt  = new util.HashMap[java.lang.String, Int](leftLen << 1)
    val rightSeenAt = new util.HashMap[java.lang.String, Int](rightLen << 1)
    val rightDedup  = new scala.Array[(java.lang.String, Json)](rightLen)
    var merged      = new scala.Array[(java.lang.String, Json)](leftLen + rightLen)
    var idx         = 0
    leftFields.foreach { kv =>
      val key = kv._1
      val pos = leftSeenAt.getOrDefault(key, -1)
      if (pos < 0) {
        leftSeenAt.put(key, idx)
        merged(idx) = kv
        idx += 1
      } else merged(pos) = kv
    }
    var rightIdx = 0
    rightFields.foreach { kv =>
      val key = kv._1
      val pos = rightSeenAt.getOrDefault(key, -1)
      if (pos < 0) {
        rightSeenAt.put(key, rightIdx)
        rightDedup(rightIdx) = kv
        rightIdx += 1
      } else rightDedup(pos) = kv
    }
    val rightDedupLen = rightIdx
    rightIdx = 0
    while (rightIdx < rightDedupLen) {
      val kv = rightDedup(rightIdx)
      rightIdx += 1
      val key = kv._1
      val pos = leftSeenAt.getOrDefault(key, -1)
      if (pos < 0) {
        merged(idx) = kv
        idx += 1
      } else merged(pos) = (key, merge(path.field(key), merged(pos)._2, kv._2, s))
    }
    if (merged.length != idx) merged = util.Arrays.copyOf(merged, idx)
    new Object(Chunk.fromArray(merged))
  }

  private[this] def mergeByIndex(path: DynamicOptic, left: Array, right: Array, s: MergeStrategy): Array = {
    val leftElements  = left.value
    val rightElements = right.value
    val leftLen       = leftElements.length
    val rightLen      = rightElements.length
    val merged        = new scala.Array[Json](Math.max(leftLen, rightLen))
    val minLen        = Math.min(leftLen, rightLen)
    var idx           = 0
    while (idx < minLen) {
      merged(idx) = merge(path.at(idx), leftElements(idx), rightElements(idx), s)
      idx += 1
    }
    while (idx < leftLen) {
      merged(idx) = leftElements(idx)
      idx += 1
    }
    while (idx < rightLen) {
      merged(idx) = rightElements(idx)
      idx += 1
    }
    new Array(Chunk.fromArray(merged))
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
        else {
          elems.flatMap {
            var idx = -1
            elem =>
              idx += 1
              toKV(elem, path.at(idx))
          }
        }
      case leaf => Chunk.single((path, leaf))
    }

  // ─────────────────────────────────────────────────────────────────────────
  // KV Construction Methods
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Encodes `value` as a `Json` tree with the JSON codec of `A`'s schema.
   *
   * {{{
   * import zio.blocks.schema.json._
   *
   * case class Book(title: String, year: Int)
   * object Book {
   *   implicit val schema: Schema[Book] = Schema.derived
   * }
   *
   * Json.from(Book("Zionomicon", 2021)).print // {"title":"Zionomicon","year":2021}
   * }}}
   *
   * @throws java.lang.IllegalArgumentException
   *   for a `Float` or `Double` that is NaN or infinite
   * @throws JsonCodecError
   *   when a codec in the tree cannot encode to `Json`, such as a custom codec
   *   that implements only the streaming methods
   */
  def from[A](value: A)(implicit schema: Schema[A]): Json = schema.jsonCodec.encodeValue(value)

  /**
   * Rebuilds a value from path-value pairs, as [[fromKVUnsafe]] does, or
   * returns `Left` when it throws. Paths that disagree do not fail: a later one
   * replaces what is in its way, so `.a` then `[0]` gives an array. Only a
   * negative index fails. No pairs give `Json.Null`.
   */
  def fromKV(kvs: Seq[(DynamicOptic, Json)]): Either[SchemaError, Json] = {
    if (kvs.isEmpty) return new Right(Null)
    try new Right(fromKVUnsafe(kvs))
    catch {
      case e: Exception => new Left(SchemaError(s"Failed to construct JSON from KV pairs: ${e.getMessage}"))
    }
  }

  /**
   * Rebuilds a value from path-value pairs, the inverse of [[Json#toKV]]. Field
   * steps create objects and index steps create arrays padded with nulls; other
   * steps are ignored.
   *
   * @throws java.lang.IndexOutOfBoundsException
   *   for a negative index
   */
  def fromKVUnsafe(kvs: Seq[(DynamicOptic, Json)]): Json = {
    val len = kvs.length
    if (len == 0) Null
    else if (len == 1 && kvs.head._1.nodes.isEmpty) kvs.head._2 // Simple case: single root value
    else kvs.foldLeft[Json](Null)((acc, pv) => setOrCreatePath(acc, pv._1, pv._2))
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
          val keyStrs = new util.HashSet[java.lang.String](keys.size << 1)
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
        case _: DynamicOptic.Node.TypeSearch =>
          return JsonSelection.fail(SchemaError("TypeSearch requires Schema context, not supported for raw JSON"))
        case search: DynamicOptic.Node.SchemaSearch =>
          schemaSearchCollectJson(jsons, search.schemaRepr)
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
      case _: DynamicOptic.Node.TypeSearch =>
        None // TypeSearch requires Schema context
      case search: DynamicOptic.Node.SchemaSearch =>
        schemaSearchModifyJson(json, search.schemaRepr, nodes, nodeIdx, f)
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
          modifyAtPathRecursive(
            json,
            nodes.drop(nodeIdx),
            0,
            x => {
              if (pf.isDefinedAt(x)) pf.apply(x)
              else json
            }
          ) match {
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
            new Some(if (isLast) { // Delete all elements
              Array.empty
            } else { // Apply delete to each element
              new Array(arr.value.flatMap(e => deleteAtPathRecursive(e, nodes, idx + 1)))
            })
          case _ => None
        }
      case _: DynamicOptic.Node.TypeSearch =>
        None // TypeSearch requires Schema context
      case search: DynamicOptic.Node.SchemaSearch =>
        schemaSearchDeleteJson(json, search.schemaRepr, nodes, idx)
      // AtMapKey, AtMapKeys, MapKeys, MapValues, Case: Json has no Map/Variant types, so these are architectural no-ops.
      // AtIndices, Wrapped: could be supported but are low-impact; returns None (safe no-op) for now.
      case _ => None
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

  /**
   * Orders values with [[Json#compare]]: null, booleans, numbers, strings,
   * arrays, objects.
   */
  implicit val ordering: Ordering[Json] = (x: Json, y: Json) => x.compare(y)

  // ─────────────────────────────────────────────────────────────────────────
  // JsonCodec for Json
  // ─────────────────────────────────────────────────────────────────────────

  /** The schema of [[Json.Null]], a record with no fields. */
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

  /** The schema of [[Json.Boolean]], a record with one `value` field. */
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

  /** The schema of [[Json.Number]], a record with one `value` field. */
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

  /** The schema of [[Json.String]], a record with one `value` field. */
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

  /** The schema of [[Json.Array]], a record with one `value` field. */
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

  /**
   * The schema of [[Json.Object]], a record with one `value` field of key-value
   * pairs; its JSON codec writes each field as a `[key, value]` array under
   * `value`.
   */
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

  /**
   * The schema of [[Json]]: a variant of the six cases. Its JSON codec, and the
   * codec of a `Json` field in a derived codec, read and write the value as
   * plain JSON; other formats and `toDynamicValue` see the variant.
   */
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

  /** The codec that reads and writes any JSON value as itself. */
  val jsonCodec: JsonCodec[Json] = new JsonCodec[Json] {
    override def decodeValue(in: JsonReader): Json = {
      var x = in.nextToken().toInt
      if (x == '"') {
        in.rollbackToken()
        new String(in.readString())
      } else if (x == 'f' || x == 't') {
        in.rollbackToken()
        if (in.readBoolean()) Json.True
        else Json.False
      } else if (x >= '0' && x <= '9' || x == '-') {
        in.rollbackToken()
        new Number(in.readBigDecimal())
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
              arr(x) = decodeValue(in)
              x += 1
              errIdx = -1
              in.isNextToken(',')
            }) {
              if (arr.length == x) arr = util.Arrays.copyOf(arr, x << 1)
            }
          } catch {
            case err if NonFatal(err) && errIdx >= 0 => error(new DynamicOptic.Node.AtIndex(errIdx), err)
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
              arr(x) = new Tuple2(key, decodeValue(in))
              x += 1
              key = null
              in.isNextToken(',')
            }) {
              if (arr.length == x) arr = util.Arrays.copyOf(arr, x << 1)
            }
          } catch {
            case err if NonFatal(err) && (key ne null) => error(new DynamicOptic.Node.Field(key), err)
          }
          if (in.isCurrentToken('}')) {
            if (arr.length != x) arr = util.Arrays.copyOf(arr, x)
            new Object(Chunk.fromArray(arr))
          } else in.objectEndOrCommaError()
        }
      } else {
        in.rollbackToken()
        in.readNullOrError(Null, "expected JSON value")
      }
    }

    override def encodeValue(x: Json, out: JsonWriter): Unit = x match {
      case str: String   => out.writeVal(str.value)
      case num: Number   => out.writeVal(num.value)
      case bool: Boolean => out.writeVal(bool.value)
      case arr: Array    =>
        out.writeArrayStart()
        val elems = arr.value
        val len   = elems.length
        var idx   = 0
        while (idx < len) {
          encodeValue(elems(idx), out)
          idx += 1
        }
        out.writeArrayEnd()
      case obj: Object =>
        out.writeObjectStart()
        val kvs = obj.value
        val len = kvs.length
        var idx = 0
        while (idx < len) {
          val kv = kvs(idx)
          out.writeKey(kv._1)
          encodeValue(kv._2, out)
          idx += 1
        }
        out.writeObjectEnd()
      case _ => out.writeNull()
    }

    override def decodeValue(json: Json): Json = json

    override def encodeValue(x: Json): Json = x
  }

  private[schema] val durationRawCodec = new JsonCodec[Duration] {
    override def decodeValue(in: JsonReader): Duration = in.readRawValAsDuration()

    override def encodeValue(x: Duration, out: JsonWriter): Unit = out.writeRawVal(x)
  }
  private[schema] val instantRawCodec = new JsonCodec[Instant] {
    override def decodeValue(in: JsonReader): Instant = in.readRawValAsInstant()

    override def encodeValue(x: Instant, out: JsonWriter): Unit = out.writeRawVal(x)
  }
  private[schema] val localDateRawCodec = new JsonCodec[LocalDate] {
    override def decodeValue(in: JsonReader): LocalDate = in.readRawValAsLocalDate()

    override def encodeValue(x: LocalDate, out: JsonWriter): Unit = out.writeRawVal(x)
  }
  private[schema] val localDateTimeRawCodec = new JsonCodec[LocalDateTime] {
    override def decodeValue(in: JsonReader): LocalDateTime = in.readRawValAsLocalDateTime()

    override def encodeValue(x: LocalDateTime, out: JsonWriter): Unit = out.writeRawVal(x)
  }
  private[schema] val localTimeRawCodec = new JsonCodec[LocalTime] {
    override def decodeValue(in: JsonReader): LocalTime = in.readRawValAsLocalTime()

    override def encodeValue(x: LocalTime, out: JsonWriter): Unit = out.writeRawVal(x)
  }
  private[schema] val monthDayRawCodec = new JsonCodec[MonthDay] {
    override def decodeValue(in: JsonReader): MonthDay = in.readRawValAsMonthDay()

    override def encodeValue(x: MonthDay, out: JsonWriter): Unit = out.writeRawVal(x)
  }
  private[schema] val offsetDateTimeRawCodec = new JsonCodec[OffsetDateTime] {
    override def decodeValue(in: JsonReader): OffsetDateTime = in.readRawValAsOffsetDateTime()

    override def encodeValue(x: OffsetDateTime, out: JsonWriter): Unit = out.writeRawVal(x)
  }
  private[schema] val offsetTimeRawCodec = new JsonCodec[OffsetTime] {
    override def decodeValue(in: JsonReader): OffsetTime = in.readRawValAsOffsetTime()

    override def encodeValue(x: OffsetTime, out: JsonWriter): Unit = out.writeRawVal(x)
  }
  private[schema] val periodRawCodec = new JsonCodec[Period] {
    override def decodeValue(in: JsonReader): Period = in.readRawValAsPeriod()

    override def encodeValue(x: Period, out: JsonWriter): Unit = out.writeRawVal(x)
  }
  private[schema] val zonedDateTimeRawCodec = new JsonCodec[ZonedDateTime] {
    override def decodeValue(in: JsonReader): ZonedDateTime = in.readRawValAsZonedDateTime()

    override def encodeValue(x: ZonedDateTime, out: JsonWriter): Unit = out.writeRawVal(x)
  }

  /**
   * Iterative stack-based depth-first traversal to collect all JSON values
   * matching a SchemaRepr pattern without recursion that could lead to stack
   * overflow. Order is depth-first, left-to-right (children are pushed in
   * reverse order). The root values themselves are included if they match the
   * pattern.
   */
  private[this] def schemaSearchCollectJson(roots: Chunk[Json], pattern: SchemaRepr): Chunk[Json] = {
    var stack = roots.toList
    val jsons = ChunkBuilder.make[Json]()
    while (stack.nonEmpty) {
      val current = stack.head
      stack = stack.tail
      if (JsonMatch.matches(pattern, current)) jsons.addOne(current)
      current match {
        case obj: Object => stack = obj.value.foldRight(stack)(_._2 :: _)
        case arr: Array  => stack = arr.value.foldRight(stack)(_ :: _)
        case _           =>
      }
    }
    jsons.result()
  }

  /**
   * Iterative pre-order tree transformation for Json. Applies `visit` to each
   * node, then reconstructs the tree bottom-up using an explicit stack to avoid
   * stack overflow on deeply nested structures.
   */
  private[json] def iterativeTransform(root: Json)(visit: Json => Json): Json = {
    sealed trait Frame

    final class Visit(val value: Json) extends Frame

    final class RebuildObject(val original: Object, val childCount: Int) extends Frame

    final class RebuildArray(val original: Array, val childCount: Int) extends Frame

    val work    = new java.util.ArrayDeque[Frame]()
    val results = new java.util.ArrayDeque[Json]()
    work.push(new Visit(root))
    while (!work.isEmpty) {
      work.pop() match {
        case v: Visit =>
          val visited = visit(v.value)
          visited match {
            case obj: Object =>
              val len = obj.value.length
              if (len == 0) results.push(visited)
              else {
                work.push(new RebuildObject(obj, len))
                var idx = len
                while ({
                  idx -= 1
                  idx >= 0
                }) work.push(new Visit(obj.value(idx)._2))
              }
            case arr: Array =>
              val len = arr.value.length
              if (len == 0) results.push(visited)
              else {
                work.push(new RebuildArray(arr, len))
                var idx = len
                while ({
                  idx -= 1
                  idx >= 0
                }) work.push(new Visit(arr.value(idx)))
              }
            case _ => results.push(visited)
          }
        case ro: RebuildObject =>
          val original   = ro.original
          val childCount = ro.childCount
          val fields     = new scala.Array[(java.lang.String, Json)](childCount)
          var changed    = false
          var idx        = childCount
          while ({
            idx -= 1
            idx >= 0
          }) {
            val child = results.pop()
            val value = original.value(idx)
            if (child ne value._2) changed = true
            fields(idx) = (value._1, child)
          }
          results.push(
            if (changed) new Object(Chunk.fromArray(fields))
            else original
          )
        case ra: RebuildArray =>
          val original   = ra.original
          val childCount = ra.childCount
          val elems      = new scala.Array[Json](childCount)
          var changed    = false
          var idx        = childCount
          while ({
            idx -= 1
            idx >= 0
          }) {
            val child = results.pop()
            if (child ne original.value(idx)) changed = true
            elems(idx) = child
          }
          results.push(
            if (changed) new Array(Chunk.fromArray(elems))
            else original
          )
      }
    }
    results.pop()
  }

  /**
   * Modifies all JSON values matching a SchemaRepr pattern and continues with
   * remaining path nodes. Uses iterative stack-based traversal to avoid stack
   * overflow on deeply nested structures.
   */
  private[this] def schemaSearchModifyJson(
    json: Json,
    pattern: SchemaRepr,
    nodes: IndexedSeq[DynamicOptic.Node],
    nodeIdx: Int,
    f: Json => Json
  ): Option[Json] = {
    var found  = false
    val result = iterativeTransform(json) { value =>
      if (JsonMatch.matches(pattern, value)) {
        modifyAtPathRecursive(value, nodes, nodeIdx + 1, f) match {
          case Some(modified) =>
            found = true
            modified
          case _ => value
        }
      } else value
    }
    if (found) new Some(result)
    else None
  }

  /**
   * Deletes all JSON values matching a SchemaRepr pattern and continues with
   * remaining path nodes. Uses iterative stack-based traversal to avoid stack
   * overflow on deeply nested structures.
   */
  private def schemaSearchDeleteJson(
    json: Json,
    pattern: SchemaRepr,
    nodes: IndexedSeq[DynamicOptic.Node],
    nodeIdx: Int
  ): Option[Json] = {
    val isLast = nodeIdx == nodes.length - 1
    var found  = false
    if (isLast) {
      // SchemaSearch is the last node - delete matching values from containers.
      // The visit function filters out matching direct children at each container level;
      // iterativeTransform handles recursion into remaining children.
      val result = iterativeTransform(json) {
        case obj: Object =>
          val fields    = obj.value
          val newFields = ChunkBuilder.make[(java.lang.String, Json)](fields.length)
          fields.foreach { kv =>
            if (JsonMatch.matches(pattern, kv._2)) found = true
            else newFields.addOne(kv)
          }
          if (newFields.knownSize != fields.length) new Object(newFields.result())
          else obj
        case arr: Array =>
          val elems    = arr.value
          val newElems = ChunkBuilder.make[Json](elems.length)
          elems.foreach { e =>
            if (JsonMatch.matches(pattern, e)) found = true
            else newElems.addOne(e)
          }
          if (newElems.knownSize != elems.length) new Array(newElems.result())
          else arr
        case other => other
      }
      if (found) new Some(result)
      else None
    } else {
      // SchemaSearch is not the last node - find matches and continue with a remaining path.
      // iterativeTransform visits every node; matching ones get the remaining path applied.
      val result = iterativeTransform(json) { value =>
        if (JsonMatch.matches(pattern, value)) {
          deleteAtPathRecursive(value, nodes, nodeIdx + 1) match {
            case Some(modified) =>
              found = true
              modified
            case _ => value
          }
        } else value
      }
      if (found) new Some(result)
      else None
    }
  }
}
