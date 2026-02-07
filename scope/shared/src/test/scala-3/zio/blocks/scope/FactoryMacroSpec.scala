package zio.blocks.scope

import zio.test._

object FactoryMacroSpec extends ZIOSpecDefault {

  case class Simple()

  class Resource extends AutoCloseable {
    var closed        = false
    def close(): Unit = closed = true
  }

  class ResourceWithScope()(using Scope.Any) {
    var cleanedUp = false
    defer { cleanedUp = true }
  }

  def spec = suite("Factory[T] macro")(
    test("Factory[T] macro derives from no-arg constructor") {
      val factory        = Factory[Simple]
      val (scope, close) = Scope.createTestableScope()
      val simple         = factory.make(scope)
      close()
      assertTrue(simple.isInstanceOf[Simple])
    },
    test("Factory[T] macro handles AutoCloseable") {
      val factory        = Factory[Resource]
      val (scope, close) = Scope.createTestableScope()
      val resource       = factory.make(scope)
      assertTrue(!resource.closed)
      close()
      assertTrue(resource.closed)
    },
    test("Factory[T] macro handles constructor with implicit Scope") {
      val factory                    = Factory[ResourceWithScope]
      val (parentScope, closeParent) = Scope.createTestableScope()
      val resource                   = factory.make(parentScope)
      assertTrue(!resource.cleanedUp)
      closeParent()
      assertTrue(resource.cleanedUp)
    }
  )
}
