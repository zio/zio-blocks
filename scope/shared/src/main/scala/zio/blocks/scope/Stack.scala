package zio.blocks.scope

/** The empty stack type, representing a scope with no services. */
sealed trait TNil

/**
 * A type-level cons cell for the scope stack.
 *
 * @tparam H
 *   The head element (typically `Context[T]`)
 * @tparam T
 *   The tail of the stack
 */
sealed trait ::[+H, +T]
