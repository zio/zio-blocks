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

package into

import zio.blocks.schema.{Into, SchemaError}
import util.ShowExpr.show

// Demonstrates converting an external DTO into a validated domain model.
// A custom Into instance is provided for Email so the macro can wire it
// into the derived UserDto → User conversion automatically.
object IntoDomainBoundaryExample extends App {

  final class Email private (val value: String)
  object Email {
    def apply(value: String): Email = new Email(value)
  }

  implicit val stringToEmail: Into[String, Email] = { s =>
    if (s.contains("@")) Right(Email(s))
    else Left(SchemaError(s"Invalid email address: '$s'"))
  }

  case class UserDto(name: String, email: String, age: Int)
  case class User(name: String, email: Email, age: Long)

  val toUser = Into.derived[UserDto, User]

  // Happy path — all fields convert successfully
  show(toUser.into(UserDto("Alice", "alice@example.com", 30)))

  // Failure — email validation rejects the value
  show(toUser.into(UserDto("Bob", "not-an-email", 25)))
}
