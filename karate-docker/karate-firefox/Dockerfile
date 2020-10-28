FROM maven:3-jdk-8

LABEL maintainer="Peter Thomas"
LABEL url="https://github.com/intuit/karate/tree/master/karate-docker/karate-firefox"

#RUN echo uname -a
#
#RUN echo "deb http://downloads.sourceforge.net/project/ubuntuzilla/mozilla/apt all main" | tee -a /etc/apt/sources.list > /dev/null \
#    && apt-key adv --recv-keys --keyserver keyserver.ubuntu.com B7B9C16F2667CA5C \
#    && apt-get update \
#    && apt-get install firefox-mozilla-build

RUN apt-get update \
    && apt-get install -y firefox-esr

# GeckoDriver v0.19.1
RUN wget -q "https://github.com/mozilla/geckodriver/releases/download/v0.27.0/geckodriver-v0.27.0-linux64.tar.gz" -O /tmp/geckodriver.tgz \
    && tar zxf /tmp/geckodriver.tgz -C /usr/local/bin/ \
    && rm /tmp/geckodriver.tgz


RUN useradd firefox --shell /bin/bash --create-home \
  && usermod -a -G sudo firefox \
  && echo 'ALL ALL = (ALL) NOPASSWD: ALL' >> /etc/sudoers \
  && echo 'firefox:karate' | chpasswd

RUN apt-get install -y --no-install-recommends \
  xvfb \
  x11vnc \
  xterm \
  fluxbox \
  wmctrl \
  supervisor \
  socat \
  ffmpeg \
  locales \
  locales-all

ENV LANG en_US.UTF-8

RUN apt-get clean \
  && rm -rf /var/cache/* /var/log/apt/* /var/lib/apt/lists/* /tmp/* \
  && mkdir ~/.vnc \
  && x11vnc -storepasswd karate ~/.vnc/passwd \
  && locale-gen ${LANG} \
  && dpkg-reconfigure --frontend noninteractive locales \
  && update-locale LANG=${LANG}

COPY supervisord.conf /etc
COPY entrypoint.sh /
RUN chmod +x /entrypoint.sh

EXPOSE 5900 4444

ADD target/karate.jar /
ADD target/repository /root/.m2/repository

CMD ["/bin/bash","/entrypoint.sh"]
