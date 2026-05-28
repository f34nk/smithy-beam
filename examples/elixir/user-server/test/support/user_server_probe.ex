defmodule UserServerProbe do
  @moduledoc false

  @spec handle_get_user(term(), User.GetUserInput.t(), term()) ::
          {:ok, User.GetUserOutput.t()} | {:error, term()}
  def handle_get_user(_ctx, input, _meta) do
    {:ok, %User.GetUserOutput{user: %{"userId" => input.user_id}}}
  end
end
