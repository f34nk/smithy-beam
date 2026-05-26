$version: "2"

namespace smithy.beam.demo.errors

use aws.protocols#restJson1

@error("client")
@retryable
structure NotFoundError {
    message: String
}

@error("client")
structure ValidationError {
    message: String
    fieldName: String
}

@error("server")
@retryable(throttling: true)
structure ThrottlingError {
    message: String
    retryAfterSeconds: Integer
}

@restJson1
service ErrorFixtureService {
    version: "2026"
    errors: [ThrottlingError]
    operations: [GetItem]
}

@http(method: "GET", uri: "/items/{id}", code: 200)
@readonly
operation GetItem {
    input: GetItemInput
    output: GetItemOutput
    errors: [NotFoundError, ValidationError]
}

structure GetItemInput {
    @required
    @httpLabel
    id: String
}

structure GetItemOutput {
    id: String
    name: String
}
