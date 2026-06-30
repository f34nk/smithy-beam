
defmodule LargeStatus do
  @moduledoc "String enum. Values are Smithy wire-format strings. Unknown values are represented as {:unknown, String.t()}."
  @wire_values ["ALPHA", "BETA", "GAMMA"]
  @wire_set MapSet.new(@wire_values)

  @type t :: String.t() | {:unknown, String.t()}

  @spec valid?(String.t()) :: boolean()
  def valid?(v) when is_binary(v), do: MapSet.member?(@wire_set, v)

  @spec from_string(String.t()) :: t()
  def from_string(v) when is_binary(v), do: if(valid?(v), do: v, else: {:unknown, v})

  @spec to_string(t()) :: String.t()
  def to_string(v) when is_binary(v), do: v
  def to_string({:unknown, v}) when is_binary(v), do: v

  @spec values() :: [String.t()]
  def values, do: @wire_values
end