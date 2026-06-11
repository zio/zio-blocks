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
 * Wraps a non-Throwable error value raised by a *stream* so it can propagate
 * through the Reader/Interpreter stack via exceptions. Used by
 * [[zio.blocks.streams.Stream.fail]] and stream I/O readers, and caught by
 * [[zio.blocks.streams.Stream.run]].
 *
 * The sibling [[SinkError]] carries *sink*-originated errors. The two are
 * deliberately distinct, unrelated types so that `run` can project each through
 * the correct side of its error `Concat` (stream → `left`, sink → `right`), and
 * so that `Sink.mapError` (which catches only `SinkError`) never rewrites a
 * stream-origin error.
 *
 * This is a non-local *control signal*, not an ordinary error, so it mixes in
 * [[scala.util.control.ControlThrowable]]. That has two important consequences:
 *
 *   - It is excluded by [[scala.util.control.NonFatal]] and is not a subtype of
 *     `Exception`, so idiomatic defect handling in a consumer (`catch
 *     NonFatal`, `catch case _: Exception`, `scala.util.Try`) will not
 *     accidentally swallow a typed stream error. Error recovery belongs to the
 *     stream (`catchAll`), not to a sink. Only a deliberate
 *     `catch case _: Throwable` can intercept it — and sinks must not do that
 *     (see [[zio.blocks.streams.Sink.create]]).
 *   - It is caught intentionally by the runtime via the exact `case e:
 *     StreamError` type, which is unaffected by the parent change.
 *
 * `ControlThrowable` mixes in `NoStackTrace`, so stack trace capture is
 * disabled for performance (matching the previous `writableStackTrace=false`).
 */
final class StreamError(val value: Any) extends scala.util.control.ControlThrowable
