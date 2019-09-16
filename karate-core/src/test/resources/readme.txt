dev:
mvn versions:set -DnewVersion=1.0.0
mvn versions:commit
(edit karate-example/pom.xml)

main:
mvn versions:set -DnewVersion=@@@
(edit archetype karate.version)
(edit README.md maven 5 places)
(edit karate-gatling/build.gradle 1 place)
(edit karate-example/pom.xml 1 place)
mvn versions:commit
mvn clean deploy -P pre-release,release

jar:
cd karate-netty
mvn install -P fatjar
https://bintray.com/ptrthomas/karate

edit-wiki:
https://github.com/intuit/karate/wiki/ZIP-Release

docker:
(double check if karate-example/pom.xml is updated for the version
cd karate-docker/karate-chrome
rm -rf target
./build.sh
docker tag karate-chrome ptrthomas/karate-chrome:latest
docker tag karate-chrome ptrthomas/karate-chrome:@@@
docker push ptrthomas/karate-chrome

misc-examples:
update https://github.com/ptrthomas/karate-gatling-demo
update https://github.com/ptrthomas/payment-service
update https://github.com/ptrthomas/karate-sikulix-demo
