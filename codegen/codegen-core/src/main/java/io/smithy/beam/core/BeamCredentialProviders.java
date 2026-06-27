package io.smithy.beam.core;

import java.util.List;

/**
 * AWS credential provider kinds used when emitting the default resolution chain for generated SigV4
 * clients.
 */
public final class BeamCredentialProviders {

  private BeamCredentialProviders() {}

  public enum BeamCredentialProviderKind {
    ENV,
    PROFILE,
    ECS,
    EC2
  }

  private static final List<BeamCredentialProviderKind> DEFAULT_CHAIN =
      List.of(
          BeamCredentialProviderKind.ENV,
          BeamCredentialProviderKind.PROFILE,
          BeamCredentialProviderKind.ECS,
          BeamCredentialProviderKind.EC2);

  public static List<BeamCredentialProviderKind> defaultChain() {
    return DEFAULT_CHAIN;
  }
}
