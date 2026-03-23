package golem.config

import golem.data.GolemSchema
import zio.blocks.schema.Schema
import scala.deriving.*
import scala.compiletime.*

object ConfigBuilderDerived {
  inline given derived[T](using m: Mirror.ProductOf[T]): ConfigBuilder[T] = {
    class DerivedConfigBuilder extends ConfigBuilder[T] {
      def build(path: List[String], loader: ConfigFieldLoader): T = {
        val values = buildElems[m.MirroredElemLabels, m.MirroredElemTypes](path, loader)
        m.fromProduct(Tuple.fromArray(values.toArray))
      }
    }
    new DerivedConfigBuilder
  }

  private inline def buildElems[Labels <: Tuple, Types <: Tuple](
    path: List[String],
    loader: ConfigFieldLoader
  ): List[Any] =
    inline (erasedValue[Labels], erasedValue[Types]) match {
      case _: (label *: labels, t *: types) =>
        val field = constValue[label].toString
        buildField[t](path :+ field, loader) :: buildElems[labels, types](path, loader)
      case _: (EmptyTuple, EmptyTuple) =>
        Nil
    }

  private inline def buildField[A](path: List[String], loader: ConfigFieldLoader): Any =
    inline erasedValue[A] match {
      case _: Secret[a] =>
        loader.loadSecret[a](path, summonInline[GolemSchema[a]].elementSchema)(using summonInline[Schema[a]])
      case _ =>
        summonFrom {
          case cb: ConfigBuilder[A] =>
            cb.build(path, loader)
          case gs: GolemSchema[A] =>
            loader.loadLocal[A](path, gs.elementSchema)(using summonInline[Schema[A]])
        }
    }
}
