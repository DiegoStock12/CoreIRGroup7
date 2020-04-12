package ranklib

import org.lemurproject.galago.core.parse.Document
import org.lemurproject.galago.core.retrieval.query.{Node, StructuredQuery}
import org.lemurproject.galago.core.retrieval.{Retrieval, RetrievalFactory}
import org.lemurproject.galago.utility.Parameters
import vector.TFIDF._
import vector.Utils._

import scala.collection.JavaConverters._

/**
  * Test different retrieval options
 * Really good example of this:
 * https://github.com/laura-dietz/galagosample/blob/d20dc6136873cdd884a12976341c435849276693/src/main/java/sample/galago/GalagoCheatSheet.java
  */
object FeatureEngineering {

  def main(args: Array[String]): Unit = {

    // Set the global parameters
    val globalParams: Parameters = Parameters.create()
    globalParams.set("index", INDEX_PATH)

    val retrieval: Retrieval = RetrievalFactory.instance(globalParams)

    // Change this to EVERY QUERY IN THE RELEVANCE FILE

    val queryID = 1
//    // Normal query (query likelihood)
    val query = "naval attack of the boat"
//    // RM3 query
    val rmQuery = "#rm(" + query + ")"

    // Set the parameters for the query retrieval
    val p = Parameters.create()
    p.set("startAt", 0)
    p.set("resultCount", 10)
    p.set("requested", 10)
    p.set("mu", 2000)

    val root: Node = StructuredQuery.parse(rmQuery)

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
      val normalQvec = query2tfidf(query)
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
      println(s"1 qid:${queryID} 1:${bm25score} 2:${TFIDFscore} 3:${TFIDFRM1score} # Query: ${query} , DocID: ${docName}")
    })





  }

}
