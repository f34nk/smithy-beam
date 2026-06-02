$version: "2"

namespace smithy.beam.demo.protocoljson

use aws.protocols#restJson1

@restJson1
service DemoRestJson {
    version: "2026-01-01"
    operations: [DescribeItem, CreateItem]
}

@readonly
@http(method: "GET", uri: "/items/{id}", code: 200)
operation DescribeItem {
    input: DescribeItemInput
    output: DescribeItemOutput
    errors: [NotFoundError]
}

structure DescribeItemInput {
    @required
    @httpLabel
    id: ItemId

    @httpQuery("verbose")
    verbose: Boolean

    @httpQuery("status")
    status: ItemStatus

    @httpHeader("X-Request-Tag")
    requestTag: String
}

structure DescribeItemOutput {
    @httpHeader("ETag")
    etag: String

    item: ItemPayload
}

string ItemId

structure ItemPayload {
    @required
    name: String

    createdAt: Timestamp

    status: ItemStatus

    meta: Document
}

enum ItemStatus {
    READY
    ARCHIVED
}

@http(method: "POST", uri: "/items", code: 201)
operation CreateItem {
    input: CreateItemInput
    output: CreateItemOutput
}

structure CreateItemInput {
    @required
    name: String

    tags: TagList
}

structure CreateItemOutput {
    @httpHeader("Location")
    location: String

    @required
    id: ItemId
}

list TagList {
    member: String
}

@error("client")
@httpError(404)
structure NotFoundError {
    @required
    message: String
}
