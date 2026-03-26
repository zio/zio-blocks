package golem

/**
 * Mixin trait for automatic JSON-based snapshotting.
 *
 * Mix this into your agent implementation class to get automatic snapshot
 * save/load support. Bundle all mutable state into a case class `S` with a
 * `zio.blocks.schema.Schema[S]` instance, provide it as `var state`, and
 * implement `stateSchema` to return the schema instance.
 *
 * The macro detects this trait on the implementation class and generates
 * snapshot handlers that serialize/deserialize `state` as JSON using
 * zio-schema's `jsonCodec`, obtaining the schema from `stateSchema`.
 *
 * ==Example==
 * {{{
 * import zio.blocks.schema.Schema
 *
 * case class CounterState(value: Int) derives Schema
 *
 * @agentDefinition(snapshotting = "enabled")
 * trait MyCounter extends BaseAgent {
 *   class Id(val value: String)
 *   def increment(): Future[Int]
 * }
 *
 * @agentImplementation()
 * final class MyCounterImpl(name: String)
 *   extends MyCounter with Snapshotted[CounterState] {
 *
 *   var state: CounterState = CounterState(0)
 *   val stateSchema: Schema[CounterState] = Schema.derived
 *
 *   override def increment(): Future[Int] = Future.successful {
 *     state = state.copy(value = state.value + 1)
 *     state.value
 *   }
 * }
 * }}}
 *
 * @tparam S
 *   The state type. Must have a `zio.blocks.schema.Schema[S]` instance
 *   provided via `stateSchema`.
 */
trait Snapshotted[S] {
  var state: S
  def stateSchema: zio.blocks.schema.Schema[S]
}
