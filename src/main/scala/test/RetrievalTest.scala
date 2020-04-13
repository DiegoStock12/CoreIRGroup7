package test

import org.lemurproject.galago.core.parse.Document
import org.lemurproject.galago.core.retrieval.query.{Node, StructuredQuery}
import org.lemurproject.galago.core.retrieval.{Retrieval, RetrievalFactory}
import org.lemurproject.galago.utility.Parameters
import vector.Utils._
import vector.TFIDF._



import scala.collection.JavaConverters._

/**
  * Test different retrieval options
 * Really good example of this:
 * https://github.com/laura-dietz/galagosample/blob/d20dc6136873cdd884a12976341c435849276693/src/main/java/sample/galago/GalagoCheatSheet.java
  */
object RetrievalTest {

  def main(args: Array[String]): Unit = {

    // Set the global parameters
    val globalParams: Parameters = Parameters.create()
    globalParams.set("index", INDEX_PATH)

    val retrieval: Retrieval = RetrievalFactory.instance(globalParams)

    // Normal query (query likelihood)
    val query = "naval attack of the boat"
    // RM3 query
    val rmQuery = "#rm(naval attack of the boat)"

    // Set the parameters for the query
    val p = Parameters.create()
    p.set("startAt", 0)
    p.set("resultCount", 20)
    p.set("requested", 20)
    p.set("mu", 2000)

    val root: Node = StructuredQuery.parse(rmQuery)

    // Transform the query with the parameters
    val transf = retrieval.transformQuery(root, p)

    // Get the ranking of the documents
    val ranking = retrieval.executeQuery(transf, p).scoredDocuments.asScala

    // print the doc rankings
    ranking.foreach(sd => println(s"${sd.rank}  ${sd.documentName}  (${sd.score})"))

    // Get the terms used in the RM3 query as an array
    val terms =
      transf.getInternalNodes.asScala.map(_.getChild(1).getDefaultParameter)

    // Get the normal tfidf vector and the augmented vector
    val normalQvec = query2tfidf(query)
    val rmVec = query2tfidf(terms.mkString(" "))

    // Get the top document in so we can compare it
    val docName = ranking.head.documentName
    val doc: Document = retrieval.getDocument(
      docName,
      new Document.DocumentComponents(true, true, true)
    )

    // Get the vector of that document
    val docVec = doc2tfidf(doc)

    println(cosineSimilarity(normalQvec, docVec))
    println(cosineSimilarity(rmVec, docVec))



  }

}
