package io.smithy.beam.elixir.writer;

import io.smithy.beam.core.ir.AuthSpec;
import io.smithy.beam.core.ir.BodyEncoding;
import io.smithy.beam.core.ir.BodySpec;
import io.smithy.beam.core.ir.EnumSpec;
import io.smithy.beam.core.ir.ErrorBinding;
import io.smithy.beam.core.ir.ErrorCodeStrategy;
import io.smithy.beam.core.ir.ErrorSpec;
import io.smithy.beam.core.ir.FieldSpec;
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

class ElixirWriterTest {

    private ElixirWriter writer;

    @BeforeEach
    void setUp() {
        writer = new ElixirWriter();
    }

    // ── Identity ──────────────────────────────────────────────────────────────

    @Nested
    class Identity {
        @Test
        void languageId() {
            assertThat(writer.languageId()).isEqualTo("elixir");
        }

        @Test
        void fileExtension() {
            assertThat(writer.fileExtension()).isEqualTo(".ex");
        }
    }

    // ── Naming ────────────────────────────────────────────────────────────────

    @Nested
    class Naming {
        @Test
        void moduleNamePreservesPascalCase() {
            assertThat(writer.moduleName("WeatherService")).isEqualTo("WeatherService");
        }

        @Test
        void functionNameConvertsToSnakeCase() {
            assertThat(writer.functionName("GetWeather")).isEqualTo("get_weather");
        }

        @Test
        void typeNameUsesModuleDotT() {
            assertThat(writer.typeName("GetWeatherInput")).isEqualTo("GetWeatherInput.t()");
        }

        @Test
        void varNameConvertsToSnakeCase() {
            assertThat(writer.varName("cityId")).isEqualTo("city_id");
        }

        @Test
        void mapKeyProducesAtom() {
            assertThat(writer.mapKey("cityId")).isEqualTo(":city_id");
            assertThat(writer.mapKey("city")).isEqualTo(":city");
        }
    }

    // ── Module structure ──────────────────────────────────────────────────────

    @Nested
    class ModuleStructure {
        @Test
        void moduleHeaderEmitsDefmodule() {
            assertThat(writer.moduleHeader("WeatherService"))
                    .isEqualTo("defmodule WeatherService do\n");
        }

        @Test
        void moduleFooterEmitsEnd() {
            assertThat(writer.moduleFooter()).isEqualTo("end\n");
        }

        @Test
        void moduleCommentEmitsModuledoc() {
            String result = writer.renderModuleComment("Generated Smithy client for WeatherService.");
            assertThat(result).contains("@moduledoc");
            assertThat(result).contains("Generated Smithy client for WeatherService.");
        }

        @Test
        void exportSectionIsEmpty() {
            // Elixir has no explicit export — all def functions are public
            assertThat(writer.exportSection(List.of(new ExportSpec("get_weather", 1)))).isEmpty();
        }

        @Test
        void behaviourDeclarationEmitsAttribute() {
            String result = writer.behaviourDeclaration("WeatherServiceHandler");
            assertThat(result).contains("@behaviour");
            assertThat(result).contains("WeatherServiceHandler");
        }

        @Test
        void renderToolingAttributesIsEmpty() {
            assertThat(writer.renderToolingAttributes()).isEmpty();
        }

        @Test
        void exportTypesIsEmpty() {
            // Elixir exports types automatically
            assertThat(writer.exportTypes(List.of("get_weather_input()"))).isEmpty();
        }
    }

    // ── Type rendering ────────────────────────────────────────────────────────

    @Nested
    class TypeRendering {
        @Test
        void renderStructTypeEmitsAtType() {
            StructSpec struct = new StructSpec("GetWeatherInput", List.of(
                    new FieldSpec("city", new TypeRef.Primitive(PrimitiveKind.STRING), true, true, false, false, false),
                    new FieldSpec("unit", new TypeRef.Named("TemperatureUnit"), false, false, false, false, false)
            ), false, 0);

            String result = writer.renderStructType(struct);
            assertThat(result).contains("@type get_weather_input");
            assertThat(result).contains("city: String.t()");
            assertThat(result).contains("unit: temperature_unit() | nil");
        }

        @Test
        void renderStructTypeHandlesEmptyStruct() {
            StructSpec empty = new StructSpec("EmptyInput", List.of(), false, 0);
            assertThat(writer.renderStructType(empty)).isEqualTo("  @type empty_input :: %{}\n");
        }

