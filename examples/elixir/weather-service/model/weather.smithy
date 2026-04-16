$version: "2.0"
namespace example.weather

use aws.protocols#restJson1

/// A simple weather service demonstrating restJson1 client generation.
///
/// Covers: GET with path labels, enums, POST with JSON body, and error shapes.
@restJson1
service WeatherService {
    version: "1.0"
    operations: [
        GetWeather
        CreateWeatherReport
    ]
}

/// Get the current weather for a city.
@readonly
@http(method: "GET", uri: "/weather/{city}", code: 200)
operation GetWeather {
    input: GetWeatherInput
    output: GetWeatherOutput
}

/// Create a new weather report for a city.
@http(method: "POST", uri: "/weather", code: 201)
operation CreateWeatherReport {
    input: CreateWeatherReportInput
    output: CreateWeatherReportOutput
    errors: [WeatherServiceError]
}

@input
structure GetWeatherInput {
    @required
    @httpLabel
    city: String
}

@output
structure GetWeatherOutput {
    temperature: Float
    unit: TemperatureUnit
}

@input
structure CreateWeatherReportInput {
    @required
    city: String

    @required
    temperature: Float

    @required
    unit: TemperatureUnit
}

@output
structure CreateWeatherReportOutput {
    reportId: String
}

/// Temperature unit enum.
enum TemperatureUnit {
    CELSIUS    = "Celsius"
    FAHRENHEIT = "Fahrenheit"
}

/// Returned when the weather service encounters an error.
@error("client")
@httpError(400)
structure WeatherServiceError {
    @required
    message: String

    code: String
}
