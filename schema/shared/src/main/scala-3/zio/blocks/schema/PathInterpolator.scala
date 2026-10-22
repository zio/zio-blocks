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

/**
 * Adds the `p"..."` interpolator, which turns a path literal into a
 * [[DynamicOptic]] at compile time. The package object `zio.blocks.schema`
 * mixes this trait in, so `import zio.blocks.schema._` brings `p` into scope.
 */
trait PathInterpolator {

  extension (inline sc: StringContext) {

    /**
     * Parses a path literal into a [[DynamicOptic]] at compile time. The syntax
     * is the one `DynamicOptic#toString` prints:
     *
     *   - `.name` or `name`: a record field (a name starts with a letter or `_`
     *     and goes on with letters, digits or `_`);
     *   - `<Case>`: a variant case;
     *   - `[0]`, `[0,2,5]`, `[1:4]` (indices 1, 2 and 3; empty when the end is
     *     not above the start), and `[*]` or `[:*]`: one index, several
     *     indices, a range of indices, or every element;
     *   - `{"key"}`, `{'c'}`, `{42}`, `{-1}`, `{true}` and `{"a", "b"}`: one
     *     map key, which is a string, char, `Int` or boolean literal, or
     *     several keys;
     *   - `{*}` or `{:*}`: every map value, and `{*:}`: every map key;
     *   - `#Book`, `#string`, `#list(Book)`, `#record { title: string }`: a
     *     search for every value that matches a schema pattern (a
     *     [[SchemaRepr]]).
     *
     * The empty literal `p""` is the root path. Whitespace is allowed inside
     * `[]`, `{}` and `<>`, and inside a search pattern, but not between steps.
     * String keys accept the escapes `\"`, `\\`, `\n`, `\t` and `\r`, and char
     * keys `\'`, `\\`, `\n`, `\t` and `\r`. The macro reads the literal as
     * written, without processing escapes first, so write a key that contains a
     * quote in a triple-quoted literal: `p"""{"api.key"}"""`.
     *
     * A malformed path is a compile error, and so is a literal with
     * interpolated arguments. There is no syntax for a wrapper step, so
     * `p"..."` rejects the `.~` that `DynamicOptic#toString` prints for one
     * (see `docs/dev/issues.md`).
     *
     * {{{
     * val path: DynamicOptic = p".books[*].title"
     *
     * path.nodes.toList                                                // List(Field(books), Elements, Field(title))
     * path == DynamicOptic.root.field("books").elements.field("title") // true
     * }}}
     *
     * @see
     *   [[https://zioblocks.com/reference/schema/path-interpolator/ Path Interpolator]]
     */
    transparent inline def p(inline args: Any*): DynamicOptic = ${ PathMacros.pImpl('sc, 'args) }
  }
}
