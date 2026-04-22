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

package zio.blocks.schema.migration

import zio.blocks.schema.SchemaBaseSpec
import zio.test._

/**
 * JVM-only source-scan enforcement for migration public API guarantees .
 * Performs the repo-file inspection that shared specs must not perform
 * (Scala.js cannot link `scala.io.Source.fromFile`). Preserves the three
 * signature assertions originally in `PublicApiSpec`:
 *
 *   - `MigrationBuilder` public signatures do not expose `DynamicOptic`
 *   - `Migration` public signatures do not expose `DynamicOptic`
 *   - `MigrationAction.at` remains the explicit allowed public exception
 */
object PublicApiJvmSpec extends SchemaBaseSpec {

  private val migrationPath: String =
    "schema/shared/src/main/scala/zio/blocks/schema/migration/Migration.scala"

  private val builderPath: String =
    "schema/shared/src/main/scala/zio/blocks/schema/migration/MigrationBuilder.scala"

  private val migrationActionPath: String =
    "schema/shared/src/main/scala/zio/blocks/schema/migration/MigrationAction.scala"

  private val scannedPaths = List(migrationPath, builderPath)

  private def readLines(path: String): Either[String, Vector[String]] =
    try {
      val src = scala.io.Source.fromFile(path, "UTF-8")
      try new Right(src.getLines().toVector)
      finally src.close()
    } catch {
      case e: Throwable => new Left(s"could not read $path: ${e.getMessage}")
    }

  private def publicSignatures(lines: Vector[String]): Vector[String] = {
    val signatures = Vector.newBuilder[String]
    var index      = 0

    while (index < lines.length) {
      val trimmed = lines(index).trim

      if (isPublicSignatureStart(trimmed)) {
        val builder = new StringBuilder(trimmed)
        var balance = parenBalance(trimmed)
        var end     = signatureEnds(trimmed, balance)
        var cursor  = index + 1

        while (!end && cursor < lines.length) {
          val next = lines(cursor).trim
          if (next.nonEmpty) {
            builder.append(' ').append(next)
            balance = balance + parenBalance(next)
            end = signatureEnds(next, balance)
          }
          cursor = cursor + 1
        }

        signatures += builder.toString
        index = cursor - 1
      }

      index = index + 1
    }

    signatures.result()
  }

  private def isPublicSignatureStart(line: String): Boolean =
    (line.startsWith("final case class ") || line.startsWith("def ")) &&
      !line.startsWith("private") &&
      !line.startsWith("protected")

  private def parenBalance(line: String): Int =
    line.count(_ == '(') - line.count(_ == ')')

  private def signatureEnds(line: String, balance: Int): Boolean =
    balance <= 0 && (line.contains("=") || line.endsWith("{") || line.contains(" extends "))

  def spec: Spec[TestEnvironment, Any] = suite("PublicApiJvmSpec")(
    test("MigrationBuilder public signatures do not expose DynamicOptic") {
      readLines(builderPath) match {
        case Right(lines) =>
          val signatures = publicSignatures(lines)
          val violations = signatures.filter(_.contains("DynamicOptic"))

          assertTrue(signatures.nonEmpty) &&
          assertTrue(violations.isEmpty)
        case Left(err) =>
          assertTrue(false) && assertTrue(err.nonEmpty)
      }
    },
    test("Migration public signatures do not expose DynamicOptic") {
      readLines(migrationPath) match {
        case Right(lines) =>
          val signatures = publicSignatures(lines)
          val violations = signatures.filter(_.contains("DynamicOptic"))

          assertTrue(signatures.nonEmpty) &&
          assertTrue(violations.isEmpty)
        case Left(err) =>
          assertTrue(false) && assertTrue(err.nonEmpty)
      }
    },
    test("MigrationAction.at remains the explicit allowed public exception") {
      readLines(migrationActionPath) match {
        case Right(lines) =>
          val contents = lines.mkString("\n")

          assertTrue(!scannedPaths.contains(migrationActionPath)) &&
          assertTrue(contents.contains("def at: DynamicOptic"))
        case Left(err) =>
          assertTrue(false) && assertTrue(err.nonEmpty)
      }
    }
  )
}
