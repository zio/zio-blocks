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

/**
 * A decoded configuration value paired with the `ConfigSource` that produced it,
 * allowing per-key provenance queries.
 */
final case class ProvenanceMap[A](value: A, private val source: ConfigSource) {

  /**
   * Look up the provenance of a specific dot-separated key path.
   */
  def provenanceOf(path: String): Option[Provenance] =
    source.get(path).map(_.provenance)

  /**
   * Render a formatted table showing all keys visible under the given prefix
   * with their values and sources.
   */
  def dump(prefix: String = ""): String = {
    val entries = source.getAll(prefix)
    if (entries.isEmpty) return "(no keys found)"

    val rows = entries.toList.sortBy(_._1).map { case (key, cv) =>
      val src = cv.provenance.sourceId
      (key, cv.value, src)
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
