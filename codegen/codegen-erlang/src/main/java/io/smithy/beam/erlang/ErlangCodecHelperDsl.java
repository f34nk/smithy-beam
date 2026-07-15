package io.smithy.beam.erlang;

import io.beam.dsl.erlang.AtomExpr;
import io.beam.dsl.erlang.AtomPattern;
import io.beam.dsl.erlang.BinaryExpr;
import io.beam.dsl.erlang.BinaryPattern;
import io.beam.dsl.erlang.BinarySegmentExpr;
import io.beam.dsl.erlang.BinarySegmentPattern;
import io.beam.dsl.erlang.CaseExpr;
import io.beam.dsl.erlang.CatchPattern;
import io.beam.dsl.erlang.Clause;
import io.beam.dsl.erlang.Expression;
import io.beam.dsl.erlang.Fun;
import io.beam.dsl.erlang.FunClause;
import io.beam.dsl.erlang.Function;
import io.beam.dsl.erlang.FunctionClause;
import io.beam.dsl.erlang.InfixExpr;
import io.beam.dsl.erlang.IntegerExpr;
import io.beam.dsl.erlang.IntegerPattern;
import io.beam.dsl.erlang.IsTypeGuard;
import io.beam.dsl.erlang.ListComprehensionExpr;
import io.beam.dsl.erlang.ListExpr;
import io.beam.dsl.erlang.ListPattern;
import io.beam.dsl.erlang.LocalCallExpr;
import io.beam.dsl.erlang.MapExpr;
import io.beam.dsl.erlang.MatchExpr;
import io.beam.dsl.erlang.MatchPattern;
import io.beam.dsl.erlang.Pattern;
import io.beam.dsl.erlang.RemoteCallExpr;
import io.beam.dsl.erlang.StringExpr;
import io.beam.dsl.erlang.TryExpr;
import io.beam.dsl.erlang.TupleExpr;
import io.beam.dsl.erlang.TuplePattern;
import io.beam.dsl.erlang.Variable;
import io.beam.dsl.erlang.VariablePattern;
import io.beam.dsl.erlang.WildcardPattern;
import java.util.ArrayList;
import java.util.List;

final class ErlangCodecHelperDsl {
  private ErlangCodecHelperDsl() {}

  enum ToBinaryVariant {
    REST_JSON,
    XML_QUERY
  }

  /** Reusable undefined/null tail clauses for wire-optional decoders. */
  static List<FunctionClause> nullUndefinedTailClauses() {
    return List.of(
        FunctionClause.of(List.of(AtomPattern.of("undefined")), AtomExpr.of("undefined")),
        FunctionClause.of(List.of(AtomPattern.of("null")), AtomExpr.of("undefined")));
  }

  /** IR for maps:get(Key, Map, Default) used by structure decoders. */
  static RemoteCallExpr mapsGetDefault(BinaryExpr key, Variable map, AtomExpr defaultValue) {
    return RemoteCallExpr.of("maps", "get", List.of(key, map, defaultValue));
  }

  public static Function generateUuid() {
    return Function.of(
        "generate_uuid",
        List.of(
            FunctionClause.of(
                List.of(),
                LocalCallExpr.of(
                    "list_to_binary",
                    List.of(
                        RemoteCallExpr.of(
                            "uuid",
                            "to_string",
                            List.of(RemoteCallExpr.of("uuid", "v4", List.of()))))))));
  }

