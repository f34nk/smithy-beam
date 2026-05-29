$version: "2"

namespace smithy.beam.demo.error_shapes

use smithy.api#error
use smithy.api#String
use smithy.api#retryable
use smithy.api#httpError
use aws.protocols#restJson1

@error("server")
@retryable
@httpError(503)
structure ServiceUnavailable {
    message: String
}

@error("client")
@retryable
@httpError(404)
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

structure Empty {}

service ErrorShapeService {
    version: "2026"
    operations: [Call]
}

@readonly
operation Call {
    input: Empty
    output: Empty
    errors: [ServiceUnavailable]
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
