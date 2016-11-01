# qurricane

## Introduction

Qurricane is one of Qubit's open source projects. The idea was to create a very lightweight but highly customizable asynchronous HTTP server. Qurricane has been designed with ease of usability in mind, see example section to check on how it's achieved.

## Design Key Points
* Performance
* Usage Simplicity
* Flexibility
* Configurability

### Performance
Qurricane uses Java asynchronous NIO API for handling connections traffic, it consists on thread pools that handle R/W operations. There is a separate thread that is dedicated for accepting only connections stage and it is working in non-blocking mode. Qurricane is designed to be able to put hardware to it's limits, but can be configured to slown down by using server options.
Each thread from a pool maintain its own or shared "jobs" queue which correspond to incoming connections. There is 3 types of handling threads:
* pool - this type is a simplest one and uses atomic reference array with constant size created upon server start. It is safest and lightest way that stores thread jobs. 
* pool-shared - same type as pool but all threads share same pool.
* queue - this type is similar to "pool" but uses concurrent list instead of statically sized array like in "pool" case. While "pool" type thread scans the array to find jobs to do, this thread peeks jobs from head and add to tail in typical FILO manner. Each thread of "queue" type maintain its own local queue instance.
* queue-shared - this is same as "queue" type with the difference that all threads share same concurrent queue. This type of jobs may work better for servers that perform slow asynchronous operations and threads can stuck for long time in one place.

In most of the cases, "pool" will be absolutely fine and best choice. If you are not sure, just leave the defaults - server by default use "pool" type. Default configuration does also check how many cpu-s is in the system in orther to estimate amount of used threads (if there is many cpus/core there will be more threads assigned).

Server processing is limited to almost absolute minimum, while request URL and headers will be recognised, extended functionality like parsing url parameters or POST body parameters is not included. There is a wide choice of API that can be used for parsing parameters.
Qurricane focuses solely on performance and flexibilty and it is able to test your hardware I/O limits.

### Usage Simplicity
Qurricane requires a Server instance to be created to start processing requests. Service handlers can be added to server class and can be chained, there is many way how your application can chain processign and choose which url/method is bounded to which handler. There is plenty of choice in terms of customization.
To create typical server application all is needed is a server instance and handler classes that will implement how to handle request. See examples section for more details.

### Flexibility
Handling threads amounts (pools), jobs queues size, processing delays and many more options are adjustable to system needs. By default system checks available cpus and will create adequate handling threads amount for each cpu to handle requests. Server can be easily configured to use little resources and to slow down it's IO, throughput of the server is a result of setting buffer size and "breaks" latencies during data polling in the queues. It can be configured to response very fast but to limit transfer speeds or vice versa. Delays can be controlled on each processing level.

Qurricane is extremely small but can serve same heavy loads and application complexity as fully featured "official" servers such as tomcat, jboss or glassfish. Being very small and highly customizable it's an excellent solution as for mobile devices as for powerful desktop stations.


##Examples

To create server instance:

```java
import com.qubit.qurricane.Server;
import com.qubit.qurricane.examples.EchoHandler;

public static void main(String[] args) {
	Server server = new Server("localhost", 3456);

	// custom setup
	server.setRequestBufferSize(32 * 1024); // this is buffer for readin, buffers for reading are hold by threads
	server.setJobsPerThread(256);
  	server.setThreadsAmount(16);
  	server.setDataHandlerWriteBufferSize(1024); // this is buffer for writing back, those buffers are hold by 
  												// data handler objects. 
  												// Data handler objects are the jobs in threads queues.
  	server.setDefaultIdleTime(25 * 1000);		// This is how long server will wait for R/W before closing.
  												// This property is configurable on handler level.
  	server.setDelayForNoIOReadsInSuite(30);		// This property indicates how many milisconds thread will sleep 
  												// if there is no I/O occuring in its queue (queue having jobs!).
  												// Threads sleeping can be waked up at any time if accepting 
  												// connection thread is updating the queue.
  	server.setSinglePoolPassThreadDelay(0);		// This is how to set any delay between "rounds" of 
  												// reading/writing from/to jobs. Value larger than zero will 
  												// cause a sleep every time thread is checking queue for I/O 
  												// (read/write -> wait etc.).
  												// This is a direct way to slow down server - when necessary.

  	server.registerHandlerByPath("/echo", new EchoHandler()); //register demo handler (echoing data back)

  	server.start(); // start the server
}
```

To see more usage exapmles, check out the examples 'com.qubit.qurricane.examples'.

##Author
Peter Fronc <peter.fronc@qubit.com>

##Benchmarks
Some benchmarks using ab tool.


### BEST RESULTS FROM 15 RUNS, 'ab -n 1000 -c 1000 ...', Intel(R) Xeon(R) CPU E5-2680 v2 @ 2.80GHz:

