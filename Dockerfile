FROM jaohaohsuan/jnlp-slave:latest
MAINTAINER Henry Jao
WORKDIR /home/jenkins
ADD . ./
RUN /usr/local/bin/sbt -sbt-dir /tmp/.sbt/0.13.11 -sbt-boot /tmp/.sbt/boot -ivy /tmp/.ivy2 -sbt-launch-dir /tmp/.sbt/launchers 'compile' 'test'