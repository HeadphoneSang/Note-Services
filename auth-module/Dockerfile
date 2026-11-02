FROM openjdk:1.7
COPY target/auth-module-1.0-SNAPSHOT.jar /execs/auth.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/execs/auth.jar"]