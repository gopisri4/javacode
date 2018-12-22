FROM tomcat:8.5-jre11
RUN rm -fr /usr/local/tomcat/webapps/ROOT
COPY target/voteapp-1.0-SNAPSHOT.war /usr/local/tomcat/webapps/ROOT.war
