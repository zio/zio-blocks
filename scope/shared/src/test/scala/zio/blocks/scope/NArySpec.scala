package zio.blocks.scope

import zio.test._
import zio.test.Assertion.isLeft

/**
 * Tests for the N-ary $ operator and related pre-existing bug fixes.
 *
 * Cross-platform (Scala 2 + Scala 3, JVM + JS).
 */
object NArySpec extends ZIOSpecDefault {

  // ── Shared test resources ────────────────────────────────────────────────

  class Db extends AutoCloseable {
    var closed                       = false
    def query(s: String)             = s"result:$s"
    def tag()                        = "db"
    def key()                        = "k"
    def getConfig()                  = "cfg"
    def queryWith(s: String)         = s"queryWith:$s"
    def result()                     = "r"
    def other()                      = "o"
    def method(a: String, b: String) = s"$a,$b"
    def isActive                     = !closed
    def close()                      = closed = true
  }

  class Cach extends AutoCloseable {
    var closed         = false
    def get(k: String) = s"cached:$k"
    def key()          = "ck"
    def result()       = "cr"
    def other()        = "co"
    def close()        = closed = true
  }

  class Loggr extends AutoCloseable {
    val lines           = scala.collection.mutable.ArrayBuffer.empty[String]
    def info(s: String) = { lines += s; () }
    def tag()           = "log"
    def close()         = ()
  }

  // A non-Unscoped type to test $[B] re-wrapping
  class Wrappd(val v: String) extends AutoCloseable {
    def close() = ()
  }

