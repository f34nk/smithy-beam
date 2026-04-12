# Changelog

All notable changes to **smithy-beam** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Planned

- Core IR types, protocol analyzers, and Erlang / Elixir writers.

---

## [0.1.0] — 2026-04-12

### Added

- Gradle multi-project build (`codegen-core`, `codegen-protocols`, `codegen-erlang`, `codegen-elixir`) with Java 17, JUnit 5, AssertJ, and `publishToMavenLocal` via `maven-publish`.
- Version catalog for Smithy (`1.54.0`), Smithy AWS traits, and test libraries.
- Gradle wrapper (8.5) and GitHub Actions CI workflow.
- Source tree placeholders: `runtime-erlang`, `runtime-elixir`, and `examples` (Erlang and Elixir demo slots).
- Documentation: `ARCHITECTURE.md` (layout and target pipeline), `CHANGELOG.md`, `AWS_SDK_SUPPORT.md` and `TRAITS.md` (roadmap matrices aligned with 0.1.0 scaffold—no shipped codegen yet).
