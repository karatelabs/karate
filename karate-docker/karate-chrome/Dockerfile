FROM maven:3-jdk-8

LABEL maintainer="Peter Thomas"
LABEL url="https://github.com/intuit/karate/tree/master/karate-docker/karate-chrome"

RUN apt-get update \
	&& apt-get install -y --no-install-recommends \
	xvfb \
	x11vnc \
	supervisor \
	gdebi \
	gnupg2 \
	fonts-takao \
	pulseaudio \
	socat \
	ffmpeg

ADD https://dl.google.com/linux/linux_signing_key.pub \
	https://dl.google.com/linux/direct/google-chrome-stable_current_amd64.deb \
	/tmp/

RUN apt-key add /tmp/linux_signing_key.pub \
	&& gdebi --non-interactive /tmp/google-chrome-stable_current_amd64.deb

RUN apt-get clean \
	&& rm -rf /var/cache/* /var/log/apt/* /var/lib/apt/lists/* /tmp/* \
	&& useradd -m -G pulse-access chrome \
	&& usermod -s /bin/bash chrome

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
