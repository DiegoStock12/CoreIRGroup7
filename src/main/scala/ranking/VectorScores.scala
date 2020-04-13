package ranking

import java.io.PrintWriter

import org.lemurproject.galago.core.parse.stem.KrovetzStemmer
import org.lemurproject.galago.core.retrieval.query.{Node, StructuredQuery}
import org.lemurproject.galago.core.retrieval.{Retrieval, RetrievalFactory, ScoredDocument}
import org.lemurproject.galago.utility.Parameters
import query.DataReader
import query.DataReader.{QueryObject, SingleQuery}
import vector.TFIDF.query2tfidf
import vector.Utils.{INDEX_PATH, calculateDocumentVectors, cosineSimilarity}
import vector.WordEmbeddings.query2Vec
import vector.{Idf, VectorType, W2V}

import scala.collection.JavaConverters._
import scala.collection.immutable
import scala.util.{Failure, Try}

/**
  * Retrieval of the vector scores of all the queries
 * In case we want to run with Word2Vec instead of TFIDF all that's needed
 * is to change the VECTOR_TYPE parameter
  */
object VectorScores {

  // Global variables for search and stemming
  val QUERIES_PATH = "queries.json"
  val OUTPUT_FILE = "results.run"

  val writer: PrintWriter = new PrintWriter(OUTPUT_FILE)
  val stemmer: KrovetzStemmer = new KrovetzStemmer()
  val retrieval: Retrieval = RetrievalFactory.instance(INDEX_PATH)

  // Type of vector to be used
  val VECTOR_TYPE : VectorType = Idf

  // Create the parameters for the search
  val params: Parameters = {
    val _p = Parameters.create()
    _p.set("startAt", 0)
    _p.set("resultCount", 100)
    _p.set("requested", 100)
    _p.set("mu", 2000)
    _p
  }

  // Hashmap that caches the vector representation of each document
  val docMap: immutable.HashMap[String, Array[Double]] =
    calculateDocumentVectors(Idf)

  /**
    * Computes the top 10 results for a query
    * @param query
    */
  def processQuery(query: SingleQuery): Unit = {

    // Stem the query
    val stemmedQuery =
      query.text.split(" ").map(x => stemmer.stem(x)).mkString(" ")

    // Transform the query for the retrieval
    val root: Node = StructuredQuery.parse(stemmedQuery)
    val transformed: Node = retrieval.transformQuery(root, params)

    // Get the results
    val ranking: List[ScoredDocument] =
      retrieval.executeQuery(transformed, params).scoredDocuments.asScala.toList

    // Compare the query to every result and get the top K
    val topResults = getTopK(stemmedQuery, ranking)

    saveResults(query,topResults)
  }


  /**
  * Save results to file
   * @param query
   * @param topK
   */
  private def saveResults(query: SingleQuery, topK: List[(String, Double)]) = {

    // Save the results in the appropriate format
    for ((result, index) ← topK.zipWithIndex) {
      val queryId : String = query.number
      val docId : String = result._1
      val score : Double = result._2
      val rank = index+1

      writer.println(s"$queryId Q0 $docId $rank $score TEST")
    }
  }

  /**
  * Returns the top K documents
   * @param query
   * @param K
   * @param ranking
   * @return
   */
  private def getTopK(query: String,
                      ranking: List[ScoredDocument],
                      K: Int = 10): List[(String, Double)] = {

    // Transform the query to the appropriate format
    val qVec : Array[Double] = VECTOR_TYPE match{
      case Idf =>
        query2tfidf(query)
      case W2V =>
        query2Vec(query)
    }

    // Calculate the cosine similarity of all
    var scores : Map[String, Double] = Map()
    ranking.foreach { doc =>
      // Get the doc Vector from the preloaded map
      val sim : Double = Try(cosineSimilarity(qVec, docMap(doc.documentName)))
        .recoverWith {
          case e: Exception => e.printStackTrace()
          Failure(e)
        }.getOrElse(0.0)

      if (sim > 0)
        scores += (doc.documentName → sim)
    }

    // Return the top K
    scores.toSeq.sortWith(_._2 > _._2).take(K).toList
  }

  /**
  * Read the queries and search for results and then
   * save all of them to a specified file
   * @param args
   */
  def main(args: Array[String]): Unit = {

    // Load the queries
    val queryObject: QueryObject = DataReader.readQueries(QUERIES_PATH)

    queryObject.queries.foreach { query =>
      processQuery(query)
    }
    retrieval.close()
    writer.close()
  }
}