  public static Function uriEncode() {
    return Function.of(
        "uri_encode",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Value")),
                RemoteCallExpr.of("uri_string", "quote", List.of(Variable.of("Value"))))));
  }

  public static Function uriDecode() {
    return Function.of(
        "uri_decode",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Value")),
                IsTypeGuard.of("binary", Variable.of("Value")),
                RemoteCallExpr.of("uri_string", "unquote", List.of(Variable.of("Value")))),
            FunctionClause.of(List.of(AtomPattern.of("undefined")), AtomExpr.of("undefined"))));
  }

  public static Function decodeQueryParam() {
    return Function.of(
        "decode_query_param",
        List.of(
            FunctionClause.of(List.of(AtomPattern.of("undefined")), AtomExpr.of("undefined")),
            FunctionClause.of(List.of(BinaryPattern.of("true")), AtomExpr.of("true")),
            FunctionClause.of(List.of(BinaryPattern.of("false")), AtomExpr.of("false")),
            FunctionClause.of(
                List.of(VariablePattern.of("V")),
                IsTypeGuard.of("binary", Variable.of("V")),
                Variable.of("V"))));
  }

  public static Function toBinary(ToBinaryVariant variant) {
    List<FunctionClause> clauses = new ArrayList<>();
    clauses.add(
        FunctionClause.of(
            List.of(VariablePattern.of("V")),
            IsTypeGuard.of("binary", Variable.of("V")),
            Variable.of("V")));
    clauses.add(
        FunctionClause.of(
            List.of(VariablePattern.of("V")),
            IsTypeGuard.of("list", Variable.of("V")),
            LocalCallExpr.of("list_to_binary", List.of(Variable.of("V")))));
    if (variant == ToBinaryVariant.REST_JSON) {
      clauses.add(FunctionClause.of(List.of(AtomPattern.of("true")), BinaryExpr.of("true")));
      clauses.add(FunctionClause.of(List.of(AtomPattern.of("false")), BinaryExpr.of("false")));
      clauses.add(
          FunctionClause.of(
              List.of(VariablePattern.of("V")),
              IsTypeGuard.of("atom", Variable.of("V")),
              LocalCallExpr.of("atom_to_binary", List.of(Variable.of("V"), AtomExpr.of("utf8")))));
      clauses.add(
          FunctionClause.of(
              List.of(VariablePattern.of("V")),
              IsTypeGuard.of("integer", Variable.of("V")),
              LocalCallExpr.of("integer_to_binary", List.of(Variable.of("V")))));
      clauses.add(
          FunctionClause.of(
              List.of(VariablePattern.of("V")),
              IsTypeGuard.of("float", Variable.of("V")),
              LocalCallExpr.of("float_to_binary", List.of(Variable.of("V")))));
    } else {
      clauses.add(
          FunctionClause.of(
              List.of(VariablePattern.of("V")),
              IsTypeGuard.of("atom", Variable.of("V")),
              LocalCallExpr.of("atom_to_binary", List.of(Variable.of("V"), AtomExpr.of("utf8")))));
      clauses.add(
          FunctionClause.of(
              List.of(VariablePattern.of("V")),
              IsTypeGuard.of("integer", Variable.of("V")),
              LocalCallExpr.of("integer_to_binary", List.of(Variable.of("V")))));
      clauses.add(
          FunctionClause.of(
              List.of(VariablePattern.of("V")),
              IsTypeGuard.of("float", Variable.of("V")),
              LocalCallExpr.of(
                  "float_to_binary",
                  List.of(Variable.of("V"), ListExpr.of(List.of(AtomExpr.of("short")))))));
      clauses.add(
          FunctionClause.of(
              List.of(VariablePattern.of("V")),
              IsTypeGuard.of("boolean", Variable.of("V")),
              LocalCallExpr.of("atom_to_binary", List.of(Variable.of("V"), AtomExpr.of("utf8")))));
    }
    return Function.of("to_binary", clauses);
  }

  public static Function encodeQueryValueRestJson() {
    return Function.of(
        "encode_query_value",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("V")),
                IsTypeGuard.of("boolean", Variable.of("V")),
                LocalCallExpr.of("atom_to_binary", List.of(Variable.of("V"), AtomExpr.of("utf8")))),
            FunctionClause.of(
                List.of(VariablePattern.of("V")),
                IsTypeGuard.of("integer", Variable.of("V")),
                LocalCallExpr.of("integer_to_binary", List.of(Variable.of("V")))),
            FunctionClause.of(
                List.of(VariablePattern.of("V")),
                IsTypeGuard.of("float", Variable.of("V")),
                LocalCallExpr.of("float_to_binary", List.of(Variable.of("V")))),
            FunctionClause.of(
                List.of(VariablePattern.of("V")),
                IsTypeGuard.of("binary", Variable.of("V")),
                Variable.of("V")),
            FunctionClause.of(
                List.of(VariablePattern.of("V")),
                IsTypeGuard.of("atom", Variable.of("V")),
                LocalCallExpr.of(
                    "atom_to_binary", List.of(Variable.of("V"), AtomExpr.of("utf8"))))));
  }

  public static Function encodeQueryValueXmlQuery() {
    return Function.of(
        "encode_query_value",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("V")),
                IsTypeGuard.of("integer", Variable.of("V")),
                LocalCallExpr.of("integer_to_binary", List.of(Variable.of("V")))),
            FunctionClause.of(
                List.of(VariablePattern.of("V")),
                IsTypeGuard.of("float", Variable.of("V")),
                LocalCallExpr.of(
                    "float_to_binary",
                    List.of(Variable.of("V"), ListExpr.of(List.of(AtomExpr.of("short")))))),
            FunctionClause.of(
                List.of(VariablePattern.of("V")),
                IsTypeGuard.of("boolean", Variable.of("V")),
                LocalCallExpr.of("atom_to_binary", List.of(Variable.of("V"), AtomExpr.of("utf8")))),
            FunctionClause.of(
                List.of(VariablePattern.of("V")),
                LocalCallExpr.of("to_binary", List.of(Variable.of("V"))))));
  }

  public static Function decodeList() {
    List<FunctionClause> clauses = new ArrayList<>(nullUndefinedTailClauses());
    clauses.add(
        FunctionClause.of(
            List.of(VariablePattern.of("List")),
            IsTypeGuard.of("list", Variable.of("List")),
            ListComprehensionExpr.of(
                Variable.of("V"),
                VariablePattern.of("V"),
                Variable.of("List"),
                InfixExpr.of(Variable.of("V"), "=/=", AtomExpr.of("null")))));
    return Function.of("decode_list", clauses);
  }

  public static Function decodeSparseList() {
    CaseExpr nullToUndefined =
        CaseExpr.of(
            Variable.of("V"),
            List.of(
                Clause.of(AtomPattern.of("null"), AtomExpr.of("undefined")),
                Clause.of(WildcardPattern.of(), Variable.of("V"))));
    List<FunctionClause> clauses = new ArrayList<>(nullUndefinedTailClauses());
    clauses.add(
        FunctionClause.of(
            List.of(VariablePattern.of("List")),
            IsTypeGuard.of("list", Variable.of("List")),
            ListComprehensionExpr.of(
                nullToUndefined, VariablePattern.of("V"), Variable.of("List"))));
    return Function.of("decode_sparse_list", clauses);
  }

  public static Function encodeSparseList() {
    CaseExpr undefinedToNull =
        CaseExpr.of(
            Variable.of("V"),
            List.of(
                Clause.of(AtomPattern.of("undefined"), AtomExpr.of("null")),
                Clause.of(WildcardPattern.of(), Variable.of("V"))));
    return Function.of(
        "encode_sparse_list",
        List.of(
            FunctionClause.of(List.of(AtomPattern.of("undefined")), AtomExpr.of("null")),
            FunctionClause.of(
                List.of(VariablePattern.of("List")),
                IsTypeGuard.of("list", Variable.of("List")),
                ListComprehensionExpr.of(
                    undefinedToNull, VariablePattern.of("V"), Variable.of("List")))));
  }

  public static Function encodeSparseMap() {
    return Function.of(
        "encode_sparse_map",
        List.of(
            FunctionClause.of(List.of(AtomPattern.of("undefined")), AtomExpr.of("null")),
            FunctionClause.of(
                List.of(VariablePattern.of("Map")),
                IsTypeGuard.of("map", Variable.of("Map")),
                sparseMapTransform(AtomPattern.of("undefined"), AtomExpr.of("null")))));
  }

  private static RemoteCallExpr sparseMapTransform(Pattern nullPattern, Expression replacement) {
    return RemoteCallExpr.of(
        "maps",
        "map",
        List.of(
            Fun.of(
                List.of(
                    FunClause.of(List.of(WildcardPattern.of("K"), nullPattern), replacement),
                    FunClause.of(
                        List.of(WildcardPattern.of("K"), VariablePattern.of("V")),
                        Variable.of("V")))),
            Variable.of("Map")));
  }

  public static Function decodeJsonBody() {
    return Function.of(
        "decode_json_body",
        List.of(
            FunctionClause.of(List.of(BinaryPattern.of("")), MapExpr.of(List.of())),
            FunctionClause.of(
                List.of(VariablePattern.of("Body")),
                CaseExpr.of(
                    RemoteCallExpr.of("jsone", "try_decode", List.of(Variable.of("Body"))),
                    List.of(
                        Clause.of(
                            TuplePattern.of(
                                List.of(
                                    AtomPattern.of("ok"),
                                    VariablePattern.of("V"),
                                    WildcardPattern.of())),
                            IsTypeGuard.of("map", Variable.of("V")),
                            Variable.of("V")),
                        Clause.of(WildcardPattern.of(), MapExpr.of(List.of())))))));
  }

  public static Function contentTypeMatches() {
    Pattern binaryMatchGuard =
        MatchPattern.of(
            BinaryPattern.of(List.of(BinarySegmentPattern.of(WildcardPattern.of(), "binary"))),
            VariablePattern.of("CT"));
    return Function.of(
        "content_type_matches",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Headers"), VariablePattern.of("Expected")),
                CaseExpr.of(
                    RemoteCallExpr.of(
                        "proplists",
                        "get_value",
                        List.of(
                            BinaryExpr.of("Content-Type"),
                            Variable.of("Headers"),
                            AtomExpr.of("undefined"))),
                    List.of(
                        Clause.of(VariablePattern.of("Expected"), AtomExpr.of("true")),
                        Clause.of(
                            binaryMatchGuard,
                            InfixExpr.of(
                                LocalCallExpr.of("ct_base", List.of(Variable.of("CT"))),
                                "=:=",
                                LocalCallExpr.of("ct_base", List.of(Variable.of("Expected"))))),
                        Clause.of(WildcardPattern.of(), AtomExpr.of("false")))))));
  }

  public static Function ctBase() {
    return Function.of(
        "ct_base",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("CT")),
                CaseExpr.of(
                    RemoteCallExpr.of(
                        "binary", "split", List.of(Variable.of("CT"), BinaryExpr.of(";"))),
                    List.of(
                        Clause.of(
                            ListPattern.cons(VariablePattern.of("Base"), WildcardPattern.of()),
                            Variable.of("Base")),
                        Clause.of(WildcardPattern.of(), Variable.of("CT")))))));
  }

  public static Function prefixHeadersToList() {
    Expression headerName =
        BinaryExpr.of(
            List.of(
                BinarySegmentExpr.of(Variable.of("Prefix"), "binary"),
                BinarySegmentExpr.of(Variable.of("H"), "binary")));
    return Function.of(
        "prefix_headers_to_list",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("_Prefix"), AtomPattern.of("undefined")),
                ListExpr.of(List.of())),
            FunctionClause.of(
                List.of(VariablePattern.of("Prefix"), VariablePattern.of("Map")),
                IsTypeGuard.of("map", Variable.of("Map")),
                ListComprehensionExpr.of(
                    TupleExpr.of(
                        List.of(
                            headerName, LocalCallExpr.of("to_binary", List.of(Variable.of("V"))))),
                    TuplePattern.of(List.of(VariablePattern.of("H"), VariablePattern.of("V"))),
                    RemoteCallExpr.of("maps", "to_list", List.of(Variable.of("Map")))))));
  }

  public static Function prefixHeadersFromList() {
    ListComprehensionExpr headerEntries =
        ListComprehensionExpr.of(
            TupleExpr.of(
                List.of(
                    RemoteCallExpr.of(
                        "binary",
                        "part",
                        List.of(
                            Variable.of("Name"),
                            LocalCallExpr.of("byte_size", List.of(Variable.of("Prefix"))))),
                    Variable.of("Val"))),
            TuplePattern.of(List.of(VariablePattern.of("Name"), VariablePattern.of("Val"))),
            Variable.of("Headers"),
            List.of(
                InfixExpr.of(
                    LocalCallExpr.of("byte_size", List.of(Variable.of("Name"))),
                    ">",
                    LocalCallExpr.of("byte_size", List.of(Variable.of("Prefix")))),
                InfixExpr.of(
                    RemoteCallExpr.of(
                        "binary",
                        "part",
                        List.of(
                            Variable.of("Name"),
                            IntegerExpr.of(0),
                            LocalCallExpr.of("byte_size", List.of(Variable.of("Prefix"))))),
                    "=:=",
                    Variable.of("Prefix"))));
    return Function.of(
        "prefix_headers_from_list",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Headers"), VariablePattern.of("Prefix")),
                MatchExpr.bind(
                    "Map",
                    RemoteCallExpr.of("maps", "from_list", List.of(headerEntries)),
                    CaseExpr.of(
                        RemoteCallExpr.of("maps", "size", List.of(Variable.of("Map"))),
                        List.of(
                            Clause.of(IntegerPattern.of(0), AtomExpr.of("undefined")),
                            Clause.of(WildcardPattern.of(), Variable.of("Map"))))))));
  }

  public static Function encodeTimestampEpochSeconds() {
    return Function.of(
        "encode_timestamp_epoch_seconds",
        List.of(
            FunctionClause.of(
                List.of(
                    TuplePattern.of(
                        List.of(
                            VariablePattern.of("Mega"),
                            VariablePattern.of("Secs"),
                            WildcardPattern.of("Micro")))),
                InfixExpr.of(
                    InfixExpr.of(Variable.of("Mega"), "*", IntegerExpr.of(1_000_000L)),
                    "+",
                    Variable.of("Secs"))),
            FunctionClause.of(List.of(AtomPattern.of("undefined")), AtomExpr.of("undefined"))));
  }

  public static Function encodeTimestampDateTime() {
    return Function.of(
        "encode_timestamp_date_time",
        List.of(
            FunctionClause.of(
                List.of(
                    TuplePattern.of(
                        List.of(
                            VariablePattern.of("Mega"),
                            VariablePattern.of("Secs"),
                            WildcardPattern.of("Micro")))),
                encodeTimestampDateTimeBody()),
            FunctionClause.of(List.of(AtomPattern.of("undefined")), AtomExpr.of("undefined"))));
  }

  private static MatchExpr encodeTimestampDateTimeBody() {
    Expression epochSecs =
        InfixExpr.of(
            InfixExpr.of(Variable.of("Mega"), "*", IntegerExpr.of(1_000_000L)),
            "+",
            Variable.of("Secs"));
    Expression gregorian =
        RemoteCallExpr.of(
            "calendar",
            "gregorian_seconds_to_datetime",
            List.of(InfixExpr.of(Variable.of("EpochSecs"), "+", IntegerExpr.of(62_167_219_200L))));
    Expression formatted =
        LocalCallExpr.of(
            "iolist_to_binary",
            List.of(
                RemoteCallExpr.of(
                    "io_lib",
                    "format",
                    List.of(
                        StringExpr.of("~4..0B-~2..0B-~2..0BT~2..0B:~2..0B:~2..0BZ"),
                        ListExpr.of(
                            List.of(
                                Variable.of("Y"),
                                Variable.of("Mo"),
                                Variable.of("D"),
                                Variable.of("H"),
                                Variable.of("Mi"),
                                Variable.of("S")))))));
    Pattern dateTimeTuple =
        TuplePattern.of(
            List.of(
                TuplePattern.of(
                    List.of(
                        VariablePattern.of("Y"),
                        VariablePattern.of("Mo"),
                        VariablePattern.of("D"))),
                TuplePattern.of(
                    List.of(
                        VariablePattern.of("H"),
                        VariablePattern.of("Mi"),
                        VariablePattern.of("S")))));
    return MatchExpr.of(
        VariablePattern.of("EpochSecs"),
        epochSecs,
        MatchExpr.of(dateTimeTuple, gregorian, formatted));
  }

  public static Function decodeTimestampEpochSeconds() {
    Expression epochTuple =
        MatchExpr.bind(
            "Mega",
            InfixExpr.of(Variable.of("V"), "div", IntegerExpr.of(1_000_000L)),
            MatchExpr.bind(
                "Secs",
                InfixExpr.of(Variable.of("V"), "rem", IntegerExpr.of(1_000_000L)),
                TupleExpr.of(
                    List.of(Variable.of("Mega"), Variable.of("Secs"), IntegerExpr.of(0)))));
    return Function.of(
        "decode_timestamp_epoch_seconds",
        List.of(
            FunctionClause.of(List.of(AtomPattern.of("null")), AtomExpr.of("undefined")),
            FunctionClause.of(List.of(AtomPattern.of("undefined")), AtomExpr.of("undefined")),
            FunctionClause.of(
                List.of(VariablePattern.of("V")),
                IsTypeGuard.of("number", Variable.of("V")),
                epochTuple)));
  }

  public static Function decodeTimestampDateTime() {
    BinaryPattern isoTimestampPattern =
        BinaryPattern.of(
            List.of(
                BinarySegmentPattern.of(VariablePattern.of("Y"), 4, "binary"),
                BinarySegmentPattern.literal("-"),
                BinarySegmentPattern.of(VariablePattern.of("Mo"), 2, "binary"),
                BinarySegmentPattern.literal("-"),
                BinarySegmentPattern.of(VariablePattern.of("D"), 2, "binary"),
                BinarySegmentPattern.literal("T"),
                BinarySegmentPattern.of(VariablePattern.of("H"), 2, "binary"),
                BinarySegmentPattern.literal(":"),
                BinarySegmentPattern.of(VariablePattern.of("Mi"), 2, "binary"),
                BinarySegmentPattern.literal(":"),
                BinarySegmentPattern.of(VariablePattern.of("S"), 2, "binary"),
                BinarySegmentPattern.of(WildcardPattern.of(), "binary")));
    Expression gregorianSecs =
        RemoteCallExpr.of("calendar", "datetime_to_gregorian_seconds", List.of(Variable.of("Dt")));
    Expression epochSecs =
        InfixExpr.of(Variable.of("GregorianSecs"), "-", IntegerExpr.of(62_167_219_200L));
    Expression mega = InfixExpr.of(Variable.of("EpochSecs"), "div", IntegerExpr.of(1_000_000L));
    Expression binaryResult =
        TupleExpr.of(
            List.of(
                Variable.of("Mega"),
                InfixExpr.of(Variable.of("EpochSecs"), "rem", IntegerExpr.of(1_000_000L)),
                IntegerExpr.of(0)));
    TryExpr iso8601Parse =
        TryExpr.of(
            MatchExpr.of(
                isoTimestampPattern,
                Variable.of("V"),
                MatchExpr.bind(
                    "Dt",
                    TupleExpr.of(
                        List.of(
                            TupleExpr.of(
                                List.of(
                                    LocalCallExpr.of(
                                        "binary_to_integer", List.of(Variable.of("Y"))),
                                    LocalCallExpr.of(
                                        "binary_to_integer", List.of(Variable.of("Mo"))),
                                    LocalCallExpr.of(
                                        "binary_to_integer", List.of(Variable.of("D"))))),
                            TupleExpr.of(
                                List.of(
                                    LocalCallExpr.of(
                                        "binary_to_integer", List.of(Variable.of("H"))),
                                    LocalCallExpr.of(
                                        "binary_to_integer", List.of(Variable.of("Mi"))),
                                    LocalCallExpr.of(
                                        "binary_to_integer", List.of(Variable.of("S"))))))),
                    MatchExpr.bind(
                        "GregorianSecs",
                        gregorianSecs,
                        MatchExpr.bind(
                            "EpochSecs", epochSecs, MatchExpr.bind("Mega", mega, binaryResult))))),
            List.of(Clause.of(CatchPattern.anyAny(), AtomExpr.of("undefined"))));
    Expression numberResult =
        MatchExpr.bind(
            "EpochSecs",
            LocalCallExpr.of("trunc", List.of(Variable.of("V"))),
            MatchExpr.bind(
                "Mega",
                InfixExpr.of(Variable.of("EpochSecs"), "div", IntegerExpr.of(1_000_000L)),
                TupleExpr.of(
                    List.of(
                        Variable.of("Mega"),
                        InfixExpr.of(Variable.of("EpochSecs"), "rem", IntegerExpr.of(1_000_000L)),
                        IntegerExpr.of(0)))));
    return Function.of(
        "decode_timestamp_date_time",
        List.of(
            FunctionClause.of(List.of(AtomPattern.of("null")), AtomExpr.of("undefined")),
            FunctionClause.of(List.of(AtomPattern.of("undefined")), AtomExpr.of("undefined")),
            FunctionClause.of(
                List.of(VariablePattern.of("V")),
                IsTypeGuard.of("number", Variable.of("V")),
                numberResult),
            FunctionClause.of(
                List.of(VariablePattern.of("V")),
                IsTypeGuard.of("binary", Variable.of("V")),
                iso8601Parse)));
  }

  public static Function headersSet() {
    return Function.of(
        "headers_set",
        List.of(
            FunctionClause.of(
                List.of(
                    VariablePattern.of("Name"),
                    VariablePattern.of("Value"),
                    VariablePattern.of("Headers")),
                RemoteCallExpr.of(
                    "lists",
                    "keystore",
                    List.of(
                        Variable.of("Name"),
                        IntegerExpr.of(1),
                        Variable.of("Headers"),
                        TupleExpr.of(List.of(Variable.of("Name"), Variable.of("Value"))))))));
  }
}