        @Test
        void renderEnumTypeEmitsAtoms() {
            EnumSpec e = new EnumSpec("TemperatureUnit", List.of("CELSIUS", "FAHRENHEIT"));
            String result = writer.renderEnumType(e);
            assertThat(result).contains("@type temperature_unit");
            assertThat(result).contains(":celsius");
            assertThat(result).contains(":fahrenheit");
            assertThat(result).contains("|");
        }

        @Test
        void renderUnionTypeEmitsTaggedTuples() {
            UnionSpec u = new UnionSpec("Result", List.of(
                    new FieldSpec("success", new TypeRef.Named("SuccessOutput"), false, false, false, false, false),
                    new FieldSpec("failure", new TypeRef.Named("FailureOutput"), false, false, false, false, false)
            ));
            String result = writer.renderUnionType(u);
            assertThat(result).contains("@type result");
            assertThat(result).contains("{:success, success_output()}");
            assertThat(result).contains("{:failure, failure_output()}");
        }

        @Test
        void renderCallbackDeclarationEmitsCallback() {
            List<ParamSpec> params = List.of(
                    new ParamSpec("input", new TypeRef.Primitive(PrimitiveKind.STRING)),
                    new ParamSpec("ctx",   new TypeRef.MapOf(new TypeRef.Primitive(PrimitiveKind.STRING), new TypeRef.Primitive(PrimitiveKind.STRING)))
            );
            TypeRef returnType = new TypeRef.Named("GetWeatherOutput");
            String result = writer.renderCallbackDeclaration("GetWeather", params, returnType);
            assertThat(result).contains("@callback get_weather(");
            assertThat(result).contains("input ::");
            assertThat(result).contains("ctx ::");
            assertThat(result).contains("::");
        }

        @Test
        void renderFunctionSpecEmitsSpec() {
            List<ParamSpec> params = List.of(
                    new ParamSpec("input", new TypeRef.Primitive(PrimitiveKind.STRING))
            );
            TypeRef returnType = new TypeRef.Named("GetWeatherOutput");
            String result = writer.renderFunctionSpec("GetWeather", params, returnType);
            assertThat(result).contains("@spec get_weather(");
            assertThat(result).contains("::");
        }

        @Test
        void renderUnionTypeEscapesBuiltinTypeName() {
            UnionSpec u = new UnionSpec("Map", List.of(
                    new FieldSpec("ok", new TypeRef.Named("OkOutput"), false, false, false, false, false),
                    new FieldSpec("err", new TypeRef.Named("ErrOutput"), false, false, false, false, false)
            ));
            String result = writer.renderUnionType(u);
            assertThat(result).contains("@type map_t");
            assertThat(result).doesNotContain("@type map ");
        }

        @Test
        void renderStructTypeEscapesBuiltinTypeNameInFieldRef() {
            StructSpec struct = new StructSpec("GetWeatherInput", List.of(
                    new FieldSpec("region", new TypeRef.Named("Node"), false, false, false, false, false)
            ), false, 0);
            String result = writer.renderStructType(struct);
            assertThat(result).contains("region: node_t() | nil");
            assertThat(result).doesNotContain("region: node() | nil");
        }

        @Test
        void renderCallbackDeclarationEscapesBuiltinReturnType() {
            List<ParamSpec> params = List.of(
                    new ParamSpec("input", new TypeRef.Primitive(PrimitiveKind.STRING))
            );
            TypeRef returnType = new TypeRef.Named("Map");
            String result = writer.renderCallbackDeclaration("GetMap", params, returnType);
            assertThat(result).contains(":: map_t()");
            assertThat(result).doesNotContain(":: map()");
        }
    }

    // ── Function body helpers ─────────────────────────────────────────────────

    @Nested
    class FunctionBody {
        @Test
        void renderFunctionHeadEmitsDef() {
            String result = writer.renderFunctionHead("GetWeather", List.of("input", "ctx"));
            assertThat(result).isEqualTo("  def get_weather(input, ctx) do");
        }

        @Test
        void renderFunctionEndEmitsEnd() {
            assertThat(writer.renderFunctionEnd()).isEqualTo("  end");
        }

        @Test
        void renderMapGetUsesAtomKey() {
            String result = writer.renderMapGet("input", "cityId", "nil");
            assertThat(result).isEqualTo("Map.get(input, :city_id, nil)");
        }

        @Test
        void renderMapBuildUsesAtomKeys() {
            List<MapEntrySpec> entries = List.of(
                    new MapEntrySpec("city", "city"),
                    new MapEntrySpec("unit", "unit")
            );
            String result = writer.renderMapBuild(entries);
            assertThat(result).isEqualTo("%{city: city, unit: unit}");
        }

