package vector

import java.io.File

import org.lemurproject.galago.core.index.disk.DiskIndex
import org.lemurproject.galago.core.parse.Document
import org.lemurproject.galago.core.retrieval.RetrievalFactory
import org.lemurproject.galago.core.retrieval.query.StructuredQuery
import vector.Utils._

import scala.collection.JavaConverters._
import scala.collection.immutable.ListMap
import scala.collection.{immutable, mutable}
import scala.util.control.Breaks._

object TFIDF {
  // Global variables needed

  private var N_DOCS: Long = _

  // Corpus that maps each term to their IDF
  private val corpus: immutable.TreeMap[String, Double] = getCorpus()

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
      .toList
      .filter(x => !STOPWORDS.contains(x))
      .map(x => normalizeTerm(x))

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
      .filter(x => !STOPWORDS.contains(x))
      .map(x => normalizeTerm(x))

    createVector(terms)
  }

  private def createVector(terms: List[String]): Array[Double] = {
    val uniqueWords: Set[String] = terms.toSet
    var vec: mutable.MutableList[Double] = mutable.MutableList()

    var norm = 0.0
    for (term: String ← corpus.keySet) {
      if (uniqueWords.contains(term)) {
        val n: Int = terms.count(t => t == term)
        val tf: Double = n.toDouble / terms.size
        val idf: Double = corpus(term)

        norm += Math.pow(tf * idf, 2)

        vec += tf * idf
      } else {
        vec += 0D
      }
    }
    // Return the array
    vec
      .map(x => x / Math.sqrt(norm))
      .toArray
  }

  /**
    * Get corpus
    * @param removeStopwords
    * @return
    */
  private def getCorpus(
    removeStopwords: Boolean = true
  ): immutable.TreeMap[String, Double] = {

    println("Loading corpus...")
    var corp: immutable.TreeMap[String, Double] = immutable.TreeMap()

    // Get the vocabulary directly from the file (the stemmed term)!
    val postings = new File(new File(INDEX_PATH), "field.krovetz.text")
    // Get the retrieval object to search for the IDF of each
    val retrieval = RetrievalFactory.instance(INDEX_PATH)

    val index = new DiskIndex(INDEX_PATH)
    val posting = DiskIndex.openIndexPart(postings.getAbsolutePath)
    val ips = index.getIndexPartStatistics("postings.krovetz")

    // Get the total number of documents
    N_DOCS = 5080
    

    val vocabulary = posting.getIterator

    while (!vocabulary.isDone) {

      var term = vocabulary.getKeyString
      term = normalizeTerm(term)

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
          println(term +" --> " + docFreq)

          //Compute smoothed idf
          val idf = Math.log10(N_DOCS.toDouble / (docFreq + 1) + 1)
          // Add the entry to the map
          corp += (term → idf)

        } catch {
          case iae: IllegalArgumentException =>
          // Do nothing in case the failure is due
          // to random index format stuff
          // Expected to happen
          case e: Exception =>
            e.printStackTrace()
        }
        vocabulary.nextKey
      }
    }

    println("Loaded corpus")
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
    corpus.toSeq.sortWith(_._2 < _._2).take(20).foreach(println)

    val query = "naval air squadron attack"
    val queryVec = query2tfidf(query)
    val docVec = doc2tfidf(doc)
    println(cosineSimilarity(queryVec, docVec))
  }
}
