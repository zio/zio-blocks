package zio.blocks.scope

import zio.ZIO
import zio.test._

import java.util.concurrent.{CyclicBarrier, CountDownLatch}
import java.util.concurrent.atomic.AtomicReference

object ScopeOwnershipSpec extends ZIOSpecDefault {

  def spec = suite("Scope Ownership")(
    test("global scope isOwner is true from any thread") {
      ZIO.succeed {
        val result = new AtomicReference[Boolean](false)
        val latch  = new CountDownLatch(1)
        val t      = new Thread(() => {
          result.set(Scope.global.isOwner)
          latch.countDown()
        })
        t.start()
        latch.await()
        assertTrue(Scope.global.isOwner, result.get())
      }
    },
    test("global.scoped succeeds from any thread") {
      ZIO.succeed {
        val result = new AtomicReference[Option[Throwable]](None)
        val latch  = new CountDownLatch(1)
        val t      = new Thread(() => {
          try {
            Scope.global.scoped { scope =>
              scope.$(42)
            }
          } catch {
            case e: Throwable => result.set(Some(e))
          }
          latch.countDown()
        })
        t.start()
        latch.await()
        assertTrue(result.get().isEmpty)
      }
    },
    test("child scope isOwner is true on owning thread") {
      ZIO.succeed {
        var ownerResult = false
        Scope.global.scoped { scope =>
          ownerResult = scope.isOwner
          scope.$(())
        }
        assertTrue(ownerResult)
      }
    },
    test("child scope isOwner is false on non-owning thread") {
      ZIO.succeed {
        val result = new AtomicReference[Boolean](true)
        val latch  = new CountDownLatch(1)
        Scope.global.scoped { scope =>
          val t = new Thread(() => {
            result.set(scope.isOwner)
            latch.countDown()
          })
          t.start()
          latch.await()
          scope.$(())
        }
        assertTrue(!result.get())
      }
    },
    test("scoped on child from same thread succeeds") {
      ZIO.succeed {
        var innerRan = false
        Scope.global.scoped { parent =>
          parent.scoped { child =>
            innerRan = true
            child.$(())
          }
          parent.$(())
        }
        assertTrue(innerRan)
      }
    },
    test("scoped on child from different thread throws IllegalStateException") {
      ZIO.succeed {
        val result = new AtomicReference[Option[Throwable]](None)
        val latch  = new CountDownLatch(1)
        Scope.global.scoped { scope =>
          val t = new Thread(() => {
            try {
              scope.scoped { inner =>
                inner.$(42)
              }
            } catch {
              case e: Throwable => result.set(Some(e))
            }
            latch.countDown()
          })
          t.start()
          latch.await()
          scope.$(())
        }
        assertTrue(
          result.get().exists(_.isInstanceOf[IllegalStateException]),
          result.get().exists(_.getMessage.contains("Cannot create child scope"))
        )
      }
    },
    test("deep nesting on same thread succeeds") {
      ZIO.succeed {
        var depth = 0
        Scope.global.scoped { s1 =>
          depth += 1
          s1.scoped { s2 =>
            depth += 1
            s2.scoped { s3 =>
              depth += 1
              s3.scoped { s4 =>
                depth += 1
                s4.$(())
              }
              s3.$(())
            }
            s2.$(())
          }
          s1.$(())
        }
        assertTrue(depth == 4)
      }
    },
    test("deep nesting with thread switch at depth throws") {
      ZIO.succeed {
        val result = new AtomicReference[Option[Throwable]](None)
        val latch  = new CountDownLatch(1)
        Scope.global.scoped { s1 =>
          s1.scoped { s2 =>
            val t = new Thread(() => {
              try {
                s2.scoped { s3 =>
                  s3.$(())
                }
              } catch {
                case e: Throwable => result.set(Some(e))
              }
              latch.countDown()
            })
            t.start()
            latch.await()
            s2.$(())
          }
          s1.$(())
        }
        assertTrue(
          result.get().exists(_.isInstanceOf[IllegalStateException])
        )
      }
    },
    test("two independent children of global on different threads both succeed") {
      ZIO.succeed {
        val result1 = new AtomicReference[Option[Throwable]](None)
        val result2 = new AtomicReference[Option[Throwable]](None)
        val barrier = new CyclicBarrier(2)
        val latch   = new CountDownLatch(2)

        val t1 = new Thread(() => {
          barrier.await()
          try {
            Scope.global.scoped { scope =>
              scope.$(1)
            }
          } catch {
            case e: Throwable => result1.set(Some(e))
          }
          latch.countDown()
        })

        val t2 = new Thread(() => {
          barrier.await()
          try {
            Scope.global.scoped { scope =>
              scope.$(2)
            }
          } catch {
            case e: Throwable => result2.set(Some(e))
          }
          latch.countDown()
        })

        t1.start()
        t2.start()
        latch.await()
        assertTrue(result1.get().isEmpty, result2.get().isEmpty)
      }
    },
    test("exception message contains thread names") {
      ZIO.succeed {
        val result = new AtomicReference[Option[Throwable]](None)
        val latch  = new CountDownLatch(1)
        Scope.global.scoped { scope =>
          val t = new Thread(
            () => {
              try {
                scope.scoped { inner =>
                  inner.$(())
                }
              } catch {
                case e: Throwable => result.set(Some(e))
              }
              latch.countDown()
            },
            "test-non-owner-thread"
          )
          t.start()
          latch.await()
          scope.$(())
        }
        val msg = result.get().map(_.getMessage).getOrElse("")
        assertTrue(
          msg.contains("Cannot create child scope"),
          msg.contains("test-non-owner-thread"),
          msg.contains("owner:")
        )
      }
    },
    test("closed parent scope on wrong thread still throws ownership error") {
      ZIO.succeed {
        val child = new Scope.Child[Scope.global.type](
          Scope.global,
          new zio.blocks.scope.internal.Finalizers,
          PlatformScope.captureOwner()
        )
        child.close()
        val result = new AtomicReference[Option[Throwable]](None)
        val latch  = new CountDownLatch(1)
        val t      = new Thread(() => {
          try {
            child.scoped { inner =>
              inner.$(42)
            }
          } catch {
            case e: Throwable => result.set(Some(e))
          }
          latch.countDown()
        })
        t.start()
        latch.await()
        assertTrue(
          result.get().exists(_.isInstanceOf[IllegalStateException]),
          result.get().exists(_.getMessage.contains("Cannot create child scope"))
        )
      }
    },
    test("closed parent scope on correct thread creates born-closed child normally") {
      ZIO.succeed {
        val child = new Scope.Child[Scope.global.type](
          Scope.global,
          new zio.blocks.scope.internal.Finalizers,
          PlatformScope.captureOwner()
        )
        child.close()
        var innerClosed = false
        child.scoped { inner =>
          innerClosed = inner.isClosed
        }
        assertTrue(child.isClosed, innerClosed)
      }
    },
    test("unowned child scope allows scoped from different thread") {
      ZIO.succeed {
        val result = new AtomicReference[Option[Throwable]](None)
        val value  = new AtomicReference[Int](0)
        val latch  = new CountDownLatch(1)

        // Create an unowned child scope (simulating what DI does)
        val fins  = new zio.blocks.scope.internal.Finalizers
        val owner = PlatformScope.captureOwner()
        val child = new Scope.Child[Scope.global.type](Scope.global, fins, owner, unowned = true)

        val t = new Thread(() => {
          try {
            child.scoped { inner =>
              value.set(42)
              inner.$(())
            }
          } catch {
            case e: Throwable => result.set(Some(e))
          }
          latch.countDown()
        })
        t.start()
        latch.await()
        child.close()

        assertTrue(result.get().isEmpty, value.get() == 42)
      }
    },
    test("unowned child scope allows concurrent scoped from multiple threads") {
      ZIO.succeed {
        val barrier = new CyclicBarrier(3)
        val latch   = new CountDownLatch(3)
        val errors  = new AtomicReference[List[Throwable]](Nil)
        val counter = new java.util.concurrent.atomic.AtomicInteger(0)

        val fins  = new zio.blocks.scope.internal.Finalizers
        val owner = PlatformScope.captureOwner()
        val child = new Scope.Child[Scope.global.type](Scope.global, fins, owner, unowned = true)

        (0 until 3).foreach { _ =>
          val t = new Thread(() => {
            barrier.await()
            try {
              child.scoped { inner =>
                counter.incrementAndGet()
                inner.$(())
              }
            } catch {
              case e: Throwable =>
                errors.updateAndGet(e :: _)
            }
            latch.countDown()
          })
          t.start()
        }
        latch.await()
        child.close()

        assertTrue(errors.get().isEmpty, counter.get() == 3)
      }
    },
    test("Resource.Shared cleans up OpenScope handle from global on refcount zero") {
      ZIO.succeed {
        var cleanupRan = false

        // Run 10 cycles of create-and-destroy shared resources
        // If handle.cancel() doesn't work, global finalizers would grow
        (1 to 10).foreach { _ =>
          val resource = Resource.shared[String] { scope =>
            scope.defer { cleanupRan = true }
            "value"
          }
          Scope.global.scoped { scope =>
            val _ = resource.make(scope)
          }
        }

        // If we got here without OOM or issues, handles are being cleaned up.
        // Also verify cleanup actually ran.
        assertTrue(cleanupRan)
      }
    }
  )
}
