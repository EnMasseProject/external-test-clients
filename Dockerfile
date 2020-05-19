FROM registry.redhat.io/ubi8/openjdk-11

ARG version
ARG commit
ARG maven_version
ENV VERSION=${version} COMMIT=${commit} MAVEN_VERSION=${maven_version}

ADD target/api-client.jar /
ADD target/probe-client.jar /
ADD target/messaging-client.jar /
