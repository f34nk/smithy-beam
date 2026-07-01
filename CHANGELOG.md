# Changelog
All notable changes to this project will be documented here.

## [Unreleased]

## 2026-07-01

### Added
- Elixir types emission can split large enum and defstruct modules into nested files when size thresholds are exceeded, with layout helpers and settings to control the split.
- Elixir AWS Query codecs emit per-shape flatten helpers for nested structure encoding, and enum decode normalizes underscore wire values before matching known variants.
- Built-in Elixir typespec shadow detection through a shared builtin-types registry, including mfa in the allowed surface.

### Fixed
- Elixir REST-XML client codecs now decode header-bound response fields, normalize list-shaped HTTP header values, and encode enum request headers with wire helpers so large REST-XML services such as S3 run correctly against LocalStack.
- Elixir checksum helpers cover newer algorithms with explicit unsupported stubs and a simpler CRC32 encoding path; event stream encoders use valid capture syntax; default client config merges unsigned payload entries only when needed.
- Single-field defexception modules render on one line; the aws-examples demo runner verifies LocalStack on the bound port before starting and runs fewer jobs in parallel.
- Unknown enum values keep their original wire form after normalization fallback fails; unused struct field pattern variables in encode functions are prefixed to avoid warnings.

### Changed
- EC2 Elixir demo infrastructure is created programmatically for query codec validation; LocalStack test ports map one host port per aws-example target.

## 2026-06-30

### Changed
- Elixir IR output breaks remote calls across lines when argument lists grow long, matching mix-format-style layout for multiline invocations.

## 2026-06-29

### Added
- Completed Elixir codegen migration to structural IR: client, server, router, behaviour, resource, compliance test, and runtime modules now compose through ExModule trees instead of string templates.
- ExTypeDef.mapType for plain map typespecs and shared guard formatting in the Elixir IR layer.

### Fixed
- Elixir IR rendering for indented capture blocks, multiline when clauses, comma placement between expression blocks, and blank lines between type aliases and nested modules.
- Function parameters and heads render on one line where appropriate; aws_credentials typespec emits as a plain map.

## 2026-06-28

### Added
- Elixir protocol codec modules for REST JSON, REST XML, AWS Query, and AWS JSON RPC now emit through dedicated IR composers, with shared codec write helpers and function collectors for module assembly.
- Runtime support modules for HTTP dispatch, credentials, endpoints, SigV4, presigning, retry, waiters, event streams, and S3 endpoints migrated to structural IR emitters.

## 2026-06-27

### Added
- Elixir structural IR foundation in codegen-ir: expression, pattern, guard, clause, function, module, and types-module nodes with golden tests and factory conventions.
- Shared Elixir codec helper IR for enums, host labels, structure and union helpers, and cross-protocol reuse between REST XML and AWS Query.
- Erlang IR migration finished for client, server, waiters, retry, credentials, endpoints, SigV4, and remaining protocol codec operation bodies.

### Fixed
- Empty HTTP binding header lists are omitted from generated request codecs; edoc comment emission restored in Erlang IR output.

## 2026-06-26

### Added
- Golden IR module and header integration tests covering Smithy-driven structure headers and protocol codec modules.

### Changed
- Completed Erlang codegen migration to structural IR; protocol codec modules, client dispatch, event streams, handler discovery, HTTP dispatch, and AWS JSON RPC factories now emit through IR trees instead of ErlangFormat.
- Generated record field type specs use a single space before ::.

### Fixed
- Duplicated terminators in REST codec captured bodies, an extra blank line between generated record and type declarations, and plugin golden parity trailing newlines.

### Removed
- ErlangFormat and the capture escape hatch for raw Erlang fragments.

## 2026-06-24

### Added
- Extended Erlang IR with modules, type headers, record definitions, and remaining expression and pattern forms; structure records render through ErlTypeHeader.
- Routed REST JSON, REST XML, AWS Query, AWS JSON RPC, HTTP dispatch, client dispatch, event streams, and handler discovery through structural IR emitters.

### Fixed
- IR rendering for compact function clause heads, match-case bindings, single-field record updates, and inline fun clause scope in filtermap.

## 2026-06-23

### Added
- codegen-ir module with structural Erlang IR foundation: IrObject contract, expression and pattern nodes, guards, clauses, function specs, and factory helpers for building trees.

## 2026-06-18

