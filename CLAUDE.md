# Project-Specific Claude Instructions

## Addressing Review Comments

When asked to address review comments on pull requests, follow this workflow:

1. **Evaluate Soundness**: First determine if the review comment is valid and makes sense
   - Check technical correctness
   - Verify the concern is legitimate
   - Consider the project context and conventions

2. **Address the Comment**: If the comment is sound:
   - Implement the suggested change or fix
   - Ensure the change compiles/builds correctly
   - Verify the fix resolves the issue raised

3. **Write Resolution Comment**: Post a GitHub comment explaining:
   - What the issue was
   - What was changed to fix it
   - Why this fix is correct
   - Verification that the fix works

4. **Resolve on GitHub**:
   - Add a reply directly to the review comment on GitHub
   - Use checkmark (✅) to indicate resolution
   - Reference the commit that addressed the fix
   - Keep comments clear and concise

### Example Flow
```
1. Read review comment → Is it valid? → YES
2. Implement fix in code → Test → Verify
3. Commit changes with clear message
4. Post resolution comment with verification details
5. Push to remote
```

## Additional Notes

- If a review comment is NOT sound, explain why it doesn't apply in a respectful manner
- Batch related fixes into logical commits (one commit per related group of changes)
- Always verify code compiles before claiming resolution
- Include verification output (test results, build logs) when relevant
