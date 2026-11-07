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

package zio.http

/**
 * A typed DSL for htmx 2 attributes and headers, on top of [[zio.blocks.html]]
 * and the `zio.http` model.
 *
 * `import zio.http.htmx._` brings the attribute keys of [[HtmxAttributes]]
 * (`hxGet`, `hxSwap`, `hxTrigger`, ...) and the value types ([[HxSwap]],
 * [[HxTarget]], [[HxTrigger]], [[HxParams]], ...) into scope. The typed `HX-*`
 * request and response headers are in [[zio.http.htmx.headers]].
 *
 * @see
 *   [[https://zioblocks.com/reference/htmx/ HTMX]]
 */
package object htmx extends HtmxAttributes
