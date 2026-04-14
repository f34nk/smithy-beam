# smithy-beam

Code generator for the [Smithy](https://smithy.io/) interface modelling language. 

Targeting **BEAM languages**: Erlang, Elixir, Gleam.

- **Contract-first** API development
- Generates **client** and **server** code from the *same* Smithy service models. 
- Produces idiomatic, standardized client and server modules, type definitions, and HTTP request/response handling for service operations
- **Protocol agnostic**: supports many [AWS protocols](https://smithy.io/2.0/aws/protocols/index.html)
- **Idempotent builds**: integrate your business logic with behaviours  

> Erlang client and server generators are fully supported.
> (Elixir and Gleam is coming soon)

See [awesome-smithy](https://github.com/smithy-lang/awesome-smithy?tab=readme-ov-file#client-code-generators) for other languages.

## Documentation

- [ARCHITECTURE](https://github.com/f34nk/smithy-beam/blob/v1/ARCHITECTURE.md) **smithy-beam** codegen documentation
- [TRAITS](https://github.com/f34nk/smithy-beam/blob/v1/TRAITS.md) list of supported Smithy traits
- [AWS_SDK_SUPPORT](https://github.com/f34nk/smithy-beam/blob/v1/AWS_SDK_SUPPORT.md) list of supported AWS-oriented features

## Build and Test

Prerequisites:
- Java 11+
- Gradle 7.0+
- Smithy CLI
- Erlang/OTP 24+
- rebar3
- Elixir

Build:
```shell
make build
```

Test:
```shell
make test
make examples
```

Any of the [examples](https://github.com/f34nk/smithy-beam/tree/v1/examples/erlang) can be run like this:

```shell
make examples/erlang/dynamodb-demo
```
