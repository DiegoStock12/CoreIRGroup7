package vector

import org.lemurproject.galago.core.index.disk.DiskIndex
import org.lemurproject.galago.core.parse.Document
import org.lemurproject.galago.core.retrieval.{Retrieval, RetrievalFactory}
import org.lemurproject.galago.core.util.WordLists

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.immutable

import vector.WordEmbeddings.doc2Vec
import vector.TFIDF.doc2tfidf

// Vector types
sealed trait VectorType
case object W2V extends VectorType
case object Idf extends VectorType

object Utils {



  val INDEX_PATH: String = "../ir_core/index"
  // Set of stopwords
  val STOPWORDS: mutable.Set[String] = {
    WordLists.getWordList("inquery").asScala
  }

  /**
    * Calculating the cosine similarity of the two vectors
    * @param queryVec
    * @param docVec
    * @return
    */
  def cosineSimilarity(queryVec: Array[Double],
                       docVec: Array[Double]): Double = {

    val normQ = queryVec.map(x => Math.pow(x, 2)).sum
    val normDoc = docVec.map(x => Math.pow(x, 2)).sum

    // Return the dot product normalized by the norm
    // Of the 2 vectors (cos(theta))
    (queryVec.zip(docVec) map {
      case (d1, d2) => d1 * d2
    }).sum / (Math.sqrt(normQ)*Math.sqrt(normDoc))
  }

  /**
  * Load into a hashmap all the vectors of all the documents in order to make
   * the computation easier
   * @param vectorType
   * @return
   */
  def calculateDocumentVectors(vectorType: VectorType): immutable.HashMap[String, Array[Double]] ={
    var vecMap : immutable.HashMap[String, Array[Double]] = immutable.HashMap()

    println("Calculating all the document vectors...")

    // Get the number of documents in the index to loop
    // Get the number of docs in the index
    val index = new DiskIndex(INDEX_PATH)
    val ips = index.getIndexPartStatistics("postings.krovetz")

    // Get the total number of documents
    val N_DOCS = ips.highestDocumentCount
    println(N_DOCS)

    // Get the retrieval object!
    val retrieval: Retrieval = RetrievalFactory.instance(INDEX_PATH)
    val dc = new Document.DocumentComponents(true, true, true)

    // Loop the docs and get the vector
    for (i : Long ← 0L until N_DOCS){
      // Get the doc name and the doc itself
      val name = retrieval.getDocumentName(i).trim
      val doc = retrieval.getDocument(name, dc)

      // Depending on the option return the TFIDF vector
      // or the embedding representation
      vectorType match {
        case Idf =>
          vecMap += (name → doc2tfidf(doc))
        case W2V =>
          vecMap += (name → doc2Vec(doc))
      }
    }

    println("Calculation completed!")
    retrieval.close()
    index.close()

    vecMap
  }

  /**
    * Normalize the term taking some of the characters out
    * @param term
    * @return
    */
  def normalizeTerm(term: String): String =
    term.replaceAll("[,.\"'`´“‘”″_’—=-]", "").trim

}


