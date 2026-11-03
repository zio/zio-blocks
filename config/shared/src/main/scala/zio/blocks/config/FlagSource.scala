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

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import scala.jdk.CollectionConverters._
import zio.blocks.maybe.Maybe

/**
 * A lookup of raw flag values by flag name, with provenance. Register sources
 * with [[FlagSource.Registry]]: a [[StaticFlag]] or [[DynamicFlag]] reads the
 * registry when its object initializes, and `DynamicFlag#reload` reads it
 * again. Every [[ConfigSource]] is also a `FlagSource`.
 *
 * {{{
 * val remote = FlagSource.fromMap(Map("com.example.Limits.maxConnections" -> "200"), "remote")
 * FlagSource.Registry.register(remote)
 *
 * val raw = FlagSource.Registry.resolve("com.example.Limits.maxConnections").toOption.map(_.value)
 * // Some(200)
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/config/flags/ Flags]]
 */
trait FlagSource {

  /**
   * Names this source in provenance. [[FlagSource.Registry]] keys sources by
   * it.
   */
  def sourceId: String

  /**
   * Looks up the raw value of the flag `name`, with its provenance, or returns
   * `Maybe.absent`.
   */
  def get(name: String): Maybe[SourceValue[String]]

  /**
   * Returns a source that looks a name up in this source first and in
   * `fallback` only when this source has no value for it. Its `sourceId` is
   * `this.sourceId|fallback.sourceId`.
   *
   * {{{
   * val local  = FlagSource.fromMap(Map("RequestTimeout" -> "5s"), "local")
   * val shared = FlagSource.fromMap(Map("RequestTimeout" -> "30s", "MaxRetries" -> "3"), "shared")
   * val both   = local.orElse(shared)
   *
   * val timeout = both.get("RequestTimeout").toOption.map(_.value)
   * // Some(5s)
   * val retries = both.get("MaxRetries").toOption.map(_.value)
   * // Some(3)
   * }}}
   */
  final def orElse(fallback: FlagSource): FlagSource = {
    val self = this
    new FlagSource {
      val sourceId: String = s"${self.sourceId}|${fallback.sourceId}"

      def get(name: String): Maybe[SourceValue[String]] =
        self.get(name).orElse(fallback.get(name))
    }
  }
}

object FlagSource {

  /**
   * The global list of [[FlagSource]]s that flags read. It keeps sources in
   * registration order, and the first source that has a value for a name wins.
   * Register sources before any flag object initializes: a [[StaticFlag]] never
   * reads the registry again.
   */
  object Registry {
    private val byId: ConcurrentHashMap[String, FlagSource] = new ConcurrentHashMap[String, FlagSource]()
    private val ordered: CopyOnWriteArrayList[FlagSource]   = new CopyOnWriteArrayList[FlagSource]()

    /**
     * Adds `source` at the end, or, when a source with the same `sourceId` is
     * registered, replaces that source in its position.
     */
    def register(source: FlagSource): Unit = {
      val existing = byId.putIfAbsent(source.sourceId, source)
      if (existing == null) ordered.add(source)
      else {
        byId.put(source.sourceId, source)
        val idx = ordered.indexOf(existing)
        if (idx >= 0) ordered.set(idx, source)
        else ordered.add(source)
      }
    }

    /** Removes the source registered under `sourceId`, if any. */
    def unregister(sourceId: String): Unit = {
      val removed = byId.remove(sourceId)
      if (removed != null) ordered.remove(removed)
    }

    /** Returns the source registered under `sourceId`. */
    def get(sourceId: String): Maybe[FlagSource] =
      Maybe.fromOption(Option(byId.get(sourceId)))

    /** Returns the registered sources in registration order. */
    def all: Seq[FlagSource] =
      ordered.asScala.toSeq

    /**
     * Returns the value of `flagName` from the first registered source that has
     * one, or `Maybe.absent`.
     */
    def resolve(flagName: String): Maybe[SourceValue[String]] = {
      val iter                                 = ordered.iterator()
      var resolved: Maybe[SourceValue[String]] = Maybe.absent
      while (iter.hasNext && resolved.isAbsent) {
        val source = iter.next()
        val result = source.get(flagName)
        resolved = result.fold(resolved)(_ => result)
      }
      resolved
    }

    /**
     * Removes every registered source, for example between tests. Flags that
     * have already initialized keep their values.
     */
    def clear(): Unit = {
      byId.clear()
      ordered.clear()
    }
  }

  /**
   * Returns a source over `map`, whose keys are flag names, with `id` as its
   * `sourceId`. Tests and local overrides use it.
   */
  def fromMap(map: Map[String, String], id: String = "map-source"): FlagSource =
    new FlagSource {
      val sourceId: String = id

      def get(name: String): Maybe[SourceValue[String]] =
        Maybe.fromOption(
          map.get(name).map(value => SourceValue(value, Provenance.Resolved(sourceId, name, Maybe.present(value))))
        )
    }
}
