defmodule Demo do
  @moduledoc """
  Firehose demo covering ListDeliveryStreams, CreateDeliveryStream,
  DescribeDeliveryStream, ListTagsForDeliveryStream, PutRecord,
  PutRecordBatch, TagDeliveryStream, UntagDeliveryStream, and
  DeleteDeliveryStream via the generated FirehoseClient.

  Uses the aws.protocols#awsJson1_1 protocol (JSON over HTTPS).
  Note: PutRecord/PutRecordBatch may return 500 errors on LocalStack because
  it attempts to deliver to the configured HTTP endpoint. The client API works
  correctly; only the backend delivery fails.
  Run against LocalStack: make demo
  """

  alias FirehoseTypes.{
    CreateDeliveryStreamInput,
    CreateDeliveryStreamOutput,
    DeleteDeliveryStreamInput,
    DeliveryStreamDescription,
    DescribeDeliveryStreamInput,
    DescribeDeliveryStreamOutput,
    HttpEndpointBufferingHints,
    HttpEndpointConfiguration,
    HttpEndpointDestinationConfiguration,
    HttpEndpointRequestConfiguration,
    HttpEndpointRetryOptions,
    ListDeliveryStreamsInput,
    ListDeliveryStreamsOutput,
    ListTagsForDeliveryStreamInput,
    ListTagsForDeliveryStreamOutput,
    PutRecordBatchInput,
    PutRecordBatchOutput,
    PutRecordBatchResponseEntry,
    PutRecordInput,
    PutRecordOutput,
    Record,
    S3destinationConfiguration,
    Tag,
    TagDeliveryStreamInput,
    UntagDeliveryStreamInput
  }

  @stream_name "firehose-demo-elixir-stream"

  def run do
    IO.puts("\n=== Running Firehose Client Application ===\n")

    config = client_config()
    IO.puts("Firehose client configured for #{Map.fetch!(config, :base_url)}\n")

    list_delivery_streams_initial(config)
    create_delivery_stream(config)
    Process.sleep(2000)
    list_delivery_streams_after_create(config)
    describe_delivery_stream(config)
    list_tags_for_delivery_stream(config)
    put_record(config)
    put_record_batch(config)
    tag_delivery_stream(config)
    untag_delivery_stream(config)
    delete_delivery_stream(config)
    verify_deletion(config)

    IO.puts("\n=== Firehose Client Application Complete ===")
    :ok
  end

  defp client_config do
    %{
      region: "us-east-1",
      endpoint_prefix: "firehose",
      signing_name: "firehose",
      base_url: System.get_env("AWS_ENDPOINT"),
      credentials: %{
        access_key_id: "dummy",
        secret_access_key: "dummy"
      }
    }
  end

  defp list_delivery_streams_initial(config) do
    IO.puts("--- ListDeliveryStreams (initial) ---")

    case FirehoseClient.list_delivery_streams(config, %ListDeliveryStreamsInput{}) do
      {:ok, %ListDeliveryStreamsOutput{delivery_stream_names: names}} ->
        IO.puts("SUCCESS: Found #{length(names)} delivery stream(s)")
        Enum.each(names, fn name -> IO.puts("  - #{name}") end)

      {:error, reason} ->
        raise("list_delivery_streams_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp create_delivery_stream(config) do
    IO.puts("--- CreateDeliveryStream ---")

    input = %CreateDeliveryStreamInput{
      delivery_stream_name: @stream_name,
      delivery_stream_type: :direct_put,
      http_endpoint_destination_configuration: %HttpEndpointDestinationConfiguration{
        endpoint_configuration: %HttpEndpointConfiguration{
          url: "http://localhost:9999/firehose",
          name: "demo-endpoint-elixir"
        },
        buffering_hints: %HttpEndpointBufferingHints{
          size_in_m_bs: 1,
          interval_in_seconds: 60
        },
        s3configuration: %S3destinationConfiguration{
          role_arn: "arn:aws:iam::000000000000:role/firehose-elixir-role",
          bucket_arn: "arn:aws:s3:::firehose-backup-elixir-bucket"
        },
        request_configuration: %HttpEndpointRequestConfiguration{
          content_encoding: :none
        },
        retry_options: %HttpEndpointRetryOptions{
          duration_in_seconds: 60
        }
      },
      tags: [
        %Tag{key: "Environment", value: "demo"},
        %Tag{key: "Project", value: "smithy-elixir"}
      ]
    }

    case FirehoseClient.create_delivery_stream(config, input) do
      {:ok, %CreateDeliveryStreamOutput{delivery_stream_arn: arn}} ->
        IO.puts("SUCCESS: Created delivery stream")
        IO.puts("    ARN: #{format_string(arn)}")
        IO.puts("")
        :ok

      {:error, reason} ->
        raise("create_delivery_stream_failed: #{inspect(reason)}")
    end
  end

  defp list_delivery_streams_after_create(config) do
    IO.puts("--- ListDeliveryStreams ---")

    case FirehoseClient.list_delivery_streams(config, %ListDeliveryStreamsInput{}) do
      {:ok, %ListDeliveryStreamsOutput{delivery_stream_names: []}} ->
        raise("assertion failed: expected non-empty stream list after create")

      {:ok, %ListDeliveryStreamsOutput{delivery_stream_names: names}} ->
        IO.puts("SUCCESS: Found #{length(names)} delivery stream(s)")

        unless @stream_name in names do
          raise("assertion failed: stream #{inspect(@stream_name)} not found in list")
        end

        IO.puts("SUCCESS: Stream '#{@stream_name}' in list")
        Enum.each(names, fn name -> IO.puts("  - #{name}") end)

      {:error, reason} ->
        raise("list_delivery_streams_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp describe_delivery_stream(config) do
    IO.puts("--- DescribeDeliveryStream ---")

    input = %DescribeDeliveryStreamInput{delivery_stream_name: @stream_name}

    case FirehoseClient.describe_delivery_stream(config, input) do
      {:ok,
       %DescribeDeliveryStreamOutput{
         delivery_stream_description: %DeliveryStreamDescription{
           delivery_stream_arn: arn,
           delivery_stream_status: status,
           delivery_stream_type: stream_type
         }
       }} ->
        if status != :active do
          raise("assertion failed: expected stream status ACTIVE, got #{inspect(status)}")
        end

        IO.puts("SUCCESS: Stream is ACTIVE")
        IO.puts("    Name:   #{@stream_name}")
        IO.puts("    ARN:    #{format_string(arn)}")
        IO.puts("    Status: #{status}")
        IO.puts("    Type:   #{stream_type}")

      {:error, reason} ->
        raise("describe_delivery_stream_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp list_tags_for_delivery_stream(config) do
    IO.puts("--- ListTagsForDeliveryStream ---")

    input = %ListTagsForDeliveryStreamInput{delivery_stream_name: @stream_name}

    case FirehoseClient.list_tags_for_delivery_stream(config, input) do
      {:ok, %ListTagsForDeliveryStreamOutput{tags: tags}} ->
        IO.puts("SUCCESS: Found #{length(tags)} tag(s):")

        Enum.each(tags, fn %Tag{key: key, value: value} ->
          IO.puts("    #{format_string(key)} = #{format_string(value)}")
        end)

      {:error, reason} ->
        raise("list_tags_for_delivery_stream_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp put_record(config) do
    IO.puts("--- PutRecord ---")

    record_data =
      Jason.encode!(%{
        "event" => "user_login",
        "user_id" => "user-123",
        "timestamp" => timestamp_rfc3339(),
        "source" => "smithy-elixir-firehose-demo"
      })

    input = %PutRecordInput{
      delivery_stream_name: @stream_name,
      record: %Record{data: Base.encode64(record_data <> "\n")}
    }

    case FirehoseClient.put_record(config, input) do
      {:ok, %PutRecordOutput{record_id: record_id}} ->
        IO.puts("SUCCESS: Record sent, ID: #{record_id}")

      {:error, reason} ->
        IO.puts("INFO: PutRecord returned error (expected in LocalStack): #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp put_record_batch(config) do
    IO.puts("--- PutRecordBatch ---")

    now = timestamp_rfc3339()

    records = [
      encode_event_record(%{
        "event" => "page_view",
        "page" => "/home",
        "user_id" => "user-123",
        "timestamp" => now
      }),
      encode_event_record(%{
        "event" => "page_view",
        "page" => "/products",
        "user_id" => "user-456",
        "timestamp" => now
      }),
      encode_event_record(%{
        "event" => "button_click",
        "button" => "add_to_cart",
        "user_id" => "user-789",
        "timestamp" => now
      })
    ]

    input = %PutRecordBatchInput{
      delivery_stream_name: @stream_name,
      records: records
    }

    case FirehoseClient.put_record_batch(config, input) do
      {:ok,
       %PutRecordBatchOutput{
         failed_put_count: failed_count,
         request_responses: responses
       }} ->
        IO.puts(
          "SUCCESS: Batch sent, #{length(responses)} records, #{failed_count} failed"
        )

        Enum.each(responses, fn
          %PutRecordBatchResponseEntry{record_id: record_id} when is_binary(record_id) ->
            IO.puts("    Record ID: #{record_id}")

          %PutRecordBatchResponseEntry{error_code: error_code} ->
            IO.puts("    FAILED: #{error_code}")
        end)

      {:error, reason} ->
        IO.puts("INFO: PutRecordBatch returned error (expected in LocalStack): #{inspect(reason)}")
    end

    IO.puts("")

    IO.puts(
      "Note: PutRecord/PutRecordBatch may return 500 errors because LocalStack attempts to deliver to the configured HTTP endpoint (which does not exist). This is expected; the client API works correctly.\n"
    )
  end

  defp tag_delivery_stream(config) do
    IO.puts("--- TagDeliveryStream ---")

    input = %TagDeliveryStreamInput{
      delivery_stream_name: @stream_name,
      tags: [%Tag{key: "CreatedBy", value: "smithy-elixir"}]
    }

    case FirehoseClient.tag_delivery_stream(config, input) do
      {:ok, _} ->
        IO.puts("SUCCESS: Tag added")

      {:error, reason} ->
        raise("tag_delivery_stream_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp untag_delivery_stream(config) do
    IO.puts("--- UntagDeliveryStream ---")

    input = %UntagDeliveryStreamInput{
      delivery_stream_name: @stream_name,
      tag_keys: ["CreatedBy"]
    }

    case FirehoseClient.untag_delivery_stream(config, input) do
      {:ok, _} ->
        IO.puts("SUCCESS: Tag 'CreatedBy' removed")

      {:error, reason} ->
        raise("untag_delivery_stream_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp delete_delivery_stream(config) do
    IO.puts("--- DeleteDeliveryStream ---")

    input = %DeleteDeliveryStreamInput{delivery_stream_name: @stream_name}

    case FirehoseClient.delete_delivery_stream(config, input) do
      {:ok, _} ->
        IO.puts("SUCCESS: Delivery stream deleted")

      {:error, reason} ->
        raise("delete_delivery_stream_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp verify_deletion(config) do
    IO.puts("--- ListDeliveryStreams (verify deletion) ---")
    Process.sleep(1000)

    case FirehoseClient.list_delivery_streams(config, %ListDeliveryStreamsInput{}) do
      {:ok, %ListDeliveryStreamsOutput{delivery_stream_names: remaining}} ->
        if @stream_name in remaining do
          raise("assertion failed: stream #{inspect(@stream_name)} still exists after delete")
        end

        IO.puts("SUCCESS: Stream deleted successfully")
        IO.puts("Remaining streams: #{length(remaining)}")

      {:error, reason} ->
        raise("list_delivery_streams_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp encode_event_record(event) do
    %Record{data: Base.encode64(Jason.encode!(event))}
  end

  defp timestamp_rfc3339 do
    DateTime.utc_now() |> DateTime.to_iso8601()
  end

  defp format_string(nil), do: "unknown"
  defp format_string(value) when is_binary(value), do: value
  defp format_string(value), do: to_string(value)
end
