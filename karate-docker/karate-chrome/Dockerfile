FROM karate-chrome:build

LABEL maintainer="Peter Thomas"
LABEL url="https://github.com/intuit/karate/tree/master/karate-docker/karate-chrome"

ARG chrome_version='N/A'

LABEL chrome.version=${chrome_version}

RUN mkdir ~/.vnc && \
    x11vnc -storepasswd karate ~/.vnc/passwd

COPY supervisord.conf /etc
COPY entrypoint.sh /
RUN chmod +x /entrypoint.sh

ADD target/karate.jar /
ADD target/repository /root/.m2/repository

VOLUME ["/home/chrome"]

EXPOSE 5900 9222

CMD ["/bin/bash", "/entrypoint.sh"]
