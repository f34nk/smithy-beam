package io.smithy.beam.core.writer;

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

import java.util.List;

public interface LanguageWriter {

    String languageId();

    String fileExtension();

    String moduleName(String smithyName);

    String functionName(String smithyName);

    String typeName(String smithyName);

    String varName(String smithyName);

    String mapKey(String smithyMemberName);

    String moduleHeader(String name);

    String moduleFooter();

    String exportSection(List<ExportSpec> exports);

    String behaviourDeclaration(String behaviourName);

    String renderStructType(StructSpec struct);

    String renderEnumType(EnumSpec e);

    String renderUnionType(UnionSpec u);

    String renderCallbackDeclaration(String name, List<ParamSpec> params, TypeRef returnType);

    String renderFunctionSpec(String name, List<ParamSpec> params, TypeRef returnType);

    String renderFunctionHead(String name, List<String> paramPatterns);

    String renderFunctionEnd();

    String renderMapGet(String mapVar, String smithyMemberName, String defaultVal);

    String renderMapBuild(List<MapEntrySpec> entries);

    String renderJsonEncode(String mapVar);

    String renderJsonDecode(String bodyVar);

    String renderXmlEncode(String mapVar, String rootElement);

    String renderXmlDecode(String bodyVar);

    String renderFormEncode(String mapVar);

    String renderUriSubstitution(String template, List<LabelBinding> labels, String inputVar);

    String renderQueryStringBuilder(List<QueryBinding> queries, String inputVar);

    String renderHeaderBuilder(String contentType, List<HeaderBinding> headers, String inputVar);

    String renderHttpClientBlock(
            OperationSpec spec,
            String urlVar,
            String headersVar,
            String bodyVar,
            String methodVar);

    String renderAuthWrapper(AuthSpec auth, String innerBlock);

    String renderRetryWrapper(RetrySpec retry, String requestFunVar);

    String renderResponseHandler(OperationSpec spec, String responseVar);

    String renderErrorSerializer(ErrorSpec errors);

    String renderPaginationHelper(OperationSpec spec, PaginationSpec pagination);

    String jsonEncodeCall(String expr);

    String jsonDecodeCall(String expr);

    String sigv4SignCall();

    String retryCall(String funExpr, String optsExpr);
}
