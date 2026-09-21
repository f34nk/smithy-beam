defmodule SyntaxFixtureEnumUnion do
  @moduledoc "Enum and union coverage for Elixir IR rendering."

  def decode_enum("a"), do: :a
  def decode_enum("b"), do: :b
  def decode_enum(v) when is_binary(v), do: {:unknown, v}
  def decode_enum(nil), do: nil

  def encode_enum(:a), do: "a"
  def encode_enum(:b), do: "b"
  def encode_enum(nil), do: nil

  def decode_union(map) when is_map(map) do
    case Map.to_list(map) do
      [{"left", v}] -> {:left, v}
      [{"right", v}] -> {:right, v}
      [{k, _v}] -> {:unknown, k}
      _ -> nil
    end
  end

  def decode_union(nil), do: nil

  def encode_union({:left, v}), do: %{"left" => v}
  def encode_union({:right, v}), do: %{"right" => v}

  def encode_union({:unknown, k}) when is_binary(k) do
    %{k => nil}
  end

  def encode_union(nil), do: nil
end
