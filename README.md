# server-java
Java-based server which works with Java-based web servers like Tomcat

# Introduction
There are presently four or more different Java server implementations.  It would be
useful to have a framework which handles the "HAPI parts" of the server, such as
input validation and data handling, so that those setting up servers can focus
on the data sources feeding into the server.

This will be a Java servlet which implements the HAPI protocol (version 3.1), using
plugins written and maintained by the data steward.  How these plugins connect will
be documented below, and may evolve as the server matures, but the intent is that
plugins for the Python server might also be used with this server implementation.

Much of this is inspired by the lessons learned implementing Das2Servers, where a 
perl script would hand off control to a reader, which would generate a stream of 
data on stdout which would go through the server and then out to the client.  The
Das2Server would perform additional operations, such as data reduction, before
sending out the stream to the client.  The state-of-the-art
Das2Server is implemented in Python and provides caches of reduced data. The HAPI 
server has similarities, except that subsetting and formatting must be done to service 
the request.  

This will provide a number of features which the other implementations may be missing.
The mature HAPI server will handle input validation, protecting the server from hostile 
attacks.  It may provide caching of data when the readers are slow, and also validate 
that reader responses are correct.  Readers should be allowed to be sloppy, so that 
boilerplate code for subsetting needn't appear in each reader.

# Initial Implementations
There are a few groups who may use this server immediately.  The CDAWeb group's SSCWeb
is implemented by scaping data from their web responses, and clearly needs to 
be reimplemented.  ESAC intends to set up a HAPI server and is interested in
this framework, and we hope to set up an initial version of the server in late May 2022.
PDS-PPI has an existing server which might be more easily maintained using this
framework.  The CDAWeb server has known problems which need to be resolved.  

