const { Writable } = require('stream-browserify');
const { nativeExecWrap } = require('./util');

const nativeFlushConHeaders = nativeExecWrap('flushConHeaders');
const nativeWriteToCon = nativeExecWrap('writeToCon');
const nativeEndCon = nativeExecWrap('endCon');

const readOnlyMethods = ['GET', 'DELETE'];
class OutgoingMessage extends Writable {
  constructor(options) {
    super();
    this.method = options.method ? options.method.toUpperCase() : 'GET';
    this.outputData = [];
    this.outputSize = 0;
    this.writable = !readOnlyMethods.includes(this.method);
    this.chunkedEncoding = false;
    this.shouldKeepAlive = true;
    this.useChunkedEncodingByDefault = true;
    this.sendDate = false;
    this.finished = false;
    this.aborted = false;
    this.socket = null;
    this.connection = null;
    this._headersSent = false;
    this.maxHeadersCount = 2000;
    this.headers = {};
    this._socketPromise = new Promise((res) =>{
      this.on('socket', res);
    });

  }

  setHeader(name, value) {
    this.headers[name.toLowerCase()] = value;
    if(Object.keys(this.headers).length > this.maxHeadersCount)
      throw new Error('Max header count exceded');
  }

  removeHeader(name) {
    delete this.headers[name.toLowerCase()];
  }

  getHeader(name) {
    return this.headers[name.toLowerCase()];
  }

  flushHeaders() {
    if(!this._flushHeadersPromise) {
      this._flushHeadersPromise = this._socketPromise.then(() => {
        return nativeFlushConHeaders(this._conId, this.headers);
      })
        .then(() => this.emit('_headersent'))
        .catch( err => process.nextTick(()=> this.emit('error', err)));
    }
    return this._flushHeadersPromise;
  }


  _write(chunk, encoding, callback) {
    this.flushHeaders()
      .then(()=> {
        const b64Str = Buffer.from(chunk, encoding).toString('base64');
        return nativeWriteToCon(this._conId, b64Str);
      })
      .then(()=> callback())
      .catch(err => callback(err));
  }

  _final(callback) {
    this.flushHeaders()
      .then(()=> nativeEndCon(this._conId))
      .then(()=> this.finished = true)
      .then(()=> callback())
      .catch(err => callback(err));
  }

  _destroy(err, callback) {
    cordova.exec(() => {
      callback(err);
    }, err => {
      callback(err);
    }, 'CordovaNodeHttp', 'destroyOutCon', [this._conId]);
  }
}

module.exports = OutgoingMessage;
