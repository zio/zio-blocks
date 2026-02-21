package golem

import golem.runtime.snapshot.SnapshotExports
import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.Future

final class SnapshotExportsCompileSpec extends AnyFunSuite {

  test("configure accepts Array[Byte] save and load functions") {
    val save: () => Future[Array[Byte]]                                       = () => Future.successful(Array[Byte](1, 2, 3))
    val load: Array[Byte] => Future[Unit]                                     = _ => Future.successful(())
    val _: ((() => Future[Array[Byte]], Array[Byte] => Future[Unit]) => Unit) =
      SnapshotExports.configure(_, _)
    SnapshotExports.configure(save, load)
    assert(true)
  }

  test("configure with empty snapshot") {
    SnapshotExports.configure(
      save = () => Future.successful(Array.empty[Byte]),
      load = _ => Future.successful(())
    )
    assert(true)
  }

  test("configure with stateful save/load") {
    var state: Array[Byte] = Array.empty[Byte]

    SnapshotExports.configure(
      save = () => Future.successful(state),
      load = bytes => { state = bytes; Future.successful(()) }
    )
    assert(true)
  }
}
