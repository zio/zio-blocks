package golem

/**
 * Mixin trait for automatic JSON-based snapshotting.
 *
 * Mix this into your agent implementation class to get automatic snapshot
 * save/load support. Bundle all mutable state into a case class `S` with a
 * `zio.blocks.schema.Schema[S]` instance, and provide it as `var state`.
 *
 * The macro detects this trait on the implementation class, summons `Schema[S]`
 * at compile time, and generates snapshot handlers that serialize/deserialize
 * `state` as JSON using zio-schema's `jsonCodec`.
 *
 * ==Example==
 * {{{
 * import zio.blocks.schema.Schema
 *
 * case class CounterState(value: Int) derives Schema
 *
 * @agentDefinition(snapshotting = "enabled")
 * trait MyCounter extends BaseAgent {
 *   @constructor def create(value: String): Unit = ()
 *   def increment(): Future[Int]
 * }
 *
 * @agentImplementation()
 * final class MyCounterImpl(name: String)
 *   extends MyCounter with Snapshotted[CounterState] {
 *
 *   var state: CounterState = CounterState(0)
 *
 *   override def increment(): Future[Int] = Future.successful {
 *     state = state.copy(value = state.value + 1)
 *     state.value
 *   }
 * }
 * }}}
 *
 * @tparam S
 *   The state type. Must have a `zio.blocks.schema.Schema[S]` instance.
 */
trait Snapshotted[S] {
  var state: S
}
