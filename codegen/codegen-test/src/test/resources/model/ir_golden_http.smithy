$version: "2"
namespace smithy.beam.demo.http

use aws.protocols#restJson1

string Name

@restJson1
service HttpService {
    version: "2026"
    operations: [GetName]
}

@readonly
@http(method: "GET", uri: "/names/{name}", code: 200)
operation GetName {
    input: GetNameInput
    output: GetNameOutput
}

structure GetNameInput {
    @required
    @httpLabel
    name: Name
}

structure GetNameOutput {
    name: Name
}
