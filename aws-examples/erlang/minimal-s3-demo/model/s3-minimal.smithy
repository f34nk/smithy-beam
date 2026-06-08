$version: "2"
namespace com.amazonaws.s3

use aws.protocols#restXml
use aws.api#service
use aws.auth#sigv4
use smithy.api#readonly
use smithy.api#xmlName
use smithy.api#xmlFlattened

@restXml
@sigv4(name: "s3")
@service(sdkId: "S3", endpointPrefix: "s3")
service AmazonS3 {
    version: "2006-03-01"
    operations: [ListBuckets]
}

@readonly
@http(method: "GET", uri: "/")
operation ListBuckets {
    input: ListBucketsInput
    output: ListBucketsOutput
}

structure ListBucketsInput {}

@xmlName("ListAllMyBucketsResult")
structure ListBucketsOutput {
    @xmlFlattened
    buckets: BucketList
}

list BucketList {
    member: Bucket
}

structure Bucket {
    name: String
    creationDate: Timestamp
}
