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
 * Runs every projection example in turn: per-entity, cross-entity, global,
 * schema evolution and tag rename. Each example can also be run on its own, see
 * the comment at the top of its file.
 *
 * Run it from this directory with:
 *   sbt run
 */

package projection.examples

import zio.*

object ProjectionExampleApp extends ZIOAppDefault {

  def run: ZIO[Any, Throwable, Unit] =
    for {
      _ <- Console.printLine("=== ZIO Blocks Projection Examples ===").orDie
      _ <- PerEntityExample.demo
      _ <- CrossEntityExample.demo
      _ <- GlobalAggregateExample.demo
      _ <- SchemaEvolutionExample.demo
      _ <- TagRenameExample.demo
      _ <- Console.printLine("\n=== All demos completed successfully ===").orDie
    } yield ()
}
