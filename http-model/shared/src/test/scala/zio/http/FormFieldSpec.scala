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

package zio.http

import zio.blocks.chunk.Chunk
import zio.test._

object FormFieldSpec extends HttpModelBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("FormField")(
    suite("Simple")(
      test("creation and accessors") {
        val field = FormField.Simple("username", "alice")
        assertTrue(
          field.name == "username",
          field.value == "alice"
        )
      },
      test("name accessor from FormField trait") {
        val field: FormField = FormField.Simple("key", "val")
        assertTrue(field.name == "key")
      }
    ),
    suite("Text")(
      test("creation with defaults") {
        val field = FormField.Text("bio", "hello world")
        assertTrue(
          field.name == "bio",
          field.value == "hello world",
          field.contentType.isEmpty,
          field.filename.isEmpty
        )
      },
      test("creation with content type") {
        val ct    = ContentType.`text/plain`
        val field = FormField.Text("bio", "hello", contentType = Some(ct))
        assertTrue(
          field.contentType == Some(ct)
        )
      },
      test("creation with filename") {
        val field = FormField.Text("notes", "content", filename = Some("notes.txt"))
        assertTrue(
          field.filename == Some("notes.txt")
        )
      },
      test("creation with all options") {
        val ct    = ContentType.`text/html`
        val field = FormField.Text("doc", "<p>hi</p>", contentType = Some(ct), filename = Some("doc.html"))
        assertTrue(
          field.name == "doc",
          field.value == "<p>hi</p>",
          field.contentType == Some(ct),
          field.filename == Some("doc.html")
        )
      }
    ),
    suite("Binary")(
      test("creation with content type") {
        val data  = Chunk[Byte](1, 2, 3)
        val ct    = ContentType.`application/octet-stream`
        val field = FormField.Binary("file", data, ct)
        assertTrue(
          field.name == "file",
          field.data == data,
          field.contentType == ct,
          field.filename.isEmpty
        )
      },
      test("creation with filename") {
        val data  = Chunk[Byte](0x50, 0x4b)
        val ct    = ContentType.`application/octet-stream`
        val field = FormField.Binary("upload", data, ct, filename = Some("archive.zip"))
        assertTrue(
          field.filename == Some("archive.zip")
        )
      }
    ),
    suite("pattern matching")(
      test("match Simple variant") {
        val field: FormField = FormField.Simple("k", "v")
        val result           = field match {
          case FormField.Simple(n, v)       => s"simple:$n=$v"
          case FormField.Text(n, _, _, _)   => s"text:$n"
          case FormField.Binary(n, _, _, _) => s"binary:$n"
        }
        assertTrue(result == "simple:k=v")
      },
      test("match Text variant") {
        val field: FormField = FormField.Text("t", "val")
        val result           = field match {
          case FormField.Simple(_, _)       => "simple"
          case FormField.Text(n, v, _, _)   => s"text:$n=$v"
          case FormField.Binary(_, _, _, _) => "binary"
        }
        assertTrue(result == "text:t=val")
      },
      test("match Binary variant") {
        val ct               = ContentType.`application/json`
        val field: FormField = FormField.Binary("b", Chunk.empty, ct)
        val result           = field match {
          case FormField.Simple(_, _)       => "simple"
          case FormField.Text(_, _, _, _)   => "text"
          case FormField.Binary(n, _, _, _) => s"binary:$n"
        }
        assertTrue(result == "binary:b")
      }
    )
  )
}
