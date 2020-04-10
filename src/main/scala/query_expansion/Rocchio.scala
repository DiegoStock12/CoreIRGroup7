package query_expansion

import vector.TFIDF._

import org.lemurproject.galago.core.parse.Document

object Rocchio {

  val INDEX_PATH: String = "/home/tomek/TUDelft/Courses/Information Retrieval/index/"

  val alpha: Double = 0.9
  val beta: Double = 0.9

  def expandQuery(query: String, relevantDocs: List[Document], irrelevantDocs: List[Document]) = {
    val queryVector: Array[Double] = query2tfidf(query)

    val relevantVector: Array[Double] = relevantDocs
      .map(doc => doc2tfidf(doc))
      .reduceLeft((a, b) => a.zip(b).map(x => x._1 + x._2))
      .map(x => x*(alpha/relevantDocs.length.toDouble))

    val irrelevantVector: Array[Double] = irrelevantDocs
      .map(doc => doc2tfidf(doc))
      .reduceLeft((a, b) => a.zip(b).map(x => x._1 + x._2))
      .map(x => x*(beta/irrelevantDocs.length.toDouble))

  }

  def main(args: Array[String]): Unit = {

  }
}
