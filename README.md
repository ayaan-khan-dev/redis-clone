# Multi-threaded redis clone

An in memory key-value database written in Java from scratch. 
This project replicates core functions of Redis, utilizing TCP sockets, a custom RESP parser, RDB snapshotting, AOF, and a TTL + LRU eviction system.

## Overview

1. The host (main thread) constantly listens on port 6379 using `ServerSocket.accept()`.
2. When a client connects, the main thread hands the socket over to another thread using `ExecutorService` in order to handle multiple clients which will then run the main command loop.
3. A daemon thread runs an infinite loop in the background which will check every second to see if any keys have expired.
4. Another thread will run every 5 minutes creating an RDB snapshot which saves the database to dump.rdb which will then be loaded when starting up main.java
5. Every command will also get logged to appendonly.aof which will then replay every command when starting main.java. Every time an RDB snapshot is saved appendonly.aof is cleared. This makes it so that no keys are lost even if an RDB snapshot hasn't been saved recently.

## Supported Commands

* `PING` -> Returns `+PONG`
* `SET <key> <value>` -> Stores a key-value pair
* `SET <key> <value> EX <seconds>` -> Stores a key-value pair with an expiration
* `SET <key> <value> PXAT <unix-time-milliseconds>` -> Stores a key-value pair with a specific Unix time at which the key will expire in milliseconds.
* `GET <key>` -> Returns the value of a key if it exists, otherwise returns `$-1`
* `DEL <key>` -> removes this key-value pair.
* `INCR <key>` -> If the value of the key is a number then increases it by 1. If the value doesn't exist then it is set to 1. Returns the new value.
* `DECR <key>` -> If the value of the key is a number then decreases it by 1. If the value doesn't exist then it is set to -1. Returns the new value.
* `SUBSCRIBE <channel>` -> Subscribe to a channel, creates one if doesn't exist.
* `UNSUBSCRIBE <channel>` -> Unsubscribe to a channel.
* `PUBLISH <channel> <message>` -> Publish a message to everyone subscribed to a channel.
* `MULTI` -> Queues up commands.
* `EXEC` -> Runs all queued commands in order at once.
* `DISCARD` -> Cancels queue.
* `EXPIRE <key> <seconds>` -> Add a TTL expiration to keys.
* `PEXPIREAT <key> <unix-time-milliseconds>` -> Set a specific Unix time at which the key will expire in milliseconds.
* `TTL <key>` -> Check the TTL for a key.
* `KEYS *` -> Returns all keys.