$version: "2"

namespace smithy.beam.demo.recursive_tree

string RtString

list RtNodeList {
    member: RtNode
}

map RtNodeMap {
    key: RtString
    value: RtNode
}

structure RtNode {
    @required
    label: RtString

    children: RtNodeList
    byKey: RtNodeMap
}

structure RtTreeOutput {
    root: RtNode
}

@readonly
operation GetRecursiveTree {
    output: RtTreeOutput
}

service RecursiveTreeService {
    version: "2026"
    operations: [GetRecursiveTree]
}
