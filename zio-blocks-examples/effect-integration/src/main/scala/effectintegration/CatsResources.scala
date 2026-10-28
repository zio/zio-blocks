package effectintegration

import cats.effect.{IO, Resource}
import cats.effect.std.Dispatcher
import zio.blocks.scope.{Resource => BlocksResource}

object CatsResources {

  // ZIO Blocks Resource -> cats-effect Resource: acquired and released on the blocking pool.
  def fromResource[A](resource: BlocksResource[A]): Resource[IO, A] =
    Resource
      .make(IO.blocking(Opened.resource(resource))) { case (_, release) => IO.blocking(release()) }
      .map(_._1)

  // cats-effect Resource -> ZIO Blocks Resource: allocated now, released with the ZIO Blocks scope.
  def toResource[A](resource: Resource[IO, A])(implicit dispatcher: Dispatcher[IO]): BlocksResource[A] =
    BlocksResource
      .acquireRelease(dispatcher.unsafeRunSync(resource.allocated)) { case (_, release) =>
        dispatcher.unsafeRunSync(release)
      }
      .map(_._1)
}
