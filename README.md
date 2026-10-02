## *Available on [Maven Central](https://central.sonatype.com/search?q=io.github.f34nk.smithy.beam) and [Hex](https://hex.pm/packages/smithy_beam)*

![smithy-beam](https://github.com/f34nk/smithy-beam/blob/v3/smithy-beam.png)

# smithy-beam

Code generator for the [Smithy](https://smithy.io/) interface modelling language. 

Support for **BEAM languages**: Erlang, Elixir, Gleam*.

> fully [extensible](https://smithy.io/2.0/guides/building-codegen/making-codegen-pluggable.html) with the toolchain (follows the **official** [Creating a Code Generator](https://smithy.io/2.0/guides/building-codegen/index.html) guidelines)

**Gleam support will follow :)*

*[Hex](https://hex.pm) package lives in-repo at [`hex/smithy_beam`](hex/smithy_beam)*

## Build and Test

Prerequisites:
- Java 21+
- Gradle 8+
- [Smithy CLI](https://smithy.io/2.0/guides/smithy-cli/cli_installation.html)
- Erlang/OTP 24+
- rebar3 3+
- Elixir 1+

```shell
make build
make test
```

## Examples

```shell
make examples
make aws-examples
```

# How to generate an Elixir client

Check out [Maven Central](https://central.sonatype.com/search?q=io.github.f34nk.smithy.beam) for the latest version.

Assuming you have a model [basic.smithy](https://github.com/f34nk/smithy-beam/blob/v3/examples/model/basic.smithy).

Create a `smithy-build.json`.

```json
{
  "version": "1.0",
  "sources": [
    "basic.smithy"
  ],
  "maven": {
    "dependencies": [
      "io.github.f34nk.smithy.beam:codegen-elixir:0.3.0",
      "software.amazon.smithy:smithy-aws-traits:1.54.0"
    ],
    "repositories": [
      {
        "url": "https://repo1.maven.org/maven2"
      }
    ]
  },
  "plugins": {
    "elixir-client-codegen": {
      "service": "smithy.beam.demo.basic#BasicService",
      "edition": "2026"
    }
  }
}
```

Generate the code with

```shell
smithy build
```

The generated code is in `build/smithy/source/elixir-client-codegen`.

```
├── build
│   └── smithy
│       ├── classpath.json
│       └── source
│           ├── build-info
│           │   └── smithy-build-info.json
│           ├── elixir-client-codegen
│           │   ├── basic_service_client.ex
│           │   ├── basic_service_rest_json_1.ex
│           │   ├── basic_service_types.ex
│           │   ├── runtime_http_client.ex
│           │   ├── runtime_http.ex
│           │   ├── runtime_types.ex
│           │   └── runtime_utils.ex
│           ├── model
│           │   └── model.json
│           └── sources
│               ├── basic.smithy
│               └── manifest
├── Makefile
├── basic.smithy
└── smithy-build.json
```

All the `basic_service_*` files are generated. The name is defined by the model namespace.
Additionally, the codegen also adds `runtime_` files, which are essentially re-usable modules used internally.
