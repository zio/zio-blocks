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

package zio.blocks.schema

import zio.blocks.chunk.{Chunk, ChunkMap}
import zio.blocks.docs.Doc
import zio.blocks.maybe.Maybe
import zio.blocks.schema.binding.Binding
import zio.blocks.schema.derive.{Derivable, DerivationBuilder, Deriver}
import zio.blocks.typeid.TypeId
import zio.blocks.schema.json.{Json, JsonCodec, JsonFormat, JsonSchema, JsonSchemaToReflect}
import zio.blocks.schema.patch.{Patch, PatchMode}

import java.util.concurrent.ConcurrentHashMap

/**
 * A `Schema[A]` describes the structure of the type `A`, such as the fields of
 * a case class or the cases of a sealed trait, together with the functions that
 * build and take apart values of `A`. Derive one with `Schema.derived`, then
 * use it to get codecs for formats such as JSON, convert values to and from
 * [[DynamicValue]], compute and apply patches, and attach documentation,
 * examples and default values.
 *
 * A schema is an immutable value: methods such as `doc`, `defaultValue` and
 * `modifier` return a new schema. It wraps a `Reflect.Bound[A]`, which holds
 * the structure and its bindings.
 *
 * {{{
 * case class Book(title: String, year: Int)
 * object Book {
 *   implicit val schema: Schema[Book] = Schema.derived
 * }
 *
 * val json = Schema[Book].jsonCodec.encodeToString(Book("Zionomicon", 2021))
 * // {"title":"Zionomicon","year":2021}
 * val value = Schema[Book].toDynamicValue(Book("Zionomicon", 2021))
 * // {
 * //   title: "Zionomicon",
 * //   year: 2021
 * // }
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/schema/ Schema]]
 */
