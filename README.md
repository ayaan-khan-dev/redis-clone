# Multi-threaded redis clone

An in memory key-value database written in Java from scratch. 
This project replicates core functions of Redis, utilizing TCP sockets, a custom RESP parser, and a TTL eviction system.

## Overview

1. The host (main thread) constantly listens on port 6379 using `ServerSocket.accept()`.
2. When a client connects, the main thread hands the socket over to another thread using `ExecutorService` in order to handle multiple clients which will then run the main command loop.
3. A daemon thread runs an infinite loop in the background which will check every second to see if any keys have expired.

## Supported Commands

* `PING` -> Returns `+PONG`
* `SET <key> <value>` -> Stores a key-value pair
* `SET <key> <value> EX <seconds>` -> Stores a key-value pair with an expiration
* `GET <key>` -> Returns the value of a key if it exists, otherwise returns `$-1`
* `INCR <key>` -> If the value of the key is a number then increases it by 1. If the value doesn't exist then it is set to 1. Returns the new value.
* `DECR <key>` -> If the value of the key is a number then decreases it by 1. If the value doesn't exist then it is set to -1. Returns the new value.
* `SUBSCRIBE <channel>` -> Subscribe to a channel, creates one if doesn't exist.
* `PUBLISH <channel> <message>` -> Publish a message to everyone subscribed to a channel.
