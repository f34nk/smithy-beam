$version: "2"

namespace smithy.beam.demo.documented_types

use aws.protocols#restJson1

@documentation("Root service documentation for the types header.")
@restJson1
service DocumentedTypesService {
    version: "2026"
    operations: [Probe]
}

@documentation("Probe operation to pull shapes into closure.")
@readonly
@http(method: "GET", uri: "/probe/{id}", code: 200)
operation Probe {
    input: ProbeInput
    output: ProbeOutput
    errors: [DocumentedNotFound]
}

structure ProbeInput {
    @required
    @httpLabel
    id: String
}

structure ProbeOutput {
    item: DocumentedItem
    status: DocumentedStatus
    kind: DocumentedKind
    labels: DocumentedLabels
}

@documentation("""
A documented structure with member docs.

Second paragraph preserved.
""")
structure DocumentedItem {
    @documentation("Human-readable item name.")
    @required
    name: String

    @documentation("Optional quantity.")
    quantity: Integer
}

@documentation("String enum with documented variants.")
enum DocumentedStatus {
    ACTIVE
    INACTIVE
}

@documentation("Tagged union carrying documented variants.")
union DocumentedKind {
    text: String
    count: Integer
}

@documentation("Client fault when the item is missing.")
@error("client")
structure DocumentedNotFound {
    message: String
}

@documentation("Named string alias for preamble coverage.")
string DocumentedLabel

list DocumentedLabels {
    member: DocumentedLabel
}
