package query

import java.io.PrintWriter

import org.lemurproject.galago.core.retrieval.query.{Node, StructuredQuery}
import org.lemurproject.galago.core.retrieval.{Results, Retrieval, RetrievalFactory}
import org.lemurproject.galago.utility.Parameters
import query.DataReader.{QueryObject, SingleQuery}

object Searcher {

  val INDEX_PATH: String = vector.Utils.INDEX_PATH

  /**
    * Run queries defined in json file and store results in file.
    * @param queriesPath
    */
  def search(queriesPath: String) = {
    val queryObject: QueryObject = DataReader.readQueries(queriesPath)
    val queryParams = Parameters.create

    queryParams.put("index", queryObject.index)
    queryParams.put("requested", queryObject.requested)
    queryParams.put("processingModel", queryObject.processingModel)

    if (queryObject.scorer.isDefined)
      queryParams.put("scorer", queryObject.scorer)

    if (queryObject.relevanceModel.isDefined) {
      queryParams.put("relevanceModel", queryObject.relevanceModel.get)
      queryParams.put("fbDocs", queryObject.fbDocs.get)
      queryParams.put("fbTerm", queryObject.fbTerm.get)
      queryParams.put("fbOrigWeight", queryObject.fbOrigWeight.get)
    }

    val writer = new PrintWriter("results.txt", "UTF-8")

    val queries: List[SingleQuery] = queryObject.queries

    queries.foreach { query =>
      val number: String = query.number
      val text: String = query.text
      writer.println(processRank(queryParams, text, number))
    }
    writer.close()
  }

  /**
    * Run single query and return results.
    * @param
    */
  def processRank(queryParams: Parameters,
                  qtext: String,
                  qnum: String): String = {
    val ret: Retrieval = RetrievalFactory.create(queryParams);

    //  Returned parsed query will be the root node of a query tree.
    val q: Node = StructuredQuery.parse(qtext);
    println("Parsed Query: " + q.toString)

    //- Transform the query in compliance to the many traversals that might
    //  (or might not) apply to the query.
    val transq: Node = ret.transformQuery(q, Parameters.create())
    System.out.println("\nTransformed Query: " + transq.toString)

    //- Do the Retrieval
    val results: Results = ret.executeQuery(transq, queryParams)

    // Give returnstring
    var retS: String = new String();

    // View Results or inform search failed.
    if (results.scoredDocuments.isEmpty) {
      println("Search failed.  Nothing retrieved.");
    } else {
      results.scoredDocuments.forEach(sd => {
        val rank: Int = sd.rank;

        //- internal ID (indexing sequence number)of document
        val iid: Long = sd.document;
        val score: Double = sd.score;

        //- Get the external ID (ID in the text) of the document.
        val eid: String = ret.getDocumentName(sd.document);

        //- Get document length based on the internal ID.
        val len: Int = ret.getDocumentLength(sd.document);

        retS = retS + s"${qnum} \t Q0 \t ${eid} [${iid}] \t ${rank}  \t ${score} \t STANDARD \n"
      })
    }
    retS
  }

  def main(args: Array[String]): Unit = {
    val querypath: String = "queries.json"
    search(querypath)

  }
}
