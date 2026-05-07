# Changelog
All notable changes to this project will be documented here.

## [Unreleased]

## 2026-05-07

### Added
- `SymbolProvider`: the core of the code generator. It maps every Smithy shape to a `Symbol` carrying the Erlang type name, definition file, and any properties needed during generation.
- `ErlangImports`: does nothing - Erlang `.hrl` files have no import mechanism.
- `ErlangWriter`: extends `SymbolWriter` and adds the factory method required by `WriterDelegator`
- `ErlangIntegration`: every code generator must define its own integration interface. It is the extension point for optional behavior such as custom file generation, model preprocessing, and symbol provider decoration.
- `ErlangContext`: a Java record implementing `CodegenContext`. It is created by `ErlangDirectedCodegen.createContext()` and passed to every generate* method via the directive objects.
- `ErlangDirectedCodegen`: the central interface of the generator. All required methods are present. Methods that do not apply to the types-only baseline are stubs. Methods for scalar/enum/union/structure generation are declared but not yet implemented.
- `ErlangTypeGeneration` is the reusable type-generation entry class for 
Erlang. It owns the `CodegenDirector` wiring for Erlang type output and 
lives in `codegen-core` so standalone types, client, and server plugins 
can all call the same implementation. 
- `ErlangTypesPlugin` is the Smithy-Build adapter discovered through Java SPI.
- Implemented `customizeBeforeShapeGeneration` in `ErlangDirectedCodegen`. This method runs before any `generate*` call and writes the `.hrl` file header plus `-type` aliases for named simple, list, and map shapes in the selected service closure.
- Implement the two enum generators. Both produce a single `-type` line using atom values. An `{unknown, binary()}` (for enum) or `{unknown, integer()}` (for intEnum) catch-all is appended last to keep unknown values representable in the generated type surface.

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
