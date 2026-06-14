$version: "2"

namespace smithy.beam.test.nestedunion

use aws.protocols#awsJson1_1
use aws.api#service

@awsJson1_1
@service(sdkId: "NestedUnion", endpointPrefix: "nestedunion")
service NestedUnionService {
    version: "2026"
    operations: [UpdateWidget]
}

operation UpdateWidget {
    input: UpdateWidgetInput
    output: UpdateWidgetOutput
}

structure UpdateWidgetInput {
    expected: ExpectedWidgetValue
}

structure ExpectedWidgetValue {
    value: WidgetValue
}

union WidgetValue {
    s: String
    n: Integer
}

structure UpdateWidgetOutput {
    id: String
}
