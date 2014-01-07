# Setup repository for Oracle JDK
echo Setup Oracle JDK repository ...
apt-get update
apt-get -y install python-software-properties
add-apt-repository ppa:webupd8team/java
apt-get update

# Accept license and install Oracle JDK
echo Installing Oracle JDK 7 ...
echo debconf shared/accepted-oracle-license-v1-1 select true | debconf-set-selections
echo debconf shared/accepted-oracle-license-v1-1 seen true | debconf-set-selections
apt-get -y install oracle-java7-installer

# Install Apache Maven
echo Installing Apache Maven ...
apt-get -y install maven

# Install git
echo Installing git ...
apt-get -y install git

# Clone jtcl repo
echo Cloning jtcl git repository ...

git clone https://github.com/jtcl-project/jtcl.git
chown -R vagrant:vagrant jtcl/