# topNET

## Introduction

topNET is a fast and small HTTP server. The idea is to have a very lightweight but highly customizable asynchronous HTTP server. topNET has been designed with ease of usability in mind, see example section to check on how it's achieved.
This server, when correctly configured can perform on NGINX level of performance, benchmarks provided are agains most populare http servers.

## Design Key Points
* Performance
* Usage Simplicity
* Flexibility
* Configurability

### Performance

Server implementation has two basic versions, one is based on a typical even only based R/W operations wakups and a second version where all R/W operations are handled in full isolation by handling threads, the main difference is that main accept loop in event based version case wakes threads to process some R/W when they become available where second version does only accepting connections and let threads to decide on their own when they should be awake for R/W operations.
Both versions have similiar performance in average use case, however under heavy load - server may be be faster and have better latency for the second case where accept loop only accepts connections (in this case when no job has anythng to R/W thread will sleep for short time).

Both versions have equal functionality, to use event only based server, use:
```
com.qubit.topnet.PureEventsTypeServer
```
for wait based server type:
```
com.qubit.topnet.AcceptOnlyEventsTypeServer
```

topNET uses Java asynchronous NIO API for handling connections traffic, it consists on thread pools that handle R/W operations. There is a separate thread that is dedicated for accepting only connections stage and it is working in non-blocking mode. topNET is designed to be able to put hardware to it's limits, but can be configured to slown down by using server options.
Each thread from a pool maintain its own or shared "jobs" queue which correspond to incoming connections. There is 3 types of handling threads:
* pool - this type is a simplest one and uses atomic reference array with constant size created upon server start. It is safest and lightest way that stores thread jobs. 
* queue - this type is similar to "pool" but uses concurrent list instead of statically sized array like in "pool" case. While "pool" type thread scans the array to find jobs to do, this thread peeks jobs from head and add to tail in typical FILO manner. Each thread of "queue" type maintain its own local queue instance.
* queue-shared - this is same as "queue" type with the difference that all threads share same concurrent queue. This type of jobs may work better for servers that perform slow asynchronous operations and threads can stuck for long time in one place.

In most of the cases, "pool" will be absolutely fine and best choice. If you are not sure, just leave the defaults - server by default use "pool" type. Default configuration does also check how many cpu-s is in the system in orther to estimate amount of used threads (if there is many cpus/core there will be more threads assigned).

Server processing is limited to almost absolute minimum, while request URL and headers will be recognised, extended functionality like parsing url parameters or POST body parameters is not included. There is a wide choice of API that can be used for parsing parameters.
topNET focuses solely on performance and flexibilty and it is able to test your hardware I/O limits.

### Usage Simplicity
topNET requires a Server instance to be created to start processing requests. Service handlers can be added to server class and can be chained, there is many way how your application can chain processign and choose which url/method is bounded to which handler. There is plenty of choice in terms of customization.
To create typical server application all is needed is a server instance and handler classes that will implement how to handle request. See examples section for more details.

### Flexibility
Handling threads amounts (pools), jobs queues size, processing delays and many more options are adjustable to system needs. By default system checks available cpus and will create adequate handling threads amount for each cpu to handle requests. Server can be easily configured to use little resources and to slow down it's IO, throughput of the server is a result of setting buffer size and "breaks" latencies during data polling in the queues. It can be configured to response very fast but to limit transfer speeds or vice versa. Delays can be controlled on each processing level.

topNET is extremely small but can serve same heavy loads and application complexity as fully featured "official" servers such as tomcat, jboss or glassfish. Being very small and highly customizable it's an excellent solution as for mobile devices as for powerful desktop stations.


##Examples

To create server instance:

```java
import com.qubit.topnet.AcceptOnlyEventsTypeServer;
import com.qubit.topnet.examples.EchoHandler;

public static void main(String[] args) {
	AcceptOnlyEventsTypeServer server = 
        new AcceptOnlyEventsTypeServer("localhost", 3456);

	server.setMaxGrowningBufferChunkSize(64 * 1024); 
        // this is a growing buffer size limit.
        // Growing buffer, is a class that is used to store data for R/W operations. 
        // topNET uses dedicated buffer chain for R/W operations, 
        // each chain chunk will have maximum size of specified by this setter.

	server.setJobsPerThread(256);
        // how many jobs each thread can have (max)
  	
    server.setThreadsAmount(16);
        // maximum amount of threads

  	server.setDefaultIdleTime(25 * 1000);
        // This is how many miliseconds server will wait for R/W 
        // before closing connection.
  		// This property is also configurable on a handler level.

  	server.setDelayForNoIOReadsInSuite(30);
        // (only for accept type server)
        // This property indicates how many nanoseconds thread will sleep (if
        // value is larger than 1000000 then it corresponds to value miliseconds after dividing by 1000000).
  		// Wait occurs when if there is no I/O occuring in entire threads queue 
        // (queue having jobs but none has anything to read or write!.

  	
  	server.registerHandlerByPath("/echo", new EchoHandler());
        //register demo handler (echoing data back)

  	server.start();
        // start the server
}
```

To see more usage exapmles, check out the examples 'com.qubit.topnet.examples'.

##Author
Peter Fronc <peter.fronc@qubit.com>

##Benchmarks
Coming.

