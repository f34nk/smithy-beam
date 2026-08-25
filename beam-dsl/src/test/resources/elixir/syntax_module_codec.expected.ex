defmodule SyntaxFixtureCodec do
  @moduledoc "Codec and spec/doc coverage for Elixir IR rendering."

  alias SyntaxTypes.Item, as: Item

  @doc "Decode input with spec and documentation."
  @spec decode(nil | map()) :: Item.t() | nil
  def decode(nil), do: nil

  def decode(map) when is_map(map) do
    %Item{
      name: Map.get(map, "name"),
      count: Map.get(map, "count")
    }
  end

  @spec encode(Item.t() | nil) :: map() | nil
  def encode(nil), do: nil

  def encode(record = %Item{}) do
    %{"name" => record.name, "count" => record.count}
    |> Enum.reject(fn {_k, v} -> is_nil(v) end)
    |> Map.new()
  end

  def no_spec_no_doc(nil), do: nil
  def no_spec_no_doc(value), do: value

  @doc "Return a plain map from a struct."
  @spec with_spec_and_doc(Item.t()) :: map()
  def with_spec_and_doc(record = %Item{}) do
    %{name: record.name, count: record.count}
  end

  @spec with_spec_no_doc(binary()) :: atom() | {:unknown, binary()}
  def with_spec_no_doc("a"), do: :a
  def with_spec_no_doc("b"), do: :b

  def with_spec_no_doc(v) when is_binary(v) do
    {:unknown, v}
  end
end