### Added
- Optional name setting in BeamSettings to override generated module stems; Erlang and Elixir layouts apply it, and aws-examples rename generated output via the name property.

## 2026-06-16

### Added
- Restored minimal Erlang S3 demo alongside the Elixir demo to exercise generated waiters and presigned URLs in tests.

### Fixed
- REST-XML client codecs decode modeled httpError shapes so retryable errors reach generated retry helpers; minimal S3 demos cover HeadBucket retry behavior.
- Syntax error in Elixir protocol emission.

### Changed
- Removed no-op builtin protocol codegen stub classes in favor of a shared no-op protocol hook; builtin wire codecs remain in the Erlang and Elixir emitters.

## 2026-06-15

### Added
- Generated Erlang and Elixir client operations wrap @retryable flows with with_retry and auto-paginate @paginated operations through shared retry and pagination support in codegen-core.

### Fixed
- Erlang client emission for retry-wrapped operations closes case blocks before the with_retry fun so generated code compiles.
- AWS Erlang demos consume flattened item lists from paginated client calls.
- S3 minimal demo adjustments.

### Changed
- Erlang example projects keep rebar.lock files; Python baseline demo downloads the EC2 model at build time; local model files are gitignored.

### Docs
- Documented model-driven pagination loops in generated client operations.

## 2026-06-14

### Added
- AWS service demos now provision and tear down resources programmatically for LocalStack and live AWS runs.
- LocalStack is started in Docker when running aws-examples.

### Changed
- Removed Docker-specific makefile targets from aws-examples and dropped the standalone minimal Erlang S3 demo.
- Each aws-example binds LocalStack to a distinct host port so several can run without port clashes.

### Fixed
- Codegen and model fixes for REST-XML header decoding, REST JSON timestamp decoding, S3 XML output shape modeling, and waiter matching on nested paths and error types.
- Broader codegen corrections for REST-XML payloads, HTTP checksum validation, union decoding, and AWS Query list encoding.

## 2026-06-13

### Added
- DynamoDB baseline demo for cross-language comparison.
- Vendored aws_endpoint_rules stub emitted for Erlang and Elixir clients.
- customizeProtocolDeserialize integration hook wired on Erlang and Elixir client decode paths.

### Fixed
- Codegen fixes for waiters, error records, empty enums, service closure pruning, paginators, event streams, REST-XML unions, checksum validation, and nested JSON document helpers.
- Syntax error in generated HTTP checksum and REST-XML decoding output.

### Changed
- Removed redundant BeamRuntimeDependency from codegen-core.
- Default baseline build skips Elixir and Python EC2 demos.

## 2026-06-12

### Added
- Erlang client protocol serialize integration hooks wired through the protocol codec emission path.

### Changed
- Renamed aws-example demo application modules for clearer service alignment.

## 2026-06-11

### Added
- Java and Python EC2 baseline demos for generated-client comparison against BEAM output.

### Fixed
- Elixir smithy-build no longer enables automatic Req response body decoding, which interfered with generated codec handling.
- Removed unused dependencies from example projects.

### Changed
- Baseline example Makefiles simplified.

## 2026-06-10

### Added
- Shared Erlang formatting helpers in codegen for consistent four-space layout across emitters.

### Fixed
- Erlang emitter indentation, module structure, union type layout, HTTP dispatch helper placement, retry module formatting, and redundant scalar type alias omission when the alias name matches the built-in Smithy type.

## 2026-06-08

### Added
- Per-service behaviour modules and startup handler discovery for Erlang and Elixir server generation, with routers dispatching operations through behaviour callbacks after init_handlers/0.
- Basic and user server examples now include impl modules and test helpers that wire handler discovery at startup.
- Shared Elixir formatting helpers in codegen for mix-format-style layout conventions such as spec line breaks and pipeline spacing.

### Fixed
- Erlang server behaviour discovery relies on compiler-generated behaviour_info from -callback attributes, with corrected discovery helper layout.
- Elixir HTTP dispatch and server handler discovery generation no longer emit invalid case and end nesting; behaviour module body indentation, runtime types template layout, and REST JSON error response tuple formatting were corrected.

### Changed
- Java and Python examples moved into the baseline layout; the Makefile can run and clean baseline demos.
- Elixir client and server generation now uses two-space indentation and updated emitter layout so basic service output aligns closely with mix format style.

## 2026-06-06

