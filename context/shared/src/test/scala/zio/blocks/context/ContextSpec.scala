package zio.blocks.context

import zio.test._

object ContextSpec extends ZIOSpecDefault {

  trait Logger {
    def log(msg: String): Unit
  }

  case class ConsoleLogger(name: String) extends Logger {
    def log(msg: String): Unit = println(s"[$name] $msg")
  }

  trait Database {
    def query(sql: String): String
  }

  case class PostgresDatabase(url: String) extends Database {
    def query(sql: String): String = s"Result from $url"
  }

  case class Config(debug: Boolean)

  case class Service1(id: Int)
  case class Service2(id: Int)
  case class Service3(id: Int)
  case class Service4(id: Int)
  case class Service5(id: Int)
  case class Service6(id: Int)
  case class Service7(id: Int)
  case class Service8(id: Int)
  case class Service9(id: Int)
  case class Service10(id: Int)

  def spec = suite("Context")(
    suite("construction")(
      test("empty context has size 0") {
        assertTrue(Context.empty.size == 0, Context.empty.isEmpty)
      },
      test("single service context") {
        val ctx = Context(ConsoleLogger("test"))
        assertTrue(ctx.size == 1, ctx.nonEmpty)
      },
      test("two service context") {
        val ctx = Context(ConsoleLogger("test"), PostgresDatabase("jdbc://localhost"))
        assertTrue(ctx.size == 2)
      },
      test("three service context") {
        val ctx = Context(Service1(1), Service2(2), Service3(3))
        assertTrue(ctx.size == 3)
      },
      test("four service context") {
        val ctx = Context(Service1(1), Service2(2), Service3(3), Service4(4))
        assertTrue(ctx.size == 4)
      },
      test("five service context") {
        val ctx = Context(Service1(1), Service2(2), Service3(3), Service4(4), Service5(5))
        assertTrue(ctx.size == 5)
      },
      test("six service context") {
        val ctx = Context(Service1(1), Service2(2), Service3(3), Service4(4), Service5(5), Service6(6))
        assertTrue(ctx.size == 6)
      },
      test("seven service context") {
        val ctx = Context(Service1(1), Service2(2), Service3(3), Service4(4), Service5(5), Service6(6), Service7(7))
        assertTrue(ctx.size == 7)
      },
      test("eight service context") {
        val ctx = Context(
          Service1(1),
          Service2(2),
          Service3(3),
          Service4(4),
          Service5(5),
          Service6(6),
          Service7(7),
          Service8(8)
        )
        assertTrue(ctx.size == 8)
      },
      test("nine service context") {
        val ctx = Context(
          Service1(1),
          Service2(2),
          Service3(3),
          Service4(4),
          Service5(5),
          Service6(6),
          Service7(7),
          Service8(8),
          Service9(9)
        )
        assertTrue(ctx.size == 9)
      },
      test("ten service context") {
        val ctx = Context(
          Service1(1),
          Service2(2),
          Service3(3),
          Service4(4),
          Service5(5),
          Service6(6),
          Service7(7),
          Service8(8),
          Service9(9),
          Service10(10)
        )
        assertTrue(ctx.size == 10)
      }
    ),
    suite("get")(
      test("get retrieves exact type") {
        val logger = ConsoleLogger("test")
        val ctx    = Context(logger)
        assertTrue(ctx.get[ConsoleLogger] == logger)
      },
      test("get retrieves by supertype") {
        val logger = ConsoleLogger("test")
        val ctx    = Context(logger)
        assertTrue(ctx.get[Logger] == logger)
      },
      test("get caches retrieved value") {
        val logger = ConsoleLogger("test")
        val ctx    = Context(logger)
        val first  = ctx.get[Logger]
        val second = ctx.get[Logger]
        assertTrue(first eq second)
      },
      test("get throws for missing service when added type differs") {
        val ctx     = Context(ConsoleLogger("test"))
        val wideCtx = ctx.asInstanceOf[Context[Logger with Database]]
        val result  = try {
          wideCtx.get[Database]
          false
        } catch {
          case _: NoSuchElementException => true
        }
        assertTrue(result)
      }
    ),
    suite("getOption")(
      test("getOption returns Some for present service") {
        val logger = ConsoleLogger("test")
        val ctx    = Context(logger)
        assertTrue(ctx.getOption[ConsoleLogger] == Some(logger))
      },
      test("getOption returns Some for supertype") {
        val logger = ConsoleLogger("test")
        val ctx    = Context(logger)
        assertTrue(ctx.getOption[Logger] == Some(logger))
      },
      test("getOption returns None for missing service") {
        val ctx     = Context(ConsoleLogger("test"))
        val wideCtx = ctx.asInstanceOf[Context[Logger with Database]]
        assertTrue(wideCtx.getOption[Database] == None)
      },
      test("getOption caches retrieved value") {
        val logger = ConsoleLogger("test")
        val ctx    = Context(logger)
        val first  = ctx.getOption[Logger]
        val second = ctx.getOption[Logger]
        assertTrue(first == second)
      }
    ),
    suite("add")(
      test("add appends new service") {
        val ctx = Context.empty.add(ConsoleLogger("test"))
        assertTrue(ctx.size == 1)
      },
      test("add overwrites existing service of same type") {
        val logger1 = ConsoleLogger("first")
        val logger2 = ConsoleLogger("second")
        val ctx     = Context(logger1).add(logger2)
        assertTrue(ctx.size == 1, ctx.get[ConsoleLogger] == logger2)
      },
      test("add preserves other services") {
        val logger = ConsoleLogger("test")
        val db     = PostgresDatabase("jdbc://localhost")
        val ctx    = Context(logger).add(db)
        assertTrue(ctx.get[ConsoleLogger] == logger, ctx.get[Database] == db)
      }
    ),
    suite("update")(
      test("update modifies existing service") {
        val ctx     = Context(Config(debug = false))
        val updated = ctx.update[Config](c => c.copy(debug = true))
        assertTrue(updated.get[Config].debug)
      },
      test("update is no-op for missing service") {
        val ctx     = Context(ConsoleLogger("test"))
        val wideCtx = ctx.asInstanceOf[Context[ConsoleLogger with Config]]
        val updated = wideCtx.update[Config](c => c.copy(debug = true))
        assertTrue(updated.asInstanceOf[Context[ConsoleLogger with Config]].getOption[Config] == None)
      }
    ),
    suite("union (++)")(
      test("union combines two contexts") {
        val ctx1   = Context(ConsoleLogger("test"))
        val ctx2   = Context(PostgresDatabase("jdbc://localhost"))
        val merged = ctx1 ++ ctx2
        assertTrue(merged.size == 2)
      },
      test("union right side wins on conflict") {
        val logger1 = ConsoleLogger("first")
        val logger2 = ConsoleLogger("second")
        val merged  = Context(logger1) ++ Context(logger2)
        assertTrue(merged.get[ConsoleLogger] == logger2)
      },
      test("union with empty left returns right") {
        val ctx    = Context(ConsoleLogger("test"))
        val merged = Context.empty ++ ctx
        assertTrue(merged.size == 1)
      },
      test("union with empty right returns left") {
        val ctx    = Context(ConsoleLogger("test"))
        val merged = ctx ++ Context.empty
        assertTrue(merged.size == 1)
      }
    ),
    suite("prune")(
      test("prune keeps only specified types") {
        val ctx    = Context(ConsoleLogger("test"), PostgresDatabase("jdbc://localhost"), Config(true))
        val pruned = ctx.prune[ConsoleLogger]
        assertTrue(pruned.size == 1, pruned.getOption[ConsoleLogger].isDefined)
      }
    ),
    suite("toString")(
      test("toString shows context contents") {
        val ctx = Context(ConsoleLogger("test"))
        assertTrue(ctx.toString.contains("Context("))
      },
      test("empty toString") {
        assertTrue(Context.empty.toString == "Context()")
      }
    ),
    suite("ContextEntries")(
      test("removed removes entry") {
        val entries = ContextEntries.empty
          .updated(implicitly[IsNominalType[Config]].typeIdErased, Config(true))
        val removed = entries.removed(implicitly[IsNominalType[Config]].typeIdErased)
        assertTrue(removed.size == 0)
      },
      test("removed is no-op for missing key") {
        val entries = ContextEntries.empty
        val removed = entries.removed(implicitly[IsNominalType[Config]].typeIdErased)
        assertTrue(removed.size == 0)
      },
      test("get returns null for missing key") {
        val entries = ContextEntries.empty
        assertTrue(entries.get(implicitly[IsNominalType[Config]].typeIdErased) == null)
      }
    ),
    suite("Cache")(
      test("cache get returns null for missing key") {
        val cache = PlatformCache.empty
        assertTrue(cache.get(implicitly[IsNominalType[Config]].typeIdErased) == null)
      },
      test("cache put and get") {
        val cache  = PlatformCache.empty
        val config = Config(true)
        val key    = implicitly[IsNominalType[Config]].typeIdErased
        cache.put(key, config)
        assertTrue(cache.get(key) == config)
      },
      test("cache putIfAbsent returns null on first insert") {
        val cache  = PlatformCache.empty
        val config = Config(true)
        val key    = implicitly[IsNominalType[Config]].typeIdErased
        val result = cache.putIfAbsent(key, config)
        assertTrue(result == null, cache.get(key) == config)
      },
      test("cache putIfAbsent returns existing on second insert") {
        val cache   = PlatformCache.empty
        val config1 = Config(true)
        val config2 = Config(false)
        val key     = implicitly[IsNominalType[Config]].typeIdErased
        cache.putIfAbsent(key, config1)
        val result = cache.putIfAbsent(key, config2)
        assertTrue(result == config1, cache.get(key) == config1)
      }
    ),
    suite("IsNominalType")(
      test("derives for case class") {
        val ev = implicitly[IsNominalType[Config]]
        assertTrue(ev.typeId.fullName.contains("Config"))
      },
      test("derives for trait implementation") {
        val ev = implicitly[IsNominalType[ConsoleLogger]]
        assertTrue(ev.typeId.fullName.contains("ConsoleLogger"))
      },
      test("does not compile for intersection type") {
        typeCheck("implicitly[IsNominalType[Logger with Database]]").map(result => assertTrue(result.isLeft))
      }
    ),
    suite("IsNominalIntersection")(
      test("derives for single type") {
        val ev = implicitly[IsNominalIntersection[Config]]
        assertTrue(ev.typeIdsErased.length == 1)
      }
    )
  )
}
