package io.smithy.beam.erlang;

import io.smithy.beam.ir.erlang.ErlBlankLine;
import io.smithy.beam.ir.erlang.ErlComment;
import io.smithy.beam.ir.erlang.ErlDefine;
import io.smithy.beam.ir.erlang.ErlEndif;
import io.smithy.beam.ir.erlang.ErlHeaderEntry;
import io.smithy.beam.ir.erlang.ErlIfndef;
import io.smithy.beam.ir.erlang.ErlRecordDef;
import io.smithy.beam.ir.erlang.ErlRecordFieldDef;
import io.smithy.beam.ir.erlang.ErlTypeDef;
import io.smithy.beam.ir.erlang.ErlTypeHeader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class ErlangRuntimeTypesIr {

  private ErlangRuntimeTypesIr() {}

  static ErlTypeHeader runtimeTypesHeader(
      String moduleName, Optional<String> endpointRuleSetMap, Optional<String> serviceId) {
    List<ErlHeaderEntry> entries = new ArrayList<>();
    serviceId.ifPresent(
        id -> entries.add(ErlComment.comment("Generated runtime types for " + id + ".")));
    entries.add(new ErlIfndef("BEAM_RUNTIME_TYPES_INCLUDED"));
    entries.add(new ErlDefine("BEAM_RUNTIME_TYPES_INCLUDED", "true"));
    entries.add(new ErlBlankLine());
    entries.add(
        ErlComment.comment("HTTP carrier types for generated clients. Adjust only via codegen."));
    entries.add(httpRequestRecord());
    entries.add(new ErlTypeDef("http_request", "#http_request{}"));
    entries.add(new ErlBlankLine());
    entries.add(httpResponseRecord());
    entries.add(new ErlTypeDef("http_response", "#http_response{}"));
    entries.add(new ErlBlankLine());
    entries.add(new ErlEndif());
    endpointRuleSetMap.ifPresent(
        map -> {
          entries.add(new ErlBlankLine());
          entries.add(ErlComment.comment("@endpointRuleSet embedded at codegen time."));
          entries.add(new ErlTypeDef("endpoint_rule_set", "map()"));
          entries.add(new ErlDefine("ENDPOINT_RULE_SET", map));
        });
    return ErlTypeHeader.typeHeader(moduleName, List.of(), entries, false);
  }

  private static ErlRecordDef httpRequestRecord() {
    return new ErlRecordDef(
        "http_request",
        List.of(
            new ErlRecordFieldDef("method", "binary()", "<<\"GET\">>"),
            new ErlRecordFieldDef("path", "binary()", "<<\"/\">>"),
            new ErlRecordFieldDef("query", "#{binary() => binary()}", "#{}"),
            new ErlRecordFieldDef("headers", "[{binary(), binary()}]", "[]"),
            new ErlRecordFieldDef("body", "iodata()", "<<>>"),
            new ErlRecordFieldDef("host", "binary() | undefined", "undefined"),
            new ErlRecordFieldDef("stream", "term() | undefined", "undefined")));
  }

  private static ErlRecordDef httpResponseRecord() {
    return new ErlRecordDef(
        "http_response",
        List.of(
            new ErlRecordFieldDef("status", "non_neg_integer()", "200"),
            new ErlRecordFieldDef("headers", "[{binary(), binary()}]", "[]"),
            new ErlRecordFieldDef("body", "iodata()", "<<>>"),
            new ErlRecordFieldDef("stream", "term() | undefined", "undefined")));
  }
}
