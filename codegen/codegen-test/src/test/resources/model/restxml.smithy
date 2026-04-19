$version: "2"
namespace example.restxml

use aws.protocols#restXml

/// A smoke-test service for the restXml protocol.
@restXml
service RestXmlService {
    version: "2024-01-01"
    operations: [ListBuckets]
}

operation ListBuckets {
    input: ListBucketsInput
    output: ListBucketsOutput
}

structure ListBucketsInput {
    prefix: String
}

structure ListBucketsOutput {
    buckets: BucketList
}

list BucketList {
    member: String
}
