from trec_car.read_data import *
import json

path = "../corpus/train/train.fold0.cbor"

articles = "../corpus/train/train.fold0.cbor.article.qrels"
outlines = "../corpus/train/train.fold0.cbor.outlines"
paragraphs = "../corpus/train/train.fold0.cbor.paragraphs"


def print_headers(file=outlines):
    """
        Print article headings.
        Based on: https://github.com/TREMA-UNH/trec-car-tools
    """
    with open(file, 'rb') as f:
        for p in iter_annotations(f):
            print('\npagename:', p.page_name)

            # get one data structure with nested (heading, [children]) pairs
            print(p)
            headings = p.nested_headings()
            print('headings= ', [(str(section.heading), len(children)) for (section, children) in headings])

            if len(p.outline()) > 2:
                print('heading 1=', p.outline()[0])

                print('deep headings= ',
                      [(str(section.heading), len(children)) for (section, children) in p.deep_headings_list()])

                print('flat headings= ', ["/".join([str(section.heading) for section in sectionpath]) for sectionpath in
                                          p.flat_headings_list()])


def print_paragraphs(file=paragraphs):
    """
        Print the content of article's paragraphs
        Based on: https://github.com/TREMA-UNH/trec-car-tools
    """
    with open(file, 'rb') as f:
        for p in iter_paragraphs(f):
            print('\n', p.para_id, ':')

            # Print just the text
            texts = [elem.text if isinstance(elem, ParaText)
                     else elem.anchor_text
                     for elem in p.bodies]
            print(' '.join(texts))

            # Print just the linked entities
            entities = [elem.page
                        for elem in p.bodies
                        if isinstance(elem, ParaLink)]
            print(entities)

            # Print text interspersed with links as pairs (text, link)
            mixed = [(elem.anchor_text, elem.page) if isinstance(elem, ParaLink)
                     else (elem.text, None)
                     for elem in p.bodies]
            print(mixed)


def create_corpus_galago(file):
    """
        Function to map the paragraphs in cbor format to a trectext format, suitable for galago indexing.
        Format based on https://github.com/jiepujiang/cs646_tutorials#installation
    """
    cnt = 0
    file_index = 0
    file_size = 50000
    output_file = r'paragraph_corpus_' + str(file_index) + '.trectext'
    stream = open(output_file, 'wb')
    for p in iter_paragraphs(open(file, 'rb')):
        stream.write(b"<DOC>\n")
        stream.write(b"<DOCNO>")
        stream.write((p.para_id).encode('utf8'))
        stream.write(b"</DOCNO>\n")
        stream.write(b"<TEXT>\n")
        # I think we only need to care about paragraph text (we do not use links)
        stream.write((p.get_text()).encode('utf8'))
        stream.write(b"\n</TEXT>\n")
        stream.write(b"</DOC>\n\n")
        cnt += 1
        if cnt > file_size:
            stream.close()
            print("Filled up file number " + str(file_index) + " with documents.")
            file_index += 1
            cnt = 0

            output_file = r'paragraph_corpus_' + str(file_index) + '.trectext'
            stream = open(output_file, 'wb')

    stream.close()
    print("DONE!")


# create_corpus_galago(paragraphs)


def create_queries(file):
    """
        Function used to create the queries based on headings hierarchy.
        Nice source: https://www.inf.ed.ac.uk/teaching/courses/tts/handouts2017/galago_tutorial.pdf
        To run the queries execute the following (optional for more informative output):
            galago/bin/galago batch-search (--verbose=true) PATH_TO_FILE/queries.json
    """
    out = r'queries_test.json'
    out_stream = open(out, 'w')
    queries = dict()
    queries['index'] = r'/home/tomek/TUDelft/Courses/Information Retrieval/index'
    queries['requested'] = 5
    queries['processingModel'] = 'org.lemurproject.galago.core.retrieval.processing.RankedDocumentModel'
    queries['scorer'] = 'bm25'
    queries['queries'] = []
    for p in iter_annotations(open(file, 'rb')):
        queries['queries'].append({'number': str(p.page_id), 'text': '#combine(' + p.page_name + ')'})
        flattened_heading_list = p.flat_headings_list()
        for query, query_id in [((" ".join([str(headings.heading) for headings in heading_path])),
                                 "/".join([str(headings.headingId) for headings in heading_path]))
                                 for heading_path in flattened_heading_list]:
            queries['queries'].append({'number': str(p.page_id + '/' + query_id),
                                       'text': '#combine(' + p.page_name + ' ' + query + ')'})
    json.dump(queries, out_stream)
    print("Done creating queries.")


# create_queries(outlines)


def create_queries_relevance(file, rm_version=1):
    """
        Function used to create the queries based on headings hierarchy for querying with pseudo-relevance feedback.
        Nice source: https://www.inf.ed.ac.uk/teaching/courses/tts/handouts2017/galago_tutorial.pdf
        To run the queries execute the following (optional for more informative output):
            galago/bin/galago batch-search (--verbose=true) PATH_TO_FILE/queries_relevance.json
    """
    out = r'queries_relevance_test.json'
    out_stream = open(out, 'w')
    queries = dict()
    queries['index'] = r'/home/tomek/TUDelft/Courses/Information Retrieval/index'
    queries['requested'] = 5
    queries['processingModel'] = 'org.lemurproject.galago.core.retrieval.processing.RankedDocumentModel'
    queries['relevanceModel'] = 'org.lemurproject.galago.core.retrieval.prf.RelevanceModel' + str(rm_version)
    queries['fbDocs'] = 10
    queries['fbTerm'] = 5
    queries['fbOrigWeight'] = 0.75
    queries['scorer'] = 'bm25'
    queries['queries'] = []
    for p in iter_annotations(open(file, 'rb')):
        queries['queries'].append({'number': str(p.page_id), 'text': '#rm(' + p.page_name + ')'})
        flattened_heading_list = p.flat_headings_list()
        for query, query_id in [((" ".join([str(headings.heading) for headings in heading_path])),
                                 "/".join([str(headings.headingId) for headings in heading_path]))
                                 for heading_path in flattened_heading_list]:
            queries['queries'].append({'number': str(p.page_id + '/' + query_id),
                                       'text': '#rm(' + p.page_name + ' ' + query + ')'})
    json.dump(queries, out_stream)
    print("Done creating queries for relevance model.")

create_queries_relevance(outlines)
