$version: "2"

namespace smithy.beam.demo.error_shape

use smithy.api#error
use smithy.api#String
use smithy.api#retryable
use smithy.api#httpError

@error("server")
@retryable
@httpError(503)
structure ServiceUnavailable {
    message: String
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
