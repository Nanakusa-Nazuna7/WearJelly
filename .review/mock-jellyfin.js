// 迷你 mock Jellyfin 服务器：仅供模拟器 UI 回归测试（REQ-TEST-005）。
// 实现 WearJelly 用到的最小端点集：登录 / 曲库 / 歌词 / 音频流 / 上报。
// 音频为脚本内生成的 5 分钟 8kHz 16-bit WAV（440Hz 正弦），seek 靠 WAV 头由 Media3 本地换算。
const http = require('http');

const SAMPLE_RATE = 8000;
const DURATION_S = 300; // 5 分钟
const TICKS_PER_S = 10_000_000;

// ---- 生成 WAV（懒加载缓存）----
let wavBuf = null;
function wav() {
  if (wavBuf) return wavBuf;
  const dataLen = SAMPLE_RATE * DURATION_S * 2;
  const buf = Buffer.alloc(44 + dataLen);
  buf.write('RIFF', 0); buf.writeUInt32LE(36 + dataLen, 4); buf.write('WAVE', 8);
  buf.write('fmt ', 12); buf.writeUInt32LE(16, 16); buf.writeUInt16LE(1, 20);
  buf.writeUInt16LE(1, 22); buf.writeUInt32LE(SAMPLE_RATE, 24);
  buf.writeUInt32LE(SAMPLE_RATE * 2, 28); buf.writeUInt16LE(2, 32); buf.writeUInt16LE(16, 34);
  buf.write('data', 36); buf.writeUInt32LE(dataLen, 40);
  for (let i = 0; i < dataLen / 2; i++) {
    const t = (i % (SAMPLE_RATE * 5)) / SAMPLE_RATE; // 每 5 秒循环的音，听感有变化
    const v = Math.round(Math.sin(2 * Math.PI * 440 * t) * 6000);
    buf.writeInt16LE(v, 44 + i * 2);
  }
  wavBuf = buf;
  return buf;
}

// ---- 曲库数据 ----
const artists = ['Alpha Artist', 'Bravo Band', 'Charlie Choir'].map((n, i) => ({
  Id: 'ar' + (i + 1), Name: n, Type: 'MusicArtist',
}));
const albums = ['Alpha Album', 'Bravo Album', 'Charlie Album'].map((n, i) => ({
  Id: 'al' + (i + 1), Name: n, Type: 'MusicAlbum', AlbumArtist: 'Mock Artist',
}));
const lyricLines = [];
const lyricTexts = [
  '第一句 时间零点整', '第二句 十秒已过', '第三句 二十秒的位置', '第四句 三十秒到了这里',
  '第五句 四十秒继续', '第六句 五十秒不停', '第七句 一分钟整', '第八句 七十秒的约定',
  '第九句 八十秒飞行', '第十句 九十秒降临', '第十一句 一百秒清醒', '第十二句 一百一十秒',
  '第十三句 两分钟附近', '第十四句 两分十秒', '第十五句 尾声之前',
];
lyricTexts.forEach((text, i) => lyricLines.push({ Text: text, Start: i * 10 * TICKS_PER_S }));

const songs = Array.from({ length: 8 }, (_, i) => ({
  Id: 's' + (i + 1),
  Name: ['Alpha Song', 'Bravo Song', 'Charlie Song', 'Delta Song', 'Echo Song', 'Foxtrot Song', 'Golf Song', 'Hotel Song'][i],
  Type: 'Audio',
  Artists: ['Mock Artist'],
  Album: 'Mock Album',
  AlbumId: 'al1',
  RunTimeTicks: DURATION_S * TICKS_PER_S,
}));

function json(res, code, obj) {
  const body = JSON.stringify(obj);
  res.writeHead(code, { 'Content-Type': 'application/json; charset=utf-8', 'Content-Length': Buffer.byteLength(body) });
  res.end(body);
}

const server = http.createServer((req, res) => {
  const url = req.url || '';
  console.log(new Date().toISOString(), req.method, url);

  if (req.method === 'POST' && url.startsWith('/Users/AuthenticateByName')) {
    return json(res, 200, { AccessToken: 'mocktoken', User: { Id: 'u1', Name: 'nazuna' } });
  }
  if (req.method === 'GET' && url.startsWith('/Artists')) {
    return json(res, 200, { Items: artists, TotalRecordCount: artists.length });
  }
  if (req.method === 'GET' && /\/Users\/[^/]+\/Items/.test(url)) {
    const isAudio = url.includes('IncludeItemTypes=Audio');
    const list = isAudio ? songs : albums;
    return json(res, 200, { Items: list, TotalRecordCount: list.length });
  }
  if (req.method === 'GET' && /\/Audio\/[^/]+\/Lyrics/.test(url)) {
    return json(res, 200, { Lyrics: lyricLines });
  }
  if (req.method === 'GET' && /\/Audio\/[^/]+\/stream/.test(url)) {
    const buf = wav();
    res.writeHead(200, {
      'Content-Type': 'audio/wav',
      'Content-Length': buf.length,
      'Accept-Ranges': 'bytes',
    });
    return res.end(buf);
  }
  if (req.method === 'POST' && (url.startsWith('/Sessions/Playing') || url.startsWith('/Sessions/Logout'))) {
    return json(res, 200, {});
  }
  // 其余（图片等）一律 404，走 app 的兜底图标
  res.writeHead(404); res.end();
});

server.listen(8096, '127.0.0.1', () => console.log('mock jellyfin on http://127.0.0.1:8096'));
