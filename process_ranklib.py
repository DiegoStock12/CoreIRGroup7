import csv 
import operator 

## Read file into dict 
f=open("ranklib_G7.txt", "r")
query_map = {}
fl =f.readlines()
lastqid = 0
for x in fl:
    line = x.split()
    qdict = {}
    qid = line[1][4:]
    qdict['id'] = qid
    if(qid!=lastqid):
        query_map[qid] = []
        lastqid = qid
        rank = 0
    qdict['query_id'] = line[6]
    qdict['doc_id'] = line[7]
    qdict['rank'] = rank 
    query_map[qid].append(qdict)
    rank += 1 

# print(query_map["1"])

## Load the rerank file and write to a csv
f = open("rerank_f1_G7.tx", "r")

with open('ranklib_csv.csv', mode='w') as ranklib_csv:
    filewriter = csv.writer(ranklib_csv, delimiter=',', quotechar='"', quoting=csv.QUOTE_MINIMAL)
    fl =f.readlines()
    for x in fl:
        line = x.split()
        qid = line[0]
        rank = int(line[1])
        sim = line[2]
        query = query_map[qid][rank]['query_id']
        docid = query_map[qid][rank]['doc_id']

        filewriter.writerow([qid,query,docid,sim])


## rerank the csv and write as list
reader = csv.reader(open("ranklib_csv.csv"), delimiter=",")
sortedsimlist = sorted(reader, key=operator.itemgetter(3))
sortedquerylist = sorted(sortedsimlist, key=operator.itemgetter(0))


# For a list 
w=open("ranklib_RANKED_G7.run", "w")
lastqid = 0
rank = 0
for row in sortedquerylist:
    qid = row[0]
    query = row[1]
    docid = row[2]
    sim = row[3]
    if(qid!=lastqid):
        lastqid = qid
        rank = 1

    w.write("enwiki:" + query + "\t Q0 \t" + docid + "\t" + str(rank) + "\t" + sim + "\t" + "ranklib \n")

    rank += 1
w.close()