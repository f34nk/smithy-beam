$version: "2"
namespace smithy.beam.test.s3restxml

use aws.protocols#restXml
use aws.api#service
use smithy.api#httpLabel
use smithy.api#idempotent

@restXml
@service(sdkId: "S3", endpointPrefix: "s3")
service S3RestXmlService {
    version: "2006-03-01"
    operations: [PutObject]
}

@http(method: "PUT", uri: "/{Bucket}/{Key}")
@idempotent
operation PutObject {
    input: PutObjectInput
}

structure PutObjectInput {
    @httpLabel
    @required
    Bucket: String

    @httpLabel
    @required
    Key: String
}
