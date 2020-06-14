#!/bin/sh
# java应用通用启动脚本
# moduel: java应用模块名

LOG_DIR="/log/${module}"
JAR_FILE="${module}.jar"

mkdir -p ${LOG_DIR} && touch ${LOG_DIR}/gc.log

exec java ${JAVA_OPTS} -Djava.security.egd=file:/dev/./urandom -jar ${JAR_FILE} "${@}"