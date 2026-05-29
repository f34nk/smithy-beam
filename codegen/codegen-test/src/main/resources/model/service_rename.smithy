$version: "2"
namespace smithy.beam.test.rename

use aws.protocols#restJson1

@restJson1
service RenameService {
    version: "2026"
    rename: {
        "smithy.beam.test.shared#Widget": "RenamedWidget"
    }
    operations: [GetWidget]
}

@http(method: "GET", uri: "/widgets/{id}")
@readonly
operation GetWidget {
    input: GetWidgetInput
    output: GetWidgetOutput
}

structure GetWidgetInput {
    @required @httpLabel
    id: String
}

structure GetWidgetOutput {
    item: smithy.beam.test.shared#Widget
}
