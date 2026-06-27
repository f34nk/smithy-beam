package io.smithy.beam.elixir;

import io.smithy.beam.ir.elixir.ExAnonymousFn;
import io.smithy.beam.ir.elixir.ExAtom;
import io.smithy.beam.ir.elixir.ExAtomPattern;
import io.smithy.beam.ir.elixir.ExCall;
import io.smithy.beam.ir.elixir.ExCallLocal;
import io.smithy.beam.ir.elixir.ExCapturedBlock;
import io.smithy.beam.ir.elixir.ExCase;
import io.smithy.beam.ir.elixir.ExCaseBranch;
import io.smithy.beam.ir.elixir.ExClause;
import io.smithy.beam.ir.elixir.ExConsPattern;
import io.smithy.beam.ir.elixir.ExFunction;
import io.smithy.beam.ir.elixir.ExGuard;
import io.smithy.beam.ir.elixir.ExIf;
import io.smithy.beam.ir.elixir.ExList;
import io.smithy.beam.ir.elixir.ExMap;
import io.smithy.beam.ir.elixir.ExMapEntry;
import io.smithy.beam.ir.elixir.ExNilPattern;
import io.smithy.beam.ir.elixir.ExOp;
import io.smithy.beam.ir.elixir.ExString;
import io.smithy.beam.ir.elixir.ExStringPattern;
import io.smithy.beam.ir.elixir.ExStructPattern;
import io.smithy.beam.ir.elixir.ExTuple;
import io.smithy.beam.ir.elixir.ExTuplePattern;
import io.smithy.beam.ir.elixir.ExVar;
import io.smithy.beam.ir.elixir.ExVarPattern;
import java.util.ArrayList;
import java.util.List;

final class ElixirCodecHelperIr {
  private ElixirCodecHelperIr() {}

  enum ToBinaryVariant {
    REST_JSON,
    XML_QUERY
  }

  /** Reusable nil tail clauses for wire-optional decoders. */
  static List<ExClause> nilUndefinedTailClauses() {
    return List.of(
        ExClause.clause(List.of(ExNilPattern.nil()), ExAtom.atom("nil")),
        ExClause.clause(List.of(ExVarPattern.var("other")), ExVar.var("other")));
  }

  public static ExFunction uriEncode() {
    return ExFunction.defpFunction(
        "uri_encode",
        List.of(
            ExClause.inlineClause(
                List.of(ExVarPattern.var("value")),
                ExCall.call(
                    "URI",
                    "encode",
                    ExCall.call("Kernel", "to_string", ExVar.var("value"))))));
  }

  public static ExFunction uriDecode() {
    return ExFunction.defpFunction(
        "uri_decode",
        List.of(
            ExClause.inlineClause(List.of(ExNilPattern.nil()), ExAtom.atom("nil")),
            ExClause.inlineClause(
                List.of(ExVarPattern.var("value")),
                ExCall.call("URI", "decode", ExVar.var("value")))));
  }

  public static ExFunction decodeQueryParam() {
    return ExFunction.defpFunction(
        "decode_query_param",
        List.of(
            ExClause.inlineClause(List.of(ExNilPattern.nil()), ExAtom.atom("nil")),
            ExClause.inlineClause(List.of(ExVarPattern.var("true")), ExVar.var("true")),
            ExClause.inlineClause(List.of(ExVarPattern.var("false")), ExVar.var("false")),
            ExClause.inlineClause(
                List.of(ExStringPattern.string("true")), ExVar.var("true")),
            ExClause.inlineClause(
                List.of(ExStringPattern.string("false")), ExVar.var("false")),
            ExClause.inlineClause(
                List.of(ExVarPattern.var("value")), ExVar.var("value"))));
  }

  public static ExFunction prefixHeadersToList() {
    return ExFunction.defpFunction(
        "prefix_headers_to_list",
        List.of(
            ExClause.inlineClause(
                List.of(ExVarPattern.var("_prefix"), ExNilPattern.nil()), ExCapturedBlock.capturedBlock("[]")),
            ExClause.blockClause(
                List.of(ExVarPattern.var("prefix"), ExVarPattern.var("map")),
                List.of(ExGuard.guard("is_map", ExVar.var("map"))),
                ExCall.call(
                    "Enum",
                    "map",
                    ExVar.var("map"),
                    ExAnonymousFn.fn(
                        ExClause.clause(
                            List.of(
                                ExTuplePattern.tuple(
                                    ExVarPattern.var("k"), ExVarPattern.var("v"))),
                            ExTuple.tuple(
                                ExOp.op(
                                    "<>",
                                    ExVar.var("prefix"),
                                    ExVar.var("k")),
                                ExCall.call(
                                    "Kernel",
                                    "to_string",
                                    ExVar.var("v")))))))));
  }

