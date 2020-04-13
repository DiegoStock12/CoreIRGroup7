package query

import io.circe.parser
import io.circe.generic.auto._

import scala.io.Source

object DataReader {

  case class QueryObject(index: String, requested: Option[Int], processingModel: Option[String],
                         relevanceModel: Option[String], fbDocs: Option[Int], fbTerm: Option[Int],
                         fbOrigWeight: Option[Double], scorer: Option[String], queries: List[SingleQuery])

  case class SingleQuery(number: String, text: String)

  final case class ParseQueriesError(private val message: String = "",
                                     private val cause: Throwable = None.orNull)
    extends Exception(message, cause)

  /**
   * Reads json with queries and create custom class with all the partameters and queries
   *
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

  /**
   * Reads the file with explicit feedback for which document are relevant for which query.
   * Necessary for Rocchio query expansion.
   *
   * @param groundTruthPath Path to the file with explicit feedback.
   * @return Mapping of the queries to their relevant documents in the corpus.
   */
  def readQueryToRelevantDocuments(groundTruthPath: String): Map[String, Set[String]] = {
    val bufferedSource = Source.fromFile(groundTruthPath)
    var queryToRelevantDocs: Map[String, Set[String]] = Map()

    for (line <- bufferedSource.getLines) {
      val splitted: Array[String] = line.split(" ")
      val query: String = splitted(0)
      val docId: String = splitted(2)

      val querySet: Option[Set[String]] = queryToRelevantDocs.get(query)
      if (querySet.isDefined) {
        val newQuerySet: Set[String] = querySet.get + docId
        queryToRelevantDocs += (query -> newQuerySet)
      }
      else {
        queryToRelevantDocs += (query -> Set(docId))
      }
    }
    bufferedSource.close()
    queryToRelevantDocs
  }

  def main(args: Array[String]): Unit = {

  }

}
