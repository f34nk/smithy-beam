
## *WORK IN PROGRESS*

# smithy-beam

Code generator for the [Smithy](https://smithy.io/) interface modelling language. 

Support for **BEAM languages**: Erlang, Elixir, Gleam.

- **Contract-first** API development
- Generates **client** and **server** code from the *same* Smithy service model
- Produces idiomatic, standardized modules, type definitions, and HTTP request/response handling for service operations
- **Protocol agnostic**: supports many [AWS protocols](https://smithy.io/2.0/aws/protocols/index.html)
- **Idempotent builds**: integrate with your business logic using behaviours  
- Support for [Smithy Interface Definition Language (IDL)](https://smithy.io/2.0/spec/idl.html) and [JSON AST](https://smithy.io/2.0/spec/json-ast.html)

> Erlang and Elixir client and server generators are fully supported.
> (Gleam is coming soon)

## Documentation

- [ARCHITECTURE](https://github.com/f34nk/smithy-beam/blob/v1/ARCHITECTURE.md) **smithy-beam** codegen documentation
- [TRAITS](https://github.com/f34nk/smithy-beam/blob/v1/TRAITS.md) list of supported Smithy traits
- [AWS_SDK_SUPPORT](https://github.com/f34nk/smithy-beam/blob/v1/AWS_SDK_SUPPORT.md) list of supported AWS-oriented features

## Build and Test

Prerequisites:
- Java 11+
- Gradle 7+
- [Smithy CLI](https://smithy.io/2.0/guides/smithy-cli/cli_installation.html)
- Erlang/OTP 24+
- rebar3 3+
- Elixir 1+

```shell
make build
make test
```

## Example

Any of the [examples](https://github.com/f34nk/smithy-beam/tree/v1/examples/erlang) can be run like this:

```shell
make examples/erlang/dynamodb-demo
```

Or run all examples in parallel:
```shell
make examples
```