  public static ExFunction prefixHeadersFromList() {
    return ExFunction.defpFunction(
        "prefix_headers_from_list",
        List.of(
            ExClause.blockClause(
                List.of(ExVarPattern.var("headers"), ExVarPattern.var("prefix")),
                ExCapturedBlock.capturedBlock(
                    "headers\n"
                        + "|> Enum.filter(fn {name, _} -> String.starts_with?(name, prefix) end)\n"
                        + "|> Map.new(fn {name, val} -> {String.slice(name, byte_size(prefix)..-1//1), val} end)\n"
                        + "|> case do\n"
                        + "  map when map == %{} -> nil\n"
                        + "  map -> map\n"
                        + "end"))));
  }

  public static ExFunction decodeSparseList() {
    return ExFunction.defpFunction(
        "decode_sparse_list",
        List.of(
            ExClause.inlineClause(List.of(ExNilPattern.nil()), ExAtom.atom("nil")),
            ExClause.blockClause(
                List.of(ExVarPattern.var("list")),
                List.of(ExGuard.guard("is_list", ExVar.var("list"))),
                ExCall.call(
                    "Enum",
                    "map",
                    ExVar.var("list"),
                    ExAnonymousFn.fn(
                        ExClause.clause(List.of(ExNilPattern.nil()), ExAtom.atom("nil")),
                        ExClause.clause(List.of(ExVarPattern.var("v")), ExVar.var("v")))))));
  }

  public static ExFunction decodeList() {
    return ExFunction.defpFunction(
        "decode_list",
        List.of(
            ExClause.inlineClause(List.of(ExNilPattern.nil()), ExAtom.atom("nil")),
            ExClause.inlineClause(
                List.of(ExVarPattern.var("list")),
                List.of(ExGuard.guard("is_list", ExVar.var("list"))),
                ExCall.call(
                    "Enum",
                    "reject",
                    ExVar.var("list"),
                    ExOp.prefix("&", ExCapturedBlock.capturedBlock("is_nil/1"))))));
  }

  public static ExFunction decodeSparseMap() {
    return ExFunction.defpFunction(
        "decode_sparse_map",
        List.of(
            ExClause.inlineClause(List.of(ExNilPattern.nil()), ExAtom.atom("nil")),
            ExClause.blockClause(
                List.of(ExVarPattern.var("map")),
                List.of(ExGuard.guard("is_map", ExVar.var("map"))),
                ExCall.call(
                    "Map",
                    "new",
                    ExVar.var("map"),
                    ExAnonymousFn.fn(
                        ExClause.clause(
                            List.of(
                                ExTuplePattern.tuple(
                                    ExVarPattern.var("k"), ExNilPattern.nil())),
                            ExTuple.tuple(ExVar.var("k"), ExAtom.atom("nil"))),
                        ExClause.clause(
                            List.of(
                                ExTuplePattern.tuple(
                                    ExVarPattern.var("k"), ExVarPattern.var("v"))),
                            ExTuple.tuple(ExVar.var("k"), ExVar.var("v"))))))));
  }

  public static ExFunction encodeTimestampEpochSeconds() {
    return ExFunction.defpFunction(
        "encode_timestamp_epoch_seconds",
        List.of(
            ExClause.inlineClause(
                List.of(ExStructPattern.structFunctionHead("dt", "DateTime", List.of())),
                ExCall.call("DateTime", "to_unix", ExVar.var("dt"))),
            ExClause.inlineClause(List.of(ExNilPattern.nil()), ExAtom.atom("nil"))));
  }

  public static ExFunction encodeTimestampDateTime() {
    return ExFunction.defpFunction(
        "encode_timestamp_date_time",
        List.of(
            ExClause.inlineClause(
                List.of(ExStructPattern.structFunctionHead("dt", "DateTime", List.of())),
                ExCall.call("DateTime", "to_iso8601", ExVar.var("dt"))),
            ExClause.inlineClause(List.of(ExNilPattern.nil()), ExAtom.atom("nil"))));
  }