        @Test
        void renderMapBuildHandlesEmpty() {
            assertThat(writer.renderMapBuild(List.of())).isEqualTo("%{}");
        }
    }

    // ── JSON helpers ──────────────────────────────────────────────────────────

    @Nested
    class JsonHelpers {
        @Test
        void renderJsonEncodeUsesJason() {
            assertThat(writer.renderJsonEncode("body_map")).isEqualTo("Jason.encode!(body_map)");
        }

        @Test
        void renderJsonDecodeUsesJason() {
            assertThat(writer.renderJsonDecode("response_body")).isEqualTo("Jason.decode!(response_body)");
        }

        @Test
        void jsonEncodeCallUsesJason() {
            assertThat(writer.jsonEncodeCall("my_map")).isEqualTo("Jason.encode!(my_map)");
        }

        @Test
        void jsonDecodeCallUsesJason() {
            assertThat(writer.jsonDecodeCall("raw")).isEqualTo("Jason.decode!(raw)");
        }
    }

    // ── URI and headers ───────────────────────────────────────────────────────

    @Nested
    class UriAndHeaders {
        @Test
        void renderUriSubstitutionWithNoLabels() {
            String result = writer.renderUriSubstitution("/weather", List.of(), "input");
            assertThat(result).isEqualTo("\"/weather\"");
        }

        @Test
        void renderUriSubstitutionInterpolatesLabels() {
            List<LabelBinding> labels = List.of(new LabelBinding("city", "city", false));
            String result = writer.renderUriSubstitution("/weather/{city}", labels, "input");
            assertThat(result).contains("#{");
            assertThat(result).contains(":city");
            assertThat(result).startsWith("\"/weather/");
        }

        @Test
        void renderUriSubstitutionEncodesWhenRequired() {
            List<LabelBinding> labels = List.of(new LabelBinding("key", "key", true));
            String result = writer.renderUriSubstitution("/bucket/{key}", labels, "input");
            assertThat(result).contains("URI.encode_www_form");
        }

        @Test
        void renderQueryStringBuilderWithNoQueries() {
            String result = writer.renderQueryStringBuilder(List.of(), "input");
            assertThat(result).contains("query_string = \"\"");
        }

        @Test
        void renderQueryStringBuilderBuildsParams() {
            List<QueryBinding> queries = List.of(new QueryBinding("unit", "unit"));
            String result = writer.renderQueryStringBuilder(queries, "input");
            assertThat(result).contains("query_params");
            assertThat(result).contains("unit");
            assertThat(result).contains("URI.encode_query");
        }

        @Test
        void renderHeaderBuilderEmitsContentType() {
            String result = writer.renderHeaderBuilder("application/json", List.of(), "input");
            assertThat(result).contains("headers = [{\"Content-Type\", \"application/json\"}]");
        }
    }

    // ── HTTP client block (operations-as-values) ──────────────────────────────

    @Nested
    class HttpClientBlock {

        private OperationSpec makeGetWeatherOp() {
            return new OperationSpec(
                "GetWeather", "WeatherService", Role.CLIENT,
                new HttpSpec("GET", "/weather/{city}", 200),
                List.of(new LabelBinding("city", "city", false)),
                List.of(), List.of(),
                null,
                new ErrorSpec(List.of(), ErrorCodeStrategy.REST_JSON),
                AuthSpec.none(), RetrySpec.disabled(), null,
                "GetWeatherOutput", "GetWeatherInput",
                BodyEncoding.JSON, "application/json", ErrorCodeStrategy.REST_JSON, null,
                null, null, List.of()
            );
        }

        @Test
        void renderHttpClientBlockEmitsOperationStruct() {
            OperationSpec op = makeGetWeatherOp();
            String result = writer.renderHttpClientBlock(op, "url", "headers", "body", "method");
            assertThat(result).contains("%SmithyClient.Operation{");
            assertThat(result).contains("name: :get_weather");
            assertThat(result).contains("http: %{method: \"GET\", uri: \"/weather/{city}\"}");
            assertThat(result).contains("input: input");
            assertThat(result).contains("output_shape: :get_weather_output");
            assertThat(result).contains("auth: :none");
        }

