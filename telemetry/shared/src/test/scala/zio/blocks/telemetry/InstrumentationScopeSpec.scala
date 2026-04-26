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

object InstrumentationScopeSpec extends ZIOSpecDefault {

  def spec = suite("InstrumentationScope")(
    suite("constructor with all parameters")(
      test("stores name, version (Option), and attributes") {
        val attrs = Attributes.builder
          .put(Attributes.ServiceName, "test-service")
          .build
        val scope =
          InstrumentationScope("my.instrumentation", Some("1.2.3"), attrs)
        assertTrue(
          scope.name == "my.instrumentation" &&
            scope.version == Some("1.2.3") &&
            scope.attributes.size == 1
        )
      },
      test("accepts None version") {
        val attrs = Attributes.builder
          .put(Attributes.ServiceName, "test-service")
          .build
        val scope =
          InstrumentationScope("my.instrumentation", None, attrs)
        assertTrue(
          scope.name == "my.instrumentation" &&
            scope.version == None &&
            scope.attributes.size == 1
        )
      },
      test("accepts empty attributes") {
        val scope =
          InstrumentationScope("my.instrumentation", Some("1.0.0"), Attributes.empty)
        assertTrue(scope.attributes.isEmpty)
      }
    ),
    suite("constructor with name only")(
      test("creates scope with no version and empty attrs") {
        val scope = InstrumentationScope("my.instrumentation")
        assertTrue(
          scope.name == "my.instrumentation" &&
            scope.version == None &&
            scope.attributes.isEmpty
        )
      },
      test("stores the name correctly") {
        val scope = InstrumentationScope("io.opentelemetry")
        assertTrue(scope.name == "io.opentelemetry")
      }
    ),
    suite("constructor with name and version")(
      test("creates scope with version and empty attrs") {
        val scope =
          InstrumentationScope("my.instrumentation", Some("2.0.0"))
        assertTrue(
          scope.name == "my.instrumentation" &&
            scope.version == Some("2.0.0") &&
            scope.attributes.isEmpty
        )
      },
      test("stores version as Some") {
        val scope =
          InstrumentationScope("my.instrumentation", Some("1.5.0"))
        assertTrue(scope.version == Some("1.5.0"))
      }
    ),
    suite("name accessor")(
      test("returns the instrumentation scope name") {
        val scope = InstrumentationScope("com.example.lib", Some("1.0.0"))
        assertTrue(scope.name == "com.example.lib")
      }
    ),
    suite("version accessor")(
      test("returns Some(version) when version is provided") {
        val scope = InstrumentationScope("my.lib", Some("3.2.1"))
        assertTrue(scope.version == Some("3.2.1"))
      },
      test("returns None when version is not provided") {
        val scope = InstrumentationScope("my.lib")
        assertTrue(scope.version == None)
      }
    ),
    suite("attributes accessor")(
      test("returns the underlying Attributes") {
        val attrs = Attributes.builder
          .put(Attributes.ServiceName, "test-service")
          .build
        val scope =
          InstrumentationScope("my.lib", Some("1.0.0"), attrs)
        val retrieved   = scope.attributes
        val serviceName =
          retrieved.get(Attributes.ServiceName)
        assertTrue(serviceName.contains("test-service"))
      },
      test("returns empty Attributes when none provided") {
        val scope = InstrumentationScope("my.lib")
        assertTrue(scope.attributes.isEmpty)
      },
      test("is immutable (returned Attributes cannot be modified)") {
        val attrs = Attributes.builder
          .put(Attributes.ServiceName, "test-service")
          .build
        val scope =
          InstrumentationScope("my.lib", Some("1.0.0"), attrs)
        val retrieved = scope.attributes
        assertTrue(retrieved.size == 1)
      }
    ),
    suite("equality")(
      test("two scopes with same name and version have same properties") {
        val attrs1 = Attributes.builder
          .put(Attributes.ServiceName, "test-service")
          .build
        val attrs2 = Attributes.builder
          .put(Attributes.ServiceName, "test-service")
          .build
        val scope1 =
          InstrumentationScope("my.lib", Some("1.0.0"), attrs1)
        val scope2 =
          InstrumentationScope("my.lib", Some("1.0.0"), attrs2)
        assertTrue(
          scope1.name == scope2.name &&
            scope1.version == scope2.version &&
            scope1.attributes.size == scope2.attributes.size
        )
      },
      test("scopes with different names are not equal") {
        val scope1 = InstrumentationScope("lib1")
        val scope2 = InstrumentationScope("lib2")
        assertTrue(scope1 != scope2)
      },
      test("scopes with different versions are not equal") {
        val scope1 = InstrumentationScope("my.lib", Some("1.0.0"))
        val scope2 = InstrumentationScope("my.lib", Some("2.0.0"))
        assertTrue(scope1 != scope2)
      },
      test("scope with version != scope without version") {
        val scope1 = InstrumentationScope("my.lib", Some("1.0.0"))
        val scope2 = InstrumentationScope("my.lib")
        assertTrue(scope1 != scope2)
      },
      test("scopes with different attributes are not equal") {
        val scope1 = InstrumentationScope(
          "my.lib",
          Some("1.0.0"),
          Attributes.builder
            .put(Attributes.ServiceName, "service-1")
            .build
        )
        val scope2 = InstrumentationScope(
          "my.lib",
          Some("1.0.0"),
          Attributes.builder
            .put(Attributes.ServiceName, "service-2")
            .build
        )
        assertTrue(scope1 != scope2)
      }
    ),
    suite("immutability")(
      test("is a case class (implicit immutability)") {
        val scope1 = InstrumentationScope("my.lib", Some("1.0.0"))
        val scope2 = InstrumentationScope("my.lib", Some("1.0.0"))
        assertTrue(scope1 == scope2)
      },
      test("constructor creates new instances") {
        val scope1 = InstrumentationScope("my.lib", Some("1.0.0"))
        val scope2 = InstrumentationScope("my.lib", Some("1.0.0"))
        assertTrue(scope1 ne scope2) // Different object references
      }
    )
  )
}