  public static ExFunction decodeTimestampEpochSeconds() {
    return ExFunction.defpFunction(
        "decode_timestamp_epoch_seconds",
        List.of(
            ExClause.inlineClause(List.of(ExNilPattern.nil()), ExAtom.atom("nil")),
            ExClause.inlineClause(
                List.of(ExVarPattern.var("v")),
                List.of(ExGuard.guard("is_number", ExVar.var("v"))),
                ExCall.call(
                    "DateTime",
                    "from_unix!",
                    ExCall.call("Kernel", "trunc", ExVar.var("v"))))));
  }

  public static ExFunction decodeTimestampDateTime() {
    return ExFunction.defpFunction(
        "decode_timestamp_date_time",
        List.of(
            ExClause.inlineClause(List.of(ExNilPattern.nil()), ExAtom.atom("nil")),
            ExClause.inlineClause(
                List.of(ExVarPattern.var("v")),
                List.of(ExGuard.guard("is_number", ExVar.var("v"))),
                ExCall.call(
                    "DateTime",
                    "from_unix!",
                    ExCall.call("Kernel", "trunc", ExVar.var("v")))),
            ExClause.blockClause(
                List.of(ExVarPattern.var("v")),
                List.of(ExGuard.guard("is_binary", ExVar.var("v"))),
                ExCase.caseExpr(
                    ExCall.call("DateTime", "from_iso8601", ExVar.var("v")),
                    ExCaseBranch.branch(
                        ExTuplePattern.tuple(
                            ExAtomPattern.atom("ok"),
                            ExVarPattern.var("dt"),
                            ExVarPattern.var("_")),
                        ExVar.var("dt")),
                    ExCaseBranch.branch(ExVarPattern.var("_"), ExAtom.atom("nil"))))));
  }

  public static ExFunction generateUuid() {
    return ExFunction.defpFunction(
        "generate_uuid",
        List.of(
            ExClause.blockClause(
                List.of(),
                ExCapturedBlock.capturedBlock(
                    "<<a::32, b::16, _::4, c::12, _::2, d::14, e::48>> = :crypto.strong_rand_bytes(16)\n"
                        + "<<a::32, b::16, 4::4, c::12, 2::2, d::14, e::48>>\n"
                        + "|> Base.encode16(case: :lower)\n"
                        + "|> then(fn hex ->\n"
                        + "  <<part_a::8, part_b::4, part_c::4, part_d::4, part_e::12>> = hex\n"
                        + "  \"#{part_a}-#{part_b}-#{part_c}-#{part_d}-#{part_e}\"\n"
                        + "end)"))));
  }

  public static ExFunction decodeJsonBody() {
    return ExFunction.defpFunction(
        "decode_json_body",
        List.of(
            ExClause.inlineClause(List.of(ExStringPattern.string("")), ExMap.map()),
            ExClause.blockClause(
                List.of(ExVarPattern.var("body")),
                ExCase.caseExpr(
                    ExCall.call("Jason", "decode", ExVar.var("body")),
                    ExCaseBranch.branch(
                        ExTuplePattern.tuple(ExAtomPattern.atom("ok"), ExVarPattern.var("map")),
                        List.of(ExGuard.guard("is_map", ExVar.var("map"))),
                        ExVar.var("map")),
                    ExCaseBranch.branch(ExVarPattern.var("_"), ExMap.map())))));
  }

  public static ExFunction contentTypeMatches() {
    return ExFunction.defpFunction(
        "content_type_matches",
        List.of(
            ExClause.blockClause(
                List.of(ExVarPattern.var("headers"), ExVarPattern.var("expected")),
                ExCase.caseExpr(
                    ExCall.call(
                        "List",
                        "keyfind",
                        ExVar.var("headers"),
                        ExString.string("Content-Type"),
                        ExCapturedBlock.capturedBlock("0")),
                    ExCaseBranch.branch(
                        ExTuplePattern.tuple(ExVarPattern.var("_"), ExVarPattern.var("ct")),
                        List.of(ExGuard.exprGuard(ExOp.op("==", ExVar.var("ct"), ExVar.var("expected")))),
                        ExAtom.atom("ok")),
                    ExCaseBranch.branch(
                        ExTuplePattern.tuple(ExVarPattern.var("_"), ExVarPattern.var("ct")),
                        List.of(ExGuard.guard("is_binary", ExVar.var("ct"))),
                        ExIf.ifExpr(
                            ExOp.op(
                                "==",
                                ExCallLocal.callLocal("ct_base", ExVar.var("ct")),
                                ExCallLocal.callLocal("ct_base", ExVar.var("expected"))),
                            ExAtom.atom("ok"),
                            ExTuple.tuple(
                                ExAtom.atom("error"),
                                ExTuple.tuple(
                                    ExAtom.atom("invalid_content_type"),
                                    ExVar.var("ct"))))),
                    ExCaseBranch.branch(
                        ExVarPattern.var("_"),
                        ExTuple.tuple(
                            ExAtom.atom("error"),
                            ExTuple.tuple(
                                ExAtom.atom("invalid_content_type"),
                                ExAtom.atom("nil"))))))));
  }

