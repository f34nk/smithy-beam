%% @doc Error dispatch for smithy.beam.test.awsjson11#GetUser.
decode_get_user_response_error(Status, _Hdrs, Body) -> {error, {unknown_error, Status, Body}}.
