-module(user_service_server_probe).

-include("user_service_types.hrl").

-export([handle_get_user/3]).

%% Test double: partial handler module for router tests (not a behaviour implementor).
handle_get_user(_Ctx, #get_user_input{user_id = UserId}, _Meta) ->
    {ok, #get_user_output{
        user = #{<<"userId">> => UserId}
    }}.
