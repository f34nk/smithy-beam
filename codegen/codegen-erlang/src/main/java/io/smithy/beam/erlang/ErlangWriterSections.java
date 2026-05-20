package io.smithy.beam.erlang;

import software.amazon.smithy.utils.CodeSection;

/**
 * Named regions inside {@link ErlangWriter} output. Integrations attach
 * {@link software.amazon.smithy.utils.CodeInterceptor}s to these sections.
 */
public final class ErlangWriterSections {

    private ErlangWriterSections() {}

    /** Module-level generated documentation block (percent comments). */
    public static final class GeneratedDocumentation implements CodeSection {}

    /** -module, -export, -behaviour, and related header lines. */
    public static final class ModuleHeader implements CodeSection {}

    /** -include / -include_lib lines emitted after -module. */
    public static final class Dependencies implements CodeSection {}

    /** Protocol framing hooks (serialization, metadata). */
    public static final class ProtocolHook implements CodeSection {}

    /** Transport hooks (HTTP client calls, endpoint wiring). */
    public static final class TransportHook implements CodeSection {}

    /** Body of a single generated operation function. */
    public static final class OperationBody implements CodeSection {}
}
