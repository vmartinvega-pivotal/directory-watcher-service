
FROM java:8
WORKDIR /opt
ADD target/directory-watcher-service-1.0-SNAPSHOT.jar /opt

CMD ["java", "-jar", "/opt/directory-watcher-service-1.0-SNAPSHOT.jar"]
