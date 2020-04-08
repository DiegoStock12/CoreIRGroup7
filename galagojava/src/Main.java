import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;
import org.lemurproject.galago.core.retrieval.RetrievalFactory;
import org.lemurproject.galago.core.retrieval.Retrieval;
import org.lemurproject.galago.core.retrieval.Results;
import org.lemurproject.galago.core.retrieval.ScoredDocument;
import org.lemurproject.galago.core.parse.Document.DocumentComponents;
import org.lemurproject.galago.core.retrieval.query.StructuredQuery;
import org.lemurproject.galago.core.retrieval.query.Node;
import org.lemurproject.galago.utility.Parameters;

import java.io.InputStream;
import java.io.PrintWriter;

import org.apache.commons.io.IOUtils;

import org.json.simple.JSONObject;


public class Main {

    public static final String indexPath = "/Users/erwin/Documents/TU/MSY1/Q3/IR/CORE_IR/corpus";

    public static final String queryFile = "queries2.json";

    public static void main (String[] args) throws Exception {
            writeEval();

    } //- end method main

    public static void writeEval() throws Exception {

        // results file
        InputStream is = Main.class.getResourceAsStream(queryFile);
        String jsonTxt = IOUtils.toString(is, "UTF-8");
        JSONParser parser = new JSONParser();
        Object jsonObj = parser.parse(jsonTxt);

        JSONObject json = (JSONObject) jsonObj;
        JSONArray queries = (JSONArray) json.get("queries");

        Parameters queryParams = Parameters.create();

        //- Note typically one gets parameter values from the commandline or a
        //  JSON configuration file.
        queryParams.set("index", indexPath);
        queryParams.set("requested", (long) json.get("requested"));
        queryParams.set("relevanceModel", (long) json.get("fbDocs"));
        queryParams.set("fbDocs", (long) json.get("fbTerm"));
        queryParams.set("processingModel", (String) json.get("processingModel"));
        queryParams.set("fbTerm", (double) json.get("fbOrigWeight"));
        queryParams.set("scorer", (String) json.get("scorer"));

//        - Do verbose output
        queryParams.set("verbose", true);

        PrintWriter writer = new PrintWriter("results.txt", "UTF-8");

        for(int i = 0; i < queries.size(); i++){
            JSONObject query = (JSONObject) queries.get(i);
            int number = Integer.parseInt((String) query.get("number"));
            String text = (String) query.get("text");

            writer.println(processRank(queryParams, text, number));
        }
        writer.close();
    }

    public static String processRank(Parameters queryParams, String qtext, int qnum){
        try {

            //- Set the index to be searched
            Retrieval ret = RetrievalFactory.create(queryParams);

            //  Returned parsed query will be the root node of a query tree.
            Node q = StructuredQuery.parse(qtext);
            System.out.println("Parsed Query: " + q.toString());

            //- Transform the query in compliance to the many traversals that might
            //  (or might not) apply to the query.
            Node transq = ret.transformQuery(q, Parameters.create());
            System.out.println("\nTransformed Query: " + transq.toString());

            //- Do the Retrieval
            Results results = ret.executeQuery(transq, queryParams);

            // Give returnstring
            String retS = new String();

            // View Results or inform search failed.
            if (results.scoredDocuments.isEmpty()) {
                System.out.println("Search failed.  Nothing retrieved.");
            } else {
                //- The DocumentComponents object stores information about a
                //  document.
                DocumentComponents dcs = new DocumentComponents();

                for (ScoredDocument sd : results.scoredDocuments) {
                    int rank = sd.rank;

                    //- internal ID (indexing sequence number)of document
                    long iid = sd.document;
                    double score = sd.score;

                    //- Get the external ID (ID in the text) of the document.
                    String eid = ret.getDocumentName(sd.document);

                    //- Get document length based on the internal ID.
                    int len = ret.getDocumentLength(sd.document);

                    retS += String.format("%d \t Q0 \t %s [%d] \t %d  \t %f \t STANDARD \n", qnum, eid, iid, rank, score);
                }
            }
            return retS;
        }
        catch (Exception ex) {
            System.out.println("Exception: " + ex.toString());
            return " ";
        }
    }

}  //- end class RunAQuery

