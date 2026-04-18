#!/usr/bin/env scala
/**
 * Scala-aware parser to extract public methods from Scala source files.
 *
 * Usage: scala extract-methods.scala <source-file> [<type-name>]
 *
 * If type-name is provided, extracts methods from that specific class/object.
 * Otherwise, extracts all top-level public methods.
 */

import scala.io.Source
import scala.util.Using
import java.io.File

case class MethodDef(
  name: String,
  isPrivate: Boolean,
  depth: Int,
  lineNum: Int
)

object MethodExtractor {
  def extractMethods(filePath: String, typeName: Option[String] = None): List[String] = {
    val file = new File(filePath)
    if (!file.exists()) {
      System.err.println(s"Error: File not found: $filePath")
      return List()
    }

    Using.resource(Source.fromFile(file)) { source =>
      val lines = source.getLines().toList
      val methods = scala.collection.mutable.ListBuffer[MethodDef]()

      var depth = 0
      var currentTypeDepth = -1
      var inTargetType = typeName.isEmpty
      var targetTypeStart = -1

      lines.zipWithIndex.foreach { case (line, idx) =>
        val trimmed = line.trim
        val lineNum = idx + 1

        // Track brace depth
        val openBraces = line.count(_ == '{')
        val closeBraces = line.count(_ == '}')

        // Check if we're entering target type
        if (typeName.isDefined && !inTargetType) {
          if (trimmed.matches(s"(abstract\\s+)?(class|trait|object)\\s+${typeName.get}\\s*[({].*")) {
            inTargetType = true
            currentTypeDepth = depth
            targetTypeStart = depth + openBraces
          }
        }

        // Check if we're leaving target type
        if (inTargetType && typeName.isDefined && depth <= currentTypeDepth && closeBraces > 0) {
          if (idx > targetTypeStart && depth < targetTypeStart) {
            inTargetType = false
          }
        }

        // Extract methods if we're in the right scope
        if (inTargetType) {
          // Match method definitions at proper depth
          val methodPattern = """^(.*?)(override\s+|final\s+|inline\s+|implicit\s+)*(def|given)\s+([a-zA-Z_][a-zA-Z0-9_]*|\+\+|-|::|!|&|\||\^|~|<<|>>|==|!=|<|>|<=|>=|\*|/|%|@|#|$)(\s*\[|:|\(|\s|=)""".r

          val isPrivate = trimmed.matches("^(private|protected)\\s+.*") || trimmed.matches("^.*private\\[.*\\]\\s+.*")

          // Filter out helper methods (starting with loop, mk, or underscore)
          methodPattern.findFirstMatchIn(line).foreach { m =>
            val methodName = m.group(4)
            if (!methodName.startsWith("loop") && !methodName.startsWith("mk") && !methodName.startsWith("_")) {
              methods += MethodDef(methodName, isPrivate, depth, lineNum)
            }
          }
        }

        // Update depth
        depth += openBraces - closeBraces
        depth = math.max(0, depth)
      }

      // Filter and return
      methods
        .filter(m => !m.isPrivate) // Exclude private methods
        .map(_.name)
        .distinct
        .sorted
        .toList
    }
  }
}

@main def run(args: String*): Unit = {
  if (args.length < 1) {
    System.err.println("Usage: scala extract-methods.scala <source-file> [<type-name>]")
    System.exit(1)
  }

  val sourceFile = args(0)
  val typeName = if (args.length > 1) Some(args(1)) else None

  val methods = MethodExtractor.extractMethods(sourceFile, typeName)

  methods.foreach(println)

  if (methods.isEmpty) {
    System.exit(2) // Indicate no methods found
  } else {
    System.exit(0) // Success
  }
}
