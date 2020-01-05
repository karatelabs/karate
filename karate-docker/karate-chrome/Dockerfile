FROM maven:3-jdk-8

LABEL maintainer="Peter Thomas"
LABEL url="https://github.com/intuit/karate/tree/master/karate-docker/karate-chrome"

RUN wget -q -O - https://dl-ssl.google.com/linux/linux_signing_key.pub | apt-key add - \
  && echo "deb http://dl.google.com/linux/chrome/deb/ stable main" >> /etc/apt/sources.list.d/google-chrome.list \
  && apt-get update \
  && apt-get install -y --no-install-recommends \
    google-chrome-stable

RUN useradd chrome --shell /bin/bash --create-home \
  && usermod -a -G sudo chrome \
  && echo 'ALL ALL = (ALL) NOPASSWD: ALL' >> /etc/sudoers \
  && echo 'chrome:karate' | chpasswd

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

EXPOSE 5900 9222

ADD target/karate.jar /
ADD target/repository /root/.m2/repository

CMD ["/bin/bash", "/entrypoint.sh"]
