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
 * The HTML module. `import zio.blocks.html._` brings in the DSL
 * ([[HtmlElements]]: tags, attribute keys and helpers), the `html`, `css`, `js`
 * and `selector` interpolators ([[TemplateInterpolators]]) and the conversion
 * that lets elements take strings, nodes and attributes as modifiers
 * ([[DomModifierConversions]]).
 *
 * @see
 *   [[https://zioblocks.com/reference/html/ HTML]]
 */
package object html extends HtmlElements with TemplateInterpolators with DomModifierConversions
