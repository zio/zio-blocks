package zio.blocks.schema.json

import zio.blocks.schema.SchemaBaseSpec
import zio.test._

object JsonContextDetectionSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("JsonContextDetectionSpec")(
    suite("key position detection")(
      test("detects key position: {$key: \"value\"}") {
        val parts  = Seq("{", ": \"value\"}")
        val result = ContextDetector.detectContexts(parts)
        assertTrue(result == Right(List(InterpolationContext.Key)))
      },
      test("detects key position with whitespace: { $key : \"value\" }") {
        val parts  = Seq("{ ", " : \"value\" }")
        val result = ContextDetector.detectContexts(parts)
        assertTrue(result == Right(List(InterpolationContext.Key)))
      },
      test("detects key position after comma: {\"a\": 1, $key: 2}") {
        val parts  = Seq("{\"a\": 1, ", ": 2}")
        val result = ContextDetector.detectContexts(parts)
        assertTrue(result == Right(List(InterpolationContext.Key)))
      },
      test("detects multiple keys: {$k1: 1, $k2: 2}") {
        val parts  = Seq("{", ": 1, ", ": 2}")
        val result = ContextDetector.detectContexts(parts)
        assertTrue(result == Right(List(InterpolationContext.Key, InterpolationContext.Key)))
      },
      test("detects key in nested object: {\"a\": {$key: 1}}") {
        val parts  = Seq("{\"a\": {", ": 1}}")
        val result = ContextDetector.detectContexts(parts)
        assertTrue(result == Right(List(InterpolationContext.Key)))
      }
    ),
    suite("value position detection")(
      test("detects value position: {\"key\": $value}") {
        val parts  = Seq("{\"key\": ", "}")
        val result = ContextDetector.detectContexts(parts)
        assertTrue(result == Right(List(InterpolationContext.Value)))
      },
      test("detects value position with whitespace: { \"key\" : $value }") {
        val parts  = Seq("{ \"key\" : ", " }")
        val result = ContextDetector.detectContexts(parts)
        assertTrue(result == Right(List(InterpolationContext.Value)))
      },
      test("detects value position in array: [$value]") {
        val parts  = Seq("[", "]")
        val result = ContextDetector.detectContexts(parts)
        assertTrue(result == Right(List(InterpolationContext.Value)))
      },
      test("detects multiple values in array: [$v1, $v2, $v3]") {
        val parts  = Seq("[", ", ", ", ", "]")
        val result = ContextDetector.detectContexts(parts)
        assertTrue(
          result == Right(
            List(
              InterpolationContext.Value,
              InterpolationContext.Value,
              InterpolationContext.Value
            )
          )
        )
      },
      test("detects value in nested object: {\"a\": {\"b\": $value}}") {
        val parts  = Seq("{\"a\": {\"b\": ", "}}")
        val result = ContextDetector.detectContexts(parts)
        assertTrue(result == Right(List(InterpolationContext.Value)))
      },
      test("detects value at top level: $value") {
        val parts  = Seq("", "")
        val result = ContextDetector.detectContexts(parts)
        assertTrue(result == Right(List(InterpolationContext.Value)))
      },
      test("detects mixed array values: [{\"k\": $v}, $standalone]") {
        val parts  = Seq("[{\"k\": ", "}, ", "]")
        val result = ContextDetector.detectContexts(parts)
        assertTrue(result == Right(List(InterpolationContext.Value, InterpolationContext.Value)))
      }
    ),
    suite("inside-string position detection")(
      test("detects inside-string position: {\"key\": \"hello $name\"}") {
        val parts  = Seq("{\"key\": \"hello ", "\"}")
        val result = ContextDetector.detectContexts(parts)
        assertTrue(result == Right(List(InterpolationContext.InString)))
      },
      test("detects inside-string at start: {\"key\": \"$name says hi\"}") {
        val parts  = Seq("{\"key\": \"", " says hi\"}")
        val result = ContextDetector.detectContexts(parts)
        assertTrue(result == Right(List(InterpolationContext.InString)))
      },
      test("detects inside-string at end: {\"key\": \"hi $name\"}") {
        val parts  = Seq("{\"key\": \"hi ", "\"}")
        val result = ContextDetector.detectContexts(parts)
        assertTrue(result == Right(List(InterpolationContext.InString)))
      },
      test("detects only interpolation in string: {\"key\": \"$name\"}") {
        val parts  = Seq("{\"key\": \"", "\"}")
        val result = ContextDetector.detectContexts(parts)
        assertTrue(result == Right(List(InterpolationContext.InString)))
      },
      test("detects multiple inside-string: {\"msg\": \"$a and $b\"}") {
        val parts  = Seq("{\"msg\": \"", " and ", "\"}")
        val result = ContextDetector.detectContexts(parts)
        assertTrue(result == Right(List(InterpolationContext.InString, InterpolationContext.InString)))
      },
      test("detects adjacent inside-string: {\"s\": \"$a$b$c\"}") {
        val parts  = Seq("{\"s\": \"", "", "", "\"}")
        val result = ContextDetector.detectContexts(parts)
        assertTrue(
          result == Right(
            List(
              InterpolationContext.InString,
              InterpolationContext.InString,
              InterpolationContext.InString
            )
          )
        )
      },
      test("handles empty interpolation in string: {\"s\": \"prefix$xsuffix\"}") {
        // When the result of interpolation could be empty
        val parts  = Seq("{\"s\": \"prefix", "suffix\"}")
        val result = ContextDetector.detectContexts(parts)
        assertTrue(result == Right(List(InterpolationContext.InString)))
      }
    ),
    suite("mixed context detection")(
      test("detects multiple contexts: {$k1: $v1, $k2: \"hi $name\"}") {
        val parts  = Seq("{", ": ", ", ", ": \"hi ", "\"}")
        val result = ContextDetector.detectContexts(parts)
        assertTrue(
          result == Right(
            List(
              InterpolationContext.Key,
              InterpolationContext.Value,
              InterpolationContext.Key,
              InterpolationContext.InString
            )
          )
        )
      },
      test("detects key and value in same object: {$key: $value}") {
        val parts  = Seq("{", ": ", "}")
        val result = ContextDetector.detectContexts(parts)
        assertTrue(result == Right(List(InterpolationContext.Key, InterpolationContext.Value)))
      },
      test("handles complex nested structure") {
        // {$k1: {$k2: [$v1, "hello $s"]}}
        val parts  = Seq("{", ": {", ": [", ", \"hello ", "\"]}}")
        val result = ContextDetector.detectContexts(parts)
        assertTrue(
          result == Right(
            List(
              InterpolationContext.Key,
              InterpolationContext.Key,
              InterpolationContext.Value,
              InterpolationContext.InString
            )
          )
        )
      }
    ),
    suite("edge cases")(
      test("handles empty parts list") {
        val result = ContextDetector.detectContexts(Seq.empty)
        assertTrue(result == Right(Nil))
      },
      test("handles single part (no interpolations)") {
        val result = ContextDetector.detectContexts(Seq("{\"key\": \"value\"}"))
        assertTrue(result == Right(Nil))
      },
      test("handles escaped quotes in string") {
        // {"key": "say \"$name\""}
        val parts  = Seq("{\"key\": \"say \\\"", "\\\"\"}")
        val result = ContextDetector.detectContexts(parts)
        assertTrue(result == Right(List(InterpolationContext.InString)))
      },
      test("handles escaped backslash before quote") {
        // {"key": "path\\$name"}
        val parts  = Seq("{\"key\": \"path\\\\", "\"}")
        val result = ContextDetector.detectContexts(parts)
        assertTrue(result == Right(List(InterpolationContext.InString)))
      },
      test("handles array of objects") {
        // [{"a": $v1}, {"b": $v2}]
        val parts  = Seq("[{\"a\": ", "}, {\"b\": ", "}]")
        val result = ContextDetector.detectContexts(parts)
        assertTrue(result == Right(List(InterpolationContext.Value, InterpolationContext.Value)))
      },
      test("handles deeply nested arrays") {
        // [[[$value]]]
        val parts  = Seq("[[[", "]]]")
        val result = ContextDetector.detectContexts(parts)
        assertTrue(result == Right(List(InterpolationContext.Value)))
      },
      test("handles empty object") {
        // $value after empty object
        val parts  = Seq("[{}, ", "]")
        val result = ContextDetector.detectContexts(parts)
        assertTrue(result == Right(List(InterpolationContext.Value)))
      },
      test("handles empty array") {
        // $value after empty array
        val parts  = Seq("[[], ", "]")
        val result = ContextDetector.detectContexts(parts)
        assertTrue(result == Right(List(InterpolationContext.Value)))
      },
      test("handles newlines and tabs in JSON") {
        val parts  = Seq("{\n\t", ":\n\t", "\n}")
        val result = ContextDetector.detectContexts(parts)
        assertTrue(result == Right(List(InterpolationContext.Key, InterpolationContext.Value)))
      },
      test("handles value after literal true") {
        // [true, $value]
        val parts  = Seq("[true, ", "]")
        val result = ContextDetector.detectContexts(parts)
        assertTrue(result == Right(List(InterpolationContext.Value)))
      },
      test("handles value after literal false") {
        // [false, $value]
        val parts  = Seq("[false, ", "]")
        val result = ContextDetector.detectContexts(parts)
        assertTrue(result == Right(List(InterpolationContext.Value)))
      },
      test("handles value after literal null") {
        // [null, $value]
        val parts  = Seq("[null, ", "]")
        val result = ContextDetector.detectContexts(parts)
        assertTrue(result == Right(List(InterpolationContext.Value)))
      },
      test("handles value after number") {
        // [42, $value]
        val parts  = Seq("[42, ", "]")
        val result = ContextDetector.detectContexts(parts)
        assertTrue(result == Right(List(InterpolationContext.Value)))
      },
      test("handles value after negative number") {
        // [-3.14, $value]
        val parts  = Seq("[-3.14, ", "]")
        val result = ContextDetector.detectContexts(parts)
        assertTrue(result == Right(List(InterpolationContext.Value)))
      },
      test("handles string containing colon") {
        // {"key": "a:b:$value"}
        val parts  = Seq("{\"key\": \"a:b:", "\"}")
        val result = ContextDetector.detectContexts(parts)
        assertTrue(result == Right(List(InterpolationContext.InString)))
      },
      test("handles string containing braces") {
        // {"key": "{$value}"}
        val parts  = Seq("{\"key\": \"{", "}\"}")
        val result = ContextDetector.detectContexts(parts)
        assertTrue(result == Right(List(InterpolationContext.InString)))
      },
      test("handles string containing brackets") {
        // {"key": "[$value]"}
        val parts  = Seq("{\"key\": \"[", "]\"}")
        val result = ContextDetector.detectContexts(parts)
        assertTrue(result == Right(List(InterpolationContext.InString)))
      }
    ),
    suite("unusual state transitions")(
      test("handles interpolation in ExpectingColon state") {
        // This is an unusual case: {\"key\"$x: value}
        // After the quoted key, we're in ExpectingColon, but interpolation happens
        // The context detection treats this as Value (will fail JSON parsing later)
        val parts  = Seq("{\"key\"", ": 1}")
        val result = ContextDetector.detectContexts(parts)
        // State after parsing "key" is ExpectingColon
        // Interpolation here should return Value (defensive handling)
        assertTrue(result == Right(List(InterpolationContext.Value)))
      },
      test("handles interpolation in AfterValue state") {
        // This is an unusual case: [1$x, 2]
        // After 1, we're in AfterValue, but interpolation happens without comma
        val parts  = Seq("[1", ", 2]")
        val result = ContextDetector.detectContexts(parts)
        // State after parsing "1" is AfterValue
        // Interpolation here should return Value (defensive handling)
        assertTrue(result == Right(List(InterpolationContext.Value)))
      },
      test("handles interpolation after number in object value position") {
        // {"key": 123$x}
        val parts  = Seq("{\"key\": 123", "}")
        val result = ContextDetector.detectContexts(parts)
        // After parsing "123", state is AfterValue
        assertTrue(result == Right(List(InterpolationContext.Value)))
      },
      test("handles interpolation after boolean in array") {
        // [true$x]
        val parts  = Seq("[true", "]")
        val result = ContextDetector.detectContexts(parts)
        // After parsing "true", state is AfterValue
        assertTrue(result == Right(List(InterpolationContext.Value)))
      }
    ),
    suite("expression syntax ${expr}")(
      test("handles ${expr} in value position") {
        // Same as $name - the StringContext doesn't distinguish
        val parts  = Seq("{\"n\": ", "}")
        val result = ContextDetector.detectContexts(parts)
        assertTrue(result == Right(List(InterpolationContext.Value)))
      },
      test("handles ${expr} in string position") {
        // {"n": "${x + 1}"}
        val parts  = Seq("{\"n\": \"", "\"}")
        val result = ContextDetector.detectContexts(parts)
        assertTrue(result == Right(List(InterpolationContext.InString)))
      },
      test("handles ${expr} in key position") {
        // {${x.toString}: 1}
        val parts  = Seq("{", ": 1}")
        val result = ContextDetector.detectContexts(parts)
        assertTrue(result == Right(List(InterpolationContext.Key)))
      }
    ),
    suite("real-world patterns")(
      test("API response pattern") {
        // {"status": $status, "data": $data, "message": "Processed $count items"}
        val parts  = Seq("{\"status\": ", ", \"data\": ", ", \"message\": \"Processed ", " items\"}")
        val result = ContextDetector.detectContexts(parts)
        assertTrue(
          result == Right(
            List(
              InterpolationContext.Value,
              InterpolationContext.Value,
              InterpolationContext.InString
            )
          )
        )
      },
      test("config pattern with dynamic keys") {
        // {$env: {"host": $host, "port": $port}}
        val parts  = Seq("{", ": {\"host\": ", ", \"port\": ", "}}")
        val result = ContextDetector.detectContexts(parts)
        assertTrue(
          result == Right(
            List(
              InterpolationContext.Key,
              InterpolationContext.Value,
              InterpolationContext.Value
            )
          )
        )
      },
      test("log entry pattern") {
        // {"timestamp": $ts, "level": $level, "message": "[$code] $msg at $location"}
        val parts = Seq(
          "{\"timestamp\": ",
          ", \"level\": ",
          ", \"message\": \"[",
          "] ",
          " at ",
          "\"}"
        )
        val result = ContextDetector.detectContexts(parts)
        assertTrue(
          result == Right(
            List(
              InterpolationContext.Value,
              InterpolationContext.Value,
              InterpolationContext.InString,
              InterpolationContext.InString,
              InterpolationContext.InString
            )
          )
        )
      },
      test("path building pattern") {
        // {"path": "/$env/$date/v$version/output.json"}
        val parts  = Seq("{\"path\": \"/", "/", "/v", "/output.json\"}")
        val result = ContextDetector.detectContexts(parts)
        assertTrue(
          result == Right(
            List(
              InterpolationContext.InString,
              InterpolationContext.InString,
              InterpolationContext.InString
            )
          )
        )
      },
      test("array of mixed values") {
        // [$person, $num, $str]
        val parts  = Seq("[", ", ", ", ", "]")
        val result = ContextDetector.detectContexts(parts)
        assertTrue(
          result == Right(
            List(
              InterpolationContext.Value,
              InterpolationContext.Value,
              InterpolationContext.Value
            )
          )
        )
      }
    ),
    suite("top-level string interpolation")(
      test("detects InString in top-level string: \"hello $name\"") {
        val parts  = Seq("\"hello ", "\"")
        val result = ContextDetector.detectContexts(parts)
        assertTrue(result == Right(List(InterpolationContext.InString)))
      },
      test("detects InString at start of top-level string: \"$name says hi\"") {
        val parts  = Seq("\"", " says hi\"")
        val result = ContextDetector.detectContexts(parts)
        assertTrue(result == Right(List(InterpolationContext.InString)))
      },
      test("detects InString at end of top-level string: \"hi $name\"") {
        val parts  = Seq("\"hi ", "\"")
        val result = ContextDetector.detectContexts(parts)
        assertTrue(result == Right(List(InterpolationContext.InString)))
      },
      test("detects multiple InString in top-level string: \"$a and $b\"") {
        val parts  = Seq("\"", " and ", "\"")
        val result = ContextDetector.detectContexts(parts)
        assertTrue(result == Right(List(InterpolationContext.InString, InterpolationContext.InString)))
      },
      test("detects only interpolation in top-level string: \"$name\"") {
        val parts  = Seq("\"", "\"")
        val result = ContextDetector.detectContexts(parts)
        assertTrue(result == Right(List(InterpolationContext.InString)))
      },
      test("detects adjacent InString in top-level string: \"$a$b$c\"") {
        val parts  = Seq("\"", "", "", "\"")
        val result = ContextDetector.detectContexts(parts)
        assertTrue(
          result == Right(
            List(
              InterpolationContext.InString,
              InterpolationContext.InString,
              InterpolationContext.InString
            )
          )
        )
      }
    )
  )
}
