defmodule AwsJson11Service.Client do
  @spec describe_item(map(), DescribeItemInput.t()) ::
    {:ok, DescribeItemOutput.t()} | {:error, term()}
  def describe_item(config, input) do
    SmithyClient.execute(config, describe_item_op(input))
  end

  @doc false
  @spec describe_item_op(DescribeItemInput.t()) :: SmithyClient.Operation.t()
  defp describe_item_op(%DescribeItemInput{} = input) do
    %SmithyClient.Operation{
      name: :describe_item,
      action: "DescribeItem",
      http: %{method: "POST", uri: "/"},
      static_headers: [
        {"X-Amz-Target", "AwsJson11Service.DescribeItem"}
      ],
      input: input,
      output_shape: DescribeItemOutput,
      auth: :sigv4,
      content_type: "application/json",
      encoding: :json,
      decoding: :json,
      parse_error_fn: &SmithyJson.parse_aws_error/2,
    }
  end


end
