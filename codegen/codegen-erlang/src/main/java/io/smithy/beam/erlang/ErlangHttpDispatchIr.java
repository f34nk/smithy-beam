package io.smithy.beam.erlang;

import io.beam.ir.erlang.AtomExpr;
import io.beam.ir.erlang.AtomPattern;
import io.beam.ir.erlang.BinaryExpr;
import io.beam.ir.erlang.BinaryPattern;
import io.beam.ir.erlang.BinarySegmentExpr;
import io.beam.ir.erlang.BlockExpr;
import io.beam.ir.erlang.CaseExpr;
import io.beam.ir.erlang.Clause;
import io.beam.ir.erlang.Expression;
import io.beam.ir.erlang.Function;
import io.beam.ir.erlang.FunctionClause;
import io.beam.ir.erlang.ListComprehensionExpr;
import io.beam.ir.erlang.ListExpr;
import io.beam.ir.erlang.ListPattern;
import io.beam.ir.erlang.LocalCallExpr;
import io.beam.ir.erlang.MapEntry;
import io.beam.ir.erlang.MapExpr;
import io.beam.ir.erlang.MapPattern;
import io.beam.ir.erlang.MapPatternEntry;
import io.beam.ir.erlang.MatchExpr;
import io.beam.ir.erlang.MatchPattern;
import io.beam.ir.erlang.Module;
import io.beam.ir.erlang.RecordExpr;
import io.beam.ir.erlang.RecordField;
import io.beam.ir.erlang.RecordPattern;
import io.beam.ir.erlang.RecordPatternField;
import io.beam.ir.erlang.RemoteCallExpr;
import io.beam.ir.erlang.TupleExpr;
import io.beam.ir.erlang.TuplePattern;
import io.beam.ir.erlang.Variable;
import io.beam.ir.erlang.VariablePattern;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.model.shapes.ServiceShape;

final class ErlangHttpDispatchIr {
  private ErlangHttpDispatchIr() {}

  static Module httpDispatchModule(
      String httpModule,
      String runtimeTypesHeaderFile,
      ServiceShape service,
      boolean sigv4,
      boolean endpointRules,
      String configVar,
      String helpersMod,
      String endpointsMod,
      String credentialsMod) {
    return Module.of(
        httpModule,
        List.of(
            dispatchArity2(),
            dispatchArity3(),
            dispatchSigned(
                sigv4, endpointRules, configVar, helpersMod, endpointsMod, credentialsMod),
            splitBaseUrl(),
            mime()),
        List.of(
            "Generated HTTP dispatcher for " + service.getId() + ".",
            "Uses httpc from OTP. Replace via adapter for testing."),
        null,
        List.of(runtimeTypesHeaderFile),
        null,
        List.of("dispatch/2", "dispatch/3"));
  }

