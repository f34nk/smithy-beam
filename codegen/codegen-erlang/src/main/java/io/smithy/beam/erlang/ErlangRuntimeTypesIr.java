package io.smithy.beam.erlang;

import io.beam.ir.erlang.Header;
import io.beam.ir.erlang.HeaderBlankLine;
import io.beam.ir.erlang.HeaderComment;
import io.beam.ir.erlang.HeaderDefine;
import io.beam.ir.erlang.HeaderEndif;
import io.beam.ir.erlang.HeaderEntry;
import io.beam.ir.erlang.HeaderIfndef;
import io.beam.ir.erlang.HeaderRecordEntry;
import io.beam.ir.erlang.HeaderTypeAliasEntry;
import io.beam.ir.erlang.RecordDef;
import io.beam.ir.erlang.TypedField;
import io.beam.ir.erlang.TypeAlias;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class ErlangRuntimeTypesIr {

  private ErlangRuntimeTypesIr() {}

  static Header runtimeTypesHeader(String moduleName, Optional<String> serviceId) {
    List<HeaderEntry> entries = new ArrayList<>();
    serviceId.ifPresent(
        id -> entries.add(new HeaderComment("Generated runtime types for " + id + ".")));
    entries.add(new HeaderIfndef("BEAM_RUNTIME_TYPES_INCLUDED"));
    entries.add(new HeaderDefine("BEAM_RUNTIME_TYPES_INCLUDED", "true"));
    entries.add(new HeaderBlankLine());
    entries.add(
        new HeaderComment("HTTP carrier types for generated clients. Adjust only via codegen."));
    entries.add(new HeaderRecordEntry(httpRequestRecord()));
    entries.add(new HeaderTypeAliasEntry(TypeAlias.of("http_request", "#http_request{}")));
    entries.add(new HeaderBlankLine());
    entries.add(new HeaderRecordEntry(httpResponseRecord()));
    entries.add(new HeaderTypeAliasEntry(TypeAlias.of("http_response", "#http_response{}")));
    entries.add(new HeaderBlankLine());
    entries.add(new HeaderEndif());
    return Header.ofEntries(entries, false);
  }

  private static RecordDef httpRequestRecord() {
    return RecordDef.of(
        "http_request",
        List.of(
            TypedField.of("method", "binary()", "<<\"GET\">>"),
            TypedField.of("path", "binary()", "<<\"/\">>"),
            TypedField.of("query", "#{binary() => binary()}", "#{}"),
            TypedField.of("headers", "[{binary(), binary()}]", "[]"),
            TypedField.of("body", "iodata()", "<<>>"),
            TypedField.of("host", "binary() | undefined", "undefined"),
            TypedField.of("stream", "term() | undefined", "undefined")));
  }

  private static RecordDef httpResponseRecord() {
    return RecordDef.of(
        "http_response",
        List.of(
            TypedField.of("status", "non_neg_integer()", "200"),
            TypedField.of("headers", "[{binary(), binary()}]", "[]"),
            TypedField.of("body", "iodata()", "<<>>"),
            TypedField.of("stream", "term() | undefined", "undefined")));
  }
}
