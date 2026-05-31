$version: "2"
namespace smithy.beam.test.awsjson10

use aws.protocols#awsJson1_0
use aws.api#service

@awsJson1_0
@service(sdkId: "Json10", endpointPrefix: "json10")
service Json10Service {
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
