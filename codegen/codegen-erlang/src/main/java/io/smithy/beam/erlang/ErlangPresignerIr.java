package io.smithy.beam.erlang;

import io.smithy.beam.ir.erlang.ErlAttribute;
import io.smithy.beam.ir.erlang.ErlCapturedBlock;
import io.smithy.beam.ir.erlang.ErlClause;
import io.smithy.beam.ir.erlang.ErlComment;
import io.smithy.beam.ir.erlang.ErlExportAttribute;
import io.smithy.beam.ir.erlang.ErlFunction;
import io.smithy.beam.ir.erlang.ErlModule;
import io.smithy.beam.ir.erlang.ErlTypeDef;
import io.smithy.beam.ir.erlang.ErlVarPattern;
import java.util.List;
import software.amazon.smithy.model.shapes.ServiceShape;

final class ErlangPresignerIr {
  private static final String CLIENT_CONFIG = "client_config()";

  private ErlangPresignerIr() {}

  static ErlModule presignerModule(
      String presignerModule,
      String runtimeTypesHeaderFile,
      String sigv4Module,
      ServiceShape service) {
    return new ErlModule(
        presignerModule,
        List.of(ErlComment.comment("Generated presigned URL helper for " + service.getId() + ".")),
        List.of(
            new ErlAttribute("include", "\"" + runtimeTypesHeaderFile + "\""),
            ErlExportAttribute.export(List.of("presign_url/3")),
            new ErlTypeDef("client_config", "#{binary() => term()}")),
        List.of(presignUrl(sigv4Module)));
  }

  static ErlFunction presignUrl(String sigv4Module) {
    return ErlFunction.functionWithSpec(
        "presign_url",
        3,
        CLIENT_CONFIG + ", Operation :: atom(), http_request()",
        "{ok, binary()} | {error, term()}",
        List.of(
            ErlClause.blockClause(
                List.of(
                    ErlVarPattern.varPattern("Config"),
                    ErlVarPattern.varPattern("Operation"),
                    ErlVarPattern.varPattern("Request")),
                ErlCapturedBlock.capturedBlock(
                    """
                                Credentials = maps:get(credentials, Config),
                                Region = maps:get(region, Config, <<"us-east-1">>),
                                Service = maps:get(signing_name, Config),
                                Expires = maps:get(presign_expires, Config, 900),
                                Unsigned = maps:get({unsigned_payload, Operation}, Config, false),
                                Opts = #{
                                    expires => Expires,
                                    unsigned_payload => Unsigned,
                                    endpoint_host => %s:endpoint_host_from_config(Config)
                                },
                                %s:presign(Request, Credentials, Region, Service, Opts)"""
                        .formatted(sigv4Module, sigv4Module)))));
  }
}
