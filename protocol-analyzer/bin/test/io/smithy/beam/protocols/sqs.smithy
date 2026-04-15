$version: "2"
namespace example.sqs

use aws.protocols#awsQuery
use aws.auth#sigv4

/// Minimal SQS-like service for testing aws.protocols#awsQuery analysis.
@awsQuery
@sigv4(name: "sqs")
@xmlNamespace(uri: "https://sqs.amazonaws.com/doc/2012-11-05/")
service SqsService {
    version: "2012-11-05"
    operations: [SendMessage]
}

operation SendMessage {
    input: SendMessageInput
    output: SendMessageOutput
    errors: [InvalidMessageContents]
}

@input
structure SendMessageInput {
    @required
    QueueUrl: String

    @required
    MessageBody: String

    DelaySeconds: Integer
}

@output
structure SendMessageOutput {
    MessageId: String
    MD5OfMessageBody: String
}

@error("client")
@httpError(400)
structure InvalidMessageContents {
    message: String
}
