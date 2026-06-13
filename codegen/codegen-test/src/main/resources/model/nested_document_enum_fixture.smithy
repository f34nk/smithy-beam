$version: "2"

namespace smithy.beam.test.nestedenum

use aws.protocols#awsJson1_1
use aws.api#service

@awsJson1_1
@service(sdkId: "NestedEnum", endpointPrefix: "nestedenum")
service NestedEnumService {
    version: "2026"
    operations: [CreateWidget]
}

operation CreateWidget {
    input: CreateWidgetInput
    output: CreateWidgetOutput
}

structure CreateWidgetInput {
    kind: WidgetKind
}

enum WidgetKind {
    ALPHA
    BETA
}

structure CreateWidgetOutput {
    id: String
}
