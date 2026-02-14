package zio.blocks.scope

import zio.test._

import java.util.concurrent.atomic.AtomicInteger

object PoolSpec extends ZIOSpecDefault {

  final class Connection(val id: Int) {
    var state: Int = 0
  }

  def spec = suite("Pool")(
    test("Pool.unique acquires fresh values for each lease") {
      val created = new AtomicInteger(0)
      val closed  = new AtomicInteger(0)

      val connectionResource = Resource.acquireRelease {
        new Connection(created.incrementAndGet())
      } { _ =>
        closed.incrementAndGet()
      }

      val ids = Scope.global.scoped { scope =>
        import scope._
        val pool = allocate(Pool.unique(connectionResource))

        val firstId = scoped { child =>
          import child._
          val leased = for {
            p <- lower(pool)
            c <- allocate(p.lease)
          } yield c.id
          use(leased)(identity)
        }

        val secondId = scoped { child =>
          import child._
          val leased = for {
            p <- lower(pool)
            c <- allocate(p.lease)
          } yield c.id
          use(leased)(identity)
        }

        (firstId, secondId)
      }

      assertTrue(
        ids._1 != ids._2,
        created.get() == 2,
        closed.get() == 2
      )
    },
    test("Pool.unique throws when leasing after pool is closed") {
      val created = new AtomicInteger(0)
      val closed  = new AtomicInteger(0)

      val connectionResource = Resource.acquireRelease {
        new Connection(created.incrementAndGet())
      } { _ =>
        closed.incrementAndGet()
      }

      var pool: Pool[Connection] = null
      Scope.global.scoped { scope =>
        import scope._
        val allocated = allocate(Pool.unique(connectionResource))
        use(allocated)(p => pool = p)
      }

      val caught: Option[IllegalStateException] =
        try {
          Scope.global.scoped { scope =>
            import scope._
            val leased = allocate(pool.lease)
            use(leased)(_.id)
          }
          None
        } catch {
          case e: IllegalStateException => Some(e)
        }

      assertTrue(
        caught.exists(_.getMessage == "Pool is closed"),
        created.get() == 0,
        closed.get() == 0
      )
    },
    test("Pool.shared reuses values and recycles on return") {
      val created  = new AtomicInteger(0)
      val recycled = new AtomicInteger(0)
      val closed   = new AtomicInteger(0)

      val connectionResource = Resource.acquireRelease {
        new Connection(created.incrementAndGet())
      } { _ =>
        closed.incrementAndGet()
      }

      val sharedResource = SharedResource(
        connectionResource,
        (c: Connection) => {
          recycled.incrementAndGet()
          c.state = 0
        }
      )

      val observed = Scope.global.scoped { scope =>
        import scope._
        val pool = allocate(Pool.shared(sharedResource))

        val firstId = scoped { child =>
          import child._
          val leased = for {
            p <- lower(pool)
            c <- allocate(p.lease)
          } yield {
            c.state = 42
            c.id
          }
          use(leased)(identity)
        }

        val second = scoped { child =>
          import child._
          val leased = for {
            p <- lower(pool)
            c <- allocate(p.lease)
          } yield (c.id, c.state)
          use(leased)(identity)
        }

        (firstId, second)
      }

      assertTrue(
        observed._1 == observed._2._1,
        observed._2._2 == 0,
        created.get() == 1,
        recycled.get() == 2,
        closed.get() == 1
      )
    },
    test("Pool.shared remains usable after recycle failure by destroying failed entries") {
      val created         = new AtomicInteger(0)
      val recycled        = new AtomicInteger(0)
      val recycleAttempts = new AtomicInteger(0)
      val closed          = new AtomicInteger(0)

      val connectionResource = Resource.acquireRelease {
        new Connection(created.incrementAndGet())
      } { _ =>
        closed.incrementAndGet()
      }

      val sharedResource = SharedResource(
        connectionResource,
        (c: Connection) => {
          val attempt = recycleAttempts.incrementAndGet()
          if (attempt == 1) throw new RuntimeException("recycle failed")
          recycled.incrementAndGet()
          c.state = 0
        }
      )

      val (message, secondLeaseId) = Scope.global.scoped { scope =>
        import scope._
        val pool = allocate(Pool.shared(sharedResource))

        val firstMessage =
          try {
            scoped { child =>
              import child._
              val leased = for {
                p <- lower(pool)
                c <- allocate(p.lease)
              } yield {
                c.state = 1
                c.id
              }
              use(leased)(identity)
            }
            ""
          } catch {
            case e: RuntimeException => e.getMessage
          }

        val secondId = scoped { child =>
          import child._
          val leased = for {
            p <- lower(pool)
            c <- allocate(p.lease)
          } yield c.id
          use(leased)(identity)
        }

        (firstMessage, secondId)
      }

      assertTrue(
        message == "recycle failed",
        secondLeaseId == 2,
        created.get() == 2,
        recycled.get() == 1,
        recycleAttempts.get() == 2,
        closed.get() == 2
      )
    },
    test("Pool.wire creates a reusable pool") {
      val created  = new AtomicInteger(0)
      val recycled = new AtomicInteger(0)
      val closed   = new AtomicInteger(0)

      val connectionResource = Resource.acquireRelease {
        new Connection(created.incrementAndGet())
      } { _ =>
        closed.incrementAndGet()
      }

      val poolWire = Pool.wire(SharedResource(connectionResource, (_: Connection) => recycled.incrementAndGet()))

      val values = Scope.global.scoped { scope =>
        import scope._
        val pool = allocate(poolWire.toResource(zio.blocks.context.Context.empty))

        val firstId = scoped { child =>
          import child._
          val leased = for {
            p <- lower(pool)
            c <- allocate(p.lease)
          } yield c.id
          use(leased)(identity)
        }

        val secondId = scoped { child =>
          import child._
          val leased = for {
            p <- lower(pool)
            c <- allocate(p.lease)
          } yield c.id
          use(leased)(identity)
        }

        (firstId, secondId)
      }

      assertTrue(
        values._1 == values._2,
        created.get() == 1,
        recycled.get() == 2,
        closed.get() == 1
      )
    }
  )
}
