$version: "2"

namespace smithy.beam.demo.resource_lifecycle

use aws.protocols#restJson1

@documentation("Service exercising resource lifecycle helper generation.")
@restJson1
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
    list: ListOrganizations
    create: CreateOrganization
    delete: DeleteOrganization
    resources: [Employee]
}

@documentation("Employee resource nested under Organization.")
resource Employee {
    identifiers: {
        orgId: String
        employeeId: String
    }
    read: GetEmployee
    collectionOperations: [ListEmployeesByStatus]
}

@readonly
@http(method: "GET", uri: "/orgs/{orgId}", code: 200)
operation GetOrganization {
    input: GetOrganizationInput
    output: GetOrganizationOutput
}

structure GetOrganizationInput {
    @required
    @httpLabel
    orgId: String
}

structure GetOrganizationOutput {
    orgId: String
}

@readonly
@http(method: "GET", uri: "/orgs", code: 200)
operation ListOrganizations {
    input: ListOrganizationsInput
    output: ListOrganizationsOutput
}

structure ListOrganizationsInput {}
structure ListOrganizationsOutput {
    orgIds: OrgIdList
}

list OrgIdList {
    member: String
}

@idempotent
@http(method: "POST", uri: "/orgs", code: 200)
operation CreateOrganization {
    input: CreateOrganizationInput
    output: CreateOrganizationOutput
}

structure CreateOrganizationInput {
    displayName: String
}

structure CreateOrganizationOutput {
    orgId: String
}

@idempotent
@http(method: "DELETE", uri: "/orgs/{orgId}", code: 204)
operation DeleteOrganization {
    input: DeleteOrganizationInput
    output: DeleteOrganizationOutput
}

structure DeleteOrganizationInput {
    @required
    @httpLabel
    orgId: String
}

structure DeleteOrganizationOutput {}

@readonly
@http(method: "GET", uri: "/orgs/{orgId}/employees/{employeeId}", code: 200)
operation GetEmployee {
    input: GetEmployeeInput
    output: GetEmployeeOutput
}

structure GetEmployeeInput {
    @required
    @httpLabel
    orgId: String
    @required
    @httpLabel
    employeeId: String
}

structure GetEmployeeOutput {
    employeeId: String
}

@readonly
@http(method: "GET", uri: "/orgs/{orgId}/employees", code: 200)
operation ListEmployeesByStatus {
    input: ListEmployeesByStatusInput
    output: ListEmployeesByStatusOutput
}

structure ListEmployeesByStatusInput {
    @required
    @httpLabel
    orgId: String
    @httpQuery("status")
    status: String
}

structure ListEmployeesByStatusOutput {
    employeeIds: EmployeeIdList
}

list EmployeeIdList {
    member: String
}
