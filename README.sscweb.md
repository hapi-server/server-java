# Introduction
We believe the SSCWeb server is a good candidate for this new server.  This page
considers its needs.

# service is not streaming
The SSCWeb service does not appear to be a streaming service.  This means its adapter
might like a feature where the Java server will granularize the request and reassemble
the granules.

# thinking out loud....

* What size should each of the calls to the reader be? 
* Will the reader handle subset of parameters?
* Does the reader output binary or ascii?
* Does the reader output unsorted data?
~~~~~
{ 
  "granuleSize":"$Y$m$d",
  "format":"ascii",
  "subset":"false",
  "sorted":"true",
  "commandLineReader":"cat /tmp/mydata/$Y$m$d.dat"
}
~~~~~