        @Test
        void renderHttpClientBlockSetsSigv4AuthWhenRequired() {
            OperationSpec op = new OperationSpec(
                "ListBuckets", "S3", Role.CLIENT,
                new HttpSpec("GET", "/", 200),
                List.of(), List.of(), List.of(),
                null,
                new ErrorSpec(List.of(), ErrorCodeStrategy.REST_XML),
                new AuthSpec(true, "s3"), RetrySpec.disabled(), null,
                "ListBucketsOutput", "ListBucketsInput",
                BodyEncoding.XML, "application/xml", ErrorCodeStrategy.REST_XML, null,
                null, null, List.of()
            );
            String result = writer.renderHttpClientBlock(op, "url", "headers", "body", "method");
            assertThat(result).contains("auth: :sigv4");
        }

        @Test
        void renderClientOperationEmitsDefFunction() {
            OperationSpec op = makeGetWeatherOp();
            String result = writer.renderClientOperation(op);
            assertThat(result).contains("def get_weather(client, input, opts \\\\ %{})");
            assertThat(result).contains("SmithyClient.request(client, get_weather_op(input), opts)");
            assertThat(result).contains("defp get_weather_op(input)");
            assertThat(result).contains("%SmithyClient.Operation{");
            assertThat(result).contains("name: :get_weather");
            assertThat(result).contains("@doc \"Calls the GetWeather operation\"");
            assertThat(result).contains("@spec get_weather(map(), map(), map()) :: {:ok, map()} | {:error, term()}");
        }

        @Test
        void renderClientOperationIncludesPaginationWhenPresent() {
            PaginationSpec pagination = new PaginationSpec("NextToken", "NextPageToken", "Items", null);
            OperationSpec op = new OperationSpec(
                "ListItems", "ItemService", Role.CLIENT,
                new HttpSpec("GET", "/items", 200),
                List.of(), List.of(), List.of(),
                null,
                new ErrorSpec(List.of(), ErrorCodeStrategy.REST_JSON),
                AuthSpec.none(), RetrySpec.disabled(), pagination,
                "ListItemsOutput", "ListItemsInput",
                BodyEncoding.JSON, "application/json", ErrorCodeStrategy.REST_JSON, null,
                null, null, List.of()
            );
            String result = writer.renderClientOperation(op);
            assertThat(result).contains("list_items_stream");
            assertThat(result).contains("SmithyClient.stream");
        }

        @Test
        void renderClientConstructorEmitsNewFunction() {
            String result = writer.renderClientConstructor();
            assertThat(result).contains("def new(config)");
            assertThat(result).contains("{:ok, config}");
            assertThat(result).contains("@spec new(map())");
        }
    }

    // ── Pagination ────────────────────────────────────────────────────────────

    @Nested
    class Pagination {
        @Test
        void renderPaginationHelperEmitsStreamFunction() {
            OperationSpec op = new OperationSpec(
                "ListItems", "ItemService", Role.CLIENT,
                new HttpSpec("GET", "/items", 200),
                List.of(), List.of(), List.of(),
                null,
                new ErrorSpec(List.of(), ErrorCodeStrategy.REST_JSON),
                AuthSpec.none(), RetrySpec.disabled(), null,
                "ListItemsOutput", "ListItemsInput",
                BodyEncoding.JSON, "application/json", ErrorCodeStrategy.REST_JSON, null,
                null, null, List.of()
            );
            PaginationSpec pagination = new PaginationSpec("NextToken", "NextPageToken", "Items", null);
            String result = writer.renderPaginationHelper(op, pagination);
            assertThat(result).contains("def list_items_stream(client, input, opts \\\\ %{})");
            assertThat(result).contains("SmithyClient.stream(client, list_items_op(input), opts)");
        }
    }

    // ── Codec helpers ─────────────────────────────────────────────────────────

    @Nested
    class CodecHelpers {
        @Test
        void renderEnumCodecEmitsEncodeAndDecodeFunctions() {
            EnumSpec e = new EnumSpec("TemperatureUnit", List.of("CELSIUS", "FAHRENHEIT"));
            String result = writer.renderEnumCodec(e);
            assertThat(result).contains("def encode_temperature_unit(:celsius)");
            assertThat(result).contains("def encode_temperature_unit(:fahrenheit)");
            assertThat(result).contains("def decode_temperature_unit(\"CELSIUS\")");
            assertThat(result).contains("def decode_temperature_unit(\"FAHRENHEIT\")");
            assertThat(result).contains("{:ok, :celsius}");
            assertThat(result).contains("{:error, {:invalid_enum_value, other}}");
        }