  static Function dispatchArity2() {
    return Function.of(
        "dispatch",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Config"), VariablePattern.of("Request")),
                BlockExpr.commaSeparated(
                    List.of(
                        MatchExpr.bindValue(
                            "HttpClient",
                            RemoteCallExpr.of(
                                "maps",
                                "get",
                                List.of(
                                    AtomExpr.of("http_client"),
                                    Variable.of("Config"),
                                    AtomExpr.of("httpc")))),
                        LocalCallExpr.of(
                            "dispatch",
                            List.of(
                                Variable.of("HttpClient"),
                                Variable.of("Config"),
                                Variable.of("Request")))),
                    false))),
        null,
        null,
        null);
  }

  static Function dispatchArity3() {
    return Function.of(
        "dispatch",
        List.of(
            FunctionClause.of(
                List.of(
                    VariablePattern.of("HttpClient"),
                    VariablePattern.of("Config"),
                    VariablePattern.of("Request")),
                LocalCallExpr.of(
                    "dispatch_signed",
                    List.of(
                        Variable.of("HttpClient"),
                        Variable.of("Config"),
                        Variable.of("Request"))))),
        null,
        null,
        null);
  }

  static Function dispatchSigned(
      boolean sigv4,
      boolean endpointRules,
      String configVar,
      String helpersMod,
      String endpointsMod,
      String credentialsMod) {
    List<Expression> body = new ArrayList<>();
    if (sigv4) {
      body.add(sigv4ConfigMatch(credentialsMod));
    }
    body.add(resolveBaseUrlMatch(configVar, helpersMod, endpointsMod, endpointRules));
    body.add(queryStringMatch());
    body.add(
        MatchExpr.of(
            TuplePattern.of(
                List.of(
                    VariablePattern.of("Scheme"),
                    VariablePattern.of("DefaultAuthority"))),
            LocalCallExpr.of("split_base_url", List.of(Variable.of("BaseUrl"))),
            BlockExpr.commaSeparated(
                List.of(
                    authorityMatch(),
                    MatchExpr.bindValue(
                        "ReqUrl",
                        BinaryExpr.of(
                            List.of(
                                BinarySegmentExpr.of(Variable.of("Scheme"), "binary"),
                                BinarySegmentExpr.of(Variable.of("Authority"), "binary"),
                                BinarySegmentExpr.of(Variable.of("Path"), "binary"),
                                BinarySegmentExpr.of(Variable.of("QueryStr"), "binary")))),
                    httpcHeadersMatch(),
                    requestTupleMatch(),
                    httpClientRequestCase()),
                false)));

    return Function.of(
        "dispatch_signed",
        List.of(
            FunctionClause.of(
                List.of(
                    VariablePattern.of("HttpClient"),
                    VariablePattern.of("Config"),
                    httpRequestPattern()),
                BlockExpr.commaSeparated(body, false))),
        null,
        null,
        null);
  }

  static Function splitBaseUrl() {
    BinaryExpr schemePrefix =
        BinaryExpr.of(
            List.of(
                BinarySegmentExpr.of(
                    LocalCallExpr.of("list_to_binary", List.of(Variable.of("Scheme"))),
                    "binary"),
                BinarySegmentExpr.literal("://")));

    BinaryExpr authority =
        BinaryExpr.of(
            List.of(
                BinarySegmentExpr.of(
                    LocalCallExpr.of("list_to_binary", List.of(Variable.of("Host"))),
                    "binary"),
                BinarySegmentExpr.of(Variable.of("PortSuffix"), "binary")));

    BinaryExpr portSuffix =
        BinaryExpr.of(
            List.of(
                BinarySegmentExpr.literal(":"),
                BinarySegmentExpr.of(
                    LocalCallExpr.of("integer_to_binary", List.of(Variable.of("Port"))),
                    "binary")));

    CaseExpr portSuffixCase =
        CaseExpr.of(
            RemoteCallExpr.of(
                "maps",
                "get",
                List.of(AtomExpr.of("port"), Variable.of("Parts"), AtomExpr.of("undefined"))),
            List.of(
                Clause.of(AtomPattern.of("undefined"), BinaryExpr.of("")),
                Clause.of(VariablePattern.of("Port"), portSuffix)));

    CaseExpr parseCase =
        CaseExpr.of(
            RemoteCallExpr.of(
                "uri_string",
                "parse",
                List.of(LocalCallExpr.of("binary_to_list", List.of(Variable.of("BaseUrl"))))),
            List.of(
                Clause.of(
                    MatchPattern.of(
                        MapPattern.of(
                            List.of(
                                MapPatternEntry.of(
                                    AtomExpr.of("scheme"), VariablePattern.of("Scheme"), true),
                                MapPatternEntry.of(
                                    AtomExpr.of("host"), VariablePattern.of("Host"), true))),
                        VariablePattern.of("Parts")),
                    BlockExpr.commaSeparated(
                        List.of(
                            MatchExpr.bindValue("PortSuffix", portSuffixCase),
                            TupleExpr.of(List.of(schemePrefix, authority))),
                        false)),
                Clause.of(
                    VariablePattern.of("_"),
                    TupleExpr.of(List.of(BinaryExpr.of(""), Variable.of("BaseUrl"))))));

    return Function.of(
        "split_base_url",
        List.of(
            FunctionClause.of(
                List.of(BinaryPattern.of("")),
                TupleExpr.of(List.of(BinaryExpr.of(""), BinaryExpr.of("")))),
            FunctionClause.of(List.of(VariablePattern.of("BaseUrl")), parseCase)));
  }

  static Function mime() {
    return Function.of(
        "mime",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Headers")),
                CaseExpr.of(
                    RemoteCallExpr.of(
                        "proplists",
                        "get_value",
                        List.of(
                            BinaryExpr.of("Content-Type"),
                            Variable.of("Headers"))),
                    List.of(
                        Clause.of(
                            AtomPattern.of("undefined"),
                            BinaryExpr.of("application/octet-stream")),
                        Clause.of(
                            VariablePattern.of("CT"),
                            LocalCallExpr.of(
                                "binary_to_list", List.of(Variable.of("CT")))))))),
        null,
        null,
        null);
  }

  private static RecordPattern httpRequestPattern() {
    return RecordPattern.bind(
        "Req",
        "http_request",
        List.of(
            RecordPatternField.of("method", VariablePattern.of("Method")),
            RecordPatternField.of("path", VariablePattern.of("Path")),
            RecordPatternField.of("query", VariablePattern.of("Query")),
            RecordPatternField.of("headers", VariablePattern.of("Headers")),
            RecordPatternField.of("body", VariablePattern.of("Body")),
            RecordPatternField.of("host", VariablePattern.of("Host"))));
  }

  private static MatchExpr sigv4ConfigMatch(String credentialsMod) {
    return MatchExpr.bindValue(
        "Config1",
        CaseExpr.of(
            RemoteCallExpr.of(
                "maps",
                "get",
                List.of(
                    AtomExpr.of("credentials"),
                    Variable.of("Config"),
                    AtomExpr.of("undefined"))),
            List.of(
                Clause.of(
                    AtomPattern.of("undefined"),
                    CaseExpr.of(
                        RemoteCallExpr.of(
                            credentialsMod, "resolve", List.of(Variable.of("Config"))),
                        List.of(
                            Clause.of(
                                TuplePattern.of(
                                    List.of(
                                        AtomPattern.of("ok"),
                                        VariablePattern.of("Creds"))),
                                MapExpr.of(
                                    Variable.of("Config"),
                                    List.of(
                                        MapEntry.of(
                                            AtomExpr.of("credentials"),
                                            Variable.of("Creds"))))),
                            Clause.of(
                                VariablePattern.of("_"), Variable.of("Config"))))),
                Clause.of(VariablePattern.of("_"), Variable.of("Config")))));
  }

  private static MatchExpr resolveBaseUrlMatch(
      String configVar, String helpersMod, String endpointsMod, boolean endpointRules) {
    Clause endpointPrefixFallback;
    if (endpointRules) {
      endpointPrefixFallback =
          Clause.of(
              VariablePattern.of("_"),
              CaseExpr.of(
                  RemoteCallExpr.of(
                      endpointsMod,
                      "resolve",
                      List.of(Variable.of(configVar), MapExpr.of(List.of()))),
                  List.of(
                      Clause.of(
                          TuplePattern.of(
                              List.of(
                                  AtomPattern.of("ok"),
                                  MapPattern.of(
                                      List.of(
                                          MapPatternEntry.of(
                                              AtomExpr.of("url"),
                                              VariablePattern.of("ResolvedUrl"), true))))),
                          Variable.of("ResolvedUrl")),
                      Clause.of(
                          VariablePattern.of("_"),
                          RemoteCallExpr.of(
                              helpersMod,
                              "resolve_base_url",
                              List.of(Variable.of(configVar)))))));
    } else {
      endpointPrefixFallback =
          Clause.of(
              VariablePattern.of("_"),
              RemoteCallExpr.of(
                  helpersMod, "resolve_base_url", List.of(Variable.of(configVar))));
    }

    return MatchExpr.bindValue(
        "BaseUrl",
        CaseExpr.of(
            RemoteCallExpr.of(
                "maps",
                "get",
                List.of(
                    AtomExpr.of("base_url"),
                    Variable.of(configVar),
                    AtomExpr.of("undefined"))),
            List.of(
                Clause.of(
                    AtomPattern.of("undefined"),
                    CaseExpr.of(
                        RemoteCallExpr.of(
                            "maps",
                            "get",
                            List.of(
                                AtomExpr.of("endpoint_prefix"),
                                Variable.of(configVar),
                                AtomExpr.of("undefined"))),
                        List.of(
                            Clause.of(AtomPattern.of("undefined"), BinaryExpr.of("")),
                            endpointPrefixFallback))),
                Clause.of(VariablePattern.of("GivenUrl"), Variable.of("GivenUrl")))));
  }

  private static MatchExpr queryStringMatch() {
    return MatchExpr.bindValue(
        "QueryStr",
        CaseExpr.of(
            RemoteCallExpr.of("maps", "to_list", List.of(Variable.of("Query"))),
            List.of(
                Clause.of(ListPattern.of(List.of()), BinaryExpr.of("")),
                Clause.of(
                    VariablePattern.of("Pairs"),
                    BlockExpr.commaSeparated(
                        List.of(
                            MatchExpr.bindValue(
                                "Encoded",
                                RemoteCallExpr.of(
                                    "uri_string",
                                    "compose_query",
                                    List.of(
                                        ListComprehensionExpr.of(
                                            TupleExpr.of(
                                                List.of(
                                                    Variable.of("K"), Variable.of("V"))),
                                            TuplePattern.of(
                                                List.of(
                                                    VariablePattern.of("K"),
                                                    VariablePattern.of("V"))),
                                            Variable.of("Pairs"),
                                            List.of())))),
                            BinaryExpr.of(
                                List.of(
                                    BinarySegmentExpr.literal("?"),
                                    BinarySegmentExpr.of(
                                        Variable.of("Encoded"), "binary")))),
                        false)))));
  }

  private static MatchExpr authorityMatch() {
    return MatchExpr.bindValue(
        "Authority",
        CaseExpr.of(
            Variable.of("Host"),
            List.of(
                Clause.of(
                    AtomPattern.of("undefined"), Variable.of("DefaultAuthority")),
                Clause.of(VariablePattern.of("_"), Variable.of("Host")))));
  }

  private static MatchExpr httpcHeadersMatch() {
    return MatchExpr.bindValue(
        "HttpcHeaders",
        ListComprehensionExpr.of(
            TupleExpr.of(
                List.of(
                    LocalCallExpr.of("binary_to_list", List.of(Variable.of("K"))),
                    LocalCallExpr.of("binary_to_list", List.of(Variable.of("V"))))),
            TuplePattern.of(
                List.of(VariablePattern.of("K"), VariablePattern.of("V"))),
            Variable.of("Headers"),
            List.of()));
  }

  private static MatchExpr requestTupleMatch() {
    return MatchExpr.bindValue(
        "Req",
        CaseExpr.of(
            Variable.of("Body"),
            List.of(
                Clause.of(
                    BinaryPattern.of(""),
                    TupleExpr.of(
                        List.of(
                            LocalCallExpr.of(
                                "binary_to_list", List.of(Variable.of("ReqUrl"))),
                            Variable.of("HttpcHeaders")))),
                Clause.of(
                    VariablePattern.of("_"),
                    TupleExpr.of(
                        List.of(
                            LocalCallExpr.of(
                                "binary_to_list", List.of(Variable.of("ReqUrl"))),
                            Variable.of("HttpcHeaders"),
                            LocalCallExpr.of("mime", List.of(Variable.of("Headers"))),
                            Variable.of("Body")))))));
  }

  private static CaseExpr httpClientRequestCase() {
    MatchExpr binHeadersMatch =
        MatchExpr.bindValue(
            "BinHeaders",
            ListComprehensionExpr.of(
                TupleExpr.of(
                    List.of(
                        LocalCallExpr.of("list_to_binary", List.of(Variable.of("K"))),
                        LocalCallExpr.of("list_to_binary", List.of(Variable.of("V"))))),
                TuplePattern.of(
                    List.of(VariablePattern.of("K"), VariablePattern.of("V"))),
                Variable.of("RespHeaders"),
                List.of()));

    Expression okResponse =
        TupleExpr.of(
            List.of(
                AtomExpr.of("ok"),
                RecordExpr.of(
                    "http_response",
                    List.of(
                        RecordField.of("status", Variable.of("Status")),
                        RecordField.of("headers", Variable.of("BinHeaders")),
                        RecordField.of("body", Variable.of("RespBody"))))));

    Clause okClause =
        Clause.of(
            TuplePattern.of(
                List.of(
                    AtomPattern.of("ok"),
                    TuplePattern.of(
                        List.of(
                            TuplePattern.of(
                                List.of(
                                    VariablePattern.of("_"),
                                    VariablePattern.of("Status"),
                                    VariablePattern.of("_"))),
                            VariablePattern.of("RespHeaders"),
                            VariablePattern.of("RespBody"))))),
            BlockExpr.commaSeparated(List.of(binHeadersMatch, okResponse), false));

    Clause errorClause =
        Clause.of(
            TuplePattern.of(
                List.of(AtomPattern.of("error"), VariablePattern.of("Reason"))),
            TupleExpr.of(List.of(AtomExpr.of("error"), Variable.of("Reason"))));

    return CaseExpr.of(
        RemoteCallExpr.of(
            Variable.of("HttpClient"),
            AtomExpr.of("request"),
            List.of(
                LocalCallExpr.of(
                    "binary_to_atom",
                    List.of(
                        RemoteCallExpr.of(
                            "string",
                            "lowercase",
                            List.of(Variable.of("Method"))),
                        AtomExpr.of("utf8"))),
                Variable.of("Req"),
                ListExpr.of(List.of()),
                ListExpr.of(
                    List.of(
                        TupleExpr.of(
                            List.of(AtomExpr.of("body_format"), AtomExpr.of("binary"))))))),
        List.of(okClause, errorClause));
  }
}
