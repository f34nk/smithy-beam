$version: "2"

namespace smithy.beam.test.nesteddoc

use aws.protocols#awsJson1_1
use aws.api#service

@awsJson1_1
@service(sdkId: "NestedDoc", endpointPrefix: "nesteddoc")
service NestedDocService {
    version: "2026"
    operations: [StartExecution]
}

operation StartExecution {
    input: StartExecutionInput
    output: StartExecutionOutput
}

structure StartExecutionInput {
    inputs: ExecutionInputList
}

list ExecutionInputList {
    member: ExecutionInput
}

structure ExecutionInput {
    name: String
}

structure StartExecutionOutput {
    id: String
}
