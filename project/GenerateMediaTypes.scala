import sbt._
import sbt.Keys._
import scala.io.Source
import scala.util.Using
import java.net.URL

object GenerateMediaTypes {

  case class MimeDetails(
    compressible: Option[Boolean],
    extensions: Option[List[String]]
  )

  case class MediaTypeInfo(
    mainType: String,
    subType: String,
    compressible: Boolean,
    extensions: List[String]
  ) {
    def binary: Boolean = mainType match {
      case "image" | "video" | "audio" | "font" | "model" | "multipart" => true
      case "application"                                                => !subType.startsWith("xml") && !subType.endsWith("json") && !subType.endsWith("javascript")
      case "text"                                                       => false
      case _                                                            => !compressible
    }

    def scalaName: String = s"`$subType`"

    def render: String = {
      val extList =
        if (extensions.isEmpty) ""
        else s", fileExtensions = List(${extensions.map(e => s""""$e"""").mkString(", ")})"

      "lazy val " + scalaName + ": MediaType =\n  " + "MediaType(\"" + mainType + "\", \"" + subType + "\", compressible = " + compressible + ", binary = " + binary + extList + ")"
    }
  }

  val reservedWords = Set(
    "abstract",
    "case",
    "catch",
    "class",
    "def",
    "do",
    "else",
    "extends",
    "false",
    "final",
    "finally",
    "for",
    "forSome",
    "if",
    "implicit",
    "import",
    "lazy",
    "match",
    "new",
    "null",
    "object",
    "override",
    "package",
    "private",
    "protected",
    "return",
    "sealed",
    "super",
    "this",
    "throw",
    "trait",
    "true",
    "try",
    "type",
    "val",
    "var",
    "while",
    "with",
    "yield"
  )

  def fetchMimeDb(): Map[String, MimeDetails] = {
    val url  = "https://raw.githubusercontent.com/jshttp/mime-db/master/db.json"
    val json = Using(Source.fromURL(new URL(url)))(_.mkString).get
    parseJson(json)
  }

  def parseJson(json: String): Map[String, MimeDetails] = {
    // Format: { "application/json": { "compressible": true, "extensions": ["json"] }, ... }
    val entries = scala.collection.mutable.Map[String, MimeDetails]()

    val content   = json.trim.stripPrefix("{").stripSuffix("}")
    var remaining = content

    while (remaining.nonEmpty) {
      val keyStart = remaining.indexOf('"')
      if (keyStart < 0) {
        remaining = ""
      } else {
        val keyEnd = remaining.indexOf('"', keyStart + 1)
        val key    = remaining.substring(keyStart + 1, keyEnd)

        val objStart = remaining.indexOf('{', keyEnd)
        if (objStart >= 0) {
          var depth  = 1
          var objEnd = objStart + 1
          while (depth > 0 && objEnd < remaining.length) {
            remaining.charAt(objEnd) match {
              case '{' => depth += 1
              case '}' => depth -= 1
              case _   =>
            }
            objEnd += 1
          }
          val objJson = remaining.substring(objStart, objEnd)

          val compressible =
            if (objJson.contains("\"compressible\":true") || objJson.contains("\"compressible\": true")) Some(true)
            else if (objJson.contains("\"compressible\":false") || objJson.contains("\"compressible\": false"))
              Some(false)
            else None

          val extensions = {
            val extMatch = """\"extensions\"\s*:\s*\[(.*?)\]""".r.findFirstMatchIn(objJson)
            extMatch.map { m =>
              val extStr = m.group(1)
              """\"([^\"]+)\"""".r.findAllMatchIn(extStr).map(_.group(1)).toList
            }
          }

          entries(key) = MimeDetails(compressible, extensions)
          remaining = remaining.substring(objEnd)
        } else {
          remaining = ""
        }
      }
    }

    entries.toMap
  }

  def toMediaTypeInfos(mimeDb: Map[String, MimeDetails]): List[MediaTypeInfo] =
    mimeDb.map { case (mimeType, details) =>
      val parts = mimeType.split('/')
      if (parts.length == 2) {
        Some(
          MediaTypeInfo(
            mainType = parts(0),
            subType = parts(1),
            compressible = details.compressible.getOrElse(false),
            extensions = details.extensions.getOrElse(Nil)
          )
        )
      } else None
    }.flatten.toList

  def generateCode(mediaTypes: List[MediaTypeInfo]): String = {
    val grouped = mediaTypes.groupBy(_.mainType).toList.sortBy(_._1)

    val objects = grouped.map { case (mainType, types) =>
      val scalaMainType = if (mainType.contains("-")) mainType.replace("-", "_") else mainType
      val sortedTypes   = types.sortBy(_.subType)

      val typeDefs = sortedTypes.map(_.render).map(_.split("\n").map("    " + _).mkString("\n")).mkString("\n\n")
      val allList  = sortedTypes.map(t => s"      ${t.scalaName}").mkString(",\n")

      s"""  object $scalaMainType {
         |$typeDefs
         |
         |    lazy val all: List[MediaType] = List(
         |$allList
         |    )
         |  }""".stripMargin
    }.mkString("\n\n")

    val allObjects = grouped.map { case (mainType, _) =>
      val scalaMainType = if (mainType.contains("-")) mainType.replace("-", "_") else mainType
      s"$scalaMainType.all"
    }.mkString(" ++ ")

    s"""package zio.blocks.mediatype
       |
       |// AUTO-GENERATED - DO NOT EDIT
       |// Generated from https://github.com/jshttp/mime-db
       |// Run: sbt generateMediaTypes
       |
       |object MediaTypes {
       |  import zio.blocks.mediatype.MediaType
       |
       |  lazy val any: MediaType = MediaType("*", "*")
       |
       |$objects
       |
       |  lazy val allMediaTypes: List[MediaType] = $allObjects
       |}
       |""".stripMargin
  }

  val generateMediaTypesTask = Def.task {
    val log = streams.value.log
    log.info("Fetching mime-db from GitHub...")

    val mimeDb = fetchMimeDb()
    log.info(s"Fetched ${mimeDb.size} MIME types")

    val mediaTypes = toMediaTypeInfos(mimeDb)
    log.info(s"Parsed ${mediaTypes.size} valid media types")

    val code = generateCode(mediaTypes)

    val outputFile = file("mediatype/shared/src/main/scala/zio/blocks/mediatype/MediaTypes.scala")
    IO.write(outputFile, code)
    log.info(s"Generated ${outputFile.absolutePath}")

    outputFile
  }
}
