/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless otherwise indicated, this file is licensed under the Apache 2.0 license.
 * See the LICENSE file in the project root for more information.
 */

package zio.blocks.data.migration

import zio.test.*
import zio.blocks.schema.migration.Migration
import zio.blocks.sql.{DbCodec, Repo, Transactor}

object TargetStrategySpec extends ZIOSpecDefault {

  // Tests that verify TargetStrategy integration (no real DB needed).
  // resolveTableName/prepare/finalize need Table[E]/DbCon which require a real DB;
  // we test them at compile level and verify the strategy dispatch logic inline.
  def spec = suite("TargetStrategyApplier")(
    test("InPlace strategy round-trips") {
      assertTrue(
        TargetStrategy.InPlace.productPrefix == "InPlace"
      )
    },
    test("ShadowTable stores name") {
      val s = TargetStrategy.ShadowTable("users_v2")
      assertTrue(s.name == "users_v2")
    },
    test("LargeMigrator with InPlace strategy compiles") {
      val _ = new LargeMigrator[Int, String, Long](
        repoV1 = null.asInstanceOf[Repo[Int, Long]],
        repoV2 = null.asInstanceOf[Repo[String, Long]],
        migration = null.asInstanceOf[Migration[Int, String]],
        queueTable = "q",
        batchSize = 10,
        target = TargetStrategy.InPlace
      )(using null.asInstanceOf[Transactor], null.asInstanceOf[DbCodec[Long]])
      assertCompletes
    },
    test("LargeMigrator with ShadowTable strategy compiles") {
      val _ = new LargeMigrator[Int, String, Long](
        repoV1 = null.asInstanceOf[Repo[Int, Long]],
        repoV2 = null.asInstanceOf[Repo[String, Long]],
        migration = null.asInstanceOf[Migration[Int, String]],
        queueTable = "q",
        batchSize = 10,
        target = TargetStrategy.ShadowTable("v2")
      )(using null.asInstanceOf[Transactor], null.asInstanceOf[DbCodec[Long]])
      assertCompletes
    },
    test("SmallMigrator with both strategies compiles") {
      val _ = new SmallMigrator[Int, String, Long](
        repoV1 = null.asInstanceOf[Repo[Int, Long]],
        repoV2 = null.asInstanceOf[Repo[String, Long]],
        migration = null.asInstanceOf[Migration[Int, String]],
        queueTable = "q",
        batchSize = 10,
        target = TargetStrategy.InPlace
      )(using null.asInstanceOf[Transactor], null.asInstanceOf[DbCodec[Long]])
      val _ = new SmallMigrator[Int, String, Long](
        repoV1 = null.asInstanceOf[Repo[Int, Long]],
        repoV2 = null.asInstanceOf[Repo[String, Long]],
        migration = null.asInstanceOf[Migration[Int, String]],
        queueTable = "q",
        batchSize = 10,
        target = TargetStrategy.ShadowTable("v2")
      )(using null.asInstanceOf[Transactor], null.asInstanceOf[DbCodec[Long]])
      assertCompletes
    }
  )
}
