package test

import org.lemurproject.galago.core.index.disk.DiskIndex
import org.lemurproject.galago.core.parse.Document
import org.lemurproject.galago.core.retrieval.RetrievalFactory
import vector.TFIDF._
import vector.Utils
import vector.Utils.INDEX_PATH

import scala.collection.JavaConverters._

object LoopDocs {

  /**
    * Loop all the documents in the index
    * @param args
    */
  def main(args: Array[String]): Unit = {

    // To store the sims
    var map = Map[String, Double]()

    // Get the retrieval object to search for the IDF of each
    val retrieval = RetrievalFactory.instance(INDEX_PATH)

    // Get the number of docs in the index
    val index = new DiskIndex(INDEX_PATH)
    val ips = index.getIndexPartStatistics("postings.krovetz")

    // Get the total number of documents
    val N_DOCS = ips.highestDocumentCount

    val dc = new Document.DocumentComponents(true, true, true)

    val query = "naval attack"
    val qVec = query2tfidf(query)

    // Loop all the Documents
    for (i: Long ← 0L to N_DOCS) {
      val name = retrieval.getDocumentName(i)
      val doc = retrieval.getDocument(name, dc)

      // Get cosine sim
      val docVec = doc2tfidf(doc)

      // Calculate the cosine similarity
      val cos = Utils.cosineSimilarity(qVec, docVec)

      // Add it to the map
      if (cos > 0)
        map += (name → cos)
    }

    // Now sort it and get the most relevant ones!
    val topK = map.toSeq.sortWith(_._2 > _._2).take(10)


    topK.foreach(
      pair =>
        println(
          s"${pair._1} --> ${pair._2}  (${retrieval.getDocument(pair._1, dc).terms.asScala.mkString(" ")})"
      )
    )

  }

}
