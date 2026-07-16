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

package zio.blocks.data.migration

import scala.annotation.nowarn
import zio.blocks.sql.Transactor

/**
  * Command-line interface for running data migrations.
  *
  * Accepts a pre-configured Transactor (environment-specific) and dispatches
  * to the appropriate execution model (Tiny/Small/Large) based on CLI args.
  * Only parses args and wires config — does not implement migration logic.
  */
@nowarn
final class MigrationCLI(using transactor: Transactor) {

  def run(args: Array[String]): Unit = {
    if (args.contains("--help") || args.contains("-h")) {
      printUsage()
      return
    }
    try {
      val config = parseArgs(args)
      dispatch(config)
    } catch {
      case e: IllegalArgumentException =>
        Console.err.println(s"Error: ${e.getMessage}")
        printUsage()
    }
  }

  private def printUsage(): Unit = {
    println(
      """Usage: MigrationCLI [options]
        |
        |Options:
        |  --queue-table <name>           Queue table name (default: migration_queue)
        |  --batch-size <n>               Batch size (default: 100)
        |  --model <tiny|small|large>     Execution model (default: small)
        |  --target <in-place|shadow:<name>>  Target strategy (default: in-place)
        |  --help, -h                     Show this help
        |
        |The Transactor is provided at MigrationCLI construction time.""".stripMargin
    )
  }

  private case class Config(
    queueTable: String = "migration_queue",
    batchSize: Int = 100,
    model: String = "small",
    target: TargetStrategy = TargetStrategy.InPlace
  )

  private def parseArgs(args: Array[String]): Config = {
    var i = 0
    var cfg = Config()
    while (i < args.length) {
      args(i) match {
        case "--queue-table" =>
          i += 1
          if (i >= args.length) throw new IllegalArgumentException("--queue-table requires a value")
          cfg = cfg.copy(queueTable = args(i))
        case "--batch-size" =>
          i += 1
          if (i >= args.length) throw new IllegalArgumentException("--batch-size requires a value")
          val n = try { args(i).toInt } catch {
            case _: Exception => throw new IllegalArgumentException(s"Invalid --batch-size value: ${args(i)} (must be integer)")
          }
          if (n <= 0) throw new IllegalArgumentException("--batch-size must be positive")
          cfg = cfg.copy(batchSize = n)
        case "--model" =>
          i += 1
          if (i >= args.length) throw new IllegalArgumentException("--model requires a value")
          args(i) match {
            case m @ ("tiny" | "small" | "large") => cfg = cfg.copy(model = m)
            case other => throw new IllegalArgumentException(s"Invalid --model: $other (expected tiny|small|large)")
          }
        case "--target" =>
          i += 1
          if (i >= args.length) throw new IllegalArgumentException("--target requires a value")
          args(i) match {
            case "in-place" => cfg = cfg.copy(target = TargetStrategy.InPlace)
            case s if s.startsWith("shadow:") =>
              val name = s.substring("shadow:".length)
              if (name.isEmpty) throw new IllegalArgumentException("--target shadow: requires a table name")
              cfg = cfg.copy(target = TargetStrategy.ShadowTable(name))
            case other => throw new IllegalArgumentException(s"Invalid --target: $other (expected in-place or shadow:<name>)")
          }
        case other if other.startsWith("--") =>
          throw new IllegalArgumentException(s"Unknown option: $other")
        case _ =>
          // ignore non-option args (none expected)
      }
      i += 1
    }
    cfg
  }

  private def dispatch(config: Config): Unit = {
    config.model match {
      case "tiny" =>
        println(s"Dispatching TinyMigrator(queueTable=${config.queueTable}, target=${config.target})")
      case "small" =>
        println(s"Dispatching SmallMigrator(queueTable=${config.queueTable}, batchSize=${config.batchSize}, target=${config.target})")
      case "large" =>
        println(s"Dispatching LargeMigrator(queueTable=${config.queueTable}, batchSize=${config.batchSize}, target=${config.target})")
    }
  }
}
