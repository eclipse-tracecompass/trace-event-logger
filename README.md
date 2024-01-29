# Trace Event Logger

This logger is based on JUL to allow fast JSON traces to be written to disk. It is not lockless or nanosecond precise, but is fast and simple to use and configure.

A Snapshot taker is provided in order to write slow transactions onto disk.

An asynchronous writer is available for putting the IO bottleneck on another thread. It is typically much faster than the FileHandler from JUL.

This is a logger helper designed to facilitate entry-exit analysis. The events generated by this logger are saved in a JSON-like message format, providing extra information associated with each event. The typical event types include:

- **Durations:**
  - **B:** Begin
  - **E:** End
  - **X:** Complete (event with a duration field)
  - **i:** Instant / Info

- **Asynchronous Nested Messages:**
  - **b:** Nested Begin
  - **n:** Nested Info
  - **e:** Nested End

- **Flows:**
  - **s:** Flow Begin
  - **t:** Flow Step (Info)
  - **f:** Flow End

- **Object Tracking:**
  - **N:** Object Created
  - **D:** Object Destroyed

- **Mark Events:**
  - **R:** Marker Event

- **Counter Events:**
  - **C:** Counter Event

## Usage

To use **durations** and/or **flows**, refer to [ScopeLog](#) and [FlowScopeLog](#). Durations are typically used to instrument simple methods, while flows are preferred when there are links to be made with other threads.

To use **Asynchronous Nested Messages**, check [traceAsyncStart](#) and [traceAsyncEnd](#).

To use **Object Tracking**, see [traceObjectCreation](#) and [traceObjectDestruction](#).

## Performance

On an Intel i5-1145G7 @ 2.60GHz with an NVME hard drive, performance passes from 45 us/event to 1.1 us/event. There is also a cache effect that one could take advantage of, where if the data is not saturating the IO, speed is even higher.

## Design Philosophy

The design philosophy of this class is heavily inspired by the trace event format of Google. The full specification is available [here](https://docs.google.com/document/d/1CvAClvFfyA5R-PhYUmn5OOQtYMH4h6I0nSsKchNAySU/edit?pli=1#).

The main goals of this logger helper are clarity of output and simplicity for the developer. While performance is a nice-to-have, it is not the main concern of this helper. A minor performance impact compared to simply logging the events is to be expected.

