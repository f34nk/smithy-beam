package io.smithy.beam.elixir;

import io.beam.ir.elixir.AnonFun;
import io.beam.ir.elixir.AnonFunClause;
import io.beam.ir.elixir.AssignPattern;
import io.beam.ir.elixir.AtomExpr;
import io.beam.ir.elixir.AtomPattern;
import io.beam.ir.elixir.BinaryExpr;
import io.beam.ir.elixir.BinaryPattern;
import io.beam.ir.elixir.BinarySegmentExpr;
import io.beam.ir.elixir.BinarySegmentPattern;
import io.beam.ir.elixir.CaptureExpr;
import io.beam.ir.elixir.CaseExpr;
import io.beam.ir.elixir.Clause;
import io.beam.ir.elixir.ComparisonGuard;
import io.beam.ir.elixir.ConsListPattern;
import io.beam.ir.elixir.Expression;
import io.beam.ir.elixir.Function;
import io.beam.ir.elixir.FunctionHead;
import io.beam.ir.elixir.Guard;
import io.beam.ir.elixir.IfExpr;
import io.beam.ir.elixir.InfixExpr;
import io.beam.ir.elixir.IntegerExpr;
import io.beam.ir.elixir.InterpolatedExpr;
import io.beam.ir.elixir.InterpolatedLiteral;
import io.beam.ir.elixir.InterpolatedStringExpr;
import io.beam.ir.elixir.IsTypeGuard;
import io.beam.ir.elixir.ListExpr;
import io.beam.ir.elixir.LocalCallExpr;
import io.beam.ir.elixir.MapExpr;
import io.beam.ir.elixir.MatchExpr;
import io.beam.ir.elixir.NilExpr;
import io.beam.ir.elixir.NilPattern;
import io.beam.ir.elixir.Pattern;
import io.beam.ir.elixir.PipeExpr;
import io.beam.ir.elixir.PipeStep;
import io.beam.ir.elixir.RemoteCallExpr;
import io.beam.ir.elixir.StringExpr;
import io.beam.ir.elixir.StringPattern;
import io.beam.ir.elixir.StructPattern;
import io.beam.ir.elixir.TupleExpr;
import io.beam.ir.elixir.TuplePattern;
import io.beam.ir.elixir.Variable;
import io.beam.ir.elixir.VariablePattern;
import io.beam.ir.elixir.WildcardPattern;
import java.util.ArrayList;
import java.util.List;

final class ElixirCodecHelperIr {
  private ElixirCodecHelperIr() {}

  enum ToBinaryVariant {
    REST_JSON,
    XML_QUERY
  }

  /** Reusable nil tail clauses for wire-optional decoders. */
  static List<Function> nilUndefinedTail(String name, Expression nilBody) {
    return List.of(
        defp(name, List.of(NilPattern.of()), nilBody, true),
        defp(name, List.of(VariablePattern.of("other")), Variable.of("other"), true));
  }

  public static List<Function> uriEncode() {
    return List.of(
        defp(
            "uri_encode",
            List.of(VariablePattern.of("value")),
            RemoteCallExpr.of(
                "URI",
                "encode",
                List.of(RemoteCallExpr.of("Kernel", "to_string", List.of(Variable.of("value"))))),
            true));
  }

  public static List<Function> uriDecode() {
    return List.of(
        defp("uri_decode", List.of(NilPattern.of()), NilExpr.of(), true),
        defp(
            "uri_decode",
            List.of(VariablePattern.of("value")),
            RemoteCallExpr.of("URI", "decode", List.of(Variable.of("value"))),
            true));
  }

