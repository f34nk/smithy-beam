defmodule AwsJson11Service.Client do
  @spec describe_item(map(), DescribeItemInput.t()) ::
    {:ok, DescribeItemOutput.t()} | {:error, term()}

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

  def describe_item(config, input) do
    {:error, :not_implemented}
  end

end
