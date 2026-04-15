$version: "2"
namespace example.dynamo

use aws.protocols#awsJson1_0
use aws.protocols#awsJson1_1
use aws.auth#sigv4

@awsJson1_0
@sigv4(name: "dynamodb")
service DynamoService {
    version: "1"
    operations: [GetItem]
}

operation GetItem {
    input: GetItemInput
    output: GetItemOutput
}

@input
structure GetItemInput {
    tableName: String
    key: String
}

@output
structure GetItemOutput {
    item: String
}

@awsJson1_1
@sigv4(name: "lambda")
service LambdaService {
    version: "1"
    operations: [InvokeFunction]
}

operation InvokeFunction {
    input: InvokeFunctionInput
    output: InvokeFunctionOutput
}

@input
structure InvokeFunctionInput {
    functionName: String
    payload: String
}

@output
structure InvokeFunctionOutput {
    statusCode: Integer
}
