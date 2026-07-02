defmodule Demo do
  @moduledoc """
  SNS demo covering CreateTopic, ListTopics, GetTopicAttributes,
  ListTagsForResource, Subscribe, ListSubscriptionsByTopic, Publish,
  Unsubscribe, and DeleteTopic via the generated SnsClient.

  Uses the aws.protocols#awsQuery protocol (XML over HTTPS).
  Run against LocalStack: make demo
  """

  alias SnsTypes.{
    CreateTopicInput,
    CreateTopicOutput,
    DeleteTopicInput,
    GetTopicAttributesInput,
    GetTopicAttributesOutput,
    ListSubscriptionsByTopicInput,
    ListTagsForResourceInput,
    ListTagsForResourceOutput,
    ListTopicsInput,
    MessageAttributeValue,
    PublishInput,
    PublishOutput,
    SubscribeInput,
    SubscribeOutput,
    Subscription,
    Tag,
    Topic,
    UnsubscribeInput
  }

  @topic_name "sns-demo-topic"
  @test_topic_name "sns-demo-test-topic"

  def run do
    IO.puts("\n=== Running SNS Client Application ===\n")

    config = client_config()
    IO.puts("SNS client configured for #{Map.fetch!(config, :base_url)}\n")

    setup_infrastructure(config)
    list_topics(config)
    test_topic_arn = create_test_topic(config)
    get_topic_attributes(config, test_topic_arn)
    list_tags_for_resource(config, test_topic_arn)
    subscription_arn = subscribe(config, test_topic_arn)
    list_subscriptions_by_topic(config, test_topic_arn)
    publish_message(config, test_topic_arn)
    publish_plain_message(config, test_topic_arn)
    unsubscribe(config, subscription_arn)
    delete_test_topic(config, test_topic_arn)
    verify_deletion(config)

    IO.puts("\n=== SNS Client Application Complete ===")
    :ok
  end

  defp client_config do
    %{
      region: "us-east-1",
      endpoint_prefix: "sns",
      signing_name: "sns",
      base_url: System.get_env("AWS_ENDPOINT"),
      credentials: %{
        access_key_id: "dummy",
        secret_access_key: "dummy"
      }
    }
  end

  defp setup_infrastructure(config) do
    IO.puts("--- Setup infrastructure ---")
    create_demo_topic(config)
    IO.puts("Infrastructure ready: Topic=#{@topic_name}\n")
  end

  defp create_demo_topic(config) do
    IO.puts("--- CreateTopic (demo topic) ---")

    input = %CreateTopicInput{
      name: @topic_name,
      tags: [
        %Tag{key: "Name", value: @topic_name},
        %Tag{key: "Environment", value: "demo"},
        %Tag{key: "Project", value: "smithy-elixir"}
      ]
    }

    case SnsClient.create_topic(config, input) do
      {:ok, %CreateTopicOutput{topic_arn: arn}} when is_binary(arn) and arn != "" ->
        IO.puts("SUCCESS: Topic '#{@topic_name}' created")
        IO.puts("SUCCESS: Topic ARN: #{arn}\n")

      {:ok, _} ->
        raise("assertion failed: CreateTopic returned empty ARN")

      {:error, reason} ->
        raise("create_topic_failed: #{inspect(reason)}")
    end
  end

  defp list_topics(config) do
    IO.puts("--- ListTopics ---")

    case SnsClient.list_topics(config, %ListTopicsInput{}) do
      {:ok, topics} when is_list(topics) and topics != [] ->
        IO.puts("SUCCESS: Found #{length(topics)} topic(s)")
        Enum.each(topics, fn %Topic{topic_arn: arn} -> IO.puts("  - #{format_string(arn)}") end)

      {:ok, _} ->
        raise("assertion failed: expected non-empty topic list")

      {:error, reason} ->
        raise("list_topics_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp create_test_topic(config) do
    IO.puts("--- CreateTopic ---")

    input = %CreateTopicInput{
      name: @test_topic_name,
      tags: [
        %Tag{key: "Environment", value: "demo"},
        %Tag{key: "CreatedBy", value: "smithy-elixir"}
      ]
    }

    case SnsClient.create_topic(config, input) do
      {:ok, %CreateTopicOutput{topic_arn: arn}} when is_binary(arn) and arn != "" ->
        unless String.contains?(arn, @test_topic_name) do
          raise("assertion failed: ARN missing topic name #{inspect(arn)}")
        end

        IO.puts("SUCCESS: ARN = #{arn}\n")
        arn

      {:ok, _} ->
        raise("assertion failed: CreateTopic returned empty ARN")

      {:error, reason} ->
        raise("create_topic_failed: #{inspect(reason)}")
    end
  end

  defp get_topic_attributes(config, topic_arn) do
    IO.puts("--- GetTopicAttributes ---")

    input = %GetTopicAttributesInput{topic_arn: topic_arn}

    case SnsClient.get_topic_attributes(config, input) do
      {:ok, %GetTopicAttributesOutput{attributes: attrs}} ->
        IO.puts("SUCCESS: Topic attributes:")
        attrs = attrs || %{}

        if map_size(attrs) == 0 do
          IO.puts("    (no attributes decoded)")
        else
          Enum.each(attrs, fn {key, value} -> IO.puts("    #{key}: #{value}") end)
        end

      {:error, reason} ->
        raise("get_topic_attributes_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp list_tags_for_resource(config, topic_arn) do
    IO.puts("--- ListTagsForResource ---")

    input = %ListTagsForResourceInput{resource_arn: topic_arn}

    case SnsClient.list_tags_for_resource(config, input) do
      {:ok, %ListTagsForResourceOutput{tags: tags}} ->
        tag_list = tags || []
        IO.puts("SUCCESS: Found #{length(tag_list)} tag(s):")

        Enum.each(tag_list, fn %Tag{key: key, value: value} ->
          IO.puts("    #{format_string(key)} = #{format_string(value)}")
        end)

      {:error, reason} ->
        raise("list_tags_for_resource_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp subscribe(config, topic_arn) do
    IO.puts("--- Subscribe ---")

    input = %SubscribeInput{
      topic_arn: topic_arn,
      protocol: "email-json",
      endpoint: "test@example.com"
    }

    case SnsClient.subscribe(config, input) do
      {:ok, %SubscribeOutput{subscription_arn: sub_arn}} ->
        if subscription_arn?(sub_arn) do
          IO.puts("SUCCESS: Subscription ARN: #{sub_arn}")
        else
          IO.puts("SUCCESS: Subscription pending confirmation")
        end

        IO.puts("")
        sub_arn

      {:error, reason} ->
        raise("subscribe_failed: #{inspect(reason)}")
    end
  end

  defp list_subscriptions_by_topic(config, topic_arn) do
    IO.puts("--- ListSubscriptionsByTopic ---")

    input = %ListSubscriptionsByTopicInput{topic_arn: topic_arn}

    case SnsClient.list_subscriptions_by_topic(config, input) do
      {:ok, subs} when is_list(subs) and subs != [] ->
        IO.puts("SUCCESS: Found #{length(subs)} subscription(s):")

        Enum.each(subs, fn %Subscription{
                             subscription_arn: sub_arn,
                             protocol: protocol,
                             endpoint: endpoint
                           } ->
          IO.puts("    Protocol: #{format_string(protocol)}, Endpoint: #{format_string(endpoint)}")
          IO.puts("    ARN: #{format_string(sub_arn)}")
        end)

      {:ok, _} ->
        raise("assertion failed: expected non-empty subscription list")

      {:error, reason} ->
        raise("list_subscriptions_by_topic_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp publish_message(config, topic_arn) do
    IO.puts("--- Publish ---")

    message_body =
      Jason.encode!(%{
        "message" => "Hello from Elixir!",
        "timestamp" => DateTime.utc_now() |> DateTime.to_iso8601(),
        "source" => "smithy-elixir-sns-demo"
      })

    input = %PublishInput{
      topic_arn: topic_arn,
      message: message_body,
      subject: "Test message from Smithy-Elixir",
      message_attributes: %{
        "Author" => %MessageAttributeValue{
          data_type: "String",
          string_value: "Smithy-Elixir Demo"
        }
      }
    }

    case SnsClient.publish(config, input) do
      {:ok, %PublishOutput{message_id: message_id}} when is_binary(message_id) and message_id != "" ->
        IO.puts("SUCCESS: Published message, ID: #{message_id}")

      {:ok, _} ->
        raise("assertion failed: Publish returned empty MessageId")

      {:error, reason} ->
        raise("publish_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp publish_plain_message(config, topic_arn) do
    IO.puts("--- Publish (second message) ---")

    input = %PublishInput{
      topic_arn: topic_arn,
      message: "This is a plain text message from the Elixir SNS demo."
    }

    case SnsClient.publish(config, input) do
      {:ok, %PublishOutput{message_id: message_id}} when is_binary(message_id) and message_id != "" ->
        IO.puts("SUCCESS: Published message, ID: #{message_id}")

      {:ok, _} ->
        raise("assertion failed: Publish returned empty MessageId")

      {:error, reason} ->
        raise("publish_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp unsubscribe(config, subscription_arn) do
    if subscription_arn?(subscription_arn) do
      IO.puts("--- Unsubscribe ---")

      input = %UnsubscribeInput{subscription_arn: subscription_arn}

      case SnsClient.unsubscribe(config, input) do
        {:ok, _} ->
          IO.puts("SUCCESS: Unsubscribed")

        {:error, reason} ->
          raise("unsubscribe_failed: #{inspect(reason)}")
      end

      IO.puts("")
    else
      IO.puts("--- Unsubscribe (skipped - pending confirmation) ---\n")
    end
  end

  defp delete_test_topic(config, topic_arn) do
    IO.puts("--- DeleteTopic ---")

    input = %DeleteTopicInput{topic_arn: topic_arn}

    case SnsClient.delete_topic(config, input) do
      {:ok, _} ->
        IO.puts("SUCCESS: Topic deleted")

      {:error, reason} ->
        raise("delete_topic_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp verify_deletion(config) do
    IO.puts("--- ListTopics (verify deletion) ---")

    case SnsClient.list_topics(config, %ListTopicsInput{}) do
      {:ok, topics} when is_list(topics) ->
        test_topic_exists =
          Enum.any?(topics, fn %Topic{topic_arn: arn} ->
            is_binary(arn) and String.contains?(arn, @test_topic_name)
          end)

        if test_topic_exists do
          raise("assertion failed: test topic still exists")
        end

        IO.puts("SUCCESS: Test topic no longer exists")
        IO.puts("Remaining topics: #{length(topics)}")

      {:error, reason} ->
        raise("list_topics_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp subscription_arn?(nil), do: false
  defp subscription_arn?(""), do: false

  defp subscription_arn?(arn) when is_binary(arn) do
    String.starts_with?(arn, "arn:")
  end

  defp subscription_arn?(_), do: false

  defp format_string(nil), do: "?"
  defp format_string(value) when is_binary(value), do: value
  defp format_string(value) when is_atom(value), do: Atom.to_string(value)
  defp format_string(value), do: inspect(value)
end
