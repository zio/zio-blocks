# Decisions - markdown-pr-review

## Architectural Choices
- Normalization uses package-level Text (not Inline.Text) as canonical form
- Doc.equals/hashCode based on normalized form for semantic equality
- Metadata merge: right wins on conflict (standard Map.++ semantics)