final case class Schema[A](reflect: Reflect.Bound[A]) extends SchemaVersionSpecific[A] {
  private[this] val cache: ConcurrentHashMap[codec.Format, ?] = new ConcurrentHashMap

  /**
   * The JSON codec of `A`, derived on first use with the default JSON deriver
   * and cached. It is the instance that `getInstance(JsonFormat)` returns.
   *
   * Derivation reads only the schema, so this codec ignores an implicit
   * `JsonCodec` in scope and the overrides of a configured deriver, such as
   * `JsonCodecDeriver.withInstance`. To use a configured deriver, derive the
   * codec with `derive` or `deriving` and call that codec directly: the
   * `toJsonString` and `fromJson` syntax always uses this one.
   *
   * {{{
   * case class Book(title: String, year: Int)
   * object Book {
   *   implicit val schema: Schema[Book] = Schema.derived
   * }
   *
   * val json = Book.schema.jsonCodec.encodeToString(Book("Zionomicon", 2021))
   * // {"title":"Zionomicon","year":2021}
   * val book = Book.schema.jsonCodec.decode("""{"title":"Zionomicon","year":2021}""")
   * // Right(Book(Zionomicon,2021))
   * }}}
   *
   * @see
   *   [[https://zioblocks.com/reference/schema/codec/ Codec]]
   */
  lazy val jsonCodec: JsonCodec[A] = getInstance(JsonFormat)

  /**
   * Returns the codec of `format` for `A`. The first call derives it with the
   * format's default deriver, `format.deriver`; later calls with the same
   * format return the cached instance.
   *
   * The cache belongs to this `Schema` value, so a schema returned by `doc`,
   * `modifier` or any other update derives its codec again.
   *
   * {{{
   * import zio.blocks.schema.json.{JsonCodec, JsonFormat}
   *
   * case class Book(title: String, year: Int)
   * object Book {
   *   implicit val schema: Schema[Book] = Schema.derived
   * }
   *
   * val codec: JsonCodec[Book] = Book.schema.getInstance(JsonFormat)
   * val cached                 = codec eq Book.schema.getInstance(JsonFormat) // true
   * }}}
   */
  def getInstance[F <: codec.Format](format: F): format.TypeClass[A] =
    cache
      .asInstanceOf[ConcurrentHashMap[codec.Format, format.TypeClass[A]]]
      .computeIfAbsent(format, _ => deriving(format.deriver).derive)

  /**
   * Returns the default value of `A` set with `defaultValue`, or `None` when
   * there is none. A derived schema stores the defaults of case class fields on
   * the fields, not on the record: read them with the overload that takes an
   * optic.
   */
  def getDefaultValue: Option[A] = reflect.getDefaultValue

  /**
   * Returns the default value of the part of `A` that `optic` focuses on, such
   * as the default of a case class field, or `None` when the optic does not
   * resolve or the part has no default.
   */
  def getDefaultValue[B](optic: Optic[A, B]): Option[B] = get(optic).flatMap(_.getDefaultValue)

  /**
   * Returns a schema in which the part of `A` that `optic` focuses on has the
   * default value `value`. When the optic does not resolve against this schema,
   * this schema is returned unchanged.
   *
   * {{{
   * case class Book(title: String, year: Int = 2021)
   * object Book extends CompanionOptics[Book] {
   *   implicit val schema: Schema[Book] = Schema.derived
   *   val year: Lens[Book, Int]         = $(_.year)
   * }
   *
   * val declared = Book.schema.getDefaultValue(Book.year)                              // Some(2021)
   * val updated  = Book.schema.defaultValue(Book.year, 2024).getDefaultValue(Book.year) // Some(2024)
   * }}}
   *
   * @see
   *   [[https://zioblocks.com/reference/schema/schema/ Schema]]
   */
  def defaultValue[B](optic: Optic[A, B], value: => B): Schema[A] =
    updated(optic)(_.defaultValue(value)).getOrElse(this)

  /**
   * Returns a schema whose default value for `A` is `value`, the value that
   * `getDefaultValue` then returns.
   */
  def defaultValue(value: => A): Schema[A] = new Schema(reflect.defaultValue(value))

  /**
   * Derives an instance of the type class `TC` for `A` with `d`: a
   * `Deriver[TC]`, or a format such as `JsonFormat`, whose default deriver is
   * then used. Unlike `getInstance`, each call derives a new instance, so keep
   * the result, for example in an implicit `val`.
   *
   * {{{
   * import zio.blocks.schema.json.{JsonCodec, JsonCodecDeriver, JsonFormat}
   *
   * case class Book(title: String, publishedIn: Int)
   * object Book {
   *   implicit val schema: Schema[Book] = Schema.derived
   * }
   *
   * val default: JsonCodec[Book]   = Book.schema.derive(JsonFormat)
   * val snakeCase: JsonCodec[Book] = Book.schema.derive(JsonCodecDeriver.withFieldNameMapper(NameMapper.SnakeCase))
   *
   * val json = snakeCase.encodeToString(Book("Zionomicon", 2021))
   * // {"title":"Zionomicon","published_in":2021}
   * }}}
   *
   * @see
   *   [[https://zioblocks.com/reference/schema/type-class-derivation/ Type-Class Derivation]]
   */
  def derive[D, TC[_]](d: D)(implicit ev: Derivable[D, TC]): TC[A] = deriving(ev.deriver(d)).derive

  /**
   * Starts a derivation of `TC` for `A` with `deriver`, which you can customize
   * before calling `derive` on the result: override the instance used for a
   * field, a case or a type, or add modifiers, without changing this schema.
   * The result is not cached.
   *
   * When `deriver` already has a `withInstance` override for a type, that
   * override wins over one given for the same type with
   * `DerivationBuilder#instance`.
   *
   * {{{
   * import zio.blocks.schema.json.JsonCodecDeriver
   *
   * case class Book(title: String, year: Int)
   * object Book extends CompanionOptics[Book] {
   *   implicit val schema: Schema[Book] = Schema.derived
   *   val title: Lens[Book, String]     = $(_.title)
   * }
   *
   * val codec = Book.schema
   *   .deriving(JsonCodecDeriver)
   *   .modifier(Book.title, Modifier.rename("name"))
   *   .derive
   *
   * val json = codec.encodeToString(Book("Zionomicon", 2021)) // {"name":"Zionomicon","year":2021}
   * }}}
   *
   * @see
   *   [[https://zioblocks.com/reference/schema/type-class-derivation/ Type-Class Derivation]]
   */
  def deriving[TC[_]](deriver: Deriver[TC]): DerivationBuilder[TC, A] =
    new DerivationBuilder[TC, A](this, deriver, Chunk.empty, Chunk.empty)

  /**
   * Decodes a value of `A` from `decodeInput` with the codec of `format`, the
   * one `getInstance` returns, and returns `Left` when the input does not
   * decode.
   *
   * {{{
   * import java.nio.ByteBuffer
   * import java.nio.charset.StandardCharsets
   * import zio.blocks.schema.json.JsonFormat
   *
   * case class Book(title: String, year: Int)
   * object Book {
   *   implicit val schema: Schema[Book] = Schema.derived
   * }
   *
   * val input = ByteBuffer.wrap("""{"title":"Zionomicon","year":2021}""".getBytes(StandardCharsets.UTF_8))
   * val book  = Book.schema.decode(JsonFormat)(input) // Right(Book(Zionomicon,2021))
   * }}}
   *
   * @see
   *   [[https://zioblocks.com/reference/schema/schema/ Schema]]
   */
  def decode[F <: codec.Format](format: F)(decodeInput: format.DecodeInput): Either[SchemaError, A] =
    getInstance(format).decode(decodeInput)

  /**
   * Returns the documentation of `A` stored in this schema, or `Doc.empty` when
   * there is none.
   */
  def doc: Doc = reflect.doc

  /**
   * Returns a schema whose documentation is `value`, replacing any
   * documentation it had.
   *
   * {{{
   * case class Book(title: String, year: Int)
   * object Book extends CompanionOptics[Book] {
   *   implicit val schema: Schema[Book] = Schema.derived
   *   val title: Lens[Book, String]     = $(_.title)
   * }
   *
   * val documented = Book.schema
   *   .doc("A book in the catalogue")
   *   .doc(Book.title, "The title on the cover")
   *
   * val bookDoc  = documented.doc.toString.trim             // A book in the catalogue
   * val titleDoc = documented.doc(Book.title).toString.trim // The title on the cover
   * }}}
   *
   * @see
   *   [[https://zioblocks.com/reference/schema/schema/ Schema]]
   */
  def doc(value: String): Schema[A] = new Schema(reflect.doc(value))

  /**
   * Returns the documentation of the part of `A` that `optic` focuses on, or
   * `Doc.empty` when the optic does not resolve or the part has none.
   */
  def doc[B](optic: Optic[A, B]): Doc = get(optic).fold[Doc](Doc.empty)(_.doc)

  /**
   * Returns a schema in which the part of `A` that `optic` focuses on has the
   * documentation `value`. When the optic does not resolve against this schema,
   * this schema is returned unchanged.
   */
  def doc[B](optic: Optic[A, B], value: String): Schema[A] = updated(optic)(_.doc(value)).getOrElse(this)

  /**
   * Encodes `value` into `output` with the codec of `format`, the one
   * `getInstance` returns.
   *
   * {{{
   * import java.nio.ByteBuffer
   * import java.nio.charset.StandardCharsets
   * import zio.blocks.schema.json.JsonFormat
   *
   * case class Book(title: String, year: Int)
   * object Book {
   *   implicit val schema: Schema[Book] = Schema.derived
   * }
   *
   * val buffer = ByteBuffer.allocate(256)
   * Book.schema.encode(JsonFormat)(buffer)(Book("Zionomicon", 2021))
   * buffer.flip()
   * val json = StandardCharsets.UTF_8.decode(buffer).toString // {"title":"Zionomicon","year":2021}
   * }}}
   *
   * @see
   *   [[https://zioblocks.com/reference/schema/schema/ Schema]]
   */
  def encode[F <: codec.Format](format: F)(output: format.EncodeOutput)(value: A): Unit =
    getInstance(format).encode(value, output)

  /** Returns the example values of `A` stored in this schema. */
  def examples: Seq[A] = reflect.examples

  /**
   * Returns a schema whose examples are `value` and `values`, replacing any
   * examples it had.
   */
  def examples(value: A, values: A*): Schema[A] = new Schema(reflect.examples(value, values: _*))

  /**
   * Returns the examples of the part of `A` that `optic` focuses on, or an
   * empty `Seq` when the optic does not resolve.
   */
  def examples[B](optic: Optic[A, B]): Seq[B] = get(optic).fold[Seq[B]](Nil)(_.examples)

  /**
   * Returns a schema in which the part of `A` that `optic` focuses on has the
   * examples `value` and `values`. When the optic does not resolve against this
   * schema, this schema is returned unchanged.
   */
  def examples[B](optic: Optic[A, B], value: B, values: B*): Schema[A] =
    updated(optic)(_.examples(value, values: _*)).getOrElse(this)

  /**
   * Converts `value` to a value of `A`. Returns `Left` when `value` does not
   * match this schema, with the failures of all record fields collected in one
   * [[SchemaError]], or when a `transform` function throws.
   */
  def fromDynamicValue(value: DynamicValue): Either[SchemaError, A] = reflect.fromDynamicValue(value, Nil)

  /**
   * Returns the structure of the part of `A` that `optic` focuses on, or `None`
   * when the optic does not resolve against this schema.
   */
  def get[B](optic: Optic[A, B]): Option[Reflect.Bound[B]] = reflect.get(optic)

  /**
   * Returns the structure of the part of `A` at the path `dynamic`, or `None`
   * when the path does not resolve against this schema.
   */
  def get(dynamic: DynamicOptic): Option[Reflect.Bound[?]] = reflect.get(dynamic)

  /**
   * Converts `value` to a [[DynamicValue]] that follows this schema. An
   * exception thrown by the `from` function of a `transform` on the way
   * propagates to the caller.
   */
  def toDynamicValue(value: A): DynamicValue = reflect.toDynamicValue(value)

  /**
   * Converts this schema to a [[DynamicSchema]] by dropping its bindings. The
   * result keeps the structure (fields, cases, types and `Validation`
   * constraints) but cannot build values of `A`; use it to check
   * [[DynamicValue]] instances against the structure.
   *
   * Its `check` does not run the checks of `transform` functions, because they
   * live in the bindings it drops.
   */
  def toDynamicSchema: DynamicSchema = new DynamicSchema(reflect.noBinding)

  /**
   * Derives a JSON Schema from this schema. `Validation` constraints are not
   * included in it.
   */
  def toJsonSchema: JsonSchema = deriving(JsonFormat.deriver).derive.toJsonSchema

  /**
   * Returns a schema in which `f` has rewritten the part of `A` at the path
   * `dynamic`, or `None` when the path does not resolve against this schema.
   */
  def updated(dynamic: DynamicOptic)(f: Reflect.Updater[Binding]): Option[Schema[A]] =
    reflect.updated(dynamic)(f).map(x => new Schema(x))

  /**
   * Returns a schema in which `f` has rewritten the part of `A` that `optic`
   * focuses on, or `None` when the optic does not resolve against this schema.
   */
  def updated[B](optic: Optic[A, B])(f: Reflect.Bound[B] => Reflect.Bound[B]): Option[Schema[A]] =
    reflect.updated(optic)(f).map(x => new Schema(x))

  /** Returns a schema with `aspect` applied to the whole structure of `A`. */
  def @@[Min >: A, Max <: A](aspect: SchemaAspect[Min, Max, Binding]): Schema[A] = new Schema(reflect.aspect(aspect))

  /**
   * Returns a schema with `aspect` applied to the part of `A` that `part`
   * focuses on. When the optic does not resolve against this schema, this
   * schema is returned unchanged.
   */
  def @@[B](part: Optic[A, B], aspect: SchemaAspect[B, B, Binding]) = new Schema(reflect.aspect(part, aspect))

  /**
   * Returns a schema with `modifier` added after the modifiers that `A` already
   * has.
   */
  def modifier(modifier: Modifier.Reflect): Schema[A] = new Schema(reflect.modifier(modifier))

  /**
   * Returns a schema with `modifiers` added after the modifiers that `A`
   * already has.
   */
  def modifiers(modifiers: Iterable[Modifier.Reflect]): Schema[A] = new Schema(reflect.modifiers(modifiers))

  /**
   * Computes a patch that turns `oldValue` into `newValue`, by comparing the
   * [[DynamicValue]] forms of the two values.
   */
  def diff(oldValue: A, newValue: A): Patch[A] =
    new Patch(toDynamicValue(oldValue).diff(toDynamicValue(newValue)), this)

  /**
   * Applies `patch` to `value` in strict mode: returns `Left` when an operation
   * of the patch cannot be applied, or when the result does not convert back to
   * `A`.
   */
  def patch(value: A, patch: Patch[A]): Either[SchemaError, A] = patch.apply(value, PatchMode.Strict)

  /**
   * Returns a schema for `B` that represents each `B` as the `A` that `from`
   * returns. Use it for wrapper types, newtypes and validated types. The
   * implicit `TypeId[B]` gives the new schema its type name.
   *
   * `to` runs when a value is decoded, by a codec or by `fromDynamicValue`, and
   * may throw to reject the value, for example with
   * `SchemaError.validationFailed`. The decoder then returns `Left` with the
   * message of the exception at a path that ends in `.wrapped`, the step into
   * the wrapped `A`. `from` runs when a value is encoded or converted with
   * `toDynamicValue`, and neither catches an exception it throws:
   * `toDynamicValue` rethrows it unchanged, and the JSON codec throws it as a
   * `JsonCodecError` that keeps its message.
   *
   * `DynamicSchema#check` does not run the checks in `to`.
   *
   * {{{
   * final case class PageCount(value: Int)
   * object PageCount {
   *   implicit val schema: Schema[PageCount] = Schema[Int].transform(
   *     n => if (n > 0) PageCount(n) else throw SchemaError.validationFailed("page count must be positive, got " + n),
   *     _.value
   *   )
   * }
   *
   * val pages   = "816".fromJson[PageCount] // Right(PageCount(816))
   * val invalid = "-5".fromJson[PageCount]
   * // Left(zio.blocks.schema.SchemaError: page count must be positive, got -5 at: .wrapped)
   * }}}
   *
   * @see
   *   [[https://zioblocks.com/reference/schema/schema/ Schema]]
   */
  def transform[B](to: A => B, from: B => A)(implicit typeId: TypeId[B]): Schema[B] =
    new Schema(new Reflect.Wrapper[Binding, B, A](reflect, typeId, new Binding.Wrapper(to, from)))

  override def toString: String = {
    val reflectStr = reflect.toString
    if (reflectStr.contains('\n')) s"Schema {\n  ${reflectStr.replace("\n", "\n  ")}\n}"
    else s"Schema {\n  $reflectStr\n}"
  }
}

