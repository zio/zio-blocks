package golem.runtime.autowire

import golem.data.GolemSchema
import golem.runtime.util.FutureInterop

import scala.concurrent.Future
import scala.scalajs.js

trait AgentConstructor[Instance] {
  def info: ConstructorMetadata

  def schema: js.Dynamic

  def initialize(payload: js.Dynamic): js.Promise[Instance]
}

object AgentConstructor {
  def asyncJs[A, Instance](ctorInfo: ConstructorMetadata)(build: A => js.Promise[Instance])(implicit
    codec: GolemSchema[A]
  ): AgentConstructor[Instance] =
    async[A, Instance](ctorInfo)(a => FutureInterop.fromPromise(build(a)))

  def noArgs[Instance](description: String, prompt: Option[String] = None)(build: => Instance)(implicit
    codec: GolemSchema[Unit]
  ): AgentConstructor[Instance] =
    sync[Unit, Instance](ConstructorMetadata(name = None, description = description, promptHint = prompt))(_ => build)

  def sync[A, Instance](ctorInfo: ConstructorMetadata)(build: A => Instance)(implicit
    codec: GolemSchema[A]
  ): AgentConstructor[Instance] =
    async[A, Instance](ctorInfo)(a => Future.successful(build(a)))

  def async[A, Instance](
    ctorInfo: ConstructorMetadata
  )(build: A => Future[Instance])(implicit codec: GolemSchema[A]): AgentConstructor[Instance] =
    new AgentConstructor[Instance] {
      override val info: ConstructorMetadata = ctorInfo
      override val schema: js.Dynamic        = HostPayload.schema[A]

      override def initialize(payload: js.Dynamic): js.Promise[Instance] =
        HostPayload
          .decode[A](payload)
          .fold(
            err => js.Promise.reject(err.asInstanceOf[Any]).asInstanceOf[js.Promise[Instance]],
            value => FutureInterop.toPromise(build(value))
          )
    }
}
