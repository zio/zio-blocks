package cloud.golem.sdk

/**
 * Marker supertype for user-facing agent traits.
 *
 * This exists purely for Scala ergonomics: user agent traits can extend `BaseAgent`
 * without importing any packaging/runtime-specific concepts.
 */
trait BaseAgent