        @Test
        void renderEnumCodecReturnsEmptyStringForEmptyEnum() {
            EnumSpec empty = new EnumSpec("EmptyStatus", List.of());
            String result = writer.renderEnumCodec(empty);
            assertThat(result).isEmpty();
        }

        @Test
        void renderUnionCodecEmitsEncodeAndDecodeFunctions() {
            UnionSpec u = new UnionSpec("MyUnion", List.of(
                    new FieldSpec("left", new TypeRef.Primitive(PrimitiveKind.STRING), false, false, false, false, false),
                    new FieldSpec("right", new TypeRef.Primitive(PrimitiveKind.INTEGER), false, false, false, false, false)
            ));
            String result = writer.renderUnionCodec(u);
            assertThat(result).contains("def encode_my_union({:left, value})");
            assertThat(result).contains("def encode_my_union({:right, value})");
            assertThat(result).contains("def decode_my_union(map)");
            assertThat(result).contains("Map.has_key?(map, \"left\")");
            assertThat(result).contains("{:unknown, map}");
        }

        @Test
        void renderValidateHelperEmitsRequiredFieldCheck() {
            StructSpec s = new StructSpec("GetWeatherInput", List.of(
                    new FieldSpec("city", new TypeRef.Primitive(PrimitiveKind.STRING), true, false, false, false, false),
                    new FieldSpec("unit", new TypeRef.Named("TemperatureUnit"), false, false, false, false, false)
            ), false, 0);
            String result = writer.renderValidateHelper(s);
            assertThat(result).contains("validate_get_weather_input");
            assertThat(result).contains(":city");
            assertThat(result).contains("{:error, {:missing_required_fields, missing}}");
        }

        @Test
        void renderValidateHelperReturnsEmptyForNoRequiredFields() {
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
        void renderSharedHelpersReturnsEmpty() {
            // Elixir clients delegate all HTTP mechanics to SmithyClient, so no
            // shared helper functions need to be emitted in the generated module.
            assertThat(writer.renderSharedHelpers()).isEmpty();
        }
    }

    // ── Error serializer ──────────────────────────────────────────────────────

    @Nested
    class ErrorSerializer {
        @Test
        void renderErrorSerializerEmitsPatternMatchFunctions() {
            ErrorSpec errors = new ErrorSpec(
                    List.of(new ErrorBinding("NoSuchKey", 404, ErrorCodeStrategy.REST_XML)),
                    ErrorCodeStrategy.REST_XML
            );
            String result = writer.renderErrorSerializer(errors);
            assertThat(result).contains("defp parse_error(\"NoSuchKey\", body)");
            assertThat(result).contains(":no_such_key");
            assertThat(result).contains("defp parse_error(_, body)");
        }

        @Test
        void renderModuleParseErrorByStatusCode() {
            List<ErrorBinding> errors = List.of(
                    new ErrorBinding("NotFound", 404, ErrorCodeStrategy.REST_JSON)
            );
            String result = writer.renderModuleParseError(errors);
            assertThat(result).contains("defp parse_error(404, body)");
            assertThat(result).contains(":not_found");
            assertThat(result).contains("defp parse_error(status_code, body)");
        }

        @Test
        void renderModuleParseErrorStringDispatchForRestXml() {
            List<ErrorBinding> errors = List.of(
                    new ErrorBinding("NoSuchBucket", 404, ErrorCodeStrategy.REST_XML)
            );
            String result = writer.renderModuleParseError(errors, ErrorCodeStrategy.REST_XML);
            assertThat(result).contains("defp parse_error(\"NoSuchBucket\", body)");
            assertThat(result).contains(":no_such_bucket");
            assertThat(result).contains("defp parse_error(_, body)");
        }

        @Test
        void renderModuleParseErrorEmptyListProducesFallback() {
            String result = writer.renderModuleParseError(List.of());
            assertThat(result).contains("defp parse_error(status_code, body)");
            assertThat(result).doesNotContain("defp parse_error(404");
        }