  public static List<Function> decodeQueryParam() {
    return List.of(
        defp("decode_query_param", List.of(NilPattern.of()), NilExpr.of(), true),
        defp("decode_query_param", List.of(VariablePattern.of("true")), Variable.of("true"), true),
        defp(
            "decode_query_param", List.of(VariablePattern.of("false")), Variable.of("false"), true),
        defp("decode_query_param", List.of(StringPattern.of("true")), Variable.of("true"), true),
        defp("decode_query_param", List.of(StringPattern.of("false")), Variable.of("false"), true),
        defp(
            "decode_query_param",
            List.of(VariablePattern.of("value")),
            Variable.of("value"),
            true));
  }

  public static List<Function> prefixHeadersToList() {
    return List.of(
        defp(
            "prefix_headers_to_list",
            List.of(VariablePattern.of("_prefix"), NilPattern.of()),
            ListExpr.of(List.of()),
            true),
        defp(
            "prefix_headers_to_list",
            List.of(VariablePattern.of("prefix"), VariablePattern.of("map")),
            IsTypeGuard.of("is_map", "map"),
            RemoteCallExpr.of(
                "Enum",
                "map",
                List.of(
                    Variable.of("map"),
                    new AnonFun(
                        List.of(
                            AnonFunClause.of(
                                List.of(
                                    TuplePattern.of(
                                        List.of(VariablePattern.of("k"), VariablePattern.of("v")))),
                                TupleExpr.of(
                                    List.of(
                                        concat(Variable.of("prefix"), Variable.of("k")),
                                        RemoteCallExpr.of(
                                            "Kernel",
                                            "to_string",
                                            List.of(Variable.of("v")))))))))),
            false));
  }

  public static List<Function> prefixHeadersFromList() {
    return List.of(
        defp(
            "prefix_headers_from_list",
            List.of(VariablePattern.of("headers"), VariablePattern.of("prefix")),
            prefixHeadersFromListBody(),
            false));
  }

  public static List<Function> decodeSparseList() {
    return List.of(
        defp("decode_sparse_list", List.of(NilPattern.of()), NilExpr.of(), true),
        defp(
            "decode_sparse_list",
            List.of(VariablePattern.of("list")),
            IsTypeGuard.of("is_list", "list"),
            RemoteCallExpr.of(
                "Enum",
                "map",
                List.of(
                    Variable.of("list"),
                    new AnonFun(
                        List.of(
                            AnonFunClause.of(List.of(NilPattern.of()), NilExpr.of()),
                            AnonFunClause.of(
                                List.of(VariablePattern.of("v")), Variable.of("v")))))),
            false));
  }

  public static List<Function> decodeList() {
    return List.of(
        defp("decode_list", List.of(NilPattern.of()), NilExpr.of(), true),
        defp(
            "decode_list",
            List.of(VariablePattern.of("list")),
            IsTypeGuard.of("is_list", "list"),
            RemoteCallExpr.of(
                "Enum", "reject", List.of(Variable.of("list"), CaptureExpr.of("is_nil", 1))),
            true));
  }

  public static List<Function> decodeSparseMap() {
    return List.of(
        defp("decode_sparse_map", List.of(NilPattern.of()), NilExpr.of(), true),
        defp(
            "decode_sparse_map",
            List.of(VariablePattern.of("map")),
            IsTypeGuard.of("is_map", "map"),
            RemoteCallExpr.of(
                "Map",
                "new",
                List.of(
                    Variable.of("map"),
                    new AnonFun(
                        List.of(
                            AnonFunClause.of(
                                List.of(
                                    TuplePattern.of(
                                        List.of(VariablePattern.of("k"), NilPattern.of()))),
                                TupleExpr.of(List.of(Variable.of("k"), NilExpr.of()))),
                            AnonFunClause.of(
                                List.of(
                                    TuplePattern.of(
                                        List.of(VariablePattern.of("k"), VariablePattern.of("v")))),
                                TupleExpr.of(List.of(Variable.of("k"), Variable.of("v")))))))),
            false));
  }

