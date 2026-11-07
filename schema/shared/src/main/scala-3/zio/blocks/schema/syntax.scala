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

import zio.blocks.schema.json.Json
import zio.blocks.schema.patch.{Patch, PatchMode}

/**
 * Extension methods on values with a [[Schema]], on JSON strings and on JSON
 * bytes, available through `import zio.blocks.schema._`.
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/syntax/ Extension Syntax]]
 */
trait SyntaxVersionSpecific {
  extension [A](self: A) {

    /**
     * Applies `patch` to this value in lenient mode: operations that cannot be
     * applied are skipped, and when the result does not convert back to `A`,
     * this value is returned unchanged.
     */
    def applyPatch(patch: Patch[A]): A = patch(self)

    /**
     * Applies `patch` to this value in strict mode, returning `Left` when an
     * operation of the patch cannot be applied.
     */
    def applyPatchStrict(patch: Patch[A]): Either[SchemaError, A] = patch(self, PatchMode.Strict)

    /**
     * Computes the patch that turns this value into `that`, as `Schema#diff`
     * does.
     */
    def diff(that: A)(using schema: Schema[A]): Patch[A] = schema.diff(self, that)

    /**
     * Renders this value as its [[DynamicValue]], in the JSON-like EJSON text
     * form with unquoted record keys.
     */
    def show(using schema: Schema[A]): String = schema.toDynamicValue(self).toString

    /** Encodes this value to a `Json` value with `Schema#jsonCodec`. */
    def toJson(using schema: Schema[A]): Json = schema.jsonCodec.encodeValue(self)

    /**
     * Encodes this value to a JSON string with `Schema#jsonCodec`, the codec
     * derived with the default JSON deriver.
     *
     * It ignores an implicit `JsonCodec` in scope and any deriver configured
     * with `withInstance` or `withModifier`: to use such a deriver, derive the
     * codec with `Schema#derive` and call `encodeToString` on it. An implicit
     * `Schema` of a field type, by contrast, is used by `Schema.derived`.
     *
     * {{{
     * case class Book(title: String, year: Int)
     * object Book {
     *   implicit val schema: Schema[Book] = Schema.derived
     * }
     *
     * val json = Book("Zionomicon", 2021).toJsonString // {"title":"Zionomicon","year":2021}
     * }}}
     *
     * @see
     *   [[https://zioblocks.com/reference/schema/syntax/ Extension Syntax]]
     */
    def toJsonString(using schema: Schema[A]): String = schema.jsonCodec.encodeToString(self)

    /** Encodes this value to UTF-8 JSON bytes with `Schema#jsonCodec`. */
    def toJsonBytes(using schema: Schema[A]): Array[Byte] = schema.jsonCodec.encode(self)
  }

  extension (self: String) {

    /**
     * Decodes a value of `A` from this JSON string with `Schema#jsonCodec`,
     * returning `Left` with a [[SchemaError]] when the JSON is malformed or
     * does not match the schema. Like `toJsonString`, it ignores implicit
     * codecs and configured derivers.
     *
     * {{{
     * case class Book(title: String, year: Int)
     * object Book {
     *   implicit val schema: Schema[Book] = Schema.derived
     * }
     *
     * val book    = """{"title":"Zionomicon","year":2021}""".fromJson[Book] // Right(Book(Zionomicon,2021))
     * val missing = """{"year":2021}""".fromJson[Book]
     * // Left(zio.blocks.schema.SchemaError: missing required field "title" at: .)
     * }}}
     *
     * @see
     *   [[https://zioblocks.com/reference/schema/syntax/ Extension Syntax]]
     */
    def fromJson[A](using schema: Schema[A]): Either[SchemaError, A] = schema.jsonCodec.decode(self)
  }

  extension (self: Array[Byte]) {

    /**
     * Decodes a value of `A` from these UTF-8 JSON bytes with
     * `Schema#jsonCodec`, returning `Left` with a [[SchemaError]] when the JSON
     * is malformed or does not match the schema.
     */
    def fromJson[A](using schema: Schema[A]): Either[SchemaError, A] = schema.jsonCodec.decode(self)
  }
}
