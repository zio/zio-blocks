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
 * Describes the entity producing telemetry (service, container, host, etc.).
 *
 * Resources are immutable wrappers around Attributes that identify the
 * application/service generating the telemetry data. A Resource typically
 * includes semantic attributes like service.name, service.version, and
 * deployment environment.
 */
final case class Resource(attributes: Attributes)

object Resource {

  /**
   * An empty Resource with no attributes.
   */
  val empty: Resource = Resource(Attributes.empty)

  /**
   * Creates a Resource from attributes.
   *
   * @param attrs
   *   The attributes to wrap
   * @return
   *   A Resource containing the given attributes
   */
  def create(attrs: Attributes): Resource =
    Resource(attrs)

  /**
   * The default Resource with auto-populated standard attributes.
   *
   * Includes:
   *   - service.name = "unknown_service"
   *   - telemetry.sdk.name = "zio-blocks"
   *   - telemetry.sdk.language = "scala"
   *   - telemetry.sdk.version = BuildInfo.version
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

  /**
   * Extension method to merge this Resource with another.
   *
   * Attributes from the other Resource take precedence on key conflicts,
   * following the semantics of Attributes.++.
   *
   * @param other
   *   The other Resource to merge
   * @return
   *   A new Resource with merged attributes
   */
  implicit class ResourceOps(val self: Resource) {
    def merge(other: Resource): Resource =
      Resource(self.attributes ++ other.attributes)
  }
}
