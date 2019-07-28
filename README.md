# cordova-plugin-node-http: Node http plugin for Android


This module is an implementation of Node's native `http` module for Cordova on Android.
It tries to match Node's API and behavior as closely as possible, but is still a work in progress and some feautures are not supported.


## Usage

The intent is to have the same API as the client part of the
[Node HTTP module](https://nodejs.org/api/http.html). The interfaces are the same wherever
practical, although limitations in browsers make an exact clone of the Node API impossible.

This module implements `http.request`, `http.get`, and most of `http.ClientRequest`
and `http.IncomingMessage` in addition to `http.METHODS` and `http.STATUS_CODES`. It also supports `ca`, `cert` and `key` https options. See the
Node docs for how these work.


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

