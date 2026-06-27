package io.smithy.beam.erlang;

import io.smithy.beam.ir.erlang.ErlAtom;
import io.smithy.beam.ir.erlang.ErlAttribute;
import io.smithy.beam.ir.erlang.ErlBinary;
import io.smithy.beam.ir.erlang.ErlBinaryPattern;
import io.smithy.beam.ir.erlang.ErlCallLocal;
import io.smithy.beam.ir.erlang.ErlCapturedBlock;
import io.smithy.beam.ir.erlang.ErlClause;
import io.smithy.beam.ir.erlang.ErlComment;
import io.smithy.beam.ir.erlang.ErlConsPattern;
import io.smithy.beam.ir.erlang.ErlExportAttribute;
import io.smithy.beam.ir.erlang.ErlFunction;
import io.smithy.beam.ir.erlang.ErlGuard;
import io.smithy.beam.ir.erlang.ErlInteger;
import io.smithy.beam.ir.erlang.ErlList;
import io.smithy.beam.ir.erlang.ErlModule;
import io.smithy.beam.ir.erlang.ErlNilPattern;
import io.smithy.beam.ir.erlang.ErlOp;
import io.smithy.beam.ir.erlang.ErlRecordFieldPattern;
import io.smithy.beam.ir.erlang.ErlRecordPattern;
import io.smithy.beam.ir.erlang.ErlTuple;
import io.smithy.beam.ir.erlang.ErlTypeDef;
import io.smithy.beam.ir.erlang.ErlVar;
import io.smithy.beam.ir.erlang.ErlVarPattern;
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

  static ErlModule sigV4Module(
      String sigV4Mod, String runtimeTypesHeaderFile, ServiceShape service) {
    return new ErlModule(
        sigV4Mod,
        List.of(ErlComment.comment("Generated SigV4 signing hook for " + service.getId() + ".")),
        List.of(
            new ErlAttribute("include", "\"" + runtimeTypesHeaderFile + "\""),
            ErlExportAttribute.export(
                List.of("sign/3", "presign/5", "endpoint_host_from_config/1")),
            clientConfigType()),
        sigV4Functions());
  }

  static ErlFunction sign() {
    return ErlFunction.functionWithSpec(
        "sign",
        3,
        SIGN_INPUT,
        "http_request()",
        List.of(
            ErlClause.blockClause(
                List.of(
                    ErlVarPattern.varPattern("Config"),
                    ErlVarPattern.varPattern("Operation"),
                    ErlVarPattern.varPattern("Request")),
                ErlCapturedBlock.capturedBlock(
                    """
                                Credentials = maps:get(credentials, Config),
                                Region = maps:get(region, Config, <<\"us-east-1\">>),
                                Service = maps:get(signing_name, Config),
                                Unsigned = maps:get({unsigned_payload, Operation}, Config, false),
                                Opts = #{
                                    unsigned_payload => Unsigned,
                                    endpoint_host => endpoint_host_from_config(Config)
                                },
                                sign_request(Request, Credentials, Region, Service, Opts)"""))));
  }

  static ErlFunction presign() {
    return ErlFunction.functionWithSpec(
        "presign",
        5,
        SIGN_REQUEST_INPUT,
        PRESIGN_RESULT,
        List.of(
            ErlClause.blockClause(
                List.of(
                    ErlVarPattern.varPattern("Request"),
                    ErlVarPattern.varPattern("Credentials"),
                    ErlVarPattern.varPattern("Region"),
                    ErlVarPattern.varPattern("Service"),
                    ErlVarPattern.varPattern("Opts")),
                ErlCapturedBlock.capturedBlock(
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
                        ] ++
                            body_digest_option(Opts) ++
                            session_token_option(maps:get(session_token, Credentials, undefined)),
                    try
                        {ok,
                            aws_signature:sign_v4_query_params(
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
                    end"""))));
  }

  static ErlFunction signRequest() {
    return ErlFunction.functionWithSpec(
        "sign_request",
        5,
        SIGN_REQUEST_INPUT,
        "http_request()",
        List.of(
            ErlClause.blockClause(
                List.of(
                    ErlVarPattern.varPattern("Request"),
                    ErlVarPattern.varPattern("Credentials"),
                    ErlVarPattern.varPattern("Region"),
                    ErlVarPattern.varPattern("Service"),
                    ErlVarPattern.varPattern("Opts")),
                ErlCapturedBlock.capturedBlock(
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
                                Request#http_request{headers = SignedHeaders}"""))));
  }

  static List<ErlFunction> helperFunctions() {
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
        endpointHostFromConfig(),
        splitBaseUrl());
  }

  private static List<ErlFunction> sigV4Functions() {
    List<ErlFunction> functions = new ArrayList<>();
    functions.add(sign());
    functions.add(presign());
    functions.add(signRequest());
    functions.addAll(helperFunctions());
    return functions;
  }

  private static ErlTypeDef clientConfigType() {
    return new ErlTypeDef("client_config", "#{binary() => term()}");
  }

  private static ErlFunction resolveHost() {
    return ErlFunction.function(
        "resolve_host",
        2,
        List.of(
            ErlClause.blockClause(
                List.of(
                    ErlRecordPattern.recordPattern(
                        "http_request",
                        ErlRecordFieldPattern.fieldPattern(
                            "host", ErlVarPattern.varPattern("Host")),
                        ErlRecordFieldPattern.fieldPattern(
                            "headers", ErlVarPattern.varPattern("Headers"))),
                    ErlVarPattern.varPattern("Opts")),
                ErlCapturedBlock.capturedBlock(
                    """
                                coalesce([
                                    Host,
                                    maps:get(host, Opts, undefined),
                                    maps:get(endpoint_host, Opts, undefined),
                                    header_host(Headers)
                                ])"""))));
  }

  private static ErlFunction coalesce() {
    return ErlFunction.function(
        "coalesce",
        1,
        List.of(
            ErlClause.blockClause(
                List.of(
                    ErlConsPattern.consPattern(
                        ErlVarPattern.varPattern("H"), ErlVarPattern.varPattern("Rest"))),
                ErlCapturedBlock.capturedBlock(
                    """
                                        case H of
                                            undefined -> coalesce(Rest);
                                            <<>> -> coalesce(Rest);
                                            Value -> Value
                                        end""")),
            ErlClause.clause(List.of(ErlNilPattern.nilPattern()), ErlBinary.binary("localhost"))));
  }

  private static ErlFunction buildUrl() {
    return ErlFunction.function(
        "build_url",
        3,
        List.of(
            ErlClause.blockClause(
                List.of(
                    ErlVarPattern.varPattern("Host"),
                    ErlVarPattern.varPattern("Path"),
                    ErlVarPattern.varPattern("Query")),
                ErlCapturedBlock.capturedBlock(
                    """
                                <<\"https://\", Host/binary, Path/binary, (query_suffix(Query))/binary>>"""))));
  }

  private static ErlFunction querySuffix() {
    return ErlFunction.function(
        "query_suffix",
        1,
        List.of(
            ErlClause.clause(
                List.of(ErlVarPattern.varPattern("Query")),
                List.of(
                    ErlGuard.exprGuard(
                        ErlOp.op(
                            "=:=",
                            ErlCallLocal.callLocal("map_size", ErlVar.var("Query")),
                            ErlInteger.integer(0)))),
                ErlBinary.binary("")),
            ErlClause.blockClause(
                List.of(ErlVarPattern.varPattern("Query")),
                ErlCapturedBlock.capturedBlock(
                    """
                                        Params = uri_string:compose_query([{K, V} || {K, V} <- maps:to_list(Query)]),
                                        <<\"?\", Params/binary>>"""))));
  }

  private static ErlFunction ensureHostHeader() {
    return ErlFunction.function(
        "ensure_host_header",
        2,
        List.of(
            ErlClause.blockClause(
                List.of(ErlVarPattern.varPattern("Headers"), ErlVarPattern.varPattern("Host")),
                ErlCapturedBlock.capturedBlock(
                    """
                                case header_host(Headers) of
                                    undefined -> [{<<\"host\">>, Host} | Headers];
                                    _ -> Headers
                                end"""))));
  }

  private static ErlFunction headerHost() {
    return ErlFunction.function(
        "header_host",
        1,
        List.of(
            ErlClause.blockClause(
                List.of(ErlVarPattern.varPattern("Headers")),
                ErlCapturedBlock.capturedBlock(
                    """
                                proplists:get_value(<<\"host\">>, Headers, proplists:get_value(<<\"Host\">>, Headers))"""))));
  }

  private static ErlFunction maybeAddSessionToken() {
    return ErlFunction.function(
        "maybe_add_session_token",
        2,
        List.of(
            ErlClause.clause(
                List.of(ErlVarPattern.varPattern("Headers"), ErlVarPattern.varPattern("undefined")),
                ErlVar.var("Headers")),
            ErlClause.blockClause(
                List.of(ErlVarPattern.varPattern("Headers"), ErlVarPattern.varPattern("Token")),
                ErlCapturedBlock.capturedBlock(
                    """
                                        case proplists:get_value(<<\"x-amz-security-token\">>, Headers) of
                                            undefined -> [{<<\"x-amz-security-token\">>, Token} | Headers];
                                            _ -> Headers
                                        end"""))));
  }

  private static ErlFunction signOptions() {
    return ErlFunction.function(
        "sign_options",
        2,
        List.of(
            ErlClause.blockClause(
                List.of(ErlVarPattern.varPattern("Service"), ErlVarPattern.varPattern("Opts")),
                ErlCapturedBlock.capturedBlock(
                    """
                                [{uri_encode_path, Service =/= <<\"s3\">>}] ++ body_digest_option(Opts)"""))));
  }

  private static ErlFunction bodyDigestOption() {
    return ErlFunction.function(
        "body_digest_option",
        1,
        List.of(
            ErlClause.blockClause(
                List.of(ErlVarPattern.varPattern("Opts")),
                ErlCapturedBlock.capturedBlock(
                    """
                                case maps:get(unsigned_payload, Opts, false) of
                                    true -> [{body_digest, <<\"UNSIGNED-PAYLOAD\">>}];
                                    false -> []
                                end"""))));
  }

  private static ErlFunction sessionTokenOption() {
    return ErlFunction.function(
        "session_token_option",
        1,
        List.of(
            ErlClause.clause(List.of(ErlVarPattern.varPattern("undefined")), ErlList.list()),
            ErlClause.clause(
                List.of(ErlVarPattern.varPattern("Token")),
                ErlList.list(ErlTuple.tuple(ErlAtom.atom("session_token"), ErlVar.var("Token"))))));
  }

  private static ErlFunction endpointHostFromConfig() {
    return ErlFunction.function(
        "endpoint_host_from_config",
        1,
        List.of(
            ErlClause.blockClause(
                List.of(ErlVarPattern.varPattern("Config")),
                ErlCapturedBlock.capturedBlock(
                    """
                    case maps:get(base_url, Config, undefined) of
                        undefined ->
                            case
                                {
                                    maps:get(endpoint_prefix, Config, undefined),
                                    maps:get(region, Config, <<\"us-east-1\">>)
                                }
                            of
                                {undefined, _} -> undefined;
                                {Prefix, Region} -> <<Prefix/binary, \".\", Region/binary, \".amazonaws.com\">>
                            end;
                        BaseUrl ->
                            {_Scheme, Authority} = split_base_url(BaseUrl),
                            Authority
                    end"""))));
  }

  private static ErlFunction splitBaseUrl() {
    return ErlFunction.function(
        "split_base_url",
        1,
        List.of(
            ErlClause.clause(
                List.of(ErlBinaryPattern.binaryPattern("")),
                ErlTuple.tuple(ErlBinary.binary(""), ErlBinary.binary(""))),
            ErlClause.blockClause(
                List.of(ErlVarPattern.varPattern("BaseUrl")),
                ErlCapturedBlock.capturedBlock(
                    """
                    case uri_string:parse(binary_to_list(BaseUrl)) of
                        #{scheme := Scheme, host := Host} = Parts ->
                            PortSuffix =
                                case maps:get(port, Parts, undefined) of
                                    undefined -> <<>>;
                                    Port -> <<\":\", (integer_to_binary(Port))/binary>>
                                end,
                            {<<(list_to_binary(Scheme))/binary, \"://\">>, <<
                                (list_to_binary(Host))/binary, PortSuffix/binary
                            >>};
                        _ ->
                            {<<>>, BaseUrl}
                    end"""))));
  }
}
