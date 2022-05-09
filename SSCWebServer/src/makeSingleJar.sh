PWD=`dirname $0`
JAVA=/usr/local/jdk1.8.0_181/bin/java

LIB=/home/jbf/ct/hapi/git/server-java/SSCWebServer/lib

string=$LIB/jettison-1.4.1.jar:$LIB/aopalliance-repackaged-2.6.1.jar:$LIB/hk2-api-2.6.1.jar:$LIB/hk2-locator-2.6.1.jar:$LIB/hk2-utils-2.6.1.jar:$LIB/jakarta.activation-api-1.2.2.jar:$LIB/jakarta.annotation-api-1.3.5.jar:$LIB/jakarta.inject-2.6.1.jar:$LIB/jakarta.json-1.1.6-module.jar:$LIB/jakarta.json-1.1.6.jar:$LIB/jakarta.json-api-1.1.6.jar:$LIB/jakarta.json.bind-api-1.0.2.jar:$LIB/jakarta.persistence-api-2.2.3.jar:$LIB/jakarta.servlet-api-4.0.3.jar:$LIB/jakarta.validation-api-2.0.2.jar:$LIB/jakarta.ws.rs-api-2.1.6-sources.jar:$LIB/jakarta.xml.bind-api-2.3.3.jar:$LIB/javassist-3.25.0-GA.jar:$LIB/org.osgi.core-6.0.0.jar:$LIB/osgi-resource-locator-1.0.3.jar:$LIB/yasson-1.0.9.jar:$LIB/jakarta.ws.rs-api-2.1.6.jar:$LIB/jersey-client.jar:$LIB/jersey-common.jar:$LIB/jersey-container-servlet-core.jar:$LIB/jersey-container-servlet.jar:$LIB/jersey-hk2.jar:$LIB/jersey-media-jaxb.jar:$LIB/jersey-media-json-binding.jar:$LIB/jersey-media-sse.jar:$LIB/jersey-server.jar:$LIB/sscWebServices-jaxb.jar:$LIB/sscWebServicesJaxRs-client.jar

IFS=':' read -r -a array <<< "$string"

mkdir -p /home/jbf/ct/hapi/git/server-java/SSCWebServer/classes
cd /home/jbf/ct/hapi/git/server-java/SSCWebServer/classes

for element in "${array[@]}"; do
   echo $element
   jar xf $element
done

rsync -a /home/jbf/ct/hapi/git/server-java/SSCWebServer/build/classes/ ./

jar cvf ../store/SSCWebReader.jar *
