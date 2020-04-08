package finalSimulation

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import io.gatling.core.Predef._
import io.gatling.http.Predef._


import scala.concurrent.duration._
import scala.util.Random

class VideoGameFullTestTemplate extends Simulation {

  val httpConf = http.baseUrl("http://localhost:8080/app/")
    .header("Accept", "application/json")

  /** Variables */
  // runtime variables
  def userCount: Int = getProperty("USERS", "3").toInt

  def rampDuration: Int = getProperty("RAMP_DURATION", "10").toInt

  def testDuration: Int = getProperty("DURATION", "60").toInt

  // other variables
  var idNumbers = (20 to 1000).iterator
  val rnd = new Random()
  val now = LocalDate.now()
  val pattern = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  /** Helper methods  */

  private def getProperty(propertyName: String, defaultValue: String) = {
    Option(System.getenv(propertyName))
      .orElse(Option(System.getProperty(propertyName)))
      .getOrElse(defaultValue)
  }

  def randomString(length: Int) = {
    rnd.alphanumeric.filter(_.isLetter).take(length).mkString
  }

  def getRandomDate(startDate: LocalDate, random: Random): String = {
    startDate.minusDays(random.nextInt(30)).format(pattern)
  }

  /** Custom Feeder */

  val customFeeder = Iterator.continually(Map(
    "gameId" -> idNumbers.next(),
    "name" -> ("Game-" + randomString(5)),
    "releaseDate" -> getRandomDate(now, rnd),
    "reviewScore" -> rnd.nextInt(100),
    "category" -> ("Category-" + randomString(6)),
    "rating" -> ("Rating-" + randomString(4))
  ))

  /** Before */

  // to print out message at the start and end of the test
  before {
    println(s"Running tests with ${userCount} users")
    println(s"Ramping users over ${rampDuration} seconds")
    println(s"Total test duration ${testDuration} seconds")
  }

  /** * HTTP CALLS ***/
  def getAllVideoGames() = {
    exec(
      http("Get all video games")
        .get("videogames")
        .check(status.is(200))
    )
  }

  def postNewGame() = {
    feed(customFeeder).
      exec(http("Post New Game")
        .post("videogames")
        .body(ElFileBody("bodies/NewGameTemplate.json")).asJson // template file goes in gatling/resources/bodies
        .check(status.is(200))
      )
  }

  def getLastPostedGame() = {
    exec(
      http("Get Last Posted Game")
        .get("videogames/${gameId}")
        .check(jsonPath("$.name").is("${name}"))
        .check(status.is(200))
    )
  }

  def deleteLastPostedGame() = {
    exec(
      http("Delete Last Posted Game")
        .delete("videogames/${gameId}")
        .check(status.is(200))
    )
  }

  /** * SCENARIO DESIGN */

  val scn = scenario("Video Game DB")
    .forever() {
      exec(getAllVideoGames())
        .pause(2)
        .exec(postNewGame())
        .pause(2)
        .exec(getLastPostedGame())
        .pause(2)
        .exec(deleteLastPostedGame())
    }

  // using http call, create a scenario that does the following
  // 1. Get all games
  // 2. Create new Game
  // 3. Get details of that single
  // 4. Delete the game

  /** SETUP LOAD SIMULATION */

  setUp(
    scn.inject(
      nothingFor(5 seconds),
      rampUsers(userCount) during (rampDuration seconds))
  )
    .protocols(httpConf)
    .maxDuration(testDuration seconds)

  // create a scenario that has runtime parameters for:
  // 1. Users
  // 2. Ramp up time
  // 3. Test duration

  /** After */

  after {
    println("Stress test completed")
  }
}
