$version: "2"

namespace smithy.beam.test.compression

use aws.protocols#restJson1
use smithy.api#http
use smithy.api#requestCompression

@restJson1
service RequestCompressionRestJsonService {
    version: "2026"
    operations: [PutCompressed, GetPlain]
}

@requestCompression(encodings: ["gzip"])
@http(method: "PUT", uri: "/items")
operation PutCompressed {
    input: PutCompressedInput
    output: Unit
}

structure PutCompressedInput {
    data: String
}

@http(method: "GET", uri: "/plain")
operation GetPlain {
    input: GetPlainInput
    output: Unit
}

structure GetPlainInput {}

structure Unit {}