  public static List<Function> encodeTimestampEpochSeconds() {
    return List.of(
        defp(
            "encode_timestamp_epoch_seconds",
            List.of(AssignPattern.of("dt", StructPattern.of("DateTime", List.of()))),
            RemoteCallExpr.of("DateTime", "to_unix", List.of(Variable.of("dt"))),
            true),
        defp("encode_timestamp_epoch_seconds", List.of(NilPattern.of()), NilExpr.of(), true));
  }

  public static List<Function> encodeTimestampDateTime() {
    return List.of(
        defp(
            "encode_timestamp_date_time",
            List.of(AssignPattern.of("dt", StructPattern.of("DateTime", List.of()))),
            RemoteCallExpr.of("DateTime", "to_iso8601", List.of(Variable.of("dt"))),
            true),
        defp("encode_timestamp_date_time", List.of(NilPattern.of()), NilExpr.of(), true));
  }

  public static List<Function> decodeTimestampEpochSeconds() {
    return List.of(
        defp("decode_timestamp_epoch_seconds", List.of(NilPattern.of()), NilExpr.of(), true),
        defp(
            "decode_timestamp_epoch_seconds",
            List.of(VariablePattern.of("v")),
            IsTypeGuard.of("is_number", "v"),
            RemoteCallExpr.of(
                "DateTime",
                "from_unix!",
                List.of(RemoteCallExpr.of("Kernel", "trunc", List.of(Variable.of("v"))))),
            true));
  }

  public static List<Function> decodeTimestampDateTime() {
    return List.of(
        defp("decode_timestamp_date_time", List.of(NilPattern.of()), NilExpr.of(), true),
        defp(
            "decode_timestamp_date_time",
            List.of(VariablePattern.of("v")),
            IsTypeGuard.of("is_number", "v"),
            RemoteCallExpr.of(
                "DateTime",
                "from_unix!",
                List.of(RemoteCallExpr.of("Kernel", "trunc", List.of(Variable.of("v"))))),
            true),
        defp(
            "decode_timestamp_date_time",
            List.of(VariablePattern.of("v")),
            IsTypeGuard.of("is_binary", "v"),
            new CaseExpr(
                RemoteCallExpr.of("DateTime", "from_iso8601", List.of(Variable.of("v"))),
                List.of(
                    Clause.of(
                        TuplePattern.of(
                            List.of(
                                AtomPattern.of("ok"),
                                VariablePattern.of("dt"),
                                WildcardPattern.of())),
                        Variable.of("dt")),
                    Clause.of(WildcardPattern.of(), NilExpr.of()))),
            false));
  }

