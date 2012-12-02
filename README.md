
This small scala program is a Web UI to visualise statsd buckets.
Currently it only supports the 'increment' operation, but it should be easily extendable to support 
other operations as well. In fact this program is not designed to be
a statsd visualiser but rather a trial to build an event-based stream processor. 
To achieve this it leverages the following technologies/libraries :

* [scala](http://www.scala-lang.org) : a great programming language that elegantly combines functional & oop programming paradigms and lives in the jvm
* The play 2.1 [iteratee library](http://www.playframework.org/documentation/2.0/Iteratees)
* [Netty](http://netty.io) 4.0 (alpha) : A java library for event-based io 
* [Rickshaw](http://code.shutterstock.com/rickshaw/) : a javascript library for timeseries based on [d3.js](http://d3js.org)

