package zio.blocks.schema.into.edge

import zio.test._
import zio.blocks.schema._

object DeepNestingSpec extends ZIOSpecDefault {

  def spec = suite("DeepNestingSpec")(
    suite("Deep Nesting")(
      test("should handle deep nested case classes (10 levels)") {
        case class Level1(level2: Level2)
        case class Level2(level3: Level3)
        case class Level3(level4: Level4)
        case class Level4(level5: Level5)
        case class Level5(level6: Level6)
        case class Level6(level7: Level7)
        case class Level7(level8: Level8)
        case class Level8(level9: Level9)
        case class Level9(level10: Level10)
        case class Level10(value: Int)

        case class Level1Copy(level2: Level2Copy)
        case class Level2Copy(level3: Level3Copy)
        case class Level3Copy(level4: Level4Copy)
        case class Level4Copy(level5: Level5Copy)
        case class Level5Copy(level6: Level6Copy)
        case class Level6Copy(level7: Level7Copy)
        case class Level7Copy(level8: Level8Copy)
        case class Level8Copy(level9: Level9Copy)
        case class Level9Copy(level10: Level10Copy)
        case class Level10Copy(value: Int)

        val derivation = Into.derived[Level1, Level1Copy]
        val input      = Level1(
          Level2(
            Level3(
              Level4(
                Level5(
                  Level6(
                    Level7(
                      Level8(
                        Level9(
                          Level10(42)
                        )
                      )
                    )
                  )
                )
              )
            )
          )
        )
        val result = derivation.into(input)

        assertTrue(result.isRight)
        result.map { level1 =>
          assertTrue(level1.level2.level3.level4.level5.level6.level7.level8.level9.level10.value == 42)
        }
      },
      test("should handle deep nested case classes with Option (20 levels)") {
        case class L1(l2: Option[L2])
        case class L2(l3: Option[L3])
        case class L3(l4: Option[L4])
        case class L4(l5: Option[L5])
        case class L5(l6: Option[L6])
        case class L6(l7: Option[L7])
        case class L7(l8: Option[L8])
        case class L8(l9: Option[L9])
        case class L9(l10: Option[L10])
        case class L10(l11: Option[L11])
        case class L11(l12: Option[L12])
        case class L12(l13: Option[L13])
        case class L13(l14: Option[L14])
        case class L14(l15: Option[L15])
        case class L15(l16: Option[L16])
        case class L16(l17: Option[L17])
        case class L17(l18: Option[L18])
        case class L18(l19: Option[L19])
        case class L19(l20: Option[L20])
        case class L20(value: Int)

        case class L1Copy(l2: Option[L2Copy])
        case class L2Copy(l3: Option[L3Copy])
        case class L3Copy(l4: Option[L4Copy])
        case class L4Copy(l5: Option[L5Copy])
        case class L5Copy(l6: Option[L6Copy])
        case class L6Copy(l7: Option[L7Copy])
        case class L7Copy(l8: Option[L8Copy])
        case class L8Copy(l9: Option[L9Copy])
        case class L9Copy(l10: Option[L10Copy])
        case class L10Copy(l11: Option[L11Copy])
        case class L11Copy(l12: Option[L12Copy])
        case class L12Copy(l13: Option[L13Copy])
        case class L13Copy(l14: Option[L14Copy])
        case class L14Copy(l15: Option[L15Copy])
        case class L15Copy(l16: Option[L16Copy])
        case class L16Copy(l17: Option[L17Copy])
        case class L17Copy(l18: Option[L18Copy])
        case class L18Copy(l19: Option[L19Copy])
        case class L19Copy(l20: Option[L20Copy])
        case class L20Copy(value: Int)

        val derivation = Into.derived[L1, L1Copy]
        // Build a chain of 20 levels
        val input = L1(
          Some(
            L2(
              Some(
                L3(
                  Some(
                    L4(
                      Some(
                        L5(
                          Some(
                            L6(
                              Some(
                                L7(
                                  Some(
                                    L8(
                                      Some(
                                        L9(
                                          Some(
                                            L10(
                                              Some(
                                                L11(
                                                  Some(
                                                    L12(
                                                      Some(
                                                        L13(
                                                          Some(
                                                            L14(
                                                              Some(
                                                                L15(
                                                                  Some(
                                                                    L16(
                                                                      Some(
                                                                        L17(
                                                                          Some(
                                                                            L18(
                                                                              Some(
                                                                                L19(
                                                                                  Some(
                                                                                    L20(42)
                                                                                  )
                                                                                )
                                                                              )
                                                                            )
                                                                          )
                                                                        )
                                                                      )
                                                                    )
                                                                  )
                                                                )
                                                              )
                                                            )
                                                          )
                                                        )
                                                      )
                                                    )
                                                  )
                                                )
                                              )
                                            )
                                          )
                                        )
                                      )
                                    )
                                  )
                                )
                              )
                            )
                          )
                        )
                      )
                    )
                  )
                )
              )
            )
          )
        )
        val result = derivation.into(input)

        assertTrue(result.isRight)
        result.map { l1 =>
          // Verify structure by checking that we can traverse to the end
          assertTrue(l1.l2.isDefined)
          assertTrue(l1.l2.get.l3.isDefined)
          assertTrue(l1.l2.get.l3.get.l4.isDefined)
          assertTrue(l1.l2.get.l3.get.l4.get.l5.isDefined)
          assertTrue(l1.l2.get.l3.get.l4.get.l5.get.l6.isDefined)
          assertTrue(l1.l2.get.l3.get.l4.get.l5.get.l6.get.l7.isDefined)
          assertTrue(l1.l2.get.l3.get.l4.get.l5.get.l6.get.l7.get.l8.isDefined)
          assertTrue(l1.l2.get.l3.get.l4.get.l5.get.l6.get.l7.get.l8.get.l9.isDefined)
          assertTrue(l1.l2.get.l3.get.l4.get.l5.get.l6.get.l7.get.l8.get.l9.get.l10.isDefined)
          assertTrue(l1.l2.get.l3.get.l4.get.l5.get.l6.get.l7.get.l8.get.l9.get.l10.get.l11.isDefined)
          assertTrue(l1.l2.get.l3.get.l4.get.l5.get.l6.get.l7.get.l8.get.l9.get.l10.get.l11.get.l12.isDefined)
          assertTrue(l1.l2.get.l3.get.l4.get.l5.get.l6.get.l7.get.l8.get.l9.get.l10.get.l11.get.l12.get.l13.isDefined)
          assertTrue(
            l1.l2.get.l3.get.l4.get.l5.get.l6.get.l7.get.l8.get.l9.get.l10.get.l11.get.l12.get.l13.get.l14.isDefined
          )
          assertTrue(
            l1.l2.get.l3.get.l4.get.l5.get.l6.get.l7.get.l8.get.l9.get.l10.get.l11.get.l12.get.l13.get.l14.get.l15.isDefined
          )
          assertTrue(
            l1.l2.get.l3.get.l4.get.l5.get.l6.get.l7.get.l8.get.l9.get.l10.get.l11.get.l12.get.l13.get.l14.get.l15.get.l16.isDefined
          )
          assertTrue(
            l1.l2.get.l3.get.l4.get.l5.get.l6.get.l7.get.l8.get.l9.get.l10.get.l11.get.l12.get.l13.get.l14.get.l15.get.l16.get.l17.isDefined
          )
          assertTrue(
            l1.l2.get.l3.get.l4.get.l5.get.l6.get.l7.get.l8.get.l9.get.l10.get.l11.get.l12.get.l13.get.l14.get.l15.get.l16.get.l17.get.l18.isDefined
          )
          assertTrue(
            l1.l2.get.l3.get.l4.get.l5.get.l6.get.l7.get.l8.get.l9.get.l10.get.l11.get.l12.get.l13.get.l14.get.l15.get.l16.get.l17.get.l18.get.l19.isDefined
          )
          assertTrue(
            l1.l2.get.l3.get.l4.get.l5.get.l6.get.l7.get.l8.get.l9.get.l10.get.l11.get.l12.get.l13.get.l14.get.l15.get.l16.get.l17.get.l18.get.l19.get.l20.isDefined
          )
          assertTrue(
            l1.l2.get.l3.get.l4.get.l5.get.l6.get.l7.get.l8.get.l9.get.l10.get.l11.get.l12.get.l13.get.l14.get.l15.get.l16.get.l17.get.l18.get.l19.get.l20.get.value == 42
          )
        }
      },
      test("should handle deep nested collections (5 levels)") {
        val derivation = Into.derived[List[List[List[List[List[Int]]]]], Vector[Vector[Vector[Vector[Vector[Int]]]]]]
        val input      = List(
          List(
            List(
              List(
                List(1, 2, 3)
              )
            )
          )
        )
        val result = derivation.into(input)

        assertTrue(result.isRight)
        result.map { nested =>
          assertTrue(nested.head.head.head.head.head == 1)
          assertTrue(nested.head.head.head.head(1) == 2)
          assertTrue(nested.head.head.head.head(2) == 3)
        }
      }
    )
  )
}
