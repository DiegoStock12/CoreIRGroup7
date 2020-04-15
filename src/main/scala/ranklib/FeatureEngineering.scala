package ranklib

import org.lemurproject.galago.core.parse.Document
import org.lemurproject.galago.core.retrieval.query.{Node, StructuredQuery}
import org.lemurproject.galago.core.retrieval.{Results, Retrieval, RetrievalFactory}
import org.lemurproject.galago.utility.Parameters
import query.DataReader.{SingleQuery, readQueryToRelevantDocuments}
import query_expansion.Rocchio.{QREL_PATH, expandQuery, indetifyDocs, prepareQueries, results2Vector, runQuery}
import vector.TFIDF._
import vector.Utils._

import java.io.PrintWriter

import scala.collection.JavaConverters._
import scala.io.Source

/**
  * Test different retrieval options
 * Really good example of this:
 * https://github.com/laura-dietz/galagosample/blob/d20dc6136873cdd884a12976341c435849276693/src/main/java/sample/galago/GalagoCheatSheet.java
  */
object FeatureEngineering {

  val QREL_PATH: String = "../corpus/train/train.pages.cbor-hierarchical.qrels"

  val queryToRelevantDocs: Map[String, Set[String]] = readQueryToRelevantDocuments(QREL_PATH)

  var queryCount: Int = 0

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

    // write to file
    val pw = new PrintWriter("ranklib_G7_3.txt")

    // Run for each query
    queries.foreach { query =>
      queryCount += 1
      print(queryCount)
//      if(queryCount >= 1986){
      if(queryCount > 1976){
        pw.write(runQuery(query, p, globalParams))
      }
      println()
    }

    pw.close

  }

  def runQuery(query: SingleQuery, p: Parameters, globalParams: Parameters): String = {
    val retrieval: Retrieval = RetrievalFactory.instance(globalParams)
    val id: String = query.number
    val queryText: String = query.text
    val rmQueryText = "#rm(" + query + ")"

    val root: Node = StructuredQuery.parse(queryText)

    // Transform the query with the parameters
    val transf = retrieval.transformQuery(root, p)

    // Get the ranking of the documents
    val ranking = retrieval.executeQuery(transf, p).scoredDocuments.asScala

    // Returnblock
    var retBlock : String = ""

    // for each of the docs, get the associated rankings
    ranking.foreach(sd => {
      // get docname and the idf score
      val docName = sd.documentName
      val bm25score = sd.score

      val relevancy = if(queryToRelevantDocs.getOrElse("enwiki:" + id, Set()).contains(docName)) 1 else 0
      print(" " + relevancy)

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

      val TFIDFscore = if(!cosineSimilarity(normalQvec, docVec).isNaN) cosineSimilarity(normalQvec, docVec) else 0
      val TFIDFRM1score = if(!cosineSimilarity(rmVec, docVec).isNaN) cosineSimilarity(normalQvec, docVec) else 0

      retBlock += s"${relevancy} \t qid:${queryCount} \t 1:${bm25score.toFloat} \t 2:${TFIDFscore.toFloat} \t 3:${TFIDFRM1score.toFloat} \t # \t ${id} \t ${docName} \n"
    })
    retBlock
  }

}
