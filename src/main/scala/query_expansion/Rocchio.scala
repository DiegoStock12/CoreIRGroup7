package query_expansion

import vector.TFIDF._
import org.lemurproject.galago.core.parse.Document
import org.lemurproject.galago.core.retrieval.query.{Node, StructuredQuery}
import org.lemurproject.galago.core.retrieval.{Results, Retrieval, RetrievalFactory, ScoredDocument}
import org.lemurproject.galago.utility.Parameters
import query.DataReader.{QueryObject, SingleQuery, readQueries, readQueryToRelevantDocuments}
import vector.Utils.cosineSimilarity

import collection.JavaConverters._

object Rocchio {

  val QUERIES_PATH: String = "queries.json"
  val QREL_PATH: String = "/home/tomek/TUDelft/Courses/Information Retrieval/test200_2.0/train.pages.cbor-hierarchical.qrels"

  val alpha: Double = 0.9
  val beta: Double = 0.9

  /**
   * Performs search with relevance feedback using Rocchio algorithm for query expansion.
   */
  def processExpandedQuery(): Unit = {
    val queryToRelevantDocs: Map[String, Set[String]] = readQueryToRelevantDocuments(QREL_PATH)
    val (queries, queryParams) = prepareQueries()

    queries.foreach { query =>
      val id: String = query.number
      val text: String = query.text
      val results: Results = runQuery(text, queryParams)
      val docsToVector: Map[Document, Array[Double]] = results2Vector(results)

      val (relevantDocs, irrelevantDocs) = indetifyDocs(docsToVector, queryToRelevantDocs.getOrElse(id, Set()))
      val expandedQuery: Array[Double] = expandQuery(text, relevantDocs, irrelevantDocs)

      var docToExpandedScore: Map[Document, Double] = Map()

      docsToVector.foreach { case (doc, docVector) => {
        val similarityScore: Double = cosineSimilarity(expandedQuery, docVector)
        docToExpandedScore += (doc -> similarityScore)
      }
      }
      val top10 = docToExpandedScore.toSeq.sortWith(_._2 > _._2).take(10)
    }
  }


  /**
   * Reads json with queries and prepare queries and all the search parameters.
   *
   * @return Queries and parameters.
   */
  def prepareQueries(): (List[SingleQuery], Parameters) = {
    val queryObject: QueryObject = readQueries(QUERIES_PATH)
    val queryParams = Parameters.create
    queryParams.put("index", queryObject.index)
    queryParams.put("requested", queryObject.requested)
    queryParams.put("processingModel", queryObject.processingModel)
    if (queryObject.scorer.isDefined)
      queryParams.put("scorer", queryObject.scorer.get)

    val queries = queryObject.queries

    (queries, queryParams)
  }

  /**
   * Runs the query in Galago search engine.
   *
   * @param query       Text of the query.
   * @param queryParams Search parameters.
   * @return Resulting document set.
   */
  def runQuery(query: String, queryParams: Parameters): Results = {
    val ret: Retrieval = RetrievalFactory.create(queryParams);
    val q: Node = StructuredQuery.parse(query);
    val transq: Node = ret.transformQuery(q, Parameters.create())

    ret.executeQuery(transq, queryParams)
  }

  /**
   * Converts documents being the result of the search query into their vector representation.
   *
   * @param results Result of the search query.
   * @return Mapping of documents to their vector representation.
   */
  def results2Vector(results: Results): Map[Document, Array[Double]] = {
    var queryToVector: Map[Document, Array[Double]] = Map()
    val dc = new Document.DocumentComponents(true, true, true)

    val scoredDocs: Seq[ScoredDocument] = results.scoredDocuments.asScala
    scoredDocs.foreach(result => {
      val doc: Document = results.pullDocuments(dc).get(result.documentName)
      queryToVector += (doc -> doc2tfidf(doc))
    })
    queryToVector
  }

  /**
   * Identifies which documents in the search result are relevant and which are not,
   * based on the provided explicit feedback.
   *
   * @param results      Result of the search query.
   * @param relevantDocs Documents in the corpus that are relevant to the query.
   * @return Sets of relevant and irrelevant documents.
   */
  def indetifyDocs(results: Map[Document, Array[Double]], relevantDocs: Set[String]): (Seq[Array[Double]], Seq[Array[Double]]) = {
    var relevantResults: Seq[Array[Double]] = Seq()
    var irrelevantResults: Seq[Array[Double]] = Seq()

    results.keys.foreach(result => {
      if (relevantDocs.contains(result.name)) {
        relevantResults = relevantResults :+ results(result)
      } else {
        irrelevantResults = irrelevantResults :+ results(result)
      }
    })
    (relevantResults, irrelevantResults)
  }

  /**
   * Expands the query using Rocchio algorithm.
   *
   * @param text           Text of the original query.
   * @param relevantDocs   Relevant documents being returned for original query.
   * @param irrelevantDocs Irrelevant documents being returned for original query.
   * @return Expanded query in vector space representation.
   */
  def expandQuery(text: String, relevantDocs: Seq[Array[Double]], irrelevantDocs: Seq[Array[Double]]): Array[Double] = {
    val queryVector: Array[Double] = query2tfidf(text)

    val relevantVector: Array[Double] = relevantDocs
      .reduceLeft((a, b) => a.zip(b).map(x => x._1 + x._2))
      .map(x => x * (alpha / relevantDocs.length.toDouble))

    val irrelevantVector: Array[Double] = irrelevantDocs
      .reduceLeft((a, b) => a.zip(b).map(x => x._1 + x._2))
      .map(x => x * (beta / irrelevantDocs.length.toDouble))

    queryVector.zip(relevantVector).map { case (x, y) => x + y }.zip(irrelevantVector).map { case (x, y) => x - y }
  }

  def main(args: Array[String]): Unit = {
    processExpandedQuery()

  }
}
