# Changelog
All notable changes to this project will be documented here.

## [Unreleased]

## 2026-05-26

### Added
- REST JSON 1 request encoding and response decoding for Erlang and Elixir, with generated codec modules wired into client operation stubs through encode, HTTP dispatch, and decode.
- HTTP dispatch modules for Erlang and Elixir that wrap httpc and Req respectively.
- Error shape generation for Erlang as typed records with kind metadata, and for Elixir as defexception modules.
- Codec and runtime types output paths on the shared Erlang and Elixir layouts, plus snake_case naming support in shared name utilities.

### Changed
- Runtime types header and module path resolution now flows through the layout helpers instead of duplicated helpers in each language pass.

## 2026-05-25

### Added
- Runtime types output for Erlang and Elixir, emitted from the client generation pass alongside the existing type surface.
- Erlang client configuration now carries the configured base URL from plugin settings.

### Fixed
- Elixir runtime types module documentation is emitted inside the defmodule block.
- Timestamp binding formats in generated output follow HttpBindingIndex rather than display offsets.

## 2026-05-24

### Added
- Protocol generation SPI and selection from the resolved service protocol trait, with a small facade over HTTP binding extraction.
- Dedicated REST JSON codec module stubs per service for Erlang and Elixir, invoked from client service and operation generation.
- Protocol context and binding handles on Erlang and Elixir codegen context records, plus integration callbacks for protocol customization.
- REST JSON and HTTP bindings on the basic example model, with a minimal REST JSON service fixture for tests.

### Fixed
- Elixir operation and resource function names are deconflicted across the full service closure.

## 2026-05-20

### Added
- Writer section support for Erlang and Elixir code generation so integrations can intercept stable output regions.
- Integration settings are now forwarded through codegen directors for Erlang and Elixir, with tests proving opt-in integrations can modify generated output.
- Erlang type generation now covers modeled error records, operation input and output aliases, smithy.api Unit, streaming blobs, recursive aggregates, nullable aggregate members, sparse collections, and generated type documentation hooks.
- Elixir type generation now follows the same strict shape coverage expectations as Erlang.

### Changed
- Erlang and Elixir client and server stubs now render through shared module header, dependency, and operation body sections.
- Erlang structure member output follows modeled declaration order and keeps unknown union variants ordered predictably.
- Constraint traits are represented without narrowing the generated Dialyzer type surface.

### Fixed
- Erlang client and server stubs now emit include lines in the correct module header location.
- Generated documentation interceptors now run for Erlang type headers.

### Docs
- Documented integration ordering and opt-in behavior for Erlang and Elixir integrations.

## 2026-05-13

### Added
- Erlang client and server generation now use layout-driven paths and include operation count comments in their stubs.
- Erlang context and type rendering now carry the module and definition metadata needed by later generation passes.

### Changed
- Elixir client and server generation avoid repeated protocol resolution after the types pass has already validated configured protocols.

## 2026-05-12

### Added
- Elixir client and server Smithy-Build plugins with directed codegen, types-first director wiring, SPI registration, and plugin tests.
- The Elixir basic example enables client and server generation in its build config.
- Shared directed-codegen transform wiring in codegen-core, applied consistently on Erlang and Elixir type, client, and server CodegenDirector runs.
- Centralized protocol resolution for plugin settings against service protocol traits, with validation when a protocol is explicitly configured.
- A small service index for TopDown operation discovery, used in generated client and server layout comments for both BEAM targets.
- Unit coverage for the shared transform and settings integration.
- Core layouts for Erlang and Elixir that centralize generated file names, module prefix, and symbol routing; Elixir types, client, and server directed codegen use the Elixir layout end to end, including service, operation, and resource symbols in the symbol provider.
- A shared core enum for symbol-only versus broader codegen mode, and Erlang symbol dependency metadata so closure shapes that rely on standard libraries carry the right symbol references.

### Changed
- Erlang types, client, and server directed codegen use the Erlang layout; the symbol provider returns service-aware symbols for operations and resources, service-relative names for structure and union members, and stronger reserved-word handling split by identifier category.
- Elixir plugin tests and the basic example build copy from the paths implied by the new layout.

### Fixed
- Tests and the multi-service Smithy fixture updated so explicit protocol settings stay valid under the new resolver rules, and relativeVersion values use SemVer form where required.
- Erlang symbol provider and layout tests updated for Smithy validation on affected fixtures.

### Docs
- Added traits documentation, refreshed BeamSettings property notes in Javadoc, and linked the shape mapping guide where helpful.

## 2026-05-10

### Added
- Elixir codegen scaffolding: writer, imports, integration extension point, context record, and directed codegen entry with clear diagnostics for unsupported service, resource, and error generation.
- Registered `ElixirTypeGeneration` and `ElixirTypesPlugin`.
- Type-only Elixir output.
- Reserved-word deconfliction across generated atoms, modules, and fields.
- Integration tests covering the types plugin.
- New `examples/elixir/basic` mirroring the Erlang basic example

### Fixed
- Skip enum and intEnum shapes in the scalar alias emitter so generated modules compile without colliding with their dedicated nested modules.
- Relax member-name indexing to `Collection` so Smithy member collections compile against the symbol provider.

## 2026-05-09

### Added
- Added `ErlangClientDirectedCodegen` and `ErlangServerDirectedCodegen`
- Added `ErlangClientGeneration` and `ErlangServerGeneration`
- Registered Erlang client and server plugins on Java SPI
- The basic example now generates empty Erlang client and server modules alongside the type header

## 2026-05-08

### Added
- Test that Erlang reserved words get escapes in the output to prevent collision.
- Test that type @error is raised
- Test model contains required shapes

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
- Implemented `generateUnion` and `generateStructure`

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
