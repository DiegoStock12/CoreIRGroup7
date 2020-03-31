from trec_car.read_data import *

path = "../corpus/train/train.fold0.cbor"

articles = "../corpus/train/train.fold0.cbor.article.qrels"
outlines = "../corpus/train/train.fold0.cbor.outlines"
paragraphs = "../corpus/train/train.fold0.cbor.paragraphs"

# Print the headers
with open(outlines, 'rb') as f:
    for p in iter_annotations(f):
        print('\npagename:', p.page_name)

        # get one data structure with nested (heading, [children]) pairs
        print(p)
        headings = p.nested_headings()
        print('headings= ',  [ (str(section.heading), len(children)) for (section, children) in headings])

        if len(p.outline())>2:
            print('heading 1=', p.outline()[0])

            print('deep headings= ', [(str(section.heading), len(children)) for (section, children) in p.deep_headings_list()])

            print('flat headings= ', ["/".join([str(section.heading) for section in sectionpath]) for sectionpath in p.flat_headings_list()])

exit(0)

# Print the paragraphs
with open(paragraphs, 'rb') as f:
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
        Function to map the paragraphs in cbor format to a trectext format, suitable for galago/lucene indexing.
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

create_corpus_galago(paragraphs)