  public static List<Function> generateUuid() {
    BinaryPattern randPattern =
        BinaryPattern.of(
            List.of(
                BinarySegmentPattern.of(VariablePattern.of("a"), "32"),
                BinarySegmentPattern.of(VariablePattern.of("b"), "16"),
                BinarySegmentPattern.of(WildcardPattern.of(), "4"),
                BinarySegmentPattern.of(VariablePattern.of("c"), "12"),
                BinarySegmentPattern.of(WildcardPattern.of(), "2"),
                BinarySegmentPattern.of(VariablePattern.of("d"), "14"),
                BinarySegmentPattern.of(VariablePattern.of("e"), "48")));

    Expression uuidBinary =
        new BinaryExpr(
            List.of(
                new BinarySegmentExpr(Variable.of("a"), "32"),
                new BinarySegmentExpr(Variable.of("b"), "16"),
                new BinarySegmentExpr(IntegerExpr.of(4), "4"),
                new BinarySegmentExpr(Variable.of("c"), "12"),
                new BinarySegmentExpr(IntegerExpr.of(2), "2"),
                new BinarySegmentExpr(Variable.of("d"), "14"),
                new BinarySegmentExpr(Variable.of("e"), "48")));

    Expression keywordOptions =
        ListExpr.of(List.of(TupleExpr.of(List.of(AtomExpr.of("case"), AtomExpr.of("lower")))));

    Expression formatHex =
        new AnonFun(
            List.of(
                AnonFunClause.of(
                    List.of(VariablePattern.of("hex")),
                    MatchExpr.bind(
                        BinaryPattern.of(
                            List.of(
                                BinarySegmentPattern.of(VariablePattern.of("part_a"), "8"),
                                BinarySegmentPattern.of(VariablePattern.of("part_b"), "4"),
                                BinarySegmentPattern.of(VariablePattern.of("part_c"), "4"),
                                BinarySegmentPattern.of(VariablePattern.of("part_d"), "4"),
                                BinarySegmentPattern.of(VariablePattern.of("part_e"), "12"))),
                        Variable.of("hex"),
                        new InterpolatedStringExpr(
                            List.of(
                                new InterpolatedExpr(Variable.of("part_a")),
                                new InterpolatedLiteral("-"),
                                new InterpolatedExpr(Variable.of("part_b")),
                                new InterpolatedLiteral("-"),
                                new InterpolatedExpr(Variable.of("part_c")),
                                new InterpolatedLiteral("-"),
                                new InterpolatedExpr(Variable.of("part_d")),
                                new InterpolatedLiteral("-"),
                                new InterpolatedExpr(Variable.of("part_e"))))))));

    Expression body =
        MatchExpr.bind(
            randPattern,
            RemoteCallExpr.of(":crypto", "strong_rand_bytes", List.of(IntegerExpr.of(16))),
            new PipeExpr(
                uuidBinary,
                List.of(
                    new PipeStep(
                        RemoteCallExpr.of("Base", "encode16", List.of()), List.of(keywordOptions)),
                    new PipeStep(
                        RemoteCallExpr.of("Kernel", "then", List.of()), List.of(formatHex)))));

    return List.of(defp("generate_uuid", List.of(), body, false));
  }

  public static List<Function> decodeJsonBody() {
    return List.of(
        defp("decode_json_body", List.of(StringPattern.of("")), MapExpr.of(List.of()), true),
        defp(
            "decode_json_body",
            List.of(VariablePattern.of("body")),
            new CaseExpr(
                RemoteCallExpr.of("Jason", "decode", List.of(Variable.of("body"))),
                List.of(
                    Clause.of(
                        TuplePattern.of(List.of(AtomPattern.of("ok"), VariablePattern.of("map"))),
                        IsTypeGuard.of("is_map", "map"),
                        Variable.of("map")),
                    Clause.of(WildcardPattern.of(), MapExpr.of(List.of())))),
            false));
  }

  public static List<Function> contentTypeMatches() {
    return List.of(
        defp(
            "content_type_matches",
            List.of(VariablePattern.of("headers"), VariablePattern.of("expected")),
            new CaseExpr(
                RemoteCallExpr.of(
                    "List",
                    "keyfind",
                    List.of(
                        Variable.of("headers"), StringExpr.of("Content-Type"), IntegerExpr.of(0))),
                List.of(
                    Clause.of(
                        TuplePattern.of(List.of(WildcardPattern.of(), VariablePattern.of("ct"))),
                        new ComparisonGuard(Variable.of("ct"), "==", Variable.of("expected")),
                        AtomExpr.of("ok")),
                    Clause.of(
                        TuplePattern.of(List.of(WildcardPattern.of(), VariablePattern.of("ct"))),
                        IsTypeGuard.of("is_binary", "ct"),
                        new IfExpr(
                            new InfixExpr(
                                LocalCallExpr.of("ct_base", List.of(Variable.of("ct"))),
                                "==",
                                LocalCallExpr.of("ct_base", List.of(Variable.of("expected")))),
                            AtomExpr.of("ok"),
                            TupleExpr.of(
                                List.of(
                                    AtomExpr.of("error"),
                                    TupleExpr.of(
                                        List.of(
                                            AtomExpr.of("invalid_content_type"),
                                            Variable.of("ct"))))),
                            false)),
                    Clause.of(
                        WildcardPattern.of(),
                        TupleExpr.of(
                            List.of(
                                AtomExpr.of("error"),
                                TupleExpr.of(
                                    List.of(
                                        AtomExpr.of("invalid_content_type"),
                                        AtomExpr.of("nil")))))))),
            false));
  }

