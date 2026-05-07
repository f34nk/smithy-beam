$version: "2"

namespace smithy.beam.demo.basic

// Minimal service so Erlang type codegen can run CodegenDirector with a service scope.

service BasicService {
    version: "2026"
    operations: []
}

// Simple shapes — one named type per Smithy prelude scalar.

string BasicString
integer BasicInteger
long BasicLong
float BasicFloat
boolean BasicBoolean
blob BasicBlob
byte BasicByte
short BasicShort
double BasicDouble
bigDecimal BasicBigDecimal
bigInteger BasicBigInteger
timestamp BasicTimestamp
document BasicDocument

// ─── Enum ───────────────────────────────────────────────────────────────────
//
// String-keyed enum. Unknown wire values MUST round-trip without failure.

enum BasicStatus {
    ACTIVE
    INACTIVE
    PENDING
}

// ─── IntEnum ────────────────────────────────────────────────────────────────
//
// Integer-keyed enum. Unknown wire values MUST round-trip without failure.

intEnum BasicPriority {
    LOW = 1
    MEDIUM = 2
    HIGH = 3
}

// ─── List ───────────────────────────────────────────────────────────────────

list BasicList {
    member: BasicString
}

// ─── Map ────────────────────────────────────────────────────────────────────

map BasicMap {
    key: BasicString
    value: BasicString
}

// ─── Union ──────────────────────────────────────────────────────────────────
//
// Exactly one member is active. Unknown variants MUST be preserved.

union BasicUnion {
    text: BasicString
    number: BasicInteger
    flag: BasicBoolean
}

// ─── Structure ──────────────────────────────────────────────────────────────
//
// Required members: typed without nil.
// Optional members: typed | nil  (NullableIndex rule).

structure BasicItem {
    @required
    name: BasicString

    count: BasicInteger
}
