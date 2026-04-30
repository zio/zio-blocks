# Evaluation 6: Scope Persistence & Reset Behavior

## Prompt
"I defined a helper function in one mdoc:silent block. Three blocks later, I want to use it. Will it still be in scope? What if I used silent:reset in between?"

## Skill Response Summary
The skill correctly explained:
1. Helper function **IS** in scope 3 blocks later (scope is cumulative)
2. Explanation of why (no reset between blocks = continuous scope)
3. If you used `mdoc:silent:reset`, scope would be wiped
4. Visual diagram showing scope persistence

## Evaluation Results

### Criteria Assessment
- ✅ **Answers first question correctly** — Helper function IS in scope 3 blocks later
- ✅ **Explains why** — Cumulative scope, no reset = definitions persist
- ✅ **Explains reset behavior** — `mdoc:silent:reset` wipes all prior scope
- ✅ **Provides example** — Scope before/after reset shown clearly

**Result: PASS (4/4 criteria met)**

## Key Technical Accuracy
- Correctly explains cumulative scope behavior
- Accurately describes what `:reset` does
- Mentions the caveat about cross-section scope (if sections are separated by prose/headers, behavior may differ)
- Recommends testing locally with `sbt docs`

## Strength of Explanation
The skill used a visual table showing scope state at each block, which makes the concept clear:
```
Block A (silent) → defines helper
Block B (silent) → helper in scope
Block C (mdoc)   → helper in scope
Block D (reset)  → scope wiped
Block E (mdoc)   → helper NOT in scope
```

## Overall Assessment
**Excellent explanation.** This addresses a real source of confusion for documentation writers, and the skill explains it clearly with supporting diagrams and examples.
