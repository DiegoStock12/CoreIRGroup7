package query_expansion

import org.lemurproject.galago.core.parse.Document
import org.lemurproject.galago.core.retrieval.query.{Node, StructuredQuery}
import org.lemurproject.galago.core.retrieval.{Results, Retrieval, RetrievalFactory, ScoredDocument}
import org.lemurproject.galago.utility.Parameters
import query.DataReader.{QueryObject, SingleQuery, readQueries, readQueryToRelevantDocuments}
import vector.Utils.{ INDEX_PATH_TRAIN, cosineSimilarity}
import ranking.VectorScores.{docMap, retrieval, saveResults, stemmer, QUERIES_PATH}
import vector.WordEmbeddings.{query2Vec, doc2Vec}

import collection.JavaConverters._

object Rocchio {

  val QREL_PATH: String = "/home/tomek/TUDelft/Courses/Information Retrieval/test200_2.0/train.pages.cbor-hierarchical.qrels"
  val QREL_PATH_TRAIN: String = "/home/tomek/TUDelft/Courses/Information Retrieval/train_2.0/fold-1-4-hierarchical.qrels"

  val trainRetrieval: Retrieval = RetrievalFactory.instance(INDEX_PATH_TRAIN)
  val queryToRelevantDocs: Map[String, Set[String]] = readQueryToRelevantDocuments(QREL_PATH)
  val queryToRelevantDocsTrain: Map[String, Set[String]] = readQueryToRelevantDocuments(QREL_PATH_TRAIN)

  val alpha: Double = 0.8
  val beta: Double = 0.1


  /**
   * Performs search with relevance feedback using Rocchio algorithm for query expansion.
   */
  private def processExpandedQuery(): Unit = {
    val (queries, queryParams) = prepareQueries()
    processExpandedQuery(queries, queryParams)
  }

  /**
   * Performs search with relevance feedback using Rocchio algorithm for query expansion.
   */
  def processExpandedQuery(queries: List[SingleQuery], queryParams: Parameters): Unit = {
    println(queries.length)
    var i: Int = 0
    queries.foreach { query =>
      println(i)
      val stemmedQuery: String =
        query.text.split(" +").map(stemmer.stem).mkString(" ")

      var expandedQuery: Array[Double] = query2Vec(stemmedQuery)
      val relevantDocIds: Set[String] = getRelevantDocIds(query)
      println("Number of relevant docs: " + relevantDocIds.size)
      var docsToScore: Map[String, Double] = Map()

      if (relevantDocIds.isEmpty) {
        val results: Results = runQuery(stemmedQuery, queryParams)
        val docsToVector: Map[Document, Array[Double]] = results2Vector(results)
        docsToVector.foreach { case (doc, docVector) => {
          val similarityScore: Double = cosineSimilarity(expandedQuery, docVector)
          docsToScore += (doc.name -> similarityScore)
        }
        }
      }
      else {
        val releventDocVectors: Seq[Array[Double]] = retrieveRelevantDocs(relevantDocIds)
        expandedQuery = expandQuery(expandedQuery, releventDocVectors)

        docMap.foreach { case (doc, docVector) => {
          val similarityScore: Double = cosineSimilarity(expandedQuery, docVector)
          docsToScore += (doc -> similarityScore)
        }
        }
      }

      val topK: List[(String, Double)] = getTopK(docsToScore)
      println("Saving " + topK.size + " results.")
      saveResults(query, topK)
      i += 1
    }
  }


  /**
   * Reads json with queries and prepare queries and all the search parameters.
   *
   * @return Queries and parameters.
   */
  private def prepareQueries(): (List[SingleQuery], Parameters) = {
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
   * @param stemmedQuery Stemmed text of the query.
   * @param queryParams Search parameters.
   * @return Resulting document set.
   */
  private def runQuery(stemmedQuery: String, queryParams: Parameters): Results = {
    val q: Node = StructuredQuery.parse(stemmedQuery);
    val transq: Node = retrieval.transformQuery(q, Parameters.create())

    retrieval.executeQuery(transq, queryParams)
  }

  /**
   * Converts documents being the result of the search query into their vector representation.
   *
   * @param results Result of the search query.
   * @return Mapping of documents to their vector representation.
   */
  private def results2Vector(results: Results): Map[Document, Array[Double]] = {
    var queryToVector: Map[Document, Array[Double]] = Map()
    val dc = new Document.DocumentComponents(true, true, true)

    val scoredDocs: Seq[ScoredDocument] = results.scoredDocuments.asScala
    scoredDocs.foreach(result => {
      val doc: Document = results.pullDocuments(dc).get(result.documentName)
      queryToVector += (doc -> docMap(doc.name))
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
  private def indetifyDocs(results: Map[Document, Array[Double]], relevantDocs: Set[String]): (Seq[Array[Double]], Seq[Array[Double]]) = {
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
   * @param queryVector  Vector representation of original query.
   * @param relevantDocs Relevant documents being returned for original query.
   *                     //   * @param irrelevantDocs Irrelevant documents being returned for original query.
   * @return Expanded query in vector space representation.
   */
  private def expandQuery(queryVector: Array[Double], relevantDocs: Seq[Array[Double]]): Array[Double] = {
    if (relevantDocs.isEmpty) {
      return queryVector
    }

    val relevantVector: Array[Double] = relevantDocs
      .reduceLeft((a, b) => a.zip(b).map(x => x._1 + x._2))
      .map(x => x * (alpha / relevantDocs.length.toDouble))

    queryVector.zip(relevantVector).map { case (x, y) => x + y }
  }

  /**
   * Get relevant docs from the training corpus based on the matching headline.
   * @param query
   * @return Ids of relevant documents.
   */
  private def getRelevantDocIds(query: SingleQuery): Set[String] = {
    val queryLastHeading: String = query.number.split("/").last
    var relevantDocIds: Set[String] = Set()
    queryToRelevantDocsTrain.foreach { case (queryNumber, relevantDocs) => {
      val docLastHeading: String = queryNumber.split("/").last
      if (docLastHeading.equals(queryLastHeading)) {
        relevantDocIds = relevantDocIds ++ relevantDocs
      }
    }
    }
    relevantDocIds
  }

  /**
   * Retrieve relevant docs from train index by docId.
   * @param relevantDocIds Ids of relevant docs.
   * @return Vector representation of relevant docs.
   */
  private def retrieveRelevantDocs(relevantDocIds: Set[String]): Seq[Array[Double]] = {
    var relevantVectors: Seq[Array[Double]] = Seq()
    var cnt: Int = 1
    relevantDocIds.foreach(docId => {
      if (cnt > 100) {
        return relevantVectors
      }
      val doc: Document = trainRetrieval.getDocument(
        docId,
        new Document.DocumentComponents(true, true, true)
      )
      if (doc != null) {
        relevantVectors = relevantVectors :+ doc2Vec(doc)
      } else {
        println("Found null.")
      }
      cnt += 1
    })
    relevantVectors
  }

  /**
   * Gets top k results by the similarity score. Filters scores equal to 0.
   * @param docToExpandedScore
   * @param k Top k results to return.
   * @return
   */
  private def getTopK(docToExpandedScore: Map[String, Double], k: Int = 10): List[(String, Double)] = {
    docToExpandedScore.toList.filter(_._2 > 0.0).sortWith(_._2 > _._2).take(k).map((a => (a._1, a._2)))
  }

  def main(args: Array[String]): Unit = {
    processExpandedQuery()

  }
}
