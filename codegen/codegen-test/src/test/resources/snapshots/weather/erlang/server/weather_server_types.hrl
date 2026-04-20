-module(weather_server_types).

-record(get_current_time_input, {}).
-type get_current_time_input() :: #get_current_time_input{}.


-record(get_current_time_output, {
    time :: integer()
}).
-type get_current_time_output() :: #get_current_time_output{}.


-record(get_forecast_input, {
    city_id :: binary()
}).
-type get_forecast_input() :: #get_forecast_input{}.


-record(get_forecast_output, {
    chance_of_rain :: float()
}).
-type get_forecast_output() :: #get_forecast_output{}.


-record(no_such_resource_error, {
    resource_type :: binary()
}).
-type no_such_resource_error() :: #no_such_resource_error{}.
