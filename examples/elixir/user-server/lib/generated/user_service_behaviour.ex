defmodule UserServiceBehaviour do
@moduledoc """
    Generated Elixir server behaviour for smithy.beam.demo.user#UserService.
"""
alias UserServiceTypes

@callback handle_create_user(
    term(),
    UserServiceTypes.CreateUserInput.t(),
    term()
) ::
    {:ok, UserServiceTypes.CreateUserOutput.t()} | {:error, term()}

@callback handle_delete_user(
    term(),
    UserServiceTypes.DeleteUserInput.t(),
    term()
) ::
    {:ok, UserServiceTypes.DeleteUserOutput.t()} | {:error, term()}

@callback handle_get_user(
    term(),
    UserServiceTypes.GetUserInput.t(),
    term()
) ::
    {:ok, UserServiceTypes.GetUserOutput.t()} | {:error, term()}

@callback handle_list_users(
    term(),
    UserServiceTypes.ListUsersInput.t(),
    term()
) ::
    {:ok, UserServiceTypes.ListUsersOutput.t()} | {:error, term()}

@callback handle_update_user(
    term(),
    UserServiceTypes.UpdateUserInput.t(),
    term()
) ::
    {:ok, UserServiceTypes.UpdateUserOutput.t()} | {:error, term()}

@spec callbacks() :: [{atom(), non_neg_integer()}]
def callbacks do
    [
        {:handle_create_user, 3},
        {:handle_delete_user, 3},
        {:handle_get_user, 3},
        {:handle_list_users, 3},
        {:handle_update_user, 3},
    ]
end

end
