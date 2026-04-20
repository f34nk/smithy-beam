defmodule SmithyRetry do
  @moduledoc """
  Retry logic for Smithy-generated Elixir clients.

  Ported from `aws_retry.erl`. Implements exponential back-off with optional
  jitter for retrying transient AWS errors.
  """

  @default_max_retries 3
  @default_initial_backoff_ms 100
  @default_max_backoff_ms 20_000
  @default_multiplier 2.0
  @default_use_jitter true

  @doc """
  Execute `fun` and retry on retryable errors.

  ## Options

    * `:max_retries` — number of additional attempts (default: `#{@default_max_retries}`)
    * `:initial_backoff_ms` — first backoff in ms (default: `#{@default_initial_backoff_ms}`)
    * `:max_backoff_ms` — ceiling for backoff (default: `#{@default_max_backoff_ms}`)
    * `:multiplier` — exponential factor (default: `#{@default_multiplier}`)
    * `:use_jitter` — add random jitter (default: `#{@default_use_jitter}`)
    * `:enable_retry` — set to `false` to disable all retrying (default: `true`)
    * `:logger` — `fun/1` called with a retry-event map on each retry
  """
  @spec with_retry((() -> {:ok, term()} | {:error, term()}), keyword()) ::
          {:ok, term()} | {:error, term()}
  def with_retry(fun, opts \\ []) when is_function(fun, 0) do
    unless Keyword.get(opts, :enable_retry, true) do
      fun.()
    else
      max_retries      = Keyword.get(opts, :max_retries,       @default_max_retries)
      initial_backoff  = Keyword.get(opts, :initial_backoff_ms, @default_initial_backoff_ms)
      max_backoff      = Keyword.get(opts, :max_backoff_ms,     @default_max_backoff_ms)
      multiplier       = Keyword.get(opts, :multiplier,         @default_multiplier)
      use_jitter       = Keyword.get(opts, :use_jitter,         @default_use_jitter)
      logger           = Keyword.get(opts, :logger)

      retry_loop(fun, 0, max_retries, initial_backoff, max_backoff, multiplier, use_jitter, logger)
    end
  end

  @doc "Return true if the error is a transient/retryable AWS error."
  @spec retryable?(term()) :: boolean()
  def retryable?({:http_error, status, _}) when status in [429, 500, 502, 503, 504], do: true
  def retryable?({:http_error, _, _}), do: false
  def retryable?(:timeout), do: true
  def retryable?(:connection_refused), do: true
  def retryable?({:aws_error, _, code, _}) when code in ["ThrottlingException",
                                                          "RequestThrottled",
                                                          "ServiceUnavailable",
                                                          "InternalError",
                                                          "InternalFailure"], do: true
  def retryable?(_), do: false

  @doc """
  Polls `poll` repeatedly with exponential backoff until it reports terminal
  success or failure, or the maximum number of attempts is reached.

  This is the polling primitive used by generated Smithy waiters
  (`@waitable` trait). The poller is the generated acceptor evaluator which
  calls the underlying operation and matches the result against the waiter's
  acceptors.

  `poll` must return one of:

    * `{:success, value}` — terminal success; `wait/3` returns `{:ok, value}`.
    * `{:failure, reason}` — terminal failure; `wait/3` returns `{:error, reason}`.
    * `{:retry, value}` — sleep with exponential backoff and call `poll` again.

  `min_delay` and `max_delay` are seconds (matching the `@waitable` trait).
  After 100 retry attempts (hard cap, no setting today) `wait/3` gives up
  with `{:error, :max_attempts_exceeded}`.
  """
  @spec wait((() -> {:success | :failure | :retry, term()}), non_neg_integer(), non_neg_integer()) ::
          {:ok, term()} | {:error, term()}
  def wait(poll, min_delay, max_delay) when is_function(poll, 0) do
    wait_loop(poll, 0, 100, min_delay * 1000, max_delay * 1000)
  end

  # ---------------------------------------------------------------------------
  # Private helpers
  # ---------------------------------------------------------------------------

  defp wait_loop(_poll, attempt, max_attempts, _min_ms, _max_ms) when attempt >= max_attempts do
    {:error, :max_attempts_exceeded}
  end

  defp wait_loop(poll, attempt, max_attempts, min_ms, max_ms) do
    case poll.() do
      {:success, value} ->
        {:ok, value}

      {:failure, reason} ->
        {:error, reason}

      {:retry, _value} ->
        base_ms = round(min_ms * :math.pow(2, attempt))
        delay_ms = min(base_ms, max_ms)
        Process.sleep(delay_ms)
        wait_loop(poll, attempt + 1, max_attempts, min_ms, max_ms)
    end
  end

  defp retry_loop(fun, attempt, max_retries, initial_backoff, max_backoff, multiplier, use_jitter, logger) do
    case fun.() do
      {:ok, _} = ok ->
        ok

      {:error, reason} when attempt >= max_retries ->
        {:error, reason}

      {:error, reason} ->
        if retryable?(reason) do
          backoff =
            (initial_backoff * :math.pow(multiplier, attempt))
            |> round()
            |> min(max_backoff)
            |> maybe_jitter(use_jitter)

          if logger, do: logger.(%{
            event: :retry_attempt,
            attempt: attempt + 1,
            max_retries: max_retries,
            backoff_ms: backoff,
            error: reason
          })

          Process.sleep(backoff)

          retry_loop(fun, attempt + 1, max_retries, initial_backoff, max_backoff, multiplier, use_jitter, logger)
        else
          {:error, reason}
        end
    end
  end

  defp maybe_jitter(backoff, false), do: backoff
  defp maybe_jitter(backoff, true) do
    jitter = :rand.uniform(max(div(backoff, 4), 1))
    backoff + jitter
  end
end
