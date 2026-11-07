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

package zio.blocks

/**
 * Schemas of Scala types and what is built on them: codecs, dynamic values,
 * optics, patches and migrations. Importing the package with
 * `import zio.blocks.schema._` also brings in the extension syntax
 * (`toJsonString`, `fromJson`, `diff`, ...) and the `p"..."` path interpolator.
 *
 * On Scala 3 the path interpolator is an extension method named `p`, so with
 * both `import zio.blocks.schema._` and `import zio.blocks.html._` in scope a
 * call to the HTML element `p(...)` is ambiguous. Write that element as
 * `zio.blocks.html.p(...)`, or import only the names you need from one of the
 * two packages.
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/syntax/ Extension Syntax]]
 */
package object schema extends PathInterpolator with SyntaxVersionSpecific {}
