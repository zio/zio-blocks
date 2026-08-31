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

package zio.blocks.config

import zio.test._

object FlagRegistrySpec extends ZIOSpecDefault {

  object TopStatic extends StaticFlag[Int](1)
  object TopDynamic extends DynamicFlag[Int](0, "5")

  def spec = suite("FlagRegistrySpec")(
    test("authoritative registry is singleton across accesses") {
      val r1 = Flag.registry
      val r2 = Flag.registry
      assertTrue(r1 eq r2)
    },
    test("Flag.registry mutation is visible via Flag.dump") {
      val sentinel = new Object()
      Flag.registry.put("repro.singleton.flag", sentinel)
      try {
        val dumped    = Flag.dump()
        val contains  = dumped.contains("repro.singleton.flag")
        val retrieved = Flag.registry.get("repro.singleton.flag")
        val same      = retrieved.asInstanceOf[AnyRef] eq sentinel.asInstanceOf[AnyRef]
        assertTrue(contains) && assertTrue(same)
      } finally Flag.registry.remove("repro.singleton.flag")
    },
    test("anonymous Flag mixins share authoritative registry and dump state") {
      val mixin1 = new Flag {}
      val mixin2 = new Flag {}
      val mixin1DelegatesToSingleton = mixin1.registry eq Flag.registry
      val mixin2DelegatesToSingleton = mixin2.registry eq Flag.registry
      val mixinsShareRegistry        = mixin1.registry eq mixin2.registry
      val sentinel = new Object()
      mixin1.registry.put("repro.mixin.flag", sentinel)
      try {
        val flagDump  = Flag.dump()
        val dump1     = mixin1.dump()
        val dump2     = mixin2.dump()
        val viaFlag   = Flag.registry.get("repro.mixin.flag").asInstanceOf[AnyRef] eq sentinel.asInstanceOf[AnyRef]
        val viaMixin2 = mixin2.registry.get("repro.mixin.flag").asInstanceOf[AnyRef] eq sentinel.asInstanceOf[AnyRef]
        val dumpsEqual = flagDump == dump1 && dump1 == dump2
        val contains = flagDump.contains("repro.mixin.flag") && dump1.contains("repro.mixin.flag") && dump2.contains(
          "repro.mixin.flag"
        )
        val wFlag    = Flag.nearMissWarnings("repro.mixin.flag")
        val w1       = mixin1.nearMissWarnings("repro.mixin.flag")
        val w2       = mixin2.nearMissWarnings("repro.mixin.flag")
        val warningsEqual = wFlag == w1 && w1 == w2
        assertTrue(mixin1DelegatesToSingleton) && assertTrue(mixin2DelegatesToSingleton) && assertTrue(
          mixinsShareRegistry
        ) && assertTrue(viaFlag) && assertTrue(viaMixin2) && assertTrue(dumpsEqual) && assertTrue(contains) && assertTrue(
          warningsEqual
        )
      } finally Flag.registry.remove("repro.mixin.flag")
    },
    test("StaticFlag and DynamicFlag share same authoritative registry") {
      val _ = (TopStatic.value, TopDynamic.expression)
      assertTrue(Flag.registry.containsKey(TopStatic.name)) &&
      assertTrue(Flag.registry.containsKey(TopDynamic.name)) &&
      assertTrue(Flag.registry.get(TopStatic.name).asInstanceOf[AnyRef] eq TopStatic.asInstanceOf[AnyRef]) &&
      assertTrue(Flag.registry.get(TopDynamic.name).asInstanceOf[AnyRef] eq TopDynamic.asInstanceOf[AnyRef]) &&
      assertTrue(Flag.dump().contains(TopStatic.name)) &&
      assertTrue(Flag.dump().contains(TopDynamic.name))
    }
  ) @@ TestAspect.sequential
}
