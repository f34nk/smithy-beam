# Changelog

All notable changes to **smithy-beam** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed

- User-facing documentation (`ARCHITECTURE.md`, `AWS_SDK_SUPPORT.md`, `TRAITS.md`): scope blurbs and support-matrix notes describe generated output and current behavior without internal development references.

## [0.1.0] — 2026-04-12

### Added

- Gradle multi-project build (`codegen-core`, `codegen-protocols`, `codegen-erlang`, `codegen-elixir`) with Java 17, JUnit 5, AssertJ, and `publishToMavenLocal` via `maven-publish`.
- Version catalog for Smithy (`1.54.0`), Smithy AWS traits, and test libraries.
- Gradle wrapper (8.5) and GitHub Actions CI workflow.
- Source tree placeholders under `runtime-erlang`, `runtime-elixir`, and `examples` for future runtimes and demos.
- **Core IR** (`io.smithy.beam.core.ir`): primitive/binding enums, `TypeRef`, operation records (`OperationSpec`, HTTP/bindings, errors, auth, retry, pagination), and type records (`StructSpec`, `EnumSpec`, `UnionSpec`, `ModuleTypeSpec`, etc.).
- **Interfaces** (`io.smithy.beam.core.writer`, `io.smithy.beam.core.protocol`): `LanguageWriter`, `ProtocolAnalyzer`, supporting value types (`ExportSpec`, `MapEntrySpec`, `ParamSpec`), and `ProtocolAnalyzerFactory` for registering analyzers by protocol `ShapeId`.
- **Infrastructure** (`io.smithy.beam.core`): `CodegenException`; `CodegenSettings`; `CodeBuffer` and `FileOutput`; `UriTemplate`, `ShapeIndex`, `TypeSpecBuilder`, `ProtocolDetector`.
- Unit test: `TypeSpecBuilderTest` for `ModuleTypeSpec` assembly from a minimal Smithy model.
- Public documentation: `ARCHITECTURE.md`, `AWS_SDK_SUPPORT.md`, `TRAITS.md` (support matrices for AWS and traits vs. generated output).
