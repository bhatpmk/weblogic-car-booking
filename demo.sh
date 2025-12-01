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

build() {
    echo "Building car-booking"
    cd $BASE_DIR
    mvn -U clean package
}

undeploy() {
  if [ -z "${ADMIN_PASSWORD}" ]; then
    echo "Environment variable ADMIN_PASSWORD not set"
    exit 1
  fi
  echo "Undeploying car-booking..."
  cd $BASE_DIR
  $JAVA_HOME/bin/java -cp $WL_HOME/server/lib/weblogic.jar weblogic.Deployer -adminurl $ADMIN_URL  -username $ADMIN_USER  -password $ADMIN_PASSWORD -undeploy -name car-booking -targets $SERVER_NAME
}

deploy() {
  if [ -z "${ADMIN_PASSWORD}" ]; then
    echo "Environment variable ADMIN_PASSWORD not set"
    exit 1
  fi
  echo "Deploying car-booking..."
  cd $BASE_DIR
  $JAVA_HOME/bin/java -cp $WL_HOME/server/lib/weblogic.jar weblogic.Deployer -adminurl $ADMIN_URL  -username $ADMIN_USER  -password $ADMIN_PASSWORD -deploy -name car-booking -targets $SERVER_NAME $BASE_DIR/app/target/car-booking.war
}

chat() {
  cd $BASE_DIR
  $JAVA_HOME/bin/java -cp $BASE_DIR/client/target/classes dev.langchain4j.cdi.example.booking.client.ChatClient "http://localhost:7001/car-booking/api/car-booking/chat"
}

sanity() {
  sample_prompt="Hello, how can you help me?"
  echo "Testing sample /chat endpoint, with the prompt - $sample_prompt"
  encoded_prompt=$(jq -rn --arg q "$sample_prompt" '$q | @uri')
  url="http://${WL_HOST}:${ADMIN_PORT}/car-booking/api/car-booking/chat?question=${encoded_prompt}"

  echo "URL $url"
  curl -X 'GET' $url -H 'accept: text/plain'
  echo -e "\n"
}

fraud() {
  # You can ask fraud for users James Bond and Emilio Largo
  test_user="name=James&surname=Bond"
  #test_user="name=Largo&surname=Emilio"
  echo "Testing sample /fraud endpoint, for the user - $test_user"
  url="http://${WL_HOST}:${ADMIN_PORT}/car-booking/api/car-booking/fraud?${test_user}"
  curl -X 'GET' $url -H 'accept: application/json'
  echo -e "\n"
}

if [ $# -eq 0 ]; then
  echo "Usage: $0 {build|deploy|undeploy|chat|fraud|curl}"
  exit 1
fi

# Read the first parameter
COMMAND=$1

# Call the appropriate function
case "$COMMAND" in
  build)
    build
    ;;
  undeploy)
    undeploy
    ;;
  deploy)
    deploy
    ;;
  chat)
    chat
    ;;
  fraud)
    fraud
    ;;
  sanity)
    sanity
    ;;
  *)
    echo "Invalid command, usage: $0 {build|deploy|undeploy|chat|fraud|sanity}"
    exit 1
    ;;
esac
