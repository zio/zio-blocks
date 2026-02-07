package golem.wasi

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

/**
 * Scala.js facades for WASI blobstore (e.g. `wasi:blobstore/blobstore`).
 */
object Blobstore {
  @js.native
  @JSImport("wasi:blobstore/blobstore", JSImport.Namespace)
  private object BlobstoreModule extends js.Object

  @js.native
  @JSImport("wasi:blobstore/container", JSImport.Namespace)
  private object ContainerModule extends js.Object

  @js.native
  @JSImport("wasi:blobstore/types", JSImport.Namespace)
  private object TypesModule extends js.Object

  def blobstoreRaw: Any = BlobstoreModule
  def containerRaw: Any = ContainerModule
  def typesRaw: Any     = TypesModule
}
