$version: "2"

namespace smithy.beam.demo.resource_lifecycle

@documentation("Service exercising resource lifecycle helper generation.")
service ResourceLifecycleService {
    version: "2026"
    resources: [Organization]
}

@documentation("Top-level organization resource.")
resource Organization {
    identifiers: {
        orgId: String
    }
    read: GetOrganization
    resources: [Employee]
}

@documentation("Employee resource nested under Organization.")
resource Employee {
    identifiers: {
        orgId: String
        employeeId: String
    }
    read: GetEmployee
}

@readonly
operation GetOrganization {
    input: GetOrganizationInput
    output: GetOrganizationOutput
}

structure GetOrganizationInput {
    @required
    orgId: String
}

structure GetOrganizationOutput {
    orgId: String
}

@readonly
operation GetEmployee {
    input: GetEmployeeInput
    output: GetEmployeeOutput
}

structure GetEmployeeInput {
    @required
    orgId: String
    @required
    employeeId: String
}

structure GetEmployeeOutput {
    employeeId: String
}
