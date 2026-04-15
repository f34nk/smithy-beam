package io.smithy.beam.protocols;

import io.smithy.beam.core.ir.BodyEncoding;
import io.smithy.beam.core.ir.OperationSpec;
import io.smithy.beam.core.ir.Role;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

import static org.assertj.core.api.Assertions.assertThat;

class RestJsonProtocolAnalyzerTest {

    private static Model model;
    private static ServiceShape service;
    private static OperationShape getWeather;
    private static OperationShape listCities;
    private static final RestJsonProtocolAnalyzer analyzer = new RestJsonProtocolAnalyzer();

    @BeforeAll
    static void loadModel() {
        model = Model.assembler(RestJsonProtocolAnalyzerTest.class.getClassLoader())
                .discoverModels(RestJsonProtocolAnalyzerTest.class.getClassLoader())
                .addImport(RestJsonProtocolAnalyzerTest.class.getResource("weather.smithy"))
                .assemble()
                .unwrap();
        service = model.expectShape(ShapeId.from("example.weather#WeatherService"), ServiceShape.class);
        getWeather = model.expectShape(ShapeId.from("example.weather#GetWeather"), OperationShape.class);
        listCities = model.expectShape(ShapeId.from("example.weather#ListCities"), OperationShape.class);
    }

    @Test
    void getProtocolReturnsRestJson1() {
        assertThat(analyzer.getProtocol()).isEqualTo(ShapeId.from("aws.protocols#restJson1"));
    }

    @Test
    void contentTypeIsApplicationJson() {
        assertThat(analyzer.contentType(service)).isEqualTo("application/json");
    }

    @Test
    void httpSpecExtractedFromHttpTrait() {
        OperationSpec spec = analyzer.analyzeClientOperation(getWeather, model, service);
        assertThat(spec.http().method()).isEqualTo("GET");
        assertThat(spec.http().uriTemplate()).isEqualTo("/weather/{city}");
        assertThat(spec.http().successCode()).isEqualTo(200);
    }

    @Test
    void labelBindingExtractedForCityMember() {
        OperationSpec spec = analyzer.analyzeClientOperation(getWeather, model, service);
        assertThat(spec.labels()).hasSize(1);
        assertThat(spec.labels().get(0).smithyMemberName()).isEqualTo("city");
        assertThat(spec.labels().get(0).requiresEncoding()).isTrue();
    }

    @Test
    void bodyIsNoneWhenAllMembersAreBound() {
        OperationSpec spec = analyzer.analyzeClientOperation(getWeather, model, service);
        assertThat(spec.body().encoding()).isEqualTo(BodyEncoding.NONE);
        assertThat(spec.body().bodyMemberNames()).isEmpty();
    }

    @Test
    void queryHeaderAndBodyMembersFromListCities() {
        OperationSpec spec = analyzer.analyzeClientOperation(listCities, model, service);

        // filter, nextToken, maxResults are all @httpQuery
        assertThat(spec.queries()).extracting("smithyMemberName")
                .containsExactlyInAnyOrder("filter", "nextToken", "maxResults");

        assertThat(spec.headers()).hasSize(1);
        assertThat(spec.headers().get(0).headerName()).isEqualTo("X-Custom-Header");

        // all members are bound via query or header → body is empty
        assertThat(spec.body().encoding()).isEqualTo(BodyEncoding.NONE);
        assertThat(spec.body().bodyMemberNames()).isEmpty();
    }

    @Test
    void sigV4AuthExtractedFromService() {
        OperationSpec spec = analyzer.analyzeClientOperation(getWeather, model, service);
        assertThat(spec.auth().requiresSigV4()).isTrue();
        assertThat(spec.auth().signingName()).isEqualTo("weather");
    }

    @Test
    void paginationExtractedFromListCities() {
        OperationSpec spec = analyzer.analyzeClientOperation(listCities, model, service);
        assertThat(spec.pagination()).isNotNull();
        assertThat(spec.pagination().inputTokenMember()).isEqualTo("nextToken");
        assertThat(spec.pagination().outputTokenMember()).isEqualTo("nextToken");
        assertThat(spec.pagination().itemsMember()).isEqualTo("cities");
        assertThat(spec.pagination().pageSizeMember()).isEqualTo("maxResults");
    }

    @Test
    void noPaginationForGetWeather() {
        OperationSpec spec = analyzer.analyzeClientOperation(getWeather, model, service);
        assertThat(spec.pagination()).isNull();
    }

    @Test
    void retryIsDefaultRetry() {
        OperationSpec spec = analyzer.analyzeClientOperation(getWeather, model, service);
        assertThat(spec.retry().enabled()).isTrue();
        assertThat(spec.retry().maxRetries()).isEqualTo(3);
    }

    // ── analyzeServerOperation ────────────────────────────────────────────────

    @Test
    void serverRoleIsServer() {
        OperationSpec spec = analyzer.analyzeServerOperation(getWeather, model, service);
        assertThat(spec.role()).isEqualTo(Role.SERVER);
    }

    @Test
    void serverAuthIsNone() {
        OperationSpec spec = analyzer.analyzeServerOperation(getWeather, model, service);
        assertThat(spec.auth().requiresSigV4()).isFalse();
        assertThat(spec.auth().signingName()).isNull();
    }

    @Test
    void serverRetryIsDisabled() {
        OperationSpec spec = analyzer.analyzeServerOperation(getWeather, model, service);
        assertThat(spec.retry().enabled()).isFalse();
    }

    @Test
    void serverPaginationIsNull() {
        OperationSpec spec = analyzer.analyzeServerOperation(getWeather, model, service);
        assertThat(spec.pagination()).isNull();
    }

    @Test
    void serverPaginationIsNullEvenForPaginatedOperation() {
        OperationSpec spec = analyzer.analyzeServerOperation(listCities, model, service);
        assertThat(spec.pagination()).isNull();
    }

    @Test
    void serverHttpSpecMatchesClient() {
        OperationSpec client = analyzer.analyzeClientOperation(getWeather, model, service);
        OperationSpec server = analyzer.analyzeServerOperation(getWeather, model, service);
        assertThat(server.http()).isEqualTo(client.http());
    }

    @Test
    void serverLabelsMatchClient() {
        OperationSpec client = analyzer.analyzeClientOperation(getWeather, model, service);
        OperationSpec server = analyzer.analyzeServerOperation(getWeather, model, service);
        assertThat(server.labels()).isEqualTo(client.labels());
    }

    @Test
    void serverQueriesMatchClient() {
        OperationSpec client = analyzer.analyzeClientOperation(listCities, model, service);
        OperationSpec server = analyzer.analyzeServerOperation(listCities, model, service);
        assertThat(server.queries()).isEqualTo(client.queries());
    }

    @Test
    void serverHeadersMatchClient() {
        OperationSpec client = analyzer.analyzeClientOperation(listCities, model, service);
        OperationSpec server = analyzer.analyzeServerOperation(listCities, model, service);
        assertThat(server.headers()).isEqualTo(client.headers());
    }

    @Test
    void serverBodyMatchesClient() {
        OperationSpec client = analyzer.analyzeClientOperation(getWeather, model, service);
        OperationSpec server = analyzer.analyzeServerOperation(getWeather, model, service);
        assertThat(server.body()).isEqualTo(client.body());
    }

    @Test
    void serverErrorsMatchClient() {
        OperationSpec client = analyzer.analyzeClientOperation(getWeather, model, service);
        OperationSpec server = analyzer.analyzeServerOperation(getWeather, model, service);
        assertThat(server.errors()).isEqualTo(client.errors());
    }
}
