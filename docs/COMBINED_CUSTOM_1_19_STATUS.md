# Combined Custom Morphe Desktop 1.19 Source Candidate

This is an isolated, source-only review and regression-test candidate. It is **not** a downloadable release or a production-ready patching build.

Base: official 1.19.0 plus qualified serial-bound device targeting / receipt / update safety fixes (SHA `ccc991d3e4fd74706dc642f342a64a4a996259cf`).

Contains the following independently reviewed incremental changes:
- Source link normalization and repository-link UI (source branch `26970beb9079ce56ba918b1a79f2b0f5fbcf8927`);
- SHA-256 APK signer certificate verification and fail-closed invalid APK rejection (`cb79721b0a8b823f7cdf837d227cf296ad4e7a55`);
- Strict read-only inspection of single APK input (`a44b87a2e76c84ef9b75ce33c3254c256d6df4a2`);
- Hash-, manifest- and size-verified relinking plus bounded, root-restricted recovery of moved patched APK outputs (`d52a4042947b4b1450ee50ddf58fec487643d06a`).

These engines are not necessarily all connected to the final GUI and installation workflows. The complete private 1.18.1 UX, device-import and update-owner migration require further integration and safety tests. No actual Android target, signing key, release binary or private Git history is present.

CI is read-only, uses public runners, runs `./gradlew test` across Ubuntu/Windows/macOS, and **must not upload artifacts**. No automatic merge or release. Pixel 9 remains read-only.
