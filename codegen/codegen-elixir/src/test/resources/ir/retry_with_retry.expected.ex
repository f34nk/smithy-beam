@doc "Invokes fun with exponential backoff when a modeled retryable error is returned."
@spec with_retry((-> term()), keyword()) :: term()
def with_retry(fun, opts) do
  max_attempts = Keyword.get(opts, :max_attempts, 3)
  base_delay_ms = Keyword.get(opts, :base_delay_ms, 100)
  with_retry(fun, max_attempts, base_delay_ms, 1)
end

defp with_retry(fun, 0, _base, _n), do: fun.()
defp with_retry(
  fun,
  attempts,
  base,
  n
) do
  case fun.() do
    {:ok, _} = ok -> ok
    {:error, _} = err ->
      if should_retry?(err) and attempts > 1 do
        Process.sleep(trunc(base * :math.pow(2, n - 1)))
        with_retry(fun, attempts - 1, base, n + 1)
      else
        err
      end
  end
end