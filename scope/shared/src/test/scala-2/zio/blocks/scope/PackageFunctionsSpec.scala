package zio.blocks.scope

import zio.test._
import zio.blocks.context.Context
import zio.blocks.scope.internal.Finalizers

object PackageFunctionsSpec extends ZIOSpecDefault {

  case class Config(debug: Boolean)

  class CloseableResource(val name: String) extends AutoCloseable {
    var closed: Boolean = false
    def close(): Unit   = closed = true
  }

  def spec = suite("package functions (Scala 2)")(
    test("defer delegates to scope") {
      var cleaned           = false
      val parent: Scope.Any = Scope.global
      val f                 = new Finalizers
      implicit val s: Scope.Any = Scope.makeCloseable[Config, TNil](parent, Context(Config(true)), f)
      defer { cleaned = true }
      s.asInstanceOf[Scope.Closeable[_, _]].close()
      assertTrue(cleaned)
    },
    test("get delegates to scope") {
      val parent: Scope.Any     = Scope.global
      val config                = Config(true)
      val f                     = new Finalizers
      implicit val s: Scope.Has[Config] = Scope.makeCloseable[Config, TNil](parent, Context(config), f)
      val retrieved = get[Config]
      s.asInstanceOf[Scope.Closeable[_, _]].close()
      assertTrue(retrieved == config)
    },
    test("injectedValue creates closeable scope") {
      val parent: Scope.Any = Scope.global
      val config            = Config(true)
      implicit val scope: Scope.Any = parent
      val closeable = injectedValue(config)
      val retrieved = closeable.get[Config]
      closeable.close()
      assertTrue(retrieved == config)
    },
    test("injectedValue registers AutoCloseable cleanup") {
      val parent: Scope.Any = Scope.global
      val resource          = new CloseableResource("test")
      implicit val scope: Scope.Any = parent
      val closeable = injectedValue(resource)
      closeable.close()
      assertTrue(resource.closed)
    }
  )
}
