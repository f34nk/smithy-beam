$version: "2"
namespace example.awsjson11

use aws.protocols#awsJson1_1

/// A smoke-test service for the awsJson1_1 protocol.
@awsJson1_1
service AwsJson11Service {
    version: "2024-01-01"
    operations: [DescribeItem]
}

operation DescribeItem {
    input: DescribeItemInput
    output: DescribeItemOutput
}

structure DescribeItemInput {
    itemId: String
}

structure DescribeItemOutput {
    name: String
    description: String
}
