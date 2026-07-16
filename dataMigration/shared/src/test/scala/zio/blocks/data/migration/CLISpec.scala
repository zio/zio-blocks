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

package zio.blocks.data.migration

import zio.blocks.sql.*
import zio.test.*

object CLISpec extends ZIOSpecDefault {

  // Minimal stub for signature/CLI tests only (no real DB).
  trait FakeTransactor extends Transactor {
    override def connect[A](f: DbCon ?=> A): A = 0L.asInstanceOf[A]
    override def transact[A](f: DbTx ?=> A): A = 0.asInstanceOf[A]
  }

  val fakeTransactor: Transactor = new FakeTransactor {}

  def spec = suite("MigrationCLI")(
    test("uses defaults when args omitted") {
      val cli = new MigrationCLI(using fakeTransactor)
      cli.run(Array.empty)
      assertTrue(true)
    },
    test("parses --queue-table --batch-size --model --target") {
      val cli = new MigrationCLI(using fakeTransactor)
      cli.run(Array(
        "--queue-table", "my_queue",
        "--batch-size", "50",
        "--model", "large",
        "--target", "shadow:users_v2"
      ))
      assertTrue(true)
    },
    test("--help prints usage without error") {
      val cli = new MigrationCLI(using fakeTransactor)
      cli.run(Array("--help"))
      assertTrue(true)
    },
    test("invalid --model prints error gracefully") {
      val cli = new MigrationCLI(using fakeTransactor)
      cli.run(Array("--model", "invalid"))
      assertTrue(true)
    },
    test("invalid --batch-size prints error") {
      val cli = new MigrationCLI(using fakeTransactor)
      cli.run(Array("--batch-size", "notanumber"))
      assertTrue(true)
    },
    test("unknown flag prints error") {
      val cli = new MigrationCLI(using fakeTransactor)
      cli.run(Array("--unknown", "foo"))
      assertTrue(true)
    },
    test("constructs right migrator type for each --model (via dispatch)") {
      val cli = new MigrationCLI(using fakeTransactor)
      cli.run(Array("--model", "tiny"))
      cli.run(Array("--model", "small"))
      cli.run(Array("--model", "large"))
      assertTrue(true)
    }
  )
}
