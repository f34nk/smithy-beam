package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamHostLabelIndex;
import io.smithy.beam.core.BeamProtocolIds;
import io.smithy.beam.core.BeamS3CustomizationIndex;
import io.smithy.beam.core.BeamXmlBindingIndex;
import io.smithy.beam.ir.elixir.ExFunction;
import java.util.List;
import java.util.Optional;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.traits.EndpointTrait;

/** REST-XML protocol codec emitter for Elixir clients and servers. */
public final class ElixirRestXmlEmitter {

  private ElixirRestXmlEmitter() {}

  public static void emitCodecModule(ElixirContext ctx, ServiceShape service) {
    Model model = ctx.model();
    BeamElixirLayout layout =
        new BeamElixirLayout(ctx.settings(), service.getId().getNamespace(), service);
    HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
    SymbolProvider sp = ctx.symbolProvider();
    String moduleName =
        ElixirSymbolProvider.toModuleName(layout.clientCodecModuleName(BeamProtocolIds.REST_XML));
    String codecFile = layout.clientCodecModuleName(BeamProtocolIds.REST_XML) + ".ex";
    String runtimeMod = ElixirSymbolProvider.toModuleName(layout.runtimeTypesModuleName());
    String typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
    List<OperationShape> operations = ElixirTopDown.containedOperationsSorted(model, service);
    boolean encodeWithConfig = serviceEncodesWithConfig(model, service);
    boolean checksumBindings =
        ElixirHttpChecksumEmitter.serviceHasChecksumOperations(model, service);

    ctx.writerDelegator()
        .useFileWriter(
            codecFile,
            writer -> {
              writer.write("defmodule $L do", moduleName);
              writer.indent();
              writer.write(
                  "@moduledoc \"REST-XML codecs for $L (generated). Do not edit.\"",
                  service.getId());
              writer.write("alias $L, as: RuntimeTypes", runtimeMod);
              writer.write("alias $L, as: Types", typesMod);
              if (BeamS3CustomizationIndex.isS3Service(service)) {
                writer.write("alias S3Endpoint");
              }
              writer.write("");

              emitServiceXmlNamespace(writer, BeamXmlBindingIndex.xmlNamespaceUri(service));

              for (OperationShape op : operations) {
                emitEncoder(
                    writer,
                    model,
                    service,
                    op,
                    httpIndex,
                    sp,
                    typesMod,
                    runtimeMod,
                    encodeWithConfig);
                emitDecoder(writer, model, op, httpIndex, sp, typesMod);
              }

              emitXmlHelpers(writer, checksumBindings);
              emitExFunctions(writer, ElixirRestXmlIr.sharedClientCodecHelpers());

              writer.dedent();
              writer.write("end");
            });
  }

  public static void emitServerCodecModule(ElixirContext ctx, ServiceShape service) {
    Model model = ctx.model();
    BeamElixirLayout layout =
        new BeamElixirLayout(ctx.settings(), service.getId().getNamespace(), service);
    ElixirRuntimeHelpersEmitter.emitIfNeeded(ctx, service);
    HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
    SymbolProvider sp = ctx.symbolProvider();
    String serverCodecModule =
        ElixirSymbolProvider.toModuleName(layout.serverCodecModuleName(BeamProtocolIds.REST_XML));
    String runtimeMod = ElixirSymbolProvider.toModuleName(layout.runtimeTypesModuleName());
    String typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
    List<OperationShape> operations = ElixirTopDown.containedOperationsSorted(model, service);
    boolean checksumBindings =
        ElixirHttpChecksumEmitter.serviceHasChecksumOperations(model, service);

    ctx.writerDelegator()
        .useFileWriter(
            layout.serverCodecModuleName(BeamProtocolIds.REST_XML) + ".ex",
            writer -> {
              writer.write("defmodule $L do", serverCodecModule);
              writer.indent();
              writer.write(
                  "@moduledoc \"Server REST-XML codecs for $L (generated). Do not edit.\"",
                  service.getId());
              writer.write("alias $L, as: RuntimeTypes", runtimeMod);
              writer.write("alias $L, as: Types", typesMod);
              writer.write("");

              emitServiceXmlNamespace(writer, BeamXmlBindingIndex.xmlNamespaceUri(service));

              for (OperationShape op : operations) {
                emitRequestDecoder(writer, model, op, httpIndex, sp, typesMod);
                emitResponseEncoder(writer, model, op, httpIndex, sp, typesMod, runtimeMod);
              }

              emitXmlHelpers(writer, checksumBindings);

              writer.dedent();
              writer.write("end");
            });
  }