/**
 * Derivation with `derived`, the built-in schemas of standard types
 * (primitives, `java.time` types, `UUID`, `Currency`, options, collections,
 * `Either` and [[DynamicValue]]), and `fromJsonSchema`.
 */
object Schema extends SchemaCompanionVersionSpecific with TypeIdSchemas with DocsSchemas {

  /** Returns the implicit schema of `A`, as in `Schema[Book]`. */
  def apply[A](implicit schema: Schema[A]): Schema[A] = schema

  /** The schema of [[DynamicValue]], which accepts any dynamic value. */
  implicit val dynamic: Schema[DynamicValue] = new Schema(Reflect.dynamic[Binding])

  /** The schema of `Unit`. */
  implicit val unit: Schema[Unit] = new Schema(Reflect.unit[Binding])

  /** The schema of `Boolean`. */
  implicit val boolean: Schema[Boolean] = new Schema(Reflect.boolean[Binding])

  /** The schema of `Byte`. */
  implicit val byte: Schema[Byte] = new Schema(Reflect.byte[Binding])

  /** The schema of `Short`. */
  implicit val short: Schema[Short] = new Schema(Reflect.short[Binding])

  /** The schema of `Int`. */
  implicit val int: Schema[Int] = new Schema(Reflect.int[Binding])

