defmodule Demo do
  @moduledoc """
  Kinesis demo covering CreateStream, ListStreams, DescribeStream, PutRecord,
  GetShardIterator, GetRecords, DescribeStreamSummary, and DeleteStream via the
  generated KinesisClient.

  Uses the aws.protocols#awsJson1_1 protocol (JSON over HTTPS).
  Run against LocalStack: make demo
  """

  alias KinesisTypes.{
    CreateStreamInput,
    DeleteStreamInput,
    DescribeStreamInput,
    DescribeStreamOutput,
    DescribeStreamSummaryInput,
    DescribeStreamSummaryOutput,
    GetRecordsInput,
    GetRecordsOutput,
    GetShardIteratorInput,
    GetShardIteratorOutput,
    ListStreamsInput,
    ListStreamsOutput,
    PutRecordInput,
    PutRecordOutput,
    Record,
    ResourceInUseException,
    ResourceNotFoundException,
    Shard,
    StreamDescription,
    StreamDescriptionSummary,
    StreamModeDetails
  }

  @stream_name "kinesis-demo-elixir-stream"

  @sent_records [
    %{"message" => "Hello from Elixir!", "id" => 1},
    %{"message" => "Kinesis streaming data", "id" => 2},
    %{"message" => "Smithy-Elixir demo", "id" => 3}
  ]

  def run do
    IO.puts("\n=== Running Kinesis Client Application ===\n")

    config = client_config()
    IO.puts("Kinesis client configured for #{Map.fetch!(config, :base_url)}\n")

    setup_infrastructure(config)
    list_streams(config)
    shard_id = describe_stream(config)
    put_records(config, shard_id)
    shard_iterator = get_shard_iterator(config, shard_id)
    get_records(config, shard_iterator)
    describe_stream_summary(config)
    delete_demo_stream(config)

    IO.puts("=== Kinesis Client Application Complete ===")
    :ok
  end

  defp client_config do
    %{
      region: "us-east-1",
      endpoint_prefix: "kinesis",
      signing_name: "kinesis",
      base_url: System.get_env("AWS_ENDPOINT"),
      credentials: %{
        access_key_id: "dummy",
        secret_access_key: "dummy"
      }
    }
  end

  defp setup_infrastructure(config) do
    IO.puts("--- Setup infrastructure ---")
    create_demo_stream(config)
    IO.puts("Infrastructure ready: StreamName=#{@stream_name}\n")
  end

  defp create_demo_stream(config) do
    IO.puts("--- CreateStream ---")

    input = %CreateStreamInput{
      stream_name: @stream_name,
      shard_count: 1,
      stream_mode_details: %StreamModeDetails{stream_mode: :provisioned},
      tags: %{
        "Name" => @stream_name,
        "Environment" => "demo"
      }
    }

    case KinesisClient.create_stream(config, input) do
      {:ok, _} ->
        IO.puts("SUCCESS: Stream '#{@stream_name}' create requested")

      {:error, %ResourceInUseException{}} ->
        IO.puts("SUCCESS: Stream '#{@stream_name}' already exists")

      {:error, reason} ->
        raise("create_stream_failed: #{inspect(reason)}")
    end

    IO.puts("--- Wait StreamExists ---")

    wait_input = %DescribeStreamInput{stream_name: @stream_name}

    case KinesisWaiters.wait_stream_exists(config, wait_input, []) do
      {:ok, _} ->
        IO.puts("SUCCESS: Stream '#{@stream_name}' is ACTIVE\n")

      {:error, reason} ->
        raise("wait_stream_exists_failed: #{inspect(reason)}")
    end
  end

  defp list_streams(config) do
    IO.puts("--- ListStreams ---")

    case KinesisClient.list_streams(config, %ListStreamsInput{}) do
      {:ok, pages} when is_list(pages) ->
        stream_names = stream_names_from_list_streams_pages(pages)

        case stream_names do
          [] -> raise("assertion failed: empty stream list")
          _ -> IO.puts("SUCCESS: Found #{length(stream_names)} stream(s)")
        end

        unless @stream_name in stream_names do
          raise("assertion failed: stream #{inspect(@stream_name)} not found")
        end

        IO.puts("SUCCESS: Stream '#{@stream_name}' found")
        Enum.each(stream_names, fn name -> IO.puts("  - #{name}") end)

      {:error, reason} ->
        raise("list_streams_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp stream_names_from_list_streams_pages(pages) do
    Enum.flat_map(pages, fn
      %ListStreamsOutput{stream_names: nil} ->
        []

      %ListStreamsOutput{stream_names: names} when is_list(names) ->
        names

      _ ->
        []
    end)
  end

  defp describe_stream(config) do
    IO.puts("--- DescribeStream ---")

    input = %DescribeStreamInput{stream_name: @stream_name}

    case KinesisClient.describe_stream(config, input) do
      {:ok,
       %DescribeStreamOutput{
         stream_description: %StreamDescription{
           stream_status: status,
           retention_period_hours: retention_hours,
           shards: shards
         }
       }} ->
        if status != :active do
          raise("assertion failed: expected ACTIVE stream, got #{inspect(status)}")
        end

        if shards == [] do
          raise("assertion failed: no shards in stream")
        end

        IO.puts("SUCCESS: Stream status is ACTIVE")
        IO.puts("SUCCESS: Stream has #{length(shards)} shard(s)")
        IO.puts("  Stream: #{@stream_name}")
        IO.puts("  Status: #{status}")
        IO.puts("  Retention: #{retention_hours} hours")

        case shards do
          [%Shard{shard_id: shard_id} | _] -> shard_id
          _ -> raise("assertion failed: no shard id available")
        end

      {:error, reason} ->
        raise("describe_stream_failed: #{inspect(reason)}")
    end
    |> tap(fn _ -> IO.puts("") end)
  end

  defp put_records(config, shard_id) do
    IO.puts("--- PutRecord (3 records) ---")

    Enum.with_index(@sent_records, 1)
    |> Enum.each(fn {record, idx} ->
      data = record |> Jason.encode!() |> Base.encode64()
      partition_key = "partition-#{idx}"

      input = %PutRecordInput{
        stream_name: @stream_name,
        data: data,
        partition_key: partition_key
      }

      case KinesisClient.put_record(config, input) do
        {:ok, %PutRecordOutput{sequence_number: seq_num, shard_id: shard_id_out}}
        when is_binary(seq_num) and byte_size(seq_num) > 0 ->
          IO.puts("  Record #{idx}: ShardId=#{shard_id_out}, Seq=#{seq_num}")

        {:ok, _} ->
          raise("assertion failed: put_record returned empty sequence number for record #{idx}")

        {:error, reason} ->
          raise("put_record_failed: record #{idx}: #{inspect(reason)}")
      end
    end)

    IO.puts("")
    shard_id
  end

  defp get_shard_iterator(config, shard_id) do
    IO.puts("--- GetShardIterator ---")

    input = %GetShardIteratorInput{
      stream_name: @stream_name,
      shard_id: shard_id,
      shard_iterator_type: :trim_horizon
    }

    case KinesisClient.get_shard_iterator(config, input) do
      {:ok, %GetShardIteratorOutput{shard_iterator: iterator}}
      when is_binary(iterator) and byte_size(iterator) > 0 ->
        IO.puts("SUCCESS: Got shard iterator")
        IO.puts("")
        iterator

      {:ok, _} ->
        raise("assertion failed: get_shard_iterator returned empty iterator")

      {:error, reason} ->
        raise("get_shard_iterator_failed: #{inspect(reason)}")
    end
  end

  defp get_records(config, shard_iterator) do
    IO.puts("--- GetRecords ---")

    input = %GetRecordsInput{
      shard_iterator: shard_iterator,
      limit: 10
    }

    case KinesisClient.get_records(config, input) do
      {:ok,
       %GetRecordsOutput{
         records: fetched_records,
         millis_behind_latest: millis_behind
       }} ->
        IO.puts(
          "SUCCESS: Retrieved #{length(fetched_records)} record(s), #{millis_behind} ms behind latest"
        )

        if length(fetched_records) != 3 do
          raise(
            "assertion failed: expected 3 records, got #{length(fetched_records)}"
          )
        end

        IO.puts("SUCCESS: All 3 records retrieved")

        decoded_bodies =
          Enum.map(fetched_records, fn %Record{data: data} ->
            data |> Base.decode64!() |> Jason.decode!()
          end)

        if decoded_bodies != @sent_records do
          raise(
            "assertion failed: expected #{inspect(@sent_records)}, got #{inspect(decoded_bodies)}"
          )
        end

        IO.puts("SUCCESS: Record bodies match")

        Enum.each(fetched_records, fn %Record{
                                        partition_key: part_key,
                                        sequence_number: seq_num,
                                        data: data
                                      } ->
          decoded_data =
            try do
              data |> Base.decode64!() |> Jason.decode!()
            rescue
              _ -> data
            end

          IO.puts("  Record: PartitionKey=#{part_key}")
          IO.puts("    SequenceNumber: #{seq_num}")
          IO.puts("    Data: #{inspect(decoded_data)}")
        end)

      {:error, reason} ->
        raise("get_records_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp describe_stream_summary(config) do
    IO.puts("--- DescribeStreamSummary ---")

    input = %DescribeStreamSummaryInput{stream_name: @stream_name}

    case KinesisClient.describe_stream_summary(config, input) do
      {:ok,
       %DescribeStreamSummaryOutput{
         stream_description_summary: %StreamDescriptionSummary{
           open_shard_count: open_shards,
           consumer_count: consumer_count
         }
       }} ->
        IO.puts("SUCCESS: Stream summary")
        IO.puts("  Open shards: #{open_shards}")
        IO.puts("  Consumers: #{consumer_count}")

      {:error, reason} ->
        raise("describe_stream_summary_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp delete_demo_stream(config) do
    IO.puts("--- DeleteStream ---")

    case KinesisClient.delete_stream(config, %DeleteStreamInput{stream_name: @stream_name}) do
      {:ok, _} ->
        IO.puts("SUCCESS: Stream '#{@stream_name}' delete requested")

      {:error, %ResourceNotFoundException{}} ->
        IO.puts("SUCCESS: Stream '#{@stream_name}' already absent")

      {:error, reason} ->
        raise("delete_stream_failed: #{inspect(reason)}")
    end

    IO.puts("--- Wait StreamNotExists ---")

    wait_input = %DescribeStreamInput{stream_name: @stream_name}

    case KinesisWaiters.wait_stream_not_exists(config, wait_input, []) do
      {:ok, _} ->
        IO.puts("SUCCESS: Stream '#{@stream_name}' removed\n")

      {:error, reason} ->
        raise("wait_stream_not_exists_failed: #{inspect(reason)}")
    end
  end
end
