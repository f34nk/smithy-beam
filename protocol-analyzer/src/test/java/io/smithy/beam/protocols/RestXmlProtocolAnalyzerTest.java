package io.smithy.beam.protocols;

import io.smithy.beam.core.ir.BodyEncoding;
import io.smithy.beam.core.ir.ErrorCodeStrategy;
import io.smithy.beam.core.ir.OperationSpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

import static org.assertj.core.api.Assertions.assertThat;

class RestXmlProtocolAnalyzerTest {

    private static Model model;
    private static ServiceShape s3Service;
    private static ServiceShape cloudFrontService;
    private static OperationShape getObject;
    private static OperationShape putObject;
    private static OperationShape listDistributions;
    private static final RestXmlProtocolAnalyzer analyzer = new RestXmlProtocolAnalyzer();

    @BeforeAll
    static void loadModel() {
        model = Model.assembler(RestXmlProtocolAnalyzerTest.class.getClassLoader())
                .discoverModels(RestXmlProtocolAnalyzerTest.class.getClassLoader())
                .addImport(RestXmlProtocolAnalyzerTest.class.getResource("s3.smithy"))
                .assemble()
                .unwrap();
        s3Service          = model.expectShape(ShapeId.from("example.s3#S3Service"),            ServiceShape.class);
        cloudFrontService  = model.expectShape(ShapeId.from("example.s3#CloudFrontService"),    ServiceShape.class);
        getObject          = model.expectShape(ShapeId.from("example.s3#GetObject"),            OperationShape.class);
        putObject          = model.expectShape(ShapeId.from("example.s3#PutObject"),            OperationShape.class);
        listDistributions  = model.expectShape(ShapeId.from("example.s3#ListDistributions"),    OperationShape.class);
    }

    @Test
    void protocolIdIsRestXml() {
        assertThat(analyzer.getProtocol()).isEqualTo(ShapeId.from("aws.protocols#restXml"));
    }

    @Test
    void contentTypeIsApplicationXml() {
        assertThat(analyzer.contentType(s3Service)).isEqualTo("application/xml");
    }

    @Test
    void requiresXmlRuntime() {
        assertThat(analyzer.requiresXmlRuntime()).isTrue();
    }

    @Test
    void requiresQueryRuntimeIsFalse() {
        assertThat(analyzer.requiresQueryRuntime()).isFalse();
    }

    // ── S3 runtime detection ─────────────────────────────────────────────────

    @Test
    void requiresS3RuntimeTrueForS3ArnNamespace() {
        assertThat(analyzer.requiresS3Runtime(s3Service)).isTrue();
    }

    @Test
    void requiresS3RuntimeFalseForNonS3Service() {
        assertThat(analyzer.requiresS3Runtime(cloudFrontService)).isFalse();
    }

    // ── HTTP spec ────────────────────────────────────────────────────────────

    @Test
    void httpSpecExtractedFromHttpTrait() {
        OperationSpec spec = analyzer.analyzeClientOperation(getObject, model, s3Service);
        assertThat(spec.http().method()).isEqualTo("GET");
        assertThat(spec.http().uriTemplate()).isEqualTo("/bucket/{Bucket}/key/{Key}");
        assertThat(spec.http().successCode()).isEqualTo(200);
    }

    // ── Labels and query params ──────────────────────────────────────────────

    @Test
    void labelBindingsExtractedForGetObject() {
        OperationSpec spec = analyzer.analyzeClientOperation(getObject, model, s3Service);
        assertThat(spec.labels()).hasSize(2);
        assertThat(spec.labels()).extracting("smithyMemberName")
                .containsExactlyInAnyOrder("Bucket", "Key");
    }

    @Test
    void queryBindingExtractedForVersionId() {
        OperationSpec spec = analyzer.analyzeClientOperation(getObject, model, s3Service);
        assertThat(spec.queries()).hasSize(1);
        assertThat(spec.queries().get(0).smithyMemberName()).isEqualTo("VersionId");
        assertThat(spec.queries().get(0).queryKey()).isEqualTo("versionId");
    }

    // ── Body encoding ────────────────────────────────────────────────────────

    @Test
    void bodyIsNoneWhenAllMembersAreBoundAsLabelsOrQuery() {
        OperationSpec spec = analyzer.analyzeClientOperation(getObject, model, s3Service);
        assertThat(spec.body().encoding()).isEqualTo(BodyEncoding.NONE);
        assertThat(spec.body().bodyMemberNames()).isEmpty();
    }

    @Test
    void bodyIsXmlForUnboundMembers() {
        OperationSpec spec = analyzer.analyzeClientOperation(putObject, model, s3Service);
        assertThat(spec.body().encoding()).isEqualTo(BodyEncoding.XML);
        assertThat(spec.body().bodyMemberNames())
                .containsExactlyInAnyOrder("ContentType", "Body");
    }

    @Test
    void queryOnlyOperationHasNoBody() {
        OperationSpec spec = analyzer.analyzeClientOperation(listDistributions, model, cloudFrontService);
        assertThat(spec.body().encoding()).isEqualTo(BodyEncoding.NONE);
        assertThat(spec.queries()).hasSize(2);
    }

    // ── Response bindings (output shape) ────────────────────────────────────

    @Test
    void responsePayloadMemberSetFromHttpPayloadTrait() {
        OperationSpec spec = analyzer.analyzeClientOperation(getObject, model, s3Service);
        assertThat(spec.responsePayloadMember()).isEqualTo("Body");
    }

    @Test
    void responsePayloadMemberNullWhenNoHttpPayloadTrait() {
        OperationSpec spec = analyzer.analyzeClientOperation(putObject, model, s3Service);
        assertThat(spec.responsePayloadMember()).isNull();
    }

    @Test
    void responseHeadersExtractedFromOutputShape() {
        OperationSpec spec = analyzer.analyzeClientOperation(getObject, model, s3Service);
        assertThat(spec.responseHeaders()).hasSize(1);
        assertThat(spec.responseHeaders().get(0).smithyMemberName()).isEqualTo("ContentType");
        assertThat(spec.responseHeaders().get(0).headerName()).isEqualTo("Content-Type");
    }

    // ── Error strategy ───────────────────────────────────────────────────────

    @Test
    void errorStrategyIsRestXml() {
        OperationSpec spec = analyzer.analyzeClientOperation(getObject, model, s3Service);
        assertThat(spec.errors().codeStrategy()).isEqualTo(ErrorCodeStrategy.REST_XML);
    }

    // ── Auth ─────────────────────────────────────────────────────────────────

    @Test
    void sigV4AuthExtractedFromS3Service() {
        OperationSpec spec = analyzer.analyzeClientOperation(getObject, model, s3Service);
        assertThat(spec.auth().requiresSigV4()).isTrue();
        assertThat(spec.auth().signingName()).isEqualTo("s3");
    }

    // ── Retry ────────────────────────────────────────────────────────────────

    @Test
    void retryIsDefaultRetry() {
        OperationSpec spec = analyzer.analyzeClientOperation(getObject, model, s3Service);
        assertThat(spec.retry().enabled()).isTrue();
        assertThat(spec.retry().maxRetries()).isEqualTo(3);
    }
}
