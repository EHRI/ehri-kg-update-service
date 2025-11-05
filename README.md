# EHRI KG Update Service
This service aims to maintain the [EHRI-KG](https://lod.ehri-project-test.eu/) synchronised to the EHRI Portal data.
For achieving this purpose, it listens for change events coming from the EHRI Portal and processes them deleting, 
creating and updating the concerned entities in the designated triple store.

## CLI
This service is mainly meant to be operated through the command line for which it offers a convenient CLI:

```
Usage: ehri-kg-update-service [<options>]

Options:
  -m, --mapping=<text>  The mapping rules to be processed by shexml-streaming. Default: conf/ehri_sse_mapping.shexml
  -c, --conf=<text>     The properties file with the entities configurations. Default: conf/config.properties
  -o, --output=<text>   File path where to store the reports of this service. Example: output.jsonl
  --kafkaServer=<text>  Kafka topic where to push the reports of this service. Example: localhost:9092
  --kafkaTopic=<text>   Kafka topic where to push the reports of this service. Example: my-topic
  -h, --help            Show this message and exit
```

There are no mandatory options to run the service, as by default it assumes that the 
mapping and the configuration properties files are located under the `conf` directory. However, it is always possible to
provide different configurations using the `-m` and `-c` options. 

The service uses a logger to print all the messages of the application, and it will be used to deliver a report
after processing each event. Additionally, these reports can also be stored in an output file (using the `-o` option) 
following the JSON Lines format and in an append-only fashion. Similarly, it is also possible to push the reports to a Kafka
topic using the `--kafkaServer` and `--kafkaTopic` options.

## Architecture
The architecture of this service heavily relies on the Reactive programming precepts in order to be able to process the 
event in an asynchronous and non-blocking manner. Therefore, it will connect to the EHRI Portal SSE endpoint by means of
the [shexml-streaming](https://github.com/herminiogg/shexml-streaming) library which, in turn, returns an Observable 
that is transformed following a series of sequential steps:

1. Parse the event data
2. Download the new data from the EHRI Portal GraphQL API
3. Establish the data status *before* performing any update
4. Transform the received data to RDF using ShExML and the mapping rules defined in the [EHRI-KG-mapping repository](https://github.com/EHRI/ehri-kg-mapping)
5. Execute DELETE and INSERT SPARQL queries
6. Establish the data status *after* performing the update
7. Compare the results in 3 and 6 and generate the event report

## Requirements
The minimum versions for this software to work are:
* JDK 17 or any compatible version (e.g., Open JDK 17)
* Kotlin 2.1.10
* Gradle 8.10

## Build
It is possible to build an uber/fat jar using Gradle or one of its embedded versions:
```
$ gradlew shadowJar
```

## Dependencies
The following dependencies are used by this software:

| Dependency                                         | License                                 |
|----------------------------------------------------|-----------------------------------------|
| com.herminiogarcia / shexml-streaming_3            | MIT License                             |
| io.ktor / ktor-client-core                         | Apache License 2.0                      |
| io.ktor / ktor-client-cio                          | Apache License 2.0                      |
| org.jetbrains.kotlinx / kotlinx-serialization-json | Apache License 2.0                      |
| io.github.oshai / kotlin-logging-jvm               | Apache License 2.0                      |
| ch.qos.logback / logback-classic                   | Eclipse Public License v1.0 or LGPL-2.1 |
| com.github.ajalt.clikt / clikt                     | Apache License 2.0                      |
| org.apache.kafka / kafka-clients                   | Apache License 2.0                      |