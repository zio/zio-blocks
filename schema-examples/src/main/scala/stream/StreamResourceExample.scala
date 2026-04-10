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
  show(
    """Stream.fromAcquireRelease(
      |  acquire = Database("main"),
      |  release = _.close()
      |)(db => Stream.fromIterable(db.query("users")))
      |.runCollect""".stripMargin
  )(result1)
  show("Resource lifecycle")(log.toList)

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
  show(
    """Stream(1, 2, 3)
      |  .tapEach(x => println(x))
      |  .ensuring(cleanup)
      |  .runCollect""".stripMargin
  )(result2)
  show("Cleanup order")(log.toList)
  show("Finalizing was called")(finalizing)

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
  show(
    """Stream with error:
      |resource still cleaned up""".stripMargin
  )(result3)
  show("Lifecycle even on error")(log.toList)

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
  show(
    """Stream with nested resources:
      |db1.fromAcquireRelease(
      |  db2.fromAcquireRelease(...)
      |)""".stripMargin
  )(result4)
  show("Nested cleanup order (LIFO)")(log.toList)

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
  show(
    """Stream.fromAcquireRelease(
      |  acquire = new AutoCloseableDb()
      |  // release defaults to .close()
      |)(db => Stream.succeed(42))
      |.runCollect""".stripMargin
  )(result5)
  show("AutoCloseable cleanup")(log.toList)
}
