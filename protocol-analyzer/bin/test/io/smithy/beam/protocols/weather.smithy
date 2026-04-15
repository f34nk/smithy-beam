$version: "2"
namespace example.weather

use aws.protocols#restJson1
use aws.auth#sigv4

@restJson1
@sigv4(name: "weather")
service WeatherService {
    version: "1"
    operations: [GetWeather, ListCities]
}

@readonly
@http(method: "GET", uri: "/cities", code: 200)
@paginated(inputToken: "nextToken", outputToken: "nextToken", items: "cities", pageSize: "maxResults")
operation ListCities {
    input: ListCitiesInput
    output: ListCitiesOutput
}

@input
structure ListCitiesInput {
    @httpQuery("filter")
    filter: String

    @httpHeader("X-Custom-Header")
    customHeader: String

    @httpQuery("nextToken")
    nextToken: String

    @httpQuery("maxResults")
    maxResults: Integer
}

@output
structure ListCitiesOutput {
    nextToken: String
    cities: CityList
}

list CityList {
    member: String
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
