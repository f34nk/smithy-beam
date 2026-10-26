package io.smithy.beam.gleam;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.SmithyBuildPlugin;

class GleamPluginDiscoveryTest {

  @Test
  void serviceLoader_resolvesGleamPluginNames() {
    ServiceLoader<SmithyBuildPlugin> loader = ServiceLoader.load(SmithyBuildPlugin.class);
    Set<String> names =
        StreamSupport.stream(loader.spliterator(), false)
            .map(SmithyBuildPlugin::getName)
            .collect(Collectors.toSet());

    assertThat(names)
        .contains("gleam-types-codegen", "gleam-client-codegen", "gleam-server-codegen");
  }

  @Test
  void pluginClasses_exposeExpectedNames() {
    assertThat(new GleamTypesPlugin().getName()).isEqualTo("gleam-types-codegen");
    assertThat(new GleamClientPlugin().getName()).isEqualTo("gleam-client-codegen");
    assertThat(new GleamServerPlugin().getName()).isEqualTo("gleam-server-codegen");
  }
}
