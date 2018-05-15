#############
# Build tcnative-alpine
#############
FROM openjdk:8-jdk-alpine AS tcnative-builder

RUN apk add --update \
        linux-headers build-base autoconf automake libtool apr-util apr-util-dev git cmake ninja go

ENV NETTY_TCNATIVE_TAG netty-tcnative-parent-2.0.8.Final
ENV MAVEN_VERSION 3.3.9
ENV MAVEN_HOME /usr/share/maven

# Install mvn
WORKDIR /usr/share
RUN wget http://archive.apache.org/dist/maven/maven-3/$MAVEN_VERSION/binaries/apache-maven-$MAVEN_VERSION-bin.tar.gz -O - | tar xzf - 
RUN mv /usr/share/apache-maven-$MAVEN_VERSION /usr/share/maven 
RUN ln -s /usr/share/maven/bin/mvn /usr/bin/mvn

# Build TC native
RUN git clone https://github.com/netty/netty-tcnative
WORKDIR netty-tcnative
RUN git checkout tags/$NETTY_TCNATIVE_TAG
RUN mvn clean install

# Build Bot
WORKDIR /
RUN mkdir bot
COPY src/ /bot/src/
COPY pom.xml /bot/
WORKDIR /bot
RUN mvn clean package

################
# Final dockerfile
################
FROM openjdk:8-jdk-alpine
RUN apk add --update libuuid
RUN adduser -D -h /home/bot -s /bin/sh bot
USER bot

COPY --from=tcnative-builder /bot/target/libs /libs
COPY --from=tcnative-builder /bot/target/original-bot.jar /libs/
COPY --from=tcnative-builder /usr/share/netty-tcnative/boringssl-static/target/netty-tcnative-boringssl-static-*-linux-x86_64.jar /libs/

ENTRYPOINT [ "java", "-cp", "/libs/*", "fr.liksi.bot.Console" ]


