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

class Ec2QueryProtocolAnalyzerTest {

    private static Model model;
    private static ServiceShape ec2Service;
    private static OperationShape describeInstances;
    private static final Ec2QueryProtocolAnalyzer analyzer = new Ec2QueryProtocolAnalyzer();

    @BeforeAll
    static void loadModel() {
        model = Model.assembler(Ec2QueryProtocolAnalyzerTest.class.getClassLoader())
                .discoverModels(Ec2QueryProtocolAnalyzerTest.class.getClassLoader())
                .addImport(Ec2QueryProtocolAnalyzerTest.class.getResource("ec2.smithy"))
                .assemble()
                .unwrap();
        ec2Service         = model.expectShape(ShapeId.from("example.ec2#Ec2Service"),         ServiceShape.class);
        describeInstances  = model.expectShape(ShapeId.from("example.ec2#DescribeInstances"),  OperationShape.class);
    }

    @Test
    void protocolIdIsEc2Query() {
        assertThat(analyzer.getProtocol()).isEqualTo(ShapeId.from("aws.protocols#ec2Query"));
    }

    @Test
    void contentTypeIsFormUrlEncoded() {
        assertThat(analyzer.contentType(ec2Service)).isEqualTo("application/x-www-form-urlencoded");
    }

    @Test
    void requiresQueryRuntime() {
        assertThat(analyzer.requiresQueryRuntime()).isTrue();
    }

    @Test
    void httpSpecIsPostRoot() {
        OperationSpec spec = analyzer.analyzeClientOperation(describeInstances, model, ec2Service);
        assertThat(spec.http().method()).isEqualTo("POST");
        assertThat(spec.http().uriTemplate()).isEqualTo("/");
        assertThat(spec.http().successCode()).isEqualTo(200);
    }

    @Test
    void noLabelsOrQueryParams() {
        OperationSpec spec = analyzer.analyzeClientOperation(describeInstances, model, ec2Service);
        assertThat(spec.labels()).isEmpty();
        assertThat(spec.queries()).isEmpty();
        assertThat(spec.headers()).isEmpty();
    }

    @Test
    void allInputMembersInFormEncodedBody() {
        OperationSpec spec = analyzer.analyzeClientOperation(describeInstances, model, ec2Service);
        assertThat(spec.body().encoding()).isEqualTo(BodyEncoding.FORM_URLENCODED);
        // Both Smithy member names are present as body members (used for Input lookup).
        assertThat(spec.body().bodyMemberNames())
                .containsExactlyInAnyOrder("filters", "maxResults");
    }

    @Test
    void ec2QueryNameOverridesApplied() {
        OperationSpec spec = analyzer.analyzeClientOperation(describeInstances, model, ec2Service);
        // @ec2QueryName("Filter") on the "filters" member must produce a wire-name override.
        assertThat(spec.body().wireNameOverrides())
                .containsEntry("filters", "Filter");
        // Members without @ec2QueryName produce no override entry.
        assertThat(spec.body().wireNameOverrides())
                .doesNotContainKey("maxResults");
    }

    @Test
    void errorStrategyIsAwsQuery() {
        OperationSpec spec = analyzer.analyzeClientOperation(describeInstances, model, ec2Service);
        assertThat(spec.errors().codeStrategy()).isEqualTo(ErrorCodeStrategy.AWS_QUERY);
    }

    @Test
    void sigV4AuthExtractedFromService() {
        OperationSpec spec = analyzer.analyzeClientOperation(describeInstances, model, ec2Service);
        assertThat(spec.auth().requiresSigV4()).isTrue();
        assertThat(spec.auth().signingName()).isEqualTo("ec2");
    }

    @Test
    void retryIsDefaultRetry() {
        OperationSpec spec = analyzer.analyzeClientOperation(describeInstances, model, ec2Service);
        assertThat(spec.retry().enabled()).isTrue();
        assertThat(spec.retry().maxRetries()).isEqualTo(3);
    }
}
