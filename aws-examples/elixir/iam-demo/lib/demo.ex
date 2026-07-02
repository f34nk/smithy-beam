defmodule Demo do
  @moduledoc """
  IAM demo covering ListUsers, GetUser, ListGroups, ListGroupsForUser,
  CreateUser, AddUserToGroup, RemoveUserFromGroup, and DeleteUser via the
  generated IamClient.

  Uses the aws.protocols#awsQuery protocol (XML over HTTPS).
  Run against LocalStack: make demo
  """

  alias IamTypes.{
    AddUserToGroupInput,
    CreateGroupInput,
    CreateGroupOutput,
    CreateUserInput,
    CreateUserOutput,
    DeleteGroupInput,
    DeleteUserInput,
    EntityAlreadyExistsException,
    GetUserInput,
    GetUserOutput,
    Group,
    ListGroupsForUserInput,
    ListGroupsInput,
    ListUsersInput,
    NoSuchEntityException,
    RemoveUserFromGroupInput,
    Tag,
    User
  }

  @user_name "iam-demo-elixir-user"
  @group_name "iam-demo-elixir-group"
  @new_user_name "iam-demo-elixir-new-user"
  @demo_path "/demo/elixir/"

  def run do
    IO.puts("\n=== Running IAM Client Application ===\n")

    config = client_config()
    IO.puts("IAM client configured for #{Map.fetch!(config, :base_url)}\n")

    setup_infrastructure(config)
    count_before = list_users(config)
    get_user(config, @user_name)
    list_groups(config)
    list_groups_for_user(config, @user_name, "ListGroupsForUser")
    create_new_user(config)
    verify_user_count(config, count_before)
    add_user_to_group(config, @new_user_name, @group_name, "AddUserToGroup")
    list_groups_for_user(config, @new_user_name, "ListGroupsForUser (new user)", @group_name)
    remove_user_from_group(config, @new_user_name, @group_name, "RemoveUserFromGroup")
    delete_user(config, @new_user_name, "DeleteUser")
    verify_user_deletion(config, @new_user_name)
    delete_demo_infrastructure(config)

    IO.puts("=== IAM Client Application Complete ===")
    :ok
  end

  defp client_config do
    %{
      region: "us-east-1",
      endpoint_prefix: "iam",
      signing_name: "iam",
      base_url: System.get_env("AWS_ENDPOINT"),
      credentials: %{
        access_key_id: "dummy",
        secret_access_key: "dummy"
      }
    }
  end

  defp setup_infrastructure(config) do
    IO.puts("--- Setup infrastructure ---")
    create_demo_group(config)
    create_demo_user(config)
    add_user_to_group(config, @user_name, @group_name, "AddUserToGroup (demo user)")
    IO.puts("Infrastructure ready: User=#{@user_name} Group=#{@group_name}\n")
  end

  defp create_demo_group(config) do
    IO.puts("--- CreateGroup ---")

    input = %CreateGroupInput{
      group_name: @group_name,
      path: @demo_path
    }

    case IamClient.create_group(config, input) do
      {:ok, %CreateGroupOutput{group: %Group{group_name: group_name}}} ->
        IO.puts("SUCCESS: Created group '#{group_name}'\n")

      {:error, reason} ->
        if entity_already_exists?(reason) do
          IO.puts("SUCCESS: Group '#{@group_name}' already exists\n")
        else
          raise("create_group_failed: #{inspect(reason)}")
        end
    end
  end

  defp create_demo_user(config) do
    IO.puts("--- CreateUser (demo user) ---")

    input = %CreateUserInput{
      user_name: @user_name,
      path: @demo_path,
      tags: [
        %Tag{key: "Name", value: @user_name},
        %Tag{key: "Environment", value: "demo"}
      ]
    }

    case IamClient.create_user(config, input) do
      {:ok, %CreateUserOutput{user: %User{user_name: user_name, arn: arn}}} ->
        IO.puts("SUCCESS: Created user '#{user_name}'")
        IO.puts("  ARN: #{arn}\n")

      {:error, reason} ->
        if entity_already_exists?(reason) do
          IO.puts("SUCCESS: User '#{@user_name}' already exists\n")
        else
          raise("create_user_failed: #{inspect(reason)}")
        end
    end
  end

  defp list_users(config) do
    IO.puts("--- ListUsers ---")

    case IamClient.list_users(config, %ListUsersInput{}) do
      {:ok, users} when is_list(users) and users != [] ->
        IO.puts("SUCCESS: Found #{length(users)} user(s)")

        Enum.each(users, fn %User{user_name: user_name, path: path} ->
          IO.puts("  - #{user_name} (path: #{path})")
        end)

        IO.puts("")
        length(users)

      {:ok, _} ->
        raise("assertion failed: empty user list")

      {:error, reason} ->
        raise("list_users_failed: #{inspect(reason)}")
    end
  end

  defp get_user(config, user_name) do
    IO.puts("--- GetUser ---")

    input = %GetUserInput{user_name: user_name}

    case IamClient.get_user(config, input) do
      {:ok,
       %GetUserOutput{
         user: %User{user_name: returned_name, arn: arn, create_date: create_date}
       }}
      when returned_name == user_name ->
        IO.puts("SUCCESS: GetUser UserName matches '#{user_name}'")
        IO.puts("  ARN: #{arn}")
        IO.puts("  Created: #{create_date}")

      {:ok, %GetUserOutput{user: %User{user_name: returned_name}}} ->
        raise(
          "assertion failed: expected username #{inspect(user_name)}, got #{inspect(returned_name)}"
        )

      {:error, reason} ->
        raise("get_user_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp list_groups(config) do
    IO.puts("--- ListGroups ---")

    case IamClient.list_groups(config, %ListGroupsInput{}) do
      {:ok, groups} when is_list(groups) and groups != [] ->
        IO.puts("SUCCESS: Found #{length(groups)} group(s)")

        Enum.each(groups, fn %Group{group_name: group_name, path: path} ->
          IO.puts("  - #{group_name} (path: #{path})")
        end)

      {:ok, _} ->
        raise("assertion failed: empty group list")

      {:error, reason} ->
        raise("list_groups_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp list_groups_for_user(config, user_name, label, expected_group \\ nil) do
    IO.puts("--- #{label} ---")

    input = %ListGroupsForUserInput{user_name: user_name}

    case IamClient.list_groups_for_user(config, input) do
      {:ok, groups} when is_list(groups) ->
        if expected_group do
          if groups == [] do
            raise("assertion failed: new user not in any group: #{user_name}")
          end

          group_names = Enum.map(groups, fn %Group{group_name: name} -> name end)

          unless expected_group in group_names do
            raise(
              "assertion failed: group #{inspect(expected_group)} not found in #{inspect(group_names)}"
            )
          end

          IO.puts("SUCCESS: Group '#{expected_group}' found")
        end

        IO.puts("SUCCESS: User '#{user_name}' is in #{length(groups)} group(s)")

        Enum.each(groups, fn %Group{group_name: group_name} ->
          IO.puts("  - #{group_name}")
        end)

      {:error, reason} ->
        raise("list_groups_for_user_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp create_new_user(config) do
    IO.puts("--- CreateUser ---")

    new_user_name = @new_user_name

    input = %CreateUserInput{
      user_name: new_user_name,
      path: @demo_path,
      tags: [%Tag{key: "CreatedBy", value: "smithy-elixir"}]
    }

    case IamClient.create_user(config, input) do
      {:ok, %CreateUserOutput{user: %User{user_name: ^new_user_name, arn: arn}}} ->
        IO.puts("SUCCESS: Created user '#{new_user_name}'")
        IO.puts("  ARN: #{arn}")

      {:ok, %CreateUserOutput{user: %User{user_name: created_name}}} ->
        raise(
          "assertion failed: expected username #{inspect(new_user_name)}, got #{inspect(created_name)}"
        )

      {:error, reason} ->
        raise("create_user_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp verify_user_count(config, count_before) do
    IO.puts("--- ListUsers (verify) ---")

    case IamClient.list_users(config, %ListUsersInput{}) do
      {:ok, users} when is_list(users) ->
        count_after = length(users)
        IO.puts("SUCCESS: Found #{count_after} user(s)")

        if count_after <= count_before do
          raise("assertion failed: user count unchanged at #{count_before}")
        end

        IO.puts("SUCCESS: User count grew from #{count_before} to #{count_after}")

        unless Enum.any?(users, fn %User{user_name: name} -> name == @new_user_name end) do
          raise("assertion failed: user not in list: #{@new_user_name}")
        end

        IO.puts("SUCCESS: '#{@new_user_name}' found in user list")

        Enum.each(users, fn %User{user_name: user_name} ->
          IO.puts("  - #{user_name}")
        end)

      {:error, reason} ->
        raise("list_users_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp add_user_to_group(config, user_name, group_name, label) do
    IO.puts("--- #{label} ---")

    input = %AddUserToGroupInput{
      user_name: user_name,
      group_name: group_name
    }

    case IamClient.add_user_to_group(config, input) do
      {:ok, _} ->
        IO.puts("SUCCESS: Added '#{user_name}' to group '#{group_name}'")

      {:error, reason} ->
        raise("add_user_to_group_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp remove_user_from_group(config, user_name, group_name, label) do
    IO.puts("--- #{label} ---")

    input = %RemoveUserFromGroupInput{
      user_name: user_name,
      group_name: group_name
    }

    case IamClient.remove_user_from_group(config, input) do
      {:ok, _} ->
        IO.puts("SUCCESS: Removed '#{user_name}' from group '#{group_name}'")

      {:error, reason} ->
        raise("remove_user_from_group_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp delete_user(config, user_name, label) do
    IO.puts("--- #{label} ---")

    case IamClient.delete_user(config, %DeleteUserInput{user_name: user_name}) do
      {:ok, _} ->
        IO.puts("SUCCESS: Deleted user '#{user_name}'")

      {:error, reason} ->
        raise("delete_user_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp verify_user_deletion(config, user_name) do
    IO.puts("--- GetUser (verify deletion) ---")

    case IamClient.get_user(config, %GetUserInput{user_name: user_name}) do
      {:ok, _} ->
        raise("assertion failed: user not deleted: #{user_name}")

      {:error, reason} ->
        cond do
          match?(%NoSuchEntityException{}, reason) ->
            IO.puts("SUCCESS: User '#{user_name}' confirmed deleted")

          no_such_entity?(reason) ->
            IO.puts("SUCCESS: User '#{user_name}' confirmed deleted")

          true ->
            raise("get_user_failed: #{inspect(reason)}")
        end
    end

    IO.puts("")
  end

  defp delete_demo_infrastructure(config) do
    remove_user_from_group(config, @user_name, @group_name, "RemoveUserFromGroup (demo user)")
    delete_user(config, @user_name, "DeleteUser (demo user)")

    IO.puts("--- DeleteGroup ---")

    case IamClient.delete_group(config, %DeleteGroupInput{group_name: @group_name}) do
      {:ok, _} ->
        IO.puts("SUCCESS: Deleted group '#{@group_name}'\n")

      {:error, reason} ->
        raise("delete_group_failed: #{inspect(reason)}")
    end
  end

  defp entity_already_exists?(%EntityAlreadyExistsException{}), do: true
  defp entity_already_exists?("EntityAlreadyExists"), do: true
  defp entity_already_exists?({code, _message}) when code in ["EntityAlreadyExists", :EntityAlreadyExists],
    do: true

  defp entity_already_exists?({:unknown_error, _status, body}) when is_binary(body),
    do: String.contains?(body, "EntityAlreadyExists")

  defp entity_already_exists?(_), do: false

  defp no_such_entity?(%NoSuchEntityException{}), do: true
  defp no_such_entity?("NoSuchEntity"), do: true
  defp no_such_entity?({code, _message}) when code in ["NoSuchEntity", :NoSuchEntity], do: true

  defp no_such_entity?({:unknown_error, _status, body}) when is_binary(body),
    do: String.contains?(body, "NoSuchEntity")

  defp no_such_entity?(_), do: false
end
