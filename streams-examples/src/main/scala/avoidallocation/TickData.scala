package avoidallocation

/**
 * How to Avoid Allocation on Hot Paths — The Tick Data
 *
 * A batch of 10,000 trades for one symbol, with prices in micro-dollars.
 */
final case class Tick(symbol: String, priceMicros: Long, size: Int)

object TickData {
  val tickCount           = 10000
  val prices: Array[Long] = Array.tabulate(tickCount)(i => 187000000L + (i % 997) * 1300L)
  val sizes: Array[Int]   = Array.tabulate(tickCount)(i => 100 + i % 50)
  val ticks: Array[Tick]  = Array.tabulate(tickCount)(i => Tick("ACME", prices(i), sizes(i)))
}
