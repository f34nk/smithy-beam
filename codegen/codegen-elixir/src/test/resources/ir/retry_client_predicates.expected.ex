def should_retry?({:error, %RetryServiceTypes.RetryableError{}}), do: true
def should_retry?(_), do: false

defp retryable?(%RetryServiceTypes.RetryableError{}), do: true
defp retryable?(_), do: false

defp throttling?(_), do: false
