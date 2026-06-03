$version: "2"

namespace smithy.beam.test.checksum

use aws.protocols#restJson1
use aws.protocols#restXml
use aws.protocols#httpChecksum
use smithy.api#http
use smithy.api#httpPayload
use smithy.api#httpHeader
use smithy.api#httpLabel

@restJson1
service HttpChecksumRestJsonService {
    version: "2026"
    operations: [PutRequiredChecksum, PutFlexibleChecksum]
}

@httpChecksum(requestChecksumRequired: true)
@http(method: "PUT", uri: "/required")
operation PutRequiredChecksum {
    input: PutRequiredChecksumInput
    output: Unit
}

structure PutRequiredChecksumInput {
    @httpPayload
    content: Blob
}

@httpChecksum(
    requestAlgorithmMember: "checksumAlgorithm",
    requestValidationModeMember: "validationMode",
    responseAlgorithms: ["CRC32C", "SHA256"]
)
@http(method: "PUT", uri: "/flexible")
operation PutFlexibleChecksum {
    input: PutFlexibleChecksumInput
    output: PutFlexibleChecksumOutput
}

structure PutFlexibleChecksumInput {
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

structure PutFlexibleChecksumOutput {
    @httpPayload
    content: Blob
}

structure Unit {}

@restXml
service HttpChecksumRestXmlService {
    version: "2026"
    operations: [PutObject]
}

@httpChecksum(requestChecksumRequired: true)
@http(method: "PUT", uri: "/{Bucket}/{Key}")
operation PutObject {
    input: PutObjectInput
    output: Unit
}

structure PutObjectInput {
    @httpLabel
    @required
    Bucket: String

    @httpLabel
    @required
    Key: String

    @httpPayload
    body: Blob
}
