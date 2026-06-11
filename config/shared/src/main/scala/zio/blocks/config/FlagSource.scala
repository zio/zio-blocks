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

/**
 * A scalar source of flag values.
 */
trait FlagSource {

  /**
   * Unique identifier for this source.
   */
  def sourceId: String

  /**
   * Resolve a raw string value by name together with provenance.
   */
  def get(name: String): Option[SourceValue[String]]

  /**
   * Compose this source with a fallback. This source is consulted first; if it
   * returns None, the fallback is tried.
   */
  final def orElse(fallback: FlagSource): FlagSource = {
    val self = this
    new FlagSource {
      val sourceId: String = s"${self.sourceId}|${fallback.sourceId}"

      def get(name: String): Option[SourceValue[String]] =
        self.get(name).orElse(fallback.get(name))
    }
  }
}

object FlagSource {

  /**
   * Global registry of [[FlagSource]] instances. Thread-safe. Sources are
   * iterated in registration order so that the first-registered source wins
   * when multiple sources resolve the same flag name.
   */
  object Registry {
    private val byId: ConcurrentHashMap[String, FlagSource] = new ConcurrentHashMap[String, FlagSource]()
    private val ordered: CopyOnWriteArrayList[FlagSource]   = new CopyOnWriteArrayList[FlagSource]()

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

    def unregister(sourceId: String): Unit = {
      val removed = byId.remove(sourceId)
      if (removed != null) ordered.remove(removed)
    }

    def get(sourceId: String): Option[FlagSource] =
      Option(byId.get(sourceId))

    def all: Seq[FlagSource] =
      ordered.asScala.toSeq

    /**
     * Resolve a flag name across all registered sources in registration order.
     * First Some wins.
     */
    def resolve(flagName: String): Option[SourceValue[String]] = {
      val iter = ordered.iterator()
      while (iter.hasNext) {
        val source = iter.next()
        source.get(flagName) match {
          case some @ Some(_) => return some
          case None           => ()
        }
      }
      None
    }

    /** Clear all registered sources (for testing). */
    def clear(): Unit = {
      byId.clear()
      ordered.clear()
    }
  }

  /**
   * Create a [[FlagSource]] backed by an in-memory map. Useful for testing.
   */
  def fromMap(map: Map[String, String], id: String = "map-source"): FlagSource =
    new FlagSource {
      val sourceId: String = id

      def get(name: String): Option[SourceValue[String]] =
        map.get(name).map(value => SourceValue(value, Provenance.Resolved(sourceId, name, Some(value))))
    }
}
