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

package zio.blocks.streams.internal

/**
 * Wraps a non-Throwable error value so it can propagate through the
 * Reader/Interpreter stack via exceptions. Used by
 * [[zio.blocks.streams.Stream.fail]] and caught by
 * [[zio.blocks.streams.Stream.run]]. The 4th constructor arg
 * (`writableStackTrace=false`) disables stack trace capture for performance.
 */
final class StreamError(val value: Any) extends Exception(null, null, true, false)
