#FROM openjdk:8-jre
FROM BASE_IMAGE
MAINTAINER walrus <tech@walrus.info>

ARG module_name
ENV DOCKERIZE_VERSION v0.6.0

RUN cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && echo "Asia/Shanghai" >/etc/timezone

#WORKDIR /$project
ADD ${module_name}.jar /
ADD entrypoint.sh /
ENV module=${module_name}
ENTRYPOINT ["sh","/entrypoint.sh"]