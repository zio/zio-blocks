package golem.config

import golem.data.GolemSchema
import scala.deriving.*
import scala.compiletime.*

/**
 * Scala 3 derivation for RPC config field accessors.
 *
 * Given a config case class `T`, generates an `RpcFields[T]` where:
 *  - Secret fields are omitted (compile-time prevention of secret overrides)
 *  - Leaf local fields become `RpcConfig.Field[T, A]`
 *  - Nested config fields become sub-`RpcFields[T]` groups
 *
 * Usage:
 * {{{
 * case class MyConfig(host: String, password: Secret[String], db: DbConfig)
 * object MyConfig {
 *   val rpcFields: RpcFields[MyConfig] = RpcConfigFieldsDerived.fields[MyConfig]
 *   // rpcFields[String]("host")          — typed field accessor
 *   // rpcFields.nested("db")[String]("host") — nested field accessor
 *   // rpcFields[String]("password")      — throws NoSuchElementException (secret excluded)
 * }
 * }}}
 */
object RpcConfigFieldsDerived {

  inline def fields[T](using m: Mirror.ProductOf[T]): RpcFields[T] =
    buildFields[T, m.MirroredElemLabels, m.MirroredElemTypes](Nil).build

  private inline def buildFields[Root, Labels <: Tuple, Types <: Tuple](
    prefix: List[String]
  ): RpcFields.Builder[Root] =
    inline (erasedValue[Labels], erasedValue[Types]) match {
      case _: (EmptyTuple, EmptyTuple) =>
        RpcFields.builder[Root]
      case _: (label *: labels, tpe *: types) =>
        val fieldName = constValue[label].toString
        val path      = prefix :+ fieldName
        val rest      = buildFields[Root, labels, types](prefix)
        addFieldEntry[Root, tpe](rest, fieldName, path)
    }

  private inline def addFieldEntry[Root, A](
    builder: RpcFields.Builder[Root],
    fieldName: String,
    path: List[String]
  ): RpcFields.Builder[Root] =
    inline erasedValue[A] match {
      case _: Secret[_] =>
        builder
      case _ =>
        inline if (hasConfigBuilder[A]) addNestedEntry[Root, A](builder, fieldName, path)
        else inline if (hasGolemSchema[A]) {
          val gs = summonInline[GolemSchema[A]]
          builder.addLeaf[A](fieldName, path, gs)
        } else builder
    }

  private inline def hasConfigBuilder[A]: Boolean =
    summonFrom {
      case _: ConfigBuilder[A] => true
      case _                   => false
    }

  private inline def hasGolemSchema[A]: Boolean =
    summonFrom {
      case _: GolemSchema[A] => true
      case _                 => false
    }

  private inline def addNestedEntry[Root, A](
    builder: RpcFields.Builder[Root],
    fieldName: String,
    path: List[String]
  ): RpcFields.Builder[Root] =
    summonFrom {
      case nm: Mirror.ProductOf[A] =>
        val sub = buildFields[Root, nm.MirroredElemLabels, nm.MirroredElemTypes](path).build
        builder.addNested(fieldName, sub)
    }
}
