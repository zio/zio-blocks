package golem.config

import golem.data.GolemSchema

/**
 * Container for typed RPC config field accessors.
 *
 * Provides `apply(name)` to look up field accessors by name.
 * Secret fields are not included, preventing accidental secret overrides.
 *
 * For nested config fields, `nested(name)` returns a sub-`RpcFields`
 * with paths rooted at the nesting prefix.
 */
final class RpcFields[T] private[golem] (
  private val leafFields: Map[String, RpcConfig.Field[T, _]],
  private val nestedFields: Map[String, RpcFields[T]]
) {

  /** Get a typed leaf field accessor by name. */
  def apply[A](name: String): RpcConfig.Field[T, A] =
    leafFields.get(name) match {
      case Some(field) => field.asInstanceOf[RpcConfig.Field[T, A]]
      case None =>
        if (nestedFields.contains(name))
          throw new IllegalArgumentException(
            s"'$name' is a nested config group, not a leaf field. Use .nested(\"$name\") instead."
          )
        else
          throw new NoSuchElementException(
            s"No RPC config field '$name'. Available fields: ${(leafFields.keys ++ nestedFields.keys).mkString(", ")}"
          )
    }

  /** Get a nested fields group by name. */
  def nested(name: String): RpcFields[T] =
    nestedFields.getOrElse(
      name,
      throw new NoSuchElementException(
        s"No nested config group '$name'. Available groups: ${nestedFields.keys.mkString(", ")}"
      )
    )
}

object RpcFields {
  def builder[T]: Builder[T] = new Builder[T]

  final class Builder[T] {
    private var leaves: Map[String, RpcConfig.Field[T, _]] = Map.empty
    private var nested: Map[String, RpcFields[T]]          = Map.empty

    def addLeaf[A](name: String, path: List[String], gs: GolemSchema[A]): Builder[T] = {
      leaves = leaves.updated(name, new RpcConfig.Field[T, A](path, gs))
      this
    }

    def addNested(name: String, sub: RpcFields[T]): Builder[T] = {
      nested = nested.updated(name, sub)
      this
    }

    def build: RpcFields[T] = new RpcFields[T](leaves, nested)
  }
}
