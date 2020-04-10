package query

import io.circe.parser
import io.circe.generic.auto._

import scala.io.Source

object QueryReader {

  case class QueryObject(index: String, requested: Int, processingModel: String,
                         relevanceModel: Option[String], fbDocs: Option[Int], fbTerm: Option[Int],
                         fbOrigWeight: Option[Double], scorer: Option[String], queries: List[SingleQuery])

  case class SingleQuery(number: String, text: String)

  final case class ParseQueriesError(private val message: String = "",
                                     private val cause: Throwable = None.orNull)
    extends Exception(message, cause)

  /**
   * Reads json with queries and create custom class with all the partameters and queries
   * @param queryFile path to file with queries
   * @return QueryObject
   */
  def readQueries(queryFile: String): QueryObject = {

    val srcFile = Source.fromFile(queryFile)
    val jsonTxt: String = srcFile.mkString.stripMargin
    srcFile.close()

    var decodingResult: QueryObject = null
    parser.decode[QueryObject](jsonTxt) match {
      case Right(json) => decodingResult = json
      case Left(_) =>
        throw ParseQueriesError("Error during reading the queries from json file.")

    }
    decodingResult
  }

  def main(args: Array[String]): Unit = {
    val queryJson: String = "queries.json"
    val query: QueryObject = readQueries(queryJson)
    print(query)
  }

}
