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

package zio.blocks.projection

import scala.annotation.StaticAnnotation

/**
 * Gives an event case a numeric tag in place of its default name-based tag.
 *
 * Every event an [[EventStore]] appends is stored with a tag that identifies
 * its kind, and reads can be filtered by tag. By default the tag is the case
 * name, so renaming a case changes the tag that new events are stored with.
 * Annotating the case with `@eventTag(n)` makes the tag the string form of `n`
 * instead, for example `"1"`, which stays the same when the case is renamed.
 *
 * Put the annotation on each case class of a sealed event type, with a
 * different number for each case. The numbers are not checked for uniqueness.
 * It is also honoured on a single, non-sealed event type.
 *
 * {{{
 * sealed trait UserEvent
 *
 * @eventTag(1)
 * case class Created(userId: String, name: String) extends UserEvent
 *
 * @eventTag(2)
 * case class Deleted(userId: String) extends UserEvent
 * }}}
 *
 * The tag is the event's identity in the store's `tag` column and in
 * [[EventEnvelope]]. It does not change how the event's payload is encoded.
 *
 * @param value
 *   the number to use as the tag
 */
final class eventTag(val value: Int) extends StaticAnnotation
