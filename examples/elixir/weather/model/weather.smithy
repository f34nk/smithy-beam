$version: "2"
namespace example.weather

/// A simple weather forecasting service.
service Weather {
    version: "2006-03-01"
    operations: [GetCurrentTime, GetForecast]
}

/// Returns the current server time.
operation GetCurrentTime {
    input: GetCurrentTimeInput
    output: GetCurrentTimeOutput
}

/// Returns a weather forecast for the given city.
operation GetForecast {
    input: GetForecastInput
    output: GetForecastOutput
    errors: [NoSuchResourceError]
}

structure GetCurrentTimeInput {}

structure GetCurrentTimeOutput {
    time: Timestamp
}

structure GetForecastInput {
    cityId: String
}

structure GetForecastOutput {
    chanceOfRain: Float
}

@error("client")
structure NoSuchResourceError {
    resourceType: String
}
