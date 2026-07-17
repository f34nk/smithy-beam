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
        host: "example.com"
        resolvedHost: "prefix.example.com"
        headers: {
            "X-Test": "1"
        }
        queryParams: [
            "filter=active"
        ]
        forbidHeaders: [
            "X-Forbidden"
        ]
        requireHeaders: [
            "X-Required"
        ]
        bodyMediaType: "application/json"
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
        body: """
            {
                "name": "widget"
            }
            """
        bodyMediaType: "application/json"
        forbidHeaders: [
            "X-Forbidden"
        ]
        requireHeaders: [
            "X-Required"
        ]
    }
    {
        id: "GetItemResponseEncode"
        protocol: restJson1
        appliesTo: "server"
        params: {
            name: "widget"
        }
        code: 200
        headers: {
            "Content-Type": "application/json"
        }
        body: """
            {
                "name": "widget"
            }
            """
        bodyMediaType: "application/json"
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
