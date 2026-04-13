package io.smithy.beam.protocols;

import io.smithy.beam.core.ir.BodyEncoding;
import io.smithy.beam.core.ir.ErrorCodeStrategy;
import io.smithy.beam.core.ir.OperationSpec;
import io.smithy.beam.core.ir.Role;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

import static org.assertj.core.api.Assertions.assertThat;

class AwsJsonProtocolAnalyzerTest {

    private static Model model;
    private static ServiceShape dynamoService;
    private static ServiceShape lambdaService;
    private static OperationShape getItem;
    private static OperationShape invokeFunction;
    private static final AwsJsonProtocolAnalyzer analyzer10 = new AwsJsonProtocolAnalyzer();
    private static final AwsJson11ProtocolAnalyzer analyzer11 = new AwsJson11ProtocolAnalyzer();

    @BeforeAll
    static void loadModel() {
        model = Model.assembler(AwsJsonProtocolAnalyzerTest.class.getClassLoader())
                .discoverModels(AwsJsonProtocolAnalyzerTest.class.getClassLoader())
                .addImport(AwsJsonProtocolAnalyzerTest.class.getResource("dynamo.smithy"))
                .assemble()
                .unwrap();
        dynamoService = model.expectShape(ShapeId.from("example.dynamo#DynamoService"), ServiceShape.class);
        lambdaService = model.expectShape(ShapeId.from("example.dynamo#LambdaService"), ServiceShape.class);
        getItem = model.expectShape(ShapeId.from("example.dynamo#GetItem"), OperationShape.class);
        invokeFunction = model.expectShape(ShapeId.from("example.dynamo#InvokeFunction"), OperationShape.class);
    }

    // ── awsJson1_0 ────────────────────────────────────────────────────────────

    @Test
    void awsJson10ProtocolId() {
        assertThat(analyzer10.getProtocol()).isEqualTo(ShapeId.from("aws.protocols#awsJson1_0"));
    }

    @Test
    void awsJson10ContentType() {
        assertThat(analyzer10.contentType(dynamoService)).isEqualTo("application/x-amz-json-1.0");
    }

    @Test
    void awsJson10HttpSpecIsPostRoot() {
        OperationSpec spec = analyzer10.analyzeClientOperation(getItem, model, dynamoService);
        assertThat(spec.http().method()).isEqualTo("POST");
        assertThat(spec.http().uriTemplate()).isEqualTo("/");
        assertThat(spec.http().successCode()).isEqualTo(200);
    }

    @Test
    void awsJson10HasNoLabelsOrQueries() {
        OperationSpec spec = analyzer10.analyzeClientOperation(getItem, model, dynamoService);
        assertThat(spec.labels()).isEmpty();
        assertThat(spec.queries()).isEmpty();
    }

    @Test
    void awsJson10AllInputMembersInJsonBody() {
        OperationSpec spec = analyzer10.analyzeClientOperation(getItem, model, dynamoService);
        assertThat(spec.body().encoding()).isEqualTo(BodyEncoding.JSON);
        assertThat(spec.body().bodyMemberNames()).containsExactlyInAnyOrder("tableName", "key");
    }

    @Test
    void awsJson10HasXAmzTargetHeader() {
        OperationSpec spec = analyzer10.analyzeClientOperation(getItem, model, dynamoService);
        assertThat(spec.headers()).hasSize(1);
        assertThat(spec.headers().get(0).headerName()).isEqualTo("X-Amz-Target");
    }

    @Test
    void awsJson10ErrorStrategyIsAwsJson() {
        OperationSpec spec = analyzer10.analyzeClientOperation(getItem, model, dynamoService);
        assertThat(spec.errors().codeStrategy()).isEqualTo(ErrorCodeStrategy.AWS_JSON);
    }

    @Test
    void awsJson10SigV4AuthFromService() {
        OperationSpec spec = analyzer10.analyzeClientOperation(getItem, model, dynamoService);
        assertThat(spec.auth().requiresSigV4()).isTrue();
        assertThat(spec.auth().signingName()).isEqualTo("dynamodb");
    }

    // ── awsJson1_1 ────────────────────────────────────────────────────────────

    @Test
    void awsJson11ProtocolId() {
        assertThat(analyzer11.getProtocol()).isEqualTo(ShapeId.from("aws.protocols#awsJson1_1"));
    }

    @Test
    void awsJson11ContentType() {
        assertThat(analyzer11.contentType(lambdaService)).isEqualTo("application/x-amz-json-1.1");
    }