  public static ExFunction ctBase() {
    return ExFunction.defpFunction(
        "ct_base",
        List.of(
            ExClause.blockClause(
                List.of(ExVarPattern.var("ct")),
                ExCase.caseExpr(
                    ExCall.call("String", "split", ExVar.var("ct"), ExString.string(";")),
                    ExCaseBranch.branch(
                        ExConsPattern.consPattern(
                            ExVarPattern.var("base"), ExVarPattern.var("_")),
                        ExVar.var("base")),
                    ExCaseBranch.branch(ExVarPattern.var("_"), ExVar.var("ct"))))));
  }

  public static ExFunction headersSet() {
    return ExFunction.defpFunction(
        "headers_set",
        List.of(
            ExClause.inlineClause(
                List.of(
                    ExVarPattern.var("name"),
                    ExVarPattern.var("value"),
                    ExVarPattern.var("headers")),
                ExCall.call(
                    "List",
                    "keystore",
                    ExVar.var("name"),
                    ExCapturedBlock.capturedBlock("0"),
                    ExVar.var("headers"),
                    ExTuple.tuple(ExVar.var("name"), ExVar.var("value"))))));
  }

  public static ExFunction toBinary(ToBinaryVariant variant) {
    List<ExClause> clauses = new ArrayList<>();
    clauses.add(
        ExClause.inlineClause(
            List.of(ExVarPattern.var("v")),
            List.of(ExGuard.guard("is_binary", ExVar.var("v"))),
            ExVar.var("v")));
    clauses.add(
        ExClause.inlineClause(
            List.of(ExVarPattern.var("v")),
            List.of(ExGuard.guard("is_list", ExVar.var("v"))),
            ExCall.call("IO", "iodata_to_binary", ExVar.var("v"))));
    if (variant == ToBinaryVariant.REST_JSON) {
      clauses.add(
          ExClause.inlineClause(
              List.of(ExVarPattern.var("true")), ExString.string("true")));
      clauses.add(
          ExClause.inlineClause(
              List.of(ExVarPattern.var("false")), ExString.string("false")));
      clauses.add(
          ExClause.inlineClause(
              List.of(ExVarPattern.var("v")),
              List.of(ExGuard.guard("is_atom", ExVar.var("v"))),
              ExCall.call("Atom", "to_string", ExVar.var("v"))));
      clauses.add(
          ExClause.inlineClause(
              List.of(ExVarPattern.var("v")),
              List.of(ExGuard.guard("is_integer", ExVar.var("v"))),
              ExCall.call("Integer", "to_string", ExVar.var("v"))));
      clauses.add(
          ExClause.inlineClause(
              List.of(ExVarPattern.var("v")),
              List.of(ExGuard.guard("is_float", ExVar.var("v"))),
              ExCall.call("Float", "to_string", ExVar.var("v"))));
    } else {
      clauses.add(
          ExClause.inlineClause(
              List.of(ExVarPattern.var("v")),
              List.of(ExGuard.guard("is_atom", ExVar.var("v"))),
              ExCall.call("Atom", "to_string", ExVar.var("v"))));
      clauses.add(
          ExClause.inlineClause(
              List.of(ExVarPattern.var("v")),
              List.of(ExGuard.guard("is_integer", ExVar.var("v"))),
              ExCall.call("Integer", "to_string", ExVar.var("v"))));
      clauses.add(
          ExClause.inlineClause(
              List.of(ExVarPattern.var("v")),
              List.of(ExGuard.guard("is_float", ExVar.var("v"))),
              ExCall.call(
                  ":erlang",
                  "float_to_binary",
                  ExVar.var("v"),
                  ExList.list(ExAtom.atom("short")))));
      clauses.add(
          ExClause.inlineClause(
              List.of(ExVarPattern.var("v")),
              List.of(ExGuard.guard("is_boolean", ExVar.var("v"))),
              ExCall.call("Atom", "to_string", ExVar.var("v"))));
    }
    return ExFunction.defpFunction("to_binary", clauses);
  }

