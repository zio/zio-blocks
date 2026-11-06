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

package zio.blocks.telemetry

/**
 * Describes what produces the telemetry, the service, host or container, as
 * [[Attributes]] such as `service.name` and `service.version`. Give one to a
 * `LoggerProvider` or `TracerProvider` builder, and every log record and span
 * they create carries it, so a backend can group signals by service.
 *
 * Build it with `Resource.create` and an attributes builder. `merge` keeps both
 * values of a key that both resources hold, and the exporters write both, so
 * `Resource.default.merge(...)` with a `service.name` exports `service.name`
 * twice, `unknown_service` first, a known bug. `zio.blocks.scope` also defines
 * a `Resource`, so a file that imports both packages with wildcards gets
 * "reference to Resource is ambiguous", a known bug: import one of them by
 * name.
 *
 * {{{
 * val resource = Resource.create(
 *   Attributes.builder
 *     .put(Attributes.ServiceName, "checkout")
 *     .put(Attributes.ServiceVersion, "1.4.0")
 *     .build
 * )
 *
 * resource.attributes.get(Attributes.ServiceName) // Some(checkout)
 * resource.attributes.size // 2
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/telemetry/common/resource/ Resource]]
 */
final case class Resource(attributes: Attributes)

/** Creates [[Resource]]s and holds the standard ones. */
object Resource {

  /** The resource with no attributes. */
  val empty: Resource = Resource(Attributes.empty)

  /** Returns a resource with these attributes. */
  def create(attrs: Attributes): Resource =
    Resource(attrs)

  /**
   * The resource that `LoggerProvider.builder`, `TracerProvider.builder` and
   * `MeterProvider.builder` start from: `service.name` is `"unknown_service"`,
   * `telemetry.sdk.name` is `"zio-blocks"`, `telemetry.sdk.language` is
   * `"scala"` and `telemetry.sdk.version` is the library version.
   */
  val default: Resource = {
    val attrs = Attributes.builder
      .put(Attributes.ServiceName, "unknown_service")
      .put("telemetry.sdk.name", "zio-blocks")
      .put("telemetry.sdk.language", "scala")
      .put("telemetry.sdk.version", zio.blocks.telemetry.BuildInfo.version)
      .build
    Resource(attrs)
  }

  /** Adds `merge` to [[Resource]]. */
  implicit class ResourceOps(val self: Resource) {

    /**
     * Returns a resource with the attributes of `self` followed by those of
     * `other`, with `Attributes#++`: a key in both stays twice. Lookups return
     * the value from `other`, and the exporters write both entries.
     */
    def merge(other: Resource): Resource =
      Resource(self.attributes ++ other.attributes)
  }
}
