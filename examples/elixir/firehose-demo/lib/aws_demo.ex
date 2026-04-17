defmodule AwsDemo do
  @moduledoc """
  Firehose demo — covers ListDeliveryStreams, CreateDeliveryStream,
  DescribeDeliveryStream, ListTagsForDeliveryStream, PutRecord,
  PutRecordBatch, TagDeliveryStream, UntagDeliveryStream, and
  DeleteDeliveryStream via the generated AwsFirehoseClient.

  Uses the aws.protocols#awsJson1_1 protocol (JSON over HTTPS).
  Note: PutRecord/PutRecordBatch may return 500 errors on LocalStack because
  it attempts to deliver to the configured HTTP endpoint. The client API works
  correctly — only the backend delivery fails.
  Run against LocalStack: make demo
  """

  @stream_name "firehose-demo-stream"

  def run do
    IO.puts("\n=== Running Firehose Client Application ===\n")

    config = %{
      endpoint: System.get_env("AWS_ENDPOINT"),
      region: "us-east-1",
      service: "firehose",
      credentials: %{
        access_key_id: "dummy",
        secret_access_key: "dummy"
      }
    }

    {:ok, client} = AwsFirehoseClient.new(config)
    IO.puts("Client created successfully\n")

    # 1. ListDeliveryStreams (initial) — may be empty, no assertion required
    IO.puts("--- ListDeliveryStreams (initial) ---")
    case AwsFirehoseClient.list_delivery_streams(client, %{}, %{enable_retry: false}) do
      {:ok, output} ->
        names = Map.get(output, "DeliveryStreamNames", [])
        IO.puts("SUCCESS: Found #{length(names)} delivery stream(s)")
        Enum.each(names, fn n -> IO.puts("  - #{n}") end)
      {:error, err} ->
        raise("list_delivery_streams_failed: #{inspect(err)}")
    end
    IO.puts("")

    # 2. CreateDeliveryStream
    IO.puts("--- CreateDeliveryStream ---")
    create_input = %{
      "DeliveryStreamName" => @stream_name,
      "DeliveryStreamType" => "DirectPut",
      "HttpEndpointDestinationConfiguration" => %{
        "EndpointConfiguration" => %{
          "Url"  => "http://localhost:9999/firehose",
          "Name" => "demo-endpoint"
        },
        "BufferingHints" => %{
          "SizeInMBs"         => 1,
          "IntervalInSeconds" => 60
        },
        "S3Configuration" => %{
          "RoleARN"   => "arn:aws:iam::000000000000:role/firehose-role",
          "BucketARN" => "arn:aws:s3:::firehose-backup-bucket"
        },
        "RequestConfiguration" => %{
          "ContentEncoding" => "NONE"
        },
        "RetryOptions" => %{
          "DurationInSeconds" => 60
        }
      },
      "Tags" => [
        %{"Key" => "Environment", "Value" => "demo"},
        %{"Key" => "Project",     "Value" => "smithy-elixir"}
      ]
    }
    stream_created =
      case AwsFirehoseClient.create_delivery_stream(client, create_input, %{enable_retry: false}) do
        {:ok, output} ->
          IO.puts("SUCCESS: Created delivery stream")
          IO.puts("    ARN: #{Map.get(output, "DeliveryStreamARN", "unknown")}")
          true
        {:error, err} ->
          raise("create_delivery_stream_failed: #{inspect(err)}")
      end
    IO.puts("")

    if stream_created do
      IO.puts("Waiting for stream to become active...")
      Process.sleep(2000)

      # 3. ListDeliveryStreams — assert stream appears in list
      IO.puts("--- ListDeliveryStreams ---")
      case AwsFirehoseClient.list_delivery_streams(client, %{}, %{enable_retry: false}) do
        {:ok, output} ->
          names = Map.get(output, "DeliveryStreamNames", [])
          if names == [],
            do: raise("assertion failed: expected non-empty stream list after create")
          IO.puts("SUCCESS: Found #{length(names)} delivery stream(s)")
          unless @stream_name in names,
            do: raise("assertion failed: stream #{inspect(@stream_name)} not found in list")
          IO.puts("SUCCESS: Stream '#{@stream_name}' in list")
          Enum.each(names, fn n -> IO.puts("  - #{n}") end)
        {:error, err} ->
          raise("list_delivery_streams_failed: #{inspect(err)}")
      end
      IO.puts("")

      # 4. DescribeDeliveryStream — assert status is ACTIVE
      IO.puts("--- DescribeDeliveryStream ---")
      describe_input = %{"DeliveryStreamName" => @stream_name}
      case AwsFirehoseClient.describe_delivery_stream(client, describe_input, %{enable_retry: false}) do
        {:ok, output} ->
          desc = Map.get(output, "DeliveryStreamDescription", %{})
          status = Map.get(desc, "DeliveryStreamStatus", "unknown")
          if status != "ACTIVE",
            do: raise("assertion failed: expected stream status ACTIVE, got #{inspect(status)}")
          IO.puts("SUCCESS: Stream is ACTIVE")
          IO.puts("    Name:   #{@stream_name}")
          IO.puts("    ARN:    #{Map.get(desc, "DeliveryStreamARN", "unknown")}")
          IO.puts("    Status: #{status}")
          IO.puts("    Type:   #{Map.get(desc, "DeliveryStreamType", "unknown")}")
        {:error, err} ->
          raise("describe_delivery_stream_failed: #{inspect(err)}")
      end
      IO.puts("")

      # 5. ListTagsForDeliveryStream
      IO.puts("--- ListTagsForDeliveryStream ---")
      case AwsFirehoseClient.list_tags_for_delivery_stream(client, %{"DeliveryStreamName" => @stream_name}, %{enable_retry: false}) do
        {:ok, output} ->
          tags = Map.get(output, "Tags", [])
          IO.puts("SUCCESS: Found #{length(tags)} tag(s):")
          Enum.each(tags, fn t ->
            IO.puts("    #{Map.get(t, "Key", "?")} = #{Map.get(t, "Value", "?")}")
          end)
        {:error, err} ->
          raise("list_tags_for_delivery_stream_failed: #{inspect(err)}")
      end
      IO.puts("")

      # 6. PutRecord — LocalStack may 500, keep as non-crashing
      IO.puts("--- PutRecord ---")
      record1 =
        Jason.encode!(%{
          "event"     => "user_login",
          "user_id"   => "user-123",
          "timestamp" => DateTime.utc_now() |> DateTime.to_iso8601(),
          "source"    => "smithy-elixir-firehose-demo"
        })
      put_input = %{
        "DeliveryStreamName" => @stream_name,
        "Record" => %{
          "Data" => Base.encode64(record1 <> "\n")
        }
      }
      case AwsFirehoseClient.put_record(client, put_input, %{enable_retry: false}) do
        {:ok, output} ->
          IO.puts("SUCCESS: Record sent, ID: #{Map.get(output, "RecordId", "unknown")}")
        {:error, err} ->
          IO.puts("INFO: PutRecord returned error (expected in LocalStack): #{inspect(err)}")
      end
      IO.puts("")

      # 7. PutRecordBatch — LocalStack may 500, keep as non-crashing
      IO.puts("--- PutRecordBatch ---")
      now = DateTime.utc_now() |> DateTime.to_iso8601()
      records = [
        %{"Data" => Base.encode64(Jason.encode!(%{"event" => "page_view",    "page"   => "/home",        "user_id" => "user-123", "timestamp" => now}))},
        %{"Data" => Base.encode64(Jason.encode!(%{"event" => "page_view",    "page"   => "/products",    "user_id" => "user-456", "timestamp" => now}))},
        %{"Data" => Base.encode64(Jason.encode!(%{"event" => "button_click", "button" => "add_to_cart",  "user_id" => "user-789", "timestamp" => now}))}
      ]
      batch_input = %{
        "DeliveryStreamName" => @stream_name,
        "Records"            => records
      }
      case AwsFirehoseClient.put_record_batch(client, batch_input, %{enable_retry: false}) do
        {:ok, output} ->
          failed = Map.get(output, "FailedPutCount", 0)
          responses = Map.get(output, "RequestResponses", [])
          IO.puts("SUCCESS: Batch sent, #{length(responses)} records, #{failed} failed")
          Enum.each(responses, fn resp ->
            case Map.get(resp, "RecordId") do
              nil -> IO.puts("    FAILED: #{Map.get(resp, "ErrorCode", "unknown")}")
              rid -> IO.puts("    Record ID: #{rid}")
            end
          end)
        {:error, err} ->
          IO.puts("INFO: PutRecordBatch returned error (expected in LocalStack): #{inspect(err)}")
      end
      IO.puts("")

      IO.puts("Note: PutRecord/PutRecordBatch may return 500 errors because LocalStack")
      IO.puts("attempts to deliver to the configured HTTP endpoint (which doesn't exist).")
      IO.puts("This is expected — the client API works correctly.\n")

      # 8. TagDeliveryStream
      IO.puts("--- TagDeliveryStream ---")
      tag_input = %{
        "DeliveryStreamName" => @stream_name,
        "Tags"               => [%{"Key" => "CreatedBy", "Value" => "smithy-elixir"}]
      }
      case AwsFirehoseClient.tag_delivery_stream(client, tag_input, %{enable_retry: false}) do
        {:ok, _} -> IO.puts("SUCCESS: Tag added")
        {:error, err} -> raise("tag_delivery_stream_failed: #{inspect(err)}")
      end
      IO.puts("")

      # 9. UntagDeliveryStream
      IO.puts("--- UntagDeliveryStream ---")
      untag_input = %{
        "DeliveryStreamName" => @stream_name,
        "TagKeys"            => ["CreatedBy"]
      }
      case AwsFirehoseClient.untag_delivery_stream(client, untag_input, %{enable_retry: false}) do
        {:ok, _} -> IO.puts("SUCCESS: Tag 'CreatedBy' removed")
        {:error, err} -> raise("untag_delivery_stream_failed: #{inspect(err)}")
      end
      IO.puts("")

      # 10. DeleteDeliveryStream
      IO.puts("--- DeleteDeliveryStream ---")
      case AwsFirehoseClient.delete_delivery_stream(client, %{"DeliveryStreamName" => @stream_name}, %{enable_retry: false}) do
        {:ok, _} -> IO.puts("SUCCESS: Delivery stream deleted")
        {:error, err} -> raise("delete_delivery_stream_failed: #{inspect(err)}")
      end
      IO.puts("")

      # 11. ListDeliveryStreams — crash if stream still exists
      IO.puts("--- ListDeliveryStreams (verify deletion) ---")
      Process.sleep(1000)
      case AwsFirehoseClient.list_delivery_streams(client, %{}, %{enable_retry: false}) do
        {:ok, output} ->
          remaining = Map.get(output, "DeliveryStreamNames", [])
          if @stream_name in remaining,
            do: raise("assertion failed: stream #{inspect(@stream_name)} still exists after delete")
          IO.puts("SUCCESS: Stream deleted successfully")
          IO.puts("Remaining streams: #{length(remaining)}")
        {:error, err} ->
          raise("list_delivery_streams_failed: #{inspect(err)}")
      end
    else
      IO.puts("Cannot continue without delivery stream")
    end

    IO.puts("\n=== Firehose Client Application Complete ===")
    :ok
  end
end
