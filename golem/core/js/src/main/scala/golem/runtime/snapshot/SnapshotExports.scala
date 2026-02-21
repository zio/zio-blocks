package golem.runtime.snapshot

import golem.runtime.util.FutureInterop

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}
import scala.scalajs.js.typedarray.Uint8Array

/**
 * Bridges Scala snapshot implementations to the host-level
 * `saveSnapshot`/`loadSnapshot` exports expected by Golem.
 *
 * Golem components that opt into snapshot-based state updates must expose:
 *   - `saveSnapshot(): Promise<ArrayBuffer>` - Captures current state
 *   - `loadSnapshot(bytes: ArrayBuffer): Promise<void>` - Restores state
 *
 * Call [[configure]] once during module initialization to install your
 * save/load handlers. If you don't configure handlers, the defaults return
 * empty snapshots and ignore incoming payloads.
 *
 * ==Example==
 * {{{
 * import golem.runtime.snapshot.SnapshotExports
 * import scala.concurrent.Future
 *
 * SnapshotExports.configure(
 *   save = () => Future.successful(serializeState()),
 *   load = bytes => { restoreState(bytes); Future.successful(()) }
 * )
 * }}}
 */
object SnapshotExports {
  private var saveHook: () => Future[Uint8Array] =
    () => Future.successful(new Uint8Array(0))

  private var loadHook: Uint8Array => Future[Unit] =
    _ => Future.successful(())

  /**
   * Registers JavaScript `Promise`-based handlers for snapshot operations.
   *
   * Use this variant when working with JavaScript interop code that naturally
   * produces Promises rather than Scala Futures.
   *
   * @param save
   *   Function returning a Promise of the serialized state
   * @param load
   *   Function accepting bytes and returning a Promise of completion
   */
  private[golem] def configureJs(
    save: () => js.Promise[Uint8Array],
    load: Uint8Array => js.Promise[Unit]
  ): Unit =
    configureRaw(
      () => FutureInterop.fromPromise(save()),
      bytes => FutureInterop.fromPromise(load(bytes))
    )

  /**
   * Registers Scala `Future`-based handlers for snapshot save/load operations.
   *
   * @param save
   *   Function that serializes current state to bytes. Called by the host when
   *   a snapshot is requested.
   * @param load
   *   Function that restores state from bytes. Called by the host when loading
   *   a previous snapshot.
   */
  def configure(
    save: () => Future[Array[Byte]],
    load: Array[Byte] => Future[Unit]
  ): Unit =
    configureRaw(
      () => save().map(toUint8Array),
      bytes => load(fromUint8Array(bytes))
    )

  /**
   * Low-level variant that works with `Uint8Array` directly.
   *
   * Prefer [[configure]] unless you need raw typed-array access.
   */
  private[golem] def configureRaw(
    save: () => Future[Uint8Array],
    load: Uint8Array => Future[Unit]
  ): Unit = {
    saveHook = save
    loadHook = load
  }

  /**
   * Host-facing export for saving snapshots. Do not call directly - the Golem
   * runtime invokes this.
   */
  @JSExportTopLevel("saveSnapshot")
  object SaveSnapshot {
    @JSExport
    def save(): js.Promise[Uint8Array] =
      FutureInterop.toPromise(saveHook())
  }

  /**
   * Host-facing export for loading snapshots. Do not call directly - the Golem
   * runtime invokes this.
   */
  @JSExportTopLevel("loadSnapshot")
  object LoadSnapshot {
    @JSExport
    def load(bytes: Uint8Array): js.Promise[Unit] =
      FutureInterop.toPromise(loadHook(bytes))
  }

  private def toUint8Array(bytes: Array[Byte]): Uint8Array = {
    val array = new Uint8Array(bytes.length)
    var i     = 0
    while (i < bytes.length) {
      array(i) = (bytes(i) & 0xff).toShort
      i += 1
    }
    array
  }

  private def fromUint8Array(bytes: Uint8Array): Array[Byte] = {
    val out = new Array[Byte](bytes.length)
    var i   = 0
    while (i < bytes.length) {
      out(i) = bytes(i).toByte
      i += 1
    }
    out
  }
}
