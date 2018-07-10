mvn versions:set -DnewVersion=0.7.0
(edit archetype karate.version)
(edit README.md maven 5 places)
mvn versions:commit
mvn clean deploy -P release

(release netty JAR)
cd karate-netty
mvn install -P shade


