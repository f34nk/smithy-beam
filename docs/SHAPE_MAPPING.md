# Smithy Shape to BEAM Language Type Mapping

## Design Decisions

The generator follows the DirectedCodegen structure and service-closure rules,
with the following BEAM-specific exceptions called out explicitly.

### BEAM-Specific Exceptions to the DirectedCodegen Shape Guidance

- Named scalar, list, and map shapes generate type aliases as an explicit
  BEAM-specific exception to the [DirectedCodegen guide's](https://smithy.io/2.0/guides/building-codegen/implementing-the-generator.html) recommendation that 
  these shape names are irrelevant.
  Reason: Erlang and Elixir users rely on [Dialyzer](https://www.erlang.org/doc/apps/dialyzer/dialyzer_chapter.html) / [Dialyxir](https://hexdocs.pm/dialyxir/readme.html) type surfaces and
  readable generated docs, while Smithy scalar, list, and map aliases still have
  no runtime identity in BEAM. The exception is narrow: aliases exist only in
  generated type files as a documentation and Dialyzer/Dialyxir surface. They do
  not create runtime wrappers, constructors, validators, serialization behavior,
  or new value semantics.
- All types for a model file land in one output file derived from the model
  namespace's last segment (e.g. `smithy.beam.demo.basic` -> `basic_types`).
  Reason: the BEAM convention keeps related type specs together.
  [Erlang records](https://www.erlang.org/doc/reference_manual/records.html) and [named types](https://www.erlang.org/doc/reference_manual/typespec.html) are idiomatically shared through one header, and
  [Elixir typespecs](https://hexdocs.pm/elixir/typespecs.html) are easier to consume as one top-level module with nested modules
  for structures and enums. This intentionally avoids a file-per-type or file-per-enum
  layout for the type generator.
- [Prelude shapes](https://smithy.io/2.0/spec/model.html#prelude) (`smithy.api#String`, `smithy.api#Integer`, etc.) and unaliased
  target references MUST still render directly as target-environment built-in types
  such as `binary()`, `String.t()`, `integer()`, lists, and maps.
- Type rendering MUST use central helpers that distinguish built-in symbols
  from named generated aliases using [symbol properties](https://smithy.io/2.0/guides/building-codegen/implementing-the-generator.html#symbol-providers), not ad hoc 
  string rewrites.
- Scalar, list, and map alias declarations are written in [customizeBeforeShapeGeneration](https://smithy.io/2.0/guides/building-codegen/implementing-the-generator.html#implementing-directedcodegen),
  but only for shapes in the selected service closure computed with [Walker](https://smithy.io/2.0/guides/building-codegen/implementing-the-generator.html#directedcodegen) from
  `directive.model()` and `directive.service()`.
- For DirectedCodegen to call `generateStructure`, `generateEnumShape`, etc.,
  shapes MUST be reachable from the service via Walker.
  Manual alias generation MUST follow the same service closure rule and never
  emit same-namespace unreachable shapes. The test model includes a dummy
  operation whose output structure references all basic types.
- Enum/intEnum unknown variants MUST be represented in the generated type
  surface. Full serialization and deserialization round-trip behavior belongs
  to future protocol serializers and deserializers.
- Union unknown variants MUST be represented in the generated type surface.
  Full protocol round-trip behavior is reserved for the protocol implementation.
- [Timestamp](https://smithy.io/2.0/spec/simple-types.html#timestamp) mappings represent Smithy instants at the generated type surface only. 
  The initial type-only generator does not choose or implement a wire timestamp format.
  Protocol serializers and deserializers must later prove that timestamp
  encoding preserves instant semantics independent of protocol format.
- Smithy [@error](https://smithy.io/2.0/spec/type-refinement-traits.html#error-trait) structures are outside the initial type-only implementation. 
  They MUST NOT be silently omitted. Until error/exception type generation is implemented,
  reachable error shapes MUST fail code generation with a clear diagnostic that
  names the shape and explains the scope limit.
- Plugin settings follow the [HOWTO](https://smithy.io/2.0/guides/building-codegen/implementing-the-generator.html) shape for type generation. 
  `edition` is required. `service` may be omitted only when the model contains 
  exactly one service. `protocol`, `relativeDate`, and `relativeVersion` are parsed 
  and retained but are no-ops in the initial type-only implementation; they become
  behavioral when protocol-aware serialization or deprecation filtering is added (TODO).
- [Erlang](https://www.erlang.org/doc/reference_manual/introduction.html#reserved-words) and [Elixir](https://hexdocs.pm/elixir/syntax-reference.html#reserved-words) reserved words MUST be escaped automatically in the initial generator. 
  The generator must never reject a Smithy model only because a shape, member, enum
  member, union member, module, or generated function name conflicts with an
  Erlang or Elixir reserved word.
- Escaped identifiers MUST be deconflicted deterministically after escaping.
  If two model identifiers map to the same generated identifier, append stable
  numeric suffixes such as `_2`, `_3` within the relevant scope.

## Erlang Mappings

| Smithy shape        | Erlang type                                  |
|---------------------|----------------------------------------------|
| [blob](https://smithy.io/2.0/spec/simple-types.html#blob) | `binary()` |
| [boolean](https://smithy.io/2.0/spec/simple-types.html#boolean) | `boolean()` |
| [string](https://smithy.io/2.0/spec/simple-types.html#string) | `binary()` |
| [byte](https://smithy.io/2.0/spec/simple-types.html#byte) | `integer()` |
| [short](https://smithy.io/2.0/spec/simple-types.html#short) | `integer()` |
| [integer](https://smithy.io/2.0/spec/simple-types.html#integer) | `integer()` |
| [long](https://smithy.io/2.0/spec/simple-types.html#long) | `integer()` |
| [float](https://smithy.io/2.0/spec/simple-types.html#float) | `float()` |
| [double](https://smithy.io/2.0/spec/simple-types.html#double) | `float()` |
| [bigInteger](https://smithy.io/2.0/spec/simple-types.html#biginteger) | `integer()` |
| [bigDecimal](https://smithy.io/2.0/spec/simple-types.html#bigdecimal) | `term()` (comment: decimal:decimal()) |
| [timestamp](https://smithy.io/2.0/spec/simple-types.html#timestamp) | `erlang:timestamp()` |
| [document](https://smithy.io/2.0/spec/simple-types.html#document) | `term()` |
| [enum](https://smithy.io/2.0/spec/simple-types.html#enum) | `atom1 \| atom2 \| {unknown, binary()}` |
| [intEnum](https://smithy.io/2.0/spec/simple-types.html#intenum) | `atom1 \| atom2 \| {unknown, integer()}` |
| [list](https://smithy.io/2.0/spec/aggregate-types.html#list) | `[member_type()]` |
| [map](https://smithy.io/2.0/spec/aggregate-types.html#map) | `#{key_type() => value_type()}` |
| [union](https://smithy.io/2.0/spec/aggregate-types.html#union) | `{tag1, t1()} \| ... \| {unknown, binary()}` |
| [structure](https://smithy.io/2.0/spec/aggregate-types.html#structure) | `-record(name, {...}). -type name() :: #name{}` |

Timestamp note: `erlang:timestamp()` is a provisional BEAM type-file
representation for a Smithy instant. It is not a Smithy wire-format decision
and does not by itself describe timestamp serialization. Protocol serializers
must later document and test conversion between Smithy timestamp formats and
this Erlang representation while preserving instant semantics.

### Naming Convention (Erlang)

Shape names are converted from UpperCamelCase to snake_case:
- `BasicString` -> `basic_string`
- `BasicBigDecimal` -> `basic_big_decimal`

Enum member names are converted from SCREAMING_SNAKE to lowercase atoms:
- `ACTIVE` -> `active`
- `LOW` -> `low`

All generated Erlang identifiers pass through separate reserved-word escapers:
- type and record names
- record field names
- enum atoms
- union tags
- future generated function names

## Elixir Mappings

| Smithy shape        | Elixir type                                           |
|---------------------|-------------------------------------------------------|
| [blob](https://smithy.io/2.0/spec/simple-types.html#blob) | `binary()` |
| [boolean](https://smithy.io/2.0/spec/simple-types.html#boolean) | `boolean()` |
| [string](https://smithy.io/2.0/spec/simple-types.html#string) | `String.t()` |
| [byte](https://smithy.io/2.0/spec/simple-types.html#byte) | `integer()` |
| [short](https://smithy.io/2.0/spec/simple-types.html#short) | `integer()` |
| [integer](https://smithy.io/2.0/spec/simple-types.html#integer) | `integer()` |
| [long](https://smithy.io/2.0/spec/simple-types.html#long) | `integer()` |
| [float](https://smithy.io/2.0/spec/simple-types.html#float) | `float()` |
| [double](https://smithy.io/2.0/spec/simple-types.html#double) | `float()` |
| [bigInteger](https://smithy.io/2.0/spec/simple-types.html#biginteger) | `integer()` |
| [bigDecimal](https://smithy.io/2.0/spec/simple-types.html#bigdecimal) | `Decimal.t()` |
| [timestamp](https://smithy.io/2.0/spec/simple-types.html#timestamp) | `DateTime.t()` |
| [document](https://smithy.io/2.0/spec/simple-types.html#document) | `any()` |
| [enum](https://smithy.io/2.0/spec/simple-types.html#enum) | nested defmodule with `@type t :: :a \| {:unknown, String.t()}` |
| [intEnum](https://smithy.io/2.0/spec/simple-types.html#intenum) | nested defmodule with `@type t :: :a \| {:unknown, integer()}` |
| [list](https://smithy.io/2.0/spec/aggregate-types.html#list) | `[member_type()]` |
| [map](https://smithy.io/2.0/spec/aggregate-types.html#map) | `%{key_type() => value_type()}` |
| [union](https://smithy.io/2.0/spec/aggregate-types.html#union) | `{:tag1, t1()} \| ... \| {:unknown, String.t()}` |
| [structure](https://smithy.io/2.0/spec/aggregate-types.html#structure) | nested defmodule with `@type t :: %__MODULE__{...}` and `defstruct` |

Timestamp note: `DateTime.t()` is used as the Elixir type-surface
representation for a Smithy instant. Protocol serializers must later normalize
and encode/decode it according to the selected Smithy protocol timestamp format
without treating local offset or display format as part of the model semantics.

All types are placed inside a top-level `defmodule <ModuleName> do ... end`.
Enums and structures generate nested defmodules inside the top-level module.

### Naming Convention (Elixir)

Scalar type aliases use snake_case with module prefix where needed:
- Shape `BasicString` -> `@type basic_string :: String.t()`

Nested module names use the shape name as-is (already UpperCamelCase):
- Shape `BasicStatus` -> `defmodule BasicStatus do`

Member references inside types use the outer module prefix:
- `BasicTypes.basic_string()` for cross-module references

All generated Elixir identifiers pass through separate reserved-word escapers:
- top-level and nested module names
- type alias names
- struct fields
- enum atoms
- union tags
- generated function names