  def spec = suite("N-ary $")(
    // ── Positive runtime tests ─────────────────────────────────────────────
    suite("positive — runtime")(
      test("N=2: first param as receiver") {
        val r: String = Scope.global.scoped { scope =>
          import scope._
          val db: $[Db]  = allocate(Resource.from[Db])
          val c: $[Cach] = allocate(Resource.from[Cach])
          $(db, c)((d, _) => d.query("a"))
        }
        assertTrue(r == "result:a")
      },
      test("N=2: second param as receiver") {
        val r: String = Scope.global.scoped { scope =>
          import scope._
          val db: $[Db]  = allocate(Resource.from[Db])
          val c: $[Cach] = allocate(Resource.from[Cach])
          $(db, c)((_, cc) => cc.get("k"))
        }
        assertTrue(r == "cached:k")
      },
      test("N=2: both params used, results combined") {
        val r: String = Scope.global.scoped { scope =>
          import scope._
          val db: $[Db]  = allocate(Resource.from[Db])
          val c: $[Cach] = allocate(Resource.from[Cach])
          $(db, c)((d, cc) => d.query("a") + "|" + cc.get("b"))
        }
        assertTrue(r == "result:a|cached:b")
      },
      test("N=2: result of second param fed to first param (feeding values to each other)") {
        val r: String = Scope.global.scoped { scope =>
          import scope._
          val db: $[Db]  = allocate(Resource.from[Db])
          val c: $[Cach] = allocate(Resource.from[Cach])
          $(db, c)((d, cc) => d.queryWith(cc.key()))
        }
        assertTrue(r == "queryWith:ck")
      },
      test("N=2: result of first param fed to second param (roles reversed)") {
        val r: String = Scope.global.scoped { scope =>
          import scope._
          val db: $[Db]  = allocate(Resource.from[Db])
          val c: $[Cach] = allocate(Resource.from[Cach])
          $(db, c)((d, cc) => cc.get(d.key()))
        }
        assertTrue(r == "cached:k")
      },
      test("N=2: second param used twice as receiver in same call") {
        val r: String = Scope.global.scoped { scope =>
          import scope._
          val db: $[Db]  = allocate(Resource.from[Db])
          val c: $[Cach] = allocate(Resource.from[Cach])
          $(db, c)((d, cc) => d.method(cc.result(), cc.other()))
        }
        assertTrue(r == "cr,co")
      },
      test("N=3: all three params as receivers") {
        val r: String = Scope.global.scoped { scope =>
          import scope._
          val db: $[Db]    = allocate(Resource.from[Db])
          val c: $[Cach]   = allocate(Resource.from[Cach])
          val lg: $[Loggr] = allocate(Resource.from[Loggr])
          $(db, c, lg)((d, cc, l) => d.query("a") + cc.get("b") + l.tag())
        }
        assertTrue(r == "result:acached:blog")
      },
      test("N=2: B is Unscoped — return type inferred as B (auto-unwrap)") {
        val r: String = Scope.global.scoped { scope =>
          import scope._
          val db: $[Db]  = allocate(Resource.from[Db])
          val c: $[Cach] = allocate(Resource.from[Cach])
          $(db, c)((d, cc) => d.query(cc.key()))
        }
        assertTrue(r == "result:ck")
      },
      test("N=2: B not Unscoped — n-ary $ result is $[B], usable with unary $") {
        // Wrappd is AutoCloseable with no Unscoped instance.
        // The applyN2 macro checks Unscoped[Wrappd] at expansion time, finds none,
        // and emits result.asInstanceOf[self.type#$[Wrappd]] — the re-wrap branch.
        // We verify: (a) the result is assignable to $[Wrappd] explicitly, and
        // (b) the wrapped value is usable via the unary $.
        var closedCount = 0
        val r: String   = Scope.global.scoped { scope =>
          import scope._
          val db: $[Db]  = allocate(Resource.from[Db])
          val c: $[Cach] = allocate(Resource.from[Cach])
          // Explicit type annotation $[Wrappd] — would fail pre-fix with the
          // intersection type `Wrappd & $[Wrappd]` produced by summonFrom.
          val w: $[Wrappd] = $(db, c)((d, cc) => new Wrappd(d.query(cc.key())))
          defer(closedCount += 1)
          $(w)(_.v)
        }
        assertTrue(r == "result:ck", closedCount == 1)
      },

      test("N=2: one value is lower()-ed from parent scope") {
        val r: String = Scope.global.scoped { outer =>
          val outerDb: outer.$[Db] = outer.allocate(Resource.from[Db])
          outer.scoped { inner =>
            import inner._
            val innerDb: inner.$[Db] = lower(outerDb)
            val c: inner.$[Cach]     = allocate(Resource.from[Cach])
            $(innerDb, c)((d, cc) => d.query(cc.key()))
          }
        }
        assertTrue(r == "result:ck")
      },
      test("N=2: on Scope.global directly (type $[A]=A path)") {
        val db        = new Db
        val cache     = new Cach
        val r: String = (Scope.global.$(db, cache))((d, c) => d.query(c.key()))
        assertTrue(r == "result:ck")
      },
      test("N=2: types inferred without annotation on Child scope") {
        val r = Scope.global.scoped { scope =>
          import scope._
          val db    = allocate(Resource.from[Db])
          val cache = allocate(Resource.from[Cach])
          $(db, cache)((d, c) => d.query(c.key()))
        }
        assertTrue(r == "result:ck")
      },
      test("N=3: B not Unscoped — $[B] re-wrap path") {
        var count     = 0
        val r: String = Scope.global.scoped { scope =>
          import scope._
          val db: $[Db]    = allocate(Resource.from[Db])
          val c: $[Cach]   = allocate(Resource.from[Cach])
          val lg: $[Loggr] = allocate(Resource.from[Loggr])
          val w: $[Wrappd] =
            $(db, c, lg)((d, cc, l) => new Wrappd(d.query(cc.key()) + l.tag()))
          defer(count += 1)
          $(w)(_.v)
        }
        assertTrue(r == "result:cklog", count == 1)
      },
      test("N=4: all four params as receivers") {
        val r: String = Scope.global.scoped { scope =>
          import scope._
          val db: $[Db]    = allocate(Resource.from[Db])
          val c1: $[Cach]  = allocate(Resource.from[Cach])
          val c2: $[Cach]  = allocate(Resource.from[Cach])
          val lg: $[Loggr] = allocate(Resource.from[Loggr])
          $(db, c1, c2, lg)((d, a, b, l) => d.query(a.key()) + b.get(l.tag()))
        }
        assertTrue(r == "result:ckcached:log")
      },
      test("N=4: B not Unscoped — $[B] re-wrap path") {
        var count     = 0
        val r: String = Scope.global.scoped { scope =>
          import scope._
          val db: $[Db]    = allocate(Resource.from[Db])
          val c1: $[Cach]  = allocate(Resource.from[Cach])
          val c2: $[Cach]  = allocate(Resource.from[Cach])
          val lg: $[Loggr] = allocate(Resource.from[Loggr])
          val w: $[Wrappd] =
            $(db, c1, c2, lg)((d, a, b, l) => new Wrappd(d.query(a.key()) + b.result() + l.tag()))
          defer(count += 1)
          $(w)(_.v)
        }
        assertTrue(r == "result:ckcr" + "log", count == 1)
      },
      test("N=5: all five params as receivers") {
        val r: String = Scope.global.scoped { scope =>
          import scope._
          val db: $[Db]    = allocate(Resource.from[Db])
          val c1: $[Cach]  = allocate(Resource.from[Cach])
          val c2: $[Cach]  = allocate(Resource.from[Cach])
          val c3: $[Cach]  = allocate(Resource.from[Cach])
          val lg: $[Loggr] = allocate(Resource.from[Loggr])
          $(db, c1, c2, c3, lg)((d, a, b, cc, l) => d.query("x") + a.key() + b.result() + cc.other() + l.tag())
        }
        assertTrue(r == "result:xckcrcolog")
      },
      test("N=5: B not Unscoped — $[B] re-wrap path") {
        var count     = 0
        val r: String = Scope.global.scoped { scope =>
          import scope._
          val db: $[Db]    = allocate(Resource.from[Db])
          val c1: $[Cach]  = allocate(Resource.from[Cach])
          val c2: $[Cach]  = allocate(Resource.from[Cach])
          val c3: $[Cach]  = allocate(Resource.from[Cach])
          val lg: $[Loggr] = allocate(Resource.from[Loggr])
          val w: $[Wrappd] =
            $(db, c1, c2, c3, lg)((d, a, b, cc, l) =>
              new Wrappd(d.query("x") + a.key() + b.result() + cc.other() + l.tag())
            )
          defer(count += 1)
          $(w)(_.v)
        }
        assertTrue(r == "result:xckcrcolog", count == 1)
      },
      test("N=1 regression — existing unary $ still works") {
        val r: String = Scope.global.scoped { scope =>
          import scope._
          val db: $[Db] = allocate(Resource.from[Db])
          $(db)(_.query("SELECT 1"))
        }
        assertTrue(r == "result:SELECT 1")
      },
      test("bug-fix regression: while loop with param as receiver allowed") {
        val r: String = Scope.global.scoped { scope =>
          import scope._
          val db: $[Db] = allocate(Resource.from[Db])
          $(db) { d =>
            var out = ""
            var i   = 0
            while (i < 3) { out = out + d.query(i.toString); i += 1 }
            out
          }
        }
        assertTrue(r == "result:0result:1result:2")
      },
      test("nested $ composition — each lambda uses only its own param") {
        // When composing nested $ calls, each lambda must only use its own param.
        // To combine results from multiple resources, extract values in sequence
        // and combine the plain results (which are Unscoped and freely composable).
        val r: String = Scope.global.scoped { scope =>
          import scope._
          val db1: $[Db] = allocate(Resource.from[Db])
          val db2: $[Db] = allocate(Resource.from[Db])
          val db3: $[Db] = allocate(Resource.from[Db])
          val q1: String = $(db1)(_.query("a")) // plain String; freely usable
          val q2: String = $(db2)(_.query("b"))
          val q3: String = $(db3)(_.tag())
          q1 + q2 + q3
        }
        assertTrue(r == "result:aresult:bdb")
      }
    ),

    // ── Negative compile-time tests ────────────────────────────────────────
    suite("negative — compile-time")(
      test("N=2: first param (p1) passed as bare arg to external fn") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._
          class MyDB extends AutoCloseable { def query(s: String) = s; def close() = () }
          class MyCache extends AutoCloseable { def key() = "k"; def close() = () }
          var sink: Any = null
          Scope.global.scoped { scope =>
            import scope._
            val db: $[MyDB]    = allocate(Resource(new MyDB))
            val c: $[MyCache]  = allocate(Resource(new MyCache))
            $(db, c)((p1, p2) => { sink = p1; p2.key() })
            ()
          }
        """))(isLeft)
      },
      test("N=2: second param (p2) returned directly") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._
          class MyDB extends AutoCloseable { def close() = () }
          class MyCache extends AutoCloseable { def close() = () }
          Scope.global.scoped { scope =>
            import scope._
            val db: $[MyDB]   = allocate(Resource(new MyDB))
            val c: $[MyCache] = allocate(Resource(new MyCache))
            $(db, c)((p1, p2) => p2)
            ()
          }
        """))(isLeft)
      },
      test("N=2: first param stored in external var") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._
          class MyDB extends AutoCloseable { def close() = () }
          class MyCache extends AutoCloseable { def close() = () }
          var stash: Any = null
          Scope.global.scoped { scope =>
            import scope._
            val db: $[MyDB]   = allocate(Resource(new MyDB))
            val c: $[MyCache] = allocate(Resource(new MyCache))
            $(db, c)((p1, p2) => { stash = p1; p2.hashCode() })
            ()
          }
        """))(isLeft)
      },
      test("N=2: second param captured in nested lambda") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._
          class MyDB extends AutoCloseable { def close() = () }
          class MyCache extends AutoCloseable { def close() = () }
          Scope.global.scoped { scope =>
            import scope._
            val db: $[MyDB]   = allocate(Resource(new MyDB))
            val c: $[MyCache] = allocate(Resource(new MyCache))
            $(db, c)((p1, p2) => () => p2.hashCode())
            ()
          }
        """))(isLeft)
      },
      test("N=2: first param wrapped in tuple") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._
          class MyDB extends AutoCloseable { def close() = () }
          class MyCache extends AutoCloseable { def close() = () }
          Scope.global.scoped { scope =>
            import scope._
            val db: $[MyDB]   = allocate(Resource(new MyDB))
            val c: $[MyCache] = allocate(Resource(new MyCache))
            $(db, c)((p1, p2) => (p1, p2.hashCode()))
            ()
          }
        """))(isLeft)
      },
      test("N=2: first param as constructor arg") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._
          class MyDB extends AutoCloseable { def close() = () }
          class MyCache extends AutoCloseable { def close() = () }
          case class Wrapper(v: Any)
          Scope.global.scoped { scope =>
            import scope._
            val db: $[MyDB]   = allocate(Resource(new MyDB))
            val c: $[MyCache] = allocate(Resource(new MyCache))
            $(db, c)((p1, p2) => Wrapper(p1))
            ()
          }
        """))(isLeft)
      },
      test("N=2: if-branch returning bare second param") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._
          class MyDB extends AutoCloseable { def close() = () }
          class MyCache extends AutoCloseable { def close() = () }
          Scope.global.scoped { scope =>
            import scope._
            val db: $[MyDB]   = allocate(Resource(new MyDB))
            val c: $[MyCache] = allocate(Resource(new MyCache))
            $(db, c)((p1, p2) => if (true) p2 else null)
            ()
          }
        """))(isLeft)
      },
      test("N=2: p2 as bare arg to p1's method — rejected") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._
          class MyDB extends AutoCloseable { def method(x: Any) = x.toString; def close() = () }
          class MyCache extends AutoCloseable { def close() = () }
          Scope.global.scoped { scope =>
            import scope._
            val db: $[MyDB]   = allocate(Resource(new MyDB))
            val c: $[MyCache] = allocate(Resource(new MyCache))
            $(db, c)((p1, p2) => p1.method(p2))
            ()
          }
        """))(isLeft)
      },
      // ── N=3 negative tests ────────────────────────────────────────────────
      test("N=3: first param (p1) returned directly — rejected") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._
          class MyDB extends AutoCloseable { def close() = () }
          Scope.global.scoped { scope =>
            import scope._
            val d1: $[MyDB] = allocate(Resource(new MyDB))
            val d2: $[MyDB] = allocate(Resource(new MyDB))
            val d3: $[MyDB] = allocate(Resource(new MyDB))
            $(d1, d2, d3)((p1, p2, p3) => p1)
            ()
          }
        """))(isLeft)
      },
      test("N=3: middle param (p2) passed as arg to external fn — rejected") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._
          class MyDB extends AutoCloseable { def hashCode() = 0; def close() = () }
          var sink: Any = null
          Scope.global.scoped { scope =>
            import scope._
            val d1: $[MyDB] = allocate(Resource(new MyDB))
            val d2: $[MyDB] = allocate(Resource(new MyDB))
            val d3: $[MyDB] = allocate(Resource(new MyDB))
            $(d1, d2, d3)((p1, p2, p3) => { sink = p2; p1.hashCode() })
            ()
          }
        """))(isLeft)
      },
      test("N=3: last param (p3) stored in external var — rejected") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._
          class MyDB extends AutoCloseable { def close() = () }
          var stash: Any = null
          Scope.global.scoped { scope =>
            import scope._
            val d1: $[MyDB] = allocate(Resource(new MyDB))
            val d2: $[MyDB] = allocate(Resource(new MyDB))
            val d3: $[MyDB] = allocate(Resource(new MyDB))
            $(d1, d2, d3)((p1, p2, p3) => { stash = p3; p1.hashCode() })
            ()
          }
        """))(isLeft)
      },
      test("N=3: middle param (p2) captured in nested lambda — rejected") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._
          class MyDB extends AutoCloseable { def close() = () }
          Scope.global.scoped { scope =>
            import scope._
            val d1: $[MyDB] = allocate(Resource(new MyDB))
            val d2: $[MyDB] = allocate(Resource(new MyDB))
            val d3: $[MyDB] = allocate(Resource(new MyDB))
            $(d1, d2, d3)((p1, p2, p3) => () => p2.hashCode())
            ()
          }
        """))(isLeft)
      },
      test("N=3: first param (p1) in if-branch — rejected") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._
          class MyDB extends AutoCloseable { def close() = () }
          Scope.global.scoped { scope =>
            import scope._
            val d1: $[MyDB] = allocate(Resource(new MyDB))
            val d2: $[MyDB] = allocate(Resource(new MyDB))
            val d3: $[MyDB] = allocate(Resource(new MyDB))
            $(d1, d2, d3)((p1, p2, p3) => if (true) p1 else null)
            ()
          }
        """))(isLeft)
      },

      // ── N=4 negative tests ────────────────────────────────────────────────
      test("N=4: first param (p1) returned directly — rejected") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._
          class MyDB extends AutoCloseable { def close() = () }
          Scope.global.scoped { scope =>
            import scope._
            val d1: $[MyDB] = allocate(Resource(new MyDB))
            val d2: $[MyDB] = allocate(Resource(new MyDB))
            val d3: $[MyDB] = allocate(Resource(new MyDB))
            val d4: $[MyDB] = allocate(Resource(new MyDB))
            $(d1, d2, d3, d4)((p1, p2, p3, p4) => p1)
            ()
          }
        """))(isLeft)
      },
      test("N=4: second param (p2) stored in var — rejected") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._
          class MyDB extends AutoCloseable { def close() = () }
          var stash: Any = null
          Scope.global.scoped { scope =>
            import scope._
            val d1: $[MyDB] = allocate(Resource(new MyDB))
            val d2: $[MyDB] = allocate(Resource(new MyDB))
            val d3: $[MyDB] = allocate(Resource(new MyDB))
            val d4: $[MyDB] = allocate(Resource(new MyDB))
            $(d1, d2, d3, d4)((p1, p2, p3, p4) => { stash = p2; p1.hashCode() })
            ()
          }
        """))(isLeft)
      },
      test("N=4: third param (p3) as bare arg to external fn — rejected") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._
          class MyDB extends AutoCloseable { def close() = () }
          var sink: Any = null
          Scope.global.scoped { scope =>
            import scope._
            val d1: $[MyDB] = allocate(Resource(new MyDB))
            val d2: $[MyDB] = allocate(Resource(new MyDB))
            val d3: $[MyDB] = allocate(Resource(new MyDB))
            val d4: $[MyDB] = allocate(Resource(new MyDB))
            $(d1, d2, d3, d4)((p1, p2, p3, p4) => { sink = p3; p1.hashCode() })
            ()
          }
        """))(isLeft)
      },
      test("N=4: last param (p4) captured in closure — rejected") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._
          class MyDB extends AutoCloseable { def close() = () }
          Scope.global.scoped { scope =>
            import scope._
            val d1: $[MyDB] = allocate(Resource(new MyDB))
            val d2: $[MyDB] = allocate(Resource(new MyDB))
            val d3: $[MyDB] = allocate(Resource(new MyDB))
            val d4: $[MyDB] = allocate(Resource(new MyDB))
            $(d1, d2, d3, d4)((p1, p2, p3, p4) => () => p4.hashCode())
            ()
          }
        """))(isLeft)
      },

      // ── N=5 negative tests ────────────────────────────────────────────────
      test("N=5: first param (p1) as bare arg to constructor — rejected") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._
          class MyDB extends AutoCloseable { def close() = () }
          case class Box(v: Any)
          Scope.global.scoped { scope =>
            import scope._
            val d1: $[MyDB] = allocate(Resource(new MyDB))
            val d2: $[MyDB] = allocate(Resource(new MyDB))
            val d3: $[MyDB] = allocate(Resource(new MyDB))
            val d4: $[MyDB] = allocate(Resource(new MyDB))
            val d5: $[MyDB] = allocate(Resource(new MyDB))
            $(d1, d2, d3, d4, d5)((p1, p2, p3, p4, p5) => Box(p1))
            ()
          }
        """))(isLeft)
      },
      test("N=5: third param (p3) in if-branch — rejected") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._
          class MyDB extends AutoCloseable { def close() = () }
          Scope.global.scoped { scope =>
            import scope._
            val d1: $[MyDB] = allocate(Resource(new MyDB))
            val d2: $[MyDB] = allocate(Resource(new MyDB))
            val d3: $[MyDB] = allocate(Resource(new MyDB))
            val d4: $[MyDB] = allocate(Resource(new MyDB))
            val d5: $[MyDB] = allocate(Resource(new MyDB))
            $(d1, d2, d3, d4, d5)((p1, p2, p3, p4, p5) => if (true) p3 else null)
            ()
          }
        """))(isLeft)
      },
      test("N=5: fourth param (p4) stored in var — rejected") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._
          class MyDB extends AutoCloseable { def close() = () }
          var stash: Any = null
          Scope.global.scoped { scope =>
            import scope._
            val d1: $[MyDB] = allocate(Resource(new MyDB))
            val d2: $[MyDB] = allocate(Resource(new MyDB))
            val d3: $[MyDB] = allocate(Resource(new MyDB))
            val d4: $[MyDB] = allocate(Resource(new MyDB))
            val d5: $[MyDB] = allocate(Resource(new MyDB))
            $(d1, d2, d3, d4, d5)((p1, p2, p3, p4, p5) => { stash = p4; p1.hashCode() })
            ()
          }
        """))(isLeft)
      },
      test("N=5: last param (p5) returned directly — rejected") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._
          class MyDB extends AutoCloseable { def close() = () }
          Scope.global.scoped { scope =>
            import scope._
            val d1: $[MyDB] = allocate(Resource(new MyDB))
            val d2: $[MyDB] = allocate(Resource(new MyDB))
            val d3: $[MyDB] = allocate(Resource(new MyDB))
            val d4: $[MyDB] = allocate(Resource(new MyDB))
            val d5: $[MyDB] = allocate(Resource(new MyDB))
            $(d1, d2, d3, d4, d5)((p1, p2, p3, p4, p5) => p5)
            ()
          }
        """))(isLeft)
      },
      test("N=5: second param (p2) captured in nested lambda — rejected") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._
          class MyDB extends AutoCloseable { def close() = () }
          Scope.global.scoped { scope =>
            import scope._
            val d1: $[MyDB] = allocate(Resource(new MyDB))
            val d2: $[MyDB] = allocate(Resource(new MyDB))
            val d3: $[MyDB] = allocate(Resource(new MyDB))
            val d4: $[MyDB] = allocate(Resource(new MyDB))
            val d5: $[MyDB] = allocate(Resource(new MyDB))
            $(d1, d2, d3, d4, d5)((p1, p2, p3, p4, p5) => () => p2.hashCode())
            ()
          }
        """))(isLeft)
      },
      // ── Bug-fix regression: While/anonymous class ─────────────────────────
      test("bug-fix: param stored in var inside while loop — rejected") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._
          class MyDB extends AutoCloseable { def close() = () }
          var stash: Any = null
          Scope.global.scoped { scope =>
            import scope._
            val db: $[MyDB] = allocate(Resource(new MyDB))
            $(db) { d =>
              var i = 0
              while (i < 1) { stash = d; i += 1 }
              42
            }
            ()
          }
        """))(isLeft)
      },
      test("bug-fix: param captured in anonymous class — rejected") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._
          class MyDB extends AutoCloseable { def close() = () }
          var captured: Any = null
          Scope.global.scoped { scope =>
            import scope._
            val db: $[MyDB] = allocate(Resource(new MyDB))
            $(db) { d =>
              val r = new Runnable { def run() = { captured = d; () } }
              r.run()
              42
            }
            ()
          }
        """))(isLeft)
      },
      test("bug-fix: N=2 + while: second param stored in var inside while — rejected") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._
          class MyDB extends AutoCloseable { def close() = () }
          class MyCache extends AutoCloseable { def close() = () }
          var stash: Any = null
          Scope.global.scoped { scope =>
            import scope._
            val db: $[MyDB]    = allocate(Resource(new MyDB))
            val c: $[MyCache]  = allocate(Resource(new MyCache))
            $(db, c) { (p1, p2) =>
              var i = 0
              while (i < 1) { stash = p2; i += 1 }
              p1.hashCode()
            }
            ()
          }
        """))(isLeft)
      }
    )
  )
}
