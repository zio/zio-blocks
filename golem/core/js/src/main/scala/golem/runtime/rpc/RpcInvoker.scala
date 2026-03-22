/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package golem.runtime.rpc

import golem.Datetime
import scala.scalajs.js

private[rpc] trait RpcInvoker {
  def invokeAndAwait(functionName: String, params: js.Array[js.Dynamic]): Either[String, js.Dynamic]

  def trigger(functionName: String, params: js.Array[js.Dynamic]): Either[String, Unit]

  def scheduleInvocation(datetime: Datetime, functionName: String, params: js.Array[js.Dynamic]): Either[String, Unit]
}
