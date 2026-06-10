package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamContextParamsIndex;
import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.core.BeamEndpointRuleSetEmitter;
import software.amazon.smithy.model.shapes.ServiceShape;

import java.util.Map;

/**
 * Emits {@code <service>_endpoints.erl} when the model defines {@code @endpointRuleSet}.
 */
public final class ErlangEndpointRulesEmitter {

    private ErlangEndpointRulesEmitter() {}

    public static void emit(ErlangContext ctx, ServiceShape service) {
        if (!BeamEndpointRuleSetEmitter.hasRuleSet(ctx.model(), service)) {
            return;
        }

        BeamErlangLayout layout = new BeamErlangLayout(
                ctx.settings(), service.getId().getNamespace(), service);
        String endpointsModule = layout.endpointsModuleName();
        Map<String, String> clientContextKeys = BeamContextParamsIndex.clientContextConfigKeys(service);

        ctx.writerDelegator().useFileWriter(layout.endpointsModuleFile(), writer -> {
            writer.write("%% Generated endpoint rule resolver for $L.", service.getId());
            writer.write("-module($L).", endpointsModule);
            writer.write("-include(\"$L\").", layout.runtimeTypesHeaderFile());
            writer.write("-export([resolve/2]).");
            writer.write("");
            writer.write("-type client_config() :: #{binary() => term()}.");
            writer.write("-type endpoint_params() :: #{binary() => term()}.");
            writer.write("");
            ErlangFormat.writeSpec(
                    writer,
                    "resolve(client_config(), endpoint_params()) -> {ok, #{url := binary()}} | {error, term()}");
            writer.write("resolve(Config, Params) ->");
            writer.indent();
            writer.write("aws_endpoint_rules:evaluate(?ENDPOINT_RULE_SET, merge_params(Config, Params)).");
            writer.dedent();
            writer.write("");
            writeMergeParams(writer, clientContextKeys);
        });
    }

    private static void writeMergeParams(ErlangWriter writer, Map<String, String> clientContextKeys) {
        writer.write("merge_params(Config, Params) ->");
        writer.indent();
        writer.write("ConfigParams = config_to_rule_params(Config),");
        writer.write("ClientParams = client_context_params(Config),");
        writer.write("maps:merge(maps:merge(ConfigParams, ClientParams), Params).");
        writer.dedent();
        writer.write("");
        writer.write("config_to_rule_params(Config) ->");
        writer.indent();
        writer.write("Region = maps:get(region, Config, undefined),");
        writer.write("case Region of");
        writer.indent();
        writer.write("undefined -> #{};");
        writer.write("Value -> #{<<\"Region\">> => Value}");
        writer.dedent();
        writer.write("end.");
        writer.dedent();
        writer.write("");
        writer.write("client_context_params(Config) ->");
        writer.indent();
        if (clientContextKeys.isEmpty()) {
            writer.write("#{}.");
        } else {
            writer.write("maps:merge(");
            boolean first = true;
            for (Map.Entry<String, String> entry : clientContextKeys.entrySet()) {
                if (!first) {
                    writer.write(",");
                }
                first = false;
                writer.write("optional_param(Config, $L, <<\"$L\">>)", entry.getValue(), entry.getKey());
            }
            writer.write(").");
        }
        writer.dedent();
        writer.write("");
        writer.write("optional_param(Config, Key, RuleKey) ->");
        writer.indent();
        writer.write("case maps:get(Key, Config, undefined) of");
        writer.indent();
        writer.write("undefined -> #{};");
        writer.write("Value -> #{RuleKey => Value}");
        writer.dedent();
        writer.write("end.");
        writer.dedent();
    }
}
