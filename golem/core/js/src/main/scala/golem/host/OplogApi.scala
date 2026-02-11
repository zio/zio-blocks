package golem.host

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

/**
 * Scala.js facade for `golem:api/oplog@1.3.0`.
 *
 * The oplog API is fairly large; this module primarily exposes the raw imported
 * module so Scala users can reach parity with other SDKs without waiting for
 * full idiomatic wrappers.
 */
object OplogApi {

  @js.native
  @JSImport("golem:api/oplog@1.3.0", JSImport.Namespace)
  private object OplogModule extends js.Object

  /**
   * Low-level access to the imported host module.
   *
   * Note: intentionally typed as `Any` so the public SDK surface does not
   * expose `scala.scalajs.js.*` types.
   */
  def raw: Any =
    OplogModule
}
