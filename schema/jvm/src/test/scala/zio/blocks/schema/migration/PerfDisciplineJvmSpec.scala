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
 * JVM-only source-grep enforcement for the migration hot-path zero-allocation
 * discipline.
 *
 * Checks:
 *   1. `Interpreter.scala` has ZERO `s"...${...}..."` interpolation sites AND
 *      ZERO `new MigrationError.*(` allocations inside `new Right(...)` return
 *      contexts.
 *   2. `Interpreter.scala` has ZERO `java.lang.reflect` calls — reflection is
 *      forbidden on the hot path.
 *   3. `Interpreter.scala` and `DynamicMigration.scala` have ZERO
 *      `Chunk.empty ++` concatenation patterns — intermediate Chunk
 *      allocations are forbidden on the hot path.
 *
 * All error-side interpolation lives in `new Left(new MigrationError.*(...))`
 * branches and behind `lazy val message` — the success path never
 * interpolates.
 *
 * Moved here from shared because `scala.io.Source.fromFile` cannot link on
 * Scala.js. The shared `PerfDisciplineSpec` still carries the runtime no-op
 * complement.
 */
object PerfDisciplineJvmSpec extends SchemaBaseSpec {

  private val migrationDir: String =
    "schema/shared/src/main/scala/zio/blocks/schema/migration"

  // Resolve the Interpreter.scala path relative to the sbt project root.
  // AGENTS.md shows sbt runs with cwd = repo root; source files live under
  // `schema/shared/src/main/scala/zio/blocks/schema/migration/`.
  private val interpreterPath: String =
    s"$migrationDir/Interpreter.scala"

  private val dynamicMigrationPath: String =
    s"$migrationDir/DynamicMigration.scala"

  /**
   * Reads a source file's full contents into a List[String] of lines.
   * If the file cannot be read the test fails with an explanatory assertion
   * rather than silently passing.
   */
  private def readLines(path: String): Either[String, List[String]] =
    try {
      val src = scala.io.Source.fromFile(path, "UTF-8")
      try new Right(src.getLines().toList)
      finally src.close()
    } catch {
      case e: Throwable => new Left(s"could not read $path: ${e.getMessage}")
    }

  /**
   * Checks whether a given line appears INSIDE a success-arm `Right(...)`
   * return context. The heuristic: success arms use `new Right(` or
   * `r.asInstanceOf[...]` patterns — interpolation inside a `new Right(s"...")`
   * literal would surface as `new Right(s"` on the same line. Error arms use
   * `new Left(new MigrationError.*(...))` and are allowed to contain
   * interpolation.
   */
  private def isRightSideInterpolation(line: String): Boolean = {
    val t = line.trim
    (t.contains("new Right(s\"") || t.contains("Right(s\"")) ||
    (t.contains("new Right(new MigrationError") || t.contains("Right(new MigrationError"))
  }

  /**
   * Checks whether a line contains a `java.lang.reflect` call, which is
   * forbidden in the migration hot path. Comments are deliberately not
   * stripped — mentioning `java.lang.reflect` in a comment is allowed only in
   * imported identifiers; actual usage would appear as a non-comment token on
   * the same line, which this heuristic catches.
   */
  private def hasReflection(line: String): Boolean = {
    val t = line.trim
    !t.startsWith("//") && !t.startsWith("*") &&
    (t.contains("java.lang.reflect.") || t.contains("Class.forName(") || t.contains(".getMethod(") || t.contains(".getDeclaredMethod("))
  }

  /**
   * Checks whether a line contains `Chunk.empty ++` which produces an
   * intermediate allocation on every call — forbidden on the hot path.
   * Builder-based accumulation with `ChunkBuilder` is the correct pattern.
   */
  private def hasChunkEmptyConcat(line: String): Boolean = {
    val t = line.trim
    !t.startsWith("//") && !t.startsWith("*") && t.contains("Chunk.empty ++")
  }

  def spec: Spec[TestEnvironment, Any] = suite("PerfDisciplineJvmSpec")(
    test("source-grep: Interpreter.scala has ZERO string-interpolation or MigrationError allocation inside success-arm Right(...) contexts") {
      readLines(interpreterPath) match {
        case Right(lines) =>
          val violations = lines.zipWithIndex.collect {
            case (line, idx) if isRightSideInterpolation(line) => s"line ${idx + 1}: $line"
          }
          assertTrue(violations.isEmpty) &&
          assertTrue(lines.nonEmpty)
        case Left(err) =>
          // Fail loudly rather than silently pass — the test is load-bearing.
          assertTrue(false) && assertTrue(err.nonEmpty)
      }
    },
    test("source-grep: Interpreter.scala has ZERO java.lang.reflect calls on the hot path") {
      readLines(interpreterPath) match {
        case Right(lines) =>
          val violations = lines.zipWithIndex.collect {
            case (line, idx) if hasReflection(line) => s"line ${idx + 1}: $line"
          }
          assertTrue(violations.isEmpty) &&
          assertTrue(lines.nonEmpty)
        case Left(err) =>
          assertTrue(false) && assertTrue(err.nonEmpty)
      }
    },
    test("source-grep: Interpreter.scala has ZERO Chunk.empty ++ intermediate allocations on the hot path") {
      readLines(interpreterPath) match {
        case Right(lines) =>
          val violations = lines.zipWithIndex.collect {
            case (line, idx) if hasChunkEmptyConcat(line) => s"line ${idx + 1}: $line"
          }
          assertTrue(violations.isEmpty) &&
          assertTrue(lines.nonEmpty)
        case Left(err) =>
          assertTrue(false) && assertTrue(err.nonEmpty)
      }
    },
    test("source-grep: DynamicMigration.scala has ZERO Chunk.empty ++ intermediate allocations on the hot path") {
      readLines(dynamicMigrationPath) match {
        case Right(lines) =>
          val violations = lines.zipWithIndex.collect {
            case (line, idx) if hasChunkEmptyConcat(line) => s"line ${idx + 1}: $line"
          }
          assertTrue(violations.isEmpty) &&
          assertTrue(lines.nonEmpty)
        case Left(err) =>
          assertTrue(false) && assertTrue(err.nonEmpty)
      }
    }
  )
}
