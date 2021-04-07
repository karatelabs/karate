dev:
mvn versions:set -DnewVersion=2.0.0
mvn versions:commit

main:
mvn versions:set -DnewVersion=@@@
(edit archetype karate.version)
(edit README.md maven 3 places)

(edit examples/gatling/build.gradle)
(edit examples/jobserver/build.gradle)
(edit examples/*/pom.xml)
(edit jbang-catalog.json)
mvn versions:commit
mvn clean deploy -P pre-release,release

jar:
mvn clean package -P fatjar -f karate-core/pom.xml
(upload to github release notes)

robot:
mvn package -P fatjar -f karate-robot/pom.xml
(upload to github release notes)

docker:
make sure docker is started and is running !
rm -rf ~/.m2/repository/com/intuit/karate
rm -rf karate-docker/karate-chrome/target
mvn clean install -P pre-release -DskipTests
./build-docker.sh

docker tag karate-chrome ptrthomas/karate-chrome:latest
docker tag karate-chrome ptrthomas/karate-chrome:@@@

docker push ptrthomas/karate-chrome:@@@
