$version: "2"
namespace smithy.beam.test

use aws.protocols#restJson1

@restJson1
service TestService {
    version: "2026"
    operations: [GetItem]
}

operation GetItem {
    input: GetItemInput
    output: GetItemOutput
}

@input
structure GetItemInput {
    @required
    id: String
}

structure GetItemOutput {
    id: String
}
