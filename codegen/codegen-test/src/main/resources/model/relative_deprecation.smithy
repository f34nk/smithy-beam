$version: "2"

namespace smithy.beam.demo.relative_deprecation

string ActiveS

@deprecated(since: "2020-01-01")
string LegacyString

@deprecated(since: "0.5.0")
string LegacyVersionString

structure Out {
    active: ActiveS
    legacy: LegacyString
    legacyVersion: LegacyVersionString
}

@readonly
operation Op {
    output: Out
}

service RelativeDeprecationService {
    version: "2026"
    operations: [Op]
}
