mvn versions:set -DnewVersion=1.0.0
(edit archetype karate.version)
(edit README.md maven 5 places)
mvn versions:commit
mvn clean deploy -P pre-release,release

(release netty JAR)
cd karate-netty
mvn install -P fatjar


