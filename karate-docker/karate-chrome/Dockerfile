FROM justinribeiro/chrome-headless

LABEL maintainer="Peter Thomas"
LABEL url="https://github.com/intuit/karate/tree/master/karate-docker/karate-chrome"

# refer https://hub.docker.com/r/justinribeiro/chrome-headless/ for how to get "chrome.json" or skip it
# docker run -d -p 9222:9222 -p 5900:5900 --security-opt seccomp=~/chrome.json ptrthomas/karate-chrome
# open vnc://localhost:5900
# docker cp <containerId>:/tmp target

USER root

RUN apt-get update && \
    apt-get install -y x11vnc xvfb socat supervisor libav-tools dbus-x11

RUN mkdir ~/.vnc && \
    x11vnc -storepasswd karate ~/.vnc/passwd

COPY supervisord.conf /etc/supervisor/conf.d/supervisord.conf
COPY karate-chrome.sh /
RUN chmod +x /karate-chrome.sh

EXPOSE 5900 9222

ENV DBUS_SESSION_BUS_ADDRESS=unix:path=/dev/null

ENTRYPOINT ["/karate-chrome.sh"]