package io.smithy.beam.erlang;

import io.beam.ir.erlang.AtomExpr;
import io.beam.ir.erlang.AtomPattern;
import io.beam.ir.erlang.BinaryExpr;
import io.beam.ir.erlang.BinaryPattern;
import io.beam.ir.erlang.ExpressionGuard;
import io.beam.ir.erlang.Function;
import io.beam.ir.erlang.FunctionClause;
import io.beam.ir.erlang.InfixExpr;
import io.beam.ir.erlang.IntegerExpr;
import io.beam.ir.erlang.ListExpr;
import io.beam.ir.erlang.ListPattern;
import io.beam.ir.erlang.LocalCallExpr;
import io.beam.ir.erlang.Module;
import io.beam.ir.erlang.OpaqueExpr;
import io.beam.ir.erlang.RecordPattern;
import io.beam.ir.erlang.RecordPatternField;
import io.beam.ir.erlang.Spec;
import io.beam.ir.erlang.TupleExpr;
import io.beam.ir.erlang.TypeAlias;
import io.beam.ir.erlang.Variable;
import io.beam.ir.erlang.VariablePattern;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.model.shapes.ServiceShape;

final class ErlangSigV4Ir {
  private static final String CLIENT_CONFIG = "client_config()";
  private static final String SIGN_INPUT = CLIENT_CONFIG + ", Operation :: atom(), http_request()";
  private static final String SIGN_REQUEST_INPUT =
      "http_request(), map(), binary(), binary(), map()";
  private static final String PRESIGN_RESULT = "{ok, binary()} | {error, term()}";

  private ErlangSigV4Ir() {}

  static Module sigV4Module(
      String sigV4Mod, String runtimeTypesHeaderFile, ServiceShape service) {
    return Module.of(
        sigV4Mod,
        sigV4Functions(),
        List.of("Generated SigV4 signing hook for " + service.getId() + "."),
        null,
        List.of(runtimeTypesHeaderFile),
        List.of(TypeAlias.of("client_config", "#{binary() => term()}")),
        List.of("sign/3", "presign/5", "endpoint_host_from_config/1"));
  }

  static Function sign() {
    return Function.of(
        "sign",
        List.of(
            FunctionClause.of(
                List.of(
                    VariablePattern.of("Config"),
                    VariablePattern.of("Operation"),
                    VariablePattern.of("Request")),
                OpaqueExpr.of(
                    """
                    Credentials = maps:get(credentials, Config),
                    Region = maps:get(region, Config, <<\"us-east-1\">>),
                    Service = maps:get(signing_name, Config),
                    Unsigned = maps:get({unsigned_payload, Operation}, Config, false),
                    Opts = #{
                        unsigned_payload => Unsigned,
                        endpoint_host => endpoint_host_from_config(Config)
                    },
                    sign_request(Request, Credentials, Region, Service, Opts)"""
                        .strip()))),
        Spec.of("sign(" + SIGN_INPUT + ") -> http_request()"),
        null,
        null);
  }

  static Function presign() {
    return Function.of(
        "presign",
        List.of(
            FunctionClause.of(
                List.of(
                    VariablePattern.of("Request"),
                    VariablePattern.of("Credentials"),
                    VariablePattern.of("Region"),
                    VariablePattern.of("Service"),
                    VariablePattern.of("Opts")),
                OpaqueExpr.of(
                    """
                    AccessKeyId = maps:get(access_key_id, Credentials),
                    SecretAccessKey = maps:get(secret_access_key, Credentials),
                    DateTime = calendar:universal_time(),
                    Host = resolve_host(Request, Opts),
                    Url = build_url(Host, Request#http_request.path, Request#http_request.query),
                    Ttl = maps:get(expires, Opts, 900),
                    QueryOpts =
                        [
                            {ttl, Ttl},
                            {uri_encode_path, Service =/= <<\"s3\">>}
                        ]
                        ++ body_digest_option(Opts)
                        ++ session_token_option(maps:get(session_token, Credentials, undefined)),
                    try
                        {ok, aws_signature:sign_v4_query_params(
                            AccessKeyId,
                            SecretAccessKey,
                            Region,
                            Service,
                            DateTime,
                            Request#http_request.method,
                            Url,
                            QueryOpts
                        )}
                    catch
                        _:Reason ->
                            {error, Reason}
                    end"""
                        .strip()))),
        Spec.of("presign(" + SIGN_REQUEST_INPUT + ") -> " + PRESIGN_RESULT),
        null,
        null);
  }

