package zio.blocks.scope

/**
 * Type-level evidence that service `T` exists somewhere in the `Stack`.
 *
 * This typeclass is resolved at compile time, ensuring you can only access
 * services that are actually available in the scope.
 *
 * ==Variance==
 *
 *   - '''Contravariant in `T`''': If the stack has `Animal`, you can request
 *     `Dog` (when `Dog <: Animal`). This follows the typical subtyping rules
 *     for consumers.
 *   - '''Covariant in `Stack`''': Evidence for a child stack (which is a
 *     subtype of parent stacks) can be used where parent stack evidence is
 *     expected.
 *
 * @tparam T
 *   the service type being searched for (contravariant)
 * @tparam Stack
 *   the scope's type-level stack to search in (covariant)
 */
trait InStack[-T, +Stack]

/**
 * Companion object providing implicit instances for [[InStack]].
 *
 * Instances are derived automatically via macros that search the scope's
 * type-level structure.
 */
object InStack extends InStackVersionSpecific
