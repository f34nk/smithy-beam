package io.smithy.beam.erlang.writer;

import io.smithy.beam.core.ir.AuthSpec;
import io.smithy.beam.core.ir.BodyEncoding;
import io.smithy.beam.core.ir.BodySpec;
import io.smithy.beam.core.ir.EnumSpec;
import io.smithy.beam.core.ir.ErrorBinding;
import io.smithy.beam.core.ir.ErrorCodeStrategy;
import io.smithy.beam.core.ir.ErrorSpec;
import io.smithy.beam.core.ir.FieldSpec;
import io.smithy.beam.core.ir.HeaderBinding;
import io.smithy.beam.core.ir.HttpSpec;
import io.smithy.beam.core.ir.LabelBinding;
import io.smithy.beam.core.ir.ModuleTypeSpec;
import io.smithy.beam.core.ir.OperationSpec;
import io.smithy.beam.core.ir.PaginationSpec;
import io.smithy.beam.core.ir.PrimitiveKind;
import io.smithy.beam.core.ir.QueryBinding;
import io.smithy.beam.core.ir.RetrySpec;
import io.smithy.beam.core.ir.Role;
import io.smithy.beam.core.ir.StructSpec;
import io.smithy.beam.core.ir.TypeRef;
import io.smithy.beam.core.ir.UnionSpec;
import io.smithy.beam.core.writer.ExportSpec;
import io.smithy.beam.core.writer.MapEntrySpec;
import io.smithy.beam.core.writer.ParamSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ErlangWriterTest {

    private ErlangWriter writer;

    @BeforeEach
    void setUp() {
        writer = new ErlangWriter();
    }

    // ── Identity ──────────────────────────────────────────────────────────────

    @Nested
    class Identity {
        @Test
        void languageId() {
            assertThat(writer.languageId()).isEqualTo("erlang");
        }

        @Test
        void fileExtension() {
            assertThat(writer.fileExtension()).isEqualTo(".erl");
        }
    }

    // ── Naming ────────────────────────────────────────────────────────────────

    @Nested
    class Naming {
        @Test
        void moduleNameConvertsToSnakeCase() {
            assertThat(writer.moduleName("WeatherService")).isEqualTo("weather_service");
        }

        @Test
        void functionNameConvertsToSnakeCase() {
            assertThat(writer.functionName("GetWeather")).isEqualTo("get_weather");
        }

        @Test
        void typeNameConvertsToSnakeCaseWithParens() {
            assertThat(writer.typeName("GetWeatherInput")).isEqualTo("get_weather_input()");
        }

        @Test
        void varNameConvertsToCapitalized() {
            // Erlang variables start with an uppercase letter
            assertThat(writer.varName("cityId")).isEqualTo("CityId");
        }

        @Test
        void mapKeyProducesBinaryLiteral() {
            assertThat(writer.mapKey("cityId")).isEqualTo("<<\"cityId\">>");
        }
    }

    // ── Module structure ──────────────────────────────────────────────────────

    @Nested
    class ModuleStructure {
        @Test
        void moduleHeaderEmitsModuleDirective() {
            assertThat(writer.moduleHeader("WeatherService"))
                    .isEqualTo("-module(weather_service).\n");
        }

        @Test
        void moduleFooterIsEmpty() {
            assertThat(writer.moduleFooter()).isEmpty();
        }

        @Test
        void renderModuleCommentEmitsErlangComment() {
            String result = writer.renderModuleComment("Generated Smithy client for AmazonS3");
            assertThat(result).contains("%%");
            assertThat(result).contains("Generated Smithy client for AmazonS3");
        }

        @Test
        void exportSectionEmptyProducesEmptyExport() {
            assertThat(writer.exportSection(List.of())).isEqualTo("-export([]).\n");
        }

        @Test
        void exportSectionSingleEntry() {
            String result = writer.exportSection(List.of(new ExportSpec("get_weather", 2)));
            assertThat(result).contains("-export([");
            assertThat(result).contains("get_weather/2");
            assertThat(result).contains("]).");
        }

        @Test
        void exportSectionMultipleEntriesHaveCommas() {
            String result = writer.exportSection(List.of(
                    new ExportSpec("get_weather", 2),
                    new ExportSpec("new", 1)
            ));
            assertThat(result).contains("get_weather/2,");
            assertThat(result).contains("new/1");
            // last entry has no trailing comma
            assertThat(result).doesNotContain("new/1,");
        }

        @Test
        void behaviourDeclarationEmitsBehaviourAttribute() {
            assertThat(writer.behaviourDeclaration("WeatherServiceHandler"))
                    .isEqualTo("-behaviour(weather_service_handler).\n");
        }

        @Test
        void renderToolingAttributesEmitsDialyzer() {
            assertThat(writer.renderToolingAttributes()).contains("-dialyzer(");
        }

        @Test
        void exportTypesEmptyReturnsEmpty() {
            assertThat(writer.exportTypes(List.of())).isEmpty();
        }

        @Test
        void exportTypesWithNamesEmitsExportType() {
            String result = writer.exportTypes(List.of("get_weather_input()", "temperature_unit()"));
            assertThat(result).contains("-export_type([");
            assertThat(result).contains("get_weather_input()");
            assertThat(result).contains("temperature_unit()");
        }
    }

    // ── Type rendering ────────────────────────────────────────────────────────

    @Nested
    class TypeRendering {
        @Test
        void renderStructTypeEmitsMapTypeWithFields() {
            StructSpec struct = new StructSpec("GetWeatherInput", List.of(
                    new FieldSpec("city", new TypeRef.Primitive(PrimitiveKind.STRING), true, false, false, false, false),
                    new FieldSpec("unit", new TypeRef.Named("TemperatureUnit"), false, false, false, false, false)
            ), false, 0);

            String result = writer.renderStructType(struct);
            assertThat(result).startsWith("-type get_weather_input() :: #{");
            assertThat(result).contains("city => binary()");
            assertThat(result).contains("unit => temperature_unit()");
            assertThat(result).endsWith("}.\n");
        }

        @Test
        void renderStructTypeHandlesEmptyStruct() {
            StructSpec empty = new StructSpec("EmptyInput", List.of(), false, 0);
            assertThat(writer.renderStructType(empty)).isEqualTo("-type empty_input() :: #{}.\n");
        }

        @Test
        void renderEnumTypeEmitsAtomUnion() {
            EnumSpec e = new EnumSpec("TemperatureUnit", List.of("CELSIUS", "FAHRENHEIT"));
            String result = writer.renderEnumType(e);
            assertThat(result).startsWith("-type temperature_unit() :: ");
            assertThat(result).contains("celsius");
            assertThat(result).contains("fahrenheit");
            assertThat(result).contains(" | ");
        }

        @Test
        void renderUnionTypeEmitsTaggedTupleVariants() {
            UnionSpec u = new UnionSpec("Result", List.of(
                    new FieldSpec("success", new TypeRef.Named("SuccessOutput"), false, false, false, false, false),
                    new FieldSpec("failure", new TypeRef.Named("FailureOutput"), false, false, false, false, false)
            ));
            String result = writer.renderUnionType(u);
            assertThat(result).startsWith("-type result() :: ");
            assertThat(result).contains("{success, success_output()}");
            assertThat(result).contains("{failure, failure_output()}");
            assertThat(result).contains(" | ");
        }

        @Test
        void renderCallbackDeclarationEmitsCallback() {
            List<ParamSpec> params = List.of(
                    new ParamSpec("input", new TypeRef.Named("GetWeatherInput")),
                    new ParamSpec("context", new TypeRef.MapOf(
                            new TypeRef.Primitive(PrimitiveKind.STRING),
                            new TypeRef.Primitive(PrimitiveKind.STRING)))
            );
            TypeRef returnType = new TypeRef.Named("GetWeatherOutput");
            String result = writer.renderCallbackDeclaration("GetWeather", params, returnType);
            assertThat(result).startsWith("-callback get_weather(");
            assertThat(result).contains("Input :: get_weather_input()");
            assertThat(result).contains("Context :: map()");
            assertThat(result).contains("->");
            assertThat(result).contains("get_weather_output()");
        }

        @Test
        void renderFunctionSpecEmitsSpec() {
            List<ParamSpec> params = List.of(
                    new ParamSpec("input", new TypeRef.Named("GetWeatherInput")),
                    new ParamSpec("config", new TypeRef.MapOf(
                            new TypeRef.Primitive(PrimitiveKind.STRING),
                            new TypeRef.Primitive(PrimitiveKind.STRING)))
            );
            TypeRef returnType = new TypeRef.Named("GetWeatherOutput");
            String result = writer.renderFunctionSpec("GetWeather", params, returnType);
            assertThat(result).isEqualTo("-spec get_weather(get_weather_input(), map()) -> get_weather_output().\n");
        }

        @Test
        void renderFunctionHeadEmitsClauseHead() {
            String result = writer.renderFunctionHead("GetWeather", List.of("Input", "Config"));
            assertThat(result).isEqualTo("get_weather(Input, Config) ->");
        }

        @Test
        void renderFunctionEndEmitsDot() {
            assertThat(writer.renderFunctionEnd()).isEqualTo(".");
        }
    }

    // ── Map operations ────────────────────────────────────────────────────────

    @Nested
    class MapOperations {
        @Test
        void renderMapGetUsesBinaryKeyAndMapsGet() {
            String result = writer.renderMapGet("Input", "cityId", "undefined");
            assertThat(result).isEqualTo("maps:get(<<\"cityId\">>, Input, undefined)");
        }

        @Test
        void renderMapBuildWithEntriesProducesMapLiteral() {
            List<MapEntrySpec> entries = List.of(
                    new MapEntrySpec("city", "City"),
                    new MapEntrySpec("unit", "Unit")
            );
            String result = writer.renderMapBuild(entries);
            assertThat(result).isEqualTo("#{<<\"city\">> => City, <<\"unit\">> => Unit}");
        }

        @Test
        void renderMapBuildEmptyProducesEmptyMap() {
            assertThat(writer.renderMapBuild(List.of())).isEqualTo("#{}");
        }
    }

    // ── JSON / XML / Form helpers ─────────────────────────────────────────────

    @Nested
    class EncodingHelpers {
        @Test
        void renderJsonEncodeUsesJsx() {
            assertThat(writer.renderJsonEncode("BodyMap")).isEqualTo("jsx:encode(BodyMap)");
        }

        @Test
        void renderJsonDecodeUsesJsx() {
            assertThat(writer.renderJsonDecode("RespBody"))
                    .isEqualTo("jsx:decode(RespBody, [return_maps])");
        }

        @Test
        void renderXmlEncodeUsesAwsXml() {
            assertThat(writer.renderXmlEncode("Map", "Body"))
                    .isEqualTo("smithy_xml:encode(Map, <<\"Body\">>)");
        }

        @Test
        void renderXmlDecodeUsesAwsXml() {
            assertThat(writer.renderXmlDecode("Body")).isEqualTo("smithy_xml:decode(Body)");
        }

        @Test
        void renderFormEncodeUsesAwsQuery() {
            assertThat(writer.renderFormEncode("ListUsers", "Map"))
                    .isEqualTo("smithy_query:encode(<<\"ListUsers\">>, Map)");
        }

        @Test
        void jsonEncodeCallUsesJsx() {
            assertThat(writer.jsonEncodeCall("my_map")).isEqualTo("jsx:encode(my_map)");
        }

        @Test
        void jsonDecodeCallUsesJsx() {
            assertThat(writer.jsonDecodeCall("raw"))
                    .isEqualTo("jsx:decode(raw, [return_maps])");
        }

        @Test
        void sigv4SignCallReturnsAwsSigv4Function() {
            assertThat(writer.sigv4SignCall()).isEqualTo("smithy_sigv4:sign_request");
        }

        @Test
        void retryCallUsesAwsRetry() {
            assertThat(writer.retryCall("Fun", "#{max_retries => 3}"))
                    .isEqualTo("smithy_retry:with_retry(Fun, #{max_retries => 3})");
        }
    }

    // ── URI and headers ───────────────────────────────────────────────────────

    @Nested
    class UriAndHeaders {
        @Test
        void renderUriSubstitutionWithNoLabelsProducesBinaryLiteral() {
            String result = writer.renderUriSubstitution("/weather", List.of(), "Input");
            assertThat(result).isEqualTo("<<\"/weather\">>");
        }

        @Test
        void renderUriSubstitutionInterpolatesLabel() {
            List<LabelBinding> labels = List.of(new LabelBinding("city", "city", false));
            String result = writer.renderUriSubstitution("/weather/{city}", labels, "Input");
            assertThat(result).startsWith("<<");
            assertThat(result).contains("maps:get(<<\"city\">>, Input)");
            assertThat(result).contains("/binary");
        }

        @Test
        void renderUriSubstitutionEncodesWhenRequired() {
            List<LabelBinding> labels = List.of(new LabelBinding("key", "key", true));
            String result = writer.renderUriSubstitution("/bucket/{key}", labels, "Input");
            assertThat(result).contains("url_encode(");
        }

        @Test
        void renderQueryStringBuilderWithNoQueriesEmitsEmptyString() {
            String result = writer.renderQueryStringBuilder(List.of(), "Input");
            assertThat(result).isEqualTo("QueryString = \"\",\n");
        }

        @Test
        void renderQueryStringBuilderWithQueriesBuildsParams() {
            List<QueryBinding> queries = List.of(new QueryBinding("unit", "unit"));
            String result = writer.renderQueryStringBuilder(queries, "Input");
            assertThat(result).contains("QueryParams = [");
            assertThat(result).contains("<<\"unit\">>");
            assertThat(result).contains("uri_string:compose_query");
            assertThat(result).contains("V =/= undefined");
        }

        @Test
        void renderQueryStringBuilderWithMultipleQueriesUsesComma() {
            List<QueryBinding> queries = List.of(
                    new QueryBinding("unit", "unit"),
                    new QueryBinding("format", "format")
            );
            String result = writer.renderQueryStringBuilder(queries, "Input");
            assertThat(result).contains("<<\"unit\">>,");
            assertThat(result).contains("<<\"format\">>");
        }

        @Test
        void renderHeaderBuilderEmitsContentTypeBase() {
            String result = writer.renderHeaderBuilder("application/json", List.of(), "Input");
            assertThat(result).isEqualTo(
                    "Headers = [{<<\"Content-Type\">>, <<\"application/json\">>}],\n");
        }

        @Test
        void renderHeaderBuilderWithRequiredHeaderAppendsHeader() {
            List<HeaderBinding> headers = List.of(
                    new HeaderBinding("authorization", "Authorization", true)
            );
            String result = writer.renderHeaderBuilder("application/json", headers, "Input");
            assertThat(result).contains("<<\"Authorization\">>");
            assertThat(result).contains("maps:get(<<\"authorization\">>, Input)");
        }

        @Test
        void renderHeaderBuilderWithOptionalHeaderUsesListComprehension() {
            List<HeaderBinding> headers = List.of(
                    new HeaderBinding("checksum", "x-checksum", false)
            );
            String result = writer.renderHeaderBuilder("application/json", headers, "Input");
            assertThat(result).contains("<<\"x-checksum\">>");
            assertThat(result).contains("maps:is_key(<<\"checksum\">>, Input)");
        }
    }

    // ── Auth and retry ────────────────────────────────────────────────────────

    @Nested
    class AuthAndRetry {
        @Test
        void renderAuthWrapperNoSigv4ReturnsInnerUnchanged() {
            assertThat(writer.renderAuthWrapper(AuthSpec.none(), "some_call()"))
                    .isEqualTo("some_call()");
        }

        @Test
        void renderAuthWrapperWithSigv4PrependsSigning() {
            String result = writer.renderAuthWrapper(new AuthSpec(true, "s3"), "inner_call()");
            assertThat(result).contains("smithy_sigv4:sign_request(");
            assertThat(result).contains("SignedHeaders");
            assertThat(result).contains("inner_call()");
        }

        @Test
        void renderRetryWrapperDisabledReturnsBareCall() {
            assertThat(writer.renderRetryWrapper(RetrySpec.disabled(), "Fun"))
                    .isEqualTo("Fun()");
        }

        @Test
        void renderRetryWrapperEnabledUsesAwsRetry() {
            String result = writer.renderRetryWrapper(RetrySpec.defaultRetry(), "RequestFun");
            assertThat(result).contains("smithy_retry:with_retry(RequestFun,");
            assertThat(result).contains("max_retries =>");
        }
    }

    // ── Response handler and error serializer ─────────────────────────────────

    @Nested
    class ResponseHandlerAndErrors {
        @Test
        void renderResponseHandlerEmitsDeserializeFunction() {
            OperationSpec op = makeGetWeatherOp(Role.CLIENT);
            String result = writer.renderResponseHandler(op, "Response");
            assertThat(result).startsWith("deserialize_get_weather(Response) ->");
        }

        @Test
        void renderResponseHandlerWithBodyMembersExtractsKeys() {
            BodySpec body = new BodySpec(BodyEncoding.JSON, List.of("temperature", "unit"), null);
            OperationSpec op = new OperationSpec(
                    "GetWeather", "WeatherService", Role.CLIENT,
                    new HttpSpec("GET", "/weather", 200),
                    List.of(), List.of(), List.of(),
                    body,
                    new ErrorSpec(List.of(), ErrorCodeStrategy.REST_JSON),
                    AuthSpec.none(), RetrySpec.disabled(), null,
                    "GetWeatherOutput", "GetWeatherInput",
                    BodyEncoding.JSON, "application/json", ErrorCodeStrategy.REST_JSON, null
            );
            String result = writer.renderResponseHandler(op, "Response");
            assertThat(result).contains("<<\"temperature\">>");
            assertThat(result).contains("<<\"unit\">>");
            assertThat(result).contains("maps:get(");
        }

        @Test
        void renderErrorSerializerProducesStringDispatch() {
            ErrorSpec errors = new ErrorSpec(
                    List.of(new ErrorBinding("NoSuchKey", 404, ErrorCodeStrategy.REST_XML)),
                    ErrorCodeStrategy.REST_XML
            );
            String result = writer.renderErrorSerializer(errors);
            assertThat(result).contains("-spec parse_error(binary(), map())");
            assertThat(result).contains("parse_error(<<\"NoSuchKey\">>, Body) ->");
            assertThat(result).contains("error_type => no_such_key");
            assertThat(result).contains("parse_error(_, Body) ->");
        }

        @Test
        void renderModuleParseErrorByStatusCode() {
            List<ErrorBinding> errors = List.of(
                    new ErrorBinding("NotFound", 404, ErrorCodeStrategy.REST_JSON)
            );
            String result = writer.renderModuleParseError(errors);
            assertThat(result).contains("-spec parse_error(integer(), binary())");
            assertThat(result).contains("parse_error(404, Body) ->");
            assertThat(result).contains("not_found");
            assertThat(result).contains("parse_error(StatusCode, Body) ->");
        }

        @Test
        void renderModuleParseErrorEmptyListProducesFallbackOnly() {
            String result = writer.renderModuleParseError(List.of());
            assertThat(result).contains("parse_error(StatusCode, Body) ->");
            assertThat(result).contains("{http_error, StatusCode, Body}");
            assertThat(result).doesNotContain("parse_error(404");
        }

        @Test
        void renderModuleParseErrorDeduplicatesByStatusCode() {
            List<ErrorBinding> errors = List.of(
                    new ErrorBinding("BadRequestA", 400, ErrorCodeStrategy.REST_JSON),
                    new ErrorBinding("BadRequestB", 400, ErrorCodeStrategy.REST_JSON)
            );
            String result = writer.renderModuleParseError(errors);
            // Only the first 400 clause should appear
            long count = result.lines()
                    .filter(line -> line.contains("parse_error(400,"))
                    .count();
            assertThat(count).isEqualTo(1);
        }

        @Test
        void renderModuleParseErrorRestXmlUsesStringDispatch() {
            List<ErrorBinding> errors = List.of(
                    new ErrorBinding("NoSuchBucket", 404, ErrorCodeStrategy.REST_XML)
            );
            String result = writer.renderModuleParseError(errors, ErrorCodeStrategy.REST_XML);
            assertThat(result).contains("parse_error(<<\"NoSuchBucket\">>, Body) ->");
            assertThat(result).contains("no_such_bucket");
            assertThat(result).contains("parse_error(_, Body) ->");
        }

        @Test
        void renderModuleParseErrorRestJsonUsesStatusCodeDispatch() {
            List<ErrorBinding> errors = List.of(
                    new ErrorBinding("NotFound", 404, ErrorCodeStrategy.REST_JSON)
            );
            String result = writer.renderModuleParseError(errors, ErrorCodeStrategy.REST_JSON);
            assertThat(result).contains("parse_error(404, Body) ->");
        }
    }

    // ── Pagination ────────────────────────────────────────────────────────────

    @Nested
    class Pagination {
        @Test
        void renderPaginationHelperEmitsStreamFunction() {
            PaginationSpec pagination = new PaginationSpec("NextToken", "NextPageToken", "Items", null);
            OperationSpec op = new OperationSpec(
                    "ListItems", "ItemService", Role.CLIENT,
                    new HttpSpec("GET", "/items", 200),
                    List.of(), List.of(), List.of(),
                    null,
                    new ErrorSpec(List.of(), ErrorCodeStrategy.REST_JSON),
                    AuthSpec.none(), RetrySpec.disabled(), null,
                    "ListItemsOutput", "ListItemsInput",
                    BodyEncoding.JSON, "application/json", ErrorCodeStrategy.REST_JSON, null
            );
            String result = writer.renderPaginationHelper(op, pagination);
            assertThat(result).contains("list_items_stream(Input, Config) ->");
            assertThat(result).contains("list_items_stream(Input, Config, [])");
            assertThat(result).contains("list_items_stream(Input, Config, Acc) ->");
            assertThat(result).contains("<<\"Items\">>");
            assertThat(result).contains("<<\"NextPageToken\">>");
            assertThat(result).contains("<<\"NextToken\">> => NextToken");
        }
    }

    // ── Codec helpers ─────────────────────────────────────────────────────────

    @Nested
    class CodecHelpers {
        @Test
        void renderEnumCodecEmitsEncodeAndDecodeFunctions() {
            EnumSpec e = new EnumSpec("TemperatureUnit", List.of("CELSIUS", "FAHRENHEIT"));
            String result = writer.renderEnumCodec(e);
            assertThat(result).contains("-spec encode_temperature_unit(temperature_unit()) -> binary().");
            assertThat(result).contains("encode_temperature_unit(celsius) -> <<\"CELSIUS\">>");
            assertThat(result).contains("encode_temperature_unit(fahrenheit) -> <<\"FAHRENHEIT\">>");
            assertThat(result).contains("-spec decode_temperature_unit(binary())");
            assertThat(result).contains("decode_temperature_unit(<<\"CELSIUS\">>) -> {ok, celsius}");
            assertThat(result).contains("decode_temperature_unit(<<\"FAHRENHEIT\">>) -> {ok, fahrenheit}");
            assertThat(result).contains("decode_temperature_unit(Other) -> {error, {invalid_enum_value, Other}}");
        }

        @Test
        void renderUnionCodecEmitsEncodeAndDecodeFunctions() {
            UnionSpec u = new UnionSpec("MyUnion", List.of(
                    new FieldSpec("left", new TypeRef.Primitive(PrimitiveKind.STRING), false, false, false, false, false),
                    new FieldSpec("right", new TypeRef.Primitive(PrimitiveKind.INTEGER), false, false, false, false, false)
            ));
            String result = writer.renderUnionCodec(u);
            assertThat(result).contains("encode_my_union({left, Value}) ->");
            assertThat(result).contains("<<\"left\">> => Value");
            assertThat(result).contains("encode_my_union({right, Value}) ->");
            assertThat(result).contains("encode_my_union({unknown, Value}) ->");
            assertThat(result).contains("decode_my_union(Map) when is_map(Map) ->");
            assertThat(result).contains("maps:find(<<\"left\">>, Map)");
            assertThat(result).contains("{unknown, Map}");
        }

        @Test
        void renderValidateHelperEmitsRequiredFieldCheck() {
            StructSpec s = new StructSpec("GetWeatherInput", List.of(
                    new FieldSpec("city", new TypeRef.Primitive(PrimitiveKind.STRING), true, false, false, false, false),
                    new FieldSpec("unit", new TypeRef.Named("TemperatureUnit"), false, false, false, false, false)
            ), false, 0);
            String result = writer.renderValidateHelper(s);
            assertThat(result).contains("validate_get_weather_input(Input) ->");
            assertThat(result).contains("-spec validate_get_weather_input(map())");
            assertThat(result).contains("<<\"city\">>");
            assertThat(result).contains("missing_required_fields");
        }

        @Test
        void renderValidateHelperReturnsEmptyWhenNoRequiredFields() {
            StructSpec s = new StructSpec("GetWeatherOutput", List.of(
                    new FieldSpec("temperature", new TypeRef.Primitive(PrimitiveKind.FLOAT), false, false, false, false, false)
            ), false, 0);
            assertThat(writer.renderValidateHelper(s)).isEmpty();
        }
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    @Nested
    class SharedHelpers {
        @Test
        void renderSharedHelpersIncludesUrlEncode() {
            String result = writer.renderSharedHelpers();
            assertThat(result).contains("url_encode(Binary) when is_binary(Binary) ->");
            assertThat(result).contains("uri_string:quote(");
        }

        @Test
        void renderSharedHelpersIncludesEnsureBinary() {
            assertThat(writer.renderSharedHelpers()).contains("ensure_binary(");
        }
    }

    // ── Client constructor and operation ──────────────────────────────────────

    @Nested
    class ClientCode {
        @Test
        void renderClientConstructorEmitsNewFunction() {
            String result = writer.renderClientConstructor();
            assertThat(result).contains("-spec new(Config :: map()) -> {ok, map()}.");
            assertThat(result).contains("new(Config) ->");
            assertThat(result).contains("{ok, Config}");
        }

        @Test
        void renderClientOperationEmitsPublicFunctions() {
            OperationSpec op = makeGetWeatherOp(Role.CLIENT);
            String result = writer.renderClientOperation(op);
            // 2-arity public entry
            assertThat(result).contains("get_weather(Client, Input) ->");
            // 3-arity public with options
            assertThat(result).contains("get_weather(Client, Input, Options)");
            // internal builder function
            assertThat(result).contains("make_get_weather_request(Client, Input)");
            // comment
            assertThat(result).contains("%% Calls the GetWeather operation");
            // method binding
            assertThat(result).contains("Method = <<\"GET\">>");
        }

        @Test
        void renderClientOperationUsesRetryWhenEnabled() {
            OperationSpec op = makeGetWeatherOp(Role.CLIENT);
            String result = writer.renderClientOperation(op);
            assertThat(result).contains("smithy_retry:with_retry");
        }
    }

    // ── Runtime modules ───────────────────────────────────────────────────────

    @Nested
    class RuntimeModules {
        @Test
        void serverRuntimeModulesIncludesSmithyFiles() {
            List<String> modules = writer.serverRuntimeModules();
            assertThat(modules).anyMatch(m -> m.contains("smithy_server.erl"));
            assertThat(modules).anyMatch(m -> m.contains("smithy_validator.erl"));
            assertThat(modules).anyMatch(m -> m.contains("smithy_error_map.erl"));
        }

        @Test
        void clientRuntimeModulesAlwaysIncludesRetryAndConfig() {
            List<String> modules = writer.clientRuntimeModules(false, false, false, false);
            assertThat(modules).anyMatch(m -> m.contains("smithy_retry.erl"));
            assertThat(modules).anyMatch(m -> m.contains("smithy_config.erl"));
        }

        @Test
        void clientRuntimeModulesIncludesSigv4WhenRequired() {
            List<String> modules = writer.clientRuntimeModules(true, false, false, false);
            assertThat(modules).anyMatch(m -> m.contains("smithy_sigv4.erl"));
            assertThat(modules).anyMatch(m -> m.contains("smithy_credentials.erl"));
        }

        @Test
        void clientRuntimeModulesIncludesXmlWhenRequired() {
            List<String> modules = writer.clientRuntimeModules(false, true, false, false);
            assertThat(modules).anyMatch(m -> m.contains("smithy_xml.erl"));
        }

        @Test
        void clientRuntimeModulesIncludesQueryWhenRequired() {
            List<String> modules = writer.clientRuntimeModules(false, false, true, false);
            assertThat(modules).anyMatch(m -> m.contains("smithy_query.erl"));
        }

        @Test
        void clientRuntimeModulesIncludesS3WhenRequired() {
            List<String> modules = writer.clientRuntimeModules(false, false, false, true);
            assertThat(modules).anyMatch(m -> m.contains("smithy_s3.erl"));
        }
    }

    // ── Server patterns ───────────────────────────────────────────────────────

    @Nested
    class ServerPatterns {
        @Test
        void renderServerCallbackDeclarationEmitsCallbackWithTypes() {
            OperationSpec op = makeGetWeatherOp(Role.SERVER);
            String result = writer.renderServerCallbackDeclaration(op);
            assertThat(result).startsWith("-callback get_weather(");
            assertThat(result).contains("Input :: get_weather_input()");
            assertThat(result).contains("Context :: map()");
            assertThat(result).contains("{ok, get_weather_output()} | {error, term()}");
        }

        @Test
        void renderServerRouteClauseWithLabelsUsesPrefix() {
            OperationSpec op = makeGetWeatherOp(Role.SERVER);
            String result = writer.renderServerRouteClause(op);
            assertThat(result).contains("route(<<\"GET\">>, <<\"/weather/");
            assertThat(result).contains("get_weather");
        }

        @Test
        void renderServerRouteClauseWithoutLabels() {
            OperationSpec op = new OperationSpec(
                    "ListWeather", "WeatherService", Role.SERVER,
                    new HttpSpec("GET", "/weather", 200),
                    List.of(), List.of(), List.of(),
                    null,
                    new ErrorSpec(List.of(), ErrorCodeStrategy.REST_JSON),
                    AuthSpec.none(), RetrySpec.disabled(), null,
                    "ListWeatherOutput", "ListWeatherInput",
                    BodyEncoding.JSON, "application/json", ErrorCodeStrategy.REST_JSON, null
            );
            String result = writer.renderServerRouteClause(op);
            assertThat(result).contains("route(<<\"GET\">>, <<\"/weather\">>)");
            assertThat(result).contains("list_weather");
        }

        @Test
        void renderServerRouteFallbackEmitsCatchAll() {
            assertThat(writer.renderServerRouteFallback())
                    .isEqualTo("route(_, _) -> {error, not_found}.\n");
        }

        @Test
        void renderServerDeserializeWithLabelExtractsPathSegment() {
            OperationSpec op = makeGetWeatherOp(Role.SERVER);
            String result = writer.renderServerDeserialize(op);
            assertThat(result).startsWith("deserialize_get_weather(Path, _Headers, Body) ->");
            assertThat(result).contains("<<\"/weather/\"");
            assertThat(result).contains("/binary>> = Path");
        }

        @Test
        void renderServerDeserializeWithNoLabelsAndNoBody() {
            OperationSpec op = new OperationSpec(
                    "Ping", "PingService", Role.SERVER,
                    new HttpSpec("GET", "/ping", 200),
                    List.of(), List.of(), List.of(),
                    null,
                    new ErrorSpec(List.of(), ErrorCodeStrategy.REST_JSON),
                    AuthSpec.none(), RetrySpec.disabled(), null,
                    "PingOutput", "PingInput",
                    BodyEncoding.JSON, "application/json", ErrorCodeStrategy.REST_JSON, null
            );
            String result = writer.renderServerDeserialize(op);
            assertThat(result).contains("deserialize_ping(Path, _Headers, Body) ->");
            assertThat(result).contains("#{}.");
        }

        @Test
        void renderServerSerializeUsesJsxEncode() {
            OperationSpec op = makeGetWeatherOp(Role.SERVER);
            String result = writer.renderServerSerialize(op);
            assertThat(result).isEqualTo(
                    "serialize_get_weather(Output) ->\n    jsx:encode(Output).\n");
        }

        @Test
        void renderServerImplStubEmitsNotImplementedStub() {
            OperationSpec op = makeGetWeatherOp(Role.SERVER);
            String result = writer.renderServerImplStub(op, "weather_service_handler");
            assertThat(result).contains("-spec get_weather(weather_service_handler:get_weather_input(), map())");
            assertThat(result).contains("{ok, weather_service_handler:get_weather_output()} | {error, term()}");
            assertThat(result).contains("get_weather(_Input, _Context) ->");
            assertThat(result).contains("{error, not_implemented}");
        }

        @Test
        void renderServerCowboyHandlerEmitsCowboyModule() {
            String result = writer.renderServerCowboyHandler("weather_service");
            assertThat(result).contains("-module(weather_service_cowboy).");
            assertThat(result).contains("-behaviour(cowboy_handler).");
            assertThat(result).contains("-export([init/2]).");
            assertThat(result).contains("weather_service_dispatcher:handle(weather_service_impl, Req0, #{})");
            assertThat(result).contains("cowboy_req:reply(Code, maps:from_list(Headers), Body, Req0)");
        }

        @Test
        void renderServerDispatchClauseEmitsDispatchFunction() {
            OperationSpec op = makeGetWeatherOp(Role.SERVER);
            String result = writer.renderServerDispatchClause(op);
            assertThat(result).contains("dispatch_get_weather(Impl, Path, Headers, Body, Context) ->");
            assertThat(result).contains("deserialize_get_weather(Path, Headers, Body)");
            assertThat(result).contains("Impl:get_weather(Input, Context)");
            assertThat(result).contains("smithy_server:response(200");
            assertThat(result).contains("smithy_server:error_response(Err)");
        }

        @Test
        void renderServerModuleEmitsCompleteModule() {
            OperationSpec op = makeGetWeatherOp(Role.SERVER);
            ModuleTypeSpec types = new ModuleTypeSpec(
                    "WeatherService", List.of(), List.of(), List.of(), List.of(), false);
            String result = writer.renderServerModule("weather_service", List.of(op), types);

            assertThat(result).contains("-module(weather_service_server).");
            assertThat(result).contains("-behaviour(cowboy_handler).");
            assertThat(result).contains("-export([");
            assertThat(result).contains("init/2");
            assertThat(result).contains("handle/3");
            assertThat(result).contains("route/2");
            assertThat(result).contains("-callback get_weather(");
            assertThat(result).contains("route(<<\"GET\">>");
            assertThat(result).contains("route(_, _) -> {error, not_found}.");
            assertThat(result).contains("dispatch_get_weather(");
            assertThat(result).contains("deserialize_get_weather(");
            assertThat(result).contains("serialize_get_weather(");
        }
    }

    // ── toErlangAtom (package-private static helper) ──────────────────────────

    @Nested
    class ToErlangAtom {
        @Test
        void simpleUppercaseBecomesLowercase() {
            assertThat(ErlangWriter.toErlangAtom("CELSIUS")).isEqualTo("celsius");
            assertThat(ErlangWriter.toErlangAtom("FAHRENHEIT")).isEqualTo("fahrenheit");
        }

        @Test
        void mixedCaseBecomesLowercase() {
            assertThat(ErlangWriter.toErlangAtom("Enabled")).isEqualTo("enabled");
        }

        @Test
        void reservedWordGetsQuoted() {
            // "end", "case", "if" are Erlang reserved words
            assertThat(ErlangWriter.toErlangAtom("END")).isEqualTo("'end'");
            assertThat(ErlangWriter.toErlangAtom("CASE")).isEqualTo("'case'");
        }

        @Test
        void hyphenBecomesUnderscore() {
            assertThat(ErlangWriter.toErlangAtom("us-east-1")).isEqualTo("us_east_1");
        }

        @Test
        void valueStartingWithDigitGetsQuoted() {
            assertThat(ErlangWriter.toErlangAtom("1CLICK")).isEqualTo("'1click'");
        }
    }

    // ── Shared factory ────────────────────────────────────────────────────────

    private OperationSpec makeGetWeatherOp(Role role) {
        return new OperationSpec(
                "GetWeather", "WeatherService", role,
                new HttpSpec("GET", "/weather/{city}", 200),
                List.of(new LabelBinding("city", "city", false)),
                List.of(), List.of(),
                null,
                new ErrorSpec(List.of(), ErrorCodeStrategy.REST_JSON),
                AuthSpec.none(), RetrySpec.disabled(), null,
                "GetWeatherOutput", "GetWeatherInput",
                BodyEncoding.JSON, "application/json", ErrorCodeStrategy.REST_JSON, null
        );
    }
}
