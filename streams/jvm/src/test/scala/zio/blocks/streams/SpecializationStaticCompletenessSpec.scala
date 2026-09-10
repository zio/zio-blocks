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

package zio.blocks.streams

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.security.MessageDigest
import scala.jdk.CollectionConverters._
import zio.blocks.streams.io.Reader
import zio.test._

object SpecializationStaticCompletenessSpec extends StreamsBaseSpec {
  private val includeCompiledInventory = scala.util.Properties.versionNumberString.startsWith("3.")
  private val javapAccessFlagWarning   =
    "Error: Access Flags: Unmatched bit position 0x8 for location CLASS for class file format RELEASE_21"
  private val exactNames = "read(?:Boolean|Byte|Char|Short|Int|Long|Float|Double|Bytes|Ints|Longs|Floats|Doubles)"
  private val exactDef   = ("\\bdef\\s+(" + exactNames + ")\\b").r
  private val exactCall  = ("\\.(" + exactNames + ")(?:Physical)?\\s*\\(").r
  private val newReader  =
    "\\bnew\\s+(?:(?:Reader\\.)?(SyncReader|AsyncReader|DelegatingReader)|Reader)(?=\\s*\\[)".r
  private val readerParent =
    "\\b(?:extends|with)\\s+(?:Reader\\.)?(?:SyncReader|AsyncReader|DelegatingReader|Reader)\\b".r
  private val nestedTypeDeclaration = "\\b(?:class|object|trait)\\s+[A-Za-z_$][A-Za-z0-9_$]*".r
  private val typeDeclaration       = "(?s)(?=\\b(?:class|object)\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\b([^{}]*?)\\{)".r
  private val factory               = "\\bdef\\s+\\w*[Rr]eader\\w*\\b|:\\s*Reader(?:\\.|\\[)".r
  private val factoryDef            = "\\bdef\\s+(\\w*[Rr]eader\\w*)\\b".r
  private val readMetadata          = "\\b(?:final\\s+val|def)\\s+(READ_[A-Z]+|readTag|storageLaneOfReadTag)\\b".r
  private val publicDef             =
    "^\\s*(?:final\\s+|override\\s+|inline\\s+)*def\\s+([A-Za-z0-9_]+|[-!#%&*+/:<=>?@\\\\^|~]+)".r
  private val publicVal        = "^\\s*(?:final\\s+|override\\s+|inline\\s+)*(?:lazy\\s+)?val\\s+([A-Za-z0-9_]+)".r
  private val namedOwner       = "\\b(?:class|object|trait|def)\\s+([A-Za-z_$][A-Za-z0-9_$]*)".r
  private val specializationId = "specialization-id:\\s*([a-z0-9][a-z0-9-]*)".r

  private def root: Path = {
    var p = Paths.get(System.getProperty("user.dir")).toAbsolutePath
    while (
      p != null &&
      !(Files.isRegularFile(p.resolve("build.sbt")) && Files.isDirectory(p.resolve("streams/shared/src/main")))
    ) p = p.getParent
    if (p == null) throw new AssertionError("cannot locate repository root from user.dir")
    p
  }

  private def sources: Vector[(String, Vector[String])] =
    Vector("shared", "jvm", "js").flatMap { platform =>
      val base = root.resolve(s"streams/$platform/src/main")
      Files
        .walk(base)
        .iterator
        .asScala
        .filter(p => Files.isRegularFile(p) && p.toString.endsWith(".scala"))
        .toVector
        .sortBy(_.toString)
        .map { p =>
          val rel = root.resolve("streams").relativize(p).toString.replace('\\', '/')
          rel -> Files.readAllLines(p, StandardCharsets.UTF_8).asScala.toVector
        }
    }

