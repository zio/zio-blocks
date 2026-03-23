package golem.config

import golem.data.GolemSchema

/**
 * Type-safe RPC config overrides for agent configuration.
 *
 * Instead of manually constructing `List[ConfigOverride]` with stringly-typed paths,
 * use `RpcConfig[T]` with typed `Field` accessors from `RpcFields[T]`:
 *
 * {{{
 * val f = MyAppConfig.rpcFields  // macro-generated
 * val config = RpcConfig.empty[MyAppConfig]
 *   .set(f[String]("appName"), "myApp")
 *   .set(f.nested("db")[String]("host"), "localhost")
 *
 * ConfigAgent.getWithConfig("input", config)
 * }}}
 *
 * Secret fields are excluded from the generated fields object at compile time,
 * preventing accidental secret overrides via RPC.
 */
final class RpcConfig[T] private[golem] (
  private[golem] val entries: List[ConfigOverride]
) {

  /** Set a typed config field override. */
  def set[A](field: RpcConfig.Field[T, A], value: A): RpcConfig[T] = {
    val encoded = field.schema.encodeElement(value) match {
      case Right(golem.data.ElementValue.Component(dv)) => dv
      case Right(_) =>
        throw new IllegalArgumentException(
          s"Expected component value for config override at ${field.path.mkString(".")}"
        )
      case Left(err) =>
        throw new IllegalArgumentException(
          s"Failed to encode config override at ${field.path.mkString(".")}: $err"
        )
    }
    new RpcConfig[T](entries :+ ConfigOverride(field.path, encoded, field.schema.elementSchema))
  }

  /** Convert to the untyped `List[ConfigOverride]` used by the runtime. */
  def toOverrides: List[ConfigOverride] = entries
}

object RpcConfig {

  /** Create an empty config override (no fields overridden). */
  def empty[T]: RpcConfig[T] = new RpcConfig[T](Nil)

  /**
   * A typed field accessor for a config field.
   *
   * `T` is the root config type, `A` is the field's value type.
   * The path and schema are captured at macro-expansion time.
   */
  final class Field[T, A](
    private[golem] val path: List[String],
    private[golem] val schema: GolemSchema[A]
  )

}
