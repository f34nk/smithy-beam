$version: "2"
namespace smithy.beam.test.restxml

use aws.protocols#restXml
use aws.api#service
use smithy.api#httpPayload
use smithy.api#httpLabel
use smithy.api#idempotent
use smithy.api#readonly
use smithy.api#xmlNamespace

@restXml
@xmlNamespace(uri: "http://restxmltest.example/doc/2020-01-01/")
@service(sdkId: "RestXmlTest", endpointPrefix: "restxmltest")
service RestXmlService {
    version: "2020-01-01"
    operations: [CreateBucket, ListBuckets]
}

@idempotent
@http(method: "PUT", uri: "/{Bucket}")
operation CreateBucket {
    input: CreateBucketInput
    output: CreateBucketOutput
}

structure CreateBucketInput {
    @httpLabel
    @required
    Bucket: String

    @httpPayload
    configuration: CreateBucketConfiguration
}

union CreateBucketConfiguration {
    locationConstraint: String
}

structure CreateBucketOutput {}

@readonly
@http(method: "GET", uri: "/")
operation ListBuckets {
    input: ListBucketsInput
    output: ListBucketsOutput
}

structure ListBucketsInput {}

structure ListBucketsOutput {
    owner: Owner
    buckets: BucketList
}

structure Owner {
    displayName: String
    id: String
}

list BucketList {
    member: BucketSummary
}

structure BucketSummary {
    name: String
    creationDate: String
}
