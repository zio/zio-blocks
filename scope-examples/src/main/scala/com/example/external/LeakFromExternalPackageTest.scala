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
    import scope._
    val db: $[Database] = allocate(Resource(new Database))

    // Since $[A] = A at runtime, we can extract the raw value via cast.
    // This bypasses compile-time safety - use sparingly.
    @nowarn
    val leaked: Database = db.asInstanceOf[Database]

    println(s"Leaked database query: ${leaked.query("SELECT 1")}")
  }

  println("\n=== Test passed - leak works from external package ===")
}
