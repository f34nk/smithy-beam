defmodule Demo do
  @moduledoc """
  SQS demo covering CreateQueue, ListQueues, GetQueueUrl, SendMessage,
  GetQueueAttributes, ReceiveMessage, DeleteMessage, and DeleteQueue via
  the generated SqsClient.

  Uses the aws.protocols#awsJson1_0 protocol (JSON over HTTPS).
  Run against LocalStack: make demo
  """

  alias SqsTypes.{
    CreateQueueInput,
    CreateQueueOutput,
    DeleteMessageInput,
    DeleteQueueInput,
    GetQueueAttributesInput,
    GetQueueAttributesOutput,
    GetQueueUrlInput,
    GetQueueUrlOutput,
    ListQueuesInput,
    Message,
    MessageAttributeValue,
    QueueDoesNotExist,
    QueueNameExists,
    ReceiveMessageInput,
    ReceiveMessageOutput,
    SendMessageInput,
    SendMessageOutput
  }

  @queue_name "sqs-demo-queue"
  @msg1_body "Hello from Elixir! This is message 1."
  @msg2_body "Hello from Elixir! This is message 2."

  def run do
    IO.puts("\n=== Running SQS Client Application ===\n")

    config = client_config()
    IO.puts("SQS client configured for #{Map.fetch!(config, :base_url)}\n")

    queue_url = setup_infrastructure(config)

    list_queues(config)
    get_queue_url(config)
    send_messages(config, queue_url)
    get_queue_attributes(config, queue_url)
    receipt_handles = receive_messages(config, queue_url)
    delete_messages(config, queue_url, receipt_handles)
    verify_empty_queue(config, queue_url)
    delete_demo_queue(config, queue_url)

    IO.puts("\n=== SQS Client Application Complete ===")
    :ok
  end

  defp client_config do
    %{
      region: "us-east-1",
      endpoint_prefix: "sqs",
      signing_name: "sqs",
      base_url: System.get_env("AWS_ENDPOINT"),
      credentials: %{
        access_key_id: "dummy",
        secret_access_key: "dummy"
      }
    }
  end

  defp setup_infrastructure(config) do
    IO.puts("--- Setup infrastructure ---")
    queue_url = create_demo_queue(config)
    IO.puts("Infrastructure ready: Queue=#{@queue_name}\n")
    queue_url
  end

  defp create_demo_queue(config) do
    IO.puts("--- CreateQueue ---")

    input = %CreateQueueInput{
      queue_name: @queue_name,
      attributes: %{
        :delay_seconds => "0",
        :maximum_message_size => "262144",
        :message_retention_period => "345600",
        :receive_message_wait_time_seconds => "0",
        :visibility_timeout => "30"
      },
      tags: %{
        "Name" => @queue_name,
        "Environment" => "demo"
      }
    }

    case SqsClient.create_queue(config, input) do
      {:ok, %CreateQueueOutput{queue_url: url}} when is_binary(url) and url != "" ->
        IO.puts("SUCCESS: Queue '#{@queue_name}' created")
        IO.puts("SUCCESS: Queue URL: #{url}")
        IO.puts("")
        url

      {:ok, _} ->
        raise("assertion failed: CreateQueue returned empty URL")

      {:error, %QueueNameExists{}} ->
        IO.puts("SUCCESS: Queue '#{@queue_name}' already exists")

        case SqsClient.get_queue_url(config, %GetQueueUrlInput{queue_name: @queue_name}) do
          {:ok, %GetQueueUrlOutput{queue_url: url}} when is_binary(url) and url != "" ->
            IO.puts("SUCCESS: Queue URL: #{url}")
            IO.puts("")
            url

          {:error, reason} ->
            raise("get_queue_url_failed: #{inspect(reason)}")
        end

      {:error, reason} ->
        raise("create_queue_failed: #{inspect(reason)}")
    end
  end

  defp list_queues(config) do
    IO.puts("--- ListQueues ---")

    case SqsClient.list_queues(config, %ListQueuesInput{}) do
      {:ok, urls} when is_list(urls) and urls != [] ->
        IO.puts("SUCCESS: Found #{length(urls)} queue(s)")

        unless Enum.any?(urls, &String.contains?(&1, @queue_name)) do
          raise("assertion failed: queue #{inspect(@queue_name)} not found in #{inspect(urls)}")
        end

        IO.puts("SUCCESS: Queue '#{@queue_name}' found in list")
        Enum.each(urls, fn url -> IO.puts("  - #{url}") end)

      {:ok, _} ->
        raise("assertion failed: expected non-empty queue list")

      {:error, reason} ->
        raise("list_queues_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp get_queue_url(config) do
    IO.puts("--- GetQueueUrl ---")

    case SqsClient.get_queue_url(config, %GetQueueUrlInput{queue_name: @queue_name}) do
      {:ok, %GetQueueUrlOutput{queue_url: url}} when is_binary(url) and url != "" ->
        IO.puts("SUCCESS: Queue URL: #{url}")

      {:ok, _} ->
        raise("assertion failed: GetQueueUrl returned empty URL")

      {:error, reason} ->
        raise("get_queue_url_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp send_messages(config, queue_url) do
    IO.puts("--- SendMessage ---")

    input1 = %SendMessageInput{
      queue_url: queue_url,
      message_body: @msg1_body,
      message_attributes: %{
        "Author" => %MessageAttributeValue{
          data_type: "String",
          string_value: "Smithy-Elixir Demo"
        }
      }
    }

    case SqsClient.send_message(config, input1) do
      {:ok, %SendMessageOutput{message_id: msg_id}} when is_binary(msg_id) and msg_id != "" ->
        IO.puts("SUCCESS: Message sent, ID: #{msg_id}")

      {:ok, _} ->
        raise("assertion failed: SendMessage returned empty MessageId")

      {:error, reason} ->
        raise("send_message_failed: #{inspect(reason)}")
    end

    IO.puts("")

    IO.puts("--- SendMessage (second message) ---")

    input2 = %SendMessageInput{
      queue_url: queue_url,
      message_body: @msg2_body
    }

    case SqsClient.send_message(config, input2) do
      {:ok, %SendMessageOutput{message_id: msg_id}} when is_binary(msg_id) and msg_id != "" ->
        IO.puts("SUCCESS: Message sent, ID: #{msg_id}")

      {:ok, _} ->
        raise("assertion failed: SendMessage returned empty MessageId")

      {:error, reason} ->
        raise("send_message_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp get_queue_attributes(config, queue_url) do
    IO.puts("--- GetQueueAttributes ---")

    input = %GetQueueAttributesInput{
      queue_url: queue_url,
      attribute_names: [:all]
    }

    case SqsClient.get_queue_attributes(config, input) do
      {:ok, %GetQueueAttributesOutput{attributes: attrs}} when is_map(attrs) ->
        IO.puts("SUCCESS: Queue attributes:")
        Enum.each(attrs, fn {key, value} -> IO.puts("    #{key}: #{value}") end)

      {:ok, _} ->
        raise("assertion failed: GetQueueAttributes returned no attributes")

      {:error, reason} ->
        raise("get_queue_attributes_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp receive_messages(config, queue_url) do
    IO.puts("--- ReceiveMessage ---")

    input = %ReceiveMessageInput{
      queue_url: queue_url,
      max_number_of_messages: 10,
      wait_time_seconds: 1,
      message_attribute_names: ["All"]
    }

    case SqsClient.receive_message(config, input) do
      {:ok, %ReceiveMessageOutput{messages: messages}} when is_list(messages) and messages != [] ->
        IO.puts("SUCCESS: Received #{length(messages)} message(s)")

        received_bodies =
          for %Message{body: body} <- messages, not is_nil(body), do: body

        expected_bodies = Enum.sort([@msg1_body, @msg2_body])

        if Enum.sort(received_bodies) != expected_bodies do
          raise(
            "assertion failed: expected bodies #{inspect(expected_bodies)}, got #{inspect(received_bodies)}"
          )
        end

        IO.puts("SUCCESS: Message bodies match")

        Enum.map(messages, fn %Message{message_id: msg_id, body: body, receipt_handle: handle} ->
          IO.puts("  Message ID: #{format_string(msg_id)}")
          IO.puts("    Body: #{format_string(body)}")
          handle
        end)

      {:ok, _} ->
        raise("assertion failed: expected at least 1 message to be received")

      {:error, reason} ->
        raise("receive_message_failed: #{inspect(reason)}")
    end
    |> tap(fn _ -> IO.puts("") end)
  end

  defp delete_messages(config, queue_url, receipt_handles) do
    IO.puts("--- DeleteMessage ---")

    Enum.each(receipt_handles, fn
      nil ->
        :ok

      handle ->
        input = %DeleteMessageInput{queue_url: queue_url, receipt_handle: handle}

        case SqsClient.delete_message(config, input) do
          {:ok, _} -> IO.puts("SUCCESS: Message deleted")
          {:error, reason} -> raise("delete_message_failed: #{inspect(reason)}")
        end
    end)

    IO.puts("")
  end

  defp verify_empty_queue(config, queue_url) do
    IO.puts("--- ReceiveMessage (verify empty) ---")

    input = %ReceiveMessageInput{
      queue_url: queue_url,
      max_number_of_messages: 10,
      wait_time_seconds: 1,
      message_attribute_names: ["All"]
    }

    case SqsClient.receive_message(config, input) do
      {:ok, %ReceiveMessageOutput{messages: messages}} ->
        case messages_or_empty(messages) do
          [] -> IO.puts("SUCCESS: Queue is empty")
          remaining -> IO.puts("INFO: Queue still has #{length(remaining)} message(s)")
        end

      {:error, reason} ->
        raise("receive_message_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp delete_demo_queue(config, queue_url) do
    IO.puts("--- DeleteQueue ---")

    case SqsClient.delete_queue(config, %DeleteQueueInput{queue_url: queue_url}) do
      {:ok, _} ->
        IO.puts("SUCCESS: Queue '#{@queue_name}' deleted")

      {:error, %QueueDoesNotExist{}} ->
        IO.puts("SUCCESS: Queue '#{@queue_name}' already absent")

      {:error, reason} ->
        raise("delete_queue_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp messages_or_empty(nil), do: []
  defp messages_or_empty(messages) when is_list(messages), do: messages
  defp messages_or_empty(_), do: []

  defp format_string(nil), do: "unknown"
  defp format_string(value) when is_binary(value), do: value
end
