package io.smithy.beam.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.smithy.beam.erlang.ErlangServerPlugin;
import java.net.URL;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.FileManifest;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.codegen.core.CodegenException;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

class ErlangServerPluginTest {

  private static final String TYPES_FILE = "basic_service_types.hrl";
  private static final String SERVER_FILE = "basic_service_server.erl";
  private static final String BEHAVIOUR_FILE = "basic_service_behaviour.erl";

  private static Model loadModel() {
    URL resource = ErlangServerPluginTest.class.getResource("/model/basic.smithy");
    assertThat(resource).isNotNull();
    return Model.assembler().addImport(resource).discoverModels().assemble().unwrap();
  }

  private static PluginContext buildContext(Model model, FileManifest manifest) {
    ObjectNode settings =
        ObjectNode.builder()
            .withMember("service", "smithy.beam.demo.basic#BasicService")
            .withMember("edition", "2026")
            .build();
    return PluginContext.builder().model(model).fileManifest(manifest).settings(settings).build();
  }

  @Test
  void emitsTypesHeaderAndServerStubOnManifest() {
    Model model = loadModel();
    MockManifest manifest = new MockManifest();

    new ErlangServerPlugin().execute(buildContext(model, manifest));

    assertThat(manifest.expectFileString(TYPES_FILE)).contains("-type basic_string()");
    assertBehaviourModule(manifest.expectFileString(BEHAVIOUR_FILE));
    assertServerDispatcher(manifest.expectFileString(SERVER_FILE));
    assertThat(manifest.getFileString("basic_service_rest_json_1.erl")).isPresent();
    assertThat(manifest.getFileString("basic_service_router.erl")).isPresent();
  }

  @Test
  void defaultsToOnlyServiceWhenServiceSettingOmitted() {
    MockManifest manifest = new MockManifest();
    ObjectNode settings = ObjectNode.builder().withMember("edition", "2026").build();
    PluginContext context =
        PluginContext.builder()
            .model(loadModel())
            .fileManifest(manifest)
            .settings(settings)
            .build();
    new ErlangServerPlugin().execute(context);
    assertThat(manifest.expectFileString(TYPES_FILE)).contains("-type basic_string()");
    assertBehaviourModule(manifest.expectFileString(BEHAVIOUR_FILE));
    assertServerDispatcher(manifest.expectFileString(SERVER_FILE));
  }

  private static void assertBehaviourModule(String source) {
    assertThat(source).contains("-module(basic_service_behaviour).");
    assertThat(source).contains("-include(\"basic_service_types.hrl\").");
    assertThat(source).contains("-callback handle_get_type_closure(");
    assertThat(source).contains("Input :: get_type_closure_input()");
  }

  private static void assertServerDispatcher(String source) {
    assertThat(source).contains("-module(basic_service_server).");
    assertThat(source).contains("-behaviour(basic_service_behaviour).");
    assertThat(source).contains("-export([init_handlers/0");
    assertThat(source).contains("-define(DEFAULT_IMPL, basic_service_impl).");
    assertThat(source).contains("-define(HANDLERS_KEY, {basic_service_server, handlers}).");
    assertThat(source).contains("resolve_impl(Impl) ->");
    assertThat(source).contains("basic_service_behaviour:behaviour_info(callbacks)");
    assertThat(source).contains("erlang:function_exported(Impl, Fun, 3)");
    assertThat(source).contains("init_handlers() ->");
    assertThat(source).contains("persistent_term:put(?HANDLERS_KEY, Handlers)");
    assertThat(source).contains("dispatch_handler(Fun, Ctx, Input, Meta) ->");
    assertThat(source).contains("dispatch_handler(handle_get_type_closure, Ctx, Input, Meta)");
    assertThat(source).doesNotContain("Impl = maps:get(impl, Ctx");
    assertThat(source).doesNotContain("{error, not_implemented}.");
    int moduleIndex = source.indexOf("-module(basic_service_server).");
    int behaviourIndex = source.indexOf("-behaviour(basic_service_behaviour).");
    int exportIndex = source.indexOf("-export([init_handlers/0");
    int includeIndex = source.indexOf("-include(\"basic_service_types.hrl\").");
    assertThat(moduleIndex).isLessThan(behaviourIndex);
    assertThat(behaviourIndex).isLessThan(exportIndex);
    assertThat(exportIndex).isLessThan(includeIndex);
  }

  @Test
  void multipleServicesWithoutExplicitServiceSettingFails() {
    URL resource = ErlangServerPluginTest.class.getResource("/model/multi_service.smithy");
    assertThat(resource).isNotNull();
    Model model = Model.assembler().addImport(resource).discoverModels().assemble().unwrap();
    ObjectNode settings = ObjectNode.builder().withMember("edition", "2026").build();
    PluginContext context =
        PluginContext.builder()
            .model(model)
            .fileManifest(new MockManifest())
            .settings(settings)
            .build();
    assertThatThrownBy(() -> new ErlangServerPlugin().execute(context))
        .isInstanceOf(CodegenException.class)
        .hasMessageContaining("service");
  }

