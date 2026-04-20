-module(weather_client).
-export([get_current_time/2, get_forecast/2, errors/0, is_error/1, error_to_atom/1]).

-spec get_current_time(Client :: map(), Input :: get_current_time_input()) ->
    {ok, get_current_time_output()} | {error, term()}.
get_current_time(Config, Input) ->
    {error, not_implemented}.

-spec get_forecast(Client :: map(), Input :: get_forecast_input()) ->
    {ok, get_forecast_output()} | {error, no_such_resource_error()}.
get_forecast(Config, Input) ->
    {error, not_implemented}.


errors() ->
    [no_such_resource_error].

is_error({error, Err}) when is_tuple(Err), is_atom(element(1, Err)) ->
    lists:member(element(1, Err), errors());
is_error(_) ->
    false.

error_to_atom({error, Err}) when is_tuple(Err), is_atom(element(1, Err)) ->
    case lists:member(element(1, Err), errors()) of
        true -> element(1, Err);
        false -> unknown_error
    end;
error_to_atom(_) ->
    unknown_error.
