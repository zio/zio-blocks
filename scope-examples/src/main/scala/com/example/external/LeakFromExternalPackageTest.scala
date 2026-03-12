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

package com.example.external

import scala.annotation.nowarn
import zio.blocks.scope._

/**
 * Test that the `leak` macro works from a package completely outside
 * zio.blocks.scope hierarchy.
 *
 * This verifies that the leak macro expansion works correctly at external call
 * sites, even though `$run` is `private[scope]`.
 */
@nowarn("msg=.*leaked.*|.*leak.*")
@main def leakFromExternalPackageTest(): Unit = {
  println("=== Testing leak from external package ===\n")

  class Database extends AutoCloseable {
    def query(sql: String): String = s"result: $sql"
    def close(): Unit              = println("[Database] closed")
  }

  Scope.global.scoped { scope =>
    import scope._
    val db: $[Database] = allocate(Resource(new Database))

    // Interop: passing the database to external code requires the raw value;
    // use leak() as an explicit escape hatch (emits a compiler warning)
    val leaked: Database = leak(db)

    println(s"Leaked database query: ${leaked.query("SELECT 1")}")
  }

  println("\n=== Test passed - leak works from external package ===")
}
