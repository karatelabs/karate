mvn versions:set -DnewVersion=0.7.0
(edit archetype karate-core version)
(edit README.md maven 5 places)
mvn versions:commit
mvn clean deploy -P release