  @Test
  void missingEditionFails() {
    MockManifest manifest = new MockManifest();
    ObjectNode settings =
        ObjectNode.builder().withMember("service", "smithy.beam.demo.basic#BasicService").build();
    PluginContext context =
        PluginContext.builder()
            .model(loadModel())
            .fileManifest(manifest)
            .settings(settings)
            .build();
    assertThatThrownBy(() -> new ErlangServerPlugin().execute(context))
        .isInstanceOf(CodegenException.class)
        .hasMessageContaining("edition");
  }

  @Test
  void relativeDateAndRelativeVersionDoNotChangeTypesOrServerStubOutput() {
    URL resource = ErlangServerPluginTest.class.getResource("/model/dedicated_operation_io.smithy");
    assertThat(resource).isNotNull();
    Model model = Model.assembler().addImport(resource).discoverModels().assemble().unwrap();
    MockManifest baseline = new MockManifest();
    ObjectNode baselineSettings =
        ObjectNode.builder()
            .withMember("service", "smithy.beam.demo.dedicated_io#DedicatedIoService")
            .withMember("edition", "2026")
            .build();
    new ErlangServerPlugin()
        .execute(
            PluginContext.builder()
                .model(model)
                .fileManifest(baseline)
                .settings(baselineSettings)
                .build());
    MockManifest extended = new MockManifest();
    ObjectNode extendedSettings =
        ObjectNode.builder()
            .withMember("service", "smithy.beam.demo.dedicated_io#DedicatedIoService")
            .withMember("edition", "2026")
            .withMember("relativeDate", "2026-01-01")
            .withMember("relativeVersion", "1.0.0")
            .build();
    new ErlangServerPlugin()
        .execute(
            PluginContext.builder()
                .model(model)
                .fileManifest(extended)
                .settings(extendedSettings)
                .build());
    assertThat(extended.expectFileString("dedicated_io_service_types.hrl"))
        .isEqualTo(baseline.expectFileString("dedicated_io_service_types.hrl"));
    assertThat(extended.expectFileString("dedicated_io_service_server.erl"))
        .isEqualTo(baseline.expectFileString("dedicated_io_service_server.erl"));
    assertThat(extended.expectFileString("dedicated_io_service_behaviour.erl"))
        .isEqualTo(baseline.expectFileString("dedicated_io_service_behaviour.erl"));
  }

  @Test
  void unsupportedModelProtocolFailsWithCodegenException() {
    URL resource = ErlangServerPluginTest.class.getResource("/model/multi_service.smithy");
    assertThat(resource).isNotNull();
    Model model = Model.assembler().addImport(resource).discoverModels().assemble().unwrap();
    MockManifest manifest = new MockManifest();
    ObjectNode settings =
        ObjectNode.builder()
            .withMember("service", "smithy.beam.demo.multi#ServiceA")
            .withMember("edition", "2026")
            .withMember("relativeDate", "2026-01-01")
            .withMember("relativeVersion", "1.0.0")
            .build();
    assertThatThrownBy(
            () ->
                new ErlangServerPlugin()
                    .execute(
                        PluginContext.builder()
                            .model(model)
                            .fileManifest(manifest)
                            .settings(settings)
                            .build()))
        .isInstanceOf(CodegenException.class)
        .hasMessageContaining("No BeamProtocolCodegen registered for protocol trait")
        .hasMessageContaining("smithy.beam.demo.multi#TestProtocol");
  }

  @Test
  void resourceLifecycleEmitsServerHelperModules() {
    URL resource = ErlangServerPluginTest.class.getResource("/model/resource_lifecycle.smithy");
    assertThat(resource).isNotNull();
    Model model = Model.assembler().addImport(resource).discoverModels().assemble().unwrap();
    MockManifest manifest = new MockManifest();
    ObjectNode settings =
        ObjectNode.builder()
            .withMember("service", "smithy.beam.demo.resource_lifecycle#ResourceLifecycleService")
            .withMember("edition", "2026")
            .build();
    new ErlangServerPlugin()
        .execute(
            PluginContext.builder().model(model).fileManifest(manifest).settings(settings).build());

    assertThat(manifest.getFileString("resource_lifecycle_service_rest_json_1.erl")).isPresent();
    assertThat(manifest.getFileString("resource_lifecycle_service_router.erl")).isPresent();

    String org = manifest.expectFileString("organization_resource.erl");
    assertThat(org).contains("-module(organization_resource).");
    assertThat(org).contains("handle_read(");
    assertThat(org).contains("resource_lifecycle_service_server:handle_get_organization(");
    assertThat(org)
        .contains(
            "resource_lifecycle_service_server:handle_create_organization(Ctx, Input, Meta).");
    assertThat(org).doesNotContain("Input#create_organization_input{}");
  }
}