  public static ExFunction encodeQueryValueRestJson() {
    return ExFunction.defpFunction(
        "encode_query_value",
        List.of(
            ExClause.inlineClause(
                List.of(ExVarPattern.var("v")),
                List.of(ExGuard.guard("is_boolean", ExVar.var("v"))),
                ExCall.call("Atom", "to_string", ExVar.var("v"))),
            ExClause.inlineClause(
                List.of(ExVarPattern.var("v")),
                List.of(ExGuard.guard("is_integer", ExVar.var("v"))),
                ExCall.call("Integer", "to_string", ExVar.var("v"))),
            ExClause.inlineClause(
                List.of(ExVarPattern.var("v")),
                List.of(ExGuard.guard("is_float", ExVar.var("v"))),
                ExCall.call("Float", "to_string", ExVar.var("v"))),
            ExClause.inlineClause(
                List.of(ExVarPattern.var("v")),
                List.of(ExGuard.guard("is_binary", ExVar.var("v"))),
                ExVar.var("v")),
            ExClause.inlineClause(
                List.of(ExVarPattern.var("v")),
                List.of(ExGuard.guard("is_atom", ExVar.var("v"))),
                ExCall.call("Atom", "to_string", ExVar.var("v")))));
  }

  public static ExFunction encodeQueryValueXmlQuery() {
    return ExFunction.defpFunction(
        "encode_query_value",
        List.of(
            ExClause.inlineClause(
                List.of(ExVarPattern.var("v")),
                List.of(ExGuard.guard("is_integer", ExVar.var("v"))),
                ExCall.call("Integer", "to_string", ExVar.var("v"))),
            ExClause.inlineClause(
                List.of(ExVarPattern.var("v")),
                List.of(ExGuard.guard("is_float", ExVar.var("v"))),
                ExCall.call(
                    ":erlang",
                    "float_to_binary",
                    ExVar.var("v"),
                    ExList.list(ExAtom.atom("short")))),
            ExClause.inlineClause(
                List.of(ExVarPattern.var("v")),
                List.of(ExGuard.guard("is_boolean", ExVar.var("v"))),
                ExCall.call("Atom", "to_string", ExVar.var("v"))),
            ExClause.inlineClause(
                List.of(ExVarPattern.var("v")),
                ExCallLocal.callLocal("to_binary", ExVar.var("v")))));
  }

  public static ExFunction encodeSparseList() {
    return ExFunction.defpFunction(
        "encode_sparse_list",
        List.of(
            ExClause.inlineClause(List.of(ExNilPattern.nil()), ExAtom.atom("nil")),
            ExClause.blockClause(
                List.of(ExVarPattern.var("list")),
                List.of(ExGuard.guard("is_list", ExVar.var("list"))),
                ExCall.call(
                    "Enum",
                    "map",
                    ExVar.var("list"),
                    ExAnonymousFn.fn(
                        ExClause.clause(List.of(ExNilPattern.nil()), ExAtom.atom("nil")),
                        ExClause.clause(List.of(ExVarPattern.var("v")), ExVar.var("v")))))));
  }

  public static ExFunction encodeSparseMap() {
    return ExFunction.defpFunction(
        "encode_sparse_map",
        List.of(
            ExClause.inlineClause(List.of(ExNilPattern.nil()), ExAtom.atom("nil")),
            ExClause.blockClause(
                List.of(ExVarPattern.var("map")),
                List.of(ExGuard.guard("is_map", ExVar.var("map"))),
                ExCall.call(
                    "Map",
                    "new",
                    ExVar.var("map"),
                    ExAnonymousFn.fn(
                        ExClause.clause(
                            List.of(
                                ExTuplePattern.tuple(
                                    ExVarPattern.var("k"), ExNilPattern.nil())),
                            ExTuple.tuple(ExVar.var("k"), ExAtom.atom("nil"))),
                        ExClause.clause(
                            List.of(
                                ExTuplePattern.tuple(
                                    ExVarPattern.var("k"), ExVarPattern.var("v"))),
                            ExTuple.tuple(ExVar.var("k"), ExVar.var("v"))))))));
  }
}
