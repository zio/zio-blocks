package zio.blocks.scope

/**
 * Type-level evidence that service `T` exists somewhere in the `Stack`.
 *
 * This typeclass is resolved at compile time, ensuring you can only access
 * services that are actually available in the scope. Variance enables subtype
 * access:
 *   - Contravariant in `T`: If the stack has `Animal`, you can request `Dog`
 *     (if Dog <: Animal)
 *   - Covariant in `Stack`: Evidence for a larger stack implies evidence for a
 *     smaller stack
 *
 * @tparam T
 *   The service type being searched for
 * @tparam Stack
 *   The scope's type-level stack to search in
 */
trait InStack[-T, +Stack]

object InStack extends InStackVersionSpecific
