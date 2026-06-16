$version: "2"
namespace com.amazonaws.s3

use aws.protocols#restXml
use aws.api#service
use aws.auth#sigv4
use smithy.api#readonly
use smithy.api#xmlName
use smithy.api#httpLabel
use smithy.api#error
use smithy.waiters#waitable

@restXml
@sigv4(name: "s3")
@service(sdkId: "S3", endpointPrefix: "s3")
service AmazonS3 {
    version: "2006-03-01"
    operations: [ListBuckets, HeadBucket]
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
    buckets: BucketList
}

list BucketList {
    member: Bucket
}

// @xmlName("Bucket") on BucketList$member, so items are <Bucket>, not <member>
apply BucketList$member @xmlName("Bucket")

structure Bucket {
    name: String
    creationDate: Timestamp
}

@waitable(
    BucketExists: {
        documentation: "Wait until the bucket exists."
        acceptors: [
            {
                state: "success"
                matcher: {
                    success: true
                }
            }
            {
                state: "retry"
                matcher: {
                    errorType: "NotFound"
                }
            }
        ]
        minDelay: 5
        maxDelay: 120
    }
    BucketNotExists: {
        documentation: "Wait until the bucket does not exist."
        acceptors: [
            {
                state: "success"
                matcher: {
                    errorType: "NotFound"
                }
            }
        ]
        minDelay: 5
        maxDelay: 120
    }
)
@readonly
@http(method: "HEAD", uri: "/{bucket}")
operation HeadBucket {
    input: HeadBucketInput
    output: HeadBucketOutput
    errors: [NotFound]
}

structure HeadBucketInput {
    @httpLabel
    @required
    bucket: String
}

structure HeadBucketOutput {}

@error("client")
structure NotFound {
    message: String
}
