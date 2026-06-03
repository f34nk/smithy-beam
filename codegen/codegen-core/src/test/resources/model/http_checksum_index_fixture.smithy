$version: "2"

namespace smithy.beam.test.checksum

use aws.protocols#httpChecksum
use smithy.api#httpPayload
use smithy.api#httpHeader

@httpChecksum(requestChecksumRequired: true)
operation RequiredChecksum {
    input: RequiredChecksumInput
    output: Unit
}

structure RequiredChecksumInput {
    @httpPayload
    content: Blob
}

@httpChecksum(
    requestAlgorithmMember: "checksumAlgorithm",
    requestValidationModeMember: "validationMode",
    responseAlgorithms: ["CRC32C", "SHA256"]
)
operation FlexibleChecksum {
    input: FlexibleChecksumInput
    output: FlexibleChecksumOutput
}

structure FlexibleChecksumInput {
    @httpHeader("x-amz-sdk-checksum-algorithm")
    checksumAlgorithm: ChecksumAlgorithm

    @httpHeader("x-amz-request-validation-mode")
    validationMode: ValidationMode

    @httpPayload
    content: Blob
}

enum ChecksumAlgorithm {
    CRC32C
    SHA256
}

enum ValidationMode {
    ENABLED
}

structure FlexibleChecksumOutput {
    @httpPayload
    content: Blob
}

structure Unit {}
