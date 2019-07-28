const statusCodes = require('builtin-status-codes');
const ClientRequest = require('./client_request');

const methods = [
  'CHECKOUT',
  'CONNECT',
  'COPY',
  'DELETE',
  'GET',
  'HEAD',
  'LOCK',
  'M-SEARCH',
  'MERGE',
  'MKACTIVITY',
  'MKCOL',
  'MOVE',
  'NOTIFY',
  'OPTIONS',
  'PATCH',
  'POST',
  'PROPFIND',
  'PROPPATCH',
  'PURGE',
  'PUT',
  'REPORT',
  'SEARCH',
  'SUBSCRIBE',
  'TRACE',
  'UNLOCK',
  'UNSUBSCRIBE'
];

const http = {
  request(url, options, callback) {
    return new ClientRequest(url, options, callback);
  },
  get(url, options, callback) {
    let req = new ClientRequest(url, options, callback);
    req.end();
    return req;
  },
  METHODS: methods.slice().sort(),
  STATUS_CODES: statusCodes,
  ClientRequest,
  IncommingMessage: ClientRequest.IncommingMessage,
};


http.Agent = class Agent {};
http.Agent.defaultMaxSockets = 4;
http.globalAgent = new http.Agent();

module.exports = http;
