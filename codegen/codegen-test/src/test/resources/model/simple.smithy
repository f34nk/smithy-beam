$version: "2"
namespace example.simple

/// A minimal service with one operation and no AWS traits.
service SimpleService {
    version: "2024-01-01"
    operations: [GetItem]
}

operation GetItem {
    input: GetItemInput
    output: GetItemOutput
}

structure GetItemInput {
    id: String
}

structure GetItemOutput {
    name: String
    value: String
}
