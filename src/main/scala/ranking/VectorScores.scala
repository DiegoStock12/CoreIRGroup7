package ranking

import java.io.PrintWriter

import org.lemurproject.galago.core.parse.stem.KrovetzStemmer
import org.lemurproject.galago.core.retrieval.query.{Node, StructuredQuery}
import org.lemurproject.galago.core.retrieval.{
  Retrieval,
  RetrievalFactory,
  ScoredDocument
}
import org.lemurproject.galago.utility.Parameters
import query.DataReader
import query.DataReader.{QueryObject, SingleQuery}
import vector.TFIDF.query2tfidf
import vector.Utils.{INDEX_PATH, INDEX_PATH_TRAIN, calculateDocumentVectors, cosineSimilarity}
import vector.WordEmbeddings.query2Vec
import vector.{Idf, VectorType, W2V}
import query_expansion.Rocchio.processExpandedQuery

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
  val OUTPUT_FILE = "test.run"

  val writer: PrintWriter = new PrintWriter(OUTPUT_FILE)
  val stemmer: KrovetzStemmer = new KrovetzStemmer()
  val retrieval: Retrieval = RetrievalFactory.instance(INDEX_PATH)

  // Type of vector to be used
  val VECTOR_TYPE: VectorType = Idf

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
    calculateDocumentVectors(VECTOR_TYPE)

  /**
    * Computes the top 10 results for a query
    * @param query
    */
  private def processQuery(query: SingleQuery): Unit = {

    // Stem the query
    val stemmedQuery =
      query.text.split(" +").map(stemmer.stem).mkString(" ")

    // Transform the query for the retrieval
    val root: Node = StructuredQuery.parse(stemmedQuery)
    val transformed: Node = retrieval.transformQuery(root, params)

    // Get the results
    val ranking: List[ScoredDocument] =
      retrieval.executeQuery(transformed, params).scoredDocuments.asScala.toList

    // Compare the query to every result and get the top K
    val topResults = getTopK(stemmedQuery, ranking)

    saveResults(query, topResults)
  }

  /**
    * Processes query with Relevance Feedback
    * @param query
    */
  private def processQueryWithFeedback(query: SingleQuery): Unit = {
    // Stem the query
    val stemmedQuery =
      query.text.split(" ").map(x => stemmer.stem(x)).mkString(" ")

    // Add the rm text so it is run with relevance feedback
    val rmQuery: String = s"#rm($stemmedQuery)"

    // Transform the query for the retrieval
    var root: Node = StructuredQuery.parse(rmQuery)
    var transformed: Node = retrieval.transformQuery(root, params)

    // First retrieval to get the extra terms
    retrieval.executeQuery(transformed, params)

    // Get those extra terms
    val terms =
      transformed.getInternalNodes.asScala
        .map(_.getChild(1).getDefaultParameter)

    // Get the second retrieval by using the terms returned
    val secondQuery = terms.mkString(" ")

    root = StructuredQuery.parse(s"#rm($secondQuery)")
    transformed = retrieval.transformQuery(root, params)

    // Get the ranking after using the Feedback
    val ranking: List[ScoredDocument] =
      retrieval.executeQuery(transformed, params).scoredDocuments.asScala.toList

    saveResults(query, getTopK(secondQuery, ranking))
  }

  /**
    * Save results to file
    * @param query
    * @param topK
    */
  def saveResults(query: SingleQuery,
                          topK: List[(String, Double)]): Unit = {

    // Save the results in the appropriate format
    for ((result, index) ← topK.zipWithIndex) {
      val queryId: String = query.number
      val docId: String = result._1
      val score: Double = result._2
      val rank = index + 1

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
    val qVec: Array[Double] = VECTOR_TYPE match {
      case Idf =>
        query2tfidf(query)
      case W2V =>
        query2Vec(query)
    }

    // Calculate the cosine similarity of all
    var scores: Map[String, Double] = Map()
    ranking.foreach { doc =>
      // Get the doc Vector from the preloaded map
      val sim: Double = Try(cosineSimilarity(qVec, docMap(doc.documentName)))
        .recoverWith {
          case e: Exception =>
            e.printStackTrace()
            Failure(e)
        }
        .getOrElse(0.0)

      if (sim > 0)
        scores += (doc.documentName → sim)
    }

    // Return the top K
    scores.toSeq.sortWith(_._2 > _._2).take(K).toList
  }

  /**
    * Read the queries and search for results and then
    * save all of them to a specified file
   *
   * Depending on args(0) the program can do several things
   * - no extra args -> run the queries as they are
   * - args(0) = rm -> Use RM3 feedback
   * - args(0) = rocchio -> Use rocchio feedback
    * @param args
    */
  def main(args: Array[String]): Unit = {

    val option: String = Try(args(0)).getOrElse("default")

    // Load the queries
    val queryObject: QueryObject = DataReader.readQueries(QUERIES_PATH)

    // Check if we have to include feedback
    option match {
      case "rm" =>
        println("Including Relevance Model feedback")
        params.set("fbDocs", 10)
        params.set("fbTerm", 5)
        params.set("fbOrigWeight", 0.75)
        // Process the queries with feedback
        queryObject.queries.foreach { query =>
          processQueryWithFeedback(query)
        }

      case "rocchio" =>
        println("Including Rocchio feedback")
        processExpandedQuery(queryObject.queries, params)

      case "default" =>
        println("Running without feedback")
        // Process the queries normally
        queryObject.queries.foreach { query =>
          processQuery(query)
        }

      case _ =>
        println("Valid options are 'rm | rocchio'")
        System.exit(-1)

    }

    retrieval.close()
    writer.close()
  }
}
