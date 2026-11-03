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
 * Where a configuration or flag value came from: [[Provenance.Resolved]] for a
 * value that a source supplied, or [[Provenance.Default]] for a [[StaticFlag]]
 * that fell back to its constructor default.
 *
 * {{{
 * val source = ConfigSource.fromMap(Map("cache.ttl" -> "PT5M"), "defaults")
 * val where  = source.get("cache.ttl").toOption.map(_.provenance) match {
 *   case Some(Provenance.Resolved(sourceId, key, _)) => sourceId + " supplied " + key
 *   case _                                          => "no value"
 * }
 * // defaults supplied cache.ttl
 * }}}
 *
 * `Resolved` holds the raw value and its `toString` prints it, even for a field
 * of type [[Secret]], a known bug.
 *
 * @see
 *   [[https://zioblocks.com/reference/config/config-source/ ConfigSource]]
 */
sealed trait Provenance {

  /**
   * Names the source of the value: the `sourceId` of the source for `Resolved`,
   * and `schema-default` for `Default`.
   */
  def sourceId: String
}

/** The [[Provenance]] cases. */
object Provenance {

  /**
   * A value that a source supplied.
   *
   * @param sourceId
   *   identifier of the source that answered the lookup
   * @param key
   *   the key as the source spells it, such as `DB_HOST` for [[EnvSource]]
   * @param rawValue
   *   original string value when the source exposes it
   */
  final case class Resolved(sourceId: String, key: String, rawValue: Maybe[String]) extends Provenance

  /**
   * A [[StaticFlag]] value that is the flag's constructor default because no
   * source supplied one. A config field filled from its default value gets no
   * provenance at all.
   */
  case object Default extends Provenance {

    /**
     * The id `schema-default`, which the Source column of `Flag.dump` shows for
     * a flag that kept its default.
     */
    val sourceId: String = "schema-default"
  }
}
