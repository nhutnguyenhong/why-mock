FROM container-registry.oracle.com/java/serverjre:8

EXPOSE 5868

ENV JAVA_OPTS=""

ADD ./target/whymock-1.0-SNAPSHOT.jar app.jar
RUN mkdir "libjar"
COPY ./target/libjar/* libjar/
RUN sh -c 'touch /app.jar'

#copy content
RUN mkdir "wiremock"
COPY ./wiremock/ wiremock/
RUN ls -la /wiremock/*

ADD --chown=1000 ./entrypoint.sh /entrypoint.sh

RUN chmod u+x /entrypoint.sh


ENTRYPOINT [ "./entrypoint.sh" ]
