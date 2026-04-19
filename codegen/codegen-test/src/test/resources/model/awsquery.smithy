$version: "2"
namespace example.awsquery

use aws.protocols#awsQuery

/// A smoke-test service for the awsQuery protocol.
@awsQuery
service AwsQueryService {
    version: "2024-01-01"
    operations: [SendCommand]
}

operation SendCommand {
    input: SendCommandInput
    output: SendCommandOutput
}

structure SendCommandInput {
    action: String
    version: String
}

structure SendCommandOutput {
    requestId: String
}
