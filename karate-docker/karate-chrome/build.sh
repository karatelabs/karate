#!/bin/bash
set -x -e
REPO_DIR=$PWD/target/repository
mvn -f ../../pom.xml clean install -DskipTests -P pre-release -Dmaven.repo.local=$REPO_DIR
mvn -f ../../karate-netty/pom.xml install -DskipTests -P fatjar -Dmaven.repo.local=$REPO_DIR
mvn -f ../../karate-example/pom.xml dependency:resolve test-compile exec:java -Dexec.mainClass=common.Main -Dexec.classpathScope=test -Dmaven.repo.local=$REPO_DIR
KARATE_VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout -f ../../pom.xml)
cp ../../karate-netty/target/karate-${KARATE_VERSION}.jar target/karate.jar
docker build -t karate-chrome .
