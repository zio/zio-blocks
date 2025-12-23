## Test Report

- **Scala version**: 3.3.7 (schemaJVM)
- **Total active tests**: **819**
- **Result**: **All active tests passed (819/819)**.

### Disabled / Known Limitations

- **Structural types (Selectable)**  
  - Tests for:
    - case class to structural type
    - case class with different field types to structural type
    - structural type to case class
    - structural type to structural type with same methods
    - structural type to structural type with subset of methods
  - **Status**: Disabled on Scala 3 due to SIP-44 architectural limitation (see `docs/STRUCTURAL_TYPES_LIMITATION.md`).  
  - **Notes**: The feature now uses AST-based generation of an anonymous `Selectable` wrapper with hard-coded dispatch. However, integration with Scala 3's `DefaultSelectable` at runtime still prevents fully generic usage in tests, so these scenarios remain excluded from the Scala 3 test matrix while remaining documented and supported within the architectural constraints.


