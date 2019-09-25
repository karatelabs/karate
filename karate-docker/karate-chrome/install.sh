#!/bin/bash
set -x -e
mvn clean install -DskipTests -P pre-release
KARATE_VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
mvn -f karate-netty/pom.xml install -DskipTests -P fatjar
cp karate-netty/target/karate-${KARATE_VERSION}.jar /root/.m2/karate.jar
mvn -f karate-example/pom.xml dependency:resolve test-compile exec:java -Dexec.mainClass=common.Main -Dexec.classpathScope=test

