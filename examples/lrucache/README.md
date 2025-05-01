# LRU cache example

This is inspired from a real-life scenario where nice traces were obtained :)

The program is a toy LRU cache backing a data structure. Reader threads each access the data structure sequentially through the cache. When the cache contains an element, it's returned really quick, else there's a delay to simulate it being loaded from slower storage (e.g. disk). The size of the data struct and cache are configurable, as well as the number of reader thread and a few other parameters. 

## Real-life case

This toy example was inspired by a real-life investigation, regarding a web-accessible application that had configurable caches, including a specific one whose performance came under scrutiny. 

The main symptoms were that some performance problems coincided with that cache being used intensively. It was also noticed that though the cache was used by multiple threads concurrently, that should have been completely independent in their execution, they seem to end-up synchronized, executing in lock-step, and finishing execution almost at the same time.

That investigation produced some interesting traces, which we aim to approximately replicate with this example.

### Real life investigation - some info about that cache:

`type`: LRU (least recently used) - when the cache is full, the cache element that was the least recently used is evicted to make space for a new element to be inserted in its place.

`configurable cache size`: The number of elements it can hold is configurable by the app's admins

`Cache usage`: The cache is used in a peculiar way. Thought the cached data can be accessed randomly, it's often accessed sequentially, from first element to the last, in order. The app is multi-threaded and often multiple reader threads would be reading the data through the cache, in parallel.

The app provides cache statistics, which seemed quite good (a high cache "hit" rate was reported) despite the.

### Investigation goal

The cache was configured to be slightly under-sized vs the data it was caching, which size (number of elements) was slowly but surely increasing. Given the good reported cache stats, was it an issue that it was a bit undersized (by ~15%)? Was the cache, as configured, potentially affecting performance?

### Tracing to the rescue!

The cache and reader thread code were instrumented. From the generated traces, it was possible to distinguish cache hits vs misses. Capturing traces for a few scenarios, some surprising details started to emerge

## Build and run the example:

### Configuration

The example has several configurable parameters, that can be provided as CLI arguments or through maven config:

- --cache-size: number of elements the cache can hold
- --data-size: number of elements contained in the structure to be cached
- --num-threads: number of reader threads that will read the structure through the cache
- --readers-startup-delay-ms: delay between starting reader threads
- --cache-miss-delay-ms: delay to use when a cache miss occurs, e.g. to simulate the data being read from a slower source (e.g. disk or network)
- --log-warmup: whether or not to include the cache warm-up loop in the generated traces
- --verbose: Whether to have verbose, detailed output on STDOUT


When run using maven, one can tweak the configuration by editing their values in local `pom.xml`, section `configuration` under `exec-maven-plugin`:

```xml
[...]
<argument>--cache-size=95</argument>
<argument>--data-size=100</argument>
<argument>--num-threads=10</argument>
<argument>--readers-startup-delay-ms=130</argument>
<argument>--cache-miss-delay-ms=6</argument>
<argument>--log-warmup=true</argument>
<argument>--verbose=false</argument>
```

### Running the example

After having configured it as wanted in `pom.xml`:

```bash
$ cd examples/lrucache
$ mvn -q compile exec:exec
```

The generated trace will be under sub-folder `traces/`.

### Opening the generated trace in Trace Compass

First, run `jsonify.py` on the generated trace. The first time, it's necessary to install a couple of Python dependencies - see near the top of `jsonify.py` for the "pip" command to run.

```bash
$ cd examples/lrucache/traces
# "massage" the trace into valid json - 
# feel free to give it a more descriptive name
# (second parameter):
../../../jsonify.py trace.log cache_trace.json
# If everything went well "cache_trace.json" can be
# opened in Trace Compass!
```

#### Trace Compass

If you do not already have it, download the Trace Compass RCP and unzip it to a convenient place, then start it. 

