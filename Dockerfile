FROM tomcat:8.5-jre8
RUN rm -fr /usr/local/tomcat/webapps/ROOT
COPY out/artifacts/web_war/vote.war /usr/local/tomcat/webapps/ROOT.war
