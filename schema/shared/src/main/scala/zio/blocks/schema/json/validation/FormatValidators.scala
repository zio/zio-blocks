package zio.blocks.schema.json.validation

import java.time.{LocalDate, LocalDateTime, LocalTime, OffsetDateTime, Duration => JDuration}
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.regex.Pattern
import scala.util.Try

/**
 * Format validators for JSON Schema string formats.
 *
 * Implements common formats defined in JSON Schema 2020-12. Format validation
 * is advisory by default but can be used for strict validation.
 *
 * @see
 *   https://json-schema.org/draft/2020-12/json-schema-validation#section-7
 */
object FormatValidators {

  /**
   * Validates a string against a format.
   *
   * @param format
   *   The format name (e.g., "date-time", "email")
   * @param value
   *   The string value to validate
   * @return
   *   true if the value matches the format, false otherwise
   */
  def validate(format: String, value: String): Boolean =
    validators.get(format).forall(_.apply(value))

  /**
   * Registry of format validators.
   */
  val validators: Map[String, String => Boolean] = Map(
    // Date and time formats (RFC 3339)
    "date-time" -> validateDateTime,
    "date"      -> validateDate,
    "time"      -> validateTime,
    "duration"  -> validateDuration,

    // Internet formats
    "email"         -> validateEmail,
    "idn-email"     -> validateEmail,        // Simplified
    "hostname"      -> validateHostname,
    "idn-hostname"  -> validateHostname,     // Simplified
    "ipv4"          -> validateIpv4,
    "ipv6"          -> validateIpv6,
    "uri"           -> validateUri,
    "uri-reference" -> validateUriReference,
    "iri"           -> validateUri,          // Simplified
    "iri-reference" -> validateUriReference, // Simplified
    "uri-template"  -> validateUriTemplate,

    // JSON related
    "json-pointer"          -> validateJsonPointer,
    "relative-json-pointer" -> validateRelativeJsonPointer,

    // Other
    "uuid"  -> validateUuid,
    "regex" -> validateRegex
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Date/Time Validators
  // ─────────────────────────────────────────────────────────────────────────

  private def validateDateTime(value: String): Boolean =
    Try(OffsetDateTime.parse(value)).isSuccess ||
      Try(LocalDateTime.parse(value)).isSuccess ||
      Try(OffsetDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME)).isSuccess

  private def validateDate(value: String): Boolean =
    Try(LocalDate.parse(value)).isSuccess

  private def validateTime(value: String): Boolean =
    Try(LocalTime.parse(value)).isSuccess ||
      value.matches("""^\d{2}:\d{2}:\d{2}(\.\d+)?(Z|[+-]\d{2}:\d{2})?$""")

  private def validateDuration(value: String): Boolean =
    Try(JDuration.parse(value)).isSuccess ||
      value.matches("""^P(\d+Y)?(\d+M)?(\d+W)?(\d+D)?(T(\d+H)?(\d+M)?(\d+(\.\d+)?S)?)?$""")

  // ─────────────────────────────────────────────────────────────────────────
  // Email Validators
  // ─────────────────────────────────────────────────────────────────────────

  private val emailPattern: Pattern = Pattern.compile(
    """^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$"""
  )

  private def validateEmail(value: String): Boolean =
    emailPattern.matcher(value).matches()

  // ─────────────────────────────────────────────────────────────────────────
  // Hostname Validators
  // ─────────────────────────────────────────────────────────────────────────

  private val hostnamePattern: Pattern = Pattern.compile(
    """^[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(\.[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$"""
  )

  private def validateHostname(value: String): Boolean =
    value.length <= 253 && hostnamePattern.matcher(value).matches()

  // ─────────────────────────────────────────────────────────────────────────
  // IP Address Validators
  // ─────────────────────────────────────────────────────────────────────────

  private val ipv4Pattern: Pattern = Pattern.compile(
    """^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"""
  )

  private def validateIpv4(value: String): Boolean =
    ipv4Pattern.matcher(value).matches()

  private def validateIpv6(value: String): Boolean = {
    // Simplified IPv6 validation
    val normalized = value.toLowerCase
    if (normalized.contains("::")) {
      // Compressed format
      val parts = normalized.split("::", -1)
      parts.length == 2 && parts.forall { part =>
        part.isEmpty || part.split(':').forall(isValidIPv6Part)
      }
    } else {
      val parts = normalized.split(':')
      parts.length == 8 && parts.forall(isValidIPv6Part)
    }
  }

  private def isValidIPv6Part(part: String): Boolean =
    part.length >= 1 && part.length <= 4 && part.forall(c => c.isDigit || (c >= 'a' && c <= 'f'))

  // ─────────────────────────────────────────────────────────────────────────
  // URI Validators
  // ─────────────────────────────────────────────────────────────────────────

  private def validateUri(value: String): Boolean =
    Try(new java.net.URI(value)).map(uri => uri.isAbsolute).getOrElse(false)

  private def validateUriReference(value: String): Boolean =
    Try(new java.net.URI(value)).isSuccess

  private def validateUriTemplate(value: String): Boolean = {
    // Basic URI template validation (RFC 6570)
    var inExpression = false
    var i            = 0
    while (i < value.length) {
      val c = value.charAt(i)
      if (c == '{') {
        if (inExpression) return false
        inExpression = true
      } else if (c == '}') {
        if (!inExpression) return false
        inExpression = false
      }
      i += 1
    }
    !inExpression
  }

  // ─────────────────────────────────────────────────────────────────────────
  // JSON Pointer Validators
  // ─────────────────────────────────────────────────────────────────────────

  private def validateJsonPointer(value: String): Boolean =
    value.isEmpty || (value.startsWith("/") && !value.contains("~") ||
      value.matches("""^(/([^/~]|~0|~1)*)*$"""))

  private def validateRelativeJsonPointer(value: String): Boolean = {
    if (value.isEmpty) return false
    val parts = value.split('#')
    if (parts.length > 2) return false
    val numPart     = if (parts.length == 2) parts(0) else value
    val pointerPart = if (parts.length == 2) "#" + parts(1) else ""

    numPart.forall(_.isDigit) && (pointerPart.isEmpty || validateJsonPointer(pointerPart.drop(1)))
  }

  // ─────────────────────────────────────────────────────────────────────────
  // UUID Validators
  // ─────────────────────────────────────────────────────────────────────────

  private def validateUuid(value: String): Boolean =
    Try(UUID.fromString(value)).isSuccess

  // ─────────────────────────────────────────────────────────────────────────
  // Regex Validators
  // ─────────────────────────────────────────────────────────────────────────

  private def validateRegex(value: String): Boolean =
    Try(Pattern.compile(value)).isSuccess

  // ─────────────────────────────────────────────────────────────────────────
  // Registration
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Registers a custom format validator.
   */
  def withFormat(name: String, validator: String => Boolean): Map[String, String => Boolean] =
    validators + (name -> validator)
}