Create a new tracing project, expand it. Select "traces" and right-click, select "open trace", navigate-to and select the newly generated cache trace. Then right-click on the new trace and hover "Select trace type". Confirm if there is a "Trace Event Format" entry. If so, select "Generic Trace Event Trace" under it, as the trace type. Else, we need to install support for this format:

`Help` -> `Install New Software`, then search for "trace event". Under 
category `Trace Types`, install:
`Trace Compass TraceEvent Parser (incubator)`

With the correct trace type selected, double-click on the trace to open it. Under it, there should be an expendable "views" item, and under it two "Thread by Component" entries - expand the second one, and select "Flame Chart (new callback)" under it. 


## Playing with the example

With the mechanical aspect out of the way, here's some suggestions for interesting configurations to try, that should generate interesting traces, from which a deeper understanding of the LRU cache, as it's used in the example, can be gained.

### 1) Warm-up and then 1 reader thread

The warm-up populates the cache, hopefully making it ready for a "real" reader to use it. Compare the performance of 1 reader thread, when the cache is fully sized (100/100) vs slightly undersized (95/100):

#### Traces to capture

a)
```xml
<!-- Fully sized cache -->
<argument>--cache-size=20</argument>
<argument>--data-size=20</argument>
<argument>--num-threads=1</argument>
<argument>--readers-startup-delay-ms=0</argument>
<argument>--cache-miss-delay-ms=6</argument>
<argument>--log-warmup=true</argument>
<argument>--verbose=false</argument>
```

b)
```xml
<!-- Slightly undersized cache -->
<argument>--cache-size=19</argument>
<argument>--data-size=20</argument>
<argument>--num-threads=1</argument>
<argument>--readers-startup-delay-ms=0</argument>
<argument>--cache-miss-delay-ms=6</argument>
<argument>--log-warmup=true</argument>
<argument>--verbose=false</argument>
```

#### Sample results (STDOUT)

a) Fully sized cache
```
--- Cache Stats ---
Total Requests   : 20
Cache Hits       : 20 (100.00%)
Cache Misses     : 0 (0.00%)
Avg get() Time/Op: 0.469 ms
Total get() Time : 9 ms
-------------------
```

b) Slightly undersized cache
```
--- Cache Stats ---
Total Requests   : 20
Cache Hits       : 0 (0.00%)
Cache Misses     : 20 (100.00%)
Avg get() Time/Op: 8.044 ms
Total get() Time : 160 ms
-------------------
```

#### Leads

- How to explain the slightly undersized cache has `100%` miss rate?
- Performance has decreased by ~20x, probably because the cache never hits?


### 2) Warm-up then 5 reader threads started at once

The warm-up populates the cache, hopefully making it ready for a "real" reader to use it. 

Compare the performance when the cache is fully sized (100/100) vs slightly undersized (95/100):

#### Traces to capture

a)
```xml
<!-- Fully sized cache -->
<argument>--cache-size=20</argument>
<argument>--data-size=20</argument>
<argument>--num-threads=5</argument>
<argument>--readers-startup-delay-ms=0</argument>
<argument>--cache-miss-delay-ms=6</argument>
<argument>--log-warmup=true</argument>
```

b)
```xml
<!-- Slightly undersized cache -->
<argument>--cache-size=19</argument>
<argument>--data-size=20</argument>
<argument>--num-threads=5</argument>
<argument>--readers-startup-delay-ms=0</argument>
<argument>--cache-miss-delay-ms=6</argument>
<argument>--log-warmup=true</argument>
<argument>--verbose=false</argument>
```

#### Sample results (STDOUT)

a) Fully sized cache
```
--- Cache Stats ---
Total Requests   : 100
Cache Hits       : 100 (100.00%)
Cache Misses     : 0 (0.00%)
Avg get() Time/Op: 0.581 ms
Total get() Time : 58 ms
-------------------
```

b) Slightly undersized cache
```
--- Cache Stats ---
Total Requests   : 100
Cache Hits       : 80 (80.00%)
Cache Misses     : 20 (20.00%)
Avg get() Time/Op: 7.446 ms
Total get() Time : 744 ms
-------------------
```

