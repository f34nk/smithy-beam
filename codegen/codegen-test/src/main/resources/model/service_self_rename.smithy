$version: "2"
namespace smithy.beam.test

use aws.protocols#restJson1

@restJson1
service OriginalName {
    version: "2026"
    rename: {
        "smithy.beam.test#OriginalName": "RenamedService"
    }
    operations: [Ping]
}

@http(method: "GET", uri: "/ping", code: 200)
operation Ping {
    input: PingInput
    output: PingOutput
}

structure PingInput {}
structure PingOutput {}