        @Test
        void renderModuleParseErrorAmbiguousCodeEmitsCaseDiscriminator() {
            List<ErrorBinding> errors = List.of(
                    new ErrorBinding("BadRequestA", 400, ErrorCodeStrategy.REST_JSON),
                    new ErrorBinding("BadRequestB", 400, ErrorCodeStrategy.REST_JSON)
            );
            String result = writer.renderModuleParseError(errors);
            // A single parse_error(400, ...) function head wrapping a case expression.
            long headCount = result.lines()
                    .filter(line -> line.contains("defp parse_error(400,"))
                    .count();
            assertThat(headCount).isEqualTo(1);
            // Both error names must appear as branches inside the case.
            assertThat(result).contains("\"BadRequestA\"");
            assertThat(result).contains("\"BadRequestB\"");
            assertThat(result).contains(":bad_request_a");
            assertThat(result).contains(":bad_request_b");
            // The discriminator must use __type / code fields.
            assertThat(result).contains("__type");
            assertThat(result).contains("\"code\"");
            // A TODO comment must mark the ambiguous fallback.
            assertThat(result).contains("# TODO: ambiguous error code 400");
            // Fallback clause returns the first error.
            long fallbackCount = result.lines()
                    .filter(line -> line.contains("_ -> {:error, {:bad_request_a"))
                    .count();
            assertThat(fallbackCount).isEqualTo(1);
        }

        @Test
        void renderModuleParseErrorDistinctCodesEmitSeparateClauses() {
            List<ErrorBinding> errors = List.of(
                    new ErrorBinding("NotFound", 404, ErrorCodeStrategy.REST_JSON),
                    new ErrorBinding("Conflict", 409, ErrorCodeStrategy.REST_JSON)
            );
            String result = writer.renderModuleParseError(errors);
            assertThat(result).contains("defp parse_error(404, body)");
            assertThat(result).contains("defp parse_error(409, body)");
            assertThat(result).doesNotContain("case type_key");
            assertThat(result).doesNotContain("# TODO:");
        }

        @Test
        void renderErrorSerializerWithMessageMemberExtractsFromBody() {
            // AWS-flavoured: messageMemberName = "Message"
            ErrorSpec errors = new ErrorSpec(
                    List.of(new ErrorBinding("NoSuchKey", 404, ErrorCodeStrategy.REST_XML, "Message")),
                    ErrorCodeStrategy.REST_XML
            );
            String result = writer.renderErrorSerializer(errors);
            // The named clause must use message extraction, not raw body.
            assertThat(result).contains("Map.get(body, \"Message\", \"\")");
            // The named clause must NOT use body: body (only the unknown fallback may).
            long namedClauseBodyCount = result.lines()
                    .filter(l -> l.contains("no_such_key") && l.contains("body: body"))
                    .count();
            assertThat(namedClauseBodyCount).isZero();
        }

        @Test
        void renderErrorSerializerWithNullMessageMemberPlacesBodyDirectly() {
            // Non-AWS: messageMemberName = null
            ErrorSpec errors = new ErrorSpec(
                    List.of(new ErrorBinding("SomeError", 422, ErrorCodeStrategy.REST_XML, null)),
                    ErrorCodeStrategy.REST_XML
            );
            String result = writer.renderErrorSerializer(errors);
            // Named clause must use body: body, not message extraction.
            assertThat(result).contains("body: body");
            assertThat(result).doesNotContain("Map.get(body, \"Message\"");
        }

        @Test
        void renderModuleParseErrorStringDispatchWithMessageMemberExtractsFromBody() {
            // AWS-flavoured string dispatch: messageMemberName = "Message"
            List<ErrorBinding> errors = List.of(
                    new ErrorBinding("NoSuchBucket", 404, ErrorCodeStrategy.REST_XML, "Message")
            );
            String result = writer.renderModuleParseError(errors, ErrorCodeStrategy.REST_XML);
            // Named clause must use message extraction.
            assertThat(result).contains("Map.get(body, \"Message\", \"\")");
            // The named clause must NOT use body: body (only the unknown fallback may).
            long namedClauseBodyCount = result.lines()
                    .filter(l -> l.contains("no_such_bucket") && l.contains("body: body"))
                    .count();
            assertThat(namedClauseBodyCount).isZero();
        }

        @Test
        void renderModuleParseErrorStringDispatchWithNullMessageMemberPlacesBodyDirectly() {
            // Non-AWS string dispatch: messageMemberName = null
            List<ErrorBinding> errors = List.of(
                    new ErrorBinding("SomeError", 422, ErrorCodeStrategy.REST_XML, null)
            );
            String result = writer.renderModuleParseError(errors, ErrorCodeStrategy.REST_XML);
            assertThat(result).contains("body: body");
            assertThat(result).doesNotContain("Map.get(body, \"Message\"");
        }
    }

    // ── Server patterns ───────────────────────────────────────────────────────

    @Nested
    class ServerPatterns {

