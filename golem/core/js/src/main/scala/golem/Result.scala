package golem

import golem.runtime.wit.WitResult

/**
 * This is a thin alias/wrapper over [[golem.runtime.wit.WitResult]] which
 * contains the full implementation.
 */
object Result {
  type Result[+Ok, +Err] = WitResult[Ok, Err]

  def ok[Ok](value: Ok): Result[Ok, Nothing] =
    WitResult.ok(value)

  def err[Err](value: Err): Result[Nothing, Err] =
    WitResult.err(value)

  def fromEither[Err, Ok](either: Either[Err, Ok]): Result[Ok, Err] =
    WitResult.fromEither(either)

  def fromOption[Ok](value: Option[Ok], orElse: => String): Result[Ok, String] =
    WitResult.fromOption(value, orElse)
}
