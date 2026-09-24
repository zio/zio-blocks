/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.streams

import zio.test._

object AsyncNioReadersApiSpec extends StreamsBaseSpec {
  def spec = suite("Async NIO reader API")(
    test("channel and socket factories honestly return AsyncReader") {
      assertTrue(scala.compiletime.testing.typeChecks("""
        import java.nio.channels.{AsynchronousByteChannel, AsynchronousSocketChannel}
        import zio.blocks.streams.AsyncNioReaders
        import zio.blocks.streams.io.Reader
        def channels(c: AsynchronousByteChannel): (Reader.AsyncReader[Byte], Reader.AsyncReader[Byte]) =
          (AsyncNioReaders.fromChannel(c), AsyncNioReaders.fromChannelUnmanaged(c))
        def sockets(s: AsynchronousSocketChannel): (Reader.AsyncReader[Byte], Reader.AsyncReader[Byte]) =
          (AsyncNioReaders.fromSocket(s), AsyncNioReaders.fromSocketUnmanaged(s))
      """))
    },
    test("blocking channels remain on NioReaders and remain synchronous") {
      assertTrue(
        scala.compiletime.testing
          .typeCheckErrors("""
        import java.nio.channels.ReadableByteChannel
        import zio.blocks.streams.NioReaders
        import zio.blocks.streams.io.Reader
        def wrong(c: ReadableByteChannel): Reader.AsyncReader[Byte] = NioReaders.fromChannel(c)
      """).nonEmpty
      )
    }
  )
}
