# Changelog
All notable changes to this project will be documented here.

## [Unreleased]

## 2026-05-04

### Added
- Gradle multi-module build setup with version catalog, wrapper, and per-module build files.
- `BeamSettings`: shared settings class for deserializing plugin configuration, resolving the
  target service, and deriving module names from the model namespace.
- `BeamNameUtils`: utility for stable, deterministic deconfliction of generated identifiers.
- Unit tests for `BeamSettings` covering service resolution, module name derivation, and all
  accessor round-trips.

### Docs
- Added sequence diagram documenting the code generation flow from plugin setup through artifact writing.

## 2026-04-30

### Docs
- Added architecture overview describing the multi-plugin layout, codegen-core rationale,
  BEAM target rationale, and naming conventions.
- Added shape mapping document describing how every Smithy shape maps to Erlang and Elixir types,
  including naming conventions and unknown-value representation.

### Changed
- Added `.gitignore` to exclude local build artifacts and tooling directories.

## 2026-04-12

- First commit.
