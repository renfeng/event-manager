# must use relative or absolute path. do not use ~
# adds variable M2_REPO=~/.m2/repository
# (the online doc is wrong about the variable name, MAVEN_REPO
mvn eclipse:configure-workspace -Declipse.workspace=/home/renfeng/workspace

# XXX does this work (M2_REPO) according to the online doc?
#mvn eclipse:add-maven-repo -Declipse.workspace=/home/renfeng/workspace

mvn eclipse:eclipse

