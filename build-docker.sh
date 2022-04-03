#!/bin/bash
set -x -e

# assume that karate jars are installed in maven local repo
# mvn clean install -P pre-release -DskipTests

KARATE_VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)

# run e2e test that depends on karate-gatling
mvn versions:set versions:commit -B -ntp -DnewVersion=${KARATE_VERSION} -f examples/gatling/pom.xml
mvn clean test -B -ntp -f examples/gatling/pom.xml

# copy only karate jars to a place where the docker image build can add from
KARATE_REPO=karate-docker/karate-chrome/target/repository/com/intuit
mkdir -p ${KARATE_REPO}
cp -r ~/.m2/repository/com/intuit/karate ${KARATE_REPO}

# create / copy the karate fatjar so that the docker image build can add it
mvn package -B -ntp -P fatjar -DskipTests -f karate-core/pom.xml
cp karate-core/target/karate-${KARATE_VERSION}.jar karate-docker/karate-chrome/target/karate.jar

# hack for apple silicon
# export DOCKER_DEFAULT_PLATFORM=linux/amd64

# build karate-chrome docker image that includes karate fatjar + maven jars for convenience
docker build -t karate-chrome karate-docker/karate-chrome

# just in case a previous run had hung (likely only in local dev)
docker stop karate || true

# note that this command is run as a background process
docker run --name karate --rm --cap-add=SYS_ADMIN -v "$PWD":/karate -v "$HOME"/.m2:/root/.m2 karate-chrome &

# just ensure that the docker container named "karate" exists after the above command
# it does not have to have completed startup, the command / karate test below will wait
sleep 5

# run a test to check a particular jar packaging issue
docker exec -w /karate karate mvn test -B -ntp -f karate-e2e-tests/pom.xml -Dtest=regex.RegexRunner

# run tests against chrome
docker exec -w /karate karate mvn test -B -ntp -f karate-e2e-tests/pom.xml -Dtest=driver.DockerRunner

docker stop karate
wait