```
----------------------------------------------------------------------------
QURRICANE (delayed no-I/O config):
Server Software:        Qurricane
Server Hostname:        127.0.0.1
Server Port:            3456

Document Path:          /echo
Document Length:        19 bytes

Concurrency Level:      1000
Time taken for tests:   0.161 seconds
Complete requests:      1000
Failed requests:        0
Write errors:           0
Total transferred:      160000 bytes
HTML transferred:       19000 bytes
Requests per second:    6225.95 [#/sec] (mean)
Time per request:       160.618 [ms] (mean)
Time per request:       0.161 [ms] (mean, across all concurrent requests)
Transfer rate:          972.81 [Kbytes/sec] received

Connection Times (ms)
              min  mean[+/-sd] median   max
Connect:        2   18  17.0     11      52
Processing:     4   15  10.2     13      43
Waiting:        3   11   7.7      8      34
Total:          5   33  25.1     25      95

(50% responses below 21ms, normally 5ms (!))
----------------------------------------------------------------------------
NODEJS:
Server Software:
Server Hostname:        127.0.0.1
Server Port:            3455

Document Path:          /echo
Document Length:        17 bytes

Concurrency Level:      1000
Time taken for tests:   0.305 seconds
Complete requests:      1000
Failed requests:        0
Write errors:           0
Non-2xx responses:      1000
Total transferred:      215000 bytes
HTML transferred:       17000 bytes
Requests per second:    3279.29 [#/sec] (mean)
Time per request:       304.944 [ms] (mean)
Time per request:       0.305 [ms] (mean, across all concurrent requests)
Transfer rate:          688.52 [Kbytes/sec] received

Connection Times (ms)
              min  mean[+/-sd] median   max
Connect:       31   44   8.2     44      59
Processing:    31  114  55.0    113     216
Waiting:       29  114  55.1    113     216
Total:         88  158  46.8    157     246
(50% responses below 160ms, normally 170ms)

----------------------------------------------------------------------------
TOMCAT:
Server Software:        Apache-Coyote/1.1
Server Hostname:        127.0.0.1
Server Port:            8080

Document Path:          /echo
Document Length:        959 bytes

Concurrency Level:      1000
Time taken for tests:   0.229 seconds
Complete requests:      1000
Failed requests:        0
Write errors:           0
Non-2xx responses:      1000
Total transferred:      1128000 bytes
HTML transferred:       959000 bytes
Requests per second:    4375.81 [#/sec] (mean)
Time per request:       228.529 [ms] (mean)
Time per request:       0.229 [ms] (mean, across all concurrent requests)
Transfer rate:          4820.23 [Kbytes/sec] received

Connection Times (ms)
              min  mean[+/-sd] median   max
Connect:        0   13  18.0      2      44
Processing:    10   21   4.3     20      55
Waiting:        5   19   4.9     18      54
Total:         14   34  20.6     23      71
(50% responses below 23ms in case, normally 28ms)

```

### BEST RESULTS FROM 15 RUNS, 'ab -n 1000 -c 30 ...', Intel(R) Xeon(R) CPU E5-2680 v2 @ 2.80GHz:
```

----------------------------------------------------------------------------
QURRICANE:
Server Software:        Qurricane
Server Hostname:        127.0.0.1
Server Port:            3456

Document Path:          /echo
Document Length:        19 bytes

Concurrency Level:      30
Time taken for tests:   0.117 seconds
Complete requests:      1000
Failed requests:        0
Write errors:           0
Total transferred:      160000 bytes
HTML transferred:       19000 bytes
Requests per second:    8563.77 [#/sec] (mean)
Time per request:       3.503 [ms] (mean)
Time per request:       0.117 [ms] (mean, across all concurrent requests)
Transfer rate:          1338.09 [Kbytes/sec] received

Connection Times (ms)
              min  mean[+/-sd] median   max
Connect:        0    0   0.2      0       1
Processing:     1    3   0.3      3       4
Waiting:        1    3   0.3      3       4
Total:          2    3   0.2      3       5
(50% responses below 3ms)

----------------------------------------------------------------------------
NODEJS:
Server Software:	
Server Hostname:        127.0.0.1
Server Port:            3455

Document Path:          /echo
Document Length:        17 bytes

Concurrency Level:      30
Time taken for tests:   0.290 seconds
Complete requests:      1000
Failed requests:        0
Write errors:           0
Non-2xx responses:      1000
Total transferred:      215000 bytes
HTML transferred:       17000 bytes
Requests per second:    3453.63 [#/sec] (mean)
Time per request:       8.686 [ms] (mean)
Time per request:       0.290 [ms] (mean, across all concurrent requests)
Transfer rate:          725.13 [Kbytes/sec] received

Connection Times (ms)
              min  mean[+/-sd] median   max
Connect:        0    0   0.2      0       2
Processing:     1    8   1.0      8      11
Waiting:        1    8   1.0      8      11
Total:          3    9   0.9      8      11
WARNING: The median and mean for the total time are not within a normal deviation
        These results are probably not that reliable.

Percentage of the requests served within a certain time (ms)
(50% responses below 9ms)

----------------------------------------------------------------------------
TOMCAT:
Server Software:        Apache-Coyote/1.1
Server Hostname:        127.0.0.1
Server Port:            8080

Document Path:          /echo
Document Length:        959 bytes

Concurrency Level:      30
Time taken for tests:   0.197 seconds
Complete requests:      1000
Failed requests:        0
Write errors:           0
Non-2xx responses:      1000
Total transferred:      1128000 bytes
HTML transferred:       959000 bytes
Requests per second:    5064.86 [#/sec] (mean)
Time per request:       5.923 [ms] (mean)
Time per request:       0.197 [ms] (mean, across all concurrent requests)
Transfer rate:          5579.25 [Kbytes/sec] received

Connection Times (ms)
              min  mean[+/-sd] median   max
Connect:        0    1   1.0      0       4
Processing:     1    5   3.4      5      38
Waiting:        0    5   2.9      5      37
Total:          2    6   3.2      5      38
(50% responses below 5ms)
```

Clearly node and tomcat are not able to use whole I/O potential of the machine.
