package vector

import org.lemurproject.galago.core.util.WordLists

import scala.collection.mutable
import scala.collection.JavaConverters._

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
    * Normalize the term taking some of the characters out
    * @param term
    * @return
    */
  def normalizeTerm(term: String): String =
    term.replaceAll("[,.\"'`´“‘”″_’—=-]", "").trim
}
