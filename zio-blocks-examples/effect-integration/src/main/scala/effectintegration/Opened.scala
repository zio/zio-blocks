package effectintegration

import scala.annotation.nowarn
import zio.blocks.scope.{Resource, Scope}

// Opens a ZIO Blocks resource outside a `scoped` block, for an effect system that manages the
// lifetime itself. The result pairs the value with a function that releases it.
object Opened {

  @nowarn // `leak` warns on purpose: the value leaves the scope, and the caller closes it
  def resource[A](resource: Resource[A]): (A, () => Unit) = {
    val open = Scope.global.open()
    try (open.scope.leak(open.scope.allocate(resource)), () => open.close().orThrow())
    catch { case t: Throwable => open.close(); throw t }
  }
}
