package golem.config

import golem.data.GolemSchema
import scala.deriving.*
import scala.compiletime.*

object ConfigSchemaDerived {
  inline given derived[T](using m: Mirror.ProductOf[T]): ConfigSchema[T] =
    fromDescriptions(describeElems[m.MirroredElemLabels, m.MirroredElemTypes](_))

  private def fromDescriptions[T](f: List[String] => List[AgentConfigDeclaration]): ConfigSchema[T] =
    new ConfigSchema[T] {
      def describe(path: List[String]): List[AgentConfigDeclaration] = f(path)
    }

  private inline def describeElems[Labels <: Tuple, Types <: Tuple](
    path: List[String]
  ): List[AgentConfigDeclaration] =
    inline (erasedValue[Labels], erasedValue[Types]) match {
      case _: (label *: labels, t *: types) =>
        val field = constValue[label].toString
        describeField[t](path :+ field) ::: describeElems[labels, types](path)
      case _: (EmptyTuple, EmptyTuple) =>
        Nil
    }

  private inline def describeField[A](path: List[String]): List[AgentConfigDeclaration] =
    summonFrom {
      case cs: ConfigSchema[A] => cs.describe(path)
      case gs: GolemSchema[A]  =>
        List(AgentConfigDeclaration(AgentConfigSource.Local, path, gs.elementSchema))
    }
}
