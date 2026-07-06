package io.smithy.beam.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import io.beam.ir.erlang.Module;
import java.io.IOException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;


@Disabled("beam-ir migration: golden fixtures live in beam-ir; re-enable locally if needed")
class ErlangCredentialProviderIrTest {
  private static final ShapeId SIGV4_SERVICE =
      ShapeId.from("smithy.beam.test.sigv4#Sigv4TestService");

  @Test
  void resolveMatchesGolden() throws IOException {
    IrGoldenAssertions.assertGolden(
        ErlangCredentialProviderIr.resolve(), "ir/credential_provider_resolve.expected.erl");
  }

  @Test
  void resolveChainMatchesGolden() throws IOException {
    String combined =
        IrGoldenAssertions.renderFunctions(
            ErlangCredentialProviderIr.credentialFunctions().stream()
                .filter(
                    fn ->
                        fn.name().startsWith("resolve_chain") || fn.name().equals("resolve_provider"))
                .toList());
    assertThat(combined)
        .isEqualTo(
            IrGoldenAssertions.readExpectedString("ir/credential_provider_chain.expected.erl"));
  }

  @Test
  void credentialsModuleMatchesGolden() throws IOException {
    ServiceShape service = sigv4Model().expectShape(SIGV4_SERVICE, ServiceShape.class);
    Module module =
        ErlangCredentialProviderIr.credentialsModule("sigv4test_service_credentials", service);
    IrGoldenAssertions.assertGolden(module, "ir/credential_provider_module.expected.erl");
  }

  private static Model sigv4Model() {
    return Model.assembler()
        .addImport(
            ErlangCredentialProviderIrTest.class.getResource("/model/sigv4_fixture.smithy"))
        .discoverModels()
        .assemble()
        .unwrap();
  }
}
