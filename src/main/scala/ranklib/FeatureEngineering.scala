package ranklib

import org.lemurproject.galago.core.parse.Document
import org.lemurproject.galago.core.retrieval.query.{Node, StructuredQuery}
import org.lemurproject.galago.core.retrieval.{Results, Retrieval, RetrievalFactory}
import org.lemurproject.galago.utility.Parameters
import query.DataReader.SingleQuery
import query_expansion.Rocchio.{expandQuery, indetifyDocs, prepareQueries, results2Vector, runQuery}
import vector.TFIDF._
import vector.Utils._

import scala.collection.JavaConverters._

/**
  * Test different retrieval options
 * Really good example of this:
 * https://github.com/laura-dietz/galagosample/blob/d20dc6136873cdd884a12976341c435849276693/src/main/java/sample/galago/GalagoCheatSheet.java
  */
object FeatureEngineering {

  val queryCount: Int = 0

  def main(args: Array[String]): Unit = {

    // Set the global parameters
    val globalParams: Parameters = Parameters.create()
    globalParams.set("index", INDEX_PATH)

    // Spaghetti code, I'm hungry.
    val (queries, queryParams) = prepareQueries()

    // Set the parameters for the query retrieval
    val p = Parameters.create()
    p.set("startAt", 0)
    p.set("resultCount", 10)
    p.set("requested", 10)
    p.set("mu", 2000)

    queries.foreach { query =>
      runQuery(query, p, globalParams)
    }

  }

  def runQuery(query: SingleQuery, p: Parameters, globalParams: Parameters): Unit = {
    val retrieval: Retrieval = RetrievalFactory.instance(globalParams)
    val id: String = query.number
    val queryText: String = query.text
    val rmQueryText = "#rm(" + query + ")"

    val root: Node = StructuredQuery.parse(queryText)

    // Transform the query with the parameters
    val transf = retrieval.transformQuery(root, p)

    // Get the ranking of the documents
    val ranking = retrieval.executeQuery(transf, p).scoredDocuments.asScala

    // for each of the docs, get the associated rankings
    ranking.foreach(sd => {
      // get docname and the idf score
      val docName = sd.documentName
      val bm25score = sd.score

      // Get the terms used in the RM3 query as an array
      val terms =
        transf.getInternalNodes.asScala.map(_.getChild(1).getDefaultParameter)

      // Get the normal tfidf vector and the augmented vector
      val normalQvec = query2tfidf(queryText)
      val rmVec = query2tfidf(terms.mkString(" "))

      // Get the document to compare it
      val doc: Document = retrieval.getDocument(
        docName,
        new Document.DocumentComponents(true, true, true)
      )

      // Get the vector of that document
      val docVec = doc2tfidf(doc)

      val TFIDFscore = cosineSimilarity(normalQvec, docVec)
      val TFIDFRM1score = cosineSimilarity(rmVec, docVec)
      println(s"1 qid:${queryCount} 1:${bm25score} 2:${TFIDFscore} 3:${TFIDFRM1score} # Query: ${id} , DocID: ${docName}")
    })
  }

}