  /** The schema of `Long`. */
  implicit val long: Schema[Long] = new Schema(Reflect.long[Binding])

  /** The schema of `Float`. */
  implicit val float: Schema[Float] = new Schema(Reflect.float[Binding])

  /** The schema of `Double`. */
  implicit val double: Schema[Double] = new Schema(Reflect.double[Binding])

  /** The schema of `Char`. */
  implicit val char: Schema[Char] = new Schema(Reflect.char[Binding])

  /** The schema of `String`. */
  implicit val string: Schema[String] = new Schema(Reflect.string[Binding])

  /** The schema of `BigInt`. */
  implicit val bigInt: Schema[BigInt] = new Schema(Reflect.bigInt[Binding])

  /** The schema of `BigDecimal`. */
  implicit val bigDecimal: Schema[BigDecimal] = new Schema(Reflect.bigDecimal[Binding])

  /** The schema of `java.time.DayOfWeek`. */
  implicit val dayOfWeek: Schema[java.time.DayOfWeek] = new Schema(Reflect.dayOfWeek[Binding])

  /** The schema of `java.time.Duration`. */
  implicit val duration: Schema[java.time.Duration] = new Schema(Reflect.duration[Binding])

  /** The schema of `java.time.Instant`. */
  implicit val instant: Schema[java.time.Instant] = new Schema(Reflect.instant[Binding])

