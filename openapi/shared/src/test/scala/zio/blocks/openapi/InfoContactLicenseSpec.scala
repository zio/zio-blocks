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

package zio.blocks.openapi

import zio.blocks.chunk.{Chunk, ChunkMap}
import zio.blocks.docs.{Doc, Inline, Paragraph}
import zio.blocks.schema._
import zio.blocks.schema.json.Json
import zio.test._

object InfoContactLicenseSpec extends SchemaBaseSpec {
  private def doc(s: String): Doc      = Doc(Chunk.single(Paragraph(Chunk.single(Inline.Text(s)))))
  def spec: Spec[TestEnvironment, Any] = suite("Info, Contact, License")(
    suite("Contact")(
      test("can be constructed with all fields empty") {
        val contact = Contact()
        assertTrue(
          contact.name.isEmpty,
          contact.url.isEmpty,
          contact.email.isEmpty,
          contact.extensions.isEmpty
        )
      },
      test("can be constructed with name only") {
        val contact = Contact(name = Some("API Support"))
        assertTrue(
          contact.name.contains("API Support"),
          contact.url.isEmpty,
          contact.email.isEmpty,
          contact.extensions.isEmpty
        )
      },
      test("can be constructed with url only") {
        val contact = Contact(url = Some("https://www.example.com/support"))
        assertTrue(
          contact.name.isEmpty,
          contact.url.contains("https://www.example.com/support"),
          contact.email.isEmpty,
          contact.extensions.isEmpty
        )
      },
      test("can be constructed with email only") {
        val contact = Contact(email = Some("support@example.com"))
        assertTrue(
          contact.name.isEmpty,
          contact.url.isEmpty,
          contact.email.contains("support@example.com"),
          contact.extensions.isEmpty
        )
      },
      test("can be constructed with all fields populated") {
        val contact = Contact(
          name = Some("API Support Team"),
          url = Some("https://www.example.com/support"),
          email = Some("support@example.com"),
          extensions = ChunkMap("x-team-id" -> Json.String("team-123"))
        )
        assertTrue(
          contact.name.contains("API Support Team"),
          contact.url.contains("https://www.example.com/support"),
          contact.email.contains("support@example.com"),
          contact.extensions.size == 1,
          contact.extensions.get("x-team-id").contains(Json.String("team-123"))
        )
      },
      test("preserves extensions on construction") {
        val contact = Contact(
          name = Some("Support"),
          extensions = ChunkMap(
            "x-custom" -> Json.String("value"),
            "x-number" -> Json.Number(42),
            "x-bool"   -> Json.Boolean(true)
          )
        )
        assertTrue(
          contact.extensions.size == 3,
          contact.extensions.get("x-custom").contains(Json.String("value")),
          contact.extensions.get("x-number").contains(Json.Number(42)),
          contact.extensions.get("x-bool").contains(Json.Boolean(true))
        )
      },
      test("Schema[Contact] can be derived") {
        val contact = Contact(name = Some("Support"))
        val schema  = Schema[Contact]
        assertTrue(schema != null, contact != null)
      },
      test("Contact round-trips through DynamicValue") {
        val contact = Contact(
          name = Some("API Support"),
          url = Some("https://example.com"),
          email = Some("support@example.com"),
          extensions = ChunkMap("x-custom" -> Json.String("test"))
        )
        val result = Schema[Contact].fromDynamicValue(Schema[Contact].toDynamicValue(contact))
        assertTrue(
          result.isRight,
          result.exists(_.name.contains("API Support")),
          result.exists(_.url.contains("https://example.com")),
          result.exists(_.email.contains("support@example.com")),
          result.exists(_.extensions.contains("x-custom"))
        )
      }
    ),
    suite("License")(
      test("can be constructed with name only") {
        val license = License(name = "Apache 2.0")
        assertTrue(
          license.name == "Apache 2.0",
          license.identifier.isEmpty,
          license.url.isEmpty,
          license.extensions.isEmpty
        )
      },
      test("can be constructed with name and identifier") {
        val license = License(
          name = "Apache 2.0",
          identifier = Some("Apache-2.0")
        )
        assertTrue(
          license.name == "Apache 2.0",
          license.identifier.contains("Apache-2.0"),
          license.extensions.isEmpty
        )
      },
      test("can be constructed with name and url") {
        val license = License(
          name = "Apache 2.0",
          url = Some("https://www.apache.org/licenses/LICENSE-2.0.html")
        )
        assertTrue(
          license.name == "Apache 2.0",
          license.url.contains("https://www.apache.org/licenses/LICENSE-2.0.html"),
          license.extensions.isEmpty
        )
      },
      test("cannot have both identifier and url") {
        val caught = try {
          License(
            name = "Apache 2.0",
            identifier = Some("Apache-2.0"),
            url = Some("https://www.apache.org/licenses/LICENSE-2.0.html"),
            extensions = ChunkMap.empty
          )
          None
        } catch {
          case e: IllegalArgumentException => Some(e)
        }
        assertTrue(
          caught.isDefined,
          caught.exists(_.getMessage.contains("mutually exclusive"))
        )
      },
      test("preserves extensions on construction") {
        val license = License(
          name = "MIT",
          extensions = ChunkMap(
            "x-license-category" -> Json.String("permissive"),
            "x-osi-approved"     -> Json.Boolean(true)
          )
        )
        assertTrue(
          license.extensions.size == 2,
          license.extensions.get("x-license-category").contains(Json.String("permissive")),
          license.extensions.get("x-osi-approved").contains(Json.Boolean(true))
        )
      },
      test("Schema[License] can be derived") {
        val license = License(name = "MIT")
        val schema  = Schema[License]
        assertTrue(schema != null, license != null)
      },
      test("License round-trips through DynamicValue with identifier") {
        val license = License(
          name = "MIT",
          identifier = Some("MIT"),
          extensions = ChunkMap("x-custom" -> Json.String("test"))
        )
        val result = Schema[License].fromDynamicValue(Schema[License].toDynamicValue(license))
        assertTrue(
          result.isRight,
          result.exists(_.name == "MIT"),
          result.exists(_.identifier.contains("MIT")),
          result.exists(_.extensions.contains("x-custom"))
        )
      },
      test("License round-trips through DynamicValue with url") {
        val license = License(
          name = "Apache 2.0",
          url = Some("https://www.apache.org/licenses/LICENSE-2.0.html"),
          extensions = ChunkMap("x-custom" -> Json.String("test"))
        )
        val result = Schema[License].fromDynamicValue(Schema[License].toDynamicValue(license))
        assertTrue(
          result.isRight,
          result.exists(_.name == "Apache 2.0"),
          result.exists(_.url.contains("https://www.apache.org/licenses/LICENSE-2.0.html")),
          result.exists(_.extensions.contains("x-custom"))
        )
      },
      test("License minimal round-trip exercises private constructor defaults") {
        val license = License(name = "MIT")
        val result  = Schema[License].fromDynamicValue(Schema[License].toDynamicValue(license))
        assertTrue(
          result.isRight,
          result.exists(_.name == "MIT"),
          result.exists(_.identifier.isEmpty),
          result.exists(_.url.isEmpty),
          result.exists(_.extensions.isEmpty)
        )
      }
    ),
    suite("Info")(
      test("can be constructed with required fields only") {
        val info = Info(title = "My API", version = "1.0.0")
        assertTrue(
          info.title == "My API",
          info.version == "1.0.0",
          info.summary.isEmpty,
          info.description.isEmpty,
          info.termsOfService.isEmpty,
          info.contact.isEmpty,
          info.license.isEmpty,
          info.extensions.isEmpty
        )
      },
      test("can be constructed with summary") {
        val info = Info(title = "My API", version = "1.0.0", summary = Some(doc("A brief summary")))
        assertTrue(
          info.title == "My API",
          info.version == "1.0.0",
          info.summary.contains(doc("A brief summary")),
          info.description.isEmpty
        )
      },
      test("can be constructed with description") {
        val info = Info(
          title = "My API",
          version = "1.0.0",
          description = Some(doc("A longer, more detailed description of the API"))
        )
        assertTrue(
          info.title == "My API",
          info.version == "1.0.0",
          info.summary.isEmpty,
          info.description.contains(doc("A longer, more detailed description of the API"))
        )
      },
      test("can have both summary and description (separate fields)") {
        val info = Info(
          title = "My API",
          version = "1.0.0",
          summary = Some(doc("Brief summary")),
          description = Some(doc("Detailed description"))
        )
        assertTrue(
          info.summary.contains(doc("Brief summary")),
          info.description.contains(doc("Detailed description")),
          info.summary != info.description
        )
      },
      test("can be constructed with termsOfService") {
        val info = Info(
          title = "My API",
          version = "1.0.0",
          termsOfService = Some("https://example.com/terms")
        )
        assertTrue(
          info.termsOfService.contains("https://example.com/terms")
        )
      },
      test("can be constructed with contact") {
        val info = Info(
          title = "My API",
          version = "1.0.0",
          contact = Some(
            Contact(
              name = Some("API Support"),
              email = Some("support@example.com")
            )
          )
        )
        assertTrue(
          info.contact.isDefined,
          info.contact.exists(_.name.contains("API Support")),
          info.contact.exists(_.email.contains("support@example.com"))
        )
      },
      test("can be constructed with license") {
        val info = Info(
          title = "My API",
          version = "1.0.0",
          license = Some(License(name = "MIT", identifier = Some("MIT")))
        )
        assertTrue(
          info.license.isDefined,
          info.license.exists(_.name == "MIT"),
          info.license.exists(_.identifier.contains("MIT"))
        )
      },
      test("can be constructed with all fields populated") {
        val info = Info(
          title = "Comprehensive API",
          version = "2.1.0",
          summary = Some(doc("A comprehensive test API")),
          description = Some(doc("This API demonstrates all Info fields populated")),
          termsOfService = Some("https://example.com/terms"),
          contact = Some(
            Contact(
              name = Some("API Support"),
              url = Some("https://example.com/support"),
              email = Some("support@example.com")
            )
          ),
          license = Some(License(name = "Apache 2.0", identifier = Some("Apache-2.0"))),
          extensions = ChunkMap(
            "x-api-id"   -> Json.String("api-123"),
            "x-audience" -> Json.String("external")
          )
        )
        assertTrue(
          info.title == "Comprehensive API",
          info.version == "2.1.0",
          info.summary.contains(doc("A comprehensive test API")),
          info.description.contains(doc("This API demonstrates all Info fields populated")),
          info.termsOfService.contains("https://example.com/terms"),
          info.contact.isDefined,
          info.license.isDefined,
          info.extensions.size == 2,
          info.extensions.get("x-api-id").contains(Json.String("api-123")),
          info.extensions.get("x-audience").contains(Json.String("external"))
        )
      },
      test("preserves extensions on construction") {
        val info = Info(
          title = "My API",
          version = "1.0.0",
          extensions = ChunkMap("x-custom" -> Json.String("value"))
        )
        assertTrue(
          info.extensions.size == 1,
          info.extensions.get("x-custom").contains(Json.String("value"))
        )
      },
      test("Schema[Info] can be derived") {
        val info   = Info(title = "My API", version = "1.0.0")
        val schema = Schema[Info]
        assertTrue(schema != null, info != null)
      },
      test("Info round-trips through DynamicValue with minimal fields") {
        val info   = Info(title = "Test API", version = "1.0.0")
        val result = Schema[Info].fromDynamicValue(Schema[Info].toDynamicValue(info))
        assertTrue(
          result.isRight,
          result.exists(_.title == "Test API"),
          result.exists(_.version == "1.0.0"),
          result.exists(_.summary.isEmpty),
          result.exists(_.description.isEmpty)
        )
      },
      test("Info round-trips through DynamicValue with all fields") {
        val info = Info(
          title = "Full API",
          version = "2.0.0",
          summary = Some(doc("Summary")),
          description = Some(doc("Description")),
          termsOfService = Some("https://example.com/terms"),
          contact = Some(Contact(name = Some("Support"))),
          license = Some(License(name = "MIT")),
          extensions = ChunkMap("x-custom" -> Json.String("test"))
        )
        val result = Schema[Info].fromDynamicValue(Schema[Info].toDynamicValue(info))
        assertTrue(
          result.isRight,
          result.exists(_.title == "Full API"),
          result.exists(_.version == "2.0.0"),
          result.exists(_.summary.contains(doc("Summary"))),
          result.exists(_.description.contains(doc("Description"))),
          result.exists(_.termsOfService.contains("https://example.com/terms")),
          result.exists(_.contact.isDefined),
          result.exists(_.license.isDefined),
          result.exists(_.extensions.contains("x-custom"))
        )
      }
    )
  )
}
