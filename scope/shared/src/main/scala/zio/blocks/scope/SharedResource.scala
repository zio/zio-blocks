package zio.blocks.scope

/**
 * A resource that can be recycled for reuse after each lease.
 *
 * @param resource
 *   the underlying resource acquisition/finalization recipe
 * @param recycle
 *   a function invoked when a leased value is returned to the pool and the pool
 *   is observed to be open at release time. Due to the inherent race between
 *   releasing a value and closing the pool, `recycle` may still run even if the
 *   pool becomes closed concurrently after this observation. In that case the
 *   recycled value is destroyed rather than reused. The function must reset the
 *   value for the next lease. If it throws, the value is destroyed and the
 *   release fails with that exception (or with a finalization exception that
 *   suppresses it when destruction also fails).
 * @tparam A
 *   the value type
 */
final case class SharedResource[A](resource: Resource[A], recycle: A => Unit)
