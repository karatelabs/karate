#!/bin/bash
set -x -e

# this moves karate binaries to karate-docker/karate-chrome/target
docker run --rm -v "$(pwd)":/karate -w /karate -v "$HOME/.m2":/root/.m2 maven:3-jdk-8 bash karate-docker/karate-chrome/install.sh

# build karate-chrome docker image that includes karate fatjar + maven jars for convenience
docker build -t karate-chrome karate-docker/karate-chrome

# just in case a previous run had hung (likely only in local dev)
docker stop karate || true

# note that this command is run as a background process
docker run --name karate --rm --cap-add=SYS_ADMIN -v "$PWD":/karate -v "$HOME/.m2":/root/.m2 karate-chrome &

# just ensure that the docker container named "karate" exists after the above command
# it does not have to have completed startup, the command / karate test below will wait
sleep 5

# run tests against chrome
docker exec -w /karate karate mvn test -f karate-e2e-tests/pom.xml -Dtest=driver.DockerRunner
docker stop karate
wait
