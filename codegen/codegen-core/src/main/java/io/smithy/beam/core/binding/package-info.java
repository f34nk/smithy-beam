/**
 * Thin static facades over Smithy {@code KnowledgeIndex} types
 * ({@code HttpBindingIndex}, {@code PaginatedIndex}, {@code WaitableTrait}
 * lookup, {@code EventStreamIndex}) so that the language-specific codegen
 * modules do not duplicate index plumbing.
 *
 * <p>This package contains no language-specific logic and no public SPI;
 * it is consumed exclusively by the {@code codegen-erlang} and
 * {@code codegen-elixir} modules.
 */
package io.smithy.beam.core.binding;