### Added
- Restored optional protocol smithy-build setting for client and server plugins; when unset, protocol derivation from model traits is unchanged.
- Custom protocol registration through BeamProtocolIntegration, with wire-capability helpers and extensible codec module suffixes for non-built-in protocols.
- Minimal custom protocol Erlang example and an EC2 Query LocalStack example.

### Fixed
- Url variable shadowing in generated HTTP dispatch and unbalanced brackets in endpoint rule array literals.

### Docs
- Protocol setting and custom protocol registration documented; trait and AWS SDK support references refreshed for model-driven wire emission and types-never-wire per-plugin behavior.

## 2026-06-03

### Added
- AWS SDK runtime helpers for rules-based endpoint resolution, credential provider chains, presigned URLs, retry metadata from @retryable errors, and waiters from @waitable trait definitions.
- Inline SigV4 signing in generated service modules, gzip request compression, HTTP checksum headers for REST JSON 1 and REST-XML, and opt-in HTTP compliance test emission from model traits.
- AWS Query and EC2 Query server codecs for Erlang and Elixir; Amazon Event Stream framing in generated codecs.
- Service-scoped types header naming derived from the Smithy service name; endpoint rule sets serialized for BEAM runtime evaluation.
- Edition enum to gate breaking generator behavior; minimal S3 demos for Erlang and Elixir.

### Fixed
- Generated syntax and emission fixes across AWS query error decoders, credential providers, S3 endpoint helpers, Elixir REST-XML decode, flattened XML lists, empty-body httpc requests, and runtime helper deduplication per client.

### Changed
- Endpoint rule sets emit only when @endpointRuleSet is present; endpoint rules resolve from the runtime_types macro.
- Removed unused module smithy-build setting and the beam_dependencies.json manifest emission.

## 2026-06-02

### Added
- Elixir AWS JSON 1.0 and 1.1 server codec generation and a static regional endpoint URL builder from aws.api#service metadata.
- Service closure pruning before codegen so shapes outside the selected service are dropped early.

### Fixed
- Url variable clash in Erlang HTTP dispatch and trait definition retention when pruning the service closure.

### Changed
- Erlang codec binding variables centralized on BeamNameUtils.

### Docs
- Clarified that the types plugin generateService hook is intentionally empty; refreshed AWS and trait support matrices.

## 2026-06-01

### Added
- Optional packageVersion smithy-build setting for generated package metadata.

### Docs
- clientOptional and default traits on dedicated operation inputs marked as supported.

## 2026-05-31

### Added
- AWS JSON 1.0, AWS JSON 1.1, AWS Query, EC2 Query, and REST-XML protocol codec generation for Erlang and Elixir, including server-side AWS JSON 1.0 dispatch and XML trait support.
- Generated SigV4 signing hooks, aws.api#service and aws.auth#sigv4 metadata in client config, and endpoint metadata seeding.
- @idempotencyToken auto-fill, @mediaType negotiation, and @streaming blob wire handling in REST JSON 1 codecs.
- -spec annotations on exported Erlang REST JSON 1 codec functions and an Inaka-style CamelCase variable naming helper.

### Fixed
- Inaka-compliant CamelCase variables in Erlang REST JSON codecs, default port omission in Elixir split_base_url generation, and safer header binding variables in Erlang response decoders.

### Docs
- @httpPrefixHeaders marked supported; Erlang shared types header and variable naming conventions documented.

## 2026-05-30

### Added
- @hostLabel and @httpPrefixHeaders support in REST JSON 1 codecs for Erlang and Elixir.
- Service-scoped effective naming in layout helpers so rename-aware module and file names flow through client, server, and codec output.
- Dedicated operation input members treated as optional in Erlang and Elixir type generation.

### Fixed
- Server and codec filenames use rename-aware service names.

### Changed
- Codec module suffixes and layout filenames derive from the resolved protocol trait.

## 2026-05-29

### Added
- Typed error dispatch in REST JSON 1 client decoders, with @httpError status matching before type-discriminated errors and an unknown-error fallback.
- Server response encoding in REST JSON 1 server codecs for Erlang and Elixir, including modeled error responses.
- REST JSON codec support for enum and intEnum round-trips, union helpers, sparse null preservation, timestamp formats, @jsonName wire keys, @httpQueryParams expansion, and @httpResponseCode binding.
- Aggregated unsupported-shape diagnostic when walking the service closure for protocol codegen.
- Erlang string enum types document @enumValue wire mappings in generated comments.
- Service-scoped layout naming for routers, codecs, paginators, runtime HTTP dispatch, and shared runtime helpers.

