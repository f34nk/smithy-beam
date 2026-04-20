defmodule SmithyJson do
  @moduledoc """
  JSON encoding and decoding for Smithy shapes.

  Delegates to `Jason` for encoding/decoding.  Provides helper utilities for
  Smithy-specific types:

    * **BigDecimal** — represented as `{unscaled :: integer(), scale :: integer()}`
      tuples (matching the Erlang convention in `smithy_json.erl`).  Encoded as
      a JSON decimal string; decoded back to the tuple form.
    * **Atom map keys** — normalised to binary strings before encoding so that
      Jason does not reject them.

  ## Usage

      iex> SmithyJson.encode(%{foo: 42, bar: "baz"})
      {:ok, ~S({"foo":42,"bar":"baz"})}

      iex> SmithyJson.decode(~S({"Items":[1,2,3]}))
      {:ok, %{"Items" => [1, 2, 3]}}
  """

  @type json_term :: map() | list() | String.t() | number() | boolean() | nil

  # ---------------------------------------------------------------------------
  # Public API
  # ---------------------------------------------------------------------------

  @doc """
  Encode a term to a JSON binary.

  Atom map keys are converted to strings.  Returns `{:ok, binary}` or
  `{:error, reason}`.
  """
  @spec encode(json_term()) :: {:ok, binary()} | {:error, term()}
  def encode(term) do
    Jason.encode(normalise_for_encode(term))
  end

  @doc """
  Encode a term to a JSON binary, raising on error.
  """
  @spec encode!(json_term()) :: binary()
  def encode!(term) do
    Jason.encode!(normalise_for_encode(term))
  end

  @doc """
  Decode a JSON binary into an Erlang/Elixir term.

  JSON objects are decoded as maps with **string keys**.  Returns
  `{:ok, term}` or `{:error, reason}`.
  """
  @spec decode(binary() | iodata()) :: {:ok, json_term()} | {:error, term()}
  def decode(json) when is_binary(json) do
    case Jason.decode(json) do
      {:ok, term}      -> {:ok, term}
      {:error, reason} -> {:error, {:json_decode_error, reason}}
    end
  end

  def decode(json), do: decode(IO.iodata_to_binary(json))

  @doc """
  Decode a JSON binary, raising on error.
  """
  @spec decode!(binary() | iodata()) :: json_term()
  def decode!(json) do
    Jason.decode!(json)
  end

  # ---------------------------------------------------------------------------
  # BigDecimal helpers
  # ---------------------------------------------------------------------------

  @doc """
  Encode a `{unscaled, scale}` BigDecimal pair as a JSON decimal string.

  ## Examples

      iex> SmithyJson.encode_decimal({12345, -2})
      "123.45"

      iex> SmithyJson.encode_decimal({42, 0})
      "42"
  """
  @spec encode_decimal({integer(), integer()}) :: String.t()
  def encode_decimal({unscaled, 0}), do: Integer.to_string(unscaled)

  def encode_decimal({unscaled, scale}) when scale < 0 do
    abs_scale = -scale
    digits    = Integer.to_string(abs(unscaled))
    len       = String.length(digits)
    sign      = if unscaled < 0, do: "-", else: ""

    if len <= abs_scale do
      pad = String.duplicate("0", abs_scale - len)
      "#{sign}0.#{pad}#{digits}"
    else
      {int_part, frac_part} = String.split_at(digits, len - abs_scale)
      "#{sign}#{int_part}.#{frac_part}"
    end
  end

  def encode_decimal({unscaled, scale}) when scale > 0 do
    zeros = String.duplicate("0", scale)
    "#{unscaled}#{zeros}"
  end

  @doc """
  Decode a JSON decimal string into a `{unscaled, scale}` tuple.

  ## Examples

      iex> SmithyJson.decode_decimal("123.45")
      {12345, -2}

      iex> SmithyJson.decode_decimal("42")
      {42, 0}
  """
  @spec decode_decimal(String.t()) :: {integer(), integer()}
  def decode_decimal(str) when is_binary(str) do
    case String.split(str, ".") do
      [int_part] ->
        {String.to_integer(int_part), 0}

      [int_part, frac_part] ->
        scale    = -String.length(frac_part)
        unscaled = String.to_integer(int_part <> frac_part)
        {unscaled, scale}
    end
  end

  # ---------------------------------------------------------------------------
  # Internal helpers
  # ---------------------------------------------------------------------------

  @doc false
  def normalise_for_encode(m) when is_map(m) do
    Map.new(m, fn {k, v} -> {key_to_string(k), normalise_for_encode(v)} end)
  end

  def normalise_for_encode(l) when is_list(l), do: Enum.map(l, &normalise_for_encode/1)
  def normalise_for_encode(a) when is_atom(a) and a not in [true, false, nil], do: Atom.to_string(a)
  def normalise_for_encode(other), do: other

  defp key_to_string(k) when is_binary(k), do: k
  defp key_to_string(k) when is_atom(k),   do: Atom.to_string(k)
  defp key_to_string(k),                   do: to_string(k)
end
