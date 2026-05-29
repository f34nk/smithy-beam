$version: "2"

namespace smithy.beam.demo.sparse_collections

use aws.protocols#restJson1

string ScString
integer ScInteger

@sparse
list ScSparseList {
    member: ScString
}

@sparse
map ScSparseMap {
    key: ScString
    value: ScInteger
}

list ScList {
    member: ScString
}

structure SparseCollectionsOutput {
    items: ScSparseList
    counts: ScSparseMap
    tags: ScList
}

@readonly
@http(method: "GET", uri: "/sparse-collections", code: 200)
operation GetSparseCollections {
    output: SparseCollectionsOutput
}

service SparseCollectionsService {
    version: "2026"
    operations: [GetSparseCollections]
}

@restJson1
service SparseCollectionsRestJson {
    version: "2026"
    operations: [GetSparseCollections]
}
