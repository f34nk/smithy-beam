$version: "2"

namespace smithy.beam.demo.errors

use smithy.api#error
use smithy.api#String

@error("client")
structure NotImplementedYet {
    message: String
}

structure Empty {}

service ErrorDemoService {
    version: "2026"
    operations: [MayFail]
}

@readonly
operation MayFail {
    input: Empty
    output: Empty
    errors: [NotImplementedYet]
}
