$version: "2"

namespace smithy.beam.demo.user

use aws.protocols#restJson1
use smithy.api#String

@documentation("User management API with full resource lifecycle.")
@restJson1
service UserService {
    version: "2026"
    resources: [User]
}

@documentation("A user account.")
resource User {
    identifiers: {
        userId: String
    }
    create: CreateUser
    read: GetUser
    update: UpdateUser
    delete: DeleteUser
    list: ListUsers
}

@documentation("User account data.")
structure UserData {
    @required
    userId: String

    @required
    email: String

    displayName: String
}

list UserList {
    member: UserData
}

@http(method: "POST", uri: "/users", code: 201)
operation CreateUser {
    input: CreateUserInput
    output: CreateUserOutput
}

structure CreateUserInput {
    @required
    email: String

    displayName: String
}

structure CreateUserOutput {
    user: UserData
}

@readonly
@http(method: "GET", uri: "/users/{userId}", code: 200)
operation GetUser {
    input: GetUserInput
    output: GetUserOutput
}

structure GetUserInput {
    @required
    @httpLabel
    userId: String
}

structure GetUserOutput {
    user: UserData
}

@idempotent
@http(method: "PUT", uri: "/users/{userId}", code: 200)
operation UpdateUser {
    input: UpdateUserInput
    output: UpdateUserOutput
}

structure UpdateUserInput {
    @required
    @httpLabel
    userId: String

    email: String

    displayName: String
}

structure UpdateUserOutput {
    user: UserData
}

@idempotent
@http(method: "DELETE", uri: "/users/{userId}", code: 204)
operation DeleteUser {
    input: DeleteUserInput
    output: DeleteUserOutput
}

structure DeleteUserInput {
    @required
    @httpLabel
    userId: String
}

structure DeleteUserOutput {}

@readonly
@http(method: "GET", uri: "/users", code: 200)
operation ListUsers {
    input: ListUsersInput
    output: ListUsersOutput
}

structure ListUsersInput {}

structure ListUsersOutput {
    users: UserList
}