  /** The schema of `java.time.LocalDate`. */
  implicit val localDate: Schema[java.time.LocalDate] = new Schema(Reflect.localDate[Binding])

  /** The schema of `java.time.LocalDateTime`. */
  implicit val localDateTime: Schema[java.time.LocalDateTime] = new Schema(Reflect.localDateTime[Binding])

  /** The schema of `java.time.LocalTime`. */
  implicit val localTime: Schema[java.time.LocalTime] = new Schema(Reflect.localTime[Binding])

  /** The schema of `java.time.Month`. */
  implicit val month: Schema[java.time.Month] = new Schema(Reflect.month[Binding])

  /** The schema of `java.time.MonthDay`. */
  implicit val monthDay: Schema[java.time.MonthDay] = new Schema(Reflect.monthDay[Binding])

  /** The schema of `java.time.OffsetDateTime`. */
  implicit val offsetDateTime: Schema[java.time.OffsetDateTime] = new Schema(Reflect.offsetDateTime[Binding])

  /** The schema of `java.time.OffsetTime`. */
  implicit val offsetTime: Schema[java.time.OffsetTime] = new Schema(Reflect.offsetTime[Binding])

  /** The schema of `java.time.Period`. */
  implicit val period: Schema[java.time.Period] = new Schema(Reflect.period[Binding])

