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

package zio.blocks.config.hocon

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import zio.test._

object HoconConfigSourcePlatformSpec extends ZIOSpecDefault {

  private def writeFile(path: Path, content: String): Path = {
    Files.createDirectories(path.getParent)
    Files.write(path, content.getBytes(StandardCharsets.UTF_8))
  }

  private def deleteRecursively(path: Path): Unit =
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try {
        val iter = stream.sorted(java.util.Comparator.reverseOrder()).iterator()
        while (iter.hasNext) Files.deleteIfExists(iter.next())
      } finally stream.close()
    }

  def spec: Spec[Any, Any] = suite("HoconConfigSourcePlatformSpec")(
    test("ConfigSource.fromFile rejects root files outside allowedBase even when the path shares a string prefix") {
      val root = Files.createTempDirectory("hocon-platform-root-cfgsrc-")
      try {
        val allowedBase = root.resolve("foo")
        val siblingBase = root.resolve("foobar")

        Files.createDirectories(allowedBase)
        Files.createDirectories(siblingBase)

        val target = writeFile(siblingBase.resolve("app.conf"), "db.host = \"outside\"")
        val result = zio.blocks.config.ConfigSource.fromFile(target.toString, Some(allowedBase.toFile))

        assertTrue(result.isLeft)
      } finally deleteRecursively(root)
    },
    test("fromFile rejects root files outside allowedBase even when the path shares a string prefix") {
      val root = Files.createTempDirectory("hocon-platform-root-")
      try {
        val allowedBase = root.resolve("foo")
        val siblingBase = root.resolve("foobar")

        Files.createDirectories(allowedBase)
        Files.createDirectories(siblingBase)

        val target = writeFile(siblingBase.resolve("app.conf"), "db.host = \"outside\"")
        val result = HoconConfigSourcePlatform.fromFile(target.toString, Some(allowedBase.toFile))

        assertTrue(result.isLeft)
      } finally deleteRecursively(root)
    },
    test("fromFile rejects included files outside allowedBase even when the path shares a string prefix") {
      val root = Files.createTempDirectory("hocon-platform-include-base-")
      try {
        val allowedBase = root.resolve("foo")
        val siblingBase = root.resolve("foobar")

        Files.createDirectories(allowedBase)
        Files.createDirectories(siblingBase)

        val main = writeFile(allowedBase.resolve("app.conf"), "include \"../foobar/secret.conf\"")
        writeFile(siblingBase.resolve("secret.conf"), "secret.value = \"outside\"")

        val result = HoconConfigSourcePlatform.fromFile(main.toString, Some(allowedBase.toFile))

        assertTrue(result.isLeft)
      } finally deleteRecursively(root)
    },
    test("fromFile counts include nesting depth instead of total sibling includes") {
      val root = Files.createTempDirectory("hocon-platform-depth-")
      try {
        val base = root.resolve("conf")

        Files.createDirectories(base)

        val main = writeFile(
          base.resolve("application.conf"),
          """include "first.conf"
            |include "second.conf"""".stripMargin
        )
        writeFile(base.resolve("first.conf"), "first.value = 1")
        writeFile(base.resolve("second.conf"), "second.value = 2")

        val result = HoconConfigSourcePlatform.fromFile(main.toString, Some(base.toFile), maxIncludeDepth = 1)

        assertTrue(
          result.toOption.exists { source =>
            source.get("first.value").exists(_.value == "1") &&
            source.get("second.value").exists(_.value == "2")
          }
        )
      } finally deleteRecursively(root)
    },
    test("fromFile resolves nested includes relative to the including file") {
      val root = Files.createTempDirectory("hocon-platform-nested-relative-")
      try {
        val base = root.resolve("conf")

        Files.createDirectories(base.resolve("nested"))

        val main = writeFile(base.resolve("application.conf"), "include \"nested/first.conf\"")
        writeFile(
          base.resolve("nested/first.conf"),
          """include "../second.conf"
            |first.value = 1""".stripMargin
        )
        writeFile(base.resolve("second.conf"), "second.value = 2")

        val result = HoconConfigSourcePlatform.fromFile(main.toString, Some(base.toFile), maxIncludeDepth = 2)

        assertTrue(
          result.toOption.exists { source =>
            source.get("first.value").exists(_.value == "1") &&
            source.get("second.value").exists(_.value == "2")
          }
        )
      } finally deleteRecursively(root)
    }
  )
}