        private OperationSpec makeGetWeatherOp() {
            return new OperationSpec(
                "GetWeather", "WeatherService", Role.SERVER,
                new HttpSpec("GET", "/weather/{city}", 200),
                List.of(new LabelBinding("city", "city", false)),
                List.of(), List.of(),
                null,
                new ErrorSpec(List.of(), ErrorCodeStrategy.REST_JSON),
                AuthSpec.none(), RetrySpec.disabled(), null,
                "GetWeatherOutput", "GetWeatherInput",
                BodyEncoding.JSON, "application/json", ErrorCodeStrategy.REST_JSON, null,
                null, null, List.of()
            );
        }

        @Test
        void renderServerCallbackDeclarationEmitsCallback() {
            String result = writer.renderServerCallbackDeclaration(makeGetWeatherOp());
            assertThat(result).contains("@callback get_weather(");
            assertThat(result).contains("input :: map()");
            assertThat(result).contains("ctx :: map()");
            assertThat(result).contains("{:ok, map()} | {:error, term()}");
        }

        @Test
        void renderServerRouteClauseEmitsPlugRoute() {
            String result = writer.renderServerRouteClause(makeGetWeatherOp());
            assertThat(result).contains("get \"/weather/:city\" do");
            assertThat(result).contains("deserialize_get_weather(conn)");
            assertThat(result).contains("SmithyValidator.validate(input,");
            assertThat(result).contains("handler().get_weather(input, %{})");
            assertThat(result).contains("SmithyServer.response(conn, 200");
            assertThat(result).contains("SmithyServer.validation_error");
            assertThat(result).contains("SmithyServer.error_response");
        }

        @Test
        void renderServerRouteFallbackEmitsMatchUnderscore() {
            String result = writer.renderServerRouteFallback();
            assertThat(result).contains("match _ do");
            assertThat(result).contains("SmithyServer.not_found(conn)");
        }

        @Test
        void renderServerDeserializeExtractsPathParams() {
            String result = writer.renderServerDeserialize(makeGetWeatherOp());
            assertThat(result).contains("defp deserialize_get_weather(conn)");
            assertThat(result).contains("conn.path_params[\"city\"]");
            assertThat(result).contains(":city");
        }

        @Test
        void renderServerDeserializeReadsRawBodyForBodyMembers() {
            BodySpec body = new BodySpec(BodyEncoding.JSON, List.of("city", "temperature"), null);
            OperationSpec op = new OperationSpec(
                    "CreateReport", "WeatherService", Role.SERVER,
                    new HttpSpec("POST", "/report", 201),
                    List.of(), List.of(), List.of(),
                    body,
                    new ErrorSpec(List.of(), ErrorCodeStrategy.REST_JSON),
                    AuthSpec.none(), RetrySpec.disabled(), null,
                    "CreateReportOutput", "CreateReportInput",
                    BodyEncoding.JSON, "application/json", ErrorCodeStrategy.REST_JSON, null,
                    null, null, List.of());
            String result = writer.renderServerDeserialize(op);
            assertThat(result).contains("Plug.Conn.read_body(conn)");
            assertThat(result).contains("Jason.decode(body_raw)");
            assertThat(result).contains(":city => Map.get(body, \"city\")");
            assertThat(result).contains(":temperature => Map.get(body, \"temperature\")");
        }

        @Test
        void renderServerSerializeUsesJason() {
            String result = writer.renderServerSerialize(makeGetWeatherOp());
            assertThat(result).contains("defp serialize_get_weather(output)");
            assertThat(result).contains("Jason.encode!(output)");
        }

        @Test
        void renderServerImplStubEmitsNotImplemented() {
            String result = writer.renderServerImplStub(makeGetWeatherOp(), "WeatherService.Handler");
            assertThat(result).contains("def get_weather(_input, _ctx)");
            assertThat(result).contains("{:error, :not_implemented}");
        }

        @Test
        void serverRuntimeModulesListsExFiles() {
            List<String> modules = writer.serverRuntimeModules();
            assertThat(modules).anyMatch(m -> m.contains("smithy_server.ex"));
            assertThat(modules).anyMatch(m -> m.contains("smithy_validator.ex"));
            assertThat(modules).anyMatch(m -> m.contains("smithy_error_map.ex"));
        }

