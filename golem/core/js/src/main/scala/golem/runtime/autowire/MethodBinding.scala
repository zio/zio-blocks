package golem.runtime.autowire

import golem.data.GolemSchema
import golem.runtime.MethodMetadata
import golem.runtime.util.FutureInterop

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

trait MethodBinding[Instance] {
  def metadata: MethodMetadata

  def inputSchema: js.Dynamic

  def outputSchema: js.Dynamic

  def invoke(instance: Instance, payload: js.Dynamic): js.Promise[js.Dynamic]
}

object MethodBinding {
  def sync[Instance, In, Out](methodMetadata: MethodMetadata)(
    handler: (Instance, In) => Out
  )(implicit inSchema: GolemSchema[In], outSchema: GolemSchema[Out]): MethodBinding[Instance] =
    async[Instance, In, Out](methodMetadata)((instance, input) => Future.successful(handler(instance, input)))

  def async[Instance, In, Out](methodMetadata: MethodMetadata)(
    handler: (Instance, In) => Future[Out]
  )(implicit inSchema: GolemSchema[In], outSchema: GolemSchema[Out]): MethodBinding[Instance] =
    new MethodBinding[Instance] {
      override val metadata: MethodMetadata = methodMetadata
      override val inputSchema: js.Dynamic  = HostPayload.schema[In]
      override val outputSchema: js.Dynamic = HostPayload.schema[Out]

      override def invoke(instance: Instance, payload: js.Dynamic): js.Promise[js.Dynamic] = {
        val future =
          HostPayload
            .decode[In](payload)
            .fold(
              err => Future.failed(js.JavaScriptException(err)),
              value =>
                handler(instance, value).flatMap { out =>
                  HostPayload.encode[Out](out) match {
                    case Left(error) => Future.failed(js.JavaScriptException(error))
                    case Right(data) => Future.successful(data)
                  }
                }
            )

        FutureInterop.toPromise(future)
      }
    }
}
