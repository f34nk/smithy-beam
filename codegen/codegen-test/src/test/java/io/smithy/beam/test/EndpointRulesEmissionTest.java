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

    private static final ShapeId BASIC_SERVICE =
            ShapeId.from("smithy.beam.demo.basic#BasicService");

    @Test
    void erlangOmitsEndpointRuleSetWhenTraitAbsent() {
        MockManifest manifest = runErlangClient(loadBasicModel(), BASIC_SERVICE);

        String runtimeTypes = manifest.expectFileString("runtime_types.hrl");
        assertThat(runtimeTypes).doesNotContain("endpoint_rule_set");
        assertThat(runtimeTypes).doesNotContain("ENDPOINT_RULE_SET");
    }

    @Test
    void elixirOmitsEndpointRuleSetWhenTraitAbsent() {
        MockManifest manifest = runElixirClient(loadBasicModel(), BASIC_SERVICE);

        String runtimeTypes = manifest.expectFileString("runtime_types.ex");
        assertThat(runtimeTypes).doesNotContain("endpoint_rule_set");
    }

    @Test
    void erlangEmitsEndpointsModuleAndPrefersRulesResolver() {
        MockManifest manifest = runErlangClient(loadModel(), SERVICE);

        String runtimeTypes = manifest.expectFileString("runtime_types.hrl");
        assertThat(runtimeTypes).contains("-type endpoint_rule_set() :: map().");
        assertThat(runtimeTypes).contains("-define(ENDPOINT_RULE_SET,");
        assertThat(runtimeTypes).contains("s3.{Region}.amazonaws.com");

        String endpoints = manifest.expectFileString("endpoint_rules_service_endpoints.erl");
        assertThat(endpoints).contains("-module(endpoint_rules_service_endpoints).");
        assertThat(endpoints).contains("-include(\"runtime_types.hrl\").");
        assertThat(endpoints).contains("-export([resolve/2]).");
        assertThat(endpoints).doesNotContain("rule_set/0");
        assertThat(endpoints)
                .contains("aws_endpoint_rules:evaluate(?ENDPOINT_RULE_SET, merge_params(Config, Params)).");

        String http = manifest.expectFileString("runtime_http.erl");
        assertThat(http).contains("endpoint_rules_service_endpoints:resolve(Config, #{})");
        assertThat(http).contains("{ok, #{url := ResolvedUrl}} -> ResolvedUrl");
        assertThat(http.indexOf("endpoint_rules_service_endpoints:resolve"))
                .isLessThan(http.indexOf("runtime_helpers:resolve_base_url"));
        assertThat(runtimeTypes).contains("<<\"argv\">> => [");

        assertRuleEvaluationMatchesReference(loadModel());
    }

    @Test
    void elixirEmitsEndpointsModuleAndPrefersRulesResolver() {
        MockManifest manifest = runElixirClient(loadModel(), SERVICE);

        String runtimeTypes = manifest.expectFileString("runtime_types.ex");
        assertThat(runtimeTypes).contains("@type endpoint_rule_set :: map()");
        assertThat(runtimeTypes).contains("@endpoint_rule_set");
        assertThat(runtimeTypes).contains("s3.{Region}.amazonaws.com");

        String endpoints = manifest.expectFileString("endpoint_rules_service_endpoints.ex");
        assertThat(endpoints).contains("defmodule EndpointRulesServiceEndpoints do");
        assertThat(endpoints).contains("def resolve(config, params) do");
        assertThat(endpoints).contains("AwsEndpointRules.evaluate(@endpoint_rule_set, merge_params(config, params))");
        assertThat(endpoints).doesNotContain("def rule_set");

        String http = manifest.expectFileString("runtime_http.ex");
        assertThat(http).contains("EndpointRulesServiceEndpoints.resolve(config, %{})");
        assertThat(http.indexOf("EndpointRulesServiceEndpoints.resolve"))
                .isLessThan(http.indexOf("RuntimeHelpers.resolve_base_url"));
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

    private static MockManifest runErlangClient(Model model, ShapeId service) {
        MockManifest manifest = new MockManifest();
        new ErlangClientPlugin().execute(PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(ObjectNode.builder()
                        .withMember("service", service.toString())
                        .withMember("edition", "2026")
                        .build())
                .build());
        return manifest;
    }

    private static MockManifest runElixirClient(Model model, ShapeId service) {
        MockManifest manifest = new MockManifest();
        new ElixirClientPlugin().execute(PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(ObjectNode.builder()
                        .withMember("service", service.toString())
                        .withMember("edition", "2026")
                        .build())
                .build());
        return manifest;
    }

    private static Model loadBasicModel() {
        URL resource = EndpointRulesEmissionTest.class.getResource("/model/basic.smithy");
        assertThat(resource).isNotNull();
        return Model.assembler()
                .addImport(resource)
                .discoverModels()
                .assemble()
                .unwrap();
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
