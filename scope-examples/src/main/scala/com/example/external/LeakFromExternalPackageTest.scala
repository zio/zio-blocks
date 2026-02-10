package com.example.external

import scala.annotation.nowarn
import zio.blocks.scope._

/**
 * Test that the `leak` macro works from a package completely outside
 * zio.blocks.scope hierarchy.
 *
 * This verifies whether `private[scope] run()` is accessible when the leak
 * macro expands at an external call site.
 */
@main def leakFromExternalPackageTest(): Unit = {
  println("=== Testing leak from external package ===\n")

  class Database extends AutoCloseable {
    def query(sql: String): String = s"result: $sql"
    def close(): Unit              = println("[Database] closed")
  }

  Scope.global.scoped { scope =>
    val db: Database @@ scope.Tag = scope.allocate(Resource(new Database))

    // This uses the leak macro from an external package
    // If private[scope] run() is not accessible, this will fail to compile
    @nowarn("msg=is being leaked")
    val leaked: Database = leak(db)

    println(s"Leaked database query: ${leaked.query("SELECT 1")}")
  }

  println("\n=== Test passed - leak works from external package ===")
}
