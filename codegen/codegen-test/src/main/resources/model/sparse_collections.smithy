$version: "2"

namespace smithy.beam.demo.sparse_collections

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

structure SparseCollectionsOutput {
    items: ScSparseList
    counts: ScSparseMap
}

@readonly
operation GetSparseCollections {
    output: SparseCollectionsOutput
}

service SparseCollectionsService {
    version: "2026"
    operations: [GetSparseCollections]
}
