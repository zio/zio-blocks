package zio.blocks.scope

import zio.blocks.context.{Context, IsNominalType}
import zio.blocks.scope.internal.{Finalizers, ScopeImpl}

sealed trait Scope[+Stack] {
  private[scope] def getImpl[T](nom: IsNominalType[T]): T

  def defer(finalizer: => Unit): Unit
}

object Scope {
  type Any = Scope[?]

  type Has[+T] = Scope[Context[T] :: ?]

  implicit final class ScopeOps[Stack](private val self: Scope[Stack]) extends AnyVal {
    def get[T](implicit ev: InStack[T, Stack], nom: IsNominalType[T]): T = self.getImpl(nom)
  }

  trait Closeable[+Head, +Tail] extends Scope[Context[Head] :: Tail] with AutoCloseable {
    def close(): Unit

    def run[B](f: Context[Head] => B): B
  }

  private[scope] def makeCloseable[T, S](
    parent: Scope[?],
    context: Context[T],
    finalizers: Finalizers
  ): Closeable[T, S] =
    new ScopeImpl[T, S](parent, context, finalizers)

  private val globalInstance: GlobalScope = new GlobalScope

  private[scope] def closeGlobal(): Unit = globalInstance.close()

  lazy val global: Scope[TNil] = {
    PlatformScope.registerShutdownHook(() => closeGlobal())
    globalInstance
  }

  private[scope] def createTestableScope(): (Scope[TNil], () => Unit) = {
    val scope = new GlobalScope
    (scope, () => scope.close())
  }

  private final class GlobalScope extends Scope[TNil] {
    private val finalizers = new Finalizers

    private[scope] def getImpl[T](nom: IsNominalType[T]): T =
      throw new IllegalStateException("Global scope has no services")

    def defer(finalizer: => Unit): Unit = finalizers.add(finalizer)

    private[scope] def close(): Unit = {
      val errors = finalizers.runAll()
      errors.headOption.foreach(throw _)
    }
  }
}
