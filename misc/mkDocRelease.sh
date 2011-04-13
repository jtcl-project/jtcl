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

dir=/tmp/jtcl-$$.d
mkdir -p $dir/jtcl-$version/doc

cp -r ../target/site/* $dir/jtcl-$version/doc
cd $dir
zip -r ../jtcl-${version}-doc.zip *
cd ..
ls -alt `pwd`/jtcl-${version}-doc.zip

rm -rf $dir
