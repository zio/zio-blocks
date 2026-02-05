package zio.blocks.scope

/**
 * A typeclass that provides a [[Wire]] for constructing a service.
 *
 * Define a `Wireable` in a type's companion object to enable automatic wire
 * derivation for traits and abstract types that can't be instantiated directly.
 *
 * @example
 *   {{{
 *   trait Database { def query(sql: String): Result }
 *
 *   class PostgresDatabase(config: Config) extends Database with AutoCloseable {
 *     def query(sql: String): Result = ...
 *     def close(): Unit = ...
 *   }
 *
 *   object Database {
 *     // Scala 3
 *     given Wireable.Typed[Config, Database] = new Wireable[Database] {
 *       type In = Config
 *       def wire: Wire[Config, Database] = Wire.Shared[Config, Database] {
 *         val db = new PostgresDatabase($[Config])
 *         defer(db.close())
 *         Context[Database](db)
 *       }
 *     }
 *   }
 *   }}}
 *
 * @tparam Out
 *   The service type this wireable produces
 */
trait Wireable[+Out] {

  /** The dependencies required to construct the service. */
  type In

  /** The wire that constructs the service. */
  def wire: Wire[In, Out]
}

object Wireable extends WireableVersionSpecific {

  /**
   * A [[Wireable]] with its `In` type exposed in the type signature.
   *
   * Use this when defining manual wireables to preserve the dependency type
   * information:
   * {{{
   * given Wireable.Typed[Config, Database] = ...
   * }}}
   */
  type Typed[-In0, +Out] = Wireable[Out] { type In >: In0 }

  /**
   * Creates a [[Wireable]] from a pre-existing value.
   *
   * The value is wrapped in a shared wire with no dependencies.
   *
   * @example
   *   {{{
   *   object Config {
   *     given Wireable[Config] = Wireable(Config("jdbc://localhost", 8080))
   *   }
   *   }}}
   */
  def apply[T](value: T)(implicit ev: zio.blocks.context.IsNominalType[T]): Wireable.Typed[Any, T] =
    fromWire(Wire(value))

  /**
   * Creates a [[Wireable]] from an existing [[Wire]].
   *
   * @example
   *   {{{
   *   object Database {
   *     given Wireable[Database] = Wireable.fromWire(shared[PostgresDatabase])
   *   }
   *   }}}
   */
  def fromWire[In0, Out](w: Wire[In0, Out]): Wireable.Typed[In0, Out] =
    new Wireable[Out] {
      type In = In0
      def wire: Wire[In0, Out] = w
    }
}
