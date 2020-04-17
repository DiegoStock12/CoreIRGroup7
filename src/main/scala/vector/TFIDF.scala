package vector

import java.io.File

import org.lemurproject.galago.core.index.KeyIterator
import org.lemurproject.galago.core.index.disk.DiskIndex
import org.lemurproject.galago.core.parse.Document
import org.lemurproject.galago.core.parse.stem.KrovetzStemmer
import org.lemurproject.galago.core.retrieval.RetrievalFactory
import org.lemurproject.galago.core.retrieval.query.StructuredQuery
import vector.Utils._

import scala.collection.JavaConverters._
import scala.collection.immutable.ListMap
import scala.collection.{immutable, mutable}
import scala.util.control.Breaks._

object TFIDF {
  // Global variables needed

  private val N_DOCS: Long = 5080
  val stemmer = new KrovetzStemmer

  // Corpus that maps each term to their IDF
  private val corpus: immutable.HashMap[String, (Int, Double)] = getCorpus()

  /**
    * Convert query into the tfidf representation
    * @param query
    * @param removeStopwords
    * @return
    */
  def query2tfidf(query: String,
                  removeStopwords: Boolean = true): Array[Double] = {
    // Get the list of terms
    val terms: List[String] = query
      .split(" +")
      .filter(x => !STOPWORDS.contains(x))
      .map(normalizeTerm)
      .toList

    createVector(terms)
  }

  /**
    * Convert the document to their tfidf representation
    * @param doc
    * @param removeStopwords
    * @return
    */
  def doc2tfidf(doc: Document,
                removeStopwords: Boolean = true): Array[Double] = {
    val terms: List[String] = doc.terms.asScala.toList
      .map(stemmer.stem)
      .filter(x => !STOPWORDS.contains(x))
      .map(x => normalizeTerm(x))

    createVector(terms)
  }

  private def createVector(terms: List[String]): Array[Double] = {
    // Fixed size vector
    val uniqueWords: Set[String] = terms.toSet
    val vec: Array[Double] = Array.fill(corpus.size) { 0.0 }

    for (term: String ← uniqueWords) {
      val n: Int = terms.count(t => t == term)
      val tf: Double = n.toDouble / terms.size

      try {
        val idf: Double = corpus(term)._2
        val index: Int = corpus(term)._1
        vec(index) = tf * idf

      } catch {
        case e: NoSuchElementException =>
        // Some numbers are not able to be read
        // Cause by Galago not handling some numbers as Strings, so
        // were not able to read them
      }

    }

    val norm = vec.map(Math.pow(_, 2)).sum

    // Return the normalized vector
    vec.map(_ / Math.sqrt(norm))
  }

  /**
    * Get corpus
    * @param removeStopwords
    * @return
    */
  private def getCorpus(
    removeStopwords: Boolean = true
  ): immutable.HashMap[String, (Int, Double)] = {

    println("Loading corpus...")
    var corp: immutable.HashMap[String, (Int, Double)] = immutable.HashMap()

    // Get the vocabulary directly from the file (the stemmed term)!
    val postings = new File(new File(INDEX_PATH), "field.krovetz.text")
    // Get the retrieval object to search for the IDF of each
    val retrieval = RetrievalFactory.instance(INDEX_PATH)

    val posting = DiskIndex.openIndexPart(postings.getAbsolutePath)

    val vocabulary: KeyIterator = posting.getIterator

    // Index we'll save in the HashMap for access
    var i: Int = 0

    while (!vocabulary.isDone) {
      var term = vocabulary.getKeyString
      term = normalizeTerm(term)

      if (!corp.contains(term)) {

        // Skip if this is a stopword and we are ignoring them
        breakable {
          if (removeStopwords) if (STOPWORDS.contains(term)) {
            vocabulary.nextKey
            break
          }

          // If not just search for it
          val termNode = StructuredQuery.parse(
            "#text:" + term + ":part=field." + "text" + "()"
          )
          termNode.getNodeParameters.set("queryType", "count")
          try {
            // Get stats of the node
            val termStats = retrieval.getNodeStatistics(termNode)
            val docFreq = termStats.nodeDocumentCount

            //Compute smoothed idf
            val idf = Math.log10(N_DOCS.toDouble / (docFreq + 1) + 1)
            // Add the entry to the map (index and idf)

            corp += (term → (i, idf))
            i += 1

          } catch {
            case iae: IllegalArgumentException =>
            // Expected to happen due to some issues
            // with how galago represents numbers internally
            case e: Exception =>
              e.printStackTrace()
          }
        }
      }
      vocabulary.nextKey

    }

    println("Loaded corpus")

    println(s"Length of the map -> ${corp.size}")

    // Return the map
    corp
  }

  def main(args: Array[String]): Unit = {
    val dc: Document.DocumentComponents =
      new Document.DocumentComponents(true, true, true)
    val retrieval = RetrievalFactory.instance(INDEX_PATH)
    val doc = retrieval.getDocument(retrieval.getDocumentName(28L), dc)
    println(doc.text)

    //corpus.foreach(u => println(s"${u._1} --> ${u._2}"))
    //corpus.toSeq.sortWith(_._2._1 < _._2._1).foreach(println)

    val query =
      "school student bus".split(" ").map(stemmer.stem).mkString(" ")
    val queryVec = query2tfidf(query)
    val docVec = doc2tfidf(doc)
    println(cosineSimilarity(queryVec, docVec))
  }
}
