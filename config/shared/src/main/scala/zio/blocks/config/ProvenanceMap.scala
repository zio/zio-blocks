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

package zio.blocks.config

import zio.blocks.maybe.Maybe

/**
 * A decoded configuration value paired with the [[ConfigSource]] it was decoded
 * from, which [[Config.loadWithProvenance]] returns. Ask it where one key came
 * from with `provenanceOf`, or print a table of the source with `dump`.
 *
 * Both methods ask the source again when called, so they report what the source
 * holds now.
 *
 * {{{
 * import zio.blocks.schema.Schema
 *
 * final case class Cache(ttlSeconds: Int, maxEntries: Int)
 * object Cache { implicit val schema: Schema[Cache] = Schema.derived }
 *
 * val defaults = ConfigSource.fromMap(Map("ttlSeconds" -> "60", "maxEntries" -> "1000"), "defaults")
 * val env      = ConfigSource.fromMap(Map("ttlSeconds" -> "300"), "env")
 * val loaded   = Config.loadWithProvenance[Cache](env.orElse(defaults))
 * val table    = loaded.map(_.dump()).getOrElse("")
 * // ┌────────────┬───────┬──────────┐
 * // │ Key        │ Value │ Source   │
 * // ├────────────┼───────┼──────────┤
 * // │ maxEntries │ 1000  │ defaults │
 * // │ ttlSeconds │ 300   │ env      │
 * // └────────────┴───────┴──────────┘
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/config/config-source/ ConfigSource]]
 */
final case class ProvenanceMap[A](value: A, private val source: ConfigSource) {

  private def shouldRedact(path: String): Boolean = Sensitive.isSensitive(path)

  private def displayValue(key: String, rawValue: String): String =
    if (shouldRedact(key)) "<secret>" else rawValue

  /**
   * Returns the provenance that the source reports for the dot-separated key
   * `path`, or `Maybe.absent` when the source has no value for it, as for a
   * field that took its default value.
   */
  def provenanceOf(path: String): Maybe[Provenance] =
    source.get(path).map(_.provenance)

  /**
   * Renders a table of every key that the source returns for `prefix` (see
   * [[ConfigSource#all]]), sorted, with its value and the `sourceId` that
   * supplied it, or `(no keys found)` when there is none.
   *
   * The table lists the source's keys, not only the keys the value was decoded
   * from, so an unprefixed [[EnvSource]] prints the whole environment, a known
   * bug. Values are hidden as `<secret>` by key name only (a key containing
   * `password`, `token` or a similar marker), so a [[Secret]] field under
   * another name is printed in clear, a known bug.
   */
  def dump(prefix: String = ""): String = {
    val entries = source.all(prefix)
    if (entries.isEmpty) return "(no keys found)"

    val rows = entries.toList.sortBy(_._1).map { case (key, cv) =>
      val src = cv.provenance.sourceId
      (key, displayValue(key, cv.value), src)
    }

    val keyWidth   = math.max("Key".length, rows.map(_._1.length).max)
    val valueWidth = math.max("Value".length, rows.map(_._2.length).max)
    val srcWidth   = math.max("Source".length, rows.map(_._3.length).max)

    val sb = new StringBuilder
    sb.append("\u250c")
    sb.append("\u2500" * (keyWidth + 2))
    sb.append("\u252c")
    sb.append("\u2500" * (valueWidth + 2))
    sb.append("\u252c")
    sb.append("\u2500" * (srcWidth + 2))
    sb.append("\u2510\n")

    sb.append("\u2502 ")
    sb.append("Key".padTo(keyWidth, ' '))
    sb.append(" \u2502 ")
    sb.append("Value".padTo(valueWidth, ' '))
    sb.append(" \u2502 ")
    sb.append("Source".padTo(srcWidth, ' '))
    sb.append(" \u2502\n")

    sb.append("\u251c")
    sb.append("\u2500" * (keyWidth + 2))
    sb.append("\u253c")
    sb.append("\u2500" * (valueWidth + 2))
    sb.append("\u253c")
    sb.append("\u2500" * (srcWidth + 2))
    sb.append("\u2524\n")

    rows.foreach { case (key, value, src) =>
      sb.append("\u2502 ")
      sb.append(key.padTo(keyWidth, ' '))
      sb.append(" \u2502 ")
      sb.append(value.padTo(valueWidth, ' '))
      sb.append(" \u2502 ")
      sb.append(src.padTo(srcWidth, ' '))
      sb.append(" \u2502\n")
    }

    sb.append("\u2514")
    sb.append("\u2500" * (keyWidth + 2))
    sb.append("\u2534")
    sb.append("\u2500" * (valueWidth + 2))
    sb.append("\u2534")
    sb.append("\u2500" * (srcWidth + 2))
    sb.append("\u2518")

    sb.toString
  }
}
