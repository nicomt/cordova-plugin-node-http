const {Readable} = require('stream');
const {Buffer} = require('buffer');

class IncomingMessage extends Readable {
  constructor(conId, req) {
    super();
    this._conId = conId;
    this.req = req;
    this.httpVersionMajor = null;
    this.httpVersionMinor = null;
    this.httpVersion = null;
    this.complete = false;
    this.headers = {};
    this.rawHeaders = [];
    this.trailers = {};
    this.rawTrailers = [];
    this.readable = true;
    this.aborted = false;
    this.upgrade = null;

    // response (client) only
    this.statusCode = null;
    this.statusMessage = null;
  }

  _parseNativePacket(packet) {
    switch (packet.type) {
    case 'head':
      this.statusCode = packet.statusCode;
      this.headers = this._parseHeaders(packet.headers);
      break;
    case 'chunk':
      this.push(Buffer.from(packet.data, 'base64'));
      break;
    case 'end':
      this.complete = true;
      if(packet.data) this.push(Buffer.from(packet.data,'base64'));
      this.push(null);
      break;
    default:
      break;
    }
  }

  _parseHeaders(headers){
    let parsed = {};
    Object.entries(headers).forEach(([k,v])=>{
      parsed[k.toLowerCase()] = v.substring(1, v.length - 1);
    });
    return parsed;
  }

  _read(size) {
    if(this.complete) return;
    cordova.exec(packet => {
      this._parseNativePacket(packet);
    }, err => {
      process.nextTick(this.emit('error', err));
    }, 'CordovaNodeHttp', 'readFromCon', [this._conId, size]);
  }

  _destroy(err, callback) {
    cordova.exec(() => {
      callback(err);
    }, err => {
      callback(err);
    }, 'CordovaNodeHttp', 'destroyInCon', [this._conId]);
  }
}

module.exports = IncomingMessage;
