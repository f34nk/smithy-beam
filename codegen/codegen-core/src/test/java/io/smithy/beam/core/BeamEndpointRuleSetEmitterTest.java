package io.smithy.beam.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.rulesengine.language.EndpointRuleSet;
import software.amazon.smithy.rulesengine.traits.EndpointRuleSetTrait;

class BeamEndpointRuleSetEmitterTest {

  private static final ShapeId SERVICE =
      ShapeId.from("smithy.beam.test.endpoints#EndpointRulesService");

  @Test
  void serializesMinimalEndpointRuleSet() {
    Model model = loadModel();
    ServiceShape service = model.expectShape(SERVICE, ServiceShape.class);

    Optional<String> json = BeamEndpointRuleSetEmitter.serializeRuleSetJson(model, service);
    assertThat(json).isPresent();
    assertThat(json.get()).contains("\"version\"");
    assertThat(json.get()).contains("\"Region\"");
    assertThat(json.get()).contains("s3.{Region}.amazonaws.com");

    EndpointRuleSetTrait trait =
        BeamEndpointRuleSetEmitter.resolveRuleSetTrait(model, service).orElseThrow();
    EndpointRuleSet ruleSet = trait.getEndpointRuleSet();
    assertThat(ruleSet.getParameters()).hasSize(2);
    assertThat(ruleSet.getRules()).hasSize(3);

    String erlangMap = RuleSetSerializer.toErlangMap(trait);
    assertThat(erlangMap).startsWith("#{");
    assertThat(erlangMap).contains("<<\"Region\">>");
    assertThat(erlangMap).contains("s3.{Region}.amazonaws.com");
    assertThat(erlangMap).contains("<<\"argv\">> => [");
    assertThat(erlangMap).contains("<<\"rules\">> => [");
    assertThat(countChar(erlangMap, '[')).isEqualTo(countChar(erlangMap, ']'));

    String elixirMap = RuleSetSerializer.toElixirMap(trait);
    assertThat(elixirMap).contains("argv => [");
    assertThat(elixirMap).contains("rules => [");
    assertThat(countChar(elixirMap, '[')).isEqualTo(countChar(elixirMap, ']'));
  }

  private static int countChar(String value, char ch) {
    int count = 0;
    for (int i = 0; i < value.length(); i++) {
      if (value.charAt(i) == ch) {
        count++;
      }
    }
    return count;
  }

  private static Model loadModel() {
    URL resource =
        BeamEndpointRuleSetEmitterTest.class.getResource("/model/endpoint_rules_minimal.smithy");
    assertThat(resource).isNotNull();
    return Model.assembler().addImport(resource).discoverModels().assemble().unwrap();
  }
}
