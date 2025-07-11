#  LangChain4j with WebLogic

## Introduction

This example is derived from [Quarkus-LangChain4j](https://github.com/jefrajames/car-booking) example used to illustrate talk at [JChateau 2024](https://www.jchateau.org).

It is based on a simplified car booking application inspired from the [Java meets AI](https://www.youtube.com/watch?v=BD1MSLbs9KE) talk from [Lize Raes](https://www.linkedin.com/in/lize-raes-a8a34110/) at Devoxx Belgium 2023 with additional work from [Jean-François James](http://jefrajames.fr/). The original demo is from [Dmytro Liubarskyi](https://www.linkedin.com/in/dmytro-liubarskyi/). The car booking company is called "Miles of Smiles" and the application exposes two AI services:

. a chat service to freely discuss with a customer assistant
. a fraud service to determine if a customer is a fraudster

For the sake of simplicity, there is no database interaction, the application is standalone and can be used "as is".

## Technical context

The project has been developed and tested with:

* Java 17
* WebLogic 15.1.1
* LangChain4j 1.1.0
* Maven 3.9.9

The sample depends on artifacts langchani4j-cdi-core and langchain4j-cdi-portable-extn with version 1.0.0-SNAPSHOT. As these 
artifacts are not available in Maven central, build the modules from https://github.com/langchain4j/langchain4j-cdi locally
using command 'mvn clean install', before building this application. The script build.sh provides the function to build langchain4j-cdi locally.

The application expects Ollama server is accessible via the URL http://localhost:11434/ and LLaMA 3 language model llama3.1 is running.

## Packaging the application

### Build weblogic-car-booking
```
cd <project_root>
cd mvn clean package
```

## Configuration

All LLM the configuration is centralized in config/llm-config.properties. The sample documents for RAG ingestion are placed
under docs-for-rag. Make sure that the fully qualified path to llm-config.properties and docs-for-rag are available as
JAVA_OPTIONS when you start WebLogic.

For example, set the following JAVA_OPTIONS from the terminal where you start WebLogic.
```
export JAVA_OPTIONS="-Dllmconfigfile=<project dir>/weblogic-car-booking/config/llm-config.properties -Ddocragdir=<project dir>/weblogic-car-booking/docs-for-rag"
```

## Running the application

* Start WebLogic 15.1.1 server
* Deploy the application weblogic-car-booking.war using a tool of your choice. Here is the sample deployment command using weblogic.Deployer
```
$JAVA_HOME/bin/java -cp $WL_HOME/server/lib/weblogic.jar weblogic.Deployer -adminurl t3://<admin host>:<admin port>  -username <user>  -password <password> -deploy -name weblogic-car-booking -targets AdminServer <path>/weblogic-car-booking.war
```

The script build.sh includes functions build, deploy and undeploy the demo application. Please take a look at the environment variables 
required by the script and the functions to know more.

## Access chat service

The chat service can be accessed in a browser by accessing the URL http://<host>:<port>/weblogic-car-booking/

It can also be accessed using curl -
```
curl -X 'GET' 'http://<host>:<port>/weblogic-car-booking/api/car-booking/chat?question=I%20want%20to%20book%20a%20car%20how%20can%20you%20help%20me%3F' -H 'accept: text/plain'
```

For more information, please see [Quarkus-LangChain4j](https://github.com/jefrajames/car-booking) example.

## Known limitations
* Redeploying the application will fail with an error like "java.lang.UnsatisfiedLinkError: Native Library <home directory>/.djl.ai/tokenizers/0.20.3-0.31.1-cpu-linux-x86_64/libtokenizers.so already loaded in another classloader",
  The sample includes DocRagIngestor.java, inspired by examples in langchain4j-cdi repository. The error comes from InMemoryEmbeddingStore, so a restart of WebLogic is required after undeploy and before subsequent deploy.