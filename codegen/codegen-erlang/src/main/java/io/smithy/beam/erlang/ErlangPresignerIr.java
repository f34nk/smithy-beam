package io.smithy.beam.erlang;

import io.beam.ir.erlang.Function;
import io.beam.ir.erlang.FunctionClause;
import io.beam.ir.erlang.Module;
import io.beam.ir.erlang.OpaqueExpr;
import io.beam.ir.erlang.Spec;
import io.beam.ir.erlang.TypeAlias;
import io.beam.ir.erlang.VariablePattern;
import java.util.List;
import software.amazon.smithy.model.shapes.ServiceShape;

final class ErlangPresignerIr {
  private static final String CLIENT_CONFIG = "client_config()";

  private ErlangPresignerIr() {}

  static Module presignerModule(
      String presignerModule,
      String runtimeTypesHeaderFile,
      String sigv4Module,
      ServiceShape service) {
    return Module.of(
        presignerModule,
        List.of(presignUrl(sigv4Module)),
        List.of("Generated presigned URL helper for " + service.getId() + "."),
        null,
        List.of(runtimeTypesHeaderFile),
        List.of(TypeAlias.of("client_config", "#{binary() => term()}")),
        List.of("presign_url/3"));
  }

  static Function presignUrl(String sigv4Module) {
    return Function.of(
        "presign_url",
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
                    Expires = maps:get(presign_expires, Config, 900),
                    Unsigned = maps:get({unsigned_payload, Operation}, Config, false),
                    Opts = #{
                        expires => Expires,
                        unsigned_payload => Unsigned,
                        endpoint_host => %s:endpoint_host_from_config(Config)
                    },
                    %s:presign(Request, Credentials, Region, Service, Opts)"""
                        .formatted(sigv4Module, sigv4Module)
                        .strip()))),
        Spec.of(
            "presign_url(" + CLIENT_CONFIG + ", Operation :: atom(), http_request()) -> {ok, binary()} | {error, term()}"),
        null,
        null);
  }
}
