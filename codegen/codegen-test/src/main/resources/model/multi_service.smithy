$version: "2"

namespace smithy.beam.demo.multi

string S

service ServiceA {
    version: "2026"
    operations: [OpA]
}

service ServiceB {
    version: "2026"
    operations: [OpB]
}

@readonly
operation OpA {
    output: Out
}

@readonly
operation OpB {
    output: Out
}

structure Out {
    v: S
}
