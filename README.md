# Cordova Node HTTP: Node API HTTP plugin for Android


This module is an implementation of Node's native `http` module for Cordova on Android.
It tries to match Node's API and behavior as closely as possible, but is still a work in progress and some features are not supported.

## Why not use [stream-http](https://github.com/jhiesey/stream-http) or [cordova-plugin-advanced-http](https://github.com/silkimen/cordova-plugin-advanced-http)?

Like `cordova-plugin-advanced-http` this plugin tries to avoid limitations with browser implementations like forced `CORS` and missing `client certificate authentication`, but it improves on it by using Node.js stream implementation, to allow for requests of virtually any size, as well as the resuse of existing libraries built for Node.js

## Installation
This plugin is published on [NPM](https://www.npmjs.com/package/cordova-plugin-node-http) and can be installed using Cordova CLI

```shell
cordova plugin add cordova-plugin-node-http
```

## Usage

The intent is to have the same API as the client part of the
[Node HTTP module](https://nodejs.org/api/http.html). The interfaces are the same wherever
practical.

This module implements `http.request`, `http.get`, and most of `http.ClientRequest`
and `http.IncomingMessage`, in addition to `http.METHODS`, `http.STATUS_CODES` and some https options (`ca`, `cert` and `key`). 
See the Node docs for how these work.


### Features missing compared to Node

* `http.createServer` is not supported
// TODO document other missing features


## Simple Example

``` js
const http = window.CordovaNodeHttp;

http.get('https://jsonplaceholder.typicode.com/users', function (res) {

    let users = '';

    res.on('data', function (buf) {
        users += buf.toString('utf8');
    });

    res.on('end', function () {
        console.log(users)
    });
})
```