  public static List<Function> ctBase() {
    return List.of(
        defp(
            "ct_base",
            List.of(VariablePattern.of("ct")),
            new CaseExpr(
                RemoteCallExpr.of(
                    "String", "split", List.of(Variable.of("ct"), StringExpr.of(";"))),
                List.of(
                    Clause.of(
                        ConsListPattern.of(VariablePattern.of("base"), WildcardPattern.of()),
                        Variable.of("base")),
                    Clause.of(WildcardPattern.of(), Variable.of("ct")))),
            false));
  }

  public static List<Function> headersSet() {
    return List.of(
        defp(
            "headers_set",
            List.of(
                VariablePattern.of("name"),
                VariablePattern.of("value"),
                VariablePattern.of("headers")),
            RemoteCallExpr.of(
                "List",
                "keystore",
                List.of(
                    Variable.of("name"),
                    IntegerExpr.of(0),
                    Variable.of("headers"),
                    TupleExpr.of(List.of(Variable.of("name"), Variable.of("value"))))),
            true));
  }

  public static List<Function> headerValue() {
    return List.of(
        defp(
            "header_value",
            List.of(VariablePattern.of("headers"), VariablePattern.of("name")),
            new CaseExpr(
                RemoteCallExpr.of(
                    "List",
                    "keyfind",
                    List.of(Variable.of("headers"), Variable.of("name"), IntegerExpr.of(0))),
                List.of(
                    Clause.of(
                        TuplePattern.of(List.of(WildcardPattern.of(), VariablePattern.of("v"))),
                        LocalCallExpr.of("header_value_raw", List.of(Variable.of("v")))),
                    Clause.of(NilPattern.of(), NilExpr.of()))),
            false));
  }

  public static List<Function> headerValueRaw() {
    return List.of(
        defp(
            "header_value_raw",
            List.of(VariablePattern.of("v")),
            IsTypeGuard.of("is_binary", "v"),
            Variable.of("v"),
            true),
        defp(
            "header_value_raw",
            List.of(ConsListPattern.of(VariablePattern.of("v"), VariablePattern.of("_"))),
            IsTypeGuard.of("is_binary", "v"),
            Variable.of("v"),
            true),
        defp("header_value_raw", List.of(VariablePattern.of("v")), Variable.of("v"), true));
  }

