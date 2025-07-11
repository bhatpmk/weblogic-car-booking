#!/bin/bash

set -e
BASE_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

if ! command -v mvn >/dev/null 2>&1; then
  echo "Maven is not available in PATH."
  exit 1
fi

JAVA_HOME="${JAVA_HOME}"
WL_HOME="${WL_HOME}"
WL_HOST="${WL_HOST:-"localhost"}"
ADMIN_PORT="${ADMIN_PORT:-"7001"}"
ADMIN_URL="${ADMIN_URL:-"t3://${WL_HOST}:${ADMIN_PORT}"}"
ADMIN_USER="${ADMIN_USER:-"weblogic"}"
ADMIN_PASSWORD="${ADMIN_PASSWORD}"
SERVER_NAME="${SERVER_NAME:-"AdminServer"}"

if [ -z "${JAVA_HOME}" ]; then
    echo "Environment variable JAVA_HOME not set"
    exit 1
fi

if [ -z "${WL_HOME}" ]; then
    echo "Environment variable WL_HOME not set"
    exit 1
fi

langchain4j_cdi() {
  if [ -z "${LANGCHAIN4J_CDI_EXTN_ROOT}" ]; then
      echo "Environment variable LANGCHAIN4J_CDI_EXTN_ROOT pointing to an existing directory in a local machine, to clone the https://github.com/langchain4j/langchain4j-cdi.git is required."
      exit 1
  fi

  # This function assumes the user has necessary git settings to clone a repository
  echo "git clone https://github.com/langchain4j/langchain4j-cdi.git under $LANGCHAIN4J_CDI_EXTN_ROOT"
  cd $LANGCHAIN4J_CDI_EXTN_ROOT
  git clone https://github.com/langchain4j/langchain4j-cdi.git
  cd langchain4j-cdi
  git checkout main
  mvn clean install -DskipTests
  cd $BASE_DIR
}

demo() {
    echo "Building weblogic-car-booking"
    cd $BASE_DIR
    mvn clean package
}

undeploy() {
  if [ -z "${ADMIN_PASSWORD}" ]; then
    echo "Environment variable ADMIN_PASSWORD not set"
    exit 1
  fi
  echo "Undeploying weblogic-car-booking..."
  cd $BASE_DIR
  $JAVA_HOME/bin/java -cp $WL_HOME/server/lib/weblogic.jar weblogic.Deployer -adminurl $ADMIN_URL  -username $ADMIN_USER  -password $ADMIN_PASSWORD -undeploy -name weblogic-car-booking -targets $SERVER_NAME
}

deploy() {
  if [ -z "${ADMIN_PASSWORD}" ]; then
    echo "Environment variable ADMIN_PASSWORD not set"
    exit 1
  fi
  echo "Deploying weblogic-car-booking..."
  cd $BASE_DIR
  $JAVA_HOME/bin/java -cp $WL_HOME/server/lib/weblogic.jar weblogic.Deployer -adminurl $ADMIN_URL  -username $ADMIN_USER  -password $ADMIN_PASSWORD -deploy -name weblogic-car-booking -targets $SERVER_NAME $BASE_DIR/weblogic-car-booking/target/weblogic-car-booking.war
}

test() {
  # Sample prompt to /chat endpoint
  sample_prompt="Hello, how can you help me?"
  # sample_prompt="What is your list of cars?"
  # sample_prompt="What is your cancellation policy?"
  # sample_prompt="What is your fleet size? Be short please."
  # sample_prompt="How many electric cars do you have?"
  # sample_prompt="My name is James Bond, please list my bookings"
  # sample_prompt="Is my booking 123-456 cancelable?"
  # sample_prompt="Is my booking 234-567 cancelable?"
  # sample_prompt="Can you check the duration please?"
  # sample_prompt="I'm James Bond, can I cancel all my booking 345-678?"
  # sample_prompt="Can you provide the details of all my bookings?"
  # sample_prompt="fraud James Bond"
  # sample_prompt="fraud Emilio Largo"
  echo "Testing sample /chat endpoint, with the prompt - $sample_prompt"

  encoded_prompt=$(jq -rn --arg q "$sample_prompt" '$q | @uri')
  url="http://${WL_HOST}:${ADMIN_PORT}/weblogic-car-booking/api/car-booking/chat?question=${encoded_prompt}"\

  echo "URL $url"
  curl -X 'GET' $url -H 'accept: text/plain'
  echo -e "\n"
}

if [ $# -eq 0 ]; then
  echo "Usage: $0 {langchain4j_cdi|demo|undeploy|deploy|test}"
  exit 1
fi

# Read the first parameter
COMMAND=$1

# Dispatch to the appropriate function
case "$COMMAND" in
  langchain4j_cdi)
    langchain4j_cdi
    ;;
  demo)
    demo
    ;;
  undeploy)
    undeploy
    ;;
  deploy)
    deploy
    ;;
  test)
    test
    ;;
  *)
    echo "Invalid command, usage: $0 {langchain4j_cdi|demo|undeploy|deploy|test}"
    exit 1
    ;;
esac
