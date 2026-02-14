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
    }
  )
}
