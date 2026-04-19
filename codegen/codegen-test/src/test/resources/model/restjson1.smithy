$version: "2"
namespace example.restjson1

use aws.protocols#restJson1

/// A smoke-test service for the restJson1 protocol.
@restJson1
service RestJson1Service {
    version: "2024-01-01"
    operations: [EchoMessage]
}

operation EchoMessage {
    input: EchoMessageInput
    output: EchoMessageOutput
}

structure EchoMessageInput {
    message: String
}

structure EchoMessageOutput {
    message: String
}
