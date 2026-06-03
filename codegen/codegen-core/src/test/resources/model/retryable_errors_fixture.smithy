$version: "2"

namespace smithy.beam.test.retry

use smithy.api#error
use smithy.api#String
use smithy.api#retryable

@error("server")
@retryable
structure RetryableError {
    message: String
}

@error("server")
@retryable(throttling: true)
structure ThrottlingError {
    message: String
}

@error("client")
structure PlainError {
    message: String
}

service RetryFixtureService {
    version: "2026"
    operations: [Ping]
}

operation Ping {
    input: Unit
    output: Unit
    errors: [RetryableError, ThrottlingError, PlainError]
}

structure Unit {}
