$version: "2"
namespace example.weather

use aws.protocols#restJson1

@restJson1
service WeatherService {
    version: "1"
    operations: [GetWeather]
}

@readonly
@http(method: "GET", uri: "/weather/{city}", code: 200)
operation GetWeather {
    input: GetWeatherInput
    output: GetWeatherOutput
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
}
