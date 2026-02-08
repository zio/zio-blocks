package zio.blocks.scope

/**
 * A typeclass that provides a [[Wire]] for constructing a service.
 *
 * Define a `Wireable` in a type's companion object to enable automatic wire
 * derivation for traits and abstract types that can't be instantiated directly.
 * When `shared[T]` or `unique[T]` is called and `T` is a trait or abstract
 * class, the macro will look for a `Wireable[T]` in implicit scope.
 *
 * ==Use Cases==
 *
 *   - '''Traits/Interfaces''': When you want to inject a trait but construct a
 *     concrete implementation
 *   - '''Abstract classes''': Similar to traits
 *   - '''Custom construction''': When the default constructor-based derivation
 *     is insufficient
 *
 * @example
 *   {{{
 *   import zio.blocks.scope._  // provides $, defer, Context DSL methods
 *
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
 *         val config = $[Config]
 *         val db = new PostgresDatabase(config $ identity)
 *         defer(db.close())
 *         Context[Database](db)
 *       }
 *     }
 *   }
 *   }}}
 *
 * @tparam Out
 *   the service type this wireable produces (covariant)
 *
 * @see
 *   [[Wireable.Typed]] for exposing the dependency type
 * @see
 *   [[Wire]] for the wire type returned by [[wire]]
 */
trait Wireable[+Out] {

  /**
   * The dependencies required to construct the service.
   *
   * This abstract type member specifies what services must be available in the
   * scope when constructing `Out`. Typically an intersection type of required
   * dependencies (e.g., `Config with Database`).
   */
  type In

  /**
   * The wire that constructs the service.
   *
   * This wire will be used by `shared[Out]` or `unique[Out]` when a `Wireable`
   * is found in implicit scope.
   *
   * @return
   *   a wire that can construct `Out` given dependencies `In`
   */
  def wire: Wire[In, Out]
}

/**
 * Companion object providing factory methods and type aliases for [[Wireable]].
 */
object Wireable extends WireableVersionSpecific {

  /**
   * A [[Wireable]] with its `In` (dependency) type exposed in the type
   * signature.
   *
   * Use this type alias when defining manual wireables to preserve the
   * dependency type information at the type level. This allows the compiler to
   * properly track what dependencies are required.
   *
   * The type uses a lower bound (`In >: In0`) so that a
   * `Wireable.Typed[Config, Database]` can be used where
   * `Wireable.Typed[Any, Database]` is expected (contravariance).
   *
   * @example
   *   {{{
   *   // Scala 3
   *   given Wireable.Typed[Config, Database] = new Wireable[Database] {
   *     type In = Config
   *     def wire = Wire.Shared[Config, Database] { ... }
   *   }
   *
   *   // Scala 2
   *   implicit val databaseWireable: Wireable.Typed[Config, Database] = ...
   *   }}}
   *
   * @tparam In0
   *   the dependency types required (contravariant via lower bound)
   * @tparam Out
   *   the service type produced (covariant)
   */
  type Typed[-In0, +Out] = Wireable[Out] { type In >: In0 }

  /**
   * Creates a [[Wireable]] from a pre-existing value.
   *
   * The value is wrapped in a shared wire with no dependencies. No cleanup is
   * registered because the value was created externally.
   *
   * @example
   *   {{{
   *   object Config {
   *     // Scala 3
   *     given Wireable[Config] = Wireable(Config("jdbc://localhost", 8080))
   *
   *     // Scala 2
   *     implicit val wireable: Wireable[Config] = Wireable(Config("jdbc://localhost", 8080))
   *   }
   *   }}}
   *
   * @param value
   *   the pre-existing value to wrap
   * @tparam T
   *   the service type
   * @return
   *   a wireable that provides the value with no dependencies
   */
  def apply[T](value: T): Wireable.Typed[Any, T] =
    fromWire(Wire(value))

  /**
   * Creates a [[Wireable]] from an existing [[Wire]].
   *
   * Use this when you already have a wire and want to make it available as a
   * `Wireable` in a companion object.
   *
   * @example
   *   {{{
   *   import zio.blocks.scope._  // provides shared DSL method
   *
   *   object Database {
   *     // Scala 3
   *     given Wireable[Database] = Wireable.fromWire(shared[PostgresDatabase])
   *
   *     // Scala 2
   *     implicit val wireable: Wireable[Database] = Wireable.fromWire(shared[PostgresDatabase])
   *   }
   *   }}}
   *
   * @param w
   *   the wire to wrap
   * @tparam In0
   *   the wire's dependency type
   * @tparam Out
   *   the wire's output type
   * @return
   *   a wireable wrapping the given wire
   */
  def fromWire[In0, Out](w: Wire[In0, Out]): Wireable.Typed[In0, Out] =
    new Wireable[Out] {
      type In = In0
      def wire: Wire[In0, Out] = w
    }
}
