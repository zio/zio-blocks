package zio.blocks.schema

import java.net.IDN
import scala.util.control.NonFatal

/**
 * JVM-specific platform implementation.
 */
trait PlatformSpecific extends Platform {
  override val isJVM: Boolean = true
  override val isJS: Boolean  = false
  override val name: String   = "JVM"

  def idnToAscii(idn: String): Option[String] =
    try new Some(IDN.toASCII(idn, IDN.USE_STD3_ASCII_RULES))
    catch {
      case err if NonFatal(err) => None
    }
}