  private static void emitServiceXmlNamespace(ElixirWriter writer, Optional<String> namespaceUri) {
    if (namespaceUri.isPresent()) {
      writer.write("defp xml_namespace, do: %{uri: \"$L\"}", namespaceUri.get());
    } else {
      writer.write("defp xml_namespace, do: %{}");
    }
    writer.write("");
  }

  private static void emitEncoder(
      ElixirWriter writer,
      Model model,
      ServiceShape service,
      OperationShape op,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      String typesMod,
      String runtimeMod,
      boolean encodeWithConfig) {
    ExFunction fn =
        ElixirRestXmlOperationIr.buildEncodeRequest(
            model, service, op, httpIndex, sp, typesMod, runtimeMod, encodeWithConfig);
    writer.write("$L", fn.asString());
    writer.write("");
  }

  private static void emitDecoder(
      ElixirWriter writer,
      Model model,
      OperationShape op,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      String typesMod) {
    ExFunction fn =
        ElixirRestXmlOperationIr.buildDecodeResponse(model, op, httpIndex, sp, typesMod);
    writer.write("$L", fn.asString());
    writer.write("");
    ExFunction errorFn = ElixirRestXmlOperationIr.buildErrorDispatch(model, op, sp, typesMod);
    if (errorFn != null) {
      writer.write("$L", errorFn.asString());
      writer.write("");
    }
  }

  private static void emitRequestDecoder(
      ElixirWriter writer,
      Model model,
      OperationShape op,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      String typesMod) {
    ExFunction fn =
        ElixirRestXmlOperationIr.buildDecodeRequest(model, op, httpIndex, sp, typesMod);
    writer.write("$L", fn.asString());
    writer.write("");
  }

  private static void emitResponseEncoder(
      ElixirWriter writer,
      Model model,
      OperationShape op,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      String typesMod,
      String runtimeMod) {
    ExFunction fn =
        ElixirRestXmlOperationIr.buildEncodeResponse(
            model, op, httpIndex, sp, typesMod, runtimeMod);
    writer.write("$L", fn.asString());
    writer.write("");
  }

