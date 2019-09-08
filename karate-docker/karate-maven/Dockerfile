FROM maven:3-jdk-8

LABEL maintainer="Peter Thomas"
LABEL url="https://github.com/intuit/karate/tree/master/karate-docker/karate-maven"

ADD target/karate.jar /opt/karate/karate.jar
ADD target/repository /root/.m2/repository

COPY entrypoint.sh /
RUN chmod +x /entrypoint.sh

ENTRYPOINT ["/bin/bash", "/entrypoint.sh"]
