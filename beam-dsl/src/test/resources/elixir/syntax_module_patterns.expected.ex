defmodule SyntaxFixturePatterns do
  @moduledoc "Pattern matching coverage for Elixir IR rendering."

  alias SyntaxTypes.Item, as: Item
  alias SyntaxTypes.TaggedError, as: TaggedError

  @spec match_patterns(term()) :: term()
  def match_patterns(nil), do: nil
  def match_patterns(%{"key" => value}), do: {:map, value}
  def match_patterns(%Item{name: name}), do: {:record, name}
  def match_patterns(%TaggedError{}), do: {:error_record, true}
  def match_patterns({:tag, value}), do: {:tuple, value}
  def match_patterns([h | t]) when is_list(t), do: {:list, h, length(t)}
  def match_patterns(<<_::binary>> = bin), do: {:binary, bin}
  def match_patterns(v) when is_function(v, 1), do: v.(:syntax)
  def match_patterns(v) when is_integer(v), do: {:int, v}
  def match_patterns(v) when is_atom(v), do: {:atom, v}
end
