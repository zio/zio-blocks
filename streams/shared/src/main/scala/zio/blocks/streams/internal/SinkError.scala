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
 * Wraps a non-Throwable error value raised by a *sink* so it can propagate
 * through the Sink/Interpreter stack via exceptions. The sibling of
 * [[StreamError]] (which carries *stream*-originated errors); both are control
 * signals (`ControlThrowable`), but they are deliberately distinct, unrelated
 * types.
 *
 * Keeping the two origins as separate types — rather than a single
 * `StreamError` — is what lets [[zio.blocks.streams.Stream.run]] tell a
 * sink-originated failure (`Sink.fail`) apart from a stream-originated failure
 * and project each through the correct side of its error `Concat`, and lets
 * `Sink.mapError` transform only sink-origin errors without ever rewriting a
 * stream error (which would otherwise produce a `ClassCastException` when the
 * two error types differ).
 *
 * `ControlThrowable` mixes in `NoStackTrace`, so stack trace capture is
 * disabled for performance.
 */
final class SinkError(val value: Any) extends scala.util.control.ControlThrowable
