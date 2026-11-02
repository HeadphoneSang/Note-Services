FROM eclipse-temurin:17-jre
COPY target/gateway-module-1.0-SNAPSHOT.jar /execs/gateway.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/execs/gateway.jar"]