package test

import java.io.PrintWriter
import java.util.NoSuchElementException

import org.lemurproject.galago.core.parse.Document
import org.lemurproject.galago.core.parse.stem.KrovetzStemmer
import org.lemurproject.galago.core.retrieval.query.{Node, StructuredQuery}
import org.lemurproject.galago.core.retrieval.{Retrieval, RetrievalFactory}
import org.lemurproject.galago.utility.Parameters
import vector.Idf
import vector.Utils._
import vector.TFIDF._
import query.DataReader
import query.DataReader.QueryObject

import scala.collection.JavaConverters._
import scala.collection.immutable

object VectorScores {

  val QUERIES_PATH = "queries.json"

  def main(args: Array[String]): Unit = {

//    val writer = new PrintWriter("results.txt", "UTF-8")
    val retrieval: Retrieval = RetrievalFactory.instance(INDEX_PATH)
    val p = Parameters.create()
    p.set("startAt", 0)
    p.set("resultCount", 50)
    p.set("requested", 50)
    p.set("mu", 2000)

    // Load the documents
    var docMap = calculateDocumentVectors(Idf)
    println(docMap.size)
    println(docMap.head._2.length)

    // Load the queries
    val queryObject: QueryObject = DataReader.readQueries(QUERIES_PATH)
    val stemmer: KrovetzStemmer = new KrovetzStemmer()
    val dc = new Document.DocumentComponents(true, true, true)

    println(queryObject.queries.size)

    var count = 0
    queryObject.queries.foreach { query =>
      var resultMap = immutable.HashMap[String, Double]()
      // Stem each word of the query
      val stemmedQuery =
        query.text.split(" ").map(x => stemmer.stem(x)).mkString(" ")

      val root: Node = StructuredQuery.parse(stemmedQuery)

      // Transform the query with the parameters
      val transf = retrieval.transformQuery(root, p)

      // Get the ranking of the documents
      val results = retrieval.executeQuery(transf, p)
      val ranking = results.scoredDocuments.asScala

      println(stemmedQuery)
      // get the vector of that query
      val qVec = query2tfidf(stemmedQuery)

      // Compare against all the vectors
      ranking.foreach { relDoc =>
        var sim = 0.0
        try {
          sim = cosineSimilarity(qVec, docMap(relDoc.documentName.trim))
        } catch {

          // For some reason some documents are not indexed in the Hashmap
          // In that case we have to get the document and calculate the vector on the go
          case nse: NoSuchElementException =>
            val docVec =
              doc2tfidf(results.pullDocuments(dc).get(relDoc.documentName))
            docMap += (relDoc.documentName → docVec)
            sim = cosineSimilarity(qVec, docVec)
            println(s"${nse.getMessage}, new sim --> $sim")
        }
        if (sim > 0)
          resultMap += (relDoc.documentName → sim)
      }

      // Take the top 10
      val top10 = resultMap.toSeq.sortWith(_._2 > _._2).take(10)
//      top10.foreach(pair => println(s"${pair._1} --> ${pair._2}"))

      count += 1
      if (count % 50 == 0)
        println(count)
      println("\n\n")

    }
  }

}