        @Test
        void renderServerModuleEmitsCompletePlugModule() {
            OperationSpec op = makeGetWeatherOp();
            ModuleTypeSpec types = new ModuleTypeSpec(
                    "WeatherService", List.of(), List.of(), List.of(), List.of(), false);
            String result = writer.renderServerModule("weather_service", List.of(op), types);

            // Dispatcher module
            assertThat(result).contains("defmodule WeatherService.Dispatcher do");
            assertThat(result).contains("use Plug.Router");
            assertThat(result).contains("plug :match");
            assertThat(result).contains("plug :dispatch");
            assertThat(result).contains("get \"/weather/:city\" do");
            assertThat(result).contains("match _ do");
            assertThat(result).contains("defp deserialize_get_weather");
            assertThat(result).contains("defp serialize_get_weather");

            // Handler behaviour module
            assertThat(result).contains("defmodule WeatherService.Handler do");
            assertThat(result).contains("@callback get_weather(");

            // Impl scaffold is written separately — not embedded in the server module
            assertThat(result).doesNotContain("@behaviour WeatherService.Handler");
            assertThat(result).doesNotContain("{:error, :not_implemented}");
        }

        @Test
        void renderServerModuleContainsHandlerAccessor() {
            OperationSpec op = makeGetWeatherOp();
            ModuleTypeSpec types = new ModuleTypeSpec(
                    "WeatherService", List.of(), List.of(), List.of(), List.of(), false);
            String result = writer.renderServerModule("weather_service", List.of(op), types);
            assertThat(result).contains("defp handler, do: WeatherService.Impl");
        }

        @Test
        void renderServerImplContentEmitsCorrectModuleNames() {
            OperationSpec op = makeGetWeatherOp();
            String result = writer.renderServerImplContent("weather_service", List.of(op));

            assertThat(result).contains("defmodule WeatherService.Impl do");
            assertThat(result).contains("@behaviour WeatherService.Handler");
            assertThat(result).contains("def get_weather(_input, _ctx)");
            assertThat(result).contains("{:error, :not_implemented}");
            assertThat(result).endsWith("end\n");
        }
    }

    // ── Auth and retry ────────────────────────────────────────────────────────

    @Nested
    class AuthAndRetry {
        @Test
        void renderAuthWrapperReturnsInnerBlockUnchanged() {
            String inner = "some_expression";
            assertThat(writer.renderAuthWrapper(AuthSpec.none(), inner)).isEqualTo(inner);
            assertThat(writer.renderAuthWrapper(new AuthSpec(true, "s3"), inner)).isEqualTo(inner);
        }

        @Test
        void renderRetryWrapperDisabled() {
            assertThat(writer.renderRetryWrapper(RetrySpec.disabled(), "fun"))
                    .isEqualTo("fun.()");
        }

        @Test
        void renderRetryWrapperEnabled() {
            String result = writer.renderRetryWrapper(RetrySpec.defaultRetry(), "my_fun");
            assertThat(result).contains("SmithyClient.Retry.with_retry");
            assertThat(result).contains("my_fun");
            assertThat(result).contains("max_retries: 3");
        }

        @Test
        void sigv4SignCallReturnsSmithyClientModule() {
            assertThat(writer.sigv4SignCall()).isEqualTo("SmithyClient.Sigv4.sign_request");
        }
    }

    // ── Client runtime modules ────────────────────────────────────────────────

    @Nested
    class ClientRuntimeModules {
        @Test
        void alwaysIncludesSmithyClientAndRetry() {
            List<String> modules = writer.clientRuntimeModules(false, false, false, false);
            assertThat(modules).anyMatch(m -> m.contains("smithy_client.ex"));
            assertThat(modules).anyMatch(m -> m.contains("smithy_retry.ex"));
        }

        @Test
        void includesSigv4WhenRequired() {
            List<String> modules = writer.clientRuntimeModules(true, false, false, false);
            assertThat(modules).anyMatch(m -> m.contains("smithy_sigv4.ex"));
            assertThat(modules).anyMatch(m -> m.contains("smithy_credentials.ex"));
        }

        @Test
        void includesXmlWhenRequired() {
            List<String> modules = writer.clientRuntimeModules(false, true, false, false);
            assertThat(modules).anyMatch(m -> m.contains("smithy_xml.ex"));
        }

        @Test
        void includesQueryWhenRequired() {
            List<String> modules = writer.clientRuntimeModules(false, false, true, false);
            assertThat(modules).anyMatch(m -> m.contains("smithy_query.ex"));
        }

        @Test
        void includesS3WhenRequired() {
            List<String> modules = writer.clientRuntimeModules(false, false, false, true);
            assertThat(modules).anyMatch(m -> m.contains("smithy_s3.ex"));
        }
    }
}