  static Function signRequest() {
    return Function.of(
        "sign_request",
        List.of(
            FunctionClause.of(
                List.of(
                    VariablePattern.of("Request"),
                    VariablePattern.of("Credentials"),
                    VariablePattern.of("Region"),
                    VariablePattern.of("Service"),
                    VariablePattern.of("Opts")),
                OpaqueExpr.of(
                    """
                    AccessKeyId = maps:get(access_key_id, Credentials),
                    SecretAccessKey = maps:get(secret_access_key, Credentials),
                    DateTime = calendar:universal_time(),
                    Host = resolve_host(Request, Opts),
                    Url = build_url(Host, Request#http_request.path, Request#http_request.query),
                    Headers0 = ensure_host_header(Request#http_request.headers, Host),
                    Headers1 = maybe_add_session_token(Headers0, maps:get(session_token, Credentials, undefined)),
                    SignOpts = sign_options(Service, Opts),
                    SignedHeaders = aws_signature:sign_v4(
                        AccessKeyId,
                        SecretAccessKey,
                        Region,
                        Service,
                        DateTime,
                        Request#http_request.method,
                        Url,
                        Headers1,
                        Request#http_request.body,
                        SignOpts
                    ),
                    Request#http_request{headers = SignedHeaders}"""
                        .strip()))),
        Spec.of("sign_request(" + SIGN_REQUEST_INPUT + ") -> http_request()"),
        null,
        null);
  }

  static List<Function> helperFunctions() {
    return List.of(
        resolveHost(),
        coalesce(),
        buildUrl(),
        querySuffix(),
        ensureHostHeader(),
        headerHost(),
        maybeAddSessionToken(),
        signOptions(),
        bodyDigestOption(),
        sessionTokenOption(),
        endpointHostFromConfig());
  }

  private static List<Function> sigV4Functions() {
    List<Function> functions = new ArrayList<>();
    functions.add(sign());
    functions.add(presign());
    functions.add(signRequest());
    functions.addAll(helperFunctions());
    return functions;
  }

  private static Function resolveHost() {
    return Function.of(
        "resolve_host",
        List.of(
            FunctionClause.of(
                List.of(
                    RecordPattern.bind(
                        "Req",
                        "http_request",
                        List.of(
                            RecordPatternField.of("host", VariablePattern.of("Host")),
                            RecordPatternField.of("headers", VariablePattern.of("Headers")))),
                    VariablePattern.of("Opts")),
                OpaqueExpr.of(
                    """
                    coalesce([
                        Host,
                        maps:get(host, Opts, undefined),
                        maps:get(endpoint_host, Opts, undefined),
                        header_host(Headers)
                    ])"""
                        .strip()))),
        null,
        null,
        null);
  }

  private static Function coalesce() {
    return Function.of(
        "coalesce",
        List.of(
            FunctionClause.of(
                List.of(
                    ListPattern.cons(VariablePattern.of("H"), VariablePattern.of("Rest"))),
                OpaqueExpr.of(
                    """
                    case H of
                        undefined -> coalesce(Rest);
                        <<>> -> coalesce(Rest);
                        Value -> Value
                    end"""
                        .strip())),
            FunctionClause.of(List.of(ListPattern.of(List.of())), BinaryExpr.of("localhost"))),
        null,
        null,
        null);
  }

  private static Function buildUrl() {
    return Function.of(
        "build_url",
        List.of(
            FunctionClause.of(
                List.of(
                    VariablePattern.of("Host"),
                    VariablePattern.of("Path"),
                    VariablePattern.of("Query")),
                OpaqueExpr.of(
                    """
                    <<\"https://\", Host/binary, Path/binary, (query_suffix(Query))/binary>>"""
                        .strip()))),
        null,
        null,
        null);
  }

