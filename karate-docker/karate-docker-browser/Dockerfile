FROM karatelabs/karate-browser AS karate-browser

RUN wget -q -O - https://dl-ssl.google.com/linux/linux_signing_key.pub | apt-key add - \
  && echo "deb http://dl.google.com/linux/chrome/deb/ stable main" >> /etc/apt/sources.list.d/google-chrome.list \
  && apt-get update \
  && apt-get install -y --no-install-recommends \
    google-chrome-stable

RUN useradd chrome --shell /bin/bash --create-home \
  && usermod -a -G sudo chrome \
  && echo 'ALL ALL = (ALL) NOPASSWD: ALL' >> /etc/sudoers \
  && echo 'chrome:karate' | chpasswd

EXPOSE 9222

CMD ["/bin/bash", "/entrypoint.sh"]