  private static void emitXmlHelpers(ElixirWriter writer, boolean checksumBindings) {
    writer.write("defp encode_xml(root_map, xml_ns) do");
    writer.indent();
    writer.write("[{root_name, content}] = Map.to_list(root_map)");
    writer.write("element = build_xml_element(root_name, content, xml_ns)");
    writer.write(
        ":xmerl.export_simple([element], :xmerl_xmlns, [], [{:prolog, false}]) |> :erlang.iolist_to_binary()");
    writer.dedent();
    writer.write("end");
    writer.write("");
    writer.write("defp build_xml_element(name, content, xml_ns) when is_map(content) do");
    writer.indent();
    writer.write("attrs = xml_namespace_attrs(xml_ns)");
    writer.write("children = for {k, v} <- content, v != nil, do: build_xml_child(k, v)");
    writer.write("{name, attrs, children}");
    writer.dedent();
    writer.write("end");
    writer.write("");
    writer.write("defp build_xml_element(name, content, xml_ns) do");
    writer.indent();
    writer.write("{name, xml_namespace_attrs(xml_ns), [{:text, to_binary(content)}]}");
    writer.dedent();
    writer.write("end");
    writer.write("");
    writer.write("defp build_xml_child(name, value) when is_map(value) do");
    writer.indent();
    writer.write("children = for {k, v} <- value, v != nil, do: build_xml_element(k, v, %{})");
    writer.write("{name, [], children}");
    writer.dedent();
    writer.write("end");
    writer.write("");
    writer.write("defp build_xml_child(name, value), do: {name, [], [{:text, to_binary(value)}]}");
    writer.write("");
    writer.write("defp xml_namespace_attrs(%{uri: uri}), do: [xmlns: uri]");
    writer.write("defp xml_namespace_attrs(_), do: []");
    writer.write("");
    writer.write("defp parse_xml_root(body, root_name) do");
    writer.indent();
    writer.write("try do");
    writer.indent();
    writer.write("{xml, _} = :xmerl_scan.string(String.to_charlist(body))");
    writer.write("cond do");
    writer.indent();
    writer.write("xml_element_named(xml, root_name) -> {:ok, xml}");
    writer.write("true ->");
    writer.indent();
    writer.write("case find_element(root_name, element_content(xml)) do");
    writer.indent();
    writer.write("nil -> {:error, {:missing_root, root_name}}");
    writer.write("");
    writer.write("root -> {:ok, root}");
    writer.dedent();
    writer.write("end");
    writer.dedent();
    writer.dedent();
    writer.write("end");
    writer.dedent();
    writer.write("rescue");
    writer.indent();
    writer.write("reason -> {:error, {:xml_parse_error, reason}}");
    writer.dedent();
    writer.write("end");
    writer.dedent();
    writer.write("end");
    writer.write("");
    writer.write("defp decode_payload(body, root_name) do");
    writer.indent();
    writer.write("case parse_xml_root(body, root_name) do");
    writer.indent();
    writer.write("{:ok, _root} -> nil");
    writer.write("{:error, _} -> nil");
    writer.dedent();
    writer.write("end");
    writer.dedent();
    writer.write("end");
    writer.write("");
    writer.write(
        "defp xml_element_named(element, name), do: is_element(element) and element_name(element) == name");
    writer.write("");
    writer.write(
        "defp element_content({:xmlElement, _, _, _, _, _, _, _, content, _, _, _}), do: content");
    writer.write(
        "defp element_content({_, _, content, _, _, _}) when is_list(content), do: content");
    writer.write("defp element_content([h | _]), do: element_content(h)");
    writer.write("defp element_content(_), do: []");
    writer.write("");
    writer.write("defp find_element(name, content) do");
    writer.indent();
    writer.write("Enum.find_value(content, fn item ->");
    writer.indent();
    writer.write("if is_element(item) and element_name(item) == name, do: item");
    writer.dedent();
    writer.write("end)");
    writer.dedent();
    writer.write("end");
    writer.write("");
    writer.write("defp is_element({:xmlElement, _, _, _, _, _, _, _, _, _, _, _}), do: true");
    writer.write("defp is_element({_, _, content, _, _, _}) when is_list(content), do: true");
    writer.write("defp is_element(_), do: false");
    writer.write("");
    writer.write(
        "defp element_name({:xmlElement, name, _, _, _, _, _, _, _, _, _, _}) when is_atom(name),");
    writer.indent();
    writer.write("do: Atom.to_string(name)");
    writer.dedent();
    writer.write(
        "defp element_name({:xmlElement, name, _, _, _, _, _, _, _, _, _, _}) when is_list(name),");
    writer.indent();
    writer.write("do: List.to_string(name)");
    writer.dedent();
    writer.write(
        "defp element_name({:xmlElement, name, _, _, _, _, _, _, _, _, _, _}) when is_binary(name),");
    writer.indent();
    writer.write("do: name");
    writer.dedent();
    writer.write(
        "defp element_name({name, _, _, _, _, _}) when is_atom(name), do: Atom.to_string(name)");
    writer.write(
        "defp element_name({name, _, _, _, _, _}) when is_list(name), do: List.to_string(name)");
    writer.write("defp element_name({name, _, _, _, _, _}) when is_binary(name), do: name");
    writer.write("");
    writer.write("defp xml_child_text(parent, name) do");
    writer.indent();
    writer.write("case find_element(name, element_content(parent)) do");
    writer.indent();
    writer.write("nil -> nil");
    writer.write("element ->");
    writer.indent();
    writer.write("case element_text(element) do");
    writer.indent();
    writer.write("[] -> nil");
    writer.write("[text | _] -> List.to_string(text)");
    writer.dedent();
    writer.write("end");
    writer.dedent();
    writer.dedent();
    writer.write("end");
    writer.dedent();
    writer.write("end");
    writer.write("");
    writer.write("defp element_text({:xmlElement, _, _, _, _, _, _, _, content, _, _, _}) do");
    writer.indent();
    writer.write("Enum.flat_map(content, &collect_text/1)");
    writer.dedent();
    writer.write("end");
    writer.write("defp element_text(_), do: []");
    writer.write("");
    writer.write("defp collect_text({:xmlText, _, _, _, text, _}), do: [text]");
    writer.write("defp collect_text(text) when is_list(text), do: [text]");
    writer.write("defp collect_text(_), do: []");
    writer.write("");
    writer.write(
        "defp is_element_string({:xmlElement, _, _, _, _, _, _, _, _, _, _, _}), do: true");
    writer.write("defp is_element_string(_), do: false");
    writer.write("");
    writer.write("defp xml_child_list(parent, nil, item_name) do");
    writer.indent();
    writer.write("parent");
    writer.write("|> element_content()");
    writer.write(
        "|> Enum.filter(fn item -> is_element(item) and element_name(item) == item_name end)");
    writer.write("|> Enum.map(fn item ->");
    writer.indent();
    writer.write("case element_text(item) do");
    writer.indent();
    writer.write("[] -> nil");
    writer.write("[text | _] -> List.to_string(text)");
    writer.dedent();
    writer.write("end");
    writer.dedent();
    writer.write("end)");
    writer.write("|> Enum.reject(&is_nil/1)");
    writer.dedent();
    writer.write("end");
    writer.write("");
    writer.write("defp xml_child_list(parent, list_name, item_name) do");
    writer.indent();
    writer.write("case find_element(list_name, element_content(parent)) do");
    writer.indent();
    writer.write("nil -> nil");
    writer.write("list_element ->");
    writer.indent();
    writer.write("list_element");
    writer.write("|> element_content()");
    writer.write(
        "|> Enum.filter(fn item -> is_element(item) and element_name(item) == item_name end)");
    writer.write("|> Enum.map(fn item ->");
    writer.indent();
    writer.write("case element_text(item) do");
    writer.indent();
    writer.write("[] -> nil");
    writer.write("[text | _] -> List.to_string(text)");
    writer.dedent();
    writer.write("end");
    writer.dedent();
    writer.write("end)");
    writer.write("|> Enum.reject(&is_nil/1)");
    writer.dedent();
    writer.dedent();
    writer.write("end");
    writer.dedent();
    writer.write("end");
    writer.write("");
    writer.write("defp xml_child_struct_list(parent, nil, item_name, decode_fun) do");
    writer.indent();
    writer.write("parent");
    writer.write("|> element_content()");
    writer.write(
        "|> Enum.filter(fn item -> is_element(item) and element_name(item) == item_name end)");
    writer.write("|> Enum.map(decode_fun)");
    writer.dedent();
    writer.write("end");
    writer.write("");
    writer.write("defp xml_child_struct_list(parent, list_name, item_name, decode_fun) do");
    writer.indent();
    writer.write("case find_element(list_name, element_content(parent)) do");
    writer.indent();
    writer.write("nil -> nil");
    writer.write("list_element ->");
    writer.indent();
    writer.write("list_element");
    writer.write("|> element_content()");
    writer.write(
        "|> Enum.filter(fn item -> is_element(item) and element_name(item) == item_name end)");
    writer.write("|> Enum.map(decode_fun)");
    writer.dedent();
    writer.dedent();
    writer.write("end");
    writer.dedent();
    writer.write("end");
    writer.write("");
    if (checksumBindings) {
      ElixirHttpChecksumEmitter.emitChecksumHelpers(writer);
    }
  }

  private static void emitExFunctions(ElixirWriter writer, List<io.smithy.beam.ir.elixir.ExFunction> functions) {
    for (io.smithy.beam.ir.elixir.ExFunction function : functions) {
      writer.write("$L", function.asString());
      writer.write("");
    }
  }

  public static boolean serviceHasHostLabelOperations(Model model, ServiceShape service) {
    BeamHostLabelIndex hostLabelIndex = BeamHostLabelIndex.of(model);
    for (OperationShape op : ElixirTopDown.containedOperationsSorted(model, service)) {
      if (!hostLabelIndex.hostLabelMembers(op).isEmpty() && op.hasTrait(EndpointTrait.class)) {
        return true;
      }
    }
    return false;
  }

  public static boolean serviceEncodesWithConfig(Model model, ServiceShape service) {
    return serviceHasHostLabelOperations(model, service)
        || BeamS3CustomizationIndex.of(model).serviceUsesBucketAddressing(service);
  }
}
