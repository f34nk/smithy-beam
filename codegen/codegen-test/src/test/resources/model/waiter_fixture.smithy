$version: "2"
namespace smithy.beam.test.waiters

use aws.protocols#restJson1
use smithy.waiters#waitable

@restJson1
service WaitableService {
    version: "2026"
    operations: [HeadBucket]
}

@waitable(
    BucketExists: {
        documentation: "Wait until a bucket exists"
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
    }
)
@readonly
@http(method: "HEAD", uri: "/{Bucket}")
operation HeadBucket {
    input: HeadBucketInput
    output: HeadBucketOutput
    errors: [NotFound]
}

structure HeadBucketInput {
    @httpLabel
    @required
    Bucket: String
}

structure HeadBucketOutput {}

@error("client")
structure NotFound {
    message: String
}