  private def codeOnly(text: String): String =
    text.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "")

  private def codeOnlyPreservingLines(text: String): String = {
    val withoutBlocks = "(?s)/\\*.*?\\*/".r.replaceAllIn(
      text,
      matched => matched.matched.map(character => if (character == '\n') '\n' else ' ')
    )
    "(?m)//.*$".r.replaceAllIn(withoutBlocks, matched => " " * matched.matched.length)
  }

  private def readerDeclarations(lines: Vector[String]): Map[Int, Vector[String]] = {
    val text = codeOnlyPreservingLines(lines.mkString("\n"))
    typeDeclaration
      .findAllMatchIn(text)
      .collect {
        case declaration
            if readerParent.findFirstIn(declaration.group(2)).nonEmpty &&
              nestedTypeDeclaration.findFirstIn(declaration.group(2)).isEmpty =>
          text.substring(0, declaration.start).count(_ == '\n') -> declaration.group(1)
      }
      .toVector
      .groupMap(_._1)(_._2)
  }

  private def stableId(parts: String*): String = {
    val bytes = MessageDigest.getInstance("SHA-256").digest(parts.mkString("\u0000").getBytes(StandardCharsets.UTF_8))
    bytes.take(8).map(byte => f"${byte & 0xff}%02x").mkString
  }

  private def discoveries: Set[String] = {
    val declaredMarkers = sources.flatMap { case (path, lines) =>
      lines.zipWithIndex.flatMap { case (line, index) =>
        specializationId.findAllMatchIn(line).map(m => (m.group(1), s"$path:${index + 1}"))
      }
    }
    val duplicateMarkers = declaredMarkers.groupBy(_._1).collect {
      case (id, occurrences) if occurrences.sizeCompare(1) > 0 => s"$id: ${occurrences.map(_._2).mkString(", ")}"
    }
    if (duplicateMarkers.nonEmpty)
      throw new AssertionError(s"duplicate specialization IDs:\n${duplicateMarkers.toVector.sorted.mkString("\n")}")

    sources.flatMap { case (path, lines) =>
      val routeFile = path.endsWith("/Stream.scala") || path.endsWith("/Sink.scala") || path.endsWith("/Pipeline.scala")
      val owners    = enclosingOwners(lines)
      val readers   = readerDeclarations(lines)
      val raw       = lines.zipWithIndex.flatMap { case (line, index) =>
        val found = Vector.newBuilder[(String, String, String)]
        readers.getOrElse(index, Vector.empty).foreach(name => found += (("reader", name, s"named-$name")))
        newReader.findAllMatchIn(line).foreach { m =>
          val nearbyMarkers = lines
            .slice(math.max(0, index - 2), index + 1)
            .flatMap(candidate => specializationId.findAllMatchIn(candidate).map(_.group(1)))
          if (nearbyMarkers.size != 1)
            throw new AssertionError(
              s"$path:${index + 1}: anonymous ${m.group(1)} requires exactly one adjacent specialization-id, found ${nearbyMarkers.mkString(", ")}"
            )
          found += (("reader", m.group(1), nearbyMarkers.head))
        }
        if (factory.findFirstIn(line).nonEmpty)
          found += ((
            "factory",
            factoryDef.findFirstMatchIn(line).map(_.group(1)).getOrElse("Reader"),
            stableId(codeOnly(line).trim)
          ))
        exactDef.findFirstMatchIn(line).foreach(m => found += (("exact", m.group(1), stableId(codeOnly(line).trim))))
        if (exactDef.findFirstIn(line).isEmpty)
          exactCall.findAllMatchIn(line).foreach(m => found += (("dispatch", m.group(1), s"lane-${m.group(1)}")))
        if (path.endsWith("/OpTag.scala"))
          readMetadata.findAllMatchIn(line).foreach(m => found += (("metadata", m.group(1), m.group(1))))
        if (routeFile && line.takeWhile(_.isWhitespace).length == 2)
          publicDef.findFirstMatchIn(line).foreach(m => found += (("route", m.group(1), m.group(1))))
        if (routeFile)
          publicVal.findFirstMatchIn(line).foreach { m =>
            if (line.takeWhile(_.isWhitespace).length == 2) found += (("route", m.group(1), m.group(1)))
          }
        val declaration = namedOwner.findFirstMatchIn(codeOnly(line)).map(_.group(1))
        found.result().map { case (kind, token, siteId) =>
          val owner =
            if (kind == "dispatch") declaration.map(name => s"${owners(index)}.$name").getOrElse(owners(index))
            else owners(index)
          (kind, token, owner, siteId)
        }
      }
      raw.map { case (kind, token, owner, siteId) =>
        s"$path|$kind|${encodeField(token)}|$owner|${encodeField(siteId)}"
      }
    }.toSet ++ (if (includeCompiledInventory) compiledSymbols else Set.empty)
  }

  /**
   * A deliberately small structural scanner: IDs depend on named scopes and
   * local source spelling, never line numbers or occurrence order.
   */
  private def enclosingOwners(lines: Vector[String]): Vector[String] = {
    val stack = scala.collection.mutable.ArrayBuffer.empty[(String, Int)]
    lines.map { original =>
      val line   = codeOnly(original)
      val indent = original.takeWhile(_.isWhitespace).length
      if (line.trim.nonEmpty)
        while (stack.lastOption.exists(_._2 >= indent)) stack.remove(stack.length - 1)
      val owner    = if (stack.isEmpty) "<top-level>" else stack.map(_._1).mkString(".")
      val declared = namedOwner.findFirstMatchIn(line).map(_.group(1))
      declared.foreach(name => stack += name -> indent)
      owner
    }
  }

  private def encodeField(value: String): String = value.replace("|", "%7C")

  private def methodBodies(lines: Vector[String]): Vector[(String, String, String)] = {
    val starts = lines.indices.filter(i => exactDef.findFirstIn(lines(i)).nonEmpty).toVector
    starts.map { start =>
      val indent = lines(start).takeWhile(_.isWhitespace).length
      val end    = ((start + 1) until lines.length).find { i =>
        val line = lines(i)
        line.trim.nonEmpty && line.takeWhile(_.isWhitespace).length <= indent &&
        line.matches("\\s*(?:override\\s+|private[^ ]*\\s+|protected\\s+|final\\s+)*(?:def|val|var|class|object)\\b.*")
      }.getOrElse(lines.length)
      val name  = exactDef.findFirstMatchIn(lines(start)).get.group(1)
      val owner = lines
        .take(start + 1)
        .reverseIterator
        .collectFirst {
          case line if line.matches(".*\\b(?:class|object|trait)\\s+\\w+.*") =>
            ".*\\b(?:class|object|trait)\\s+(\\w+).*".r.findFirstMatchIn(line).get.group(1)
        }
        .getOrElse("<top-level>")
      (owner, name, lines.slice(start, end).mkString("\n"))
    }
  }

  private lazy val javapOutput: Either[String, String] = {
    val location = Paths.get(classOf[Reader[_]].getProtectionDomain.getCodeSource.getLocation.toURI)
    if (!Files.isDirectory(location)) Left(s"production class location is not a directory: $location")
    else {
      val javap   = Paths.get(System.getProperty("java.home"), "bin", "javap").toString
      val classes =
        Files.walk(location).iterator.asScala.filter(_.toString.endsWith(".class")).toVector.sortBy(_.toString).map {
          file =>
            val cls = location.relativize(file).toString.stripSuffix(".class").replace('/', '.').replace('\\', '.')
            cls
        }
      Right(
        classes
          .grouped(200)
          .toVector
          .flatMap { group =>
            val process =
              new ProcessBuilder((Vector(javap, "-classpath", location.toString, "-c", "-p", "-s") ++ group): _*)
                .redirectErrorStream(true)
                .start()
            val text              = new String(process.getInputStream.readAllBytes(), StandardCharsets.UTF_8)
            val exit              = process.waitFor()
            val errors            = text.linesIterator.map(_.trim).filter(_.startsWith("Error:")).toVector
            val knownJavapWarning = errors.nonEmpty && errors.forall(_ == javapAccessFlagWarning)
            if (exit != 0 && !knownJavapWarning) throw new AssertionError(s"javap exited $exit:\n$text")
            Vector(text)
          }
          .mkString("\n")
      )
    }
  }

  private lazy val compiledSymbols: Set[String] = javapOutput.fold(
    _ => Set.empty,
    { text =>
      val classHeader = "(?m)^(?:public |final |abstract )*(?:class|interface) ([^ ]+)(.*)$".r
      classHeader
        .findAllMatchIn(text)
        .flatMap { declaration =>
          val owner = declaration.group(1)
          val end   = classHeader
            .findFirstMatchIn(text.substring(declaration.end))
            .map(_.start + declaration.end)
            .getOrElse(text.length)
          val block  = text.substring(declaration.start, end)
          val reader =
            if (declaration.group(2).matches(".*(?:Reader|SyncReader|AsyncReader).*"))
              Set(s"jvm-compiled|compiledReader|$owner|$owner|class")
            else Set.empty[String]
          val methods = block.split("(?m)(?=^  (?:public|protected|private).+[;{]?$)").iterator
          reader ++ methods.flatMap { section =>
            val header     = section.linesIterator.nextOption().getOrElse("").trim
            val descriptor = "(?m)^    descriptor: (.+)$".r.findFirstMatchIn(section).map(_.group(1)).getOrElse("")
            val methodId   = stableId(header, descriptor)
            val factory    =
              if (descriptor.matches(".*\\)Lzio/blocks/streams/(?:io/)?[^;]*Reader(?:\\$[^;]+)?;"))
                Set(s"jvm-compiled|compiledFactory|$methodId|$owner|$methodId")
              else Set.empty[String]
            val dispatches =
              "// (?:Interface)?Method (zio/blocks/streams/[^: ]+\\.read(?:Boolean|Byte|Char|Short|Int|Long|Float|Double|Bytes|Ints|Longs|Floats|Doubles))".r
                .findAllMatchIn(section)
                .map(m => s"jvm-compiled|compiledDispatch|${m.group(1).replace('/', '.')}|$owner|$methodId")
                .toSet
            factory ++ dispatches
          }.toSet
        }
        .toSet
    }
  )

  private def javapViolations: Vector[String] = javapOutput.fold(
    Vector(_),
    text =>
      text
        .split("(?m)(?=^(?:public |final |abstract )*(?:class|interface) )")
        .toVector
        .flatMap { classBlock =>
          val owner = classBlock.linesIterator.nextOption().getOrElse("unknown class").trim
          classBlock.split("(?m)(?=^  (?:public|protected|private).+\\);$)").toVector.collect {
            case section
                if section.matches(s"(?s)^  .*$exactNames\\(.*") &&
                  section.matches("(?s).*zio/blocks/streams/io/Reader(?:\\$SyncReader|\\$AsyncReader)?\\.read:.*") =>
              s"$owner :: ${section.linesIterator.next().trim}"
          }
        }
        .distinct
  )

  private def behavioralTestCaseIds: Vector[String] = {
    val roots       = Vector("shared", "jvm", "js").map(platform => root.resolve(s"streams/$platform/src/test"))
    val test        = "\\btest\\(\"([^\"]+)\"\\)".r
    val occurrences = scala.collection.mutable.Map.empty[(String, String), Int].withDefaultValue(0)
    roots
      .filter(Files.isDirectory(_))
      .flatMap { base =>
        Files
          .walk(base)
          .iterator
          .asScala
          .filter(path => Files.isRegularFile(path) && path.toString.endsWith(".scala"))
          .toVector
      }
      .sortBy(_.toString)
      .flatMap { path =>
        val owner = path.getFileName.toString.stripSuffix(".scala")
        test.findAllMatchIn(Files.readString(path)).map { matched =>
          val title   = matched.group(1)
          val key     = owner -> title
          val ordinal = occurrences(key)
          occurrences.update(key, ordinal + 1)
          s"$owner::$title::$ordinal"
        }
      }
  }

  def spec = suite("specialization static completeness")(
    test("checked-in inventory reconciles production discoveries in both directions") {
      val actual = discoveries
      if (
        sys.env.get("SPECIALIZATION_INVENTORY_UPDATE").contains("true") ||
        sys.props.get("specialization.inventory.update").contains("true")
      )
        Files.write(
          root.resolve("streams/jvm/src/test/resources/specialization-conformance-inventory.txt"),
          (actual.toVector.sorted.mkString("\n") + "\n").getBytes(StandardCharsets.UTF_8)
        )
      val entries      = SpecializationConformanceInventory.entries
      val instrumented =
        try { Class.forName("scala.runtime.coverage.Invoker"); true }
        catch {
          case _: ClassNotFoundException =>
            try { Class.forName("scoverage.Invoker"); true }
            catch { case _: ClassNotFoundException => false }
        }
      val compareCompiled  = includeCompiledInventory && !instrumented
      val comparableActual = if (compareCompiled) actual else actual.filterNot(_.startsWith("jvm-compiled|"))
      val expectedAll      = entries.map(_.id).toSet
      val expected         = if (compareCompiled) expectedAll else expectedAll.filterNot(_.startsWith("jvm-compiled|"))
      val missing          = (comparableActual -- expected).toVector.sorted
      val stale            = (expected -- comparableActual).toVector.sorted
      val testCaseIds      = behavioralTestCaseIds
      val testCases        = testCaseIds.toSet
      val duplicates       =
        entries.groupBy(_.id).collect { case (id, copies) if copies.sizeCompare(1) > 0 => id }.toVector.sorted
      val duplicateTestCases =
        testCaseIds.groupBy(identity).collect { case (id, copies) if copies.sizeCompare(1) > 0 => id }.toVector.sorted
      val duplicateClaims = entries.filter(entry => entry.testCases.distinct.size != entry.testCases.size)
      val unclassified    =
        entries.filterNot(entry => entry.classification == "PullRoute" || entry.classification.startsWith("NonPull("))
      val badLinks = entries.filterNot(entry =>
        entry.classification.startsWith("NonPull(") || (entry.testCases.nonEmpty && entry.testCases.forall(testCases))
      )
      if (missing.nonEmpty || stale.nonEmpty)
        throw new AssertionError(s"uninventoried:\n${missing.mkString("\n")}\nstale:\n${stale.mkString("\n")}")
      if (duplicates.nonEmpty)
        throw new AssertionError(s"duplicate inventory IDs:\n${duplicates.mkString("\n")}")
      if (duplicateTestCases.nonEmpty)
        throw new AssertionError(s"duplicate qualified test case IDs:\n${duplicateTestCases.mkString("\n")}")
      if (duplicateClaims.nonEmpty)
        throw new AssertionError(s"duplicate inventory claims:\n${duplicateClaims.map(_.id).mkString("\n")}")
      if (unclassified.nonEmpty)
        throw new AssertionError(s"unclassified inventory rows:\n${unclassified.map(_.id).mkString("\n")}")
      if (badLinks.nonEmpty)
        throw new AssertionError(s"missing behavioral test links:\n${badLinks.map(_.symbol).mkString("\n")}")
      assertTrue(
        missing.isEmpty,
        stale.isEmpty,
        duplicates.isEmpty,
        duplicateTestCases.isEmpty,
        duplicateClaims.isEmpty,
        unclassified.isEmpty,
        badLinks.isEmpty
      )
    },
    test("source gate rejects variance/evidence cheats and exact-to-generic reads") {
      val cheats = sources.flatMap { case (path, lines) =>
        lines.zipWithIndex.collect {
          case (line, i)
              if ((line.contains("@uncheckedVariance") || line.contains("unchecked.uncheckedVariance")) &&
                !line.contains("Concat.WithOut") && !line
                  .contains("import scala.annotation.unchecked.uncheckedVariance")) ||
                line.matches(".*asInstanceOf\\s*\\[\\s*JvmType\\.Infer.*") ||
                line.matches(".*asInstanceOf\\s*\\[[^]]*<:<[^]]*\\].*") ||
                line.matches(".*(?:def|val)\\s+\\w*[Ee]vidence\\w*.*<:<.*") =>
            s"$path:${i + 1}: ${line.trim}"
        }
      }
      val inward = sources.flatMap { case (path, lines) =>
        methodBodies(lines).collect {
          case (owner, name, body)
              if codeOnly(body).linesIterator.exists(line =>
                line.matches(".*\\.read\\[[^]]+\\]\\s*\\(.*") || line.matches("\\s*(?:this\\.)?read\\s*\\(.*")
              ) =>
            s"$path:$owner.$name"
        }
      }
      if (cheats.nonEmpty || inward.nonEmpty) throw new AssertionError((cheats ++ inward).mkString("\n"))
      assertTrue(cheats.isEmpty, inward.isEmpty)
    },
    test("compiled JVM exact-read methods never invoke generic Reader.read") {
      val violations = javapViolations
      if (violations.nonEmpty) throw new AssertionError(violations.mkString("\n"))
      assertTrue(violations.isEmpty)
    }
  )
}
