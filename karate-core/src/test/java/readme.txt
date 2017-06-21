mvn versions:set -DnewVersion=0.4.0-SNAPSHOT
(edit archetype karate-core version)
(edit README.md maven 3 places)
mvn versions:commit
mvn clean deploy -P release



