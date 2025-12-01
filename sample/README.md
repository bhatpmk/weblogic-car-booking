#  LangChain4j with WebLogic

## Introduction

This example is derived from [Quarkus-LangChain4j](https://github.com/jefrajames/car-booking) example used to illustrate talk at [JChateau 2024](https://www.jchateau.org).

It is based on a simplified car booking application inspired from the [Java meets AI](https://www.youtube.com/watch?v=BD1MSLbs9KE) talk from [Lize Raes](https://www.linkedin.com/in/lize-raes-a8a34110/) at Devoxx Belgium 2023 with additional work from [Jean-François James](http://jefrajames.fr/). The original demo is from [Dmytro Liubarskyi](https://www.linkedin.com/in/dmytro-liubarskyi/). The car booking company is called "Miles of Smiles" and the application exposes two AI services:

. a chat service to freely discuss with a customer assistant
. a fraud service to determine if a customer is a fraudster

This sample now uses an embedded Apache Derby database accessed through a WebLogic-managed JDBC Data Source. The JNDI name is jdbc/CarBookingDS. SQL scripts to create the schema and seed records are under sample/db, and a WLST script to create the data source is under sample/wlst.

## Technical context

The project has been developed and tested with:

* Java 17
* WebLogic 15.1.1
* Langchain4j 1.7.1
* Maven 3.9.9

## Packaging the application

### Build car-booking
```
cd <project_root>
mvn -U clean package
```

## Configuration

All LLM the configuration is centralized in config/llm-config.properties. The application is tested with Ollama (enabled by default) as well as OpenAI models. To switch providers, comment/uncomment the appropriate section in config/llm-config.properties and restart the application.
Please note that the "demo" key is free for demonstration purposes, has a quota and is restricted to the gpt-4o-mini model. Please expand **What if I don't have an API key?** in the documentation at
https://github.com/langchain4j/langchain4j/blob/main/docs/docs/integrations/language-models/open-ai.md#api-key

The sample documents for RAG ingestion are placed under docs-for-rag. Make sure that the fully qualified path to llm-config.properties and docs-for-rag are available as JAVA_OPTIONS when you start WebLogic.

For example, set the following JAVA_OPTIONS from the terminal where you start WebLogic.
```
export JAVA_OPTIONS="-Dllmconfigfile=<project dir>/sample/config/llm-config.properties -Ddocragdir=<project dir>/sample/docs-for-rag"
```

## Database setup (Derby Embedded)

Summary
- JNDI name: jdbc/CarBookingDS
- Derby URL (Embedded): jdbc:derby:/absolute/path/to/milesofsmiles;create=true
- Scripts:
  - sample/db/derby-schema.sql
  - sample/db/derby-data.sql
  - sample/db/derby-init.sql (edit the connect line to point at your absolute DB path)
- WLST to create the Data Source: sample/wlst/create-derby-ds.py

Step 1: Choose database location
- Pick a writable absolute path on the server host, for example: <DOMAIN_HOME>/derby/milesofsmiles
- Using an absolute path avoids ambiguity with working directories.

Step 2: Create WebLogic JDBC Data Source (Embedded Derby)
- Example (online WLST):
  ${WL_HOME}/common/bin/wlst.sh sample/wlst/create-derby-ds.py \
    --adminUrl t3://<host>:<port> --user <user> --password <password> \
    --dsName CarBookingDS --jndi jdbc/CarBookingDS \
    --driver org.apache.derby.jdbc.EmbeddedDriver \
    --url jdbc:derby:/absolute/path/to/milesofsmiles;create=true \
    --target AdminServer

Step 3: Initialize schema and seed data
- Important: Do NOT have WebLogic running while initializing the embedded Derby database (embedded DB cannot be opened by two JVMs).
- cd sample/db
- Edit derby-init.sql: update the connect line to the same absolute URL used in the Data Source, e.g.
  connect 'jdbc:derby:/absolute/path/to/milesofsmiles;create=true';
- Run ij to execute the init script. Example:
  $JAVA_HOME/bin/java -cp "$DERBY_HOME/lib/derbytools.jar:$DERBY_HOME/lib/derby.jar" org.apache.derby.tools.ij derby-init.sql
- Alternatively, start ij interactively, connect with the URL above, then run derby-schema.sql and derby-data.sql

Step 4: Start WebLogic and verify
- Start WebLogic.
- Optionally test the Data Source from the Admin Console (test table name: SYSTABLES).
- Deploy the application as described below.

## Running the application

* Start WebLogic 15.1.1 server
* Deploy the application weblogic-car-booking.war using a tool of your choice. Here is the sample deployment command using weblogic.Deployer
```
$JAVA_HOME/bin/java -cp $WL_HOME/server/lib/weblogic.jar weblogic.Deployer -adminurl t3://<admin host>:<admin port>  -username <user>  -password <password> -deploy -name weblogic-car-booking -targets AdminServer <path>/weblogic-car-booking.war
```

The script weblogic-demo includes functions build, deploy and undeploy the demo application. Please take a look at the environment variables 
required by the script.

## Access chat service

The chat service can be accessed in a browser by accessing the URL http://host:port/car-booking/. The index.jsp assumes that the application is deployed
to WebLogic server running on port 7001 and is accessible using localhost.


It can also be accessed using curl -
```
curl -X 'GET' 'http://<host>:<port>/car-booking/api/car-booking/chat?question=I%20want%20to%20book%20a%20car%20how%20can%20you%20help%20me%3F' -H 'accept: text/plain'
```

For more information, please see [Quarkus-LangChain4j](https://github.com/jefrajames/car-booking) example.

## Known Issues
* Redeploying the application will fail with an error like "java.lang.UnsatisfiedLinkError: Native Library <home directory>/.djl.ai/tokenizers/0.20.3-0.31.1-cpu-linux-x86_64/libtokenizers.so already loaded in another classloader",
  Probably the error is coming due to the use of InMemoryEmbeddingStore, so a restart of WebLogic is required after undeploy and before subsequent deploy.
