package effectintegration

import zio._
import zio.blocks.scope.Resource

object ZioResources {

  // ZIO Blocks Resource -> ZIO: acquired on the blocking pool, released when the ZIO Scope closes.
  def fromResource[A](resource: Resource[A]): ZIO[Scope, Throwable, A] =
    ZIO
      .acquireRelease(ZIO.attemptBlocking(Opened.resource(resource))) { case (_, release) =>
        ZIO.attemptBlocking(release()).orDie
      }
      .map(_._1)

  // ZIO -> ZIO Blocks Resource: runs `acquire` in a fresh ZIO Scope, closed with the ZIO Blocks scope.
  def toResource[A](acquire: ZIO[Scope, Throwable, A])(implicit runtime: Runtime[Any]): Resource[A] =
    Resource.acquireRelease {
      Unsafe.unsafe { implicit unsafe =>
        val scope = runtime.unsafe.run(Scope.make).getOrThrow()
        runtime.unsafe.run(scope.extend[Any](acquire)) match {
          case Exit.Success(a)     => (a, scope)
          case Exit.Failure(cause) =>
            runtime.unsafe.run(scope.close(Exit.failCause(cause)))
            throw cause.squash
        }
      }
    } { case (_, scope) =>
      Unsafe.unsafe(implicit unsafe => runtime.unsafe.run(scope.close(Exit.unit)).getOrThrow())
    }
      .map(_._1)
}
