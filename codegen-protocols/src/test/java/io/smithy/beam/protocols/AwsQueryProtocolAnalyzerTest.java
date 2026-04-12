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

class AwsQueryProtocolAnalyzerTest {

    private static Model model;
    private static ServiceShape sqsService;
    private static OperationShape sendMessage;
    private static final AwsQueryProtocolAnalyzer analyzer = new AwsQueryProtocolAnalyzer();

    @BeforeAll
    static void loadModel() {
        model = Model.assembler(AwsQueryProtocolAnalyzerTest.class.getClassLoader())
                .discoverModels(AwsQueryProtocolAnalyzerTest.class.getClassLoader())
                .addImport(AwsQueryProtocolAnalyzerTest.class.getResource("sqs.smithy"))
                .assemble()
                .unwrap();
        sqsService  = model.expectShape(ShapeId.from("example.sqs#SqsService"),  ServiceShape.class);
        sendMessage = model.expectShape(ShapeId.from("example.sqs#SendMessage"),  OperationShape.class);
    }

    @Test
    void protocolIdIsAwsQuery() {
        assertThat(analyzer.getProtocol()).isEqualTo(ShapeId.from("aws.protocols#awsQuery"));
    }

    @Test
    void contentTypeIsFormUrlEncoded() {
        assertThat(analyzer.contentType(sqsService)).isEqualTo("application/x-www-form-urlencoded");
    }

    @Test
    void requiresQueryRuntime() {
        assertThat(analyzer.requiresQueryRuntime()).isTrue();
    }

    @Test
    void requiresXmlRuntimeIsFalse() {
        assertThat(analyzer.requiresXmlRuntime()).isFalse();
    }

    @Test
    void httpSpecIsPostRoot() {
        OperationSpec spec = analyzer.analyzeClientOperation(sendMessage, model, sqsService);
        assertThat(spec.http().method()).isEqualTo("POST");
        assertThat(spec.http().uriTemplate()).isEqualTo("/");
        assertThat(spec.http().successCode()).isEqualTo(200);
    }

    @Test
    void noLabelsOrQueryParams() {
        OperationSpec spec = analyzer.analyzeClientOperation(sendMessage, model, sqsService);
        assertThat(spec.labels()).isEmpty();
        assertThat(spec.queries()).isEmpty();
        assertThat(spec.headers()).isEmpty();
    }

    @Test
    void allInputMembersInFormEncodedBody() {
        OperationSpec spec = analyzer.analyzeClientOperation(sendMessage, model, sqsService);
        assertThat(spec.body().encoding()).isEqualTo(BodyEncoding.FORM_URLENCODED);
        assertThat(spec.body().bodyMemberNames())
                .containsExactlyInAnyOrder("QueueUrl", "MessageBody", "DelaySeconds");
    }

    @Test
    void errorStrategyIsAwsQuery() {
        OperationSpec spec = analyzer.analyzeClientOperation(sendMessage, model, sqsService);
        assertThat(spec.errors().codeStrategy()).isEqualTo(ErrorCodeStrategy.AWS_QUERY);
        assertThat(spec.errors().errors()).hasSize(1);
        assertThat(spec.errors().errors().get(0).smithyName()).isEqualTo("InvalidMessageContents");
    }

    @Test
    void sigV4AuthExtractedFromService() {
        OperationSpec spec = analyzer.analyzeClientOperation(sendMessage, model, sqsService);
        assertThat(spec.auth().requiresSigV4()).isTrue();
        assertThat(spec.auth().signingName()).isEqualTo("sqs");
    }

    @Test
    void retryIsDefaultRetry() {
        OperationSpec spec = analyzer.analyzeClientOperation(sendMessage, model, sqsService);
        assertThat(spec.retry().enabled()).isTrue();
        assertThat(spec.retry().maxRetries()).isEqualTo(3);
    }
}
