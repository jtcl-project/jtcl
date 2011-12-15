#!
cd `dirname $0`
cd ..
mvn -DskipTests clean javadoc:javadoc site package
