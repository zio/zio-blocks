package zio.blocks.template

import zio.test._

object CssColorSpec extends ZIOSpecDefault {
  def spec = suite("CssColor")(
    test("Hex renders with hash") {
      assertTrue(CssColor.Hex("ff0000").map(_.render) == Some("#ff0000"))
    },
    test("Hex validates format") {
      assertTrue(CssColor.Hex("invalid").isEmpty)
    },
    test("Rgb renders") {
      assertTrue(CssColor.Rgb(255, 0, 0).render == "rgb(255,0,0)")
    },
    test("Rgba renders") {
      assertTrue(CssColor.Rgba(255, 0, 0, 0.5).render == "rgba(255,0,0,0.5)")
    },
    test("Hsl renders with percent") {
      assertTrue(CssColor.Hsl(120, 50, 50).render == "hsl(120,50%,50%)")
    },
    test("Named validates against whitelist") {
      assertTrue(CssColor.Named("red").map(_.render) == Some("red"))
    },
    test("Named rejects invalid names") {
      assertTrue(CssColor.Named("notacolor").isEmpty)
    },
    test("Named unsafe allows any string") {
      assertTrue(CssColor.Named.unsafe("anything").render == "anything")
    }
  )
}
