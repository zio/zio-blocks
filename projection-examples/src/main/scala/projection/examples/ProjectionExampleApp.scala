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

/*
 * Runs every projection example in turn, in the order they are meant to be
 * read:
 *   1. Example1PerEntity
 *   2. Example2CrossEntity
 *   3. Example3GlobalAggregate
 *   4. Example4SchemaEvolution
 *   5. Example5TagRename
 *
 * Each example can also be run on its own, see the comment at the top of its
 * file.
 *
 * Run it from the repository root with:
 *   sbt projection-examples/run
 */

package projection.examples

import zio.*

object ProjectionExampleApp extends ZIOAppDefault {

  def run: ZIO[Any, Throwable, Unit] =
    for {
      _ <- Console.printLine("=== ZIO Blocks Projection Examples ===").orDie
      _ <- Example1PerEntity.demo
      _ <- Example2CrossEntity.demo
      _ <- Example3GlobalAggregate.demo
      _ <- Example4SchemaEvolution.demo
      _ <- Example5TagRename.demo
      _ <- Console.printLine("\n=== All demos completed successfully ===").orDie
    } yield ()
}
