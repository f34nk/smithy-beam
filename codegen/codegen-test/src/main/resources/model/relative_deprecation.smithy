$version: "2"

namespace smithy.beam.demo.relative_deprecation

string ActiveS

@deprecated(since: "2020-01-01")
string LegacyString

structure Out {
    active: ActiveS
    legacy: LegacyString
}

@readonly
operation Op {
    output: Out
}

service RelativeDeprecationService {
    version: "2026"
    operations: [Op]
}
