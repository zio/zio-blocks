package zio.blocks.schema.binding

import scala.scalajs.reflect.Reflect

trait SeqConstructorPlatformSpecific {
  def classForName(fqcn: String): Class[_] =
    Reflect.lookupInstantiatableClass(fqcn).getOrElse(throw new ClassNotFoundException(fqcn)).runtimeClass
}
