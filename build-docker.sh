#!/bin/bash
set -x -e

docker run -it --rm -v "$(pwd)":/karate -w /karate -v "$HOME/.m2":/root/.m2 maven:3-jdk-8 bash karate-docker/karate-chrome/install.sh

docker build -t karate-chrome karate-docker/karate-chrome

docker stop karate || true
docker run --name karate --rm --cap-add=SYS_ADMIN -v "$PWD":/src -v "$HOME/.m2":/root/.m2 karate-chrome &

# just ensure that the docker container named "karate" exists after the above command
# it does not have to have completed startup, the command / karate test below will wait
sleep 5

docker exec -it -w /src karate mvn test -f karate-core/pom.xml -Dtest=driver.DockerRunner
docker stop karate
wait
