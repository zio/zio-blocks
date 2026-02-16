package scope.examples

import zio.blocks.scope._

import scala.annotation.nowarn

/**
 * Demonstrates `leak(scopedValue)` for third-party library interop.
 *
 * Sometimes you must pass scoped resources to legacy or third-party libraries
 * that require raw types. The `leak` escape hatch extracts the underlying
 * value, bypassing compile-time safety. Use sparingly—you assume responsibility
 * for ensuring the resource outlives its usage.
 */

// ---------------------------------------------------------------------------
// Fake classes simulating a legacy networking library
// ---------------------------------------------------------------------------

/** Configuration for establishing a socket connection. */
case class SocketConfig(host: String, port: Int)

/**
 * A managed socket that implements AutoCloseable.
 *
 * In a real scenario, this would wrap an actual network socket.
 */
class ManagedSocket(val config: SocketConfig) extends AutoCloseable {
  private var closed = false

  def send(data: Array[Byte]): Unit =
    if (closed) throw new IllegalStateException("Socket closed")
    else println(s"  [Socket] Sent ${data.length} bytes to ${config.host}:${config.port}")

  def receive(): Array[Byte] =
    if (closed) throw new IllegalStateException("Socket closed")
    else {
      println(s"  [Socket] Received response from ${config.host}:${config.port}")
      "ACK".getBytes
    }

  override def close(): Unit = {
    closed = true
    println(s"  [Socket] Connection to ${config.host}:${config.port} closed")
  }
}

/**
 * Simulates a third-party protocol handler that cannot be modified.
 *
 * This legacy library requires a raw `ManagedSocket` and does not understand
 * scoped types. This is the typical scenario where `leak` becomes necessary.
 */
object LegacyProtocolHandler {

  /**
   * Handles a connection using a proprietary protocol.
   *
   * @param socket
   *   the raw socket—must remain open for the duration of this call
   */
  def handleConnection(socket: ManagedSocket): Unit = {
    println("  [Legacy] Starting proprietary protocol handshake...")
    socket.send("HELLO".getBytes)
    val response = socket.receive()
    println(s"  [Legacy] Handshake complete: ${new String(response)}")
  }
}

// ---------------------------------------------------------------------------
// Example entry point
// ---------------------------------------------------------------------------

@main def legacyLibraryInteropExample(): Unit = {
  println("=== Legacy Library Interop Example ===\n")
  println("Demonstrating leak() for passing scoped resources to third-party code.\n")

  Scope.global.scoped { scope =>
    import scope._
    // Allocate the socket as a scoped resource.
    // The socket is tagged with the scope's type, preventing accidental escape.
    val scopedSocket: $[ManagedSocket] = allocate(
      Resource.fromAutoCloseable(new ManagedSocket(SocketConfig("api.example.com", 443)))
    )
    println("Allocated scoped socket.\n")

    // -------------------------------------------------------------------------
    // WARNING: leak() bypasses compile-time safety guarantees!
    //
    // After calling leak(), the compiler cannot prevent you from:
    //   - Storing the socket in a field that outlives the scope
    //   - Passing it to code that might cache or close it unexpectedly
    //   - Using it after the scope has closed
    //
    // Only use leak() when:
    //   1. The third-party API genuinely cannot accept scoped types
    //   2. You can guarantee the scope outlives all usage of the leaked value
    //   3. The third-party code won't cache or transfer ownership
    // -------------------------------------------------------------------------

    // WARNING: leak() bypasses compile-time safety — use only for third-party interop.
    // This intentionally escapes the scoped type and will emit a compiler warning.
    @nowarn("msg=.*leaked.*|.*leak.*")
    val rawSocket: ManagedSocket = leak(scopedSocket)

    println("Passing raw socket to legacy protocol handler:")
    LegacyProtocolHandler.handleConnection(rawSocket)

    println("\nScope exiting - socket will be closed automatically:")
  }

  println("\nExample complete. The socket was safely closed when the scope exited.")
}
