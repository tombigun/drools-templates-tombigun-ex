#!/usr/bin/env bash
mvn clean package -Dmaven.test.skip=true
cd target/
mv drools-templates-tombigun-ex-6.5.0-SNAPSHOT-jar-with-dependencies.jar drools-templates-tombigun-ex-6.5.0-SNAPSHOT.jar

#mvn install:install-file -Dfile=jar包的位置 -DgroupId=上面的groupId -DartifactId=上面的artifactId -Dversion=上面的version -Dpackaging=jar
mvn install:install-file -Dfile=drools-templates-tombigun-ex-6.5.0-SNAPSHOT.jar -DgroupId=tombigun -DartifactId=drools-templates-tombigun-ex -Dversion=6.5.0-SNAPSHOT -Dpackaging=jar