    @Test
    void awsJson11HttpSpecIsPostRoot() {
        OperationSpec spec = analyzer11.analyzeClientOperation(invokeFunction, model, lambdaService);
        assertThat(spec.http().method()).isEqualTo("POST");
        assertThat(spec.http().uriTemplate()).isEqualTo("/");
    }

    @Test
    void awsJson11AllInputMembersInBody() {
        OperationSpec spec = analyzer11.analyzeClientOperation(invokeFunction, model, lambdaService);
        assertThat(spec.body().encoding()).isEqualTo(BodyEncoding.JSON);
        assertThat(spec.body().bodyMemberNames()).containsExactlyInAnyOrder("functionName", "payload");
    }

    @Test
    void awsJson11SigV4AuthFromService() {
        OperationSpec spec = analyzer11.analyzeClientOperation(invokeFunction, model, lambdaService);
        assertThat(spec.auth().requiresSigV4()).isTrue();
        assertThat(spec.auth().signingName()).isEqualTo("lambda");
    }

    // ── analyzeServerOperation ────────────────────────────────────────────────

    @Test
    void serverRoleIsServer() {
        OperationSpec spec = analyzer10.analyzeServerOperation(getItem, model, dynamoService);
        assertThat(spec.role()).isEqualTo(Role.SERVER);
    }

    @Test
    void serverAuthIsNone() {
        OperationSpec spec = analyzer10.analyzeServerOperation(getItem, model, dynamoService);
        assertThat(spec.auth().requiresSigV4()).isFalse();
        assertThat(spec.auth().signingName()).isNull();
    }

    @Test
    void serverRetryIsDisabled() {
        OperationSpec spec = analyzer10.analyzeServerOperation(getItem, model, dynamoService);
        assertThat(spec.retry().enabled()).isFalse();
    }

    @Test
    void serverPaginationIsNull() {
        OperationSpec spec = analyzer10.analyzeServerOperation(getItem, model, dynamoService);
        assertThat(spec.pagination()).isNull();
    }

    @Test
    void serverHttpSpecIsPostRoot() {
        OperationSpec spec = analyzer10.analyzeServerOperation(getItem, model, dynamoService);
        assertThat(spec.http().method()).isEqualTo("POST");
        assertThat(spec.http().uriTemplate()).isEqualTo("/");
        assertThat(spec.http().successCode()).isEqualTo(200);
    }

    @Test
    void serverHasXAmzTargetHeader() {
        OperationSpec spec = analyzer10.analyzeServerOperation(getItem, model, dynamoService);
        assertThat(spec.headers()).hasSize(1);
        assertThat(spec.headers().get(0).headerName()).isEqualTo("X-Amz-Target");
    }

    @Test
    void serverXAmzTargetLiteralValueContainsServiceAndOperation() {
        OperationSpec spec = analyzer10.analyzeServerOperation(getItem, model, dynamoService);
        String literal = spec.headers().get(0).literalValue();
        assertThat(literal).isEqualTo("DynamoService.GetItem");
    }

    @Test
    void serverAllInputMembersInBody() {
        OperationSpec spec = analyzer10.analyzeServerOperation(getItem, model, dynamoService);
        assertThat(spec.body().encoding()).isEqualTo(BodyEncoding.JSON);
        assertThat(spec.body().bodyMemberNames()).containsExactlyInAnyOrder("tableName", "key");
    }

    @Test
    void serverHasNoLabelsOrQueries() {
        OperationSpec spec = analyzer10.analyzeServerOperation(getItem, model, dynamoService);
        assertThat(spec.labels()).isEmpty();
        assertThat(spec.queries()).isEmpty();
    }

    @Test
    void serverErrorStrategyIsAwsJson() {
        OperationSpec spec = analyzer10.analyzeServerOperation(getItem, model, dynamoService);
        assertThat(spec.errors().codeStrategy()).isEqualTo(ErrorCodeStrategy.AWS_JSON);
    }

    @Test
    void awsJson11ServerRoleIsServer() {
        OperationSpec spec = analyzer11.analyzeServerOperation(invokeFunction, model, lambdaService);
        assertThat(spec.role()).isEqualTo(Role.SERVER);
    }

    @Test
    void awsJson11ServerAuthIsNone() {
        OperationSpec spec = analyzer11.analyzeServerOperation(invokeFunction, model, lambdaService);
        assertThat(spec.auth().requiresSigV4()).isFalse();
    }

    @Test
    void awsJson11ServerXAmzTargetLiteralValue() {
        OperationSpec spec = analyzer11.analyzeServerOperation(invokeFunction, model, lambdaService);
        assertThat(spec.headers().get(0).literalValue()).isEqualTo("LambdaService.InvokeFunction");
    }
}
