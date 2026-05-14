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
import scala.jdk.CollectionConverters._

/**
 * A pluggable source for resolving flag values by name.
 * Providers are consulted before system properties and environment variables.
 */
trait FlagProvider {

  /**
   * Unique identifier for this provider (used in provenance tracking).
   */
  def providerId: String

  /**
   * Resolve a flag value by name alone.
   */
  def resolve(flagName: String): Option[String]

  /**
   * Resolve a flag value with additional context (key path and attributes).
   * Defaults to delegating to the simple resolve.
   */
  def resolve(flagName: String, key: String, attributes: Map[String, String]): Option[String] = {
    val _ = (key, attributes)
    resolve(flagName)
  }

  /**
   * Compose this provider with a fallback. This provider is consulted first;
   * if it returns None, the fallback is tried.
   */
  final def orElse(fallback: FlagProvider): FlagProvider = {
    val self = this
    new FlagProvider {
      val providerId: String = s"${self.providerId}|${fallback.providerId}"

      def resolve(flagName: String): Option[String] =
        self.resolve(flagName).orElse(fallback.resolve(flagName))

      override def resolve(flagName: String, key: String, attributes: Map[String, String]): Option[String] =
        self.resolve(flagName, key, attributes).orElse(fallback.resolve(flagName, key, attributes))
    }
  }
}

object FlagProvider {

  /**
   * Global registry of FlagProvider instances.
   * Thread-safe via ConcurrentHashMap.
   */
  object Registry {
    private val providers: ConcurrentHashMap[String, FlagProvider] = new ConcurrentHashMap[String, FlagProvider]()

    def register(provider: FlagProvider): Unit =
      providers.put(provider.providerId, provider)

    def unregister(providerId: String): Unit =
      providers.remove(providerId)

    def get(providerId: String): Option[FlagProvider] =
      Option(providers.get(providerId))

    def all: Seq[FlagProvider] =
      providers.values().asScala.toSeq

    /** Resolve a flag name across all registered providers. First Some wins. */
    def resolve(flagName: String): Option[(String, String)] = {
      val iter = providers.values().iterator()
      while (iter.hasNext) {
        val p = iter.next()
        p.resolve(flagName) match {
          case Some(v) => return Some((v, p.providerId))
          case None    => ()
        }
      }
      None
    }

    /** Clear all registered providers (for testing). */
    def clear(): Unit =
      providers.clear()
  }

  /**
   * Create a FlagProvider backed by an in-memory map. Useful for testing.
   */
  def fromMap(map: Map[String, String], id: String = "map-provider"): FlagProvider =
    new FlagProvider {
      val providerId: String = id
      def resolve(flagName: String): Option[String] = map.get(flagName)
    }
}