  public static List<Function> toBinary(ToBinaryVariant variant) {
    List<Function> functions = new ArrayList<>();
    functions.add(
        defp(
            "to_binary",
            List.of(VariablePattern.of("v")),
            IsTypeGuard.of("is_binary", "v"),
            Variable.of("v"),
            true));
    functions.add(
        defp(
            "to_binary",
            List.of(VariablePattern.of("v")),
            IsTypeGuard.of("is_list", "v"),
            RemoteCallExpr.of("IO", "iodata_to_binary", List.of(Variable.of("v"))),
            true));
    if (variant == ToBinaryVariant.REST_JSON) {
      functions.add(
          defp("to_binary", List.of(VariablePattern.of("true")), StringExpr.of("true"), true));
      functions.add(
          defp("to_binary", List.of(VariablePattern.of("false")), StringExpr.of("false"), true));
      functions.add(
          defp(
              "to_binary",
              List.of(VariablePattern.of("v")),
              IsTypeGuard.of("is_atom", "v"),
              RemoteCallExpr.of("Atom", "to_string", List.of(Variable.of("v"))),
              true));
      functions.add(
          defp(
              "to_binary",
              List.of(VariablePattern.of("v")),
              IsTypeGuard.of("is_integer", "v"),
              RemoteCallExpr.of("Integer", "to_string", List.of(Variable.of("v"))),
              true));
      functions.add(
          defp(
              "to_binary",
              List.of(VariablePattern.of("v")),
              IsTypeGuard.of("is_float", "v"),
              RemoteCallExpr.of("Float", "to_string", List.of(Variable.of("v"))),
              true));
    } else {
      functions.add(
          defp(
              "to_binary",
              List.of(VariablePattern.of("v")),
              IsTypeGuard.of("is_atom", "v"),
              RemoteCallExpr.of("Atom", "to_string", List.of(Variable.of("v"))),
              true));
      functions.add(
          defp(
              "to_binary",
              List.of(VariablePattern.of("v")),
              IsTypeGuard.of("is_integer", "v"),
              RemoteCallExpr.of("Integer", "to_string", List.of(Variable.of("v"))),
              true));
      functions.add(
          defp(
              "to_binary",
              List.of(VariablePattern.of("v")),
              IsTypeGuard.of("is_float", "v"),
              RemoteCallExpr.of(
                  ":erlang",
                  "float_to_binary",
                  List.of(Variable.of("v"), ListExpr.of(List.of(AtomExpr.of("short"))))),
              true));
      functions.add(
          defp(
              "to_binary",
              List.of(VariablePattern.of("v")),
              IsTypeGuard.of("is_boolean", "v"),
              RemoteCallExpr.of("Atom", "to_string", List.of(Variable.of("v"))),
              true));
    }
    return functions;
  }

  public static List<Function> encodeQueryValueRestJson() {
    return List.of(
        defp(
            "encode_query_value",
            List.of(VariablePattern.of("v")),
            IsTypeGuard.of("is_boolean", "v"),
            RemoteCallExpr.of("Atom", "to_string", List.of(Variable.of("v"))),
            true),
        defp(
            "encode_query_value",
            List.of(VariablePattern.of("v")),
            IsTypeGuard.of("is_integer", "v"),
            RemoteCallExpr.of("Integer", "to_string", List.of(Variable.of("v"))),
            true),
        defp(
            "encode_query_value",
            List.of(VariablePattern.of("v")),
            IsTypeGuard.of("is_float", "v"),
            RemoteCallExpr.of("Float", "to_string", List.of(Variable.of("v"))),
            true),
        defp(
            "encode_query_value",
            List.of(VariablePattern.of("v")),
            IsTypeGuard.of("is_binary", "v"),
            Variable.of("v"),
            true),
        defp(
            "encode_query_value",
            List.of(VariablePattern.of("v")),
            IsTypeGuard.of("is_atom", "v"),
            RemoteCallExpr.of("Atom", "to_string", List.of(Variable.of("v"))),
            true));
  }

  public static List<Function> encodeQueryValueXmlQuery() {
    return List.of(
        defp(
            "encode_query_value",
            List.of(VariablePattern.of("v")),
            IsTypeGuard.of("is_integer", "v"),
            RemoteCallExpr.of("Integer", "to_string", List.of(Variable.of("v"))),
            true),
        defp(
            "encode_query_value",
            List.of(VariablePattern.of("v")),
            IsTypeGuard.of("is_float", "v"),
            RemoteCallExpr.of(
                ":erlang",
                "float_to_binary",
                List.of(Variable.of("v"), ListExpr.of(List.of(AtomExpr.of("short"))))),
            true),
        defp(
            "encode_query_value",
            List.of(VariablePattern.of("v")),
            IsTypeGuard.of("is_boolean", "v"),
            RemoteCallExpr.of("Atom", "to_string", List.of(Variable.of("v"))),
            true),
        defp(
            "encode_query_value",
            List.of(VariablePattern.of("v")),
            LocalCallExpr.of("to_binary", List.of(Variable.of("v"))),
            true));
  }

