#!/bin/bash
set -x -e
cd ../..
docker run -it --rm -v "$(pwd)":/karate -w /karate -v "$(pwd)"/karate-docker/karate-firefox/target:/root/.m2 maven:3-jdk-8 bash karate-docker/karate-firefox/install.sh
cd karate-docker/karate-firefox
docker build -t karate-firefox .
