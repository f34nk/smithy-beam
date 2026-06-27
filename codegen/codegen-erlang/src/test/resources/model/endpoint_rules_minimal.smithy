$version: "2.0"

namespace smithy.beam.test.endpoints

use aws.api#service
use smithy.rules#clientContextParams
use smithy.rules#endpointRuleSet

@endpointRuleSet({
    version: "1.0"
    parameters: {
        Region: {
            type: "String"
            documentation: "The AWS region, for example us-east-1."
        }
        Bucket: {
            type: "String"
            documentation: "The S3 bucket name."
        }
    }
    rules: [
        {
            conditions: [
                {
                    fn: "isSet"
                    argv: [
                        { ref: "Region" }
                    ]
                }
                {
                    fn: "isSet"
                    argv: [
                        { ref: "Bucket" }
                    ]
                }
            ]
            endpoint: {
                url: "https://{Bucket}.s3.{Region}.amazonaws.com"
                properties: {}
                headers: {}
            }
            type: "endpoint"
        }
        {
            conditions: [
                {
                    fn: "isSet"
                    argv: [
                        { ref: "Region" }
                    ]
                }
            ]
            endpoint: {
                url: "https://s3.{Region}.amazonaws.com"
                properties: {}
                headers: {}
            }
            type: "endpoint"
        }
        {
            conditions: []
            error: "Invalid region: region was not a valid DNS name."
            type: "error"
        }
    ]
})
@clientContextParams(
    Region: {
        type: "string"
        documentation: "The AWS region, for example us-east-1."
    }
    Bucket: {
        type: "string"
        documentation: "The S3 bucket name."
    }
)
@service(sdkId: "S3", endpointPrefix: "s3", arnNamespace: "s3")
service EndpointRulesService {
    version: "2006-03-01"
    operations: [GetObject]
}

@readonly
operation GetObject {
    input: GetObjectInput
    output: GetObjectOutput
}

structure GetObjectInput {
    @required
    bucket: String

    @required
    key: String
}

structure GetObjectOutput {
    body: Blob
}
