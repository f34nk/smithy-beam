defmodule SyntaxFixtureRetry do
  @moduledoc "Retry, try/catch, and helper coverage for Elixir IR rendering."

  alias SyntaxTypes.TaggedError, as: TaggedError

  @doc "Invoke fun with exponential backoff on retryable errors."
  @spec with_retry((-> term()), map()) :: term()
  def with_retry(fun, opts) do
    max = Map.get(opts, :max_attempts, 3)
    base = Map.get(opts, :base_delay_ms, 100)
    with_retry(fun, max, base, 1)
  end

  defp with_retry(fun, 0, _base, _n), do: fun.()

  defp with_retry(fun, attempts, base, n) do
    case fun.() do
      {:ok, _} = ok ->
        ok

      {:error, _} = err ->
        if retryable?(err) and attempts > 1 do
          Process.sleep(trunc(base * :math.pow(2, n - 1)))
          with_retry(fun, attempts - 1, base, n + 1)
        else
          err
        end
    end
  end

  defp retryable?({:error, %TaggedError{}}), do: true
  defp retryable?(_), do: false

  @spec risky_call((-> term())) :: term() | {:error, term()}
  def risky_call(fun) do
    try do
      fun.()
    catch
      _, reason -> {:error, reason}
    end
  end

  @spec helpers() :: term()
  def helpers do
    result =
      case fetch("x") do
        {:ok, value} -> value
        :error -> :default
      end

    filtered =
      Enum.filter_map(
        [result],
        fn
          v when v != nil -> encode_value(v)
          _ -> :pop
        end
      )

    if length(filtered) > 0 do
      hd(filtered)
    else
      nil
    end
  end

  defp fetch(key) do
    case Map.get(%{}, key) do
      nil -> :error
      v -> {:ok, v}
    end
  end

  defp encode_value(v) when is_boolean(v), do: Atom.to_string(v)
  defp encode_value(v) when is_integer(v), do: Integer.to_string(v)
  defp encode_value(v) when is_float(v), do: Float.to_string(v)
  defp encode_value(v) when is_binary(v), do: v
  defp encode_value(v) when is_atom(v), do: Atom.to_string(v)
end