### Fixed
- Enum codec helpers resolve atom mappings through cached symbol providers.
- Union encode helpers skip absent optional values.
- Erlang server routers decode requests through the server codec module; runtime helpers are emitted with server codec generation.
- Unset timestamps are omitted from Erlang REST JSON request and response bodies.
- Basic REST JSON example no longer models bigDecimal in the service closure.

### Changed
- Erlang and Elixir generated module names follow centralized layout helpers with per-service router and codec modules.
- Error shape test fixtures consolidated into a shared model.

## 2026-05-28

### Added
- Server-side REST JSON for Erlang and Elixir, including per-service runtime helpers for path label parsing, request decoders, HTTP routers that dispatch by method and path, and codec modules emitted from the server generation pass.
- Pagination helpers for @paginated operations on Erlang and Elixir, backed by a shared pagination index wrapper in codegen-core, with a paginated operation on the basic example model.
- Client HTTP dispatch honors a configurable HTTP client module from client configuration on Erlang and Elixir.
- Shared HTTP URI template path segment parser in codegen-core for consistent labeled route matching.
- Types output for Erlang and Elixir now carries Smithy @documentation on structures, enums, unions, error shapes, named scalar aliases, and documented members, using shared helpers in codegen-core for shape and member comment formatting.
- The basic example model documents BasicItem and BasicStatus so generated type comments can be exercised end to end.
- Per-resource lifecycle helper modules on Erlang and Elixir client and server generation for bound create, read, update, delete, list, and collection operations, backed by shared resource discovery and input building in codegen-core.
- Separate client and server example projects for the basic Erlang and Elixir REST JSON setups, plus a user service example with matching client and server layouts.
- Shared Erlang runtime_types header and runtime_helpers module emitted once per generation run instead of duplicating stubs in each service types file.

### Fixed
- Generated routers match labeled path templates instead of treating label segments as literal path text.
- Server request decoders decode only wire-bound fields rather than full input shapes.
- Paginated client helpers preserve item order when accumulating results across pages.
- Resource create helpers pass the full operation input on Erlang and Elixir instead of dropping fields during struct assembly.
- Erlang REST JSON codec document bodies no longer emit a trailing comma in the final map entry.
- Erlang generated server routers use consistent clause indentation.
- Elixir paginator modules are written alongside other generated client artifacts.

### Changed
- Basic Erlang and Elixir examples are split into dedicated client and server projects; the previous combined basic layouts were removed.
- Unused baseUrl plugin setting and default base URL client emission were dropped from codegen.

### Docs
- Trait support tables and shape mapping notes now describe @documentation on types output alongside operation stubs.
- Added AWS SDK support status reference for generated BEAM output.
- Trait support and shape mapping now cover resource lifecycle helpers and resource-oriented generated output.
- AWS SDK support status notes for client default endpoints were refreshed.

## 2026-05-27

### Added
- Shared BeamDocumentation helper in codegen-core extracts Smithy @documentation trait text for generated comments.
- Erlang and Elixir client and server operation stubs emit documentation from the trait on each operation.
- The basic example model documents GetTypeClosure with multiline markdown so generated docs can be exercised end to end.

### Changed
- Documentation formatting dedents triple-quoted Smithy text, and preserves line breaks in Elixir @doc heredocs and Erlang edoc continuation lines instead of collapsing to a single line. Allows HTML or markdown.

## 2026-05-26

### Added
- REST JSON 1 request encoding and response decoding for Erlang and Elixir, with generated codec modules wired into client operation stubs through encode, HTTP dispatch, and decode.
- HTTP dispatch modules for Erlang and Elixir that wrap httpc and Req, with injectable client modules so tests can substitute mocks without network calls.
- Error shape generation for Erlang as typed records with kind metadata, and for Elixir as defexception modules.
- Codec and runtime types output paths on the shared Erlang and Elixir layouts, plus snake_case naming support in shared name utilities.
- HTTP dispatch and REST JSON 1 codec tests for the basic Erlang and Elixir examples.
- OTP application descriptor for the Erlang basic example.

### Fixed
- REST JSON 1 codec output uses valid Erlang binding variables and safer JSON body decoding.

### Changed
- Runtime types header and module path resolution now flows through the layout helpers instead of duplicated helpers in each language pass.
- Erlang basic example build compiles generated sources directly.

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
