#!/bin/bash
set -x -e

# assumes a maven build has completed so the jar files are in place
# mvn clean verify -DskipTests -Djavacpp.platform=linux-x86_64

KARATE_VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
MVN_INSTALL=org.apache.maven.plugins:maven-install-plugin:3.0.0-M1:install-file
MVN_REPO=karate-docker/karate-chrome/target/repository

for MODULE in core junit4 junit5 gatling mock-servlet
do
  mvn ${MVN_INSTALL} -Dfile=karate-${MODULE}/target/karate-${MODULE}-${KARATE_VERSION}.jar -DlocalRepositoryPath=${MVN_REPO}
done

mvn -f karate-core/pom.xml package -DskipTests -P fatjar
cp karate-core/target/karate-${KARATE_VERSION}.jar karate-docker/karate-chrome/target/karate.jar