  /** The schema of `java.time.Year`. */
  implicit val year: Schema[java.time.Year] = new Schema(Reflect.year[Binding])

  /** The schema of `java.time.YearMonth`. */
  implicit val yearMonth: Schema[java.time.YearMonth] = new Schema(Reflect.yearMonth[Binding])

  /** The schema of `java.time.ZoneId`. */
  implicit val zoneId: Schema[java.time.ZoneId] = new Schema(Reflect.zoneId[Binding])

  /** The schema of `java.time.ZoneOffset`. */
  implicit val zoneOffset: Schema[java.time.ZoneOffset] = new Schema(Reflect.zoneOffset[Binding])

  /** The schema of `java.time.ZonedDateTime`. */
  implicit val zonedDateTime: Schema[java.time.ZonedDateTime] = new Schema(Reflect.zonedDateTime[Binding])

  /** The schema of `java.util.Currency`. */
  implicit val currency: Schema[java.util.Currency] = new Schema(Reflect.currency[Binding])

  /** The schema of `java.util.UUID`. */
  implicit val uuid: Schema[java.util.UUID] = new Schema(Reflect.uuid[Binding])

  /**
   * The schema of `Option[A]` for a reference type `A`: a variant with the
   * cases `None` and `Some`.
   *
   * Options of primitive types have their own instances, such as `optionInt`.
   */
  implicit def option[A <: AnyRef](implicit element: Schema[A]): Schema[Option[A]] =
    new Schema(Reflect.option(element.reflect))

  /**
   * The schema of `Option[Double]`, with a `Some` binding specialized for
   * `Double`.
   */
  implicit val optionDouble: Schema[Option[Double]] = new Schema(Reflect.optionDouble(Schema[Double].reflect))

  /**
   * The schema of `Option[Long]`, with a `Some` binding specialized for `Long`.
   */
  implicit val optionLong: Schema[Option[Long]] = new Schema(Reflect.optionLong(Schema[Long].reflect))

  /**
   * The schema of `Option[Float]`, with a `Some` binding specialized for
   * `Float`.
   */
  implicit val optionFloat: Schema[Option[Float]] = new Schema(Reflect.optionFloat(Schema[Float].reflect))

  /**
   * The schema of `Option[Int]`, with a `Some` binding specialized for `Int`.
   */
  implicit val optionInt: Schema[Option[Int]] = new Schema(Reflect.optionInt(Schema[Int].reflect))

  /**
   * The schema of `Option[Char]`, with a `Some` binding specialized for `Char`.
   */
  implicit val optionChar: Schema[Option[Char]] = new Schema(Reflect.optionChar(Schema[Char].reflect))

  /**
   * The schema of `Option[Short]`, with a `Some` binding specialized for
   * `Short`.
   */
  implicit val optionShort: Schema[Option[Short]] = new Schema(Reflect.optionShort(Schema[Short].reflect))

