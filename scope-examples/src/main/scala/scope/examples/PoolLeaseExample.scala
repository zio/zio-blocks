package scope.examples

import java.util.concurrent.atomic.AtomicInteger
import zio.blocks.scope._

/**
 * Demonstrates pool leasing with SharedResource + Pool.lease.
 */
final class PoolLeaseConnection(var dirty: Boolean, val id: Int) extends AutoCloseable {
  def query(sql: String): String = s"conn=$id -> $sql"
  def close(): Unit              = println(s"closing connection $id")
}

@main def poolLeaseExample(): Unit = {
  val created = new AtomicInteger(0)

  val pooledConnection: SharedResource[PoolLeaseConnection] =
    SharedResource(
      Resource.acquireRelease {
        new PoolLeaseConnection(dirty = false, id = created.incrementAndGet())
      }(_.close()),
      c => c.dirty = false
    )

  Scope.global.scoped { scope =>
    import scope._
    val pool = allocate(Pool.shared(pooledConnection))

    val first = scoped { child =>
      import child._
      val leased = for {
        p <- lower(pool)
        c <- allocate(p.lease)
      } yield {
        c.dirty = true
        c.query("SELECT 1")
      }
      use(leased)(identity)
    }

    val second = scoped { child =>
      import child._
      val leased = for {
        p <- lower(pool)
        c <- allocate(p.lease)
      } yield (c.id, c.dirty, c.query("SELECT 2"))
      use(leased)(identity)
    }

    println(s"first: $first")
    println(s"second (id reused + cleaned): $second")
  }
}
