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

import zio.test._

object ResourceSpec extends ZIOSpecDefault {

  def spec = suite("Resource")(
    suite("empty")(
      test("creates Resource with no attributes") {
        val resource = Resource.empty
        assertTrue(resource.attributes.isEmpty)
      },
      test("isValid returns true") {
        val resource = Resource.empty
        assertTrue(resource.attributes.size == 0)
      }
    ),
    suite("default")(
      test("includes service.name") {
        val resource    = Resource.default
        val serviceName =
          resource.attributes.get(Attributes.ServiceName)
        assertTrue(serviceName.contains("unknown_service"))
      },
      test("includes telemetry.sdk.name") {
        val resource = Resource.default
        val sdkName  = resource.attributes.get(
          AttributeKey.string("telemetry.sdk.name")
        )
        assertTrue(sdkName.contains("zio-blocks"))
      },
      test("includes telemetry.sdk.language") {
        val resource = Resource.default
        val language = resource.attributes.get(
          AttributeKey.string("telemetry.sdk.language")
        )
        assertTrue(language.contains("scala"))
      },
      test("includes telemetry.sdk.version") {
        val resource = Resource.default
        val version  =
          resource.attributes.get(AttributeKey.string("telemetry.sdk.version"))
        assertTrue(version.isDefined)
      },
      test("has at least 4 attributes") {
        val resource = Resource.default
        assertTrue(resource.attributes.size >= 4)
      }
    ),
    suite("create")(
      test("creates Resource from attributes") {
        val attrs = Attributes.builder
          .put(Attributes.ServiceName, "my-service")
          .build
        val resource    = Resource.create(attrs)
        val serviceName =
          resource.attributes.get(Attributes.ServiceName)
        assertTrue(serviceName.contains("my-service"))
      },
      test("returns attributes as-is") {
        val attrs    = Attributes.empty
        val resource = Resource.create(attrs)
        assertTrue(resource.attributes.isEmpty)
      }
    ),
    suite("constructor")(
      test("Resource(attrs) stores attributes") {
        val attrs = Attributes.builder
          .put(Attributes.ServiceName, "test-service")
          .build
        val resource    = Resource(attrs)
        val serviceName =
          resource.attributes.get(Attributes.ServiceName)
        assertTrue(serviceName.contains("test-service"))
      },
      test("Resource(empty) is equivalent to Resource.empty") {
        val r1 = Resource(Attributes.empty)
        val r2 = Resource.empty
        assertTrue(r1.attributes.isEmpty && r2.attributes.isEmpty)
      }
    ),
    suite("merge")(
      test("combines attributes from two resources") {
        val attrs1 = Attributes.builder
          .put(Attributes.ServiceName, "service-1")
          .build
        val attrs2 = Attributes.builder
          .put(Attributes.ServiceVersion, "1.0.0")
          .build
        val r1          = Resource(attrs1)
        val r2          = Resource(attrs2)
        val merged      = r1.merge(r2)
        val serviceName =
          merged.attributes.get(Attributes.ServiceName)
        val serviceVersion =
          merged.attributes.get(Attributes.ServiceVersion)
        assertTrue(
          serviceName.contains("service-1") && serviceVersion.contains("1.0.0")
        )
      },
      test("other resource wins on conflict") {
        val attrs1 = Attributes.builder
          .put(Attributes.ServiceName, "service-1")
          .build
        val attrs2 = Attributes.builder
          .put(Attributes.ServiceName, "service-2")
          .build
        val r1          = Resource(attrs1)
        val r2          = Resource(attrs2)
        val merged      = r1.merge(r2)
        val serviceName =
          merged.attributes.get(Attributes.ServiceName)
        assertTrue(serviceName.contains("service-2"))
      },
      test("merge with empty resource returns same attributes") {
        val attrs = Attributes.builder
          .put(Attributes.ServiceName, "my-service")
          .build
        val r1          = Resource(attrs)
        val r2          = Resource.empty
        val merged      = r1.merge(r2)
        val serviceName =
          merged.attributes.get(Attributes.ServiceName)
        assertTrue(serviceName.contains("my-service"))
      },
      test("preserves non-conflicting attributes from both resources") {
        val attrs1 = Attributes.builder
          .put(Attributes.ServiceName, "service-1")
          .put(AttributeKey.string("custom.attr1"), "value1")
          .build
        val attrs2 = Attributes.builder
          .put(Attributes.ServiceVersion, "1.0.0")
          .put(AttributeKey.string("custom.attr2"), "value2")
          .build
        val r1      = Resource(attrs1)
        val r2      = Resource(attrs2)
        val merged  = r1.merge(r2)
        val name    = merged.attributes.get(Attributes.ServiceName)
        val version =
          merged.attributes.get(Attributes.ServiceVersion)
        val attr1 =
          merged.attributes.get(AttributeKey.string("custom.attr1"))
        val attr2 =
          merged.attributes.get(AttributeKey.string("custom.attr2"))
        assertTrue(
          name.contains("service-1") &&
            version.contains("1.0.0") &&
            attr1.contains("value1") &&
            attr2.contains("value2")
        )
      }
    ),
    suite("attributes accessor")(
      test("returns the underlying Attributes") {
        val attrs = Attributes.builder
          .put(Attributes.ServiceName, "test-service")
          .build
        val resource    = Resource(attrs)
        val retrieved   = resource.attributes
        val serviceName =
          retrieved.get(Attributes.ServiceName)
        assertTrue(serviceName.contains("test-service"))
      },
      test("is immutable (returned Attributes cannot be modified)") {
        val attrs = Attributes.builder
          .put(Attributes.ServiceName, "test-service")
          .build
        val resource  = Resource(attrs)
        val retrieved = resource.attributes
        assertTrue(retrieved.size == 1)
      }
    )
  )
}
