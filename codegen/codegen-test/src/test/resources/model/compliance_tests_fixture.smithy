$version: "2"

namespace smithy.beam.test.compliance

use aws.protocols#restJson1
use smithy.test#httpRequestTests
use smithy.test#httpResponseTests

@restJson1
service ComplianceService {
    version: "2026"
    operations: [GetItem]
}

@http(method: "GET", uri: "/items/{id}")
@httpRequestTests([
    {
        id: "GetItemRequest"
        protocol: restJson1
        appliesTo: "client"
        params: {
            id: "abc"
        }
        method: "GET"
        uri: "/items/abc"
        headers: {
            "X-Test": "1"
        }
    }
])
@httpResponseTests([
    {
        id: "GetItemResponse"
        protocol: restJson1
        appliesTo: "client"
        params: {
            name: "widget"
        }
        code: 200
        headers: {
            "Content-Type": "application/json"
        }
        body: "{\"name\": \"widget\"}"
    }
])
operation GetItem {
    input: GetItemInput
    output: GetItemOutput
}

structure GetItemInput {
    @httpLabel
    @required
    id: String
}

structure GetItemOutput {
    name: String
}
