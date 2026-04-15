$version: "2"
namespace example.s3

use aws.protocols#restXml
use aws.auth#sigv4
use aws.api#service

/// Minimal S3-like service for testing aws.protocols#restXml analysis,
/// including requiresS3Runtime detection via arnNamespace.
@restXml
@sigv4(name: "s3")
@service(sdkId: "S3", arnNamespace: "s3", cloudFormationName: "S3", endpointPrefix: "s3")
service S3Service {
    version: "2006-03-01"
    operations: [GetObject, PutObject]
}

/// GET with path labels and query parameter; body is empty.
@readonly
@http(method: "GET", uri: "/bucket/{Bucket}/key/{Key}", code: 200)
operation GetObject {
    input: GetObjectInput
    output: GetObjectOutput
}

@input
structure GetObjectInput {
    @required
    @httpLabel
    Bucket: String

    @required
    @httpLabel
    Key: String

    @httpQuery("versionId")
    VersionId: String
}

@output
structure GetObjectOutput {
    ContentType: String
}

/// PUT with path label and XML body.
@http(method: "PUT", uri: "/bucket/{Bucket}/key/{Key}", code: 200)
operation PutObject {
    input: PutObjectInput
    output: PutObjectOutput
}

@input
structure PutObjectInput {
    @required
    @httpLabel
    Bucket: String

    @required
    @httpLabel
    Key: String

    ContentType: String
    Body: String
}

@output
structure PutObjectOutput {
    ETag: String
}

/// Non-S3 restXml service (arnNamespace does not start with "s3").
@restXml
@sigv4(name: "cloudfront")
@service(sdkId: "CloudFront", arnNamespace: "cloudfront", cloudFormationName: "CloudFront", endpointPrefix: "cloudfront")
service CloudFrontService {
    version: "2020-05-31"
    operations: [ListDistributions]
}

@readonly
@http(method: "GET", uri: "/2020-05-31/distribution", code: 200)
operation ListDistributions {
    input: ListDistributionsInput
    output: ListDistributionsOutput
}

@input
structure ListDistributionsInput {
    @httpQuery("Marker")
    marker: String

    @httpQuery("MaxItems")
    maxItems: Integer
}

@output
structure ListDistributionsOutput {
    nextMarker: String
}
