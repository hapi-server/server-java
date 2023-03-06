# templates area
These are templates that were used to develop and test the server, and
should serve as examples when creating new servers.  Each folder, including windspeed, aggregation, 
classpath, and spawn, contains the files which should be copied into the config area of the
server.  Two files, about.json and capabilities.json should also be copied from this directory.
When a directory and these two files are copied into the server config directory, the server should
work with this configuration.  Of course resources needed to implement these will not exist on the
new server, for example:

* file:/home/jbf/data/tempest/34803/$Y/$m/34803.wind.$Y$m$d.csv
* file:/net/spot8/home/jbf/ct/hapi/git/server-java/SimpleClasspathExtension/dist/SimpleClasspathExtension.jar
* file:/home/jbf/bin/autoplotDataServer.sh


