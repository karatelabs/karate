dev:
mvn versions:set -DnewVersion=2.0.0
mvn versions:commit
(edit examples/jobserver/pom.xml)
(edit examples/gatling/pom.xml)

main:
mvn versions:set -DnewVersion=@@@
(edit archetype karate.version)
(edit README.md maven 5 places)

(edit examples/gatling/build.gradle)
(edit examples/jobserver/build.gradle)
(edit examples/*/pom.xml)
mvn versions:commit
mvn clean deploy -P pre-release,release

jar:
cd karate-netty
mvn install -P fatjar
https://bintray.com/ptrthomas/karate
(upload to github release notes)

robot:
cd karate-robot
mvn install -P fatjar
https://bintray.com/ptrthomas/karate
(upload to github release notes)

docker:
(double check if the below pom files are updated for the version
(edit examples/jobserver/pom.xml)
(edit examples/gatling/pom.xml)
make sure docker is started and is running !
cd karate-docker/karate-chrome
rm -rf target
./build.sh
docker tag karate-chrome ptrthomas/karate-chrome:latest

(run WebDockerJobRunner and Test03DockerRunner to test that docker chrome is ok locally)

docker tag karate-chrome ptrthomas/karate-chrome:@@@
docker push ptrthomas/karate-chrome