  public static List<Function> encodeSparseList() {
    return List.of(
        defp("encode_sparse_list", List.of(NilPattern.of()), NilExpr.of(), true),
        defp(
            "encode_sparse_list",
            List.of(VariablePattern.of("list")),
            IsTypeGuard.of("is_list", "list"),
            RemoteCallExpr.of(
                "Enum",
                "map",
                List.of(
                    Variable.of("list"),
                    new AnonFun(
                        List.of(
                            AnonFunClause.of(List.of(NilPattern.of()), NilExpr.of()),
                            AnonFunClause.of(
                                List.of(VariablePattern.of("v")), Variable.of("v")))))),
            false));
  }

  public static List<Function> encodeSparseMap() {
    return List.of(
        defp("encode_sparse_map", List.of(NilPattern.of()), NilExpr.of(), true),
        defp(
            "encode_sparse_map",
            List.of(VariablePattern.of("map")),
            IsTypeGuard.of("is_map", "map"),
            RemoteCallExpr.of(
                "Map",
                "new",
                List.of(
                    Variable.of("map"),
                    new AnonFun(
                        List.of(
                            AnonFunClause.of(
                                List.of(
                                    TuplePattern.of(
                                        List.of(VariablePattern.of("k"), NilPattern.of()))),
                                TupleExpr.of(List.of(Variable.of("k"), NilExpr.of()))),
                            AnonFunClause.of(
                                List.of(
                                    TuplePattern.of(
                                        List.of(VariablePattern.of("k"), VariablePattern.of("v")))),
                                TupleExpr.of(List.of(Variable.of("k"), Variable.of("v")))))))),
            false));
  }

  private static Function defp(
      String name, List<Pattern> params, Expression body, boolean oneLiner) {
    return new Function(name, true, List.of(FunctionHead.of(params)), body, null, null, oneLiner);
  }

  private static Function defp(
      String name, List<Pattern> params, Guard guard, Expression body, boolean oneLiner) {
    return new Function(
        name, true, List.of(FunctionHead.of(params, guard)), body, null, null, oneLiner);
  }

  private static InfixExpr concat(Expression left, Expression right) {
    return new InfixExpr(left, "<>", right);
  }

  private static Expression prefixHeadersFromListBody() {
    AnonFun filterFn =
        new AnonFun(
            List.of(
                AnonFunClause.of(
                    List.of(
                        TuplePattern.of(List.of(VariablePattern.of("name"), WildcardPattern.of()))),
                    RemoteCallExpr.of(
                        "String",
                        "starts_with?",
                        List.of(Variable.of("name"), Variable.of("prefix"))))));

    AnonFun mapFn =
        new AnonFun(
            List.of(
                AnonFunClause.of(
                    List.of(
                        TuplePattern.of(
                            List.of(VariablePattern.of("name"), VariablePattern.of("val")))),
                    TupleExpr.of(
                        List.of(
                            LocalCallExpr.of(
                                "binary_part",
                                List.of(
                                    Variable.of("name"),
                                    LocalCallExpr.of("byte_size", List.of(Variable.of("prefix"))),
                                    new InfixExpr(
                                        LocalCallExpr.of("byte_size", List.of(Variable.of("name"))),
                                        "-",
                                        LocalCallExpr.of(
                                            "byte_size", List.of(Variable.of("prefix")))))),
                            Variable.of("val"))))));

    return new PipeExpr(
        Variable.of("headers"),
        List.of(
            new PipeStep(RemoteCallExpr.of("Enum", "filter", List.of(filterFn)), List.of()),
            new PipeStep(RemoteCallExpr.of("Map", "new", List.of(mapFn)), List.of()),
            new PipeStep(
                CaseExpr.piped(
                    List.of(
                        Clause.of(
                            VariablePattern.of("map"),
                            new ComparisonGuard(Variable.of("map"), "==", MapExpr.of(List.of())),
                            NilExpr.of()),
                        Clause.of(VariablePattern.of("map"), Variable.of("map")))),
                List.of())));
  }
}
