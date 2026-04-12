$version: "2.0"

// A simple storage API demonstrating union types
//
// This example shows how Smithy unions are represented as tagged tuples in Erlang

namespace com.example.storage

use aws.protocols#restJson1

/// A simple storage service with multiple storage backends
@restJson1
service StorageService {
    version: "2024-01-01"
    operations: [
        CreateStorageLocation
        GetStorageLocation
    ]
}

/// Create a new storage location
@http(method: "POST", uri: "/storage")
operation CreateStorageLocation {
    input: CreateStorageLocationInput
    output: CreateStorageLocationOutput
}

/// Get storage location details
@readonly
@http(method: "GET", uri: "/storage/{locationId}")
operation GetStorageLocation {
    input: GetStorageLocationInput
    output: GetStorageLocationOutput
    errors: [StorageLocationNotFound]
}

// Input/Output structures

structure CreateStorageLocationInput {
    @required
    name: String
    
    @required
    storageType: StorageType
}

structure CreateStorageLocationOutput {
    @required
    locationId: String
    
    @required
    storageType: StorageType
}

structure GetStorageLocationInput {
    @required
    @httpLabel
    locationId: String
}

structure GetStorageLocationOutput {
    @required
    locationId: String
    
    @required
    name: String
    
    @required
    storageType: StorageType
}

// Union types - the key feature we're testing

/// Storage type can be S3, Glacier, or EFS
union StorageType {
    s3: S3Storage
    glacier: GlacierStorage
    efs: EfsStorage
}

/// S3 storage configuration
structure S3Storage {
    @required
    bucket: String
    
    @required
    region: String
    
    prefix: String
}

/// Glacier storage configuration
structure GlacierStorage {
    @required
    vault: String
    
    @required
    region: String
    
    retrievalOption: GlacierRetrievalOption
}

/// EFS storage configuration
structure EfsStorage {
    @required
    fileSystemId: String
    
    @required
    region: String
    
    mountPath: String
}

/// Glacier retrieval options
enum GlacierRetrievalOption {
    EXPEDITED = "expedited"
    STANDARD = "standard"
    BULK = "bulk"
}

// Error types

@error("client")
@httpError(404)
structure StorageLocationNotFound {
    @required
    message: String
    
    locationId: String
}