#### Leads

- How to explain the disproportional decrease in performance between the fully sized caches vs 95% sized results? It's a ~12x factor!

### 3) Warm-up then 5 reader threads started with a delay

The warm-up populates the cache, hopefully making it ready for a "real" reader to use it. 

Compare the performance when the cache is fully sized (100/100) vs slightly undersized (95/100):

#### Traces to capture

a)
```xml
<!-- Fully sized cache -->
<argument>--cache-size=20</argument>
<argument>--data-size=20</argument>
<argument>--num-threads=5</argument>
<argument>--readers-startup-delay-ms=25</argument>
<argument>--cache-miss-delay-ms=6</argument>
<argument>--log-warmup=true</argument>
```

b)
```xml
<!-- Slightly undersized cache -->
<argument>--cache-size=19</argument>
<argument>--data-size=20</argument>
<argument>--num-threads=5</argument>
<argument>--readers-startup-delay-ms=25</argument>
<argument>--cache-miss-delay-ms=6</argument>
<argument>--log-warmup=true</argument>
<argument>--verbose=false</argument>
```

#### Sample results (STDOUT)

a) Fully sized cache
```
--- Cache Stats ---
Total Requests   : 100
Cache Hits       : 100 (100.00%)
Cache Misses     : 0 (0.00%)
Avg get() Time/Op: 0.187 ms
Total get() Time : 18 ms
-------------------
```

b) Slightly undersized cache
```
--- Cache Stats ---
Total Requests   : 100
Cache Hits       : 80 (80.00%)
Cache Misses     : 20 (20.00%)
Avg get() Time/Op: 5.026 ms
Total get() Time : 502 ms
-------------------
```

#### Leads

- How to explain the disproportional decrease in performance between the fully sized caches vs 95% sized results?
- Looking at the captured traces, do reader thread executions overlap in both cases? Any unexpected effect of overlap when the cache is undersized (e.g. unintended thread synchronization or lockstep effect)?
- zooming-in, in a zone in the trace where two or more threads seem to execute in "lockstep", examine one particular iteration
  - how many of the reader threads end-up having a cache miss? 
  - how does iteration execution time compare between the reader thread that has a cache miss vs those who have a "cache hit after waiting"?
  - in such a situation, is there a meaningful performance gain in a reported cache miss vs hit?

### 4) "At scale" run

This run we will push the limits a bit to generate a nice, rather big trace. 

Compare the performance when the cache is fully sized (100/100) vs slightly undersized (95/100):

#### Traces to capture

a)
```xml
<!-- Fully sized cache -->
<argument>--cache-size=100</argument>
<argument>--data-size=100</argument>
<argument>--num-threads=10</argument>
<argument>--readers-startup-delay-ms=130</argument>
<argument>--cache-miss-delay-ms=6</argument>
<argument>--log-warmup=true</argument>
```

b)
```xml
<!-- Slightly undersized cache -->
<argument>--cache-size=95</argument>
<argument>--data-size=100</argument>
<argument>--num-threads=10</argument>
<argument>--readers-startup-delay-ms=130</argument>
<argument>--cache-miss-delay-ms=6</argument>
<argument>--log-warmup=true</argument>
<argument>--verbose=false</argument>
```

#### Sample results (STDOUT)

a) Fully sized cache
```
--- Cache Stats ---
Total Requests   : 1000
Cache Hits       : 1000 (100.00%)
Cache Misses     : 0 (0.00%)
Avg get() Time/Op: 0.083 ms
Total get() Time : 82 ms
-------------------
```

b) Slightly undersized cache
```
--- Cache Stats ---
Total Requests   : 1000
Cache Hits       : 806 (80.60%)
Cache Misses     : 194 (19.40%)
Avg get() Time/Op: 3.985 ms
Total get() Time : 3985 ms
-------------------
```

#### Leads

Nothing new, but look how nice the trace looks :)
