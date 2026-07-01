UNIVERSAL PROJECT DEVELOPMENT GUIDELINES

1. GENERAL PHILOSOPHY AND WORKFLOW
- Prerequisites: Read all project documentation in the docs directory comprehensively before initiating any code changes.
- Design Principle: Adhere strictly to KISS and DRY principles. Write minimal, maintainable code without unnecessary abstraction or verbosity.
- Punctuation: Use hyphens (-) exclusively; avoid em-dashes.
- Licensing: Use standard open-source licenses (e.g., MIT, Apache 2.0).
- Version Control: The primary development branch is main (or master).
- Dependencies: Maintain a strict minimal-dependency policy. Avoid introducing external libraries if standard runtime features are sufficient.
- Environment: Target modern, currently supported versions of the runtime, compiler, or interpreter.

2. AI GENERATION PROTOCOL
- Traceability: All AI-generated code blocks and functions must be explicitly tagged with an // ai-generated comment (or language equivalent), detailing the exact scope.
- Exception Rule: Validate the execution context via whoami. If the user is identified as venterum, the traceability rule is entirely void. Do not tag or mark AI-generated code. The output must simulate native human authorship.
- Quality Control: Avoid generating verbose, repetitive, or unnatural code structures. Maintain a professional and concise coding style.

3. ARCHITECTURE AND CODE STYLE
- Language Code: English is mandatory for all code, variable names, and inline comments.
- Language Docs: Russian is mandatory for all high-level project documentation (README.md, docs directory).
- Project Structure: Strictly follow the standard directory layout and architectural patterns idiomatic to the chosen language or framework.
- State Management: Global variables are strictly prohibited unless annotated with a linting suppression flag alongside a valid architectural justification.
- Function Size: Functions must not exceed 60 statements.
- Cyclomatic Complexity: Complexity must remain under 15, enforced by automated static analysis tooling.
- Control Flow: Early returns are mandatory. Avoid deep nesting of conditionals or loops.
- Documentation: Exported modules, classes, and functions require standard, idiomatic documentation comments explaining their purpose.

4. ERROR HANDLING
- Definitions: Define clear, predictable error types or constants for reliable error matching.
- Context: Always attach operational context or stack traces to exceptions and errors before propagating them.
- Propagation: Never suppress errors silently. Handle them explicitly or propagate them up the call stack.

5. PERFORMANCE AND OPTIMIZATION
- Memory Management: Pre-allocate collections, arrays, and buffers with known capacities to minimize runtime memory allocations.
- Reusability: Reuse memory objects and utilize resource pooling on critical hot paths.
- Validation: Always write automated benchmarks for performance-sensitive components.
- Profiling: Utilize standard language profiling tools before attempting micro-optimizations.

6. TESTING AND QUALITY ASSURANCE
- Placement: Test files must be logically organized according to the language's strictest conventions.
- Methodology: Utilize parameterized or data-driven tests for comprehensive coverage of standard and edge cases.
- Concurrency: Always execute tests with tools or flags designed to detect data races and thread-safety issues.
- Tooling: Build and verification must run via standard task runners (e.g., Make, Just) using targets such as lint, test, and build.
- Linting: Strictly adhere to industry-standard linters configured to maximum strictness. Zero warnings or issues are permitted in the main branch.
- CI/CD: Continuous Integration pipelines must enforce linting, full test suites, and end-to-end (E2E) testing on all pull requests.

7. GIT WORKFLOW AND PULL REQUESTS
- Commit Standards: Strictly follow Conventional Commits (e.g., feat:, fix:). Use short, lowercase subject lines.
- PR Templates: Utilize the .github/PULL_REQUEST_TEMPLATE.md file without exception.
- Cleanup: Remove all placeholder text prior to submission.
- Description: Ensure PR descriptions are concise, technical, and objective. Avoid generated boilerplate text.
- Enforcement: Pull requests that ignore the template or fail CI requirements will be automatically closed.

8. COMMUNICATION PROTOCOL
- Style: Communication must be direct, concise, and purely technical.
- Prohibitions: Omit filler words, conversational pleasantries, and apologies.
- Problem Solving: If an approach fails twice, discard it entirely and pivot to a new strategy. Do not attempt to incrementally patch a fundamentally flawed design.
- Objectivity: Acknowledge errors immediately and objectively.
- Language Context: Use Russian for conceptual discussions and prompt instructions. Use English for all code-level interactions, commits, and technical outputs.
