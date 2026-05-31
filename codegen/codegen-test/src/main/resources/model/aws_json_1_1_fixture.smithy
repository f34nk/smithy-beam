$version: "2"
namespace smithy.beam.test.awsjson11

use aws.protocols#awsJson1_1
use aws.api#service

@awsJson1_1
@service(sdkId: "Json11", endpointPrefix: "json11")
service Json11Service {
    version: "2026"
    operations: [GetUser]
}

operation GetUser {
    input: GetUserInput
    output: GetUserOutput
}

structure GetUserInput {
    userName: String
}

structure GetUserOutput {
    userName: String
}
