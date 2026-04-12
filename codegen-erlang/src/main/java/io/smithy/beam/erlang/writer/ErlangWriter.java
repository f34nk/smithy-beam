package io.smithy.beam.erlang.writer;

import io.smithy.beam.core.ir.AuthSpec;
import io.smithy.beam.core.ir.EnumSpec;
import io.smithy.beam.core.ir.ErrorSpec;
import io.smithy.beam.core.ir.HeaderBinding;
import io.smithy.beam.core.ir.LabelBinding;
import io.smithy.beam.core.ir.OperationSpec;
import io.smithy.beam.core.ir.PaginationSpec;
import io.smithy.beam.core.ir.QueryBinding;
import io.smithy.beam.core.ir.RetrySpec;
import io.smithy.beam.core.ir.StructSpec;
import io.smithy.beam.core.ir.TypeRef;
import io.smithy.beam.core.ir.UnionSpec;
import io.smithy.beam.core.writer.ExportSpec;
import io.smithy.beam.core.writer.LanguageWriter;
import io.smithy.beam.core.writer.MapEntrySpec;
import io.smithy.beam.core.writer.ParamSpec;

import java.util.List;

/** Stub emitter; returns empty strings until Erlang rendering is implemented. */
public final class ErlangWriter implements LanguageWriter {

    @Override
    public String languageId() {
        return "erlang";
    }

    @Override
    public String fileExtension() {
        return ".erl";
    }

    @Override
    public String moduleName(String smithyName) {
        return "";
    }

    @Override
    public String functionName(String smithyName) {
        return "";
    }

    @Override
    public String typeName(String smithyName) {
        return "";
    }

    @Override
    public String varName(String smithyName) {
        return "";
    }

    @Override
    public String mapKey(String smithyMemberName) {
        return "";
    }

    @Override
    public String moduleHeader(String name) {
        return "";
    }

    @Override
    public String moduleFooter() {
        return "";
    }

    @Override
    public String exportSection(List<ExportSpec> exports) {
        return "";
    }

    @Override
    public String behaviourDeclaration(String behaviourName) {
        return "";
    }

    @Override
    public String renderStructType(StructSpec struct) {
        return "";
    }

    @Override
    public String renderEnumType(EnumSpec e) {
        return "";
    }

    @Override
    public String renderUnionType(UnionSpec u) {
        return "";
    }

    @Override
    public String renderCallbackDeclaration(String name, List<ParamSpec> params, TypeRef returnType) {
        return "";
    }

    @Override
    public String renderFunctionSpec(String name, List<ParamSpec> params, TypeRef returnType) {
        return "";
    }

    @Override
    public String renderFunctionHead(String name, List<String> paramPatterns) {
        return "";
    }

    @Override
    public String renderFunctionEnd() {
        return "";
    }

    @Override
    public String renderMapGet(String mapVar, String smithyMemberName, String defaultVal) {
        return "";
    }

    @Override
    public String renderMapBuild(List<MapEntrySpec> entries) {
        return "";
    }

    @Override
    public String renderJsonEncode(String mapVar) {
        return "";
    }

    @Override
    public String renderJsonDecode(String bodyVar) {
        return "";
    }

    @Override
    public String renderXmlEncode(String mapVar, String rootElement) {
        return "";
    }

    @Override
    public String renderXmlDecode(String bodyVar) {
        return "";
    }

    @Override
    public String renderFormEncode(String mapVar) {
        return "";
    }

    @Override
    public String renderUriSubstitution(String template, List<LabelBinding> labels, String inputVar) {
        return "";
    }

    @Override
    public String renderQueryStringBuilder(List<QueryBinding> queries, String inputVar) {
        return "";
    }

    @Override
    public String renderHeaderBuilder(String contentType, List<HeaderBinding> headers, String inputVar) {
        return "";
    }

    @Override
    public String renderHttpClientBlock(
            OperationSpec spec, String urlVar, String headersVar, String bodyVar, String methodVar) {
        return "";
    }

    @Override
    public String renderAuthWrapper(AuthSpec auth, String innerBlock) {
        return "";
    }

    @Override
    public String renderRetryWrapper(RetrySpec retry, String requestFunVar) {
        return "";
    }

    @Override
    public String renderResponseHandler(OperationSpec spec, String responseVar) {
        return "";
    }

    @Override
    public String renderErrorSerializer(ErrorSpec errors) {
        return "";
    }

    @Override
    public String renderPaginationHelper(OperationSpec spec, PaginationSpec pagination) {
        return "";
    }

    @Override
    public String jsonEncodeCall(String expr) {
        return "";
    }

    @Override
    public String jsonDecodeCall(String expr) {
        return "";
    }

    @Override
    public String sigv4SignCall() {
        return "";
    }

    @Override
    public String retryCall(String funExpr, String optsExpr) {
        return "";
    }
}
