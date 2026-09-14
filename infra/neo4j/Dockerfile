FROM neo4j:5.26-community

# GDS 2.13 is the supported algorithm library for the Neo4j 5.26 LTS line.
ADD --chmod=644 --checksum=sha256:10e072f73992224f1159f246c9d6a89da5f3b3434aeffa5be42647edda13a8d8 https://github.com/neo4j/graph-data-science/releases/download/2.13.4/neo4j-graph-data-science-2.13.4.jar /plugins/neo4j-graph-data-science-2.13.4.jar
RUN chmod 755 /plugins