  /**
   * The schema of `Option[Boolean]`, with a `Some` binding specialized for
   * `Boolean`.
   */
  implicit val optionBoolean: Schema[Option[Boolean]] = new Schema(Reflect.optionBoolean(Schema[Boolean].reflect))

  /**
   * The schema of `Option[Byte]`, with a `Some` binding specialized for `Byte`.
   */
  implicit val optionByte: Schema[Option[Byte]] = new Schema(Reflect.optionByte(Schema[Byte].reflect))

  /**
   * The schema of `Option[Unit]`, with a `Some` binding specialized for `Unit`.
   */
  implicit val optionUnit: Schema[Option[Unit]] = new Schema(Reflect.optionUnit(Schema[Unit].reflect))

  /**
   * The schema of `Maybe[A]` for a reference type `A`: a variant with the cases
   * `Absent` and `Present`.
   *
   * Maybes of primitive types have their own instances, such as `maybeInt`.
   */
  implicit def maybe[A <: AnyRef](implicit element: Schema[A]): Schema[Maybe[A]] =
    new Schema(Reflect.maybe(element.reflect).asInstanceOf[Reflect.Bound[Maybe[A]]])

  /**
   * The schema of `Maybe[Double]`, with a `Present` binding specialized for
   * `Double`.
   */
  implicit val maybeDouble: Schema[Maybe[Double]] =
    new Schema(Reflect.maybeDouble(Schema[Double].reflect).asInstanceOf[Reflect.Bound[Maybe[Double]]])

  /**
   * The schema of `Maybe[Long]`, with a `Present` binding specialized for
   * `Long`.
   */
  implicit val maybeLong: Schema[Maybe[Long]] =
    new Schema(Reflect.maybeLong(Schema[Long].reflect).asInstanceOf[Reflect.Bound[Maybe[Long]]])

  /**
   * The schema of `Maybe[Float]`, with a `Present` binding specialized for
   * `Float`.
   */
  implicit val maybeFloat: Schema[Maybe[Float]] =
    new Schema(Reflect.maybeFloat(Schema[Float].reflect).asInstanceOf[Reflect.Bound[Maybe[Float]]])

  /**
   * The schema of `Maybe[Int]`, with a `Present` binding specialized for `Int`.
   */
  implicit val maybeInt: Schema[Maybe[Int]] =
    new Schema(Reflect.maybeInt(Schema[Int].reflect).asInstanceOf[Reflect.Bound[Maybe[Int]]])

  /**
   * The schema of `Maybe[Char]`, with a `Present` binding specialized for
   * `Char`.
   */
  implicit val maybeChar: Schema[Maybe[Char]] =
    new Schema(Reflect.maybeChar(Schema[Char].reflect).asInstanceOf[Reflect.Bound[Maybe[Char]]])

  /**
   * The schema of `Maybe[Short]`, with a `Present` binding specialized for
   * `Short`.
   */
  implicit val maybeShort: Schema[Maybe[Short]] =
    new Schema(Reflect.maybeShort(Schema[Short].reflect).asInstanceOf[Reflect.Bound[Maybe[Short]]])

  /**
   * The schema of `Maybe[Boolean]`, with a `Present` binding specialized for
   * `Boolean`.
   */
  implicit val maybeBoolean: Schema[Maybe[Boolean]] =
    new Schema(Reflect.maybeBoolean(Schema[Boolean].reflect).asInstanceOf[Reflect.Bound[Maybe[Boolean]]])

  /**
   * The schema of `Maybe[Byte]`, with a `Present` binding specialized for
   * `Byte`.
   */
  implicit val maybeByte: Schema[Maybe[Byte]] =
    new Schema(Reflect.maybeByte(Schema[Byte].reflect).asInstanceOf[Reflect.Bound[Maybe[Byte]]])

  /**
   * The schema of `Maybe[Unit]`, with a `Present` binding specialized for
   * `Unit`.
   */
  implicit val maybeUnit: Schema[Maybe[Unit]] =
    new Schema(Reflect.maybeUnit(Schema[Unit].reflect).asInstanceOf[Reflect.Bound[Maybe[Unit]]])

  /** The schema of `Set[A]`, built from the schema of its elements. */
  implicit def set[A](implicit element: Schema[A]): Schema[Set[A]] = new Schema(Reflect.set(element.reflect))

