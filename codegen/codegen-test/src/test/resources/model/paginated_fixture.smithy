$version: "2"
namespace smithy.beam.test.paginated

use aws.protocols#restJson1
use smithy.api#httpQuery

@restJson1
service PaginatedService {
    version: "2026"
    operations: [ListWidgets, ListNestedWidgets]
}

@readonly
@http(method: "GET", uri: "/widgets")
@paginated(
    inputToken: "next_token"
    outputToken: "next_token"
    pageSize: "max_results"
    items: "widgets"
)
operation ListWidgets {
    input: ListWidgetsInput
    output: ListWidgetsOutput
    errors: [WidgetError]
}

structure ListWidgetsInput {
    @httpQuery("next_token")
    next_token: String

    @httpQuery("max_results")
    max_results: Integer
}

structure ListWidgetsOutput {
    next_token: String
    widgets: WidgetList
}

list WidgetList {
    member: Widget
}

structure Widget {
    id: String
}

@error("client")
structure WidgetError {
    message: String
}

@readonly
@http(method: "GET", uri: "/nested-widgets")
@paginated(inputToken: "nextToken", outputToken: "nextToken", items: "result.items")
operation ListNestedWidgets {
    input: ListNestedWidgetsInput
    output: ListNestedWidgetsOutput
}

structure ListNestedWidgetsInput {
    @httpQuery("nextToken")
    nextToken: String
}

structure ListNestedWidgetsOutput {
    nextToken: String
    result: NestedWidgetResult
}

structure NestedWidgetResult {
    items: WidgetList
}
