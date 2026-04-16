package io.smithy.beam.elixir.symbol;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ElixirSymbolProviderTest {

    // ── toModuleName ──────────────────────────────────────────────────────────

    @Test
    void moduleNamePreservesPascalCase() {
        assertThat(ElixirSymbolProvider.toModuleName("WeatherService")).isEqualTo("WeatherService");
    }

    @Test
    void moduleNameCapitalisesFirstLetter() {
        assertThat(ElixirSymbolProvider.toModuleName("weather")).isEqualTo("Weather");
    }

    @Test
    void moduleNameConvertsTitleCaseFromSnake() {
        assertThat(ElixirSymbolProvider.toModuleName("weather_service")).isEqualTo("WeatherService");
    }

    @Test
    void moduleNameHandlesNull() {
        assertThat(ElixirSymbolProvider.toModuleName(null)).isNull();
    }

    @Test
    void moduleNameHandlesEmpty() {
        assertThat(ElixirSymbolProvider.toModuleName("")).isEmpty();
    }

    // ── toFunctionName ────────────────────────────────────────────────────────

    @Test
    void functionNameConvertsGetWeather() {
        assertThat(ElixirSymbolProvider.toFunctionName("GetWeather")).isEqualTo("get_weather");
    }

    @Test
    void functionNameConvertsListBuckets() {
        assertThat(ElixirSymbolProvider.toFunctionName("ListBuckets")).isEqualTo("list_buckets");
    }

    @Test
    void functionNameConvertsAmazonS3() {
        assertThat(ElixirSymbolProvider.toFunctionName("AmazonS3")).isEqualTo("amazon_s3");
    }

    @Test
    void functionNameIsIdempotentForSnakeCase() {
        assertThat(ElixirSymbolProvider.toFunctionName("get_weather")).isEqualTo("get_weather");
    }

    @Test
    void functionNameEscapesReservedWord_case() {
        assertThat(ElixirSymbolProvider.toFunctionName("case")).isEqualTo("case_");
    }

    @Test
    void functionNameEscapesReservedWord_do() {
        assertThat(ElixirSymbolProvider.toFunctionName("do")).isEqualTo("do_");
    }

    @Test
    void functionNameEscapesReservedWord_fn() {
        assertThat(ElixirSymbolProvider.toFunctionName("fn")).isEqualTo("fn_");
    }

    @Test
    void functionNameEscapesReservedWord_when() {
        assertThat(ElixirSymbolProvider.toFunctionName("when")).isEqualTo("when_");
    }

    // ── toTypeName ────────────────────────────────────────────────────────────

    @Test
    void typeNameProducesModuleDotTNotation() {
        assertThat(ElixirSymbolProvider.toTypeName("GetWeatherInput")).isEqualTo("GetWeatherInput.t()");
    }

    @Test
    void typeNamePreservesPascalCase() {
        assertThat(ElixirSymbolProvider.toTypeName("WeatherService")).isEqualTo("WeatherService.t()");
    }

    // ── toVarName ─────────────────────────────────────────────────────────────

    @Test
    void varNameConvertsLowerCamelCase() {
        assertThat(ElixirSymbolProvider.toVarName("cityId")).isEqualTo("city_id");
    }

    @Test
    void varNameConvertsPascalCase() {
        assertThat(ElixirSymbolProvider.toVarName("CityId")).isEqualTo("city_id");
    }

    @Test
    void varNameIsIdempotentForSnakeCase() {
        assertThat(ElixirSymbolProvider.toVarName("city_id")).isEqualTo("city_id");
    }

    @Test
    void varNameEscapesReservedWordCase() {
        assertThat(ElixirSymbolProvider.toVarName("case")).isEqualTo("case_");
    }

    @Test
    void varNameEscapesReservedWordWhen() {
        assertThat(ElixirSymbolProvider.toVarName("when")).isEqualTo("when_");
    }

    @Test
    void varNameEscapesReservedWordTrue() {
        assertThat(ElixirSymbolProvider.toVarName("true")).isEqualTo("true_");
    }

    @Test
    void varNameEscapesReservedWordNil() {
        assertThat(ElixirSymbolProvider.toVarName("nil")).isEqualTo("nil_");
    }

    // ── toAtomTag ─────────────────────────────────────────────────────────────

    @Test
    void atomTagPrefixesColon() {
        assertThat(ElixirSymbolProvider.toAtomTag("city")).isEqualTo(":city");
    }

    @Test
    void atomTagConvertsToSnakeCase() {
        assertThat(ElixirSymbolProvider.toAtomTag("cityId")).isEqualTo(":city_id");
    }

    @Test
    void atomTagConvertsFromPascalCase() {
        assertThat(ElixirSymbolProvider.toAtomTag("GetWeather")).isEqualTo(":get_weather");
    }

    // ── toSnakeCase ───────────────────────────────────────────────────────────

    @Test
    void snakeCaseConvertsWeatherService() {
        assertThat(ElixirSymbolProvider.toSnakeCase("WeatherService")).isEqualTo("weather_service");
    }

    @Test
    void snakeCaseConvertsGetWeather() {
        assertThat(ElixirSymbolProvider.toSnakeCase("GetWeather")).isEqualTo("get_weather");
    }

    @Test
    void snakeCaseConvertsAmazonS3() {
        assertThat(ElixirSymbolProvider.toSnakeCase("AmazonS3")).isEqualTo("amazon_s3");
    }

    @Test
    void snakeCaseIsIdempotent() {
        assertThat(ElixirSymbolProvider.toSnakeCase("already_snake")).isEqualTo("already_snake");
    }

    @Test
    void snakeCaseHandlesConsecutiveUppercase() {
        assertThat(ElixirSymbolProvider.toSnakeCase("HTMLParser")).isEqualTo("html_parser");
    }

    // ── ElixirReservedWords ───────────────────────────────────────────────────

    @Test
    void reservedWordsIncludesDoEndFn() {
        assertThat(ElixirReservedWords.isReserved("do")).isTrue();
        assertThat(ElixirReservedWords.isReserved("end")).isTrue();
        assertThat(ElixirReservedWords.isReserved("fn")).isTrue();
    }

    @Test
    void reservedWordsIncludesTrueFalseNil() {
        assertThat(ElixirReservedWords.isReserved("true")).isTrue();
        assertThat(ElixirReservedWords.isReserved("false")).isTrue();
        assertThat(ElixirReservedWords.isReserved("nil")).isTrue();
    }

    @Test
    void reservedWordsIncludesControlFlow() {
        assertThat(ElixirReservedWords.isReserved("when")).isTrue();
        assertThat(ElixirReservedWords.isReserved("with")).isTrue();
        assertThat(ElixirReservedWords.isReserved("for")).isTrue();
        assertThat(ElixirReservedWords.isReserved("case")).isTrue();
        assertThat(ElixirReservedWords.isReserved("cond")).isTrue();
    }

    @Test
    void nonReservedWordIsNotEscaped() {
        assertThat(ElixirReservedWords.escape("city")).isEqualTo("city");
        assertThat(ElixirReservedWords.escape("get_weather")).isEqualTo("get_weather");
    }

    @Test
    void reservedWordIsEscapedWithUnderscoreSuffix() {
        assertThat(ElixirReservedWords.escape("do")).isEqualTo("do_");
        assertThat(ElixirReservedWords.escape("end")).isEqualTo("end_");
        assertThat(ElixirReservedWords.escape("when")).isEqualTo("when_");
    }

    @Test
    void escapeHandlesNull() {
        assertThat(ElixirReservedWords.isReserved(null)).isFalse();
        assertThat(ElixirReservedWords.escape(null)).isNull();
    }
}
