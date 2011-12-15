#!/bin/bash

echo "deprecated - used 'release_build.sh' instead"
echo "^C to abort (or return to continue)"
read junk


# creates jtcl documentation distribution after 
# building with "mvn -DskipTests clean package javadoc:javadoc site"

if [ $# -ne 1 ] ; then
    echo usage: `basename $0` version
    exit
fi

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
