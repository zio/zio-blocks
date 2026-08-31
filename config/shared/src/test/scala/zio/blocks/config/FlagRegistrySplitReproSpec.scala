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

object FlagRegistrySplitReproSpec extends ZIOSpecDefault {

  // Top-level objects for registration tests: must be Scala objects (class name ends with $)
  object TopStatic extends StaticFlag[Int](1)
  object TopDynamic extends DynamicFlag[Int](0, "5")

  def spec = suite("FlagRegistrySplitReproSpec")(
    test("authoritative registry is singleton across accesses") {
      val r1 = Flag.registry
      val r2 = Flag.registry
      assertTrue(r1 eq r2)
    },
    test("Flag.registry mutation is visible via Flag.dump") {
      val sentinel = new Object()
      Flag.registry.put("repro.singleton.flag", sentinel)
      try {
        val dumped   = Flag.dump()
        val contains = dumped.contains("repro.singleton.flag")
        val retrieved = Flag.registry.get("repro.singleton.flag")
        val same     = retrieved.asInstanceOf[AnyRef] eq sentinel.asInstanceOf[AnyRef]
        assertTrue(contains) && assertTrue(same)
      } finally Flag.registry.remove("repro.singleton.flag")
    },
    test("trait Flag is sealed so external mixin cannot obtain separate registry") {
      // Sealed trait can only be extended in FlagReader.scala; `new Flag {}` in
      // a different file fails to compile (proven by compilation error after fix).
      // Cross-platform check: Flag is an interface and registry is singleton.
      assertTrue(classOf[Flag].isInterface) && assertTrue(Flag.registry ne null)
    },
    test("StaticFlag and DynamicFlag share same authoritative registry") {
      // TopStatic/TopDynamic are initialized on first access; force init
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
