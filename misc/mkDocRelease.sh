#!/bin/bash

# creates jtcl documentationdistribution after building with "mvn -DskipTests clean package site"

version=$1

if [ ! -f  ../target/jtcl-$version.jar ] ; then
    echo "missing ../target/jtcl-$version.jar"
    exit
fi
if [ ! -d  ../target/site ] ; then
    echo "missing ../target/site"
    exit
fi

cd ../target/site
zip -r ../jtcl-${version}-doc.zip *
cd ..
ls -alt `pwd`/jtcl-${version}-doc.zip
