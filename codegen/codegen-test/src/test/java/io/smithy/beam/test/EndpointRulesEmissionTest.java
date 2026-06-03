package io.smithy.beam.test;

import io.smithy.beam.core.BeamEndpointRuleSetEmitter;
import io.smithy.beam.elixir.ElixirClientPlugin;
import io.smithy.beam.erlang.ErlangClientPlugin;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.rulesengine.language.EndpointRuleSet;
import software.amazon.smithy.rulesengine.language.evaluation.RuleEvaluator;
import software.amazon.smithy.rulesengine.language.evaluation.value.EndpointValue;
import software.amazon.smithy.rulesengine.language.evaluation.value.Value;
import software.amazon.smithy.rulesengine.language.syntax.Identifier;
import software.amazon.smithy.rulesengine.traits.EndpointRuleSetTrait;

import java.net.URL;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EndpointRulesEmissionTest {

    private static final ShapeId SERVICE =
            ShapeId.from("smithy.beam.test.endpoints#EndpointRulesService");

    @Test
    void erlangEmitsEndpointsModuleAndPrefersRulesResolver() {
        MockManifest manifest = runErlangClient();

        String endpoints = manifest.expectFileString("endpoint_rules_service_endpoints.erl");
        assertThat(endpoints).contains("-module(endpoint_rules_service_endpoints).");
        assertThat(endpoints).contains("-export([resolve/2, rule_set/0]).");
        assertThat(endpoints).contains("aws_endpoint_rules:evaluate(rule_set(), merge_params(Config, Params)).");
        assertThat(endpoints).contains("s3.{Region}.amazonaws.com");

        String http = manifest.expectFileString("runtime_http.erl");
        assertThat(http).contains("endpoint_rules_service_endpoints:resolve(Config, #{})");
        assertThat(http.indexOf("endpoint_rules_service_endpoints:resolve"))
                .isLessThan(http.indexOf("runtime_helpers:resolve_base_url"));

        assertManifestListsAwsEndpointRules(manifest);
        assertRuleEvaluationMatchesReference(loadModel());
    }

    @Test
    void elixirEmitsEndpointsModuleAndPrefersRulesResolver() {
        MockManifest manifest = runElixirClient();

        String endpoints = manifest.expectFileString("endpoint_rules_service_endpoints.ex");
        assertThat(endpoints).contains("defmodule EndpointRulesServiceEndpoints do");
        assertThat(endpoints).contains("def resolve(config, params) do");
        assertThat(endpoints).contains("AwsEndpointRules.evaluate(rule_set(), merge_params(config, params))");
        assertThat(endpoints).contains("s3.{Region}.amazonaws.com");

        String http = manifest.expectFileString("runtime_http.ex");
        assertThat(http).contains("EndpointRulesServiceEndpoints.resolve(config, %{})");
        assertThat(http.indexOf("EndpointRulesServiceEndpoints.resolve"))
                .isLessThan(http.indexOf("RuntimeHelpers.resolve_base_url"));

        assertManifestListsAwsEndpointRules(manifest);
    }

    private static void assertManifestListsAwsEndpointRules(MockManifest manifest) {
        String deps = manifest.expectFileString("smithy/beam_dependencies.json");
        assertThat(deps).contains("aws_endpoint_rules");
    }

    private static void assertRuleEvaluationMatchesReference(Model model) {
        ServiceShape service = model.expectShape(SERVICE, ServiceShape.class);
        EndpointRuleSetTrait trait = BeamEndpointRuleSetEmitter.resolveRuleSetTrait(model, service).orElseThrow();
        EndpointRuleSet ruleSet = trait.getEndpointRuleSet();

        Map<Identifier, Value> params = Map.of(
                Identifier.of("Region"), Value.stringValue("us-west-2"),
                Identifier.of("Bucket"), Value.stringValue("mybucket"));

        Value result = RuleEvaluator.evaluate(ruleSet, params);
        EndpointValue endpoint = result.expectEndpointValue();
        assertThat(endpoint.getUrl()).isEqualTo("https://mybucket.s3.us-west-2.amazonaws.com");
    }

    private static MockManifest runErlangClient() {
        MockManifest manifest = new MockManifest();
        new ErlangClientPlugin().execute(PluginContext.builder()
                .model(loadModel())
                .fileManifest(manifest)
                .settings(ObjectNode.builder()
                        .withMember("service", SERVICE.toString())
                        .withMember("edition", "2026")
                        .build())
                .build());
        return manifest;
    }

    private static MockManifest runElixirClient() {
        MockManifest manifest = new MockManifest();
        new ElixirClientPlugin().execute(PluginContext.builder()
                .model(loadModel())
                .fileManifest(manifest)
                .settings(ObjectNode.builder()
                        .withMember("service", SERVICE.toString())
                        .withMember("edition", "2026")
                        .build())
                .build());
        return manifest;
    }

    private static Model loadModel() {
        URL resource = EndpointRulesEmissionTest.class.getResource("/model/endpoint_rules_minimal.smithy");
        assertThat(resource).isNotNull();
        return Model.assembler()
                .addImport(resource)
                .discoverModels()
                .assemble()
                .unwrap();
    }
}
