# Attribution and Acknowledgments

## TOON Format Implementation

This implementation of the TOON (Text-Oriented Object Notation) format for ZIO Schema 2
is based on the official TOON Format Specification v3.0 and incorporates patterns and
test cases from Apache 2.0 licensed open source projects.

## Acknowledgments

### TOON Format Specification
- **Source**: [TOON Format Specification](https://github.com/toon-format/spec)
- **Version**: v3.0
- **License**: Apache License 2.0
- **Usage**: Specification compliance, test fixtures, and format examples

### toon4s Library
- **Source**: [toon4s on GitHub](https://github.com/vim89/toon4s)
- **License**: Apache License 2.0
- **Usage**: Test patterns, codec design inspiration, and array format examples
- **Credit**: Test data structures and encoding patterns adapted from toon4s

## Test Fixtures

The following official TOON specification fixtures inform our test suite:
- `primitives.json` - Basic primitive type encoding tests
- `objects.json` - Record and nested object tests
- `arrays-primitive.json` - Inline primitive array tests
- `arrays-tabular.json` - Tabular array format tests
- `arrays-nested.json` - Complex nested array tests

All fixtures are transcribed into Scala test cases in `ToonSpecComplianceTests.scala`
to ensure specification compliance without runtime JSON parsing dependencies.

## License Compliance

This implementation is released under the Apache License 2.0, compatible with all
acknowledged sources. Full license text available in the repository root LICENSE file.

### Apache License 2.0 Notice

```
Copyright 2026 ZIO Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

## Contributing

When contributing to this implementation, please ensure:
1. All code additions maintain TOON v3.0 specification compliance
2. Test coverage includes exact match assertions for encoded output
3. Array-focused test cases demonstrate TOON's optimization features
4. Proper attribution is maintained for any external references
