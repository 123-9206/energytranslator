/** Optional upload utility. Requires your own account; never commits an upload private key. */
const path = require('path');
const fs = require('fs');

async function upload() {
  const appid = process.env.WECHAT_APP_ID;
  const privateKeyPath = process.env.WECHAT_PRIVATE_KEY_PATH;
  const version = process.env.WECHAT_UPLOAD_VERSION;
  if (!appid || !privateKeyPath || !version) {
    throw new Error('Set WECHAT_APP_ID, WECHAT_PRIVATE_KEY_PATH and WECHAT_UPLOAD_VERSION first');
  }
  if (!fs.existsSync(privateKeyPath)) throw new Error('Upload private-key file not found');
  const ci = require('miniprogram-ci');
  const project = new ci.Project({
    appid, type: 'miniProgram', projectPath: path.resolve(__dirname),
    privateKeyPath: path.resolve(privateKeyPath),
    ignores: ['node_modules/**/*', '*.key', '*.pem', '.env*'],
  });
  await ci.upload({
    project, version, desc: process.argv[2] || 'Development build',
    setting: { es6: true, minify: true, minifyJS: true, minifyWXML: true, minifyWXSS: true },
  });
  console.log('Upload completed');
}

upload().catch(error => { console.error(error.message); process.exitCode = 1; });
