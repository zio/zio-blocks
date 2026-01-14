package golem.wasi

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

/**
 * Scala.js facades for WASI keyvalue (e.g. `wasi:keyvalue/eventual@0.1.0`).
 */
object KeyValue {
  @js.native
  @JSImport("wasi:keyvalue/eventual@0.1.0", JSImport.Namespace)
  private object EventualModule extends js.Object

  @js.native
  @JSImport("wasi:keyvalue/eventual-batch@0.1.0", JSImport.Namespace)
  private object EventualBatchModule extends js.Object

  @js.native
  @JSImport("wasi:keyvalue/types@0.1.0", JSImport.Namespace)
  private object TypesModule extends js.Object

  @js.native
  @JSImport("wasi:keyvalue/wasi-keyvalue-error@0.1.0", JSImport.Namespace)
  private object ErrorModule extends js.Object

  def eventualRaw: js.Dynamic      = EventualModule.asInstanceOf[js.Dynamic]
  def eventualBatchRaw: js.Dynamic = EventualBatchModule.asInstanceOf[js.Dynamic]
  def typesRaw: js.Dynamic         = TypesModule.asInstanceOf[js.Dynamic]
  def errorRaw: js.Dynamic         = ErrorModule.asInstanceOf[js.Dynamic]
}

