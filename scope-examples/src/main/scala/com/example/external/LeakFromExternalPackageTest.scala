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
