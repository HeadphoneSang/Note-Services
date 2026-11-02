FROM openjdk:1.7
COPY target/gateway-module-1.0-SNAPSHOT.jar /execs/gateway.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/execs/gateway.jar"]