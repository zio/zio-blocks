package zio.blocks.schema.toon

import zio.test._

object IndentationSpec extends ZIOSpecDefault {
  def spec = suite("Indentation")(
    suite("ToonWriter")(
      test("indentation tracking") {
        val writer = ToonWriter()
        writer.writeRaw("level0")
        writer.newLine()
        writer.increaseIndent()
        writer.writeIndent()
        writer.writeRaw("level1")

        assertTrue(writer.toString == "level0\n  level1")
      },
      test("multiple indentation levels") {
        val writer = ToonWriter()
        writer.writeRaw("root")
        writer.newLine()
        writer.increaseIndent()
        writer.writeIndent()
        writer.writeRaw("level1")
        writer.newLine()
        writer.increaseIndent()
        writer.writeIndent()
        writer.writeRaw("level2")
        writer.newLine()
        writer.decreaseIndent()
        writer.writeIndent()
        writer.writeRaw("back to level1")

        assertTrue(writer.toString == "root\n  level1\n    level2\n  back to level1")
      },
      test("getIndentLevel tracks correctly") {
        val writer = ToonWriter()
        val level0 = writer.getIndentLevel
        writer.increaseIndent()
        val level1 = writer.getIndentLevel
        writer.increaseIndent()
        val level2 = writer.getIndentLevel
        writer.decreaseIndent()
        val level1Again = writer.getIndentLevel
        writer.decreaseIndent()
        val level0Again = writer.getIndentLevel
        writer.decreaseIndent() // Try to go below 0
        val levelStillZero = writer.getIndentLevel

        assertTrue(
          level0 == 0 &&
            level1 == 1 &&
            level2 == 2 &&
            level1Again == 1 &&
            level0Again == 0 &&
            levelStillZero == 0 // Can't go below 0
        )
      }
    ),
    suite("ToonReader")(
      test("skipIndentation counts spaces") {
        val reader = ToonReader("    hello")
        val spaces = reader.skipIndentation()
        assertTrue(spaces == 4)
      },
      test("getCurrentIndentLevel after skipIndentation") {
        val reader = ToonReader("  indented")
        reader.skipIndentation()
        assertTrue(reader.getCurrentIndentLevel == 1) // 2 spaces = 1 indent level
      },
      test("line tracking") {
        val reader = ToonReader("line1\nline2")
        assertTrue(reader.getLine == 1) &&
        {
          while (!reader.isEof && reader.peek() != '\n') reader.readString()
          reader.skipIndentation() // moves past remaining
          assertTrue(reader.getLine >= 1)
        }
      }
    )
  )
}