  /** The schema of `List[A]`, built from the schema of its elements. */
  implicit def list[A](implicit element: Schema[A]): Schema[List[A]] = new Schema(Reflect.list(element.reflect))

  /** The schema of `Vector[A]`, built from the schema of its elements. */
  implicit def vector[A](implicit element: Schema[A]): Schema[Vector[A]] = new Schema(Reflect.vector(element.reflect))

  /** The schema of `IndexedSeq[A]`, built from the schema of its elements. */
  implicit def indexedSeq[A](implicit element: Schema[A]): Schema[IndexedSeq[A]] =
    new Schema(Reflect.indexedSeq(element.reflect))

  /** The schema of `Seq[A]`, built from the schema of its elements. */
  implicit def seq[A](implicit element: Schema[A]): Schema[Seq[A]] = new Schema(Reflect.seq(element.reflect))

  /** The schema of `Chunk[A]`, built from the schema of its elements. */
  implicit def chunk[A](implicit element: Schema[A]): Schema[Chunk[A]] = new Schema(Reflect.chunk(element.reflect))

  /**
   * The schema of an immutable `Map[A, B]`, built from the schemas of its keys
   * and values.
   */
  implicit def map[A, B](implicit key: Schema[A], value: Schema[B]): Schema[collection.immutable.Map[A, B]] =
    new Schema(Reflect.map(key.reflect, value.reflect))

  /**
   * The schema of `ChunkMap[A, B]`, built from the schemas of its keys and
   * values.
   */
  implicit def chunkMap[A, B](implicit key: Schema[A], value: Schema[B]): Schema[ChunkMap[A, B]] =
    new Schema(Reflect.chunkMap(key.reflect, value.reflect))

  /**
   * Provides a schema for `Either[A, B]` when schemas for both value types are
   * available.
   *
   * Primitive values and primitive-backed wrappers retain their specialized
   * register layouts in both branches.
   *
   * @param left
   *   the schema for left values
   * @param right
   *   the schema for right values
   * @tparam A
   *   the left value type
   * @tparam B
   *   the right value type
   * @return
   *   a schema for `Either[A, B]`
   * @example
   *   {{{
   * val schema = Schema[Either[String, Int]]
   *   }}}
   */
  implicit def either[A, B](implicit left: Schema[A], right: Schema[B]): Schema[Either[A, B]] =
    new Schema(Reflect.either(left.reflect, right.reflect))

  /**
   * Builds a `Schema[Json]` from `jsonSchema`. Decoding with it, for example
   * with its `jsonCodec`, checks each value against `jsonSchema` and returns
   * `Left` when the check fails. The check runs at decode time, not when the
   * schema is built.
   *
   * Every property listed in `"properties"` is treated as required, even when
   * `"required"` leaves it out: an object without it fails to decode with
   * `missing required field`. Decode errors carry an extra `.wrapped` step,
   * from the wrapper that runs the check, and a failed check is reported at
   * that step, with the place inside the JSON value only in the message text,
   * as in the example below.
   *
   * {{{
   * import zio.blocks.schema.json.JsonSchema
   *
   * val jsonSchema = JsonSchema
   *   .parse("""{"type":"object","properties":{"title":{"type":"string","minLength":1}},"required":["title"]}""")
   *   .fold(e => throw e, identity)
   * val books = Schema.fromJsonSchema(jsonSchema)
   *
   * val valid   = books.jsonCodec.decode("""{"title":"Zionomicon"}""").isRight // true
   * val invalid = books.jsonCodec.decode("""{"title":""}""")
   * // Left(zio.blocks.schema.SchemaError: String length 0 is less than minLength 1 at: .title at: .wrapped)
   * }}}
   *
   * @see
   *   [[https://zioblocks.com/reference/schema/built-in-codecs/json/json-schema/ JSON Schema]]
   */
  def fromJsonSchema(jsonSchema: JsonSchema): Schema[Json] =
    new Schema(
      new Reflect.Wrapper[Binding, Json, DynamicValue](
        JsonSchemaToReflect.toReflect(jsonSchema),
        TypeId.of[Json],
        new Binding.Wrapper[Json, DynamicValue](
          wrap = { dv =>
            val json = Json.fromDynamicValue(dv)
            jsonSchema.check(json) match {
              case Some(error) => throw error
              case _           => json
            }
          },
          unwrap = j => j.toDynamicValue
        )
      )
    )
}
