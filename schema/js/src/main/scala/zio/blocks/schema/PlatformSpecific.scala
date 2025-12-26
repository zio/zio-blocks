package zio.blocks.schema

/**
 * JavaScript-specific platform implementation.
 */
trait PlatformSpecific extends Platform {
  override val isJVM: Boolean = false
  override val isJS: Boolean = true
  override val isNative: Boolean = false
  override val name: String = "JS"
}

