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

package stream

import zio.blocks.streams.Stream
import util.ShowExpr.show
import scala.collection.mutable.Buffer

object StreamResourceExample extends App {
  println("=== Stream Resource Management ===\n")

  // Simulated resource type
  case class Database(name: String) {
    private var closed = false

    def query(q: String): List[String] = {
      if (closed) throw new Exception("Database is closed")
      q match {
        case "users" => List("Alice", "Bob", "Charlie")
        case "ids"   => List("1", "2", "3")
        case _       => List()
      }
    }

    def close(): Unit = {
      println(s"  → Closing database: $name")
      closed = true
    }

    def isClosed: Boolean = closed
  }

  // Basic resource management
  println("1. Basic fromAcquireRelease - automatic cleanup:")
  val log = Buffer[String]()

  val managed = Stream.fromAcquireRelease(
    acquire = {
      log += "opened"
      Database("main")
    },
    release = { db =>
      db.close()
      log += "closed"
    }
  )(db => Stream.fromIterable(db.query("users")))

  val result1 = managed.runCollect
  show(result1)
  show(log.toList)

  // Ensuring cleanup
  println("\n2. Using ensuring for guaranteed cleanup:")
  log.clear()
  var finalizing = false

  val withEnsure = Stream(1, 2, 3)
    .tapEach(x => log += s"processing $x")
    .ensuring {
      finalizing = true
      log += "finalizing"
    }

  val result2 = withEnsure.runCollect
  show(result2)
  show(log.toList)
  show(finalizing)

  // Error safety
  println("\n3. Cleanup happens even on error:")
  log.clear()

  sealed trait Error
  case object ProcessingFailed extends Error

  val errorStream = Stream.fromAcquireRelease(
    acquire = {
      log += "opened"
      Database("error-test")
    },
    release = { db =>
      db.close()
      log += "closed"
    }
  )(db =>
    Stream(1, 2, 3).flatMap { x =>
      if (x == 2) Stream.fail(ProcessingFailed)
      else Stream(x)
    }
  )

  val result3 = errorStream.runCollect
  show(result3)
  show(log.toList)

  // Multiple nested resources
  println("\n4. Multiple nested resources with proper cleanup order:")
  log.clear()

  val nested = Stream.fromAcquireRelease(
    acquire = {
      log += "open db1"
      Database("db1")
    },
    release = db => {
      db.close()
      log += "close db1"
    }
  )(db1 =>
    Stream.fromAcquireRelease(
      acquire = {
        log += "open db2"
        Database("db2")
      },
      release = db2 => {
        db2.close()
        log += "close db2"
      }
    ) { db2 =>
      val data = db1.query("users") ++ db2.query("ids")
      Stream.fromIterable(data).tapEach(x => log += s"emit $x")
    }
  )

  val result4 = nested.runCollect
  show(result4)
  show(log.toList)

  // AutoCloseable integration
  println("\n5. Using AutoCloseable for simpler cleanup:")
  log.clear()

  class AutoCloseableDb extends AutoCloseable {
    def close(): Unit =
      log += "auto-closed"
  }

  val autoCloseable = Stream.fromAcquireRelease(
    acquire = {
      log += "acquired"
      new AutoCloseableDb
    }
    // release defaults to calling .close() on AutoCloseable
  )(db => Stream.succeed(42))

  val result5 = autoCloseable.runCollect
  show(result5)
  show(log.toList)
}

object X extends App {
  import zio.blocks.streams.*
  import java.io.*

  val charCount: Either[IOException, Long] =
    Stream
      .fromJavaReader(new StringReader("Hello\nWorld")) // lazily acquires reader
      .filter(!_.isWhitespace)                          // process only non-whitespace
      .count                                            // count all matching characters

  println(charCount) // prints Right(10) — count of non-whitespace characters
}
