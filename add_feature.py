## Read file into dict 
f=open("TFIDF_Rocchio_G7.run", "r")
query_map = {}
fl =f.readlines()
lastq = ''
for x in fl:
    line = x.split()
    query = line[0]
    if(lastq != query):
        lastq = query
        query_map[query] = {}
    doc = line[2]
    sim = line[4]
    query_map[query][doc] = sim

# Read the ranklib file 
f=open("ranklib_G7.txt", "r")
w=open("ranklib_G7_Rocchio.txt", "w+")
fl =f.readlines()
lastquery = ''
for x in fl:
    line = x.split('#') 
    qerdoc = line[1].split()
    query = 'enwiki:' + qerdoc[0]
    thisdoc = qerdoc[1]
    rocsim = 0.0
    if query in query_map:
        docdict = query_map[query]
        if thisdoc in docdict:
            rocsim = docdict[thisdoc]

    w.write(line[0] + "4:" + str(rocsim) + " \t # \t " + line[1])

w.close()
