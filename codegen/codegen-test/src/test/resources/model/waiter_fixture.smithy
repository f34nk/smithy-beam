$version: "2"
namespace smithy.beam.test.waiters

use aws.protocols#restJson1
use smithy.waiters#waitable

@restJson1
service WaitableService {
    version: "2026"
    operations: [HeadBucket, DescribeTable]
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

@waitable(
    TableExists: {
        acceptors: [
            {
                state: "success"
                matcher: {
                    output: {
                        path: "Table.TableStatus"
                        expected: "ACTIVE"
                        comparator: "stringEquals"
                    }
                }
            }
        ]
    }
)
@readonly
@http(method: "GET", uri: "/tables/{TableName}")
operation DescribeTable {
    input: DescribeTableInput
    output: DescribeTableOutput
}

structure DescribeTableInput {
    @httpLabel
    @required
    TableName: String
}

structure DescribeTableOutput {
    Table: TableDescription
}

structure TableDescription {
    TableStatus: String
}

@error("client")
structure NotFound {
    message: String
}
