const url = require('url');
const OutgoingMessage = require('./outgoing_message');
const IncommingMessage = require('./incomming_message');
const { nativeExecWrap } = require('./util');

const nativeConnect = nativeExecWrap('connect');
const nativeReadHeadFromCon = nativeExecWrap('readHeadFromCon');


class ClientRequest extends OutgoingMessage {
  constructor(input, options, cb) {
    if (typeof input === 'object') {
      cb = options;
      options = input;
      input = ClientRequest._resolveUrl(options);
    } else if(typeof input === 'string') {
      if (typeof options === 'function') {
        cb = options;
        options = {};
      }
      options = options || {};
    } else {
      throw new Error('Invalid arguments');
    }

    super(options);
    this.agent = null;
    this.res = null;
    this.timeoutCb = null;
    this.upgradeOrConnect = false;
    this.parser = null;
    this._cb = cb;
    this._makeNativeConnection(input, options);
    this.once('_headersent', this._onHeaderSent.bind(this));
  }

  static _resolveUrl(opt) {
    let urlOpt;
    if(opt.protocol === 'https:'){
      urlOpt = { hostname: 'localhost', pathname: '/', port: '443', protocol: 'https:' };
    } else {
      urlOpt = { hostname: 'localhost', pathname: '/', port: '80', protocol: 'http:' };
    }

    var [pathname, query] = (opt.path || '').split('?');
    urlOpt.hostname = opt.hostname || opt.host || urlOpt.hostname;
    urlOpt.port = opt.port || urlOpt.port;
    urlOpt.pathname = pathname || urlOpt.pathname;
    urlOpt.search = query;
    return url.format(urlOpt);
  }

  abort() {
    if(!this.aborted && !this.finished) {
      this.aborted = true;

      this._socketPromise.then(() => {
        this.destroy();
        this.res.destroy();
      });
    }
  }

  _makeNativeConnection(url, options) {
    nativeConnect(url, options).then(packet => {
      this._conId = packet.conId;
      this.emit('socket', packet.socket);
    }).catch(err => {
      process.nextTick(() => this.emit('error', err));
    });
  }

  _onHeaderSent() {
    nativeReadHeadFromCon(this._conId).then(packet => {
      this.res = new IncommingMessage(this._conId, this);
      this.res._parseNativePacket(packet);
      if(this._cb) this._cb(this.res);
      this.emit('response', this.res);
    }).catch(err => {
      process.nextTick(() => this.emit('error', err));
    });
  }
}

ClientRequest.OutgoingMessage = OutgoingMessage;
ClientRequest.IncommingMessage = IncommingMessage;
module.exports = ClientRequest;
