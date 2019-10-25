#!/bin/bash
set -x -e
cd ../..
docker run -it --rm -v "$(pwd)":/karate -w /karate -v "$(pwd)"/karate-docker/karate-chrome/target:/root/.m2 maven:3-jdk-8 bash karate-docker/karate-chrome/install.sh
cd karate-docker/karate-chrome
docker build -t karate-chrome:build . -f Dockerfile.build

CHROME_VERSION=`docker run --rm -it karate-chrome:build /usr/bin/google-chrome --version | cut -d' ' -f3`

docker build --build-arg chrome_version=${CHROME_VERSION} -t karate-chrome:latest . -f Dockerfile
