defmodule WaitableServiceWaiters do
  @moduledoc "Generated waiters for smithy.beam.test.waiters#WaitableService (generated)."

  @doc "Waits using the BucketExists waiter on smithy.beam.test.waiters#HeadBucket."
  @spec wait_bucket_exists(term(), map(), keyword()) :: {:ok, term()} | {:error, term()}
  def wait_bucket_exists(client, input, opts) do
    acceptors = [%{
  state: :success,
  matcher: :success,
  expected: true
}, %{
  state: :retry,
  matcher: :errorType,
  expected: %WaitableServiceTypes.NotFound{}
}]
    wait_opts = Keyword.merge([{:min_delay_ms, 4000}, {:max_delay_ms, 300000}], opts)
    wait_until(fn  -> WaitableServiceClient.head_bucket(client, input) end, acceptors, wait_opts)
  end

  @doc "Waits using the TableExists waiter on smithy.beam.test.waiters#DescribeTable."
  @spec wait_table_exists(term(), map(), keyword()) :: {:ok, term()} | {:error, term()}
  def wait_table_exists(client, input, opts) do
    acceptors = [%{
  state: :success,
  matcher: :output,
  path: [:table, :table_status],
  comparator: :stringEquals,
  expected: "ACTIVE"
}]
    wait_opts = Keyword.merge([{:min_delay_ms, 2000}, {:max_delay_ms, 120000}], opts)
    wait_until(fn  -> WaitableServiceClient.describe_table(client, input) end, acceptors, wait_opts)
  end

  defp wait_until(step, acceptors, opts) do
    max_attempts = Keyword.get(opts, :max_attempts, 25)
    min_delay = Keyword.get(opts, :min_delay_ms, 2000)
    max_delay = Keyword.get(opts, :max_delay_ms, 120000)
    wait_until(step, acceptors, max_attempts, min_delay, max_delay)
  end

  defp wait_until(_step, _acceptors, 0, _delay, _max_delay), do: {:error, :max_attempts_exceeded}
  defp wait_until(
    step,
    acceptors,
    attempts,
    delay,
    max_delay
  ) do
    result = step.()
    case classify(acceptors, result) do
      :success -> {:ok, result}
    
      :failure -> {:error, result}
    
      :retry when attempts <= 1 -> {:error, :max_attempts_exceeded}
    
      :retry ->
        Process.sleep(delay)
        next_delay = min(delay * 2, max_delay)
        wait_until(step, acceptors, attempts - 1, next_delay, max_delay)
    end
  end

  defp classify([], _result), do: :retry
  defp classify([acceptor | rest], result) do
    if matches_acceptor?(acceptor, result) do
      acceptor.state
    else
      classify(rest, result)
    end
  end

  defp matches_acceptor?(
    acceptor,
    result
  ) do
    case {acceptor, result} do
      {%{matcher: :success, expected: true}, {:ok, _}} -> true
      {%{matcher: :success, expected: false}, {:error, _}} -> true
      {%{matcher: :errorType, expected: expected}, {:error, got}} ->
        error_types_match?(expected, got)
      {%{matcher: :output, path: path, comparator: :stringEquals, expected: expected}, {:ok, output}} ->
        path_string_equals?(path, expected, output)
      {%{matcher: :inputOutput, path: path, comparator: :stringEquals, expected: expected}, {:ok, output}} ->
        path_string_equals?(path, expected, output)
      _ -> false
    end
  end

  defp error_types_match?(expected, _got) when is_binary(expected), do: true
  defp error_types_match?(%{:__struct__ => struct}, %{:__struct__ => struct}), do: true
  defp error_types_match?(expected, got), do: expected == got

  defp path_string_equals?(
    path,
    expected,
    output
  ) do
    case path_value(path, output) do
      nil -> false
      value -> string_equals?(value, expected)
    end
  end

  defp path_value(path, _value) when is_binary(path), do: nil

  defp path_value([], value), do: value

  defp path_value(
    [key | rest],
    value
  ) when is_map(value) do
    case Map.get(value, key) do
      nil -> nil
      next -> path_value(rest, next)
    end
  end

  defp path_value(
    [key | rest],
    value
  ) when is_struct(value) do
    case Map.get(Map.from_struct(value), key) do
      nil -> nil
      next -> path_value(rest, next)
    end
  end

  defp path_value(_path, _value), do: nil

  defp string_equals?(
    left,
    right
  ) when is_atom(left) and is_binary(right) do
    String.upcase(Atom.to_string(left)) == String.upcase(right)
  end

  defp string_equals?(
    left,
    right
  ) when is_binary(left) and is_binary(right) do
    String.upcase(left) == String.upcase(right)
  end

  defp string_equals?(left, right), do: left == right
end