package golem.config

import golem.data.{DataInterop, ElementSchema}
import golem.runtime.autowire.{WitTypeBuilder, WitValueCodec}
import golem.runtime.rpc.host.AgentHostApi

import zio.blocks.schema.Schema

private[golem] object ConfigLoader extends ConfigFieldLoader {

  override def loadLocal[A](path: List[String], elementSchema: ElementSchema)(implicit schema: Schema[A]): A =
    loadValue[A](path, elementSchema)

  override def loadSecret[A](path: List[String], elementSchema: ElementSchema)(implicit schema: Schema[A]): Secret[A] =
    makeSecret[A](path, elementSchema)

  def loadConfig[T](builder: ConfigBuilder[T]): Config[T] =
    Config(builder.build(Nil, this))

  def loadValue[A](path: List[String], elementSchema: ElementSchema)(implicit schema: Schema[A]): A =
    elementSchema match {
      case ElementSchema.Component(dataType) =>
        val witType  = WitTypeBuilder.build(dataType)
        val witValue = AgentHostApi.getConfigValue(path, witType)
        val decoded  = WitValueCodec.decode(dataType, witValue) match {
          case Right(dv)  => dv
          case Left(err)  => throw new RuntimeException(s"Failed to decode config value at path ${path.mkString(".")}: $err")
        }
        DataInterop.fromData[A](decoded) match {
          case Right(a)   => a
          case Left(err)  => throw new RuntimeException(s"Failed to convert config value at path ${path.mkString(".")}: $err")
        }
      case _ =>
        throw new UnsupportedOperationException(
          s"Config loading only supports component schemas, found: $elementSchema"
        )
    }

  def makeSecret[A](path: List[String], elementSchema: ElementSchema)(implicit schema: Schema[A]): Secret[A] =
    new Secret[A](path, () => loadValue[A](path, elementSchema))
}