  private static Function querySuffix() {
    return Function.of(
        "query_suffix",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Query")),
                ExpressionGuard.of(
                    InfixExpr.of(
                        LocalCallExpr.of("map_size", List.of(Variable.of("Query"))),
                        "=:=",
                        IntegerExpr.of(0))),
                BinaryExpr.of("")),
            FunctionClause.of(
                List.of(VariablePattern.of("Query")),
                OpaqueExpr.of(
                    """
                    Params = uri_string:compose_query([{K, V} || {K, V} <- maps:to_list(Query)]),
                    <<\"?\", Params/binary>>"""
                        .strip()))),
        null,
        null,
        null);
  }

  private static Function ensureHostHeader() {
    return Function.of(
        "ensure_host_header",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Headers"), VariablePattern.of("Host")),
                OpaqueExpr.of(
                    """
                    case header_host(Headers) of
                        undefined -> [{<<\"host\">>, Host} | Headers];
                        _ -> Headers
                    end"""
                        .strip()))),
        null,
        null,
        null);
  }

  private static Function headerHost() {
    return Function.of(
        "header_host",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Headers")),
                OpaqueExpr.of(
                    """
                    proplists:get_value(<<\"host\">>, Headers, proplists:get_value(<<\"Host\">>, Headers))"""
                        .strip()))),
        null,
        null,
        null);
  }

  private static Function maybeAddSessionToken() {
    return Function.of(
        "maybe_add_session_token",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Headers"), AtomPattern.of("undefined")),
                Variable.of("Headers")),
            FunctionClause.of(
                List.of(VariablePattern.of("Headers"), VariablePattern.of("Token")),
                OpaqueExpr.of(
                    """
                    case proplists:get_value(<<\"x-amz-security-token\">>, Headers) of
                        undefined -> [{<<\"x-amz-security-token\">>, Token} | Headers];
                        _ -> Headers
                    end"""
                        .strip()))),
        null,
        null,
        null);
  }

  private static Function signOptions() {
    return Function.of(
        "sign_options",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Service"), VariablePattern.of("Opts")),
                OpaqueExpr.of(
                    """
                    [{uri_encode_path, Service =/= <<\"s3\">>}] ++ body_digest_option(Opts)"""
                        .strip()))),
        null,
        null,
        null);
  }

  private static Function bodyDigestOption() {
    return Function.of(
        "body_digest_option",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Opts")),
                OpaqueExpr.of(
                    """
                    case maps:get(unsigned_payload, Opts, false) of
                        true -> [{body_digest, <<\"UNSIGNED-PAYLOAD\">>}];
                        false -> []
                    end"""
                        .strip()))),
        null,
        null,
        null);
  }

  private static Function sessionTokenOption() {
    return Function.of(
        "session_token_option",
        List.of(
            FunctionClause.of(List.of(VariablePattern.of("undefined")), ListExpr.of(List.of())),
            FunctionClause.of(
                List.of(VariablePattern.of("Token")),
                ListExpr.of(
                    List.of(
                        TupleExpr.of(
                            List.of(AtomExpr.of("session_token"), Variable.of("Token"))))))),
        null,
        null,
        null);
  }

  private static Function endpointHostFromConfig() {
    return Function.of(
        "endpoint_host_from_config",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Config")),
                OpaqueExpr.of(
                    """
                    case maps:get(base_url, Config, undefined) of
                        undefined ->
                            case {maps:get(endpoint_prefix, Config, undefined),
                                  maps:get(region, Config, <<\"us-east-1\">>)} of
                                {undefined, _} -> undefined;
                                {Prefix, Region} -> <<Prefix/binary, \".\", Region/binary, \".amazonaws.com\">>
                            end;
                        BaseUrl ->
                            {_Scheme, Authority} = runtime_http:split_base_url(BaseUrl),
                            Authority
                    end"""
                        .strip()))),
        null,
        null,
        null);
  }

}
