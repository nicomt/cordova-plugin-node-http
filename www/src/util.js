
module.exports = {
  nativeExecWrap(func) {
    return (...args) => {
      return new Promise((res, rej) => {
        cordova.exec(res, err => rej(new Error(err)), 'CordovaNodeHttp', func, args);
      });
    };
  }
};
