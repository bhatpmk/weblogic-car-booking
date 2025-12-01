# A simple Car Booking application with LangChain4j CDI Portable Extension

This repository provides a reference implementation for integrating Large Language Models (LLMs) into Jakarta EE applications, running on Oracle WebLogic Server 15.1.1. It demonstrates how to:
- Expose AI-powered REST endpoints using LangChain4J CDI Portable Extension
- Compose LLM capabilities with domain oriented tools
- Retrieval-Augmented Generation (RAG) using local documents ingested at application startup
- Interact from a console client while the server retains conversational history

This example is derived from [Quarkus-LangChain4j](https://github.com/jefrajames/car-booking) used to illustrate a talk at JChateau 2024. It is inspired by “Java meets AI” by [Lize Raes](https://www.youtube.com/watch?v=BD1MSLbs9KE) (Devoxx Belgium 2023), with additional work by [Jean‑François James](http://jefrajames.fr/). The original demo is by [Dmytro Liubarskyi](https://www.linkedin.com/in/dmytro-liubarskyi/).

---

## Project Layout

```
weblogic-car-booking/
├─ app/                             # Jakarta EE web application module (WAR)
│  ├─ src/main/java/dev/langchain4j/cdi/example/booking/
│  │  ├─ BookingService.java        # Implements booking retrieval and cancellation; uses DataSource (jdbc/CarBookingDS)
│  │  ├─ ChatAiService.java         # @RegisterAIService - chat orchestration, memory, tools configuration
│  │  ├─ FraudAiService.java        # Additional tool demonstrating fraud checks
│  │  ├─ CarBookingResource.java    # REST resource exposing /chat and /fraud
│  ├─ src/main/webapp/
│  │  └─ WEB-INF/
│  │     └─ beans.xml               # CDI bootstrapping
│  ├─ config/llm-config.properties  # Model provider configuration (Ollama / OpenAI)
│  ├─ docs-for-rag/                 # Sample documents for RAG ingestion
│  └─ pom.xml
│
├─ client/                          # Console client to interact with /chat
│  ├─ src/main/java/dev/langchain4j/cdi/example/booking/client/ChatClient.java
│  └─ pom.xml
│
├─ scripts/
│  ├─ sql/                          # Derby database scripts
│  │  ├─ derby-schema.sql           # Creates database schema for CUSTOMER and BOOKING
│  │  ├─ derby-data.sql             # Seeds bookings with dates relative to “today”
│  │  └─ derby-init.sql             # ij runner
│  └─ wlst/
│     └─ create-derby-ds.py         # WLST script to create jdbc/CarBookingDS
│
├─ demo.sh                          # Helper script to build, deploy, undeploy, run client, etc.
```

---

## Prerequisites

- Java 17
- Maven 3.9.x
- Oracle WebLogic Server 15.1.1, started with derby enabled

---

## Model configuration

Configuration is centralized in `app/config/llm-config.properties` and loaded at runtime via the system property `llmconfigfile`.

Two options are pre-wired:

- OpenAI demo (commented out by default)
  - base-url: `http://langchain4j.dev/demo/openai/v1`
  - model-name: `gpt-4o-mini`
  - api-key: `demo`
  - Note: The demo key is free for demonstration purposes, has a quota and is restricted to the `gpt-4o-mini` model. Please see “What if I don't have an API key?”:
    https://github.com/langchain4j/langchain4j/blob/main/docs/docs/integrations/language-models/open-ai.md#api-key

- Ollama (enabled by default)
  - class: `dev.langchain4j.model.ollama.OllamaChatModel`
  - base-url: `http://localhost:11434`
  - model-name: `llama3.1:latest`

Assumptions for Ollama
- Ollama is running locally (`http://localhost:11434`).
- The model is available locally:
  ```
  ollama pull llama3.1:latest
  ollama serve
  ```

To switch providers, comment/uncomment the appropriate section in `app/config/llm-config.properties` and restart the application.

---

## RAG data

- Sample documents for RAG are located under `app/docs-for-rag/`.
- At application startup, documents are ingested into an in-memory embedding store (`DocRagIngestor`) using the `AllMiniLmL6V2EmbeddingModel`. This data is not persisted across restarts.

Required system properties (set via `JAVA_OPTIONS`) before starting WebLogic:
- `llmconfigfile`: absolute path to `app/config/llm-config.properties`
- `docragdir`: absolute path to `app/docs-for-rag`

Example:
```
export JAVA_OPTIONS="-Dllmconfigfile=<repo>/app/config/llm-config.properties -Ddocragdir=<repo>/app/docs-for-rag"
```

---

## Database and DataSource Setup using embedded Derby Database with WebLogic

This sample uses a container managed DataSource to access Apache Derby in embedded mode. The JNDI name is `jdbc/CarBookingDS`.

Summary
- JNDI: `jdbc/CarBookingDS`
- Driver: `org.apache.derby.jdbc.EmbeddedDriver`
- URL (Embedded): `jdbc:derby:/absolute/path/to/milesofsmiles;create=true`
- Recommended pool test table: `SYS.SYSTABLES`

### Initialization order

### Step 1: Choose a database location
Select a writable absolute path on the host and update the path in `scripts/sql/derby-init.sql`, for example:
```
/u01/oracle/user_projects/milesofsmiles
```

### Step 2: Initialize schema and seed data
- Run `derby-init.sql` from `scripts/sql`
  ```
  rm -rf /absolute/path/to/milesofsmiles
  $JAVA_HOME/bin/java -cp "$DERBY_HOME/lib/derbytools.jar:$DERBY_HOME/lib/derby.jar" \
    org.apache.derby.tools.ij scripts/sql/derby-init.sql
  ```

### Step 3: Create the WebLogic JDBC Data Source (WLST)
Run the WLST script while the WebLogic server is running:
```
${WL_HOME}/common/bin/wlst.sh scripts/wlst/create-derby-ds.py \
  --adminUrl t3://<host>:<port> --user <user> --password <password> \
  --dsName CarBookingDS --jndi jdbc/CarBookingDS \
  --driver org.apache.derby.jdbc.EmbeddedDriver \
  --url jdbc:derby:/absolute/path/to/milesofsmiles;create=true \
  --target <WebLogic server or a cluster>
```
---

## Application Walkthrough

### BookingService.java
Implements booking retrieval and cancellation against the Derby database via the container managed DataSource (`jdbc/CarBookingDS`). It enforces the cancellation policy and normalizes input.

### ChatAiService.java 
Defines an AI service using annotation @RegisterAIService with a server-side chat memory and registered tools (including `BookingService`). The service orchestrates LLM interactions and delegates domain operations to tools.

### FraudAiService.java
Provides an additional tool illustrating how auxiliary domain checks (e.g., fraud detection) can be made available to the LLM in a controlled manner.

### CarBookingResource.java
Exposes two endpoints:
- `/chat` (text/plain): routes user messages to `ChatAiService`
- `/fraud` (application/json): exercises `FraudAiService`

---

## Architecture

```
+--------------------+        HTTP         +---------------------+
|  Browser / Client  |  --->  /chat,/fraud |  CarBookingResource |
+--------------------+                     +----------+----------+
                                                    |
                                                    v
                                          +---------+---------+
                                          |   ChatAiService   |  (LangChain4J CDI, memory)
                                          +---------+---------+
                                                    |
                    +-------------------------------+------------------------------+
                    |                                                              |
                    v                                                              v
          +---------+----------+                                       +----------+-----------+
          |   BookingService   |                                       |   FraudAiService     |
          |  (DataSource/JDBC) |                                       | (fraud tool logic)   |
          +---------+----------+                                       +----------+-----------+
                    |
                    v
          +---------+----------+
          |  JDBC DataSource   |  (jdbc/CarBookingDS)
          +---------+----------+
                    |
                    v
          +---------+----------+                         +------------------------+
          |  Derby Embedded    |                         |   LLM Provider(s)      |
          +--------------------+                         | (Ollama / OpenAI demo) |
                                                         +------------------------+
```

- Conversation memory is maintained on the server in `ChatAiService`.
- Tools expose domain operations for the LLM to invoke.
- Local documents are embedded at startup and retrieved during chat (RAG).

---

## Build and Deploy

Build all modules:
```
mvn -U clean package
```

Deploy the WAR (from `app/target/`) to WebLogic:
```
$JAVA_HOME/bin/java -cp $WL_HOME/server/lib/weblogic.jar weblogic.Deployer \
  -adminurl t3://<admin host>:<admin port> \
  -username <user> -password <password> \
  -deploy -name weblogic-car-booking -targets <WebLogic server or cluster> <path to .war file>
```

You may also deploy using the tool of your choice like admin console.

---

## Chat Client

A console client is provided in `client/`:

Build:
```
mvn -pl client -DskipTests package
```

Run (default endpoint is `http://localhost:7001/car-booking/api/car-booking/chat`):
```
java -cp client/target/classes \
  dev.langchain4j.cdi.example.booking.client.ChatClient \
  http://<host>:<port>/car-booking/api/car-booking/chat
```

- Enter messages and press Enter
- Type `/quit` (or an empty line) to exit
- The server retains conversation context via chat memory in `ChatAiService`

The chat can also be accessed via curl:
  ```
  curl -X 'GET' 'http://<host>:<port>/car-booking/api/car-booking/fraud?name=James&surname=Bond' -H 'accept: application/json'
  ```

---

## Helper script

A helper script is provided at `demo.sh` with the following commands:
- `build`: build all modules
- `deploy`: deploy the application to WebLogic
- `undeploy`: undeploy the application from WebLogic
- `chat`: invoke the `/chat` endpoint with a sample prompt
- `fraud`: invoke the `/fraud` endpoint with sample parameters

Required environment variables:
- `JAVA_HOME`, `WL_HOME`, `ADMIN_PASSWORD`

Optional overrides:
- `WL_HOST` (default `localhost`)
- `ADMIN_PORT` (default `7001`)
- `ADMIN_USER` (default `weblogic`)
- `ADMIN_URL` (default `t3://localhost:7001`)
- `SERVER_NAME` (default `AdminServer`)

Review `demo.sh` for details and adapt it to your environment.

---

## Troubleshooting

- For Derby embedded, only one JVM may hold the database at a time. Stop WebLogic before running `ij`, or run maintenance via a DataSource-aware process to avoid locks.
- Use `SYS.SYSTABLES` as the WebLogic pool test table for Derby.
- For LLM connectivity:
  - Ensure Ollama is running locally and the model is pulled, if you are using the app/config/llm-config.properties
  - Provide a valid OpenAI key and verify the configured base URL, if you want to use OpenAi chat model

---

## Applicability

This sample separates concerns clearly:
- REST (JAX-RS) for transport
- AI orchestration for LLM prompting, memory, and tool invocation
- Domain services for data access and policy enforcement

The design is intended to be used as a foundation for real-world Jakarta EE systems that integrate LLM capabilities and RAG on WebLogic.
