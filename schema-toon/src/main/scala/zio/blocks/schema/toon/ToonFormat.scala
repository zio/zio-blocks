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

package zio.blocks.schema.toon

import zio.blocks.schema.codec.BinaryFormat

/**
 * The TOON [[zio.blocks.schema.codec.BinaryFormat]]: MIME type `text/toon`,
 * with codecs derived by the default [[ToonCodecDeriver]]. Pass it to
 * `Schema#derive` to get a [[ToonCodec]] for a type. TOON (Token-Oriented
 * Object Notation) writes the JSON data model as indented `key: value` lines,
 * with each array's length in its header and an optional table form for arrays
 * of records. To change a derivation setting, pass a configured
 * `ToonCodecDeriver` to `Schema#derive` instead.
 *
 * {{{
 * import zio.blocks.schema._
 *
 * case class Sensor(id: String, celsius: Double, online: Boolean)
 * object Sensor {
 *   implicit val schema: Schema[Sensor] = Schema.derived
 * }
 *
 * val codec = Sensor.schema.derive(ToonFormat)
 *
 * codec.encodeToString(Sensor("greenhouse-1", 21.5, online = true))
 * // id: greenhouse-1
 * // celsius: 21.5
 * // online: true
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/built-in-codecs/toon/ TOON]]
 */
object ToonFormat extends BinaryFormat("text/toon", ToonCodecDeriver)
