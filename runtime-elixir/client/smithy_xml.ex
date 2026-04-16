defmodule SmithyXml do
  @moduledoc """
  XML encoding/decoding for Smithy-generated Elixir clients.

  Ported from `aws_xml.erl`. Uses `:xmerl` (Erlang/OTP standard library) for
  XML processing. Handles REST-XML services such as S3 and CloudFront.

  xmerl records are accessed directly via `elem/2` to avoid a compile-time
  dependency on `xmerl/include/xmerl.hrl` (which may not be on the include
  path in all Mix environments).

  Record tuple layouts (stable since OTP R13):
    `xmlElement` → `{:xmlElement, name, expanded_name, nsinfo, namespace,
                      parents, pos, attributes, content, language, xmlbase, elementdef}`
    `xmlText`    → `{:xmlText, parents, pos, language, value, type}`
  """

  @doc """
  Decode an XML binary into a nested map.

  Returns `{:ok, map}` on success or `{:error, reason}` on parse failure.
  The outermost element name becomes the top-level key.
  """
  @spec decode(binary() | charlist()) :: {:ok, map()} | {:error, term()}
  def decode(xml) when is_binary(xml), do: decode(:binary.bin_to_list(xml))

  def decode(xml) when is_list(xml) do
    try do
      {parsed, _rest} = :xmerl_scan.string(xml, quiet: true)
      {:ok, xml_to_map(parsed)}
    rescue
      e -> {:error, {:xml_parse_error, e}}
    catch
      _, reason -> {:error, {:xml_parse_error, reason}}
    end
  end

  @doc """
  Encode a map to an XML iolist.

  When `root_name` is `nil` the top-level keys are emitted as siblings;
  otherwise the output is wrapped in a `<root_name>` element.
  """
  @spec encode(map(), atom() | String.t() | nil) :: iolist()
  def encode(map, root_name \\ nil) when is_map(map) do
    elements = Enum.map(map, fn {k, v} -> encode_element(k, v) end)

    if root_name do
      root_atom = to_atom(root_name)
      :xmerl.export_simple([{root_atom, [], elements}], :xmerl_xml)
    else
      elements
    end
  end

  @doc false
  def encode_element(key, value) do
    {to_atom(key), [], encode_value(value)}
  end

  @doc false
  def encode_value(value) when is_map(value) do
    Enum.map(value, fn {k, v} -> encode_element(k, v) end)
  end

  def encode_value(value) when is_list(value) do
    if string_charlist?(value) do
      [value]
    else
      Enum.flat_map(value, &encode_value/1)
    end
  end

  def encode_value(value) when is_binary(value),  do: [binary_to_charlist(value)]
  def encode_value(value) when is_integer(value), do: [Integer.to_charlist(value)]
  def encode_value(value) when is_float(value),   do: [:io_lib.format(~c"~g", [value])]
  def encode_value(true),                          do: [~c"true"]
  def encode_value(false),                         do: [~c"false"]
  def encode_value(nil),                           do: []
  def encode_value(value) when is_atom(value),    do: [Atom.to_charlist(value)]

  # ---------------------------------------------------------------------------
  # Private helpers — xmerl tuple access via elem/2
  # ---------------------------------------------------------------------------

  # xmlElement tuple: {xmlElement, name(1), expanded_name(2), nsinfo(3),
  #                    namespace(4), parents(5), pos(6), attributes(7),
  #                    content(8), language(9), xmlbase(10), elementdef(11)}
  defp xml_element?(e),   do: is_tuple(e) and tuple_size(e) >= 9 and elem(e, 0) == :xmlElement
  defp elem_name(e),      do: elem(e, 1)
  defp elem_content(e),   do: elem(e, 8)

  # xmlText tuple: {xmlText, parents(1), pos(2), language(3), value(4), type(5)}
  defp xml_text?(t),      do: is_tuple(t) and tuple_size(t) >= 5 and elem(t, 0) == :xmlText
  defp text_value(t),     do: elem(t, 4)

  defp xml_to_map(element) when is_tuple(element) do
    cond do
      xml_element?(element) ->
        name     = elem_name(element)
        content  = elem_content(element)
        children = extract_children(content)

        inner =
          if children == [] do
            text = extract_text(content)
            if text == "", do: %{}, else: text
          else
            merge_children(children)
          end

        %{to_binary(name) => inner}

      xml_text?(element) ->
        value = element |> text_value() |> to_string() |> String.trim()
        if value == "", do: %{}, else: %{"text" => value}

      true ->
        %{}
    end
  end

  defp xml_to_map(_), do: %{}

  defp extract_text(content) do
    content
    |> Enum.filter(&xml_text?/1)
    |> Enum.map(fn node -> node |> text_value() |> to_string() |> String.trim() end)
    |> Enum.reject(&(&1 == ""))
    |> Enum.join("")
  end

  defp extract_children(content) do
    content
    |> Enum.filter(&xml_element?/1)
    |> Enum.map(&xml_to_map/1)
  end

  defp merge_children(children) do
    Enum.reduce(children, %{}, fn child_map, acc ->
      Enum.reduce(child_map, acc, fn {k, v}, inner_acc ->
        case Map.fetch(inner_acc, k) do
          {:ok, existing} when is_list(existing) -> Map.put(inner_acc, k, existing ++ [v])
          {:ok, existing}                         -> Map.put(inner_acc, k, [existing, v])
          :error                                  -> Map.put(inner_acc, k, v)
        end
      end)
    end)
  end

  defp to_atom(key) when is_atom(key),   do: key
  defp to_atom(key) when is_binary(key), do: String.to_atom(key)
  defp to_atom(key) when is_list(key),   do: List.to_atom(key)

  defp to_binary(key) when is_atom(key),   do: Atom.to_string(key)
  defp to_binary(key) when is_binary(key), do: key
  defp to_binary(key) when is_list(key),   do: List.to_string(key)

  defp string_charlist?([]), do: true
  defp string_charlist?([h | t]) when is_integer(h) and h >= 0 and h <= 0x10FFFF, do: string_charlist?(t)
  defp string_charlist?(_), do: false

  defp binary_to_charlist(bin), do: :binary.bin_to_list(bin)
end
