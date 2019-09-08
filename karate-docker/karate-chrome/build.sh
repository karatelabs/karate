#!/bin/bash
set -x -e

BASE_DIR=$PWD
REPO_DIR=$BASE_DIR/target/repository

cd ../..
mvn clean install -DskipTests -P pre-release -Dmaven.repo.local=$REPO_DIR
cd karate-netty
mvn install -DskipTests -P fatjar -Dmaven.repo.local=$REPO_DIR
cp target/karate-1.0.0.jar $BASE_DIR/target/karate.jar
cd $BASE_DIR
docker build -t karate-chrome .
