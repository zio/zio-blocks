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

package zio.blocks.endpoint

/**
 * Defines a group of HTTP endpoints in a single block and returns them as a
 * statically-typed [[scala.NamedTuple]].
 *
 * Each statement in the block must be either:
 *   - `val name = Endpoint(...)` — exposed as member `.name`
 *   - a bare `Endpoint(...)` expression — auto-named from its route render,
 *     e.g. `` .`GET /user/{userId}` `` (RFC 6570 `{var}` style, method prefix,
 *     multi-method rendered as `GET#|POST`)
 *
 * Constant-prefix nesting (`"api" / endpoints { ... }`) composes prefixes into
 * every child route at the description level. Capturing prefixes
 * (`PathCodec.int("id") / endpoints { ... }`) additionally widen each child's
 * static `PathInput`.
 *
 * @param body
 *   the block of endpoint declarations; every statement must be an
 *   `Endpoint(...)` construction, optionally bound to a `val`
 * @return
 *   a `NamedTuple[Names, Values]` pairing each declared name with its
 *   fully-typed `Endpoint`
 * @note
 *   Scala 3.7+ only (named tuples); group operators require
 *   `import zio.blocks.endpoint.BulkDsl.*`
 */
transparent inline def endpoints(inline body: Any): Any =
  ${ EndpointGroupMacro.build('body) }
