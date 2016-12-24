# topNET

## Introduction

topNET is a very lightweight and fast asynchronous HTTP server. It's highly customizable and has been designed with ease of usability in mind.
Performance is on pair with servers like NGINX, Fasthttp or Netty and even higher - depending on configuration.
topNET is as large as few tens of kilobytes.

## Design Key Points
* Performance
* API Simplicity
* Flexibility
* Configurability

### Performance

topNET is designed to behave and look similar to typical J2EE specification but only on a basic level. Structurally one may see some similarities with technologies like Servlet.

There has been implemented two server types, one is based on a typical event only based R/W operations wakups and a second version where all R/W operations are handled in full isolation by handling workers, the main difference between two is that main accept loop in event based version case wakes threads to process incoming R/W operations where second version does only accepting connections and let threads to decide on their own how handle R/W operations (typically they wait for such).
Both versions have similar performance in average use case, however under heavy 
load - AcceptOnlyEventsTypeServer server may be slightly faster and have better micro-latency.

Both versions however have same functionality with exception of on or two extra options for AcceptOnlyEventsTypeServer.
Both server types also share same handling API for requests and response.

To use event only based server simply use:
```
com.qubit.topnet.PureEventsTypeServer
```
to use "wait" based server just choose:
```
com.qubit.topnet.AcceptOnlyEventsTypeServer
```

Because both servers share same functionality, they wont be referred seperately anymore unless its required in this documentation and as a single logical entity we will use a "server" word in following content.

Server uses 3 types of handling threads where POOL type is default:
* POOL - this type uses atomic reference array with constant 
    size created upon server start. It is safest and lightest way that stores thread jobs. 
* QUEUE - this type is similar to "pool" but uses concurrent list instead of 
    statically sized array like in "pool" case. While "pool" type thread scans the 
    array to find jobs to do, this thread peeks jobs from head and add to tail in 
    typical FILO manner. Each thread of "queue" type maintain its own local queue 
    instance. QUEUE type has an option of unlimited queue size and such option may be useful in some advanced cases.
* QUEUE_SHARED - this is same as QUEUE type with the difference that all threads 
    share same concurrent queue. This type of jobs may work better for servers that 
    perform slow asynchronous operations and threads can stuck for long time in 
    one place.

In most of the cases, "pool" will be absolutely fine and recommended choice. 
If you are not sure, just leave the defaults. 
Default configuration does also check how many cpu-s is in the system in order to
 estimate amount of used threads (if there is many cpus/core there will be more 
threads assigned). By default, server auto-scales when it runs out of jobs allocation
space - if server will deal with short processing - it is fine to keep threads amount 
no larger than cores amount in the system. Autoscaling process by default re-uses threads, 
if server was downscaling, upscaling will reuse threads that were burried during 
downscaling.

To observe jobs and threads, add one of examples to the handlers set: 

```
server.registerHandlerByPath(
    "/listjobsandthreads",
    new com.qubit.qurricane.examples.JobsNumHandler(server));

```

Server processing is limited to almost absolute minimum. While request URL and headers will be parsed and ready for a handler - extended functionality like parsing url parameters or POST body parameters is not provided (yet). 
There is a wide choice of API that can be used for parsing parameters and will be added to topNET in near future.
topNET focuses solely on performance and flexibility.

Server supports protocol up to HTTP/1.1 (including keep-alive).

### Usage Simplicity
topNET only requires a Server instance to be created to start processing requests. 
Service handlers can be added to server and can be chained, there is many 
way how your application can chain processing and choose which url/method is bounded 
to which handler in what order. There is plenty of choice in terms of customization.
To create typical server application all is needed is a server instance and handler 
classes that will implement how to handle request. See examples section for more 
details. Handlers are added to server using:

```
server.registerHandlerByPath(String path, Handler handler)
// this will register handler by path name. if request path equals,
 handler will process request and pass it forward to next handler in chain.

server.registerPathMatchingHandler(Handler handler)
// this will register handler by handler.match(...) function use, if it returns
true, handler will process request and pass it forward to next handler in chain.

```

### Flexibility 
Handling threads amounts (pools), jobs queues size, processing delays and many 
more options are adjustable to system needs. By default system checks available 
cpus and will create adequate handling threads amount for each cpu to handle requests.

topNET is extremely small but can serve same heavy loads and application complexity 
as good or better as fully featured "official" servers such as tomcat, jboss or glassfish. 
Being very small and highly customizable it's an excellent solution as 
for mobile devices as for powerful desktop stations.


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

To see more usage examples, check out the examples 'com.qubit.topnet.examples' package.

##Author
Peter Fronc <peter.fronc@qubit.com>

##Benchmarks
Coming.

